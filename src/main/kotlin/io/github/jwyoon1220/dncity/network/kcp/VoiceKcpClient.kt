package io.github.jwyoon1220.dncity.network.kcp

import io.github.jwyoon1220.dncity.Dncity
import io.github.jwyoon1220.dncity.client.phone.PhoneCallReceiver
import io.github.jwyoon1220.dncity.music.MusicClientReceiver
import io.github.jwyoon1220.dncity.network.MusicAssetChunkPayload
import io.github.jwyoon1220.dncity.voice.ClientRadioReceiver
import io.github.jwyoon1220.dncity.voice.ClientVoiceReceiver
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import net.minecraft.client.Minecraft
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * The client side of [VoiceKcpServer] -- see that class's doc comment for the overall design.
 * Opens its own UDP socket as soon as the server's one-time handshake payload arrives
 * (`network.VoiceKcpHandshakePayload`, sent on login), authenticates with a signed
 * [VoiceKcpProtocol.encodeHello], then carries every close-range voice/radio/phone-call audio
 * frame in both directions for the rest of the session. Audio goes out (and comes in) as raw
 * datagrams, everything else over the reliable KCP lane -- see [VoiceKcpServer]'s doc comment.
 */
object VoiceKcpClient {
    private const val TICK_INTERVAL_MS = 10L

    private var group: NioEventLoopGroup? = null
    private var channel: Channel? = null
    private var kcp: NativeKcpSession? = null
    private var conv = 0
    private var remoteAddress: InetSocketAddress? = null

    /** Downlink audio streams, keyed by message type and speaker. */
    private val freshness = VoiceKcpProtocol.FreshnessGate()

