package io.github.jwyoon1220.dncity.network.kcp

import io.github.jwyoon1220.dncity.network.RadioRangeInfo
import io.github.jwyoon1220.dncity.network.RadioSenderPosition
import io.github.jwyoon1220.dncity.radio.RadioMode
import java.nio.ByteBuffer

/**
 * The application-level message format carried inside each [Kcp] message (itself carried inside
 * [VoiceKcpServer]/[VoiceKcpClient]'s UDP datagrams) -- everything close-range voice/radio/phone-
 * call audio used to encode via [net.minecraft.network.codec.StreamCodec] now packs by hand into a
 * plain [ByteArray] instead, since none of this rides Minecraft's own packet channel any more. One
 * leading type-tag byte selects the layout; every audio-carrying message also carries an 8-byte
 * send timestamp so the receiver can drop a frame that arrived more than [STALE_MS] late after a
 * KCP retransmit, bounding worst-case audio latency (KCP is a *reliable* protocol -- see [Kcp]'s
 * doc comment -- so without this, a burst of loss could otherwise cause a backlog of stale audio
 * to arrive all at once).
 */
object VoiceKcpProtocol {
    const val TYPE_HELLO: Byte = 0
    const val TYPE_VOICE_AUDIO: Byte = 1
    const val TYPE_VOICE_AUDIO_RELAY: Byte = 2
    const val TYPE_RADIO_AUDIO: Byte = 3
    const val TYPE_RADIO_AUDIO_RELAY: Byte = 4
    const val TYPE_PHONE_CALL_AUDIO: Byte = 5
    const val TYPE_PHONE_CALL_AUDIO_RELAY: Byte = 6

    /** Opaque bulk data (e.g. Distant Horizons LOD/terrain streaming payloads) riding the same
     * per-player [NativeKcpSession] as voice/radio/phone audio, demuxed by this same leading
     * type-tag byte -- see [VoiceKcpServer.sendLodData]/[VoiceKcpClient.sendLodData]. Unlike the
     * audio types above this carries no send timestamp: LOD data isn't perishable the way a stale
     * audio frame is, so there's nothing to drop on a late/retransmitted arrival. */
    const val TYPE_LOD_DATA: Byte = 7

    /** One chunk of a paced bulk file transfer (music soundfont/MIDI/audio), server -> client -- see
     * [io.github.jwyoon1220.dncity.music.MusicAssetSender] and [encodeAssetChunk]. */
    const val TYPE_ASSET_CHUNK: Byte = 8

    /** Client -> server over the reliable lane every [KEEPALIVE_INTERVAL_MS], no body. Audio only flows while someone
     * talks and KCP sends nothing on an idle session, so without this a player who stayed quiet for
     * `VoiceKcpServer.SESSION_IDLE_TIMEOUT_MS` lost their session silently (no radio/voice either way until relog),
     * and a NAT mapping could expire so relays to the player stopped arriving. Going reliable (not raw) also lets the
     * server learn a changed client address, which the raw lane never accepts. */
    const val TYPE_KEEPALIVE: Byte = 9

    const val KEEPALIVE_INTERVAL_MS = 5_000L

    const val STALE_MS = 250L

    /**
     * Second byte-4 value next to KCP's own `kCmdPush` (81) / `kCmdAck` (82): a datagram
     * `[conv (int32 LE)][RAW_DATAGRAM_CMD][message]` is *not* a KCP segment but one whole message sent
     * once, with no sequence number, ack or retransmit (see [wrapRaw]). Audio rides this lane: KCP
     * delivers in order, so one lost segment used to hold every later audio frame back until its
     * retransmit (RTO 30 ms at best, 200 ms before the first RTT sample) -- the same head-of-line
     * blocking TCP has. A lost voice frame is better dropped than late; the receiver's [FreshnessGate]
     * drops frames that arrive behind a newer one.
     */
    const val RAW_DATAGRAM_CMD: Byte = 90

    /** Largest message sent raw: one datagram, same MTU as `DncityKcp`'s 1200 minus the 5-byte raw header.
     * Anything bigger falls back to the reliable KCP lane. */
    const val MAX_RAW_MESSAGE_BYTES = 1200 - 5

