// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.client.dispatch

import io.github.jwyoon1220.dncity.network.DispatchCallPayload

/** Client-side dispatch state: the call being shown top-left, the history (police phone app), whether this player is police. */
object DispatchClientState {
    const val MAX_HISTORY = 30

    @Volatile var police: Boolean = false
    /** The police channel (kHz), as the server last told us. */
    @Volatile var frequencyKhz: Double = 7_000.0
    @Volatile var latest: DispatchCallPayload? = null
    @Volatile var latestAtMs: Long = 0
    /** Newest first. */
    val history = ArrayList<DispatchCallPayload>()

    fun onCall(call: DispatchCallPayload) {
        synchronized(history) {
            history.add(0, call)
            while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        }
        latest = call
        latestAtMs = System.currentTimeMillis()
    }

    fun snapshot(): List<DispatchCallPayload> = synchronized(history) { ArrayList(history) }

    /** Leaving a world: nothing from the old one stays. */
    fun clear() {
        synchronized(history) { history.clear() }
        latest = null
        police = false
    }
}
