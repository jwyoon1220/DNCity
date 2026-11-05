// AGENT-DONE(claude): sodium-gpu-hiz-occlusion
package net.caffeinemc.mods.sodium.client.render.occlusion;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;

/**
 * CPU half of the GPU-assisted Hi-Z occlusion system, modelled on Dagor Engine's {@code Occlusion} /
 * {@code OcclusionTest} / {@code OcclusionRenderer::reprojectHWDepthBuffer} (GaijinEntertainment/DagorEngine,
 * {@code prog/engine/lib3d/occlusionSystem.cpp}, {@code prog/dagorInclude/math/dag_occlusionTest.h}):
 *
 * <ol>
 *     <li>The GPU reduces the frame's terrain depth to a tiny {@value #WIDTH}x{@value #HEIGHT} "farthest depth"
 *     image (Dagor uses the same 256x128 size), which is read back asynchronously a few frames later
 *     ({@link GpuDepthCapture}). This class never touches OpenGL.</li>
 *     <li>{@link #reproject} forward-splats that old image into the <em>current</em> camera, so camera movement
 *     between capture and use does not shift occluders on screen.</li>
 *     <li>A max-mip chain is built over the reprojected image (mip 0..{@value #MAX_TEST_MIP}, same coarsest test
 *     mip as Dagor's {@code DEFAULT_MAX_TEST_MIP}).</li>
 *     <li>{@link #isBoxOccluded} projects a box, picks the mip where its screen rectangle is a handful of texels and
 *     reports it occluded only if every covered texel's farthest occluder is nearer than the box's nearest point.</li>
 * </ol>
 *
 * <p>Differences from Dagor, all in the conservative direction (more things stay visible):
 * <ul>
 *     <li>Stored values are linear view depth ({@code w}), not reverse-Z; Minecraft uses a standard [0, 1] depth range.</li>
 *     <li>Each source texel splats a one-texel square (2x2 destination texels) and when several land on one texel
 *     the <em>farthest</em> is kept (Dagor writes one texel and keeps the nearest); every splatted depth is pushed
 *     back by {@link #DEPTH_SLACK}.</li>
 *     <li>Sky (depth 1.0) splats "open" too, so it wins over terrain on shared texels; texels nothing splatted onto
 *     occlude nothing.</li>
 *     <li>The box's screen rectangle comes from the clip-space interval of the box (centre +- |M| * extent), which is a
 *     superset of the exact 8-corner rectangle and costs four dot products instead of eight transforms.</li>
 * </ul>
 */
public final class HiZBuffer {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 128;
    public static final int MAX_TEST_MIP = 3;

    /** Reprojected depth is pushed back by this factor (Dagor scales its 1/w by 1/1.01, the same 1 %). */
    static final float DEPTH_SLACK = 1.01f;

    /** Anything with a corner nearer than this (blocks, view depth) is never culled: the near plane is 0.05. */
    static final float NEAR_W = 0.1f;

    /** A capture taken more than this many blocks away from the current camera is not reprojected. */
    static final double MAX_REPROJECT_DISTANCE = 32.0;

    private static final float UNWRITTEN = -1.0f;

    /** Window depth sky texels are reprojected at (their direction is all that matters). */
    private static final float SKY_DEPTH = 0.9999f;

    private final float[][] mips = new float[MAX_TEST_MIP + 1][];
    private final int[] mipWidth = new int[MAX_TEST_MIP + 1];
    private final int[] mipHeight = new int[MAX_TEST_MIP + 1];

    private final Matrix4f clip = new Matrix4f();
    private final Matrix4f tmp = new Matrix4f();
    private final Matrix4f tmp2 = new Matrix4f();

    private boolean valid;
    private double camX, camY, camZ;

    // clip rows of the current camera-relative clip matrix, cached for the box test
    private float r0x, r0y, r0z, r0w, r1x, r1y, r1z, r1w, r3x, r3y, r3z, r3w;

