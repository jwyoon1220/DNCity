// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.network

import io.github.jwyoon1220.dncity.Dncity
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/** One listed item as the shop UI shows it. Prices are whole dollars for one unit; [history] is oldest-first. */
data class ShopEntry(
    val id: String,
    val category: String,
    val sub: String,
    val kind: String,
    val buy: Long,
    val sell: Long,
    val change: Float,
    val available: Int,
    val owned: Int,
    val history: List<Float>,
) {
    companion object {
        // StreamCodec.composite stops at 6 fields, so this one is written by hand
        val STREAM_CODEC: StreamCodec<ByteBuf, ShopEntry> = object : StreamCodec<ByteBuf, ShopEntry> {
            private val history = ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(64))

            override fun decode(buf: ByteBuf) = ShopEntry(
                ByteBufCodecs.stringUtf8(96).decode(buf), ByteBufCodecs.stringUtf8(48).decode(buf),
                ByteBufCodecs.stringUtf8(64).decode(buf), ByteBufCodecs.stringUtf8(32).decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                history.decode(buf),
            )

            override fun encode(buf: ByteBuf, e: ShopEntry) {
                ByteBufCodecs.stringUtf8(96).encode(buf, e.id)
                ByteBufCodecs.stringUtf8(48).encode(buf, e.category)
                ByteBufCodecs.stringUtf8(64).encode(buf, e.sub)
                ByteBufCodecs.stringUtf8(32).encode(buf, e.kind)
                ByteBufCodecs.VAR_LONG.encode(buf, e.buy)
                ByteBufCodecs.VAR_LONG.encode(buf, e.sell)
                ByteBufCodecs.FLOAT.encode(buf, e.change)
                ByteBufCodecs.VAR_INT.encode(buf, e.available)
                ByteBufCodecs.VAR_INT.encode(buf, e.owned)
                history.encode(buf, e.history)
            }
        }
    }
}

/** Server -> client: the whole market as one player sees it (sent on `/shop` and after every trade). */
data class ShopSnapshotPayload(val shop: String, val cash: Long, val bank: Long, val entries: List<ShopEntry>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShopSnapshotPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Dncity.ID, "shop_snapshot"))

        val STREAM_CODEC: StreamCodec<ByteBuf, ShopSnapshotPayload> = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), ShopSnapshotPayload::shop,
            ByteBufCodecs.VAR_LONG, ShopSnapshotPayload::cash,
            ByteBufCodecs.VAR_LONG, ShopSnapshotPayload::bank,
            ShopEntry.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), ShopSnapshotPayload::entries,
            ::ShopSnapshotPayload,
        )
    }
}

/** Client -> server: buy or sell [qty] of [id]. The server re-checks price, stock, money and items. */
data class ShopTradePayload(val shop: String, val id: String, val qty: Int, val buy: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShopTradePayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Dncity.ID, "shop_trade"))

        val STREAM_CODEC: StreamCodec<ByteBuf, ShopTradePayload> = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), ShopTradePayload::shop,
            ByteBufCodecs.stringUtf8(96), ShopTradePayload::id,
            ByteBufCodecs.VAR_INT, ShopTradePayload::qty,
            ByteBufCodecs.BOOL, ShopTradePayload::buy,
            ::ShopTradePayload,
        )
    }
}
