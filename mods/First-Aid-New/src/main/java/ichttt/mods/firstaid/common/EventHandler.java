/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.damage.HitProfile;
import ichttt.mods.firstaid.api.damage.HitProfiles;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.health.HealthUnits;
import ichttt.mods.firstaid.common.init.FirstAidDataAttachments;
import ichttt.mods.firstaid.common.network.MessageSyncCommandSettings;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.PlayerSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.UUID;

public class EventHandler {
    public static final Random RAND = new Random();
    private static final EntityDimensions PLAYER_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 1.0F);
    private static final int RESCUE_DURATION_TICKS = PlayerDamageModel.getRescueDurationTicks();
    private static final int DEFIBRILLATOR_RESCUE_DURATION_TICKS = PlayerDamageModel.getDefibrillatorRescueDurationTicks();
    private static final int EXECUTION_DURATION_TICKS = PlayerDamageModel.getExecutionDurationTicks();

    /** Damage types in this tag always use the recorded hit location, even while friendly random distribution is on. */
    public static final TagKey<DamageType> PRECISE_HIT = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "precise_hit"));
    public static final Map<Player, ProjectileHitContext> hitList = new WeakHashMap<>();
    private static final Map<UUID, RescueProgress> rescueProgress = new HashMap<>();
    private static final Map<UUID, ExecutionProgress> executionProgress = new HashMap<>();
    private static final IDamageDistributionAlgorithm FOOT_ONLY_DAMAGE_DISTRIBUTION = new StandardDamageDistributionAlgorithm(
            Collections.singletonMap(EquipmentSlot.FEET, CommonUtils.getPartListForSlot(EquipmentSlot.FEET)),
            false,
            true
    );

    private static void awardStarterRecipes(ServerPlayer player) {
        List<RecipeHolder<?>> recipes = Objects.requireNonNull(player.level().getServer())
                .getRecipeManager()
                .getRecipes()
                .stream()
                .filter(recipe -> FirstAid.MODID.equals(recipe.id().getNamespace()))
                .toList();
        if (!recipes.isEmpty()) {
            player.awardRecipes(recipes);
        }
    }

    public static Boolean preHandleCustomPlayerDamage(Player player, DamageSource source, float amountToDamage) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return null;
        if (isProtectedUnconsciousSuffocation(damageModel, source)) {
            hitList.remove(player);
            return Boolean.FALSE;
        }

        if (amountToDamage == Float.MAX_VALUE || Float.isNaN(amountToDamage) || amountToDamage == Float.POSITIVE_INFINITY) {
            damageModel.forEach(damageablePart -> damageablePart.currentHealth = 0F);
            if (player instanceof ServerPlayer serverPlayer)
                CommonUtils.syncDamageModel(serverPlayer);
            CommonUtils.killPlayer(damageModel, player, source);
            hitList.remove(player);
            return Boolean.TRUE;
        }
        return null;
    }

    public static boolean handleCustomPlayerDamage(Player player, DamageSource source, float amountToDamage) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return false;
        boolean addStat = amountToDamage < 3.4028235E37F;
        HitProfile hitProfile = HitProfiles.resolve(source);
        if (hitProfile != null && addStat) {
            amountToDamage *= hitProfile.damageMultiplier();
        }
        IDamageDistributionAlgorithm damageDistribution = getForcedDamageDistribution(source);
        boolean hasForcedDamageDistribution = damageDistribution != null;
        if (damageDistribution == null) {
            damageDistribution = FirstAidRegistryLookups.getDamageDistributions(source.type());
        }

        if (isProjectileDamage(source) && !hasForcedDamageDistribution) {
            Entity directEntity = source.getDirectEntity();
            // The hit context is kept until the end of the tick: one gun shot can hurt twice (normal + armor piercing part)
            // and both parts have to land on the same limb.
            ProjectileHitContext knownHit = hitList.get(player);
            boolean preciseHit = knownHit != null && knownHit.projectile() == directEntity && source.is(PRECISE_HIT);
            if (FirstAid.shouldUseFriendlyRandomDistribution() && !preciseHit) {
                damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_NOKILL;
            } else {
                ProjectileHitContext projectileHitContext = knownHit;
                if (projectileHitContext != null && projectileHitContext.projectile() == directEntity) {
                    IDamageDistributionAlgorithm projectileDistribution = PlayerSizeHelper.getProjectileDistribution(player, projectileHitContext.hitPosition());
                    if (projectileDistribution != null) {
                        damageDistribution = projectileDistribution;
                    }
                }

                if (damageDistribution == null && directEntity != null) {
                    EquipmentSlot slot = PlayerSizeHelper.getSlotTypeForProjectileHit(directEntity, player);
                    if (slot != null) {
                        damageDistribution = new StandardDamageDistributionAlgorithm(Collections.singletonMap(slot, CommonUtils.getPartListForSlot(slot)), false, true);
                    }
                }
                if (damageDistribution == null) {
                    damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
                }
            }
        }
        if (damageDistribution == null) {
            // No given distribution found, and no projectile distribution either. Let's check if we can tell by the source where we should apply the damage, otherwise fall back to random
            if (isMeleeDamageSource(source)) {
                if (FirstAid.shouldUseFriendlyRandomDistribution()) {
                    damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_NOKILL;
                } else {
                    damageDistribution = PlayerSizeHelper.getMeleeDistribution(player, source);
                    if (damageDistribution == null) {
                        damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
                    }
                }
            } else {
                damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
            }
        }

        IDamageDistributionAlgorithm finalDamageDistribution = damageDistribution;
        float finalAmountToDamage = amountToDamage;
        boolean redistributeLeftoverDamage = shouldRedistributeLeftoverDamage(source);
        CommonUtils.runWithoutSetHealthInterception(
                () -> HitProfiles.run(hitProfile, () -> DamageDistribution.handleDamageTaken(finalDamageDistribution, damageModel, finalAmountToDamage, player, source, addStat, redistributeLeftoverDamage)));
        return true;
    }

    /**
     * Projectiles hit by their own movement logic (TACZ bullets never go through Projectile#onHit and their damage types
     * are not in the vanilla projectile tag) still count as projectiles for locational damage.
     */
    public static boolean isProjectileDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
    }

    public static IDamageDistributionAlgorithm getForcedDamageDistribution(DamageSource source) {
        return CommonUtils.isFootOnlyDamageSource(source) ? FOOT_ONLY_DAMAGE_DISTRIBUTION : null;
    }

    private static boolean isMeleeDamageSource(DamageSource source) {
        Entity causingEntity = source.getEntity();
        return causingEntity != null && causingEntity == source.getDirectEntity() && causingEntity instanceof LivingEntity;
    }

    private static boolean shouldRedistributeLeftoverDamage(DamageSource source) {
        return !CommonUtils.isFootOnlyDamageSource(source);
    }

    @SubscribeEvent(priority =  EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        HitResult result = event.getRayTraceResult();
        if (result.getType() != HitResult.Type.ENTITY)
            return;

        Entity entity = ((EntityHitResult) result).getEntity();
        if (!entity.level().isClientSide() && entity instanceof Player) {
            recordProjectileHit((Player) entity, event.getEntity(), result.getLocation());
        }
    }

    public static void recordProjectileHit(Player player, Entity projectile, Vec3 hitPosition) {
        hitList.put(player, new ProjectileHitContext(projectile, getProjectileHitPosition(player, projectile, hitPosition)));
    }

    private static Vec3 getProjectileHitPosition(Player player, Entity projectile, Vec3 fallbackHitPosition) {
        AABB playerBox = player.getBoundingBox();
        Vec3 currentPosition = projectile.position();
        Vec3 previousTickPosition = new Vec3(projectile.xo, projectile.yo, projectile.zo);
        Optional<Vec3> previousTickHit = playerBox.clip(previousTickPosition, currentPosition);
        if (previousTickHit.isPresent()) {
            return previousTickHit.get();
        }

        Vec3 previousPosition = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
        Optional<Vec3> previousHit = playerBox.clip(previousPosition, currentPosition);
        if (previousHit.isPresent()) {
            return previousHit.get();
        }

        Vec3 nextPosition = currentPosition.add(projectile.getDeltaMovement());
        return playerBox.clip(currentPosition, nextPosition).orElse(fallbackHitPosition);
    }

    private record ProjectileHitContext(Entity projectile, Vec3 hitPosition) {
    }

    @SubscribeEvent
    public static void tickPlayersPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.getAbilities().invulnerable || !player.isAlive()) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(player);
        if (damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious()) {
            // Zero motion before movement/action systems run this tick.
            restrictUnconsciousMovement(player, playerDamageModel);
        }
    }

    @SubscribeEvent
    public static void tickPlayers(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.getAbilities().invulnerable) {
            if (!player.isAlive()) return;
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            if (!player.level().isClientSide() && damageModel instanceof PlayerDamageModel playerDamageModel) {
                float nearMissStrength = getNearbyProjectileStrength(player);
                if (nearMissStrength > 0.0F) {
                    playerDamageModel.registerAdrenalineNearMiss(player, nearMissStrength);
                }
                if (playerDamageModel.isUnconscious()) {
                    clearAttackTargetsAround(player, 24.0D);
                    restrictUnconsciousMovement(player, playerDamageModel);
                }
            } else if (player.level().isClientSide()
                    && damageModel instanceof PlayerDamageModel clientModel
                    && clientModel.isUnconscious()) {
                // Also damp client-predicted motion so ability mods (e.g. Parcool) cannot slide the player.
                restrictUnconsciousMovement(player, clientModel);
            }
            damageModel.tick(player.level(), player);
            if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                tickRescueProgress(serverPlayer);
                tickExecutionProgress(serverPlayer);
            }
            hitList.remove(player);
        }
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (ModList.get().isLoaded("morpheus")) return;
        for (Player player : event.getLevel().players()) {
            if (player.isSleepingLongEnough()) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                damageModel.sleepHeal(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation tableName = event.getName();
        int bandage, plaster, morphine;
        NumberProvider bandageMax  = UniformGenerator.between(1, 3);
        NumberProvider plasterMax  = UniformGenerator.between(1, 5);
        NumberProvider morphineMax = UniformGenerator.between(1, 2);
        NumberProvider poolRolls   = ConstantValue.exactly(1.0F);
        if (tableName.equals(BuiltInLootTables.SPAWN_BONUS_CHEST)) {
            bandage = 8;
            plaster = 16;
            morphine = 4;
            morphineMax = ConstantValue.exactly(1);
        } else if (tableName.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR) || tableName.equals(BuiltInLootTables.STRONGHOLD_CROSSING) || tableName.equals(BuiltInLootTables.ABANDONED_MINESHAFT)) {
            bandage = 20;
            plaster = 24;
            morphine = 8;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (tableName.equals(BuiltInLootTables.VILLAGE_BUTCHER)) {
            bandage = 4;
            plaster = 20;
            morphine = 2;
            plasterMax = UniformGenerator.between(3, 8);
        } else if (tableName.equals(BuiltInLootTables.IGLOO_CHEST)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (tableName.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            bandageMax = UniformGenerator.between(1, 2);
            plasterMax = UniformGenerator.between(1, 3);
            morphineMax = ConstantValue.exactly(1);
            poolRolls = UniformGenerator.between(0, 1);
        } else {
            return;
        }
        LootPool.Builder builder = LootPool.lootPool().name("firstaid_main").setRolls(poolRolls);
        builder.add(LootItem.lootTableItem(RegistryObjects.BANDAGE::get)
                    .apply(SetItemCountFunction.setCount(bandageMax))
                    .setWeight(bandage)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.PLASTER::get)
                    .apply(SetItemCountFunction.setCount(plasterMax))
                    .setWeight(plaster)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.MORPHINE::get)
                    .apply(SetItemCountFunction.setCount(morphineMax))
                    .setWeight(morphine)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.TOURNIQUET::get)
                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
                    .setWeight(Math.max(1, bandage / 3))
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.SPLINT::get)
                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
                    .setWeight(Math.max(1, plaster / 4))
                    .setQuality(0));
        event.getTable().addPool(builder.build());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.isDeadOrDying() || !CommonUtils.hasDamageModel(entity))
            return;
        event.setCanceled(true);
        if (entity.level().isClientSide() || !FirstAidConfig.SERVER.allowOtherHealingItems.get())
            return;
        float amount = event.getAmount();
        //Hacky shit to reduce vanilla regen
        boolean fromFood = HealthDistribution.IN_FOOD_TICK.get();
        if (fromFood) {
            amount = amount * (float) (double) FirstAidConfig.SERVER.naturalRegenMultiplier.get();
        } else {
            amount = amount * (float) (double) FirstAidConfig.SERVER.otherRegenMultiplier.get();
        }
        // Healing from other mods is expressed in vanilla health units, limb hit points are not.
        amount = HealthUnits.toEngine(amount, (Player) entity, CommonUtils.getDamageModel((Player) entity));
        if (FirstAidConfig.GENERAL.debug.get()) {
            CommonUtils.debugLogStacktrace("External healing: : " + amount);
        }
        if (fromFood) {
            HealthDistribution.applyNaturalRegen(amount, (Player) entity, true);
        } else {
            HealthDistribution.distributeHealth(amount, (Player) entity, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            FirstAid.LOGGER.debug("Sending damage model to {}", event.getEntity().getName());
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(event.getEntity());
            if (damageModel == null) return;
            if (damageModel.hasTutorial)
                CapProvider.tutorialDone.add(event.getEntity().getName().getString());
            ServerPlayer playerMP = (ServerPlayer) event.getEntity();
            awardStarterRecipes(playerMP);
            CommonUtils.syncDamageModel(playerMP);
            FirstAid.NETWORKING.sendCommandSettingsSync(playerMP, MessageSyncCommandSettings.current());
            sendOpCommandTip(playerMP);
        }
    }

    @SubscribeEvent(priority =  EventPriority.LOW)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        hitList.remove(event.getEntity());
        rescueProgress.remove(event.getEntity().getUUID());
        executionProgress.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        LevelAccessor world = event.getLevel();
        if (!world.isClientSide() && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION)
                    .set(FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF, serverLevel.getServer());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player instanceof ServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            CommonUtils.syncDamageModel((ServerPlayer) player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player rescuer = event.getEntity();
        if (isUnconscious(rescuer)) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        cancelIfUnconscious(event);
    }

    /**
     * Milk clears vanilla effects; also clear FirstAid morphine ticks / pending so status cannot stick at 00:00.
     */
    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isMilkConsumption(event.getItem(), player)) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.clearPainSuppressants(player);
        }
    }

    private static boolean isMilkConsumption(ItemStack stack, Player player) {
        if (stack != null && !stack.isEmpty() && stack.is(Items.MILK_BUCKET)) {
            return true;
        }
        ItemStack useItem = player.getUseItem();
        return useItem != null && !useItem.isEmpty() && useItem.is(Items.MILK_BUCKET);
    }

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        cancelIfUnconscious(event);
    }

    @SubscribeEvent
    public static void onBlockAttack(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfUnconscious(event);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void adjustHitboxSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && !entity.isPassenger()) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(player);
            if (damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious()) {
                event.setNewSize(PlayerDamageModel.getUnconsciousDimensions(playerDamageModel.shouldUseCrampedUnconsciousDimensions(player)));
            }
        }
    }

    @SubscribeEvent
    public static void onSetAttackTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget instanceof Player player && isUnconscious(player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            FirstAidRegistryLookups.init(event.getRegistryAccess(), event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FirstAidConfig.applyCommandSettings();
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        FirstAid.LOGGER.debug("Cleaning up");
        FirstAid.dynamicPainEnabled = false;
        FirstAid.mildPainLevel = 1;
        FirstAid.enablePainVignette = true;
        FirstAid.enablePainBlur = true;
        FirstAid.enablePainFovCompression = true;
        FirstAid.enablePainAudioEffects = true;
        FirstAid.lowSuppressionEnabled = true;
        FirstAid.projectileSuppressionEnabled = true;
        FirstAid.lowSuppressionMultiplier = 0.4F;
        FirstAid.rescueWakeUpEnabled = true;
        FirstAid.rescueWakeUpDelaySeconds = FirstAid.DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS;
        FirstAid.naturalRegenMode = FirstAid.NaturalRegenMode.LIMITED;
        FirstAid.naturalRegenStrategy = FirstAid.NaturalRegenStrategy.CRITICAL;
        FirstAid.naturalRegenLimitRatio = 0.85F;
        FirstAid.naturalRegenCriticalPriorityRatio = 0.85F;
        FirstAid.medicineEffectMode = FirstAid.MedicineEffectMode.REALISTIC;
        FirstAid.medicineTimingMultiplier = 1.0F;
        FirstAid.morphineActivationDelaySeconds = FirstAid.DEFAULT_MORPHINE_ACTIVATION_DELAY_SECONDS;
        FirstAid.painkillerActivationDelaySeconds = FirstAid.DEFAULT_PAINKILLER_ACTIVATION_DELAY_SECONDS;
        FirstAid.injuryDebuffMode = FirstAid.InjuryDebuffMode.NORMAL;
        FirstAid.lowInjuryDebuffDamageScale = 0.4F;
        FirstAid.lowInjuryDebuffAmplifierScale = 0.5F;
        FirstAid.lowInjuryDebuffDurationScale = 0.5F;
        FirstAid.injuryDebuffOverrides.clear();
        FirstAid.setSuppressionEntityBlacklist(FirstAid.getDefaultSuppressionEntityBlacklist());
        CapProvider.tutorialDone.clear();
        EventHandler.hitList.clear();
        rescueProgress.clear();
        executionProgress.clear();
        FirstAidRegistryLookups.reset();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        DebugDamageCommand.register(event.getDispatcher());
        FirstAidCommand.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (!event.isEndConquered() && !player.level().isClientSide() && player instanceof ServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            damageModel.runScaleLogic(player);
            // Respawn is a new body: surgery penalties go and destroyed limbs come back (heal() alone would refuse them)
            damageModel.forEach(damageablePart -> {
                damageablePart.surgeryPenalty = 0F;
                damageablePart.currentHealth = damageablePart.getMaxHealth();
            });
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                playerDamageModel.clearStatusEffects();
                playerDamageModel.clearInjuries();
                playerDamageModel.beginAudioMute(80);
            }
            damageModel.scheduleResync();
        }
    }

    private static float getNearbyProjectileStrength(Player player) {
        if (!FirstAid.projectileSuppressionEnabled) {
            return 0.0F;
        }

        AABB scanBox = player.getBoundingBox().inflate(3.25D);
        AABB playerBox = player.getBoundingBox().inflate(0.12D);
        Vec3 eyePosition = player.getEyePosition();
        Vec3 torsoPosition = player.position().add(0.0D, player.getBbHeight() * 0.6D, 0.0D);
        float strongest = 0.0F;
        List<Projectile> projectiles = player.level().getEntitiesOfClass(Projectile.class, scanBox, projectile -> {
            if (!projectile.isAlive() || projectile.getOwner() == player) {
                return false;
            }
            if (FirstAid.isSuppressionBlacklisted(projectile)) {
                return false;
            }
            return projectile.getDeltaMovement().lengthSqr() >= 0.02D;
        });
        for (Projectile projectile : projectiles) {
            Vec3 currentPosition = projectile.position();
            Vec3 previousPosition = currentPosition.subtract(projectile.getDeltaMovement());
            Vec3 endPosition = currentPosition.add(projectile.getDeltaMovement());
            if (playerBox.intersects(projectile.getBoundingBox()) || playerBox.clip(previousPosition, endPosition).isPresent()) {
                continue;
            }
            strongest = Math.max(strongest, getNearMissStrength(player, projectile, previousPosition, endPosition, eyePosition));
            strongest = Math.max(strongest, getNearMissStrength(player, projectile, previousPosition, endPosition, torsoPosition));
        }
        return strongest;
    }

    private static float getNearMissStrength(Player player, Projectile projectile, Vec3 start, Vec3 end, Vec3 target) {
        ClosestPointResult closestPointResult = closestPointOnSegment(start, end, target);
        if (closestPointResult.progress <= 0.0D || closestPointResult.progress >= 1.0D) {
            return 0.0F;
        }
        double distance = closestPointResult.point.distanceTo(target);
        if (distance > 1.85D) {
            return 0.0F;
        }
        BlockHitResult hitResult = player.level().clip(new ClipContext(closestPointResult.point, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hitResult.getType() != HitResult.Type.MISS) {
            return 0.0F;
        }
        double speed = projectile.getDeltaMovement().length();
        float speedFactor = Mth.clamp((float) ((speed - 0.18D) / 0.65D), 0.0F, 1.0F);
        float distanceFactor = Mth.clamp(1.32F - (float) (distance / 1.85D), 0.0F, 1.0F);
        return Mth.clamp(distanceFactor * (0.82F + 0.58F * speedFactor), 0.0F, 1.45F);
    }

    private static ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7D) {
            return new ClosestPointResult(start, 0.0D);
        }
        double progress = Mth.clamp(target.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return new ClosestPointResult(start.add(segment.scale(progress)), progress);
    }

    private static <T extends PlayerInteractEvent & ICancellableEvent> void cancelIfUnconscious(T event) {
        if (isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isUnconscious(Player player) {
        return isUnconscious(player, true);
    }

    private static boolean isUnconscious(Player player, boolean allowCreate) {
        AbstractPlayerDamageModel damageModel = allowCreate
                ? CommonUtils.getDamageModel(player)
                : CommonUtils.getExistingDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious();
    }

    /**
     * Stops vanilla and ability-mod mobility while the player is downed.
     * During the critical crawl window, allows slow horizontal movement only.
     */
    private static void restrictUnconsciousMovement(Player player, PlayerDamageModel playerDamageModel) {
        player.setSprinting(false);
        player.setJumping(false);
        Vec3 motion = player.getDeltaMovement();
        double y = Math.min(0.0D, motion.y);
        if (playerDamageModel.canCrawlWhileDowned()) {
            double maxHorizontal = 0.10D * playerDamageModel.getCrawlSpeedFactor() / 0.28D;
            maxHorizontal = Math.max(0.06D, Math.min(0.12D, maxHorizontal));
            Vec3 projected = projectForwardCrawlMotion(player, motion.x, y, motion.z, maxHorizontal);
            if (projected.x != motion.x || projected.z != motion.z || motion.y > 0.0D) {
                player.setDeltaMovement(projected);
            }
            player.xxa = 0.0F;
            if (player.zza < 0.0F) {
                player.zza = 0.0F;
            }
            player.yya = 0.0F;
            player.hasImpulse = true;
            player.hurtMarked = true;
            return;
        }
        if (motion.x != 0.0D || motion.z != 0.0D || motion.y > 0.0D) {
            player.setDeltaMovement(0.0D, y, 0.0D);
        }
        player.hasImpulse = true;
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.yya = 0.0F;
        player.hurtMarked = true;
    }

    private static Vec3 projectForwardCrawlMotion(Player player, double motionX, double motionY, double motionZ, double maxHorizontal) {
        Vec3 look = player.getLookAngle();
        double fx = look.x;
        double fz = look.z;
        double flen = Math.sqrt(fx * fx + fz * fz);
        if (flen < 1.0E-4D) {
            fx = 0.0D;
            fz = 1.0D;
            flen = 1.0D;
        }
        fx /= flen;
        fz /= flen;
        double along = motionX * fx + motionZ * fz;
        if (along < 0.0D) {
            along = 0.0D;
        }
        if (along > maxHorizontal) {
            along = maxHorizontal;
        }
        return new Vec3(fx * along, motionY, fz * along);
    }


    private static boolean isProtectedUnconsciousSuffocation(AbstractPlayerDamageModel damageModel, DamageSource source) {
        return damageModel instanceof PlayerDamageModel playerDamageModel
                && playerDamageModel.isUnconscious()
                && source.is(DamageTypes.IN_WALL);
    }

    private static void clearAttackTargetsAround(LivingEntity victim, double range) {
        LevelAccessor level = victim.level();
        if (!(level instanceof net.minecraft.world.level.Level gameLevel)) {
            return;
        }
        List<Mob> mobs = gameLevel.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(range));
        for (Mob mob : mobs) {
            if (mob.getTarget() == victim) {
                mob.setTarget(null);
                Brain<?> brain = mob.getBrain();
                eraseMemory(brain, MemoryModuleType.ANGRY_AT);
                eraseMemory(brain, MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    private static void eraseMemory(Brain<?> brain, MemoryModuleType<?> type) {
        if (brain.hasMemoryValue(type)) {
            brain.eraseMemory(type);
        }
    }

    private static void tickRescueProgress(ServerPlayer rescuer) {
        InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        ItemStack rescueStack = rescuer.getItemInHand(rescueTarget.hand());
        int rescueDurationTicks = getRescueDurationTicks(rescueStack);
        RescueProgress progress = rescueProgress.get(rescuer.getUUID());
        if (progress == null || !progress.matches(rescueTarget, rescueDurationTicks)) {
            progress = new RescueProgress(rescueTarget.target().getUUID(), rescueTarget.hand(), 0, rescueDurationTicks);
        }

        int nextTicks = Math.min(rescueDurationTicks, progress.ticks() + 1);
        if (nextTicks < rescueDurationTicks) {
            rescueProgress.put(rescuer.getUUID(), progress.withTicks(nextTicks));
            return;
        }

        completeRescue(rescuer, rescueTarget);
    }

    private static void tickExecutionProgress(ServerPlayer executor) {
        InteractionTarget executionTarget = findExecutionTarget(executor, true);
        if (executionTarget == null) {
            executionProgress.remove(executor.getUUID());
            return;
        }

        ExecutionProgress progress = executionProgress.get(executor.getUUID());
        if (progress == null || !progress.matches(executionTarget)) {
            progress = new ExecutionProgress(executionTarget.target().getUUID(), executionTarget.hand(), 0);
        }

        int nextTicks = Math.min(EXECUTION_DURATION_TICKS, progress.ticks() + 1);
        if (nextTicks < EXECUTION_DURATION_TICKS) {
            executionProgress.put(executor.getUUID(), progress.withTicks(nextTicks));
            return;
        }

        completeExecution(executor, executionTarget);
    }

    public static void attemptImmediateRescue(ServerPlayer rescuer) {
        InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        completeRescue(rescuer, rescueTarget);
    }

    public static void attemptImmediateExecution(ServerPlayer executor) {
        InteractionTarget executionTarget = findExecutionTarget(executor, true);
        if (executionTarget == null) {
            executionProgress.remove(executor.getUUID());
            return;
        }
        completeExecution(executor, executionTarget);
    }

    private static void completeRescue(ServerPlayer rescuer, InteractionTarget rescueTarget) {
        ItemStack stack = rescuer.getItemInHand(rescueTarget.hand());
        if (!isRescueItem(stack)) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(rescueTarget.target());
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        boolean usingDefibrillator = isDefibrillator(stack);
        if (usingDefibrillator) {
            stack.hurtAndBreak(1, rescuer, getEquipmentSlot(rescueTarget.hand()));
        } else {
            stack.shrink(1);
        }

        boolean rescued = usingDefibrillator
                ? playerDamageModel.defibrillatorRescueFromCriticalState(rescueTarget.target(), FirstAid.rescueWakeUpEnabled)
                : playerDamageModel.rescueFromCriticalState(rescueTarget.target(), null, FirstAid.rescueWakeUpEnabled);
        if (rescued) {
            rescuer.displayClientMessage(Component.translatable("firstaid.gui.rescue_other", rescueTarget.target().getDisplayName()).withStyle(ChatFormatting.GREEN), true);
            rescueTarget.target().displayClientMessage(Component.translatable("firstaid.gui.rescue_received", rescuer.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        }
        rescueProgress.remove(rescuer.getUUID());
    }

    private static void completeExecution(ServerPlayer executor, InteractionTarget executionTarget) {
        ItemStack stack = executor.getItemInHand(executionTarget.hand());
        if (!CommonUtils.isExecutionItem(stack)) {
            executionProgress.remove(executor.getUUID());
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(executionTarget.target());
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
            executionProgress.remove(executor.getUUID());
            return;
        }

        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(1, executor, getEquipmentSlot(executionTarget.hand()));
        }
        executor.displayClientMessage(Component.translatable("firstaid.gui.execute_other", executionTarget.target().getDisplayName()).withStyle(ChatFormatting.RED), true);
        executionTarget.target().displayClientMessage(Component.translatable("firstaid.gui.execute_received", executor.getDisplayName()).withStyle(ChatFormatting.RED), true);
        playerDamageModel.giveUp(executionTarget.target());
        executionProgress.remove(executor.getUUID());
    }

    private static InteractionTarget findRescueTarget(Player rescuer, boolean requireSneaking) {
        if (rescuer == null || rescuer.level().isClientSide() || isUnconscious(rescuer)) {
            return null;
        }
        if (requireSneaking && !rescuer.isCrouching()) {
            return null;
        }

        InteractionSelection selection = getInteractionSelection(rescuer);
        if (selection == null || selection.type() != InteractionType.RESCUE) {
            return null;
        }

        Player closestTarget = findClosestRescueTarget(rescuer);
        return closestTarget == null ? null : new InteractionTarget(closestTarget, selection.hand());
    }

    private static InteractionTarget findExecutionTarget(Player executor, boolean requireSneaking) {
        if (executor == null || executor.level().isClientSide() || isUnconscious(executor)) {
            return null;
        }
        if (requireSneaking && !executor.isCrouching()) {
            return null;
        }

        InteractionSelection selection = getInteractionSelection(executor);
        if (selection == null || selection.type() != InteractionType.EXECUTE) {
            return null;
        }

        Player closestTarget = findClosestRescueTarget(executor);
        return closestTarget == null ? null : new InteractionTarget(closestTarget, selection.hand());
    }

    private static Player findClosestRescueTarget(Player actor) {
        double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
        Player closestTarget = null;
        double closestDistanceSqr = maxDistanceSqr;
        for (Player candidate : actor.level().players()) {
            if (candidate == actor || !candidate.isAlive()) {
                continue;
            }
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(candidate);
            if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
                continue;
            }
            double distanceSqr = actor.distanceToSqr(candidate);
            if (distanceSqr > closestDistanceSqr) {
                continue;
            }
            closestDistanceSqr = distanceSqr;
            closestTarget = candidate;
        }
        return closestTarget;
    }


    public static void attemptSelfDefibrillator(ServerPlayer player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
            return;
        }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!isDefibrillator(stack)) {
            hand = InteractionHand.OFF_HAND;
            stack = player.getItemInHand(hand);
        }
        if (!isDefibrillator(stack)) {
            return;
        }
        stack.hurtAndBreak(1, player, getEquipmentSlot(hand));
        boolean rescued = playerDamageModel.defibrillatorRescueFromCriticalState(player, FirstAid.rescueWakeUpEnabled);
        if (rescued) {
            player.displayClientMessage(Component.translatable("firstaid.gui.self_defib_success").withStyle(ChatFormatting.GREEN), true);
        }
    }

    private static InteractionSelection getInteractionSelection(Player player) {
        if (isRescueItem(player.getMainHandItem())) {
            return new InteractionSelection(InteractionType.RESCUE, InteractionHand.MAIN_HAND);
        }
        if (CommonUtils.isExecutionItem(player.getMainHandItem())) {
            return new InteractionSelection(InteractionType.EXECUTE, InteractionHand.MAIN_HAND);
        }
        if (isRescueItem(player.getOffhandItem())) {
            return new InteractionSelection(InteractionType.RESCUE, InteractionHand.OFF_HAND);
        }
        if (CommonUtils.isExecutionItem(player.getOffhandItem())) {
            return new InteractionSelection(InteractionType.EXECUTE, InteractionHand.OFF_HAND);
        }
        return null;
    }

    private static boolean isRescueItem(ItemStack stack) {
        return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get()) || isDefibrillator(stack);
    }

    private static boolean isDefibrillator(ItemStack stack) {
        return stack.is(RegistryObjects.DEFIBRILLATOR.get());
    }

    private static int getRescueDurationTicks(ItemStack stack) {
        return isDefibrillator(stack) ? DEFIBRILLATOR_RESCUE_DURATION_TICKS : RESCUE_DURATION_TICKS;
    }

    private static EquipmentSlot getEquipmentSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private static void sendOpCommandTip(ServerPlayer player) {
        if (!FirstAidConfig.SERVER.commandTipsEnabled.get() || !player.hasPermissions(2)) {
            return;
        }
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.subheader").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(buildCommandTipLine(
                "firstaid.tip.commands.group.core",
                buildCommandTipChip("firstaid.tip.commands.pain.label", "firstaid.tip.commands.pain.detail", "/firstaid pain mild", ChatFormatting.AQUA),
                buildCommandTipChip("firstaid.tip.commands.suppression.label", "firstaid.tip.commands.suppression.detail", "/firstaid suppression dynamic", ChatFormatting.AQUA),
                buildCommandTipChip("firstaid.tip.commands.commandtips.label", "firstaid.tip.commands.commandtips.detail", "/firstaid commandtips off", ChatFormatting.GRAY),
                buildCommandTipChip("firstaid.tip.commands.medicineeffect.label", "firstaid.tip.commands.medicineeffect.detail", "/firstaid medicineeffect assisted", ChatFormatting.YELLOW)
        ), false);
        player.displayClientMessage(buildCommandTipLine(
                "firstaid.tip.commands.group.rescue",
                buildCommandTipChip("firstaid.tip.commands.naturalregen.label", "firstaid.tip.commands.naturalregen.detail", "/firstaid naturalregen limited critical", ChatFormatting.GREEN),
                buildCommandTipChip("firstaid.tip.commands.revivewakeup.label", "firstaid.tip.commands.revivewakeup.detail", "/firstaid revivewakeup on 15", ChatFormatting.GREEN)
        ), false);
        player.displayClientMessage(buildCommandTipLine(
                "firstaid.tip.commands.group.advanced",
                buildCommandTipChip("firstaid.tip.commands.injurydebuff.label", "firstaid.tip.commands.injurydebuff.detail", "/firstaid injurydebuff normal", ChatFormatting.GOLD),
                buildCommandTipChip("firstaid.tip.commands.randomdamage.label", "firstaid.tip.commands.randomdamage.detail", "/firstaid randomdamage friendly chance 80", ChatFormatting.GOLD),
                buildCommandTipChip("firstaid.tip.commands.addiction.label", "firstaid.tip.commands.addiction.detail", "/firstaid addiction set @s 0", ChatFormatting.LIGHT_PURPLE),
                buildCommandTipChip("firstaid.tip.commands.damagepart.label", "firstaid.tip.commands.damagepart.detail", "/damagePart HEAD 4", ChatFormatting.RED)
        ), false);
    }

    private static Component buildCommandTipLine(String sectionKey, MutableComponent... commandChips) {
        MutableComponent line = Component.translatable(sectionKey).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD);
        for (MutableComponent commandChip : commandChips) {
            line.append(Component.literal(" "));
            line.append(commandChip);
        }
        return line;
    }

    private static MutableComponent buildCommandTipChip(String labelKey, String detailKey, String suggestion, ChatFormatting color) {
        MutableComponent chip = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY);
        chip.append(Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(detailKey)))));
        chip.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        return chip;
    }

    private record ClosestPointResult(Vec3 point, double progress) {
    }

    private record RescueProgress(UUID targetId, InteractionHand hand, int ticks, int durationTicks) {
        private boolean matches(InteractionTarget rescueTarget, int rescueDurationTicks) {
            return targetId.equals(rescueTarget.target().getUUID()) && hand == rescueTarget.hand() && durationTicks == rescueDurationTicks;
        }

        private RescueProgress withTicks(int updatedTicks) {
            return new RescueProgress(targetId, hand, updatedTicks, durationTicks);
        }
    }

    private record ExecutionProgress(UUID targetId, InteractionHand hand, int ticks) {
        private boolean matches(InteractionTarget executionTarget) {
            return targetId.equals(executionTarget.target().getUUID()) && hand == executionTarget.hand();
        }

        private ExecutionProgress withTicks(int updatedTicks) {
            return new ExecutionProgress(targetId, hand, updatedTicks);
        }
    }

    private record InteractionTarget(Player target, InteractionHand hand) {
    }

    private record InteractionSelection(InteractionType type, InteractionHand hand) {
    }

    private enum InteractionType {
        RESCUE,
        EXECUTE
    }
}
