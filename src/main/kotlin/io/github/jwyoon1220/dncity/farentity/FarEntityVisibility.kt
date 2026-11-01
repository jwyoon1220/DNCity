// AGENT-DONE(claude): far-entity-render
package io.github.jwyoon1220.dncity.farentity

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType
import io.github.jwyoon1220.dncity.Dncity
import net.neoforged.fml.ModList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Server-side line of sight for far players: [FarEntityServer] only sends a viewer the far players it could see, so a
 * modified client never receives the position of someone hidden behind terrain (an anti-wallhack measure in the spirit of
 * Valorant's "Fog of War", applied here to the long range only; vanilla tracking range is unchanged).
 *
 * The test is the same terrain march the client uses for drawing ([FarEntityMath.isOccluded]), run on the server's own
 * Distant Horizons data ([DhTerrainColumns], [EDhApiLevelType.SERVER_LEVEL]: built from real chunks, so a player's
 * buildings count once DH has re-read the chunk) on a background thread, because DH's lookups block. To keep players from
 * popping in late, the viewer's eye is also tried a step to either side and a jump up ([FarEntityMath.peekEyes]) against
 * the target's head, chest and feet, and a player that was visible stays sent for [VISIBLE_GRACE_NANOS].
 *
 * Fails open (user, 2026-10-28: far players must show up over LOD terrain): a pair is hidden only while a check has
 * positively found every line blocked. Before the first answer, without DH on the server, before DH has loaded the
 * dimension, or when a lookup throws, the player is sent. `-Ddncity.farEntities.serverOcclusion=false` turns the check off.
 */
object FarEntityVisibility {
    val enabled: Boolean = System.getProperty("dncity.farEntities.serverOcclusion", "true") != "false"

    /** A visible player stays sent this long after its last clear line (covers check latency and brief cover). DESIGN. */
    private const val VISIBLE_GRACE_NANOS = 1_500_000_000L

    /** Re-check period per pair: often near, rarer far away, where a few hundred ms changes little on screen. DESIGN. */
    private fun periodNanos(distanceBlocks: Double): Long = if (distanceBlocks < 1024.0) 200_000_000L else 500_000_000L

    /** Blocks skipped at the eye (the viewer's own block) and before the target (its own column). */
    private const val START_DISTANCE = 0.5
    private const val END_MARGIN = 1.5

    /** One (viewer, target) pair. Created and read on the server thread, answered by the check thread. */
    class PairState {
        /** Starts visible: nothing has proved the line blocked yet. */
        @Volatile var visibleUntilNanos = Long.MAX_VALUE
        @Volatile var pending = false
        var nextCheckNanos = 0L
    }

    private val dhLoaded: Boolean by lazy { ModList.get().isLoaded("distanthorizons") }

    @Volatile private var warnedNoDh = false

    private val threads = AtomicInteger()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DNCity far-entity visibility #${threads.incrementAndGet()}").apply { isDaemon = true }
    }

    /** Only touched after [dhLoaded], so DH's classes are never loaded without DH. */
    private object ServerTerrain {
        val terrain = DhTerrainColumns(EDhApiLevelType.SERVER_LEVEL)
    }

    fun isVisible(pair: PairState, nowNanos: Long): Boolean = !enabled || pair.visibleUntilNanos > nowNanos

    /**
     * Queues a check of [pair] if one is due. [eye] is the viewer's eye position, [feet] the target's position; both are
     * copied, so the check sees this tick's positions.
     */
    fun schedule(pair: PairState, dimension: String, eye: DoubleArray, feet: DoubleArray, distanceBlocks: Double, nowNanos: Long) {
        if (!enabled || pair.pending || nowNanos < pair.nextCheckNanos) return
        if (!dhLoaded) {
            if (!warnedNoDh) {
                warnedNoDh = true
                Dncity.LOGGER.warn(
                    "DNCity: Distant Horizons is not on this server, so far players are sent without a line-of-sight check.",
                )
            }
            return
        }
        pair.pending = true
        pair.nextCheckNanos = nowNanos + periodNanos(distanceBlocks)
        val targets = listOf(1.62, 1.0, 0.2).map { doubleArrayOf(feet[0], feet[1] + it, feet[2]) }
        val eyes = FarEntityMath.peekEyes(eye, targets[0])
        executor.execute {
            val visible = try {
                val columns = ServerTerrain.terrain.columns(dimension)
                // no DH world for this dimension (yet): nothing proves the line is blocked, so send
                columns == null || FarEntityMath.isVisibleFromAny(eyes, targets, START_DISTANCE, END_MARGIN, columns)
            } catch (e: Throwable) {
                Dncity.LOGGER.debug("DNCity: far-entity visibility check failed", e)
                true
            }
            if (visible) {
                pair.visibleUntilNanos = System.nanoTime() + VISIBLE_GRACE_NANOS
            } else if (pair.visibleUntilNanos == Long.MAX_VALUE) {
                // the first answer, and it is "blocked": end the before-any-answer state
                pair.visibleUntilNanos = 0L
            }
            pair.pending = false
        }
    }

    fun clear() {
        if (dhLoaded) executor.execute { ServerTerrain.terrain.clear() }
    }
}