    /**
     * Largest message the reliable KCP lane can deliver. `DncityKcp` only reassembles a message once
     * every fragment sits in its receive queue, which holds `kRcvWnd` = 128 segments of `kMss` = 1176
     * bytes (150,528 bytes), and a fragment index is one byte -- a bigger message never completes and
     * stalls the session for good. 128 KiB (112 segments) leaves room for the header.
     */
    const val MAX_RELIABLE_MESSAGE_BYTES = 128 * 1024

    /** Same cap the old [net.minecraft.network.codec.StreamCodec]-based payloads enforced (see git
     * history's `VoicePayloads`/`RadioVoicePayloads`/`PhonePayloads`) -- without it, a message
     * claiming a huge `buf.remaining()` would force an unbounded server- or client-side allocation
     * per packet. */
    private const val MAX_AUDIO_FRAME_BYTES = 2048

    /** Distant Horizons LOD/terrain payloads are legitimately much larger than an audio frame, but
     * must fit one reliable message (this used to be 1 MiB, which `DncityKcp` can never reassemble --
     * see [MAX_RELIABLE_MESSAGE_BYTES]). */
    const val MAX_LOD_PAYLOAD_BYTES = MAX_RELIABLE_MESSAGE_BYTES - 1

    /** Data bytes per [TYPE_ASSET_CHUNK] message (28 KCP segments). */
    const val ASSET_CHUNK_BYTES = 32 * 1024
    private const val MAX_EXTENSION_BYTES = 16

    private class MalformedMessageException(message: String) : Exception(message)

    private fun requireRemaining(buf: ByteBuffer, bytes: Int, what: String) {
        if (buf.remaining() < bytes) throw MalformedMessageException("truncated $what: need $bytes more bytes, have ${buf.remaining()}")
    }

    private fun readBoundedPayload(buf: ByteBuffer, max: Int, what: String): ByteArray {
        val size = buf.remaining()
        if (size > max) throw MalformedMessageException("$what too large: $size > $max bytes")
        val payload = ByteArray(size)
        buf.get(payload)
        return payload
    }

    // ---- HELLO: the first message a client sends on a fresh UDP session, proving it holds the
    // secret the server handed it over Minecraft's own (already encrypted/authenticated) channel
    // -- see network.VoiceKcpHandshakePayload. ----

    fun encodeKeepalive(): ByteArray = byteArrayOf(TYPE_KEEPALIVE)

    fun encodeHello(conv: Int, secret: Long): ByteArray =
        ByteBuffer.allocate(1 + 4 + 8).put(TYPE_HELLO).putInt(conv).putLong(secret).array()

    class Hello(val conv: Int, val secret: Long)

    fun decodeHello(data: ByteArray): Hello {
        val buf = ByteBuffer.wrap(data)
        buf.get() // type tag, already dispatched on
        requireRemaining(buf, 4 + 8, "HELLO")
        return Hello(buf.int, buf.long)
    }

    // ---- Plain audio frames (close-range uplink, radio uplink, phone-call up/downlink -- all
    // just a timestamp plus opaque codec bytes, no extra addressing needed). ----

    fun encodeAudio(type: Byte, audio: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + 8 + audio.size).put(type).putLong(System.currentTimeMillis()).put(audio).array()

    /** Peeks the send timestamp every audio message carries right after its type byte, for [FreshnessGate]. */
    fun sentAtMs(data: ByteArray): Long? {
        if (data.size < 9) return null
        return ByteBuffer.wrap(data, 1, 8).long
    }

    /** `[conv LE][RAW_DATAGRAM_CMD][message]` -- see [RAW_DATAGRAM_CMD]. */
    fun wrapRaw(conv: Int, message: ByteArray): ByteArray {
        val out = ByteArray(5 + message.size)
        out[0] = conv.toByte(); out[1] = (conv shr 8).toByte(); out[2] = (conv shr 16).toByte(); out[3] = (conv shr 24).toByte()
        out[4] = RAW_DATAGRAM_CMD
        System.arraycopy(message, 0, out, 5, message.size)
        return out
    }

    /** Whether a datagram is a raw message ([wrapRaw]) rather than KCP segments. */
    fun isRaw(datagram: ByteArray): Boolean = datagram.size > 5 && datagram[4] == RAW_DATAGRAM_CMD

    fun unwrapRaw(datagram: ByteArray): ByteArray = datagram.copyOfRange(5, datagram.size)

