// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.life.market

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList

/**
 * Turns a market key into an item. A key is a plain registry id (`minecraft:bread`, `superbwarfare:large_shell_he`) or, for
 * TACZ ammo that is one item type told apart by an ammo id (the tank rounds, `dncity:m829`), `tacz_ammo:<ammo id>`.
 * TACZ classes are only touched for the second kind, and only when TACZ is loaded.
 */
object MarketItems {
    const val TACZ_PREFIX = "tacz_ammo:"

    fun isTaczAmmo(key: String) = key.startsWith(TACZ_PREFIX)

    private fun taczLoaded() = ModList.get().isLoaded("tacz")

    /** True when the item exists in this game (a mod that is missing makes its items vanish from the market). */
    fun exists(key: String): Boolean {
        if (isTaczAmmo(key)) return taczLoaded() && ResourceLocation.tryParse(key.removePrefix(TACZ_PREFIX)) != null
        val rl = ResourceLocation.tryParse(key) ?: return false
        return BuiltInRegistries.ITEM.containsKey(rl)
    }

    fun stackOf(key: String, count: Int = 1): ItemStack {
        if (isTaczAmmo(key)) {
            if (!taczLoaded()) return ItemStack.EMPTY
            return Tacz.stack(ResourceLocation.parse(key.removePrefix(TACZ_PREFIX)), count)
        }
        val rl = ResourceLocation.tryParse(key) ?: return ItemStack.EMPTY
        return BuiltInRegistries.ITEM.getOptional(rl).map { ItemStack(it, count) }.orElse(ItemStack.EMPTY)
    }

    fun matches(key: String, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (isTaczAmmo(key)) return taczLoaded() && Tacz.ammoId(stack)?.toString() == key.removePrefix(TACZ_PREFIX)
        return ResourceLocation.tryParse(key)?.let { BuiltInRegistries.ITEM.getKey(stack.item) == it } ?: false
    }

    private object Tacz {
        fun stack(id: ResourceLocation, count: Int): ItemStack =
            com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(id).setCount(count).build()

        fun ammoId(stack: ItemStack): ResourceLocation? =
            (stack.item as? com.tacz.guns.api.item.IAmmo)?.getAmmoId(stack)
    }
}
