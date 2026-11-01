package com.mamiyaotaru.voxelmap.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = "voxelmap", dist = Dist.CLIENT)
public class VoxelmapNeoForgeMod {

    // VoxelmapSettingsChannelHandler/VoxelmapWorldIdChannelHandler (common code) construct
    // Fabric-only networking classes (ClientPlayNetworking, ClientConfigurationNetworking,
    // PayloadTypeRegistry) directly in their constructors -- calling either one here crashed
    // NeoForge with NoClassDefFoundError the instant this mod class was constructed (before this
    // fix, `new VoxelmapSettingsChannelHandler()` / `new VoxelmapWorldIdChannelHandler()` ran
    // right here). Neither handler has a NeoForge-side companion server sending these packets in
    // this modpack anyway, so they're just left unconstructed rather than ported -- see
    // VoxelMap.java's try/catch around its own WorldIdC2S send for the same reasoning.
    public VoxelmapNeoForgeMod(IEventBus bus, ModContainer modContainer) {
    }
}
