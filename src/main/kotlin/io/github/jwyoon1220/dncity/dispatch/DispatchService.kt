// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.dispatch

import io.github.jwyoon1220.dncity.life.Life
import io.github.jwyoon1220.dncity.life.core.Crime
import io.github.jwyoon1220.dncity.life.core.Jobs
import io.github.jwyoon1220.dncity.network.DispatchCallPayload
import io.github.jwyoon1220.dncity.network.DispatchRolePayload
import io.github.jwyoon1220.dncity.network.RadioRangeInfo
import io.github.jwyoon1220.dncity.network.RadioSenderPosition
import io.github.jwyoon1220.dncity.network.kcp.VoiceKcpServer
import io.github.jwyoon1220.dncity.radio.RadioActions
import io.github.jwyoon1220.dncity.radio.RadioBand
import io.github.jwyoon1220.dncity.voice.Codec2Codec
import io.github.jwyoon1220.dncity.voice.Codec2Encoder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.math.abs
import kotlin.random.Random

/**
 * Police dispatch. A crime ([io.github.jwyoon1220.dncity.life.LifeServerEvents.commit]) raises a call:
 *  1. the text goes to every player with a law job and every player whose powered hotbar radio is tuned to the dispatch
 *     frequency (the top-left card, and the phone app for police);
 *  2. the same line is recorded with the machine's speech synthesizer ([DispatchTts]), pushed through **codec2** (so it
 *     does not sound like a plain TTS voice: vocoder buzz, no TTS prosody, a squelch burst around it and the odd lost
 *     frame, then the receiver's own USB-style static, see [io.github.jwyoon1220.dncity.voice.ClientRadioReceiver]) and
 *     played to the same listeners as a global radio transmission on [frequencyKhz]. No range check: this is a
 *     dispatch centre, not a handheld.
 * One worker thread does synthesis, encoding and the 20 ms pacing; calls play one after another.
 */
object DispatchService {
    private val LOGGER = LoggerFactory.getLogger("dncity/dispatch")

    /**
     * Shortwave, 7.000 MHz: HF has no range limit here (ground wave + map-spanning night skywave) and, with codec2's
     * HF tier (MODE_1200, 1.2 kb/s), is the most vocoder-garbled of the bands. Change with `/dispatch freq`.
     */
    const val DEFAULT_FREQUENCY_KHZ = 7_000.0

    /** Transmitter id the clients mix the dispatch stream under (a real entity id can never be this large). */
    private const val DISPATCH_SENDER_ID = Int.MAX_VALUE - 7

    /** Sideband by HF convention (LSB below 10 MHz, USB above); both are codec2 modes with identical DSP. */
    private fun modeNameFor(freqKhz: Double) = if (freqKhz < 10_000.0) "LSB" else "USB"
    private const val GLOBAL_RANGE_BLOCKS = 1.0e9
    private const val PER_CRIME_COOLDOWN_MS = 45_000L
    private const val MAX_QUEUE = 4

    /** Chance that one codec2 frame is dropped (heard as a tiny gap, like a marginal digital link). */
    private const val FRAME_LOSS = 0.02

    @Volatile
    var frequencyKhz: Double = DEFAULT_FREQUENCY_KHZ

    private val nextId = AtomicInteger(1)
    private val lastCall = ConcurrentHashMap<String, Long>()
    private val roleSent = HashMap<UUID, Pair<Boolean, Double>>()

