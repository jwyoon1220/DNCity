// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.network

import io.github.jwyoon1220.dncity.Dncity
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Server -> client: one police dispatch call as text (the same call is also read out over the radio, see
 * [io.github.jwyoon1220.dncity.dispatch.DispatchService]). Shown top-left ([io.github.jwyoon1220.dncity.client.dispatch.DispatchHud]);
 * police also keep it in the phone's dispatch app. [id] is unique per call, [epochMs] the server clock when it was raised.
 */
data class DispatchCallPayload(
    val id: Int,
    val epochMs: Long,
    val crime: String,
    val stars: Int,
    val suspect: String,
    val dimension: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val text: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<DispatchCallPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Dncity.ID, "dispatch_call"))

        // StreamCodec.composite stops at 6 fields
        val STREAM_CODEC: StreamCodec<ByteBuf, DispatchCallPayload> = object : StreamCodec<ByteBuf, DispatchCallPayload> {
            override fun decode(buf: ByteBuf) = DispatchCallPayload(
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.stringUtf8(32).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.stringUtf8(48).decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.stringUtf8(256).decode(buf),
            )

            override fun encode(buf: ByteBuf, p: DispatchCallPayload) {
                ByteBufCodecs.VAR_INT.encode(buf, p.id)
                ByteBufCodecs.VAR_LONG.encode(buf, p.epochMs)
                ByteBufCodecs.stringUtf8(32).encode(buf, p.crime)
                ByteBufCodecs.VAR_INT.encode(buf, p.stars)
                ByteBufCodecs.stringUtf8(48).encode(buf, p.suspect)
                ByteBufCodecs.stringUtf8(64).encode(buf, p.dimension)
                ByteBufCodecs.VAR_INT.encode(buf, p.x)
                ByteBufCodecs.VAR_INT.encode(buf, p.y)
                ByteBufCodecs.VAR_INT.encode(buf, p.z)
                ByteBufCodecs.stringUtf8(256).encode(buf, p.text)
            }
        }
    }
}

/** Server -> client: whether this player has a law job (the phone shows the dispatch app only then). Sent on change. */
data class DispatchRolePayload(val police: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<DispatchRolePayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Dncity.ID, "dispatch_role"))

        val STREAM_CODEC: StreamCodec<ByteBuf, DispatchRolePayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, DispatchRolePayload::police,
            ::DispatchRolePayload,
        )
    }
}