    public HiZBuffer() {
        for (int mip = 0; mip <= MAX_TEST_MIP; mip++) {
            this.mipWidth[mip] = WIDTH >> mip;
            this.mipHeight[mip] = HEIGHT >> mip;
            this.mips[mip] = new float[this.mipWidth[mip] * this.mipHeight[mip]];
        }
    }

    public boolean isValid() {
        return this.valid;
    }

    public void invalidate() {
        this.valid = false;
    }

    /**
     * Rebuilds the buffer for the current camera from a captured frame.
     *
     * @param rawDepth        {@value #WIDTH} x {@value #HEIGHT} window-space depths in [0, 1], row 0 at the bottom
     *                        (glReadPixels order); each value is the farthest depth of its block of screen pixels
     * @param captureClip     camera-relative clip matrix (projection * modelView) the captured frame was drawn with
     * @param captureX        camera position of the captured frame
     * @param currentClip     camera-relative clip matrix of the frame being culled
     * @param currentX        camera position of the frame being culled
     * @return whether the buffer is usable; false leaves it invalid (nothing will be culled)
     */
    public boolean reproject(float[] rawDepth, Matrix4fc captureClip, double captureX, double captureY, double captureZ,
                             Matrix4fc currentClip, double currentX, double currentY, double currentZ) {
        this.valid = false;

        double ddx = captureX - currentX, ddy = captureY - currentY, ddz = captureZ - currentZ;

        if (ddx * ddx + ddy * ddy + ddz * ddz > MAX_REPROJECT_DISTANCE * MAX_REPROJECT_DISTANCE) {
            return false;
        }

        Matrix4f inverse = this.tmp.set(captureClip);

        if (!Float.isFinite(inverse.determinant()) || inverse.determinant() == 0.0f) {
            return false;
        }

        inverse.invert();

        this.camX = currentX;
        this.camY = currentY;
        this.camZ = currentZ;
        this.setClip(currentClip);

        // One matrix from captured NDC to current clip space: R = currentClip * T(capture - current) * inverse.
        // inverse * ndc = hw * (old camera-relative point, 1), so R * ndc = hw * clip; the screen position needs no
        // division by hw and the view depth only one (cw = (R * ndc).w / hw).
        Matrix4f r = this.tmp2.set(currentClip).translate((float) ddx, (float) ddy, (float) ddz).mul(inverse);

        // JOML accessors are mCR (column C, row R); row R of the matrix is (m0R, m1R, m2R, m3R)
        final float ax = r.m00(), bx = r.m10(), cx = r.m20(), dx = r.m30();
        final float ay = r.m01(), by = r.m11(), cy = r.m21(), dy = r.m31();
        final float aw = r.m03(), bw = r.m13(), cw = r.m23(), dw = r.m33();
        final float ah = inverse.m03(), bh = inverse.m13(), ch = inverse.m23(), dh = inverse.m33();

        final float stepX = 2.0f / WIDTH;
        final float[] dst = this.mips[0];
        Arrays.fill(dst, UNWRITTEN);

        int zeroTexels = 0;

        for (int y = 0; y < HEIGHT; y++) {
            final float ndcY = ((y + 0.5f) / HEIGHT) * 2.0f - 1.0f;
            final float rowX = bx * ndcY + dx;
            final float rowY = by * ndcY + dy;
            final float rowW = bw * ndcY + dw;
            final float rowH = bh * ndcY + dh;
            final int rowStart = y * WIDTH;

            float ndcX = stepX * 0.5f - 1.0f;

            for (int x = 0; x < WIDTH; x++, ndcX += stepX) {
                float depth = rawDepth[rowStart + x];

                if (depth <= 0.0f) {
                    zeroTexels++;
                    continue;
                }

                // sky / far plane / NaN: reprojected like a point just short of the far plane, but splats "open"
                // (+Inf), so it also clears whatever else lands on those texels
                final boolean open = !(depth < 1.0f);
                final float ndcZ = (open ? SKY_DEPTH : depth) * 2.0f - 1.0f;

                final float hw = ah * ndcX + ch * ndcZ + rowH;
                final float clipW = aw * ndcX + cw * ndcZ + rowW;

                // view depth of the point in the current frame; also rejects hw ~ 0 and points behind the camera
                final float viewW = clipW / hw;

                if (!(viewW > NEAR_W)) {
                    continue;
                }

                final float invClipW = 1.0f / clipW;
                final float sx = (ax * ndcX + cx * ndcZ + rowX) * invClipW;
                final float sy = (ay * ndcX + cy * ndcZ + rowY) * invClipW;

                // splat a one-texel square centred on the reprojected point: it touches 2x2 texels, which closes the
                // holes forward splatting leaves when the view magnifies (moving forward, turning) by up to 2x
                final float fx = (sx * 0.5f + 0.5f) * WIDTH - 0.5f;
                final float fy = (sy * 0.5f + 0.5f) * HEIGHT - 0.5f;

                if (!(fx > -1.0f && fx < WIDTH && fy > -1.0f && fy < HEIGHT)) {
                    continue;
                }

                final int tx0 = (int) (fx + 1.0f) - 1; // floor for fx > -1
                final int ty0 = (int) (fy + 1.0f) - 1;
                final float w = open ? Float.POSITIVE_INFINITY : viewW * DEPTH_SLACK;

                // UNWRITTEN (-1) is below every real depth, so max() both writes and keeps the farthest
                if (ty0 >= 0) {
                    final int row = ty0 * WIDTH;
                    if (tx0 >= 0) dst[row + tx0] = Math.max(dst[row + tx0], w);
                    if (tx0 + 1 < WIDTH) dst[row + tx0 + 1] = Math.max(dst[row + tx0 + 1], w);
                }

                if (ty0 + 1 < HEIGHT) {
                    final int row = (ty0 + 1) * WIDTH;
                    if (tx0 >= 0) dst[row + tx0] = Math.max(dst[row + tx0], w);
                    if (tx0 + 1 < WIDTH) dst[row + tx0 + 1] = Math.max(dst[row + tx0 + 1], w);
                }
            }
        }

        // An incomplete / unreadable depth texture reads back as all zeros; a real depth of 0 cannot happen
        // (that would be geometry on the near plane), so a capture with many zeros is garbage, not occluders.
        if (zeroTexels > (WIDTH * HEIGHT) / 8) {
            return false;
        }

        for (int i = 0; i < dst.length; i++) {
            if (dst[i] == UNWRITTEN) {
                dst[i] = Float.POSITIVE_INFINITY;
            }
        }

        for (int mip = 1; mip <= MAX_TEST_MIP; mip++) {
            float[] src = this.mips[mip - 1];
            float[] out = this.mips[mip];
            int srcW = this.mipWidth[mip - 1];
            int w = this.mipWidth[mip], h = this.mipHeight[mip];

            for (int y = 0; y < h; y++) {
                int row0 = (y * 2) * srcW, row1 = row0 + srcW;

                for (int x = 0; x < w; x++) {
                    int sx = x * 2;
                    out[y * w + x] = Math.max(Math.max(src[row0 + sx], src[row0 + sx + 1]),
                            Math.max(src[row1 + sx], src[row1 + sx + 1]));
                }
            }
        }

        this.valid = true;
        return true;
    }

