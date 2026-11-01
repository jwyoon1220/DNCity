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

package ichttt.mods.firstaid.common.damagesystem.distribution;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.damage.HitProfiles;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.event.FirstAidLivingDamageEvent;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.health.HealthUnits;
import ichttt.mods.firstaid.common.health.InjuryEngine;
import ichttt.mods.firstaid.common.init.FirstAidDataAttachments;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class DamageDistribution implements IDamageDistributionAlgorithm {

    public static float handleDamageTaken(IDamageDistributionAlgorithm damageDistribution, AbstractPlayerDamageModel damageModel, float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat, boolean redistributeIfLeft) {
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- Damaging {} using {} for dmg source {}, redistribute {}, addStat {} ---", damage, damageDistribution.toString(), source.type().msgId(), redistributeIfLeft, addStat);
        }
        PlayerDamageModel before = damageModel instanceof PlayerDamageModel limbModel ? limbModel.snapshotLimbs() : null;
        CompoundTag beforeCache = before == null ? damageModel.serializeNBT() : null;
        if (!damageDistribution.skipGlobalPotionModifiers())
            damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
        //VANILLA COPY - combat tracker and exhaustion
        if (damage != 0.0F) {
            player.causeFoodExhaustion(source.getFoodExhaustion());
            player.getCombatTracker().recordDamage(source, damage);
        }

        float left = damageDistribution.distributeDamage(damage, player, source, addStat);
        if (left > 0 && redistributeIfLeft) {
            boolean hasTriedNoKill = isNoKillRandomDistribution(damageDistribution);
            damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
            left = damageDistribution.distributeDamage(left, player, source, addStat);
            if (left > 0 && !hasTriedNoKill) {
                left = damageDistribution.distributeDamage(left, player, source, addStat);
            }
        }
        if (before == null) {
            before = new PlayerDamageModel();
            before.deserializeNBT(beforeCache);
        }
        FirstAidLivingDamageEvent event = new FirstAidLivingDamageEvent(player, damageModel, before, source, left);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            if (damageModel instanceof PlayerDamageModel limbModel) {
                limbModel.restoreLimbs(before); //restore prev state
            } else {
                damageModel.deserializeNBT(beforeCache);
            }
            syncOnce(player, damageModel);
            if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! Event got canceled ---");
            }
            return 0F;
        }

        InjuryEngine.onDamaged(player, damageModel, before, source);
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.registerDamageFeedback(player, source, before);
            playerDamageModel.handlePostDamage(player, source);
            playerDamageModel.syncVanillaHealth(player);
        }
        if (damageModel.isDead(player))
            CommonUtils.killPlayer(damageModel, player, source);
        syncOnce(player, damageModel);
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! {} still left ---", left);
        }
        return left;
    }

    /** One client sync per hit instead of one per damaged part (every sync serializes the whole model). */
    private static void syncOnce(Player player, AbstractPlayerDamageModel damageModel) {
        if (player instanceof ServerPlayer serverPlayer) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    private static boolean isNoKillRandomDistribution(IDamageDistributionAlgorithm damageDistribution) {
        return damageDistribution instanceof RandomDamageDistributionAlgorithm randomDamageDistribution && randomDamageDistribution.isNoKill();
    }

    protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart part) {
        return 0F;
    }

    static float getIncomingPartDamageMultiplier(AbstractPlayerDamageModel damageModel, AbstractDamageablePart part) {
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            return playerDamageModel.getIncomingPartDamageMultiplier(part);
        }
        return 1.0F;
    }

    static float restoreOriginalDamageScale(float scaledDamage, float damageMultiplier) {
        if (damageMultiplier <= 0.0F) {
            return 0.0F;
        }
        return scaledDamage / damageMultiplier;
    }

    static float consumeGlobalAbsorption(Player player, float damage) {
        if (damage <= 0.0F) {
            return 0.0F;
        }
        float absorption = player.getAbsorptionAmount();
        if (absorption <= 0.0F) {
            return damage;
        }
        if (absorption >= damage) {
            player.setAbsorptionAmount(absorption - damage);
            return 0.0F;
        }
        player.setAbsorptionAmount(0.0F);
        return damage - absorption;
    }

    protected float distributeDamageOnParts(float damage, @Nonnull AbstractPlayerDamageModel damageModel, @Nonnull EnumPlayerPart[] enumParts, @Nonnull Player player, boolean addStat) {
        ArrayList<AbstractDamageablePart> damageableParts = new ArrayList<>(enumParts.length);
        for (EnumPlayerPart part : enumParts) {
            damageableParts.add(damageModel.getFromEnum(part));
        }
        Collections.shuffle(damageableParts);
        // The damage that arrives here is in vanilla units (it already went through armor math tuned for them);
        // limb hit points are engine units, so the conversion happens right where hit points are removed.
        float unit = HealthUnits.engineHpPerVanillaHp(player, damageModel);
        float overkillFactor = HitProfiles.current().overkillFactorOr(FirstAidConfig.SERVER.limbOverkillFactor.get().floatValue());
        for (AbstractDamageablePart part : damageableParts) {
            float minHealth = minHealth(player, part);
            float damageMultiplier = getIncomingPartDamageMultiplier(damageModel, part);
            float scaledDamage = damage * damageMultiplier * unit;
            float scaledLeft = part.damage(scaledDamage, player, !player.hasEffect(RegistryObjects.PAINKILLER_EFFECT), minHealth);
            float scaledDamageDone = scaledDamage - scaledLeft;
            float dmgConsumed = Math.min(damage, restoreOriginalDamageScale(scaledDamageDone, damageMultiplier * unit));
            if (addStat)
                player.awardStat(Stats.DAMAGE_TAKEN, Math.round(scaledDamageDone / unit * 10.0F));
            damage = Math.max(0.0F, damage - dmgConsumed);
            if (damage == 0)
                break;
            // A limb that is gone swallows part of the excess instead of passing all of it on (head/body keep their rules)
            if (!part.canCauseDeath && part.currentHealth <= minHealth + 0.001F) {
                damage *= overkillFactor;
                if (damage <= 0.0F)
                    break;
            }
        }
        return damage;
    }

    @Nonnull
    protected abstract List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList();

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        if (damage <= 0F) return 0F;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return 0F;
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Starting distribution of {} damage...", damage);
        }
        damage = consumeGlobalAbsorption(player, damage);
        if (damage <= 0.0F) {
            return 0.0F;
        }
        for (Pair<EquipmentSlot, EnumPlayerPart[]> pair : getPartList()) {
            EquipmentSlot slot = pair.getLeft();
            EnumPlayerPart[] parts = pair.getRight();
            if (Arrays.stream(parts).map(damageModel::getFromEnum).anyMatch(part -> part.currentHealth > minHealth(player, part))) {
                final float originalDamage = damage;
                damage = ArmorUtils.applyArmor(player, player.getItemBySlot(slot), source, damage, slot);
                if (damage <= 0F)
                    return 0F;
                damage = ArmorUtils.applyEnchantmentModifiers(player, slot, source, damage);
                if (damage <= 0F)
                    return 0F;
                final float dmgAfterReduce = damage;

                damage = distributeDamageOnParts(damage, damageModel, parts, player, addStat);
                if (damage == 0F)
                    break;
                final float absorbFactor = originalDamage / dmgAfterReduce;
                final float damageDistributed = dmgAfterReduce - damage;
                damage = originalDamage - (damageDistributed * absorbFactor);
                if (FirstAidConfig.GENERAL.debug.get()) {
                    FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Distribution round: Not done yet, going to next round. Needed to distribute {} damage (reduced to {}) to {}, but only distributed {}. New damage to be distributed is {}, based on absorb factor {}", originalDamage, dmgAfterReduce, slot, damageDistributed, damage, absorbFactor);
                }
            } else if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Skipping {}, no health > min in parts!", slot);
            }
        }
        return damage;
    }
}