    fun connect(host: String, port: Int, conv: Int, secret: Long) {
        disconnect()
        val eventLoopGroup = NioEventLoopGroup(1)
        group = eventLoopGroup
        val remote = InetSocketAddress(host, port)
        try {
            val bootstrap = Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel::class.java)
                .handler(object : ChannelInitializer<NioDatagramChannel>() {
                    override fun initChannel(ch: NioDatagramChannel) {
                        ch.pipeline().addLast(object : SimpleChannelInboundHandler<DatagramPacket>() {
                            override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
                                val bytes = ByteArray(msg.content().readableBytes())
                                msg.content().readBytes(bytes)
                                if (VoiceKcpProtocol.isRaw(bytes)) {
                                    // the socket is connected to the server, so only it can be the sender
                                    val raw = VoiceKcpProtocol.unwrapRaw(bytes)
                                    if (raw.isNotEmpty() && VoiceKcpProtocol.isAudio(raw[0])) handleMessage(raw)
                                    return
                                }
                                kcp?.input(bytes)
                                drainReceived()
                            }
                        })
                    }
                })
            // connect(), not bind(0) -- an unconnected socket accepts a datagram claiming to be
            // from anyone, so anyone who learns this ephemeral port and the plaintext conv id could
            // inject forged KCP segments straight into decode without ever touching the real
            // server. A connected UDP socket has the OS itself drop anything not from `remote`.
            val ch = bootstrap.connect(remote).sync().channel()
            channel = ch
            val session = NativeKcpSession(conv) { out -> ch.writeAndFlush(DatagramPacket(Unpooled.wrappedBuffer(out), remote)) }
            kcp = session
            this.conv = conv
            remoteAddress = remote
            session.send(VoiceKcpProtocol.encodeHello(conv, secret))
            eventLoopGroup.next().scheduleAtFixedRate({ tick() }, 0, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS)
            eventLoopGroup.next().scheduleAtFixedRate(
                { kcp?.send(VoiceKcpProtocol.encodeKeepalive()) },
                VoiceKcpProtocol.KEEPALIVE_INTERVAL_MS, VoiceKcpProtocol.KEEPALIVE_INTERVAL_MS, TimeUnit.MILLISECONDS,
            )
        } catch (e: Throwable) {
            // Throwable, not Exception -- NativeKcpSession's construction can throw
            // UnsatisfiedLinkError/ExceptionInInitializerError (both Error, not Exception) if the
            // native library fails to load, and that must degrade to "disabled this session" too,
            // same as every other native-loader failure in this repo (see engine/audio, engine/fmod).
            Dncity.LOGGER.error("VoiceKcpClient failed to connect to {}:{} -- voice/radio/phone audio disabled this session", host, port, e)
            disconnect()
        }
    }

    fun disconnect() {
        channel?.close()
        group?.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS)
        channel = null
        group = null
        kcp?.close()
        kcp = null
        remoteAddress = null
        freshness.clear()
    }

    /** Same host the game's own TCP connection uses -- an integrated (singleplayer) server has no
     * real socket to read that from, so falls back to loopback since it's running in this same
     * process either way. */
    fun resolveHost(): String {
        val minecraft = Minecraft.getInstance()
        if (minecraft.hasSingleplayerServer()) return "127.0.0.1"
        return minecraft.currentServer?.ip?.substringBeforeLast(':') ?: "127.0.0.1"
    }

    fun sendVoiceAudio(opusData: ByteArray) = sendAudio(VoiceKcpProtocol.encodeAudio(VoiceKcpProtocol.TYPE_VOICE_AUDIO, opusData))

    fun sendRadioAudio(audioData: ByteArray) = sendAudio(VoiceKcpProtocol.encodeAudio(VoiceKcpProtocol.TYPE_RADIO_AUDIO, audioData))

    fun sendPhoneCallAudio(opusData: ByteArray) = sendAudio(VoiceKcpProtocol.encodeAudio(VoiceKcpProtocol.TYPE_PHONE_CALL_AUDIO, opusData))

    /** Raw datagram ([VoiceKcpProtocol.RAW_DATAGRAM_CMD]); a message too big for one goes over the reliable lane. */
    private fun sendAudio(message: ByteArray) {
        val session = kcp ?: return
        val ch = channel
        val remote = remoteAddress
        if (ch == null || remote == null || message.size > VoiceKcpProtocol.MAX_RAW_MESSAGE_BYTES) {
            session.send(message)
            return
        }
        ch.writeAndFlush(DatagramPacket(Unpooled.wrappedBuffer(VoiceKcpProtocol.wrapRaw(conv, message)), remote))
    }

    /** Sends an opaque bulk payload (e.g. a Distant Horizons LOD/terrain request) to the server
     * over the same unified KCP channel voice/radio/phone audio already use -- see
     * [VoiceKcpProtocol.TYPE_LOD_DATA]. No-op if not currently connected. */
    fun sendLodData(payload: ByteArray) {
        kcp?.send(VoiceKcpProtocol.encodeLodData(payload))
    }

    /** Set by whichever feature wants to receive [TYPE_LOD_DATA][VoiceKcpProtocol.TYPE_LOD_DATA]
     * messages sent by the server (e.g. a future Distant Horizons LOD response) -- invoked on the
     * render/client thread, same as every other message handler here. */
    var onLodDataReceived: ((ByteArray) -> Unit)? = null

    /** [VoiceKcpProtocol.FreshnessGate] per stream: the type, plus the speaker for relays (bytes 9..12). */
    private fun isFresh(msg: ByteArray): Boolean {
        val type = msg[0]
        if (!VoiceKcpProtocol.isAudio(type)) return true
        val sentAt = VoiceKcpProtocol.sentAtMs(msg) ?: return false
        val speaker = if (type != VoiceKcpProtocol.TYPE_PHONE_CALL_AUDIO_RELAY && msg.size >= 13) {
            java.nio.ByteBuffer.wrap(msg, 9, 4).int
        } else 0
        return freshness.accept(VoiceKcpProtocol.FreshnessGate.key(type, speaker), sentAt)
    }

    private fun tick() {
        kcp?.update(System.currentTimeMillis())
        drainReceived()
    }

    private fun drainReceived() {
        val session = kcp ?: return
        while (true) {
            val msg = session.recv() ?: break
            handleMessage(msg)
        }
    }

    private fun handleMessage(msg: ByteArray) {
        if (msg.isEmpty()) return
        if (!isFresh(msg)) return
        // Hop onto the render/client thread -- these calls feed VoiceAudioMixer, which
        // VoiceClientLoop also drains from that same thread every client tick; like the
        // IPayloadHandler.enqueueWork bodies this replaced, they're not meant to run on an
        // arbitrary I/O thread (this UDP channel's own Netty event loop, see connect()).
        Minecraft.getInstance().execute {
            try {
                when (msg[0]) {
                    VoiceKcpProtocol.TYPE_VOICE_AUDIO_RELAY -> {
                        val relay = VoiceKcpProtocol.decodeVoiceRelay(msg)
                        if (!relay.isStale) ClientVoiceReceiver.handleRelayedFrame(relay.senderEntityId, relay.audio)
                    }
                    VoiceKcpProtocol.TYPE_RADIO_AUDIO_RELAY -> {
                        val relay = VoiceKcpProtocol.decodeRadioRelay(msg)
                        if (!relay.isStale) {
                            ClientRadioReceiver.handleRelayedFrame(
                                relay.senderEntityId, relay.audio, relay.frequencyKhz, relay.modeName,
                                relay.senderPosition, relay.rangeInfo.effectiveMaxRangeBlocks, relay.rangeInfo.obstructed, relay.rangeInfo.stormNoise,
                            )
                        }
                    }
                    VoiceKcpProtocol.TYPE_PHONE_CALL_AUDIO_RELAY -> {
                        val audio = VoiceKcpProtocol.decodeAudio(msg)
                        if (!audio.isStale) PhoneCallReceiver.handleFrame(audio.audio)
                    }
                    VoiceKcpProtocol.TYPE_LOD_DATA -> {
                        onLodDataReceived?.invoke(VoiceKcpProtocol.decodeLodData(msg))
                    }
                    VoiceKcpProtocol.TYPE_ASSET_CHUNK -> {
                        MusicClientReceiver.handleChunk(VoiceKcpProtocol.decodeAssetChunk(msg).let {
                            MusicAssetChunkPayload(it.kind, it.transferId, it.chunkIndex, it.totalChunks, it.extension, it.data)
                        })
                    }
                }
            } catch (e: Exception) {
                // A malformed/truncated message (bad length, out-of-range field) must not kill the
                // render thread -- see VoiceKcpProtocol's requireRemaining/readBoundedPayload.
                Dncity.LOGGER.warn("VoiceKcpClient: dropping malformed message (type {})", msg[0], e)
            }
        }
    }
}