    private void setClip(Matrix4fc m) {
        this.clip.set(m);
        // row r of a column-major JOML matrix: (m0r, m1r, m2r, m3r)
        this.r0x = m.m00(); this.r0y = m.m10(); this.r0z = m.m20(); this.r0w = m.m30();
        this.r1x = m.m01(); this.r1y = m.m11(); this.r1z = m.m21(); this.r1w = m.m31();
        this.r3x = m.m03(); this.r3y = m.m13(); this.r3z = m.m23(); this.r3w = m.m33();
    }

    /**
     * Whether the world-space box is hidden behind the captured occluders. False when unsure (no data, box crosses
     * the near plane, box leaves the screen, any covered texel is open).
     */
    public boolean isBoxOccluded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!this.valid) {
            return false;
        }

        float cx = (float) ((minX + maxX) * 0.5 - this.camX);
        float cy = (float) ((minY + maxY) * 0.5 - this.camY);
        float cz = (float) ((minZ + maxZ) * 0.5 - this.camZ);
        float ex = (float) ((maxX - minX) * 0.5);
        float ey = (float) ((maxY - minY) * 0.5);
        float ez = (float) ((maxZ - minZ) * 0.5);

        return this.isCenteredBoxOccluded(cx, cy, cz, ex, ey, ez);
    }

    /** As {@link #isBoxOccluded}, with the box given camera-relative as centre and half extents. */
    public boolean isCenteredBoxOccluded(float cx, float cy, float cz, float ex, float ey, float ez) {
        if (!this.valid) {
            return false;
        }

        float wc = this.r3x * cx + this.r3y * cy + this.r3z * cz + this.r3w;
        float we = Math.abs(this.r3x) * ex + Math.abs(this.r3y) * ey + Math.abs(this.r3z) * ez;
        float minW = wc - we;

        if (!(minW > NEAR_W)) {
            return false;
        }

        float maxW = wc + we;

        float xc = this.r0x * cx + this.r0y * cy + this.r0z * cz + this.r0w;
        float xe = Math.abs(this.r0x) * ex + Math.abs(this.r0y) * ey + Math.abs(this.r0z) * ez;
        float yc = this.r1x * cx + this.r1y * cy + this.r1z * cz + this.r1w;
        float ye = Math.abs(this.r1x) * ex + Math.abs(this.r1y) * ey + Math.abs(this.r1z) * ez;

        // x/w over x in [xc-xe, xc+xe], w in [minW, maxW] (w > 0): extremes are at the interval corners
        float xLo = xc - xe, xHi = xc + xe, yLo = yc - ye, yHi = yc + ye;
        float ndcMinX = Math.min(xLo / minW, xLo / maxW);
        float ndcMaxX = Math.max(xHi / minW, xHi / maxW);
        float ndcMinY = Math.min(yLo / minW, yLo / maxW);
        float ndcMaxY = Math.max(yHi / minW, yHi / maxW);

        if (ndcMaxX < -1.0f || ndcMinX > 1.0f || ndcMaxY < -1.0f || ndcMinY > 1.0f) {
            return false; // off screen: the frustum test owns that decision
        }

        int x0 = clampTexel((ndcMinX * 0.5f + 0.5f) * WIDTH, WIDTH);
        int x1 = clampTexel((ndcMaxX * 0.5f + 0.5f) * WIDTH, WIDTH);
        int y0 = clampTexel((ndcMinY * 0.5f + 0.5f) * HEIGHT, HEIGHT);
        int y1 = clampTexel((ndcMaxY * 0.5f + 0.5f) * HEIGHT, HEIGHT);

        int size = Math.max(x1 - x0, y1 - y0) + 1;
        int mip = 0;

        while (mip < MAX_TEST_MIP && (size >> mip) > 4) {
            mip++;
        }

        float[] zb = this.mips[mip];
        int pitch = this.mipWidth[mip];
        int mx0 = x0 >> mip, mx1 = x1 >> mip, my0 = y0 >> mip, my1 = y1 >> mip;

        for (int y = my0; y <= my1; y++) {
            int row = y * pitch;

            for (int x = mx0; x <= mx1; x++) {
                if (zb[row + x] >= minW) {
                    return false; // the farthest occluder in this texel is not in front of the box
                }
            }
        }

        return true;
    }

    private static int clampTexel(float value, int size) {
        int t = (int) Math.floor(value);
        return t < 0 ? 0 : (t >= size ? size - 1 : t);
    }

    /** Test/debug access to one texel of a mip (farthest view depth, +Inf when open). */
    float texel(int mip, int x, int y) {
        return this.mips[mip][y * this.mipWidth[mip] + x];
    }
}
