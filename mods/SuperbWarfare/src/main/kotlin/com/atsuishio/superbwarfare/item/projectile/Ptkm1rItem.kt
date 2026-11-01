package com.atsuishio.superbwarfare.item.projectile

import com.atsuishio.superbwarfare.client.renderer.item.Ptkm1rItemRenderer
import com.atsuishio.superbwarfare.entity.projectile.Ptkm1rEntity
import com.atsuishio.superbwarfare.item.misc.AbstractDeployerItem
import com.atsuishio.superbwarfare.tools.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

object Ptkm1rItem : AbstractDeployerItem(Properties().rarity(Rarity.RARE).stacksTo(2)) {

    init {
        // RegisterClientExtensionsEvent is a mod-bus-only event (IModBusEvent) -- unlike
        // registerToEventBus's NeoForge.EVENT_BUS (the common bus), which this object's only
        // listener isn't valid on ("IModBusEvent events are not allowed on the common NeoForge
        // bus!").
        MOD_BUS.register(this)
    }

    @SubscribeEvent
    fun registerRenderer(event: RegisterClientExtensionsEvent) {
        event.registerItem(object : IClientItemExtensions {
            private val renderer by lazy { Ptkm1rItemRenderer(mc.blockEntityRenderDispatcher, mc.entityModels) }

            override fun getCustomRenderer() = renderer
        }, this)
    }

    override fun spawnDeployedEntity(level: Level, player: Player): Entity {
        return Ptkm1rEntity(player, level)
    }
}