    private val worker = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(MAX_QUEUE),
        { r -> Thread(r, "DNCity-Dispatch").also { it.isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    private fun isPolice(p: ServerPlayer) = Jobs.find(Life.get(p).job.jobId)?.law == true

    private fun tunedToDispatch(p: ServerPlayer): Boolean {
        val band = RadioBand.fromFrequencyKhz(frequencyKhz) ?: return false
        return RadioActions.hotbarRadios(p).any { (radio, stack) ->
            val data = radio.dataOf(stack)
            data.powered && data.slots.any { it.enabled && abs(it.frequencyKhz - frequencyKhz) <= band.tuningToleranceKhz }
        }
    }

    /** Once a second: tell a player when their police role changes (login, joining/quitting the police job, ...). */
    fun tick(server: MinecraftServer) {
        val online = HashSet<UUID>()
        for (p in server.playerList.players) {
            online += p.uuid
            val role = isPolice(p) to frequencyKhz
            if (roleSent[p.uuid] != role) {
                roleSent[p.uuid] = role
                PacketDistributor.sendToPlayer(p, DispatchRolePayload(role.first, role.second))
            }
        }
        roleSent.keys.retainAll(online)
    }

    /** A crime by [suspect]: raise a call unless the same crime by the same player was dispatched recently. */
    fun onCrime(suspect: ServerPlayer, crime: Crime, stars: Int) {
        val key = "${suspect.uuid}:${crime.name}"
        val now = System.currentTimeMillis()
        val prev = lastCall[key]
        if (prev != null && now - prev < PER_CRIME_COOLDOWN_MS) return
        lastCall[key] = now
        val pos = suspect.blockPosition()
        raise(
            suspect.server, crime.ko, stars, suspect.gameProfile.name,
            suspect.level().dimension().location().toString(), pos.x, pos.y, pos.z,
        )
    }

    fun raise(server: MinecraftServer, crime: String, stars: Int, suspect: String, dimension: String, x: Int, y: Int, z: Int) {
        val id = nextId.getAndIncrement()
        val text = buildText(id, crime, stars, suspect, x, z)
        val payload = DispatchCallPayload(id, System.currentTimeMillis(), crime, stars, suspect, dimension, x, y, z, text)

        val listeners = ArrayList<UUID>()
        for (p in server.playerList.players) {
            val tuned = tunedToDispatch(p)
            if (tuned || isPolice(p)) PacketDistributor.sendToPlayer(p, payload)
            if (tuned) listeners += p.uuid
        }
        if (listeners.isEmpty()) return
        val freq = frequencyKhz
        worker.execute { broadcast(text, freq, listeners) }
    }

    /** What the dispatcher says. Short, coordinates rounded so the speech stays quick. */
    private fun buildText(id: Int, crime: String, stars: Int, suspect: String, x: Int, z: Int): String =
        "전 유닛, 전 유닛. 신고 ${id}번. $crime 발생. 용의자 $suspect. 수배 ${stars}성. " +
            "좌표 엑스 ${round10(x)}, 지 ${round10(z)} 부근. 인근 순찰 유닛은 즉시 출동 바랍니다."

    private fun round10(v: Int) = Math.round(v / 10.0) * 10

    private fun broadcast(text: String, freq: Double, listeners: List<UUID>) {
        try {
            val pcm = DispatchTts.synthesize(text) ?: return
            val band = RadioBand.fromFrequencyKhz(freq) ?: return
            val mode = Codec2Codec.modeForBand(band)
            Codec2Encoder(mode).use { enc ->
                val n = enc.samplesPerFrame
                val frameNanos = n * 1_000_000_000L / Codec2Codec.SAMPLE_RATE
                val prepared = prepare(pcm)
                val modeName = modeNameFor(freq)
                val rng = Random(text.hashCode())
                var deadline = System.nanoTime()
                val range = RadioRangeInfo.of(GLOBAL_RANGE_BLOCKS, obstructed = false, stormNoise = false)
                val origin = RadioSenderPosition(0.0, 64.0, 0.0)
                var offset = 0
                while (offset < prepared.size) {
                    val frame = ShortArray(n)
                    System.arraycopy(prepared, offset, frame, 0, minOf(n, prepared.size - offset))
                    offset += n
                    val bytes = enc.encode(frame)
                    if (rng.nextDouble() >= FRAME_LOSS) {
                        for (uuid in listeners) {
                            VoiceKcpServer.sendRadioRelay(uuid, DISPATCH_SENDER_ID, bytes, freq, modeName, origin, range)
                        }
                    }
                    deadline += frameNanos
                    val wait = deadline - System.nanoTime()
                    if (wait > 0) LockSupport.parkNanos(wait)
                }
            }
        } catch (t: Throwable) {
            LOGGER.warn("dispatch broadcast failed: {}", t.toString())
        }
    }

    /**
     * Makes the recording sound like something that came over a link instead of out of a speech engine: levelled, a
     * 90 ms squelch burst before and a 120 ms noise tail after (the transmitter keying and dropping), and a little
     * noise under the voice. Everything else is codec2's doing.
     */
    private fun prepare(pcm: ShortArray): ShortArray {
        var peak = 1
        for (s in pcm) peak = maxOf(peak, abs(s.toInt()))
        val gain = 24000.0 / peak
        val rng = Random(7)
        val head = 8000 * 90 / 1000
        val tail = 8000 * 120 / 1000
        val out = ShortArray(head + pcm.size + tail)
        for (i in 0 until head) out[i] = (rng.nextInt(-5000, 5000) * (1.0 - i / head.toDouble())).toInt().toShort()
        for (i in pcm.indices) {
            val v = pcm[i] * gain + rng.nextInt(-350, 350)
            out[head + i] = v.toInt().coerceIn(-32768, 32767).toShort()
        }
        for (i in 0 until tail) {
            out[head + pcm.size + i] = (rng.nextInt(-4000, 4000) * (1.0 - i / tail.toDouble())).toInt().toShort()
        }
        return out
    }
}
