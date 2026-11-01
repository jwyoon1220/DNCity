package com.mamiyaotaru.voxelmap.neoforge;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * NeoForge replacement for two of the five hookups in VoxelMap's common
 * {@code com.mamiyaotaru.voxelmap.Events}, which registers exclusively against Fabric API classes
 * (ClientPlayConnectionEvents, ClientLifecycleEvents, HudRenderCallback) that don't exist under
 * NeoForge -- see that class's own doc comment, and {@code VoxelMap.lateInit}'s try/catch around
 * calling it. The other three: the actual minimap/HUD draw hook (Fabric's HudRenderCallback) is
 * instead done via a mixin (see neoforge.mixins.MixinInGameHud's own doc comment for why:
 * VoxelMap's neoforge.mods.toml declares `modLoader = "lowcodefml"`, which doesn't reliably run
 * NeoForge's normal @EventBusSubscriber annotation scanning -- so these two listeners may not
 * actually fire either, but degrade gracefully if not, unlike the render hook); Fabric's
 * "configuration phase" (onConfigurationInit) has no public NeoForge client-event equivalent and
 * its body is just another clearServerSettings() call, already covered here by
 * {@link #onLoggingIn}/{@link #onLoggingOut}; onClientStopping (flushes VoxelMap's own waypoint
 * save queue) has no direct NeoForge client "stopping" event either, and losing it only risks the
 * last few seconds of unsaved waypoint edits on a clean game exit, not a correctness issue.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class VoxelmapNeoForgeEvents {
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        VoxelConstants.getVoxelMapInstance().onPlayInit();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VoxelConstants.getVoxelMapInstance().onDisconnect();
    }
}