    /** Whether a message type is real-time audio (sent raw, gated by [FreshnessGate]). */
    fun isAudio(type: Byte): Boolean = type in TYPE_VOICE_AUDIO..TYPE_PHONE_CALL_AUDIO_RELAY

    /**
     * Drops an audio frame that arrives after a newer one of the same stream (raw datagrams can be
     * reordered on the way). Streams are keyed by the caller ([key]: message type plus the speaker for
     * relays), times are the stream's own sender clock, so clock skew between machines does not matter.
     */
    class FreshnessGate(private val maxStreams: Int = 4096) {
        private val newest = HashMap<Long, Long>()

        @Synchronized
        fun accept(key: Long, sentAtMs: Long): Boolean {
            val previous = newest[key]
            if (previous != null && sentAtMs < previous) return false
            if (previous == null && newest.size >= maxStreams) newest.clear()
            newest[key] = sentAtMs
            return true
        }

        @Synchronized
        fun clear() = newest.clear()

        companion object {
            fun key(type: Byte, stream: Int): Long = (type.toLong() shl 32) or (stream.toLong() and 0xFFFFFFFFL)
        }
    }

    class TimedAudio(sentAtMs: Long, val audio: ByteArray) {
        val isStale = System.currentTimeMillis() - sentAtMs > STALE_MS
    }

    fun decodeAudio(data: ByteArray): TimedAudio {
        val buf = ByteBuffer.wrap(data)
        buf.get()
        requireRemaining(buf, 8, "audio frame")
        val sentAt = buf.long
        val audio = readBoundedPayload(buf, MAX_AUDIO_FRAME_BYTES, "audio frame")
        return TimedAudio(sentAt, audio)
    }

    // ---- LOD/terrain data (opaque bulk payload, either direction -- see TYPE_LOD_DATA's doc
    // comment). No addressing or timestamp: the payload's own format (e.g. Distant Horizons'
    // already-compressed FullDataPayload bytes) is meaningless to this transport layer. ----

