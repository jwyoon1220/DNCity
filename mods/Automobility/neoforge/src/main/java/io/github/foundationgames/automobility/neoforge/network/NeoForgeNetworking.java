package io.github.foundationgames.automobility.neoforge.network;

import io.github.foundationgames.automobility.util.network.AutomobilityPacketPayload;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.foundationgames.automobility.util.network.CommonPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class NeoForgeNetworking {
    public static final String PROTOCOL_VERSION = "0.5";
    public static final DirectionalPayloadHandler<AutomobilityPacketPayload> HANDLER = new DirectionalPayloadHandler<>(
            NeoForgeNetworking::receiveClient, NeoForgeNetworking::receiveServer
    );

    public static void receiveClient(AutomobilityPacketPayload payload, IPayloadContext ctx) {
        ClientPackets.CLIENTBOUND_HANDLERS.get(payload.id()).accept(Minecraft.getInstance(), payload.buf());
    }

    public static void receiveServer(AutomobilityPacketPayload payload, IPayloadContext ctx) {
        var player = ctx.player();
        if (player instanceof ServerPlayer sPlayer) {
            CommonPackets.SERVERBOUND_HANDLERS.get(payload.id()).accept(player.getServer(), sPlayer, payload.buf());
        }
    }
}
