// AGENT-DONE(claude): tarkov-health-engine
package ichttt.mods.firstaid.common.health;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only place where the limb model and the vanilla health value of a player touch.
 * <p>
 * The limb model ({@link PlayerDamageModel}) is the source of truth. {@code getHealth()} is a value that is
 * <i>derived</i> from it (see {@link #project}) so vanilla code and other mods keep working. Writes in the other
 * direction - a mod calling {@code setHealth} - are not trusted blindly either: they are translated into limb damage or
 * healing by {@link #onExternalSetHealth}, after which the derived value is pushed again.
 */
public final class VanillaHealthBridge {
    /**
     * The lowest vanilla health a living model is projected to. Vanilla and many mods treat {@code health <= 0} as
     * dead, so a player with a single hit point left in a limb must never project to zero.
     */
    public static final float ALIVE_FLOOR = 0.5F;
    private static final float NO_OP_EPSILON = 1.0E-3F;
    private static final Set<UUID> BEING_REVIVED = ConcurrentHashMap.newKeySet();

    private VanillaHealthBridge() {
    }

    /** Pure: maps the summed limb hit points onto vanilla max health. */
    public static float project(float currentLimbTotal, float maxLimbTotal, float vanillaMaxHealth) {
        if (Float.isNaN(currentLimbTotal) || !(maxLimbTotal > 0.0F)) {
            return 0.0F;
        }
        return Mth.clamp(currentLimbTotal / maxLimbTotal, 0.0F, 1.0F) * vanillaMaxHealth;
    }

    /** Pure: a living model never projects below {@link #ALIVE_FLOOR}. */
    public static float applyAliveFloor(float projected, boolean modelAlive) {
        return modelAlive ? Math.max(ALIVE_FLOOR, projected) : projected;
    }

    /** Pure: decides what an external write of {@code requested} on top of {@code current} means. */
    public static Intent classify(float current, float requested, float vanillaMax) {
        if (Float.isNaN(requested)) {
            return Intent.IGNORE;
        }
        float target = Mth.clamp(requested, 0.0F, vanillaMax);
        if (target <= 0.0F) {
            return Intent.KILL;
        }
        float delta = target - current;
        if (Math.abs(delta) < NO_OP_EPSILON) {
            return Intent.IGNORE;
        }
        return delta < 0.0F ? Intent.DAMAGE : Intent.HEAL;
    }

    public enum Intent {
        IGNORE, DAMAGE, HEAL, KILL
    }

    /** Re-derives the vanilla health value from the limb model. */
    public static void push(Player player, @Nullable AbstractPlayerDamageModel model) {
        if (model instanceof PlayerDamageModel playerDamageModel && !player.level().isClientSide()) {
            playerDamageModel.syncVanillaHealth(player);
        }
    }

    /** Keeps the vanilla health strictly positive while the limb model says the player is still alive (downed, rescued). */
    public static void ensureAlive(Player player) {
        CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), ALIVE_FLOOR)));
    }

    public static void setBeingRevived(Player player, boolean beingRevived) {
        if (beingRevived) {
            BEING_REVIVED.add(player.getUUID());
        } else {
            BEING_REVIVED.remove(player.getUUID());
        }
    }

    public static boolean isBeingRevived(Player player) {
        return BEING_REVIVED.contains(player.getUUID());
    }

    /**
     * Called by {@code LivingEntityHealthMixin} for every {@code setHealth} on a player.
     *
     * @return true if the write was consumed (and must not reach vanilla), false to let vanilla write the value itself
     */
    public static boolean onExternalSetHealth(ServerPlayer player, float requested) {
        if (!FirstAidConfig.watchSetHealth || CommonUtils.isSetHealthInterceptionSuppressed()) {
            return false;
        }
        // Not logged in yet / respawning: vanilla still copies its own health around (ServerPlayer#restoreFrom, NBT load)
        // before the limb model was attached to this instance.
        if (player.connection == null || player.tickCount <= 0) {
            return false;
        }
        AbstractPlayerDamageModel model = CommonUtils.getExistingDamageModel(player);
        if (!(model instanceof PlayerDamageModel playerDamageModel)) {
            return false;
        }
        if (isBeingRevived(player)) {
            return true;
        }
        float current = player.getHealth();
        if (current <= 0.0F || !player.isAlive() || model.isDead(player)) {
            return false;
        }
        Intent intent = classify(current, requested, player.getMaxHealth());
        if (intent == Intent.IGNORE) {
            return true;
        }
        CommonUtils.runWithoutSetHealthInterception(() -> applyIntent(player, playerDamageModel, intent, current, requested));
        return true;
    }

    private static void applyIntent(ServerPlayer player, PlayerDamageModel model, Intent intent, float current, float requested) {
        DamageSource activeSource = CommonUtils.getActiveDamageSource();
        switch (intent) {
            case KILL -> {
                model.forEach(part -> part.currentHealth = 0.0F);
                CommonUtils.syncDamageModel(player);
                CommonUtils.killPlayer(model, player, activeSource);
                return;
            }
            case DAMAGE -> {
                float vanillaDamage = current - Mth.clamp(requested, 0.0F, player.getMaxHealth());
                DamageSource source = activeSource != null ? activeSource : player.damageSources().magic();
                IDamageDistributionAlgorithm distribution = activeSource != null ? EventHandler.getForcedDamageDistribution(activeSource) : null;
                if (distribution == null) {
                    distribution = RandomDamageDistributionAlgorithm.getDefault();
                }
                if (FirstAidConfig.GENERAL.debug.get()) {
                    CommonUtils.debugLogStacktrace("External setHealth damage: " + vanillaDamage);
                }
                DamageDistribution.handleDamageTaken(distribution, model, vanillaDamage, player, source, true,
                        activeSource == null || !CommonUtils.isFootOnlyDamageSource(activeSource));
            }
            case HEAL -> {
                float vanillaHeal = Mth.clamp(requested, 0.0F, player.getMaxHealth()) - current;
                if (FirstAidConfig.GENERAL.debug.get()) {
                    CommonUtils.debugLogStacktrace("External setHealth healing: " + vanillaHeal);
                }
                if (requested >= player.getMaxHealth() - 0.001F) {
                    // A write to full health is a deliberate full restore by another mod (admin heal, totem-like effects): it
                    // also brings back destroyed limbs, which no ordinary healing does (they need surgery). Surgery penalties stay.
                    for (AbstractDamageablePart part : model) {
                        part.currentHealth = part.effectiveMaxHealth();
                    }
                } else {
                    HealthDistribution.addRandomHealth(HealthUnits.toEngine(vanillaHeal, player, model), player, true);
                }
            }
            default -> FirstAid.LOGGER.warn("Unhandled external health intent {}", intent);
        }
        model.syncVanillaHealth(player);
    }
}
