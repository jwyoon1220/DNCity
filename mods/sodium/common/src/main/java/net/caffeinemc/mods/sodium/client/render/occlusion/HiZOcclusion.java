// AGENT-DONE(claude): sodium-gpu-hiz-occlusion
package net.caffeinemc.mods.sodium.client.render.occlusion;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Frame-level driver of the Dagor-style GPU Hi-Z occlusion (see {@link HiZBuffer} for the algorithm and
 * {@link GpuDepthCapture} for the GPU side). Render thread only.
 *
 * <p>Per frame: the terrain's depth is captured right after the opaque terrain passes ({@link #onOpaqueTerrainDrawn});
 * before the section graph is searched, the newest finished capture is reprojected into the current camera
 * ({@link #prepare}); the graph search, entity culling and other mods (Distant Horizons, through
 * {@link #isBoxOccludedPublic}) then ask {@link #isBoxOccluded}.
 *
 * <p>The win is on the CPU: a culled section is neither drawn nor traversed further, so the graph search, the render
 * list / draw command building, block entity and entity rendering of everything behind it are skipped, while the GPU
 * does the depth reduction (one small full-screen pass) it is good at.
 */
public final class HiZOcclusion {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-HiZ");

    private static final HiZOcclusion INSTANCE = new HiZOcclusion();

    /**
     * While anything is culled and the camera is still, the graph is re-searched with a fresh capture at least this
     * often, so a hole opening without any section rebuild (or a missed refresh) cannot stay hidden.
     */
    private static final int STATIC_REFRESH_FRAMES = 8;

    public static HiZOcclusion instance() {
        return INSTANCE;
    }

    private final GpuDepthCapture capture = new GpuDepthCapture();
    private final HiZBuffer buffer = new HiZBuffer();
    private final Matrix4f tmpClip = new Matrix4f();

    private final Matrix4f currentClip = new Matrix4f();
    private double currentX, currentY, currentZ;
    private boolean hasCurrent;

    private long reprojectedSerial = -1;
    private long usedSerial = -1;
    private long recheckAfterSerial = Long.MAX_VALUE;
    private int framesSinceGraph;
    private boolean culledInLastGraph;

    private int testedLastGraph, culledLastGraph, culledEntities;

    private HiZOcclusion() {
    }

    public static boolean isEnabledInOptions() {
        return SodiumClientMod.options().performance.useGpuOcclusionCulling;
    }

    private boolean isActive() {
        return isEnabledInOptions() && !this.capture.isBroken() && !IrisShadowPass.isActive();
    }

    // ------------------------------------------------------------------ capture

    /** Called right after Sodium has drawn the opaque (solid + cutout) terrain of the main view. */
    public void onOpaqueTerrainDrawn(Matrix4fc projection, Matrix4fc modelView, double camX, double camY, double camZ) {
        if (!this.isActive()) {
            return;
        }

        this.capture.capture(this.tmpClip.set(projection).mul(modelView), camX, camY, camZ);
    }

    // ------------------------------------------------------------------ culling

    /**
     * Sets the camera the next culling queries are for and reprojects the newest finished capture into it.
     *
     * @param clip camera-relative projection * modelView of the frame being culled
     * @return whether occlusion queries can cull anything this frame
     */
    public boolean prepare(Matrix4fc clip, double camX, double camY, double camZ) {
        if (IrisShadowPass.isActive()) {
            return false; // the shadow view gets no occlusion; keep the main view's buffer for its entity pass
        }

        this.hasCurrent = false;
        this.buffer.invalidate();

        if (!this.isActive()) {
            return false;
        }

        this.capture.poll();

        if (!this.capture.hasReadback()) {
            return false;
        }

        double[] cam = this.capture.readbackCamera();

        this.currentClip.set(clip);
        this.currentX = camX;
        this.currentY = camY;
        this.currentZ = camZ;
        this.hasCurrent = true;

        boolean ok = this.buffer.reproject(this.capture.readback(), this.capture.readbackClip(), cam[0], cam[1], cam[2],
                clip, camX, camY, camZ);
        this.reprojectedSerial = ok ? this.capture.readbackSerial() : -1;
        return ok;
    }

    /** True if the world-space box is certainly hidden behind last frames' terrain. */
    public boolean isBoxOccluded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.buffer.isBoxOccluded(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** As {@link #isBoxOccluded}, for a world-space centre and half extent; counts the test for the debug overlay. */
    public boolean isSectionOccluded(double cx, double cy, double cz, float extent) {
        this.testedLastGraph++;

        if (this.buffer.isBoxOccluded(cx - extent, cy - extent, cz - extent, cx + extent, cy + extent, cz + extent)) {
            this.culledLastGraph++;
            return true;
        }

        return false;
    }

    public boolean isEntityBoxOccluded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (IrisShadowPass.isActive()) {
            return false; // hidden from the camera is not hidden from the sun
        }

        if (this.buffer.isBoxOccluded(minX, minY, minZ, maxX, maxY, maxZ)) {
            this.culledEntities++;
            return true;
        }

        return false;
    }

    public boolean isReady() {
        return this.buffer.isValid();
    }

    // ------------------------------------------------------------------ graph refresh bookkeeping

    /** Resets the per-graph counters; call before every section graph search. */
    public void beginGraph() {
        if (IrisShadowPass.isActive()) {
            return;
        }

        this.testedLastGraph = 0;
        this.culledLastGraph = 0;
    }

    /**
     * Call after every section graph search.
     *
     * @param used whether the search was given this buffer
     */
    public void endGraph(boolean used) {
        if (IrisShadowPass.isActive()) {
            return; // the shadow graph says nothing about the main view's graph
        }

        if (!used) {
            // this view was not prepared: whatever the buffer holds is for an older camera
            this.buffer.invalidate();
            this.hasCurrent = false;
        }

        this.culledInLastGraph = used && this.culledLastGraph > 0;
        this.usedSerial = used ? this.reprojectedSerial : -1;
        this.framesSinceGraph = 0;
    }

    /** Something other than the camera changed what is visible (a section was rebuilt or removed). */
    public void onGeometryChanged() {
        // the depth of the frame drawn with the new geometry is the next capture
        this.recheckAfterSerial = Math.min(this.recheckAfterSerial, this.capture.nextSerial());
    }

    /**
     * Called once per frame before the graph would be rebuilt. Returns true when the current graph was culled with a
     * capture that may be stale and a newer capture can correct it, so the caller should re-search the graph.
     */
    public boolean needsGraphRefresh() {
        if (IrisShadowPass.isActive()) {
            return false;
        }

        this.framesSinceGraph++;
        this.culledEntities = 0;

        if (!this.culledInLastGraph || !this.isActive()) {
            this.recheckAfterSerial = Long.MAX_VALUE;
            return false;
        }

        this.capture.poll();

        long newest = this.capture.readbackSerial();

        if (newest <= this.usedSerial) {
            return false;
        }

        if (newest >= this.recheckAfterSerial) {
            this.recheckAfterSerial = Long.MAX_VALUE;
            return true;
        }

        return this.framesSinceGraph >= STATIC_REFRESH_FRAMES;
    }

    /** Level change, renderer reload, teleport: nothing captured so far describes the new view. */
    public void reset() {
        this.capture.discard();
        this.buffer.invalidate();
        this.culledInLastGraph = false;
        this.usedSerial = -1;
        this.reprojectedSerial = -1;
        this.recheckAfterSerial = Long.MAX_VALUE;
    }

    public String getDebugString() {
        if (!isEnabledInOptions()) {
            return "Hi-Z: off";
        }

        if (this.capture.isBroken()) {
            return "Hi-Z: failed (see log)";
        }

        return String.format("Hi-Z: %s, sections %d/%d culled, entities %d", this.buffer.isValid() ? "on" : "waiting",
                this.culledLastGraph, this.testedLastGraph, this.culledEntities);
    }

    // ------------------------------------------------------------------ public, reflection-friendly entry points

    /**
     * Occlusion query for other mods (Distant Horizons calls it by reflection, so the signature is part of the
     * contract): true if the world-space box is hidden behind the near terrain Sodium drew in recent frames, as seen
     * from the camera of the current frame. Always false when the feature is off, during a shadow pass or before the
     * first capture arrives. Render thread only.
     */
    public static boolean isBoxOccludedPublic(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        HiZOcclusion hiz = INSTANCE;

        if (!hiz.hasCurrent || IrisShadowPass.isActive()) {
            return false;
        }

        return hiz.buffer.isBoxOccluded(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Whether {@link #isBoxOccludedPublic} can currently return true. */
    public static boolean isReadyPublic() {
        return INSTANCE.hasCurrent && INSTANCE.buffer.isValid() && !IrisShadowPass.isActive();
    }

    /** Iris's public API, looked up by reflection so Sodium keeps no dependency on Iris. */
    static final class IrisShadowPass {
        private static final MethodHandle IS_SHADOW_PASS = lookup();

        private static MethodHandle lookup() {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                MethodHandle handle = MethodHandles.publicLookup()
                        .findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class));
                return handle.bindTo(instance);
            } catch (ClassNotFoundException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not reach Iris's API; GPU Hi-Z occlusion assumes no shadow pass", t);
                return null;
            }
        }

        static boolean isActive() {
            if (IS_SHADOW_PASS == null) {
                return false;
            }

            try {
                return (boolean) IS_SHADOW_PASS.invokeExact();
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
