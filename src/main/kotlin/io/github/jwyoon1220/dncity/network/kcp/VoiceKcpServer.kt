package io.github.jwyoon1220.dncity.network.kcp

import io.github.jwyoon1220.dncity.Dncity
import io.github.jwyoon1220.dncity.phone.PhoneCallSession
import io.github.jwyoon1220.dncity.voice.RadioRelay
import io.github.jwyoon1220.dncity.voice.VoiceRelay
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import net.minecraft.server.MinecraftServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * The server side of this mod's self-built KCP-over-Netty UDP transport for close-range voice,
 * radio (handheld and station broadcast alike), and phone-call audio -- replacing what used to be
 * plain Minecraft [net.minecraft.network.protocol.common.custom.CustomPacketPayload] traffic (see
 * `network.VoiceNetworking`/`RadioNetworking`/`PhoneNetworking`'s git history). Only the one-time
 * per-player handshake (`network.VoiceKcpHandshakePayload`, conv id + shared secret + this port)
 * still rides Minecraft's own channel -- everything after that is raw UDP.
 *
 * One [Kcp] session per logged-in player, keyed by a random 32-bit `conv` id the player's client
 * proves ownership of via a signed [VoiceKcpProtocol.encodeHello] before any of its audio is
 * trusted (a UDP datagram is otherwise unauthenticated and easy to spoof -- see [beginSession]).
 * Each session's [Kcp.update] is driven by a task scheduled directly on the channel's own Netty
 * event loop (see [start]) rather than Minecraft's server tick, so voice latency isn't bounded by
 * the game's 50ms tick rate.
 *
 * Two lanes share the socket: audio goes as single unacknowledged datagrams
 * ([VoiceKcpProtocol.RAW_DATAGRAM_CMD]) so a lost frame never holds the next ones back, everything
 * else (HELLO, LOD data, file chunks) through the reliable, ordered KCP session. A raw datagram is
 * only accepted from an authenticated session's current address.
 */
object VoiceKcpServer {
    const val DEFAULT_PORT = 25577
    private const val TICK_INTERVAL_MS = 10L
    private const val PENDING_SESSION_TIMEOUT_MS = 60_000L
    private const val SESSION_IDLE_TIMEOUT_MS = 120_000L

    private class PendingSession(val playerUuid: UUID, val secret: Long, val createdAtMs: Long)

    private class ServerSession(val conv: Int, val playerUuid: UUID, val secret: Long) {
        var remote: InetSocketAddress? = null
        var authenticated = false
        var lastActivityMs = System.currentTimeMillis()
        lateinit var kcp: NativeKcpSession

        /** Uplink audio streams of this player, keyed by message type. */
        val freshness = VoiceKcpProtocol.FreshnessGate()
    }

    private var group: NioEventLoopGroup? = null
    private var channel: Channel? = null
    private var boundPort = DEFAULT_PORT
    private var mcServer: MinecraftServer? = null

    private val pendingByConv = ConcurrentHashMap<Int, PendingSession>()
    private val sessionsByConv = ConcurrentHashMap<Int, ServerSession>()
    private val sessionsByPlayer = ConcurrentHashMap<UUID, ServerSession>()

    fun attachServer(server: MinecraftServer) {
        mcServer = server
    }

    fun boundPort(): Int = boundPort

    fun start() {
        if (group != null) return
        boundPort = System.getProperty("dncity.voiceKcpPort")?.toIntOrNull() ?: DEFAULT_PORT
        val eventLoopGroup = NioEventLoopGroup(1)
        group = eventLoopGroup
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
                                onDatagram(bytes, msg.sender())
                            }
                        })
                    }
                })
            channel = bootstrap.bind(boundPort).sync().channel()
            eventLoopGroup.next().scheduleAtFixedRate({ tickAll() }, 0, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS)
            Dncity.LOGGER.info("VoiceKcpServer bound on UDP port {}", boundPort)
        } catch (e: Exception) {
            Dncity.LOGGER.error("VoiceKcpServer failed to bind UDP port {} -- voice/radio/phone audio disabled this session", boundPort, e)
            eventLoopGroup.shutdownGracefully()
            group = null
            channel = null
        }
    }

    fun stop() {
        channel?.close()
        group?.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS)
        channel = null
        group = null
        mcServer = null
        pendingByConv.clear()
        sessionsByConv.values.forEach { it.kcp.close() }
        sessionsByConv.clear()
        sessionsByPlayer.clear()
    }

    /** Allocates a fresh conv/secret pair for a just-logged-in player -- handed to their client
     * via `network.VoiceKcpHandshakePayload` (still sent over Minecraft's own channel, since the
     * client has no other way to learn which UDP session belongs to it). */
    fun beginSession(playerUuid: UUID): Pair<Int, Long> {
        val random = ThreadLocalRandom.current()
        var conv: Int
        do conv = random.nextInt() while (conv == 0 || pendingByConv.containsKey(conv) || sessionsByConv.containsKey(conv))
        val secret = random.nextLong()
        pendingByConv[conv] = PendingSession(playerUuid, secret, System.currentTimeMillis())
        return conv to secret
    }

    fun endSession(playerUuid: UUID) {
        sessionsByPlayer.remove(playerUuid)?.let {
            sessionsByConv.remove(it.conv)
            it.kcp.close()
        }
        pendingByConv.entries.removeIf { it.value.playerUuid == playerUuid }
    }

    fun sendVoiceRelay(playerUuid: UUID, senderEntityId: Int, opusData: ByteArray) {
        authenticatedSession(playerUuid)?.let { sendAudio(it, VoiceKcpProtocol.encodeVoiceRelay(senderEntityId, opusData)) }
    }

    fun sendRadioRelay(
        playerUuid: UUID,
        senderEntityId: Int,
        audioData: ByteArray,
        frequencyKhz: Double,
        modeName: String,
        senderPosition: io.github.jwyoon1220.dncity.network.RadioSenderPosition,
        rangeInfo: io.github.jwyoon1220.dncity.network.RadioRangeInfo,
    ) {
        authenticatedSession(playerUuid)?.let {
            sendAudio(it, VoiceKcpProtocol.encodeRadioRelay(senderEntityId, audioData, frequencyKhz, modeName, senderPosition, rangeInfo))
        }
    }

    fun sendPhoneCallRelay(playerUuid: UUID, opusData: ByteArray) {
        authenticatedSession(playerUuid)?.let { sendAudio(it, VoiceKcpProtocol.encodeAudio(VoiceKcpProtocol.TYPE_PHONE_CALL_AUDIO_RELAY, opusData)) }
    }

    /** Sends one file chunk over the reliable lane; false (nothing sent) if the player has no session. */
    fun sendAssetChunk(playerUuid: UUID, chunk: VoiceKcpProtocol.AssetChunk): Boolean {
        val session = authenticatedSession(playerUuid) ?: return false
        session.kcp.send(VoiceKcpProtocol.encodeAssetChunk(chunk))
        return true
    }

    /** One audio message as a raw datagram ([VoiceKcpProtocol.RAW_DATAGRAM_CMD]); too big for one datagram -> reliable lane. */
    private fun sendAudio(session: ServerSession, message: ByteArray) {
        val remote = session.remote
        val ch = channel
        if (remote == null || ch == null || message.size > VoiceKcpProtocol.MAX_RAW_MESSAGE_BYTES) {
            session.kcp.send(message)
            return
        }
        ch.writeAndFlush(DatagramPacket(Unpooled.wrappedBuffer(VoiceKcpProtocol.wrapRaw(session.conv, message)), remote))
    }

    /** Sends an opaque bulk payload (e.g. Distant Horizons LOD/terrain data) to one player over
     * the same unified KCP channel voice/radio/phone audio already use -- see
     * [VoiceKcpProtocol.TYPE_LOD_DATA]. No-op if the player has no authenticated session. */
    fun sendLodData(playerUuid: UUID, payload: ByteArray) {
        authenticatedSession(playerUuid)?.kcp?.send(VoiceKcpProtocol.encodeLodData(payload))
    }

    /** Set by whichever feature wants to receive [TYPE_LOD_DATA][VoiceKcpProtocol.TYPE_LOD_DATA]
     * messages sent by a client (e.g. a future Distant Horizons request payload) -- invoked on the
     * server's main thread, same as every other message handler here. */
    var onLodDataReceived: ((java.util.UUID, ByteArray) -> Unit)? = null

    private fun authenticatedSession(playerUuid: UUID): ServerSession? =
        sessionsByPlayer[playerUuid]?.takeIf { it.authenticated }

    private fun onDatagram(bytes: ByteArray, sender: InetSocketAddress) {
        if (bytes.size < 4) return
        val conv = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or ((bytes[3].toInt() and 0xFF) shl 24)

        if (VoiceKcpProtocol.isRaw(bytes)) {
            // raw messages are never a session's first contact: only an authenticated session, only from
            // the address its (reliable, HELLO-checked) KCP traffic comes from
            val raw = sessionsByConv[conv] ?: return
            if (!raw.authenticated || raw.remote != sender) return
            raw.lastActivityMs = System.currentTimeMillis()
            val msg = VoiceKcpProtocol.unwrapRaw(bytes)
            if (msg.isNotEmpty() && msg[0] != VoiceKcpProtocol.TYPE_HELLO) handleMessage(raw, msg)
            return
        }

        var session = sessionsByConv[conv]
        if (session == null) {
            val pending = pendingByConv[conv] ?: return // unknown conv -- drop (no session was ever allocated for it)
            val newSession = ServerSession(conv, pending.playerUuid, pending.secret)
            newSession.kcp = NativeKcpSession(conv) { out -> channel?.writeAndFlush(DatagramPacket(Unpooled.wrappedBuffer(out), newSession.remote ?: sender)) }
            session = newSession
            sessionsByConv[conv] = session
        }
        session.remote = sender
        session.lastActivityMs = System.currentTimeMillis()
        session.kcp.input(bytes)

        while (true) {
            val msg = session.kcp.recv() ?: break
            handleMessage(session, msg)
        }
    }

    private fun handleMessage(session: ServerSession, msg: ByteArray) {
        if (msg.isEmpty()) return
        try {
            handleMessageChecked(session, msg)
        } catch (e: Exception) {
            // A malformed/truncated/spoofed message (bad length, out-of-range field) must not kill
            // this session's I/O thread -- see VoiceKcpProtocol's requireRemaining/readBoundedPayload.
            Dncity.LOGGER.warn("VoiceKcpServer: dropping malformed message (type {}) from conv {}", msg[0], session.conv, e)
        }
    }

    private fun handleMessageChecked(session: ServerSession, msg: ByteArray) {
        if (msg[0] == VoiceKcpProtocol.TYPE_HELLO) {
            val hello = VoiceKcpProtocol.decodeHello(msg)
            if (hello.conv == session.conv && hello.secret == session.secret) {
                pendingByConv.remove(session.conv)
                sessionsByPlayer.put(session.playerUuid, session)?.takeIf { it !== session }?.let { old -> sessionsByConv.remove(old.conv); old.kcp.close() }
                session.authenticated = true
            } else {
                sessionsByConv.remove(session.conv) // bad secret -- reject, don't trust this conv going forward
                session.kcp.close()
            }
            return
        }

        if (!session.authenticated) return
        val type = msg[0]
        // lastActivityMs and remote were already refreshed by onDatagram
        if (type == VoiceKcpProtocol.TYPE_KEEPALIVE) return
        if (VoiceKcpProtocol.isAudio(type)) {
            val sentAt = VoiceKcpProtocol.sentAtMs(msg) ?: return
            if (!session.freshness.accept(VoiceKcpProtocol.FreshnessGate.key(type, 0), sentAt)) return
        }
        val server = mcServer ?: return
        // Hop onto the server's own main thread -- these calls touch ServerLevel/player state
        // that (like the IPayloadHandler.enqueueWork bodies this replaced) isn't meant to be
        // touched from an arbitrary I/O thread, which is what's calling handleMessage here (the
        // UDP channel's own Netty event loop, see start()).
        server.execute {
            val player = server.playerList.getPlayer(session.playerUuid) ?: return@execute
            try {
                when (msg[0]) {
                    VoiceKcpProtocol.TYPE_VOICE_AUDIO -> {
                        val audio = VoiceKcpProtocol.decodeAudio(msg)
                        if (!audio.isStale) VoiceRelay.relay(player, audio.audio)
                    }
                    VoiceKcpProtocol.TYPE_RADIO_AUDIO -> {
                        val audio = VoiceKcpProtocol.decodeAudio(msg)
                        if (!audio.isStale) RadioRelay.relay(player, audio.audio)
                    }
                    VoiceKcpProtocol.TYPE_PHONE_CALL_AUDIO -> {
                        val audio = VoiceKcpProtocol.decodeAudio(msg)
                        if (!audio.isStale) PhoneCallSession.relayAudio(player, audio.audio)
                    }
                    VoiceKcpProtocol.TYPE_LOD_DATA -> {
                        onLodDataReceived?.invoke(session.playerUuid, VoiceKcpProtocol.decodeLodData(msg))
                    }
                }
            } catch (e: Exception) {
                Dncity.LOGGER.warn("VoiceKcpServer: dropping malformed message (type {}) from {}", msg[0], player.gameProfile.name, e)
            }
        }
    }

    private fun tickAll() {
        val now = System.currentTimeMillis()
        val it = sessionsByConv.entries.iterator()
        while (it.hasNext()) {
            val (_, session) = it.next()
            if (now - session.lastActivityMs > SESSION_IDLE_TIMEOUT_MS) {
                it.remove()
                sessionsByPlayer.remove(session.playerUuid, session)
                session.kcp.close()
                continue
            }
            session.kcp.update(now)
        }
        pendingByConv.entries.removeIf { now - it.value.createdAtMs > PENDING_SESSION_TIMEOUT_MS }
    }
}