    fun encodeLodData(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_LOD_PAYLOAD_BYTES) { "LOD payload of ${payload.size} bytes exceeds ${MAX_LOD_PAYLOAD_BYTES}; split it" }
        return ByteBuffer.allocate(1 + payload.size).put(TYPE_LOD_DATA).put(payload).array()
    }

    fun decodeLodData(data: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(data)
        buf.get()
        return readBoundedPayload(buf, MAX_LOD_PAYLOAD_BYTES, "LOD payload")
    }

    // ---- Paced bulk file transfer chunk (server -> client), see MusicAssetSender. ----

    class AssetChunk(val kind: Int, val transferId: Int, val chunkIndex: Int, val totalChunks: Int, val extension: String, val data: ByteArray)

    fun encodeAssetChunk(chunk: AssetChunk): ByteArray {
        val ext = chunk.extension.toByteArray(Charsets.UTF_8)
        require(ext.size <= MAX_EXTENSION_BYTES && chunk.data.size <= ASSET_CHUNK_BYTES)
        return ByteBuffer.allocate(1 + 1 + 4 + 4 + 4 + 1 + ext.size + chunk.data.size)
            .put(TYPE_ASSET_CHUNK).put(chunk.kind.toByte())
            .putInt(chunk.transferId).putInt(chunk.chunkIndex).putInt(chunk.totalChunks)
            .put(ext.size.toByte()).put(ext).put(chunk.data)
            .array()
    }

    fun decodeAssetChunk(data: ByteArray): AssetChunk {
        val buf = ByteBuffer.wrap(data)
        buf.get()
        requireRemaining(buf, 1 + 4 + 4 + 4 + 1, "asset chunk")
        val kind = buf.get().toInt()
        val transferId = buf.int
        val chunkIndex = buf.int
        val totalChunks = buf.int
        val extLength = buf.get().toInt() and 0xFF
        if (extLength > MAX_EXTENSION_BYTES) throw MalformedMessageException("asset chunk: extension of $extLength bytes")
        if (totalChunks <= 0 || chunkIndex !in 0 until totalChunks) throw MalformedMessageException("asset chunk: index $chunkIndex of $totalChunks")
        requireRemaining(buf, extLength, "asset chunk extension")
        val ext = ByteArray(extLength).also { buf.get(it) }
        return AssetChunk(kind, transferId, chunkIndex, totalChunks, String(ext, Charsets.UTF_8), readBoundedPayload(buf, ASSET_CHUNK_BYTES, "asset chunk"))
    }

    // ---- Close-range voice relay (server -> one in-range listener): needs the speaker's entity
    // id so the client can key its per-speaker decoder and look up their live position for gain. ----

    fun encodeVoiceRelay(senderEntityId: Int, audio: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + 8 + 4 + audio.size)
            .put(TYPE_VOICE_AUDIO_RELAY).putLong(System.currentTimeMillis()).putInt(senderEntityId).put(audio)
            .array()

    class VoiceRelay(sentAtMs: Long, val senderEntityId: Int, val audio: ByteArray) {
        val isStale = System.currentTimeMillis() - sentAtMs > STALE_MS
    }

    fun decodeVoiceRelay(data: ByteArray): VoiceRelay {
        val buf = ByteBuffer.wrap(data)
        buf.get()
        requireRemaining(buf, 8 + 4, "voice relay")
        val sentAt = buf.long
        val senderEntityId = buf.int
        val audio = readBoundedPayload(buf, MAX_AUDIO_FRAME_BYTES, "voice relay")
        return VoiceRelay(sentAt, senderEntityId, audio)
    }

    // ---- Radio relay (server -> one qualifying listener, handheld or station broadcast alike --
    // see io.github.jwyoon1220.dncity.voice.RadioRelay): the richest message, carrying everything
    // io.github.jwyoon1220.dncity.voice.ClientRadioReceiver.handleRelayedFrame needs. ----

    fun encodeRadioRelay(
        senderEntityId: Int,
        audio: ByteArray,
        frequencyKhz: Double,
        modeName: String,
        senderPosition: RadioSenderPosition,
        rangeInfo: RadioRangeInfo,
    ): ByteArray {
        val modeCode = RadioMode.entries.indexOfFirst { it.name.equals(modeName, ignoreCase = true) }
        require(modeCode >= 0) { "Unknown RadioMode: $modeName" }
        return ByteBuffer.allocate(1 + 8 + 4 + 8 + 1 + 8 + 8 + 8 + 8 + 1 + audio.size)
            .put(TYPE_RADIO_AUDIO_RELAY)
            .putLong(System.currentTimeMillis())
            .putInt(senderEntityId)
            .putDouble(frequencyKhz)
            .put(modeCode.toByte())
            .putDouble(senderPosition.x).putDouble(senderPosition.y).putDouble(senderPosition.z)
            .putDouble(rangeInfo.effectiveMaxRangeBlocks)
            .put(RadioRangeInfo.flagsOf(rangeInfo).toByte())
            .put(audio)
            .array()
    }

    class RadioRelay(
        sentAtMs: Long,
        val senderEntityId: Int,
        val audio: ByteArray,
        val frequencyKhz: Double,
        val modeName: String,
        val senderPosition: RadioSenderPosition,
        val rangeInfo: RadioRangeInfo,
    ) {
        val isStale = System.currentTimeMillis() - sentAtMs > STALE_MS
    }

    fun decodeRadioRelay(data: ByteArray): RadioRelay {
        val buf = ByteBuffer.wrap(data)
        buf.get()
        requireRemaining(buf, 8 + 4 + 8 + 1 + 8 + 8 + 8 + 8 + 1, "radio relay")
        val sentAt = buf.long
        val senderEntityId = buf.int
        val frequencyKhz = buf.double
        val modeCode = buf.get().toInt()
        val x = buf.double; val y = buf.double; val z = buf.double
        val effectiveMaxRangeBlocks = buf.double
        val flags = buf.get().toInt()
        val audio = readBoundedPayload(buf, MAX_AUDIO_FRAME_BYTES, "radio relay")
        val mode = RadioMode.entries.getOrNull(modeCode)
            ?: throw MalformedMessageException("radio relay: mode code $modeCode out of range")
        return RadioRelay(
            sentAt, senderEntityId, audio, frequencyKhz, mode.name,
            RadioSenderPosition(x, y, z),
            RadioRangeInfo.of(effectiveMaxRangeBlocks, (flags and 1) != 0, (flags and 2) != 0),
        )
    }
}
