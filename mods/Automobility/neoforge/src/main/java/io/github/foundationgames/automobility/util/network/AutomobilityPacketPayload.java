package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AutomobilityPacketPayload(ResourceLocation id, byte[] bytes) implements CustomPacketPayload {
    public static final Type<AutomobilityPacketPayload> TYPE = new Type<>(Automobility.rl("message_packet"));
    public static final StreamCodec<FriendlyByteBuf, AutomobilityPacketPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, AutomobilityPacketPayload::id,
            ByteBufCodecs.BYTE_ARRAY, AutomobilityPacketPayload::bytes,
            AutomobilityPacketPayload::new
    );

    public FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
