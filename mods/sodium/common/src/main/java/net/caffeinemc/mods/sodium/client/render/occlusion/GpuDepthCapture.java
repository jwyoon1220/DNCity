// AGENT-DONE(claude): sodium-gpu-hiz-occlusion
package net.caffeinemc.mods.sodium.client.render.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GPU half of the Hi-Z occlusion system (see {@link HiZBuffer}): one full-screen pass reduces the depth buffer the
 * terrain was just drawn into to a {@value HiZBuffer#WIDTH}x{@value HiZBuffer#HEIGHT} R32F image of the farthest
 * depth per block (Dagor's {@code downsample_depth_hzb}, done in one pass instead of a mip ladder), which is copied
 * into one of {@value #RING} pixel-pack buffers and fenced. A later frame maps the oldest finished buffer, so the CPU
 * never waits for the GPU (Dagor's {@code RingCPUTextureLock} with 3 textures).
 *
 * <p>Needs only OpenGL 3.2 core (texelFetch, PBOs, fence sync), which Minecraft already requires. Every piece of GL
 * state it touches is read first and put back exactly, through {@link GlStateManager} where Minecraft caches it.
 */
public final class GpuDepthCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-HiZ");

    static final int RING = 3;
    private static final int BYTES = HiZBuffer.WIDTH * HiZBuffer.HEIGHT * Float.BYTES;

    private int program;
    private int uSrcRect, uDstSize, uDepth;
    private int framebuffer;
    private int colorTexture;
    private int vertexArray;
    private int sampler;
    private final int[] pbo = new int[RING];
    private final long[] fence = new long[RING];
    private final Matrix4f[] captureClip = new Matrix4f[RING];
    private final double[][] captureCamera = new double[RING][3];
    private final long[] captureSerial = new long[RING];
    private int next;
    private long serial;
    private boolean broken;

    /** Latest result read back to the CPU. */
    private final float[] readback = new float[HiZBuffer.WIDTH * HiZBuffer.HEIGHT];
    private final Matrix4f readbackClip = new Matrix4f();
    private final double[] readbackCamera = new double[3];
    private long readbackSerial = -1;

    public GpuDepthCapture() {
        for (int i = 0; i < RING; i++) {
            this.captureClip[i] = new Matrix4f();
        }
    }

    public boolean isBroken() {
        return this.broken;
    }

    private void init() {
        GLCapabilities caps = GL.getCapabilities();

        if (!caps.OpenGL32) {
            throw new IllegalStateException("OpenGL 3.2 is required");
        }

        int vs = compile(GL20C.GL_VERTEX_SHADER, ResourceLocation.fromNamespaceAndPath("sodium", "occlusion/hiz_downsample.vsh"));
        int fs = compile(GL20C.GL_FRAGMENT_SHADER, ResourceLocation.fromNamespaceAndPath("sodium", "occlusion/hiz_downsample.fsh"));

        this.program = GL20C.glCreateProgram();
        GL20C.glAttachShader(this.program, vs);
        GL20C.glAttachShader(this.program, fs);
        GL30C.glBindFragDataLocation(this.program, 0, "out_Depth");
        GL20C.glLinkProgram(this.program);
        GL20C.glDetachShader(this.program, vs);
        GL20C.glDetachShader(this.program, fs);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);

        if (GL20C.glGetProgrami(this.program, GL20C.GL_LINK_STATUS) != GL11C.GL_TRUE) {
            throw new IllegalStateException("Hi-Z program failed to link: " + GL20C.glGetProgramInfoLog(this.program));
        }

        this.uSrcRect = GL20C.glGetUniformLocation(this.program, "u_SrcRect");
        this.uDstSize = GL20C.glGetUniformLocation(this.program, "u_DstSize");
        this.uDepth = GL20C.glGetUniformLocation(this.program, "u_Depth");

        int prevTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        this.colorTexture = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.colorTexture);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R32F, HiZBuffer.WIDTH, HiZBuffer.HEIGHT, 0, GL11C.GL_RED, GL11C.GL_FLOAT, (ByteBuffer) null);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTexture); // raw GL: Minecraft's cache never saw the change

        int prevDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        this.framebuffer = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.framebuffer);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, this.colorTexture, 0);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);

        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Hi-Z framebuffer incomplete: 0x" + Integer.toHexString(status));
        }

        this.vertexArray = GL30C.glGenVertexArrays();

        if (caps.OpenGL33) {
            // forces NEAREST / no compare whatever the depth texture's own parameters are (an incomplete mip
            // setting would otherwise make texelFetch return 0)
            this.sampler = GL33C.glGenSamplers();
            GL33C.glSamplerParameteri(this.sampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL33C.glSamplerParameteri(this.sampler, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL33C.glSamplerParameteri(this.sampler, GL30C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        }

        int prevPack = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);

        for (int i = 0; i < RING; i++) {
            this.pbo[i] = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, this.pbo[i]);
            GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER, BYTES, GL15C.GL_STREAM_READ);
        }

        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, prevPack);
    }

    private static int compile(int type, ResourceLocation name) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, ShaderLoader.getShaderSource(name));
        GL20C.glCompileShader(shader);

        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != GL11C.GL_TRUE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("Hi-Z shader " + name + " failed to compile: " + log);
        }

        return shader;
    }

    /**
     * Reduces the depth attachment of the currently bound draw framebuffer (what the terrain was just drawn into)
     * and queues its readback. Does nothing if that depth is not a 2D texture.
     *
     * @param clip camera-relative projection * modelView the depth was drawn with
     */
    public void capture(Matrix4fc clip, double camX, double camY, double camZ) {
        if (this.broken) {
            return;
        }

        try {
            if (this.program == 0) {
                this.init();
            }

            this.captureInternal(clip, camX, camY, camZ);
        } catch (Throwable t) {
            LOGGER.error("GPU Hi-Z occlusion disabled for this session", t);
            this.broken = true;
        }
    }

    private void captureInternal(Matrix4fc clip, double camX, double camY, double camZ) {
        int slot = this.next;

        if (this.fence[slot] != 0L) {
            // the ring is full: this slot's previous readback was never consumed; drop it rather than wait
            GL32C.glDeleteSync(this.fence[slot]);
            this.fence[slot] = 0L;
        }

        int drawFb = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);

        if (drawFb == 0) {
            return; // default framebuffer: no depth texture to read
        }

        int depthTexture;
        int x, y, w, h;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int type = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);

            if (type != GL11C.GL_TEXTURE) {
                return;
            }

            depthTexture = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

            IntBuffer viewport = stack.mallocInt(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            x = viewport.get(0);
            y = viewport.get(1);
            w = viewport.get(2);
            h = viewport.get(3);
        }

        if (depthTexture == 0 || w <= 0 || h <= 0) {
            return;
        }

        // ---- save state
        int readFb = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int prevProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int prevVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        int prevTexture0 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int prevSampler0 = this.sampler != 0 ? GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING) : 0;
        int prevPack = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean[] colorMask = new boolean[4];
        int[] prevViewport = {x, y, w, h};

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, mask);

            for (int i = 0; i < 4; i++) {
                colorMask[i] = mask.get(i) != 0;
            }
        }

        // ---- reduce
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, this.framebuffer);
        GlStateManager._viewport(0, 0, HiZBuffer.WIDTH, HiZBuffer.HEIGHT);
        if (blend) GlStateManager._disableBlend();
        if (cull) GlStateManager._disableCull();
        if (scissor) GlStateManager._disableScissorTest();
        GlStateManager._colorMask(true, true, true, true);

        GlStateManager._glUseProgram(this.program);
        GlStateManager._bindTexture(depthTexture);
        if (this.sampler != 0) GL33C.glBindSampler(0, this.sampler);
        GL20C.glUniform1i(this.uDepth, 0);
        GL20C.glUniform4i(this.uSrcRect, x, y, w, h);
        GL20C.glUniform2i(this.uDstSize, HiZBuffer.WIDTH, HiZBuffer.HEIGHT);
        GlStateManager._glBindVertexArray(this.vertexArray);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);

        // ---- queue readback
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, this.framebuffer);
        GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, this.pbo[slot]);
        GL11C.glReadPixels(0, 0, HiZBuffer.WIDTH, HiZBuffer.HEIGHT, GL11C.GL_RED, GL11C.GL_FLOAT, 0L);
        this.fence[slot] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        // ---- restore state
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, prevPack);
        GlStateManager._glBindVertexArray(prevVao);
        if (this.sampler != 0) GL33C.glBindSampler(0, prevSampler0);
        GlStateManager._bindTexture(prevTexture0);
        GlStateManager._activeTexture(prevActive);
        GlStateManager._glUseProgram(prevProgram);
        GlStateManager._colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        if (scissor) GlStateManager._enableScissorTest();
        if (cull) GlStateManager._enableCull();
        if (blend) GlStateManager._enableBlend();
        GlStateManager._viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFb);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFb);

        this.captureClip[slot].set(clip);
        this.captureCamera[slot][0] = camX;
        this.captureCamera[slot][1] = camY;
        this.captureCamera[slot][2] = camZ;
        this.captureSerial[slot] = this.serial++;
        this.next = (slot + 1) % RING;
    }

    /**
     * Copies the newest capture the GPU has finished into {@link #readback()} without blocking.
     *
     * @return whether a newer capture than the previous call's arrived
     */
    public boolean poll() {
        if (this.broken || this.program == 0) {
            return false;
        }

        int newest = -1;

        for (int i = 0; i < RING; i++) {
            if (this.fence[i] == 0L) {
                continue;
            }

            int result = GL32C.glClientWaitSync(this.fence[i], 0, 0L);

            if (result == GL32C.GL_ALREADY_SIGNALED || result == GL32C.GL_CONDITION_SATISFIED) {
                if (newest < 0 || this.captureSerial[i] > this.captureSerial[newest]) {
                    newest = i;
                }
            } else if (result == GL32C.GL_WAIT_FAILED) {
                GL32C.glDeleteSync(this.fence[i]);
                this.fence[i] = 0L;
            }
        }

        if (newest < 0) {
            return false;
        }

        // finished captures older than the newest one are useless now
        for (int i = 0; i < RING; i++) {
            if (this.fence[i] != 0L && this.captureSerial[i] <= this.captureSerial[newest]) {
                if (i != newest) {
                    GL32C.glDeleteSync(this.fence[i]);
                    this.fence[i] = 0L;
                }
            }
        }

        int prevPack = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, this.pbo[newest]);
        ByteBuffer mapped = GL30C.glMapBufferRange(GL21C.GL_PIXEL_PACK_BUFFER, 0, BYTES, GL30C.GL_MAP_READ_BIT);
        boolean ok = false;

        if (mapped != null) {
            FloatBuffer floats = mapped.order(ByteOrder.nativeOrder()).asFloatBuffer();
            floats.get(this.readback, 0, this.readback.length);
            ok = GL15C.glUnmapBuffer(GL21C.GL_PIXEL_PACK_BUFFER);
        }

        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, prevPack);

        GL32C.glDeleteSync(this.fence[newest]);
        this.fence[newest] = 0L;

        if (!ok) {
            return false;
        }

        this.readbackClip.set(this.captureClip[newest]);
        System.arraycopy(this.captureCamera[newest], 0, this.readbackCamera, 0, 3);
        this.readbackSerial = this.captureSerial[newest];
        return true;
    }

    public boolean hasReadback() {
        return this.readbackSerial >= 0;
    }

    public long readbackSerial() {
        return this.readbackSerial;
    }

    /** Captures issued so far; the next capture gets this serial. */
    public long nextSerial() {
        return this.serial;
    }

    public float[] readback() {
        return this.readback;
    }

    public Matrix4fc readbackClip() {
        return this.readbackClip;
    }

    public double[] readbackCamera() {
        return this.readbackCamera;
    }

    /** Forget queued and read-back captures (level change, teleport, resize). */
    public void discard() {
        for (int i = 0; i < RING; i++) {
            if (this.fence[i] != 0L) {
                GL32C.glDeleteSync(this.fence[i]);
                this.fence[i] = 0L;
            }
        }

        this.readbackSerial = -1;
    }

    public void delete() {
        this.discard();

        if (this.program != 0) {
            GL20C.glDeleteProgram(this.program);
            GL30C.glDeleteFramebuffers(this.framebuffer);
            GL11C.glDeleteTextures(this.colorTexture);
            GL30C.glDeleteVertexArrays(this.vertexArray);

            if (this.sampler != 0) {
                GL33C.glDeleteSamplers(this.sampler);
            }

            for (int i = 0; i < RING; i++) {
                GL15C.glDeleteBuffers(this.pbo[i]);
            }

            this.program = 0;
        }
    }
}
