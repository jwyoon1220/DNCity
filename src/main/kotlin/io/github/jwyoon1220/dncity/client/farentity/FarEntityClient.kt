// AGENT-DONE(claude): far-entity-render
package io.github.jwyoon1220.dncity.client.farentity

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import io.github.jwyoon1220.dncity.Dncity
import io.github.jwyoon1220.dncity.farentity.FarEntityHooks
import io.github.jwyoon1220.dncity.farentity.FarEntityMath
import io.github.jwyoon1220.dncity.network.FarEntityRangePayload
import io.github.jwyoon1220.dncity.network.FarPlayerSnapshot
import io.github.jwyoon1220.dncity.network.FarPlayersPayload
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Matrix4f
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Draws players that are out in Distant Horizons' LOD terrain, beyond vanilla entity tracking, as real player models.
 *
 * Escape from Tarkov ties player draw distance to its "Overall Visibility" (terrain) setting; the same rule here: a
 * player is drawn as far as the terrain under them is drawn, i.e. out to DH's LOD radius, which this client reports to the
 * server ([FarEntityRangePayload]). The server sends snapshots of everyone in that radius vanilla does not track
 * ([io.github.jwyoon1220.dncity.farentity.FarEntityServer]); each becomes a [RemotePlayer] that is never added to the
 * level and is drawn with the normal [net.minecraft.client.renderer.entity.EntityRenderDispatcher] from inside the
 * level's entity pass (`mixin/MixinLevelRendererFarEntities`), so skins, armour, held guns and shader packs behave as for
 * any player. A player the level does have (vanilla range) is always left to vanilla.
 *
 * Three things vanilla's path would get wrong out there, handled here:
 * - **Far plane.** Vanilla's projection ends at 4 x render distance; a proxy past 90 % of that is drawn nearer and smaller
 *   by the same factor ([FarEntityMath.compressionScale]), which covers the same pixels.
 * - **Culling.** Sodium culls any entity outside its visible chunk sections, and vanilla's frustum has that far plane,
 *   so the proxies skip both and are frustum-tested at their compressed position.
 * - **LOD terrain does not occlude.** DH keeps its depth to itself ([DhLodTerrain]), so a background thread marches each
 *   proxy's sight lines through DH's terrain data and hides proxies with no clear line (eyes, chest, feet).
 */
object FarEntityClient {
    /** Server snapshot timestamps lag by this many send intervals before they are drawn (interpolation headroom). */
    private const val INTERPOLATION_INTERVALS = 2.0

    /** A proxy with no snapshot for this long is dropped (the server also sends explicit removals). */
    private const val TIMEOUT_NANOS = 5_000_000_000L

    /** How often each proxy's sight lines are re-marched. */
    private const val OCCLUSION_PERIOD_NANOS = 250_000_000L

    /** Proxies past this share of the far clip plane are compressed toward the eye. */
    private const val FAR_PLANE_SHARE = 0.9

    private class Proxy(val uuid: UUID) {
        val samples = FarEntityMath.SampleBuffer()
        val vehicleSamples = FarEntityMath.SampleBuffer()
        var lastReceiveNanos = 0L
        var intervalTicks = 2
        var name = ""
        var pose: Pose = Pose.STANDING
        var invisible = false
        var light = LightTexture.FULL_BRIGHT
        var pendingGear: FarPlayerSnapshot.Gear? = null

        var vehicleType: EntityType<*>? = null
        var vehicleUuid: UUID? = null
        var seat = -1

        var player: RemotePlayer? = null
        var vehicle: Entity? = null

        /** Drawn until an occlusion answer says every line is blocked (the server already sent only visible players). */
        @Volatile var occluded = false
        @Volatile var occlusionPending = false
        var nextOcclusionNanos = 0L

        var lastTickX = Double.NaN
        var lastTickZ = Double.NaN
    }

    private val proxies = HashMap<UUID, Proxy>()
    private var level: ClientLevel? = null
    private var lastSentRadius = -1
    private var ticks = 0

    /** Vehicle types whose client-side construction or rendering threw once; never tried again this session. */
    private val brokenVehicleTypes = HashSet<EntityType<*>>()

    private val dhLoaded: Boolean by lazy { ModList.get().isLoaded("distanthorizons") }

