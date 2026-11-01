package com.atsuishio.superbwarfare.item.gun.special

import com.atsuishio.superbwarfare.client.renderer.gun.GeoGunRenderer
import com.atsuishio.superbwarfare.item.gun.GunItem
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

object TaserV2Item : GunItem(Properties()) {

    init {
        // RegisterClientExtensionsEvent is a mod-bus-only event (IModBusEvent) -- unlike
        // registerToEventBus's NeoForge.EVENT_BUS (the common bus), which this object's only
        // listener isn't valid on ("IModBusEvent events are not allowed on the common NeoForge
        // bus!").
        MOD_BUS.register(this)
    }

    @SubscribeEvent
    fun initializeClient(event: RegisterClientExtensionsEvent) {
        event.registerItem(object : IClientItemExtensions {
            private val renderer by lazy { GeoGunRenderer() }

            override fun getCustomRenderer() = renderer
        }, this)
    }
}
