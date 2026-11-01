// AGENT-DONE(claude): ammo-round-variants
package com.tacz.guns.api.item;

import net.minecraft.world.item.ItemStack;

/**
 * An {@link IAmmo} item that is one specific round (ball, tracer, AP, ...) instead of a generic ammo id, added by another
 * mod (DNCity's cartridge items). The round is identified by the item's registry name, so a gun can remember which round
 * it loaded ({@link com.tacz.guns.api.item.nbt.LoadedRounds}) and give the same item back when it is unloaded.
 * <p>
 * Reloading mixes tracer and other rounds of one caliber in a fixed pattern ({@link com.tacz.guns.api.item.nbt.LoadedRounds#TRACER_PERIOD}),
 * and a loaded round's tracer flag replaces the gun's own {@code tracer_count_interval}.
 */
public interface IAmmoRound {
    boolean isTracerRound(ItemStack ammo);
}
