package io.github.jwyoon1220.dncity.client

import io.github.jwyoon1220.dncity.Dncity
import io.github.jwyoon1220.dncity.client.farentity.FarEntityClient
import io.github.jwyoon1220.dncity.client.network.DhNetworkTuning
import io.github.jwyoon1220.dncity.client.phone.PhoneCallManager
import io.github.jwyoon1220.dncity.client.phone.PhoneController
import io.github.jwyoon1220.dncity.client.phone.nanovg.PhoneNanoVgSurface
import io.github.jwyoon1220.dncity.client.window.WindowOverlayManager
import io.github.jwyoon1220.dncity.command.PhoneCommand
import io.github.jwyoon1220.dncity.command.WindowCommand
import io.github.jwyoon1220.dncity.item.RadioItem
import io.github.jwyoon1220.dncity.music.AudioPlayer
import io.github.jwyoon1220.dncity.music.MidiPlayer
import io.github.jwyoon1220.dncity.music.MusicClientReceiver
import io.github.jwyoon1220.dncity.voice.RadioStationClientState
import io.github.jwyoon1220.dncity.voice.VoiceClientLoop
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import io.github.jwyoon1220.dncity.client.render.BallisticArmorBar
import io.github.jwyoon1220.dncity.client.render.VehicleHitCamOverlay
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import org.apache.logging.log4j.Level

/**
 * All client-only setup for [Dncity], split out of the main mod class so it lives behind an
 * `@EventBusSubscriber(value = [Dist.CLIENT])` class of its own -- see [Dncity]'s doc comment for
 * why that split matters (FML's dist-filtered class scan means a dedicated server never loads
 * this class at all, unlike relying on an event simply never firing there).
 */
@Suppress("removal")
@EventBusSubscriber(modid = Dncity.ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ClientModEvents {
    /** War Thunder-style hit camera after hitting an armored vehicle, see [VehicleHitCamOverlay]. */
    @SubscribeEvent
    fun onRegisterGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(VehicleHitCamOverlay.ID, VehicleHitCamOverlay)
        event.registerAboveAll(io.github.jwyoon1220.dncity.client.dispatch.DispatchHud.ID, io.github.jwyoon1220.dncity.client.dispatch.DispatchHud)
        // Red ballistic armor row above vanilla's armor row
        event.registerAbove(VanillaGuiLayers.ARMOR_LEVEL, BallisticArmorBar.ID, BallisticArmorBar)
    }

    @SubscribeEvent
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(ModKeyMappings.RADIO_PTT)
        event.register(ModKeyMappings.PHONE_TOGGLE)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        Dncity.LOGGER.log(Level.INFO, "Initializing client...")
        RadioItem.screenOpener = { radio, stack, hand ->
            Minecraft.getInstance().setScreen(RadioScreen(radio, stack, hand))
        }

        // Close-range voice: mic capture/playback only runs while actually in a world, not
        // sitting at the main menu.
        NeoForge.EVENT_BUS.addListener(::onVoiceLogin)
        NeoForge.EVENT_BUS.addListener(::onVoiceLogout)
        NeoForge.EVENT_BUS.addListener(::onVoiceClientTick)

        // Native window overlay (see client.window.WindowOverlayManager) -- registered here, not
        // Dncity's shared init{} block RadioCommand/MusicCommand use, since WindowCommand's body
        // touches AWT/native APIs that must never run on a dedicated server.
        NeoForge.EVENT_BUS.addListener(WindowCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onWindowOverlayTick)
        NeoForge.EVENT_BUS.addListener(::onWindowOverlayLogout)

        // Phone screen (see client.phone.PhoneController) -- registered here for the same reason
        // as WindowCommand above.
        NeoForge.EVENT_BUS.addListener(PhoneCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onPhoneLogout)
        NeoForge.EVENT_BUS.addListener(::onPhoneToggleKey)
        NeoForge.EVENT_BUS.addListener(::onPhoneNanoVgTick)

        // Players out in Distant Horizons' LOD terrain, drawn as real models (see client.farentity.FarEntityClient)
        NeoForge.EVENT_BUS.addListener(FarEntityClient::onClientTick)
        NeoForge.EVENT_BUS.addListener(FarEntityClient::onLoggingOut)

        // DH's LOD stream follows the link instead of filling the game's TCP connection (see client.network.DhNetworkTuning)
        if (ModList.get().isLoaded("distanthorizons")) DhNetworkTuning.apply()

        // Body armor wear tooltip (see compat.TaczSmallArmsBridge)
        NeoForge.EVENT_BUS.addListener(BallisticArmorBar::onTooltip)

        // Makes VoiceSettingsScreen reachable from this mod's "Config" button in the Mods list.
        ModList.get().getModContainerById(Dncity.ID).ifPresent { container ->
            container.registerExtensionPoint(IConfigScreenFactory::class.java, IConfigScreenFactory { _, screen ->
                VoiceSettingsScreen(screen)
            })
        }
    }

    private fun onVoiceLogin(event: ClientPlayerNetworkEvent.LoggingIn) = VoiceClientLoop.start()

    private fun onVoiceLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        VoiceClientLoop.stop()
        MidiPlayer.stop()
        AudioPlayer.shutdown()
        MusicClientReceiver.reset()
        PhoneCallManager.reset()
        RadioStationClientState.clear()
    }

    private fun onVoiceClientTick(event: ClientTickEvent.Post) {
        VoiceClientLoop.tick()
        AudioPlayer.tick()
        RadioStationClientState.tick()
    }

    private fun onWindowOverlayTick(event: ClientTickEvent.Post) = WindowOverlayManager.tick()

    private fun onWindowOverlayLogout(event: ClientPlayerNetworkEvent.LoggingOut) = WindowOverlayManager.destroyAll()

    private fun onPhoneLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        PhoneController.close()
        io.github.jwyoon1220.dncity.client.dispatch.DispatchClientState.clear()
    }

    // ClientTickEvent.Post already runs on the render thread, so PhoneController.toggle() can be
    // called directly here -- unlike PhoneCommand's handlers, which hop via
    // Minecraft.getInstance().execute {} since Brigadier commands run on the integrated server's
    // own thread even in singleplayer.
    private fun onPhoneToggleKey(event: ClientTickEvent.Post) {
        while (ModKeyMappings.PHONE_TOGGLE.consumeClick()) {
            PhoneController.toggle()
        }
    }

    // Render-thread only (ClientTickEvent.Post runs there, see PhoneNanoVgSurface's doc comment
    // for why that matters -- it's the only thread with Minecraft's GL context current).
    private fun onPhoneNanoVgTick(event: ClientTickEvent.Post) {
        if (PhoneController.isOpen) PhoneNanoVgSurface.renderFrame()
    }
}
