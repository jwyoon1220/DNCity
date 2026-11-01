/*
 * FirstAid API
 * Copyright (c) 2017-2024
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package ichttt.mods.firstaid.api.damagesystem;

import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class AbstractDamageablePart implements INBTSerializable<CompoundTag> {
    /** No bleed on this part. */
    public static final byte BLEED_NONE = 0;
    /** Slow bleed, stopped by bandages/plasters (and anything stronger). */
    public static final byte BLEED_LIGHT = 1;
    /** Fast bleed, needs a tourniquet (limbs) or a trauma kit (any part). */
    public static final byte BLEED_HEAVY = 2;

    public final int initialMaxHealth;
    public final boolean canCauseDeath;
    @Nonnull
    public final EnumPlayerPart part;
    @Nullable
    public AbstractPartHealer activeHealer;
    public float currentHealth;
    /** One of {@link #BLEED_NONE}, {@link #BLEED_LIGHT}, {@link #BLEED_HEAVY}. */
    public byte bleedLevel = BLEED_NONE;
    /** True while the bone of this part is broken. Only arms, legs and feet can fracture. */
    public boolean fractured;
    /**
     * A wound that was dressed (bandage, hemostatic, medkit) but not sutured: {@link #BLEED_LIGHT} or {@link #BLEED_HEAVY}, the
     * bleed it would be again. Moving does not reopen it; a new hit on this part does. Only a suture kit closes it for good.
     */
    public byte dressedWound = BLEED_NONE;
    /** Ticks since a tourniquet was tied on this limb, or -1 without one. Past the safe time the limb suffers ischemia. */
    public int tourniquetTicks = -1;
    /**
     * Share of max hit points lost to surgery on a destroyed limb (0..{@value #MAX_SURGERY_PENALTY}); the limb heals only up to
     * {@link #effectiveMaxHealth()} until death.
     */
    public float surgeryPenalty;

    public static final float MAX_SURGERY_PENALTY = 0.8F;

    public boolean hasTourniquet() {
        return tourniquetTicks >= 0;
    }

    /** A non-critical limb at 0 hit points ("blacked out"): no medkit or bandage heals it, only surgery. */
    public boolean isDestroyed() {
        return !canCauseDeath && currentHealth <= 0.0F;
    }

    /** Max hit points less what surgery took. */
    public float effectiveMaxHealth() {
        return getMaxHealth() * (1.0F - surgeryPenalty);
    }

    public AbstractDamageablePart(int maxHealth, boolean canCauseDeath, @Nonnull EnumPlayerPart playerPart) {
        this.initialMaxHealth = maxHealth;
        this.canCauseDeath = canCauseDeath;
        this.part = playerPart;
    }

    /**
     * Heals the part for the specified amount.
     *
     * @param amount      The amount the part should be healed, clamped to max health
     * @param player      The entity that this part belongs to. May be null if applyDebuff is false, otherwise this is required nonnull
     * @param applyDebuff If all registered debuffs should be notified of the healing done
     * @return The amount of health that could not be added
     */
    public abstract float heal(float amount, @Nullable Player player, boolean applyDebuff);

    /**
     * Damages the part for the specified amount.
     *
     * @param amount      The amount the part should be damaged, clamped to 0
     * @param player      The entity that this part belongs to. May be null if applyDebuff is false, otherwise this is required nonnull
     * @param applyDebuff If all registered debuffs should be notified of the damage taken
     * @return The amount of damage that could not be done
     */
    public abstract float damage(float amount, @Nullable Player player, boolean applyDebuff);

    /**
     * Damages the part for the specified amount.
     *
     * @param amount      The amount the part should be damaged, clamped to minHealth
     * @param player      The entity that this part belongs to. May be null if applyDebuff is false, otherwise this is required nonnull
     * @param applyDebuff If all registered debuffs should be notified of the damage taken
     * @param minHealth   The minimum health the part should drop to
     * @return The amount of damage that could not be done
     */
    public abstract float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth);

    /**
     * Updates the part.
     * Should not be called by other mods!
     */
    public void tick(Level world, Player player, boolean tickDebuffs) {
        tick(world, player, tickDebuffs, false);
    }

    /**
     * Updates the part.
     * Should not be called by other mods!
     */
    public abstract void tick(Level world, Player player, boolean tickDebuffs, boolean pauseHealing);

    public abstract void setMaxHealth(int maxHealth);

    public abstract int getMaxHealth();

    public abstract void loadDebuffInfo(IDebuff[] debuffs);
}
