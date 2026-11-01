// AGENT-DONE(claude): tarkov-health-engine
package ichttt.mods.firstaid.gametest;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.health.HealthUnits;
import ichttt.mods.firstaid.common.health.InjuryEngine;
import ichttt.mods.firstaid.common.health.VanillaHealthBridge;
import ichttt.mods.firstaid.common.util.CommonUtils;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side checks of the limb health engine, run with {@code ./gradlew runGameTestServer}.
 * Not part of the shipped jar (see build.gradle).
 */
@GameTestHolder(FirstAid.MODID)
@PrefixGameTestTemplate(false)
public class FirstAidGameTests {
    private static final String EMPTY = "empty";
    private static final Map<UUID, List<Packet<?>>> SENT_PACKETS = new ConcurrentHashMap<>();
    /** ServerPlayer rejects damage for 60 ticks after joining. */
    private static final int SPAWN_PROTECTION_TICKS = 64;

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        // Same friendliness for every test, the config default (0.8) would make kills random.
        FirstAid.useFriendlyRandomDistribution = true;
        FirstAid.friendlyRandomDistributionChance = 1.0F;
        // GameTestHelper#makeMockServerPlayerInLevel builds a connection without NeoForge's payload registry, which makes
        // the first attachment sync throw. This player gets a connection that simply drops every packet.
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "firstaid-test");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        List<Packet<?>> sent = new CopyOnWriteArrayList<>();
        SENT_PACKETS.put(player.getUUID(), sent);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
                sent.add(packet);
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
                sent.add(packet);
            }
        };
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        // A real player is ticked by its network handler (ServerPlayer#doTick -> Player#tick -> PlayerTickEvent).
        // This one has no registered connection, so the test drives that tick itself.
        helper.onEachTick(() -> {
            if (!player.isRemoved() && player.isAlive()) {
                player.doTick();
            }
        });
        return player;
    }

    private static PlayerDamageModel model(GameTestHelper helper, ServerPlayer player) {
        AbstractPlayerDamageModel model = CommonUtils.getDamageModel(player);
        helper.assertTrue(model instanceof PlayerDamageModel, "player has no limb model");
        return (PlayerDamageModel) model;
    }

    private static float limbSum(AbstractPlayerDamageModel model) {
        float sum = 0.0F;
        for (AbstractDamageablePart part : model) {
            sum += part.currentHealth;
        }
        return sum;
    }

    private static void near(GameTestHelper helper, String what, float actual, float expected, float tolerance) {
        helper.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + " (+-" + tolerance + ") but was " + actual);
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    // ------------------------------------------------------------------ pure rules

    @GameTest(template = EMPTY)
    public static void pureRules(GameTestHelper helper) {
        near(helper, "auto unit", HealthUnits.engineHpPerVanillaHp(false, 410, 20.0F), 20.5F, 0.001F);
        near(helper, "raw unit", HealthUnits.engineHpPerVanillaHp(true, 410, 20.0F), 1.0F, 0.001F);
        near(helper, "reference scale", HealthUnits.toReferenceScale(false, 41.0F, 410), 2.0F, 0.001F);

        // half a head (17.5 of 35 hit points) is "damage 2" for the debuff data authored for a 4 hit point head
        near(helper, "debuff scale head", HealthUnits.toDebuffScale(ichttt.mods.firstaid.api.enums.EnumPlayerPart.HEAD, 35, 17.5F), 2.0F, 0.001F);
        near(helper, "debuff scale body", HealthUnits.toDebuffScale(ichttt.mods.firstaid.api.enums.EnumPlayerPart.BODY, 85, 42.5F), 3.0F, 0.001F);
        near(helper, "debuff scale legacy sizes", HealthUnits.toDebuffScale(ichttt.mods.firstaid.api.enums.EnumPlayerPart.HEAD, 4, 2.0F), 2.0F, 0.001F);
        near(helper, "projection", VanillaHealthBridge.project(205.0F, 410.0F, 20.0F), 10.0F, 0.001F);
        near(helper, "projection overflow", VanillaHealthBridge.project(500.0F, 410.0F, 20.0F), 20.0F, 0.001F);
        near(helper, "alive floor", VanillaHealthBridge.applyAliveFloor(0.001F, true), VanillaHealthBridge.ALIVE_FLOOR, 0.0001F);
        near(helper, "dead stays dead", VanillaHealthBridge.applyAliveFloor(0.0F, false), 0.0F, 0.0001F);

        helper.assertTrue(VanillaHealthBridge.classify(20.0F, 10.0F, 20.0F) == VanillaHealthBridge.Intent.DAMAGE, "damage intent");
        helper.assertTrue(VanillaHealthBridge.classify(10.0F, 20.0F, 20.0F) == VanillaHealthBridge.Intent.HEAL, "heal intent");
        helper.assertTrue(VanillaHealthBridge.classify(10.0F, 10.0004F, 20.0F) == VanillaHealthBridge.Intent.IGNORE, "no-op write");
        helper.assertTrue(VanillaHealthBridge.classify(10.0F, 0.0F, 20.0F) == VanillaHealthBridge.Intent.KILL, "kill intent");
        helper.assertTrue(VanillaHealthBridge.classify(10.0F, Float.NaN, 20.0F) == VanillaHealthBridge.Intent.IGNORE, "NaN write");

        helper.assertTrue(InjuryEngine.bleedLevelFor(0.5D, 0.25D) == AbstractDamageablePart.BLEED_HEAVY, "big hit bleeds heavily");
        helper.assertTrue(InjuryEngine.bleedLevelFor(0.1D, 0.25D) == AbstractDamageablePart.BLEED_LIGHT, "small hit bleeds lightly");
        near(helper, "bleed chance cap", (float) InjuryEngine.bleedChance(0.15D, 0.9D, 10.0D), 0.95F, 0.0001F);
        near(helper, "heavy drain", InjuryEngine.bleedDrainPerSecond(85, AbstractDamageablePart.BLEED_HEAVY, 0.8D, 2.5D), 2.125F, 0.001F);
        near(helper, "fall fracture", (float) InjuryEngine.fractureChance(InjuryEngine.FractureCause.FALL, 1.0D), 1.0F, 0.0001F);
        near(helper, "no fall fracture on small hit", (float) InjuryEngine.fractureChance(InjuryEngine.FractureCause.FALL, 0.1D), 0.0F, 0.0001F);
        helper.assertFalse(InjuryEngine.canFracture(ichttt.mods.firstaid.api.enums.EnumPlayerPart.HEAD), "head cannot fracture");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void injuriesSurviveSaveAndLoad(GameTestHelper helper) {
        PlayerDamageModel saved = new PlayerDamageModel();
        saved.LEFT_LEG.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;
        saved.LEFT_LEG.fractured = true;
        saved.BODY.bleedLevel = AbstractDamageablePart.BLEED_LIGHT;
        CompoundTag tag = saved.serializeNBT();

        PlayerDamageModel loaded = new PlayerDamageModel();
        loaded.deserializeNBT(tag);
        helper.assertTrue(loaded.LEFT_LEG.bleedLevel == AbstractDamageablePart.BLEED_HEAVY, "heavy bleed lost");
        helper.assertTrue(loaded.LEFT_LEG.fractured, "fracture lost");
        helper.assertTrue(loaded.BODY.bleedLevel == AbstractDamageablePart.BLEED_LIGHT, "light bleed lost");
        helper.assertTrue(loaded.HEAD.bleedLevel == AbstractDamageablePart.BLEED_NONE && !loaded.HEAD.fractured, "phantom injury on head");
        loaded.clearInjuries();
        helper.assertTrue(loaded.LEFT_LEG.bleedLevel == AbstractDamageablePart.BLEED_NONE && !loaded.LEFT_LEG.fractured, "clearInjuries");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the vanilla health value is derived

    @GameTest(template = EMPTY)
    public static void projectionFollowsLimbs(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        float total = model.getCurrentMaxHealth();
        near(helper, "full health", player.getHealth(), player.getMaxHealth(), 0.01F);

        float lost = model.BODY.getMaxHealth() / 2.0F;
        model.BODY.currentHealth -= lost;
        model.syncVanillaHealth(player);
        near(helper, "half a body lost", player.getHealth(), (total - lost) / total * player.getMaxHealth(), 0.01F);
        near(helper, "model projection agrees", player.getHealth(), model.projectVanillaHealth(player), 0.01F);
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void externalSetHealthBecomesLimbDamage(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        float before = limbSum(model);
        player.tickCount = 100;
        player.setHealth(10.0F);
        helper.assertTrue(limbSum(model) < before - 1.0F, "limbs did not lose hit points: " + limbSum(model) + "/" + before);
        helper.assertTrue(player.isAlive(), "player died from a non-lethal setHealth");
        helper.assertTrue(player.getHealth() > 5.0F && player.getHealth() < 19.5F, "health should have dropped moderately, was " + player.getHealth());
        near(helper, "vanilla value is derived from the limbs", player.getHealth(), model.projectVanillaHealth(player), 0.01F);
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void externalSetHealthToMaxHealsLimbs(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        player.tickCount = 100;
        float total = model.getCurrentMaxHealth();
        model.BODY.currentHealth = 20.0F;
        model.LEFT_LEG.currentHealth = 5.0F;
        model.RIGHT_ARM.currentHealth = 0.0F;
        model.syncVanillaHealth(player);
        helper.assertTrue(player.getHealth() < player.getMaxHealth() - 1.0F, "setup: health should be reduced");

        player.setHealth(player.getMaxHealth());
        near(helper, "limbs fully restored", limbSum(model), total, 0.5F);
        near(helper, "vanilla value", player.getHealth(), player.getMaxHealth(), 0.05F);
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void healEventIsConvertedToLimbHitPoints(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        player.tickCount = 100;
        for (AbstractDamageablePart part : model) {
            part.currentHealth = part.getMaxHealth() / 2.0F;
        }
        model.syncVanillaHealth(player);
        float before = limbSum(model);
        float expected = 5.0F * ichttt.mods.firstaid.FirstAidConfig.SERVER.otherRegenMultiplier.get().floatValue()
                * HealthUnits.engineHpPerVanillaHp(player, model);

        player.heal(5.0F);
        near(helper, "healed hit points", limbSum(model) - before, expected, 1.5F);
        near(helper, "vanilla value", player.getHealth(), model.projectVanillaHealth(player), 0.01F);
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hurtIsProportionalToVanillaMaxHealth(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            helper.assertTrue(player.hurt(player.damageSources().magic(), 5.0F), "hurt() was rejected");
            helper.assertTrue(player.isAlive(), "5 damage must not kill a full player");
            helper.assertTrue(player.getHealth() > 13.0F && player.getHealth() < 17.5F, "5 damage of 20 should leave about 15, was " + player.getHealth());
            near(helper, "vanilla value", player.getHealth(), model.projectVanillaHealth(player), 0.01F);
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyDistributionTypeConvertsVanillaDamage(GameTestHelper helper) {
        // explosion = equal distribution, anvil/lightning = standard with a fixed part map, magic = random:
        // every one of them has to turn vanilla damage into limb hit points, none may be 20 times too weak
        String[] kinds = {"explosion", "magic", "anvil", "lightning"};
        ServerPlayer[] players = new ServerPlayer[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            players[i] = survivalPlayer(helper);
        }
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            for (int i = 0; i < kinds.length; i++) {
                ServerPlayer player = players[i];
                PlayerDamageModel model = model(helper, player);
                float total = model.getCurrentMaxHealth();
                DamageSource source = switch (kinds[i]) {
                    case "explosion" -> player.damageSources().explosion(null, null);
                    case "anvil" -> player.damageSources().anvil(null);
                    case "lightning" -> player.damageSources().lightningBolt();
                    default -> player.damageSources().magic();
                };
                helper.assertTrue(player.hurt(source, 5.0F), kinds[i] + ": hurt() was rejected");
                float lostShare = (total - limbSum(model)) / total;
                // the equal distribution spreads 5/20 over the whole body (a quarter of the pool); the others start on one
                // limb and lose part of the excess to limbOverkillFactor, but must stay far above the ~1% an unconverted
                // 5 hit points would remove
                float minimum = kinds[i].equals("explosion") ? 0.18F : 0.08F;
                helper.assertTrue(lostShare > minimum && lostShare < 0.30F, kinds[i] + ": 5 of 20 vanilla damage removed " + lostShare + " of the pool");
                leave(helper, player);
            }
            helper.succeed();
        });
    }

    // Own batch: it switches friendly random distribution off, a static the other tests rely on being on.
    @GameTest(template = EMPTY, batch = "locationalHits", timeoutTicks = 200)
    public static void gunStyleHitsLandWhereTheyHitWithBothDamageParts(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        FirstAid.useFriendlyRandomDistribution = false;
        PlayerDamageModel model = model(helper, player);
        Arrow bullet = helper.spawn(EntityType.ARROW, 1, 1, 1);
        // A damage type that is NOT in minecraft:is_projectile with a projectile as direct cause: how TACZ bullets arrive
        DamageSource gunShot = player.damageSources().source(net.minecraft.world.damagesource.DamageTypes.MAGIC, bullet, null);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            ichttt.mods.firstaid.common.EventHandler.recordProjectileHit(player, bullet, player.getEyePosition());
            // one shot, two hurt() calls in the same tick (normal part, armor piercing part)
            player.invulnerableTime = 0; // TACZ clears the invulnerability frames before each part as well
            helper.assertTrue(player.hurt(gunShot, 0.5F), "first part rejected");
            player.invulnerableTime = 0;
            helper.assertTrue(player.hurt(gunShot, 0.5F), "second part rejected");
            float headLost = model.HEAD.getMaxHealth() - model.HEAD.currentHealth;
            near(helper, "both parts hit the head", headLost, 2 * 0.5F * HealthUnits.engineHpPerVanillaHp(player, model), 0.5F);
            for (AbstractDamageablePart part : model) {
                if (part != model.HEAD) {
                    helper.assertTrue(part.currentHealth >= part.getMaxHealth() - 0.001F, part.part + " was damaged by a headshot");
                }
            }
            FirstAid.friendlyRandomDistributionChance = 1.0F;
            FirstAid.useFriendlyRandomDistribution = true;
            bullet.discard();
            leave(helper, player);
            helper.succeed();
        });
    }

    /** Prints how long the hot paths take; not a pass/fail check (the numbers depend on the machine). */
    @GameTest(template = EMPTY, batch = "benchmark", timeoutTicks = 400, required = false)
    public static void benchmarkHotPaths(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            int hits = 3000;
            DamageSource magic = player.damageSources().magic();
            long start = System.nanoTime();
            for (int i = 0; i < hits; i++) {
                player.invulnerableTime = 0;
                player.hurt(magic, 0.001F);
                for (AbstractDamageablePart part : model) {
                    part.currentHealth = part.getMaxHealth();
                }
            }
            long hurtNs = System.nanoTime() - start;
            start = System.nanoTime();
            for (int i = 0; i < hits; i++) {
                player.heal(0.001F);
            }
            long healNs = System.nanoTime() - start;
            FirstAid.LOGGER.info("BENCH hurt: {} us/hit, heal: {} us/call", hurtNs / hits / 1000.0, healNs / hits / 1000.0);
            leave(helper, player);
            helper.succeed();
        });
    }

    private static boolean snowballProfileRegistered;

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hitProfilesScaleAndRedefineTheDamageOfAmmunition(GameTestHelper helper) {
        if (!snowballProfileRegistered) {
            snowballProfileRegistered = true;
            // snowballs stand in for a mod's ammunition: double power, and the damage value means limb hit points
            ichttt.mods.firstaid.api.damage.HitProfiles.register(source -> source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Snowball
                    ? new ichttt.mods.firstaid.api.damage.HitProfile(2.0F, true, 0.0F, 1.0F, -1.0F, 0.0F, -1.0F, false)
                    : null);
        }
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        net.minecraft.world.entity.projectile.Snowball ammo = helper.spawn(EntityType.SNOWBALL, 1, 1, 1);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            float before = limbSum(model);
            helper.assertTrue(player.hurt(player.damageSources().thrown(ammo, null), 3.0F), "hurt() was rejected");
            // 3 limb hit points x 2 power; a vanilla-unit 3 would have been about 61
            near(helper, "limb hit points lost", before - limbSum(model), 6.0F, 0.3F);
            ammo.discard();
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void hitProfileBleedBonusAlwaysOpensABleed(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel after = model(helper, player);
        Arrow arrow = helper.spawn(EntityType.ARROW, 1, 1, 1);
        DamageSource hit = player.damageSources().arrow(arrow, null);
        ichttt.mods.firstaid.api.damage.HitProfile heavy = new ichttt.mods.firstaid.api.damage.HitProfile(1.0F, false, 0.0F, 1.0F, 0.01F, 0.0F, -1.0F, false);
        for (int i = 0; i < 40; i++) {
            PlayerDamageModel before = new PlayerDamageModel();
            before.deserializeNBT(after.serializeNBT());
            after.BODY.bleedLevel = AbstractDamageablePart.BLEED_NONE;
            after.BODY.currentHealth = before.BODY.currentHealth - 1.0F;
            ichttt.mods.firstaid.api.damage.HitProfiles.run(heavy, () -> InjuryEngine.onDamaged(player, after, before, hit));
            helper.assertTrue(after.BODY.bleedLevel == AbstractDamageablePart.BLEED_HEAVY || i < 39, "bonus 1.0 should (almost) always bleed, and a tiny threshold makes it heavy");
            after.BODY.currentHealth = before.BODY.currentHealth;
        }
        arrow.discard();
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lethalWritesKillThroughTheLimbModel(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.tickCount = 100;
        player.setHealth(0.0F);
        helper.assertFalse(player.isAlive(), "setHealth(0) must kill");
        leave(helper, player);

        ServerPlayer other = survivalPlayer(helper);
        helper.assertTrue(other.isAlive(), "second player should be alive");
        other.hurt(other.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertFalse(other.isAlive(), "kill damage must kill");
        leave(helper, other);
        helper.succeed();
    }

    // ------------------------------------------------------------------ injuries

    @GameTest(template = EMPTY)
    public static void headDebuffsFollowThePartSizeNotTheAbsoluteDamage(GameTestHelper helper) {
        for (float fractionOfHead : new float[] {0.05F, 0.5F}) {
            ServerPlayer player = survivalPlayer(helper);
            PlayerDamageModel model = model(helper, player);
            float lost = model.HEAD.getMaxHealth() * fractionOfHead;
            DamageDistribution.handleDamageTaken(new DirectDamageDistributionAlgorithm(EnumPlayerPart.HEAD, true), model,
                    HealthUnits.toVanilla(lost, player, model), player, player.damageSources().generic(), false, false);
            near(helper, "head hit points lost", model.HEAD.getMaxHealth() - model.HEAD.currentHealth, lost, 0.01F);
            if (fractionOfHead < 0.1F) {
                helper.assertFalse(player.hasEffect(MobEffects.BLINDNESS), "a scratch on the head must not blind");
            } else {
                helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "half a head lost should blind, like it did with 4 hit point heads");
            }
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void fallBreaksBothFeet(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            player.hurt(player.damageSources().fall(), 8.0F);
            helper.assertTrue(model.LEFT_FOOT.fractured && model.RIGHT_FOOT.fractured, "a hard landing should break both feet");
            helper.assertTrue(model.LEFT_FOOT.currentHealth <= 0.001F && model.RIGHT_FOOT.currentHealth <= 0.001F, "feet should be blacked out");
            helper.assertTrue(player.isAlive(), "a fall cannot kill through the feet");
            helper.assertTrue(!model.HEAD.fractured && !model.BODY.fractured, "head and body cannot fracture");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void onlyPiercingSourcesOpenBleeds(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel after = model(helper, player);
        Arrow arrow = helper.spawn(EntityType.ARROW, 1, 1, 1);
        DamageSource arrowHit = player.damageSources().arrow(arrow, null);
        DamageSource fall = player.damageSources().fall();

        boolean everBled = false;
        for (int i = 0; i < 40; i++) {
            PlayerDamageModel before = new PlayerDamageModel();
            before.deserializeNBT(after.serializeNBT());
            after.BODY.bleedLevel = AbstractDamageablePart.BLEED_NONE;
            after.BODY.currentHealth = before.BODY.currentHealth - before.BODY.getMaxHealth() * 0.5F;
            InjuryEngine.onDamaged(player, after, before, arrowHit);
            if (after.BODY.bleedLevel != AbstractDamageablePart.BLEED_NONE) {
                everBled = true;
                helper.assertTrue(after.BODY.bleedLevel == AbstractDamageablePart.BLEED_HEAVY, "losing half a body must be a heavy bleed");
            }
            after.BODY.currentHealth = before.BODY.currentHealth;
        }
        helper.assertTrue(everBled, "40 arrow hits that took half the body never caused a bleed");

        after.BODY.bleedLevel = AbstractDamageablePart.BLEED_NONE;
        for (int i = 0; i < 40; i++) {
            PlayerDamageModel before = new PlayerDamageModel();
            before.deserializeNBT(after.serializeNBT());
            after.BODY.currentHealth = before.BODY.currentHealth - before.BODY.getMaxHealth() * 0.5F;
            InjuryEngine.onDamaged(player, after, before, fall);
            helper.assertTrue(after.BODY.bleedLevel == AbstractDamageablePart.BLEED_NONE, "fall damage must not open a bleed");
            after.BODY.currentHealth = before.BODY.currentHealth;
        }
        arrow.discard();
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void bleedDrainsAndTourniquetStopsIt(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        float startHealth = model.LEFT_ARM.currentHealth;
        model.LEFT_ARM.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;

        helper.runAfterDelay(45, () -> {
            float drained = startHealth - model.LEFT_ARM.currentHealth;
            // heavy bleed: 2.5% of 60 hit points per second, 2 or 3 seconds fit into 45 ticks
            helper.assertTrue(drained >= 2.5F && drained <= 5.0F, "heavy bleed should drain about 3-4.5 hit points in 45 ticks, drained " + drained);
            helper.assertTrue(model.LEFT_ARM.bleedLevel == AbstractDamageablePart.BLEED_HEAVY, "a bleed must not stop on its own");
            near(helper, "vanilla value follows the bleed", player.getHealth(), model.projectVanillaHealth(player), 0.01F);

            ItemStack stack = new ItemStack(RegistryObjects.TOURNIQUET.get());
            ItemHealing tourniquet = (ItemHealing) stack.getItem();
            helper.assertTrue(tourniquet.canTreat(model.LEFT_ARM), "tourniquet should treat a bleeding arm");
            helper.assertFalse(tourniquet.canTreat(model.RIGHT_ARM), "tourniquet should not treat a healthy arm");
            model.BODY.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;
            helper.assertFalse(tourniquet.canTreat(model.BODY), "a tourniquet cannot be tied around the body");
            model.BODY.bleedLevel = AbstractDamageablePart.BLEED_NONE;

            model.LEFT_ARM.activeHealer = tourniquet.createNewHealer(stack);
            helper.runAfterDelay(70, () -> {
                helper.assertTrue(model.LEFT_ARM.bleedLevel == AbstractDamageablePart.BLEED_NONE, "the tourniquet should have stopped the bleed");
                helper.assertTrue(model.LEFT_ARM.activeHealer == null, "treatment should be finished");
                leave(helper, player);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bleedingOutKillsWithTheBleedDeathMessage(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        model.HEAD.currentHealth = 0.4F;
        model.BODY.currentHealth = 0.4F;
        model.HEAD.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;
        model.BODY.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;

        helper.runAfterDelay(30, () -> {
            helper.assertFalse(player.isAlive(), "bleeding out of both vital parts must kill");
            // The combat tracker is cleared once the player is dead, so read the message that was sent to the client.
            String message = SENT_PACKETS.get(player.getUUID()).stream()
                    .filter(ClientboundPlayerCombatKillPacket.class::isInstance)
                    .map(packet -> ((ClientboundPlayerCombatKillPacket) packet).message().getString())
                    .findFirst().orElse("<no death packet>");
            helper.assertTrue(message.contains("bled out"), "death message should name the bleed, was: " + message);
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void fractureCripplesLegsUntilSplinted(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double base = speed.getValue();
        model.LEFT_LEG.fractured = true;

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(speed.getValue() < base * 0.8D, "a broken leg should slow the player: " + base + " -> " + speed.getValue());

            ItemStack stack = new ItemStack(RegistryObjects.SPLINT.get());
            ItemHealing splint = (ItemHealing) stack.getItem();
            helper.assertTrue(splint.canTreat(model.LEFT_LEG), "splint should treat the broken leg");
            helper.assertFalse(splint.canTreat(model.RIGHT_LEG), "splint should not treat a healthy leg");
            model.LEFT_LEG.activeHealer = splint.createNewHealer(stack);

            helper.runAfterDelay(100, () -> {
                helper.assertFalse(model.LEFT_LEG.fractured, "the splint should have fixed the fracture");
                helper.assertTrue(Math.abs(speed.getValue() - base) < 1.0E-6D, "speed should be back to normal: " + speed.getValue());
                leave(helper, player);
                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ wound care (marker realistic-wound-care)

    @GameTest(template = EMPTY)
    public static void woundCareRules(GameTestHelper helper) {
        // 2 h at 20x compression is 6 game minutes
        helper.assertTrue(InjuryEngine.compressedTicks(InjuryEngine.TOURNIQUET_SAFE_REAL_SECONDS, 20.0D) == 6 * 60 * 20, "compressed safe time");
        helper.assertTrue(InjuryEngine.ischemiaStage(-1, 100, 300) == 0 && InjuryEngine.ischemiaStage(99, 100, 300) == 0, "safe");
        helper.assertTrue(InjuryEngine.ischemiaStage(100, 100, 300) == 1 && InjuryEngine.ischemiaStage(300, 100, 300) == 2, "ischemia, loss");

        PlayerDamageModel model = new PlayerDamageModel();
        AbstractDamageablePart leg = model.LEFT_LEG;
        leg.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;
        helper.assertTrue(InjuryEngine.applyTourniquet(leg), "tourniquet on a bleeding leg");
        helper.assertTrue(leg.bleedLevel == AbstractDamageablePart.BLEED_NONE && leg.dressedWound == AbstractDamageablePart.BLEED_HEAVY && leg.tourniquetTicks == 0, "bleed stops under the tourniquet");
        helper.assertFalse(InjuryEngine.convertTourniquet(leg, AbstractDamageablePart.BLEED_LIGHT), "a bandage cannot take a tourniquet off a heavy wound");
        helper.assertTrue(InjuryEngine.convertTourniquet(leg, AbstractDamageablePart.BLEED_HEAVY), "hemostatic gauze converts it");
        helper.assertTrue(!leg.hasTourniquet() && leg.dressedWound == AbstractDamageablePart.BLEED_HEAVY, "converted: dressed, no tourniquet");
        helper.assertTrue(InjuryEngine.suture(leg) && leg.dressedWound == AbstractDamageablePart.BLEED_NONE, "suture closes the wound");
        helper.assertFalse(InjuryEngine.applyTourniquet(model.BODY), "no tourniquet on the torso");

        // destroyed limb: no healing, surgery brings it back at a reduced maximum
        AbstractDamageablePart arm = model.RIGHT_ARM;
        arm.currentHealth = 0.0F;
        helper.assertTrue(arm.isDestroyed(), "empty limb is destroyed");
        arm.heal(30.0F, null, false);
        helper.assertTrue(arm.currentHealth == 0.0F, "heal() refuses a destroyed limb");
        helper.assertTrue(InjuryEngine.surgery(arm, 0.3F), "surgery");
        near(helper, "after surgery", arm.currentHealth, InjuryEngine.SURGERY_RESTORED_HEALTH, 0.001F);
        arm.heal(1000.0F, null, false);
        near(helper, "capped at the reduced maximum", arm.currentHealth, arm.getMaxHealth() * 0.7F, 0.01F);

        // the new state is saved
        model.LEFT_FOOT.tourniquetTicks = 1234;
        model.LEFT_FOOT.dressedWound = AbstractDamageablePart.BLEED_LIGHT;
        PlayerDamageModel loaded = new PlayerDamageModel();
        loaded.deserializeNBT(model.serializeNBT());
        helper.assertTrue(loaded.LEFT_FOOT.tourniquetTicks == 1234 && loaded.LEFT_FOOT.dressedWound == AbstractDamageablePart.BLEED_LIGHT, "tourniquet/wound saved");
        near(helper, "surgery penalty saved", loaded.RIGHT_ARM.surgeryPenalty, 0.3F, 0.0001F);
        helper.assertTrue(loaded.HEAD.tourniquetTicks == -1, "no phantom tourniquet");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void medkitPaysForBleedThenHitPoints(GameTestHelper helper) {
        ichttt.mods.firstaid.common.items.ItemMedkit afak = (ichttt.mods.firstaid.common.items.ItemMedkit) RegistryObjects.AFAK.get();
        ichttt.mods.firstaid.common.items.ItemMedkit car = (ichttt.mods.firstaid.common.items.ItemMedkit) RegistryObjects.CAR_FIRST_AID_KIT.get();
        PlayerDamageModel model = new PlayerDamageModel();
        AbstractDamageablePart body = model.BODY;
        body.currentHealth = body.getMaxHealth() - 40.0F;
        body.bleedLevel = AbstractDamageablePart.BLEED_HEAVY;

        var plan = afak.plan(body, 400);
        helper.assertTrue(plan.closeBleed() == AbstractDamageablePart.BLEED_HEAVY && plan.healHp() == 40, "full kit: heavy bleed then 40 HP, was " + plan);
        helper.assertTrue(plan.cost(afak) == 170 + 40, "cost = heavy bleed + hit points");
        var poor = afak.plan(body, 100);
        helper.assertTrue(poor.closeBleed() == AbstractDamageablePart.BLEED_NONE && poor.healHp() == 40, "too little left for the bleed: only hit points");
        helper.assertFalse(car.canTreat(model.HEAD) , "nothing to do on a full head");
        body.currentHealth = body.getMaxHealth();
        helper.assertFalse(car.plan(body, 220).cost(car) > 0, "a car kit cannot close a heavy bleed");
        model.LEFT_ARM.currentHealth = 0.0F;
        helper.assertFalse(afak.canTreat(model.LEFT_ARM), "no medkit on a destroyed limb");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void dressedWoundReopensOnAHitNotOnItsOwn(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel after = model(helper, player);
        after.LEFT_ARM.dressedWound = AbstractDamageablePart.BLEED_HEAVY;
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(after.LEFT_ARM.bleedLevel == AbstractDamageablePart.BLEED_NONE, "a dressed wound does not reopen by itself");
            PlayerDamageModel before = new PlayerDamageModel();
            before.deserializeNBT(after.serializeNBT());
            after.LEFT_ARM.currentHealth -= 2.0F;
            InjuryEngine.onDamaged(player, after, before, player.damageSources().generic());
            helper.assertTrue(after.LEFT_ARM.bleedLevel == AbstractDamageablePart.BLEED_HEAVY && after.LEFT_ARM.dressedWound == AbstractDamageablePart.BLEED_NONE, "a hit reopens it");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void tourniquetLeftOnTooLongKillsTheLimb(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerDamageModel model = model(helper, player);
        int safe = InjuryEngine.compressedTicks(InjuryEngine.TOURNIQUET_SAFE_REAL_SECONDS);
        int loss = InjuryEngine.compressedTicks(InjuryEngine.TOURNIQUET_LIMB_LOSS_REAL_SECONDS);
        model.RIGHT_LEG.tourniquetTicks = safe;
        model.LEFT_LEG.tourniquetTicks = loss - 1;
        float start = model.RIGHT_LEG.currentHealth;
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(model.RIGHT_LEG.currentHealth < start, "past the safe time the limb loses hit points");
            helper.assertTrue(model.LEFT_LEG.isDestroyed(), "past the loss time the limb is destroyed");
            leave(helper, player);
            helper.succeed();
        });
    }
}