    private val occlusionThreads = AtomicInteger()
    private val occlusionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DNCity far-entity occlusion #${occlusionThreads.incrementAndGet()}").apply { isDaemon = true }
    }

    fun receive(payload: FarPlayersPayload) {
        val now = System.nanoTime()
        for (uuid in payload.removed) proxies.remove(uuid)
        for (snapshot in payload.players) {
            val proxy = proxies.getOrPut(snapshot.uuid) { Proxy(snapshot.uuid) }
            proxy.lastReceiveNanos = now
            proxy.intervalTicks = snapshot.intervalTicks.toInt().coerceAtLeast(1)
            proxy.samples.add(
                FarEntityMath.Sample(
                    now, snapshot.x, snapshot.y, snapshot.z,
                    FarEntityMath.unpackDegrees(snapshot.bodyYaw), FarEntityMath.unpackDegrees(snapshot.headYaw), FarEntityMath.unpackDegrees(snapshot.pitch),
                ),
            )
            proxy.pose = snapshot.pose
            proxy.invisible = snapshot.invisible
            val light = snapshot.light.toInt() and 0xFF
            proxy.light = LightTexture.pack(light shr 4, light and 0xF)
            snapshot.gear?.let {
                proxy.pendingGear = it
                proxy.name = it.name
            }

            val vehicle = snapshot.vehicle
            if (vehicle == null) {
                dismount(proxy)
            } else {
                if (vehicle.uuid != proxy.vehicleUuid || vehicle.type != proxy.vehicleType || vehicle.seat != proxy.seat) dismount(proxy)
                proxy.vehicleType = vehicle.type
                proxy.vehicleUuid = vehicle.uuid
                proxy.seat = vehicle.seat
                val yaw = FarEntityMath.unpackDegrees(vehicle.yaw)
                proxy.vehicleSamples.add(FarEntityMath.Sample(now, vehicle.x, vehicle.y, vehicle.z, yaw, yaw, FarEntityMath.unpackDegrees(vehicle.pitch)))
            }
        }
    }

    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) = reset()

    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val currentLevel = mc.level
        if (currentLevel !== level) {
            // proxies belong to the level they were made in; the server starts over on a dimension change too
            reset()
            level = currentLevel
        }
        if (currentLevel == null || mc.player == null) return

        if (ticks++ % 20 == 0) reportRange()

        val now = System.nanoTime()
        proxies.values.removeIf { now - it.lastReceiveNanos > TIMEOUT_NANOS }
        for (proxy in proxies.values) {
            val player = proxy.player ?: continue
            val sample = proxy.samples.at(renderTime(proxy, now)) ?: continue
            // vanilla's LivingEntity.calculateEntityAnimation, from the interpolated path instead of xo/zo
            if (!proxy.lastTickX.isNaN()) {
                val moved = FarEntityMath.horizontalDistance(sample.x, sample.z, proxy.lastTickX, proxy.lastTickZ)
                player.walkAnimation.update(minOf(moved.toFloat() * 4.0f, 1.0f), 0.4f)
            }
            proxy.lastTickX = sample.x
            proxy.lastTickZ = sample.z
            player.tickCount++
        }
        if (dhLoaded) scheduleOcclusion(currentLevel, mc, now)
    }

    /**
     * Called from `LevelRenderer.renderLevel` after the vanilla entity loop, with the same buffer source, so the proxies are
     * batched (and, under Iris, shaded) exactly like vanilla entities.
     */
    fun render(camera: Camera, modelView: Matrix4f, projection: Matrix4f, partialTick: Float, buffers: MultiBufferSource) {
        if (proxies.isEmpty()) return
        val mc = Minecraft.getInstance()
        val currentLevel = mc.level ?: return
        if (currentLevel !== level) return
        val now = System.nanoTime()
        val cam = camera.position
        val limit = mc.gameRenderer.depthFar * FAR_PLANE_SHARE
        val frustum = Frustum(modelView, projection).apply { prepare(cam.x, cam.y, cam.z) }
        val dispatcher = mc.entityRenderDispatcher
        val poseStack = PoseStack()
        val realVehicles = if (proxies.values.any { it.vehicleUuid != null }) realEntityUuids(currentLevel) else emptySet()
        val failed = ArrayList<UUID>()

        for (proxy in proxies.values) {
            if (proxy.occluded) continue
            // vanilla has the real player (or got it back): never draw both
            if (currentLevel.getPlayerByUUID(proxy.uuid) != null) continue
            val sample = proxy.samples.at(renderTime(proxy, now)) ?: continue
            val player = ensurePlayer(proxy, currentLevel) ?: continue
            applyPose(proxy, player, sample)

            val vehicleSample = proxy.vehicleSamples.at(renderTime(proxy, now))
            val vehicle = if (vehicleSample != null && proxy.vehicleUuid !in realVehicles) ensureVehicle(proxy, player, currentLevel) else null
            if (vehicle != null && vehicleSample != null) {
                placeVehicle(vehicle, vehicleSample)
                draw(vehicle, Vec3(vehicleSample.x, vehicleSample.y, vehicleSample.z), vehicleSample.bodyYaw, proxy.light, cam, limit, frustum, partialTick, poseStack, buffers) {
                    brokenVehicleTypes += vehicle.type
                    dismount(proxy)
                }
            }
            draw(player, Vec3(sample.x, sample.y, sample.z), sample.bodyYaw, proxy.light, cam, limit, frustum, partialTick, poseStack, buffers) {
                failed += proxy.uuid
            }
        }
        failed.forEach(proxies::remove)
    }

    private fun draw(
        entity: Entity, position: Vec3, yaw: Float, light: Int, cam: Vec3, limit: Double, frustum: Frustum,
        partialTick: Float, poseStack: PoseStack, buffers: MultiBufferSource, onFailure: () -> Unit,
    ) {
        val relative = position.subtract(cam)
        val scale = FarEntityMath.compressionScale(relative.length(), limit)
        val box = entity.boundingBoxForCulling
        val culled = AABB(
            cam.x + (box.minX - cam.x) * scale, cam.y + (box.minY - cam.y) * scale, cam.z + (box.minZ - cam.z) * scale,
            cam.x + (box.maxX - cam.x) * scale, cam.y + (box.maxY - cam.y) * scale, cam.z + (box.maxZ - cam.z) * scale,
        )
        if (!frustum.isVisible(culled)) return
        poseStack.pushPose()
        // scaling about the eye before the dispatcher's own translate moves the model to scale x its offset, scale x its size
        poseStack.scale(scale.toFloat(), scale.toFloat(), scale.toFloat())
        try {
            Minecraft.getInstance().entityRenderDispatcher.render(entity, relative.x, relative.y, relative.z, yaw, partialTick, poseStack, buffers, light)
        } catch (e: Throwable) {
            // a proxy is not a real level entity; a renderer that cannot cope must not take the frame down with it
            Dncity.LOGGER.warn("DNCity: could not draw far proxy {} ({}), dropping it", entity.type, entity.uuid, e)
            onFailure()
        } finally {
            poseStack.popPose()
        }
    }

    private fun renderTime(proxy: Proxy, now: Long): Long =
        now - (INTERPOLATION_INTERVALS * proxy.intervalTicks * 50_000_000L).toLong()

    private fun ensurePlayer(proxy: Proxy, level: ClientLevel): RemotePlayer? {
        proxy.player?.let { return it }
        val mc = Minecraft.getInstance()
        // the tab list has every online player's profile (with skin); the snapshot name is the fallback
        val profile = mc.connection?.getPlayerInfo(proxy.uuid)?.profile ?: GameProfile(proxy.uuid, proxy.name)
        val player = RemotePlayer(level, profile)
        player.isSilent = true
        player.noPhysics = true
        proxy.player = player
        return player
    }

    private fun applyPose(proxy: Proxy, player: RemotePlayer, sample: FarEntityMath.Sample) {
        proxy.pendingGear?.let { gear ->
            FarPlayerSnapshot.SLOTS.forEachIndexed { i, slot -> player.setItemSlot(slot, gear.items.getOrElse(i) { net.minecraft.world.item.ItemStack.EMPTY }) }
            proxy.pendingGear = null
        }
        player.setPos(sample.x, sample.y, sample.z)
        player.xo = sample.x; player.yo = sample.y; player.zo = sample.z
        player.xOld = sample.x; player.yOld = sample.y; player.zOld = sample.z
        player.yBodyRot = sample.bodyYaw; player.yBodyRotO = sample.bodyYaw
        player.yRot = sample.bodyYaw; player.yRotO = sample.bodyYaw
        player.yHeadRot = sample.headYaw; player.yHeadRotO = sample.headYaw
        player.xRot = sample.pitch; player.xRotO = sample.pitch
        if (player.pose != proxy.pose) player.pose = proxy.pose
        player.isInvisible = proxy.invisible
    }

    private fun ensureVehicle(proxy: Proxy, player: RemotePlayer, level: ClientLevel): Entity? {
        proxy.vehicle?.let { return it }
        val type = proxy.vehicleType ?: return null
        if (type in brokenVehicleTypes) return null
        val vehicle = try {
            type.create(level)?.also { v ->
                proxy.vehicleUuid?.let(v::setUUID)
                v.isSilent = true
                v.noPhysics = true
                FarEntityHooks.prepareProxyMount(v, proxy.seat)
                // seated (and, for SuperbWarfare, hidden by seat) the way the real one is
                player.startRiding(v, true)
            }
        } catch (e: Throwable) {
            Dncity.LOGGER.warn("DNCity: cannot make a far proxy of vehicle type {}, drawing its riders without it", type, e)
            brokenVehicleTypes += type
            null
        }
        proxy.vehicle = vehicle
        return vehicle
    }

    private fun placeVehicle(vehicle: Entity, sample: FarEntityMath.Sample) {
        vehicle.setPos(sample.x, sample.y, sample.z)
        vehicle.xo = sample.x; vehicle.yo = sample.y; vehicle.zo = sample.z
        vehicle.xOld = sample.x; vehicle.yOld = sample.y; vehicle.zOld = sample.z
        vehicle.yRot = sample.bodyYaw; vehicle.yRotO = sample.bodyYaw
        vehicle.xRot = sample.pitch; vehicle.xRotO = sample.pitch
    }

    private fun dismount(proxy: Proxy) {
        if (proxy.vehicle != null) {
            try {
                proxy.player?.stopRiding()
            } catch (e: Throwable) {
                // the vehicle's own removePassenger ran on an entity that is not in any level; drop the pair instead
                Dncity.LOGGER.debug("DNCity: far proxy dismount failed", e)
                proxy.player = null
            }
        }
        proxy.vehicle = null
        proxy.vehicleType = null
        proxy.vehicleUuid = null
        proxy.seat = -1
    }

    private fun realEntityUuids(level: ClientLevel): Set<UUID> {
        val set = HashSet<UUID>()
        for (entity in level.entitiesForRendering()) set += entity.uuid
        return set
    }

    private fun reportRange() {
        val radius = if (dhLoaded) DhLodTerrain.lodRadiusBlocks() else 0
        if (radius == lastSentRadius) return
        // a server without DNCity (or an older one) has no such channel
        if (Minecraft.getInstance().connection?.hasChannel(FarEntityRangePayload.TYPE) != true) return
        PacketDistributor.sendToServer(FarEntityRangePayload(radius))
        lastSentRadius = radius
    }

    private fun scheduleOcclusion(level: ClientLevel, mc: Minecraft, now: Long) {
        val camera = mc.gameRenderer.mainCamera.position
        // vanilla chunks are in the depth buffer; only the LOD stretch beyond them needs marching
        val startDistance = mc.options.effectiveRenderDistance * 16.0 - 16.0
        val dimension = level.dimension().location().toString()
        for (proxy in proxies.values) {
            if (proxy.occlusionPending || now < proxy.nextOcclusionNanos) continue
            val sample = proxy.samples.at(renderTime(proxy, now)) ?: continue
            val eye = doubleArrayOf(camera.x, camera.y, camera.z)
            val targets = listOf(sample.y + 1.62, sample.y + 1.0, sample.y + 0.2).map { doubleArrayOf(sample.x, it, sample.z) }
            proxy.occlusionPending = true
            proxy.nextOcclusionNanos = now + OCCLUSION_PERIOD_NANOS
            occlusionExecutor.execute {
                val occluded = try {
                    val columns = DhLodTerrain.columns(dimension)
                    // no DH world yet: nothing is drawn out there either, so nothing hides the proxy
                    columns != null && targets.all { FarEntityMath.isOccluded(eye, it, startDistance, endMargin = 1.5, columns = columns) }
                } catch (e: Throwable) {
                    Dncity.LOGGER.debug("DNCity: far-entity occlusion test failed", e)
                    false
                }
                proxy.occluded = occluded
                proxy.occlusionPending = false
            }
        }
    }

    private fun reset() {
        proxies.clear()
        lastSentRadius = -1
        if (dhLoaded) occlusionExecutor.execute { DhLodTerrain.clear() }
    }
}
