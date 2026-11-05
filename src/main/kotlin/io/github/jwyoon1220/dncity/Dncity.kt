package io.github.jwyoon1220.dncity

import io.github.jwyoon1220.dncity.block.ModBlockEntities
import io.github.jwyoon1220.dncity.block.ModBlocks
import io.github.jwyoon1220.dncity.command.PgpCommand
import io.github.jwyoon1220.dncity.ballistics.ModAttachments
import io.github.jwyoon1220.dncity.ballistics.ShellRegistry
import io.github.jwyoon1220.dncity.ballistics.smallarms.SmallArmsData
import io.github.jwyoon1220.dncity.ballistics.terminal.ArmorEffectModels
import io.github.jwyoon1220.dncity.ballistics.terminal.TerminalBallistics
import io.github.jwyoon1220.dncity.ballistics.vehicle.VehicleArmorRegistry
import io.github.jwyoon1220.dncity.ballistics.terminal.ammo.AmmoRegistry
import io.github.jwyoon1220.dncity.ballistics.terminal.ammo.AmmoSolvers
import io.github.jwyoon1220.dncity.command.AmmoCommand
import io.github.jwyoon1220.dncity.ballistics.terminal.fullcaliber.FullCaliberApPresetRegistry
import io.github.jwyoon1220.dncity.compat.FirstAidShellBridge
import io.github.jwyoon1220.dncity.compat.SbwFarEntityCompat
import io.github.jwyoon1220.dncity.compat.SbwFlightModelBridge
import io.github.jwyoon1220.dncity.compat.SbwRemovedContent
import io.github.jwyoon1220.dncity.compat.SbwVehicleArmorBridge
import io.github.jwyoon1220.dncity.compat.TaczCartridges
import io.github.jwyoon1220.dncity.compat.TaczVehicleArmorBridge
import io.github.jwyoon1220.dncity.compat.TaczFirstAidBridge
import io.github.jwyoon1220.dncity.compat.TaczShellKeys
import io.github.jwyoon1220.dncity.compat.TaczSmallArmsBridge
import io.github.jwyoon1220.dncity.farentity.FarEntityServer
import io.github.jwyoon1220.dncity.command.PhoneNumberCommand
import io.github.jwyoon1220.dncity.command.RadioCommand
import io.github.jwyoon1220.dncity.item.ModItems
import io.github.jwyoon1220.dncity.life.LifeAttachments
import io.github.jwyoon1220.dncity.life.LifeCommand
import io.github.jwyoon1220.dncity.life.LifeServerEvents
import io.github.jwyoon1220.dncity.life.market.MarketService
import io.github.jwyoon1220.dncity.command.ShopCommand
import io.github.jwyoon1220.dncity.network.ShopNetworking
import io.github.jwyoon1220.dncity.item.component.ModDataComponents
import io.github.jwyoon1220.dncity.command.MusicCommand
import io.github.jwyoon1220.dncity.music.MusicAssetSender
import io.github.jwyoon1220.dncity.music.MusicServerEvents
import io.github.jwyoon1220.dncity.network.FarEntityNetworking
import io.github.jwyoon1220.dncity.network.MusicNetworking
import io.github.jwyoon1220.dncity.network.PgpNetworking
import io.github.jwyoon1220.dncity.network.PhoneNetworking
import io.github.jwyoon1220.dncity.network.RadioNetworking
import io.github.jwyoon1220.dncity.network.RadioStationNetworking
import io.github.jwyoon1220.dncity.network.VehicleHitNetworking
import io.github.jwyoon1220.dncity.network.VoiceNetworking
import io.github.jwyoon1220.dncity.network.kcp.VoiceKcpServerEvents
import io.github.jwyoon1220.dncity.phone.PhoneServerEvents
import io.github.jwyoon1220.dncity.radio.RadioStationServerEvents
import io.github.jwyoon1220.dncity.security.PgpAuthServerEvents
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/**
 * Main mod class.
 *
 * Only dist-agnostic (common) registrations belong here -- KotlinForForge's automatic
 * `@EventBusSubscriber` injection (`AutoKotlinEventBusSubscriber`) reflects over every declared
 * method of this class via kotlin-reflect during mod construction, which forces the JVM to
 * resolve each method's parameter/return types even for methods that are never called. A
 * client-only method living here (e.g. one referencing `net.minecraft.client.KeyMapping`) used to
 * crash the dedicated server under RuntimeDistCleaner for exactly this reason, even when the
 * event it listened for would never have fired on the server. All client-only setup lives in
 * [io.github.jwyoon1220.dncity.client.ClientModEvents] instead, whose own
 * `@EventBusSubscriber(value = [Dist.CLIENT])` annotation is dist-filtered by FML's ASM-based
 * class scan *before* the class is ever loaded, so a dedicated server never reflects over it at
 * all.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Suppress("removal")
@Mod(Dncity.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object Dncity {
    const val ID = "dncity"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.REGISTRY.register(MOD_BUS)
        ModBlockEntities.REGISTRY.register(MOD_BUS)
        ModItems.REGISTRY.register(MOD_BUS)
        ModDataComponents.REGISTRY.register(MOD_BUS)
        ModAttachments.REGISTRY.register(MOD_BUS)
        LifeAttachments.REGISTRY.register(MOD_BUS)
        NeoForge.EVENT_BUS.addListener(LifeCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(LifeServerEvents::onServerTick)
        NeoForge.EVENT_BUS.addListener(LifeServerEvents::onDeath)
        NeoForge.EVENT_BUS.addListener(LifeServerEvents::onDamage)
        NeoForge.EVENT_BUS.addListener(LifeServerEvents::onRespawn)
        NeoForge.EVENT_BUS.addListener(LifeServerEvents::onLoggedOut)
        NeoForge.EVENT_BUS.addListener(ShopCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(io.github.jwyoon1220.dncity.command.DispatchCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(MarketService::onServerStarted)
        NeoForge.EVENT_BUS.addListener(MarketService::onServerStopped)
        NeoForge.EVENT_BUS.addListener(MarketService::onServerTick)
        MOD_BUS.addListener(ShopNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(io.github.jwyoon1220.dncity.network.DispatchNetworking::onRegisterPayloadHandlers)

        NeoForge.EVENT_BUS.addListener(RadioCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(MusicCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(PhoneNumberCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(PgpCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(MusicServerEvents::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(MusicAssetSender::onServerTick)
        NeoForge.EVENT_BUS.addListener(MusicAssetSender::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(PhoneServerEvents::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(PhoneServerEvents::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(RadioStationServerEvents::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(VoiceKcpServerEvents::onServerStarting)
        NeoForge.EVENT_BUS.addListener(VoiceKcpServerEvents::onServerStopping)
        NeoForge.EVENT_BUS.addListener(VoiceKcpServerEvents::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(VoiceKcpServerEvents::onPlayerLoggedOut)
        MOD_BUS.addListener(RadioNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(RadioStationNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(VoiceNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(MusicNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(PhoneNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(PgpNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(PgpAuthServerEvents::onRegisterConfigurationTasks)
        NeoForge.EVENT_BUS.addListener(ShellRegistry::onAddReloadListeners)
        NeoForge.EVENT_BUS.addListener(SmallArmsData::onAddReloadListeners)
        NeoForge.EVENT_BUS.addListener(TerminalBallistics::onAddReloadListeners)
        AmmoSolvers.registerBuiltins { FullCaliberApPresetRegistry.asMap() }
        ArmorEffectModels.registerBuiltins()
        NeoForge.EVENT_BUS.addListener(AmmoRegistry::onAddReloadListeners)
        NeoForge.EVENT_BUS.addListener(AmmoCommand::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(VehicleArmorRegistry::onAddReloadListeners)
        MOD_BUS.addListener(VehicleHitNetworking::onRegisterPayloadHandlers)
        MOD_BUS.addListener(FarEntityNetworking::onRegisterPayloadHandlers)
        NeoForge.EVENT_BUS.addListener(FarEntityServer::onServerTick)
        NeoForge.EVENT_BUS.addListener(FarEntityServer::onLoggedOut)
        NeoForge.EVENT_BUS.addListener(FarEntityServer::onChangedDimension)
        NeoForge.EVENT_BUS.addListener(FarEntityServer::onServerStopping)
        // Loaded lazily: these reference TACZ and First Aid classes
        if (ModList.get().isLoaded("tacz")) {
            TaczShellKeys.register()
            TaczSmallArmsBridge.register()
            // One item per round in dncity/cartridges.json, plus the "Ammunition" creative tab
            TaczCartridges.register(MOD_BUS)
        }
        if (ModList.get().isLoaded("firstaid")) {
            FirstAidShellBridge.register()
        }
        if (ModList.get().isLoaded("tacz") && ModList.get().isLoaded("firstaid")) {
            TaczFirstAidBridge.register()
        }
        // Armor penetration on SuperbWarfare vehicles (+ the shooter's hit camera)
        if (ModList.get().isLoaded("superbwarfare")) {
            // War Thunder-style flight model for SuperbWarfare's aircraft that have data in dncity/flight_models
            SbwFlightModelBridge.register()
            SbwVehicleArmorBridge.register()
            // far-player proxies sit in the real seat (hidden tank crew stays hidden)
            SbwFarEntityCompat.register()
            if (ModList.get().isLoaded("tacz")) TaczVehicleArmorBridge.register()
        }
        // SuperbWarfare's energy / sci-fi weapons: no recipes, loot, creative entries or entities. No SuperbWarfare classes
        // involved, and the loot modifier type must exist for data/neoforge/loot_modifiers either way
        SbwRemovedContent.register(MOD_BUS)
    }

    /**
     * Fired on the mod bus -- only ever fires on a dedicated server, so (unlike client-only
     * setup) this is safe to keep here: its own event type carries no client-only references.
     */
    @SubscribeEvent
    fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
    }

    @SubscribeEvent
    fun onBuildCreativeModeTabContents(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            ModItems.ALL_RADIOS.forEach(event::accept)
        }
    }
}
