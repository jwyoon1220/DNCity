package com.mamiyaotaru.voxelmap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class Events {
    private Events() {
    }

    /**
     * Fabric-only -- these classes don't exist on NeoForge's classpath at all, so merely linking
     * this method (which every caller does unconditionally, since this class is shared "common"
     * code) throws NoClassDefFoundError there. DNCity's NeoForge port
     * (com.mamiyaotaru.voxelmap.neoforge.VoxelmapNeoForgeEvents) registers the same five hookups
     * against NeoForge's own event bus instead, so callers must tolerate this failing on that
     * loader -- see [VoxelMap#lateInit] for the try/catch that does that.
     */
    public static void initEvents(VoxelMap map) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> map.onDisconnect());
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> map.onConfigurationInit());
        ClientPlayConnectionEvents.INIT.register((handler, client) -> map.onPlayInit());
        ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> map.onClientStopping());
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> VoxelConstants.renderOverlay(drawContext));
    }
}
