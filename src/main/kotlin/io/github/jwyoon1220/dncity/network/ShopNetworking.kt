// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.network

import io.github.jwyoon1220.dncity.client.shop.ShopClient
import io.github.jwyoon1220.dncity.life.market.MarketService
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadHandler

object ShopNetworking {
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        registrar.playToServer(ShopTradePayload.TYPE, ShopTradePayload.STREAM_CODEC, IPayloadHandler { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                MarketService.trade(player, payload.shop, payload.id, payload.qty, payload.buy)
            }
        })

        registrar.playToClient(ShopSnapshotPayload.TYPE, ShopSnapshotPayload.STREAM_CODEC, IPayloadHandler { payload, context ->
            context.enqueueWork { ShopClient.handleSnapshot(payload) }
        })
    }
}
