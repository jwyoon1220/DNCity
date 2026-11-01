// AGENT-DONE(claude): tarkov-health-engine
package ichttt.mods.firstaid.common.health;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damage.HitProfiles;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Two unit systems meet in this mod:
 * <ul>
 *     <li><b>engine units</b> - the hit points of the limbs (head 35, body 85, ...). This is the real health.</li>
 *     <li><b>vanilla units</b> - what other mods speak: {@code hurt(6)}, {@code heal(4)}, {@code setHealth(12)}, and
 *     what the item configs call a "half heart".</li>
 * </ul>
 * In {@code AUTO} mode both are tied together linearly through the player's vanilla max health, so a hit worth 30% of
 * vanilla max health always removes 30% of the whole limb pool no matter what absolute limb values are configured.
 * In {@code RAW} mode the numbers are used 1:1.
 */
public final class HealthUnits {
    /** Vanilla health used to express pain/feedback thresholds that were tuned for a 20 hit point player. */
    public static final float VANILLA_REFERENCE_HEALTH = 20.0F;

    /**
     * Damage types in this tag carry limb hit points instead of vanilla health: a gun whose damage value is 50 removes
     * 50 hit points from the limb it hits. Empty by default (everything is balanced for vanilla health).
     */
    public static final TagKey<DamageType> RAW_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "raw_damage"));

    private HealthUnits() {
    }

    /** Pure: how many limb hit points one vanilla hit point is worth. */
    public static float engineHpPerVanillaHp(boolean raw, int totalLimbMaxHealth, float vanillaMaxHealth) {
        if (raw || totalLimbMaxHealth <= 0 || !(vanillaMaxHealth > 0.0F)) {
            return 1.0F;
        }
        return totalLimbMaxHealth / vanillaMaxHealth;
    }

    /** Pure: converts limb hit points into the 20 hit point scale pain and feedback were tuned for. */
    public static float toReferenceScale(boolean raw, float engineHp, int totalLimbMaxHealth) {
        if (raw || totalLimbMaxHealth <= 0) {
            return engineHp;
        }
        return engineHp * VANILLA_REFERENCE_HEALTH / totalLimbMaxHealth;
    }

    /**
     * The injury debuff data ({@code damageTakenThreshold: 1, 2, ...}) was authored for the original part sizes
     * (head, arms, legs and feet 4, body 6 hit points). Damage handed to debuffs is therefore expressed relative to
     * the size of the part that was hit, which keeps "half a head" meaning the same no matter the configured limb hp.
     */
    public static float toDebuffScale(EnumPlayerPart part, int partMaxHealth, float engineHp) {
        if (partMaxHealth <= 0) {
            return engineHp;
        }
        return engineHp * authoredPartHealth(part) / partMaxHealth;
    }

    public static float authoredPartHealth(EnumPlayerPart part) {
        return part == EnumPlayerPart.BODY ? 6.0F : 4.0F;
    }

    public static boolean isRaw() {
        FirstAidConfig.Server.DamageScaleMode mode = FirstAidConfig.isServerConfigLoaded()
                ? FirstAidConfig.SERVER.damageScaleMode.get()
                : FirstAidConfig.SERVER.damageScaleMode.getDefault();
        return mode == FirstAidConfig.Server.DamageScaleMode.RAW;
    }

    public static float engineHpPerVanillaHp(Player player, @Nullable AbstractPlayerDamageModel model) {
        if (model == null) {
            return 1.0F;
        }
        DamageSource active = CommonUtils.getActiveDamageSource();
        if ((active != null && active.is(RAW_DAMAGE)) || HitProfiles.current().damageIsLimbHp()) {
            return 1.0F;
        }
        return engineHpPerVanillaHp(isRaw(), model.getCurrentMaxHealth(), player.getMaxHealth());
    }

    public static float toEngine(float vanillaHp, Player player, @Nullable AbstractPlayerDamageModel model) {
        return vanillaHp * engineHpPerVanillaHp(player, model);
    }

    public static float toVanilla(float engineHp, Player player, @Nullable AbstractPlayerDamageModel model) {
        float factor = engineHpPerVanillaHp(player, model);
        return factor <= 0.0F ? engineHp : engineHp / factor;
    }

    /** One heal pulse of a healing item (authored as one "half heart") in limb hit points. */
    public static float healPulse(Player player, @Nullable AbstractPlayerDamageModel model) {
        return engineHpPerVanillaHp(player, model);
    }

    public static float toReferenceScale(float engineHp, @Nullable AbstractPlayerDamageModel model) {
        if (model == null) {
            return engineHp;
        }
        return toReferenceScale(isRaw(), engineHp, model.getCurrentMaxHealth());
    }
}
