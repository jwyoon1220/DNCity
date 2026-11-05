// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.network

import io.github.jwyoon1220.dncity.client.dispatch.DispatchClientState
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadHandler

object DispatchNetworking {
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(DispatchCallPayload.TYPE, DispatchCallPayload.STREAM_CODEC, IPayloadHandler { payload, context ->
            context.enqueueWork { DispatchClientState.onCall(payload) }
        })
        registrar.playToClient(DispatchRolePayload.TYPE, DispatchRolePayload.STREAM_CODEC, IPayloadHandler { payload, context ->
            context.enqueueWork {
                DispatchClientState.police = payload.police
                DispatchClientState.frequencyKhz = payload.frequencyKhz
            }
        })
    }
}
