package io.github.jwyoon1220.dncity.client.phone.nanovg

import io.github.jwyoon1220.dncity.client.phone.PhoneCallManager
import io.github.jwyoon1220.dncity.phone.PhoneCallState
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGLUFramebuffer
import org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER
import org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT
import org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE
import org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT
import org.lwjgl.nanovg.NanoVG.nvgBeginFrame
import org.lwjgl.nanovg.NanoVG.nvgBeginPath
import org.lwjgl.nanovg.NanoVG.nvgCircle
import org.lwjgl.nanovg.NanoVG.nvgCreateFontMem
import org.lwjgl.nanovg.NanoVG.nvgEndFrame
import org.lwjgl.nanovg.NanoVG.nvgFill
import org.lwjgl.nanovg.NanoVG.nvgFillColor
import org.lwjgl.nanovg.NanoVG.nvgFontFace
import org.lwjgl.nanovg.NanoVG.nvgFontSize
import org.lwjgl.nanovg.NanoVG.nvgRGBA
import org.lwjgl.nanovg.NanoVG.nvgRestore
import org.lwjgl.nanovg.NanoVG.nvgRoundedRect
import org.lwjgl.nanovg.NanoVG.nvgSave
import org.lwjgl.nanovg.NanoVG.nvgStroke
import org.lwjgl.nanovg.NanoVG.nvgStrokeColor
import org.lwjgl.nanovg.NanoVG.nvgStrokeWidth
import org.lwjgl.nanovg.NanoVG.nvgText
import org.lwjgl.nanovg.NanoVG.nvgTextAlign
import org.lwjgl.nanovg.NanoVG.nvgTranslate
import org.lwjgl.nanovg.NanoVGGL3
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class PhonePage { HOME, DIALER, MESSAGES }

sealed class PhoneAction {
    data class Navigate(val page: PhonePage) : PhoneAction()
    data class KeypadDigit(val digit: Char) : PhoneAction()
    object KeypadBackspace : PhoneAction()
    object Call : PhoneAction()
    object Accept : PhoneAction()
    object Decline : PhoneAction()
    object Hangup : PhoneAction()
    object SendMessage : PhoneAction()
    /** Opens VoxelMap's fullscreen world map screen directly, replacing the phone screen -- see [io.github.jwyoon1220.dncity.client.phone.PhoneFragment.handleAction]. */
    object OpenMap : PhoneAction()
    /** Closes the phone and opens the market UI (asks the server through `/shop`, see PhoneFragment.handleAction). */
    object OpenShop : PhoneAction()
    object OpenAmmoShop : PhoneAction()
}

private data class HitRect(val x: Float, val y: Float, val w: Float, val h: Float, val action: PhoneAction) {
    fun contains(px: Float, py: Float) = px in x..(x + w) && py in y..(y + h)
}

/**
 * Owns the NanoVG rendering surface for the phone screen -- an offscreen FBO NanoVG draws into,
 * read back to CPU each frame and handed to [io.github.jwyoon1220.dncity.client.phone.nanovg.PhoneCanvasView]
 * (which uploads it into a ModernUI [icyllis.modernui.graphics.Bitmap]/[icyllis.modernui.graphics.Image]
 * on ModernUI's own UI thread). This offscreen-FBO-then-composite approach was chosen deliberately
 * over interleaving NanoVG calls directly inside a Screen's render pass: this codebase has no
 * existing precedent for safely mixing raw GL state (bound shader/texture/VAO/blend) with
 * Minecraft's RenderSystem, and NanoVG's own GL calls would otherwise risk corrupting whatever
 * Minecraft draws next in the same frame (tooltips, F3 overlay, etc).
 *
 * [create]/[resize]/[destroy]/[renderFrame] must all run on Minecraft's render thread (the same
 * thread this project's existing `Minecraft.getInstance().execute { ... }` hops target elsewhere
 * in the phone code) -- that's also the only thread with Minecraft's GL context current. ModernUI
 * runs its own separate UI thread for View/Canvas work, which must never touch raw GL directly;
 * [PhoneCanvasView] only ever reads the published (thread-safe, volatile) frame/hit-rect state
 * this object produces, never NanoVG/GL APIs themselves.
 */
object PhoneNanoVgSurface {
    const val PANEL_WIDTH = 360
    /** Galaxy-style outer bezel/frame width -- the "screen" content area is inset by this on every side. */
    const val BEZEL = 14
    const val STATUS_BAR_HEIGHT = 32
    const val BANNER_HEIGHT = 36
    const val CONTENT_Y = STATUS_BAR_HEIGHT + BANNER_HEIGHT
    const val CONTENT_HEIGHT = 680
    const val PANEL_HEIGHT = CONTENT_Y + CONTENT_HEIGHT

    // Shared layout constants PhoneFragment positions its EditText overlays against, so the
    // NanoVG-drawn field backgrounds and the real (invisible-background) EditText widgets that
    // actually receive typed input line up exactly.
    const val DIALER_FIELD_Y = CONTENT_Y + 16
    const val DIALER_FIELD_HEIGHT = 40
    const val MESSAGE_TO_FIELD_Y = CONTENT_Y + 16
    const val MESSAGE_TO_FIELD_HEIGHT = 32
    const val MESSAGE_BODY_FIELD_Y = MESSAGE_TO_FIELD_Y + MESSAGE_TO_FIELD_HEIGHT + 12
    const val MESSAGE_BODY_FIELD_HEIGHT = 160
    const val FIELD_MARGIN = 16

    @Volatile
    var currentPage: PhonePage = PhonePage.HOME
        private set

    /** Latest composited frame, RGBA8 packed into `int`s the way [io.github.jwyoon1220.dncity.client.phone.nanovg.PhoneCanvasView] expects for `Bitmap.setPixels`. Null until the first [renderFrame]. */
    @Volatile
    var latestFrame: IntArray? = null
        private set

    private var hitRects: List<HitRect> = emptyList()

    private var ctx = 0L
    private var fb: NVGLUFramebuffer? = null
    private var fontBuffer: java.nio.ByteBuffer? = null
    private var readBuffer: java.nio.ByteBuffer? = null
    private var width = 0
    private var height = 0

    // NVGColor.create() (no-arg) backs its struct with a plain BufferUtils/JDK direct ByteBuffer,
    // not native-allocator (jemalloc) memory -- Struct.free() unconditionally calls
    // MemoryUtil.nmemFree() on it regardless, which corrupts jemalloc's heap (confirmed by hand:
    // this crashed the game -- always in unrelated Sodium chunk-buffer code, since heap
    // corruption surfaces at the next affected free(), not at the bad free() itself). So this
    // scratch color is allocated once and never explicitly freed -- its backing ByteBuffer is
    // reclaimed normally by the JVM's own GC/Cleaner when [destroy] drops the reference.
    private var scratchColor: NVGColor? = null

    /** No-op if already created at this size. Render-thread only. */
    fun create(width: Int = PANEL_WIDTH, height: Int = PANEL_HEIGHT) {
        if (ctx != 0L && this.width == width && this.height == height) return
        destroy()

        ctx = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS or NanoVGGL3.NVG_STENCIL_STROKES)
        check(ctx != 0L) { "PhoneNanoVgSurface: nvgCreate failed" }

        val fontBytes = javaClass.classLoader
            .getResourceAsStream("assets/modernui/font/NotoSansKR-Regular.ttf")
            ?.use { it.readBytes() }
            ?: error("PhoneNanoVgSurface: NotoSansKR-Regular.ttf resource not found on classpath")
        val buffer = BufferUtils.createByteBuffer(fontBytes.size)
        buffer.put(fontBytes).flip()
        fontBuffer = buffer
        check(nvgCreateFontMem(ctx, "phone", buffer, false) >= 0) { "PhoneNanoVgSurface: failed to load bundled font" }

        fb = NanoVGGL3.nvgluCreateFramebuffer(ctx, width, height, 0)
            ?: error("PhoneNanoVgSurface: nvgluCreateFramebuffer failed")
        readBuffer = BufferUtils.createByteBuffer(width * height * 4)
        scratchColor = NVGColor.create()
        this.width = width
        this.height = height
        currentPage = PhonePage.HOME
    }

    /** Render-thread only. Safe to call even if not created (no-op). */
    fun destroy() {
        fb?.let { NanoVGGL3.nvgluDeleteFramebuffer(ctx, it) }
        fb = null
        if (ctx != 0L) NanoVGGL3.nvgDelete(ctx)
        ctx = 0L
        fontBuffer = null
        readBuffer = null
        scratchColor = null
        width = 0
        height = 0
        latestFrame = null
        hitRects = emptyList()
    }

    fun navigate(page: PhonePage) {
        currentPage = page
    }

    /**
     * Called from the phone canvas view's touch handler (ModernUI UI thread) -- pure geometry
     * lookup, no GL/render-thread requirement. [x]/[y] are in full-panel (untranslated) pixel
     * coordinates; recorded hit rects are in screen-local (post-[BEZEL]-translate) coordinates,
     * same as everything [drawFrame] draws after `nvgTranslate(ctx, BEZEL, BEZEL)`.
     */
    fun hitTest(x: Float, y: Float): PhoneAction? =
        hitRects.firstOrNull { it.contains(x - BEZEL, y - BEZEL) }?.action

    /**
     * Render-thread only. No-op if [create] hasn't been called (or [destroy] already ran).
     *
     * NanoVG's own GL calls (bound shader program, VAO/VBO, active texture, blend state) share
     * Minecraft's GL context, and this runs at tick time -- interleaved with, not isolated from,
     * whatever Minecraft/ModernUI's own GL state Machine expects on the *next* render pass on
     * this same thread. Every bit of global GL state NanoVG or this method itself touches is
     * saved before and restored after, not just the framebuffer/viewport -- confirmed necessary
     * by hand: without this, the game flickered and then crashed after a few seconds with the
     * phone open, consistent with leftover NanoVG GL state (bound program/VAO/blend func)
     * corrupting Minecraft's/ModernUI's own subsequent draw calls.
     */
    fun renderFrame() {
        val fb = fb ?: return
        val readBuffer = readBuffer ?: return

        val savedFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        val savedViewport = IntArray(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport)
        val savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val savedVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val savedArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val savedElementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)
        val savedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val savedTexture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        val savedBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val savedBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val savedBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val savedBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        val cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)

        NanoVGGL3.nvgluBindFramebuffer(ctx, fb)
        GL11.glViewport(0, 0, width, height)
        GL11.glClearColor(0f, 0f, 0f, 0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_STENCIL_BUFFER_BIT)

        val recordedHitRects = ArrayList<HitRect>()
        nvgBeginFrame(ctx, width.toFloat(), height.toFloat(), 1f)
        drawFrame(recordedHitRects)
        nvgEndFrame(ctx)
        hitRects = recordedHitRects

        readBuffer.clear()
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuffer)
        latestFrame = bytesToArgb(readBuffer, width, height)

        NanoVGGL3.nvgluBindFramebuffer(ctx, null)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo)
        GL11.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3])
        GL20.glUseProgram(savedProgram)
        GL30.glBindVertexArray(savedVao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedArrayBuffer)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, savedElementArrayBuffer)
        GL13.glActiveTexture(savedActiveTexture)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTexture2d)
        GL14.glBlendFuncSeparate(savedBlendSrcRgb, savedBlendDstRgb, savedBlendSrcAlpha, savedBlendDstAlpha)
        setEnabled(GL11.GL_BLEND, blendWasEnabled)
        setEnabled(GL11.GL_DEPTH_TEST, depthTestWasEnabled)
        setEnabled(GL11.GL_SCISSOR_TEST, scissorWasEnabled)
        setEnabled(GL11.GL_STENCIL_TEST, stencilWasEnabled)
        setEnabled(GL11.GL_CULL_FACE, cullWasEnabled)
    }

    private fun setEnabled(cap: Int, enabled: Boolean) {
        if (enabled) GL11.glEnable(cap) else GL11.glDisable(cap)
    }

    /**
     * NanoVG's FBO is RGBA8 (byte order R,G,B,A); ModernUI's `Bitmap.setPixels` expects packed
     * ARGB ints (0xAARRGGBB), matching `android.graphics.Bitmap`'s convention. Also un-flips:
     * `glReadPixels` returns rows bottom-to-top (OpenGL's window-space convention), but
     * `Bitmap`/`Canvas`/every other 2D API here is top-to-bottom, so row 0 of the source buffer
     * (the bottom of what NanoVG drew) has to land in the *last* output row, not the first --
     * without this the composited image comes out upside-down.
     */
    private fun bytesToArgb(buffer: java.nio.ByteBuffer, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        buffer.rewind()
        for (srcRow in 0 until h) {
            val dstRow = h - 1 - srcRow
            var dstIndex = dstRow * w
            repeat(w) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                out[dstIndex] = (a shl 24) or (r shl 16) or (g shl 8) or b
                dstIndex++
            }
        }
        return out
    }

    // ---- drawing ----

    private fun color(r: Int, g: Int, b: Int, a: Int, out: NVGColor) =
        nvgRGBA(r.toByte(), g.toByte(), b.toByte(), a.toByte(), out)

    /** Cheap pseudo-bold: NanoVG/stb_truetype has no weight axis, so a single, thin-looking pass through a variable font isn't fixable by just picking a "bold" font name -- draw the glyphs a few times at sub-pixel offsets to thicken the strokes instead. */
    private fun boldText(x: Float, y: Float, text: String) {
        nvgText(ctx, x - 0.35f, y, text)
        nvgText(ctx, x + 0.35f, y, text)
        nvgText(ctx, x, y - 0.35f, text)
        nvgText(ctx, x, y, text)
    }

    private fun drawFrame(hits: MutableList<HitRect>) {
        val col = scratchColor ?: return
        val screenW = width - BEZEL * 2f
        val screenH = height - BEZEL * 2f

        drawBezel(col)

        nvgSave(ctx)
        nvgTranslate(ctx, BEZEL.toFloat(), BEZEL.toFloat())

        // Screen background
        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, 0f, 0f, screenW, screenH, 20f)
        nvgFillColor(ctx, color(24, 24, 28, 255, col))
        nvgFill(ctx)

        drawStatusBar(col, screenW)
        drawCallBanner(col, hits, screenW)

        when (currentPage) {
            PhonePage.HOME -> drawHomePage(col, hits, screenW)
            PhonePage.DIALER -> drawDialerPage(col, hits, screenW)
            PhonePage.MESSAGES -> drawMessagesPage(col, hits, screenW)
        }

        nvgRestore(ctx)
    }

    /** Galaxy-style outer frame: dark chassis body, a faint edge highlight, and a small camera punch-hole -- drawn in full-panel (pre-[BEZEL]-translate) coordinates, around the screen area [drawFrame] draws inside. */
    private fun drawBezel(col: NVGColor) {
        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, 0f, 0f, width.toFloat(), height.toFloat(), 32f)
        nvgFillColor(ctx, color(6, 6, 8, 255, col))
        nvgFill(ctx)

        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, 1f, 1f, width - 2f, height - 2f, 31f)
        nvgStrokeColor(ctx, color(72, 72, 80, 255, col))
        nvgStrokeWidth(ctx, 1.5f)
        nvgStroke(ctx)

        nvgBeginPath(ctx)
        nvgCircle(ctx, width / 2f, BEZEL * 0.55f, 3.5f)
        nvgFillColor(ctx, color(2, 2, 3, 255, col))
        nvgFill(ctx)
    }

    private fun drawStatusBar(col: NVGColor, screenW: Float) {
        nvgFontFace(ctx, "phone")
        nvgFontSize(ctx, 15f)
        nvgFillColor(ctx, color(255, 255, 255, 255, col))
        nvgTextAlign(ctx, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        boldText(16f, STATUS_BAR_HEIGHT / 2f, time)

        nvgFillColor(ctx, color(200, 200, 200, 255, col))
        nvgTextAlign(ctx, NVG_ALIGN_RIGHT or NVG_ALIGN_MIDDLE)
        val number = PhoneCallManager.myNumber.ifEmpty { "..." }
        boldText(screenW - 16f, STATUS_BAR_HEIGHT / 2f, number)
    }

    private fun drawCallBanner(col: NVGColor, hits: MutableList<HitRect>, screenW: Float) {
        val state = PhoneCallManager.state
        if (state == PhoneCallState.IDLE) return

        val y = STATUS_BAR_HEIGHT.toFloat()
        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, 8f, y + 4f, screenW - 16f, BANNER_HEIGHT - 8f, 8f)
        nvgFillColor(ctx, color(52, 90, 60, 255, col))
        nvgFill(ctx)

        val message = when (state) {
            PhoneCallState.INCOMING -> "${PhoneCallManager.peerName} 전화 옴"
            PhoneCallState.CALLING -> "${PhoneCallManager.peerName} 연결 중..."
            PhoneCallState.ACTIVE -> "${PhoneCallManager.peerName}와 통화 중"
            PhoneCallState.ENDED -> "통화 종료됨"
            PhoneCallState.DECLINED -> "상대방이 거절함"
            PhoneCallState.BUSY -> "상대방이 통화 중"
            PhoneCallState.NO_SUCH_NUMBER -> "존재하지 않는 번호"
            PhoneCallState.UNREACHABLE -> "상대방이 오프라인"
            PhoneCallState.IDLE -> ""
        }
        nvgFontFace(ctx, "phone")
        nvgFontSize(ctx, 14f)
        nvgFillColor(ctx, color(255, 255, 255, 255, col))
        nvgTextAlign(ctx, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
        boldText(16f, y + BANNER_HEIGHT / 2f, message)

        var buttonX = screenW - 16f
        fun button(label: String, action: PhoneAction) {
            val bw = 56f
            buttonX -= bw
            drawSmallButton(col, buttonX, y + 6f, bw, BANNER_HEIGHT - 12f, label)
            hits += HitRect(buttonX, y + 6f, bw, BANNER_HEIGHT - 12f, action)
            buttonX -= 6f
        }
        when (state) {
            PhoneCallState.INCOMING -> {
                button("거절", PhoneAction.Decline)
                button("수락", PhoneAction.Accept)
            }
            PhoneCallState.CALLING, PhoneCallState.ACTIVE -> button("종료", PhoneAction.Hangup)
            else -> {}
        }
    }

    private fun drawSmallButton(col: NVGColor, x: Float, y: Float, w: Float, h: Float, label: String) {
        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, x, y, w, h, h / 2f)
        nvgFillColor(ctx, color(255, 255, 255, 40, col))
        nvgFill(ctx)
        nvgFontFace(ctx, "phone")
        nvgFontSize(ctx, 13f)
        nvgFillColor(ctx, color(255, 255, 255, 255, col))
        nvgTextAlign(ctx, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
        boldText(x + w / 2f, y + h / 2f, label)
    }

    private fun drawWideButton(col: NVGColor, x: Float, y: Float, w: Float, h: Float, label: String, primary: Boolean) {
        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, x, y, w, h, 10f)
        if (primary) {
            nvgFillColor(ctx, color(74, 108, 212, 255, col))
        } else {
            nvgFillColor(ctx, color(255, 255, 255, 24, col))
        }
        nvgFill(ctx)
        nvgFontFace(ctx, "phone")
        nvgFontSize(ctx, 16f)
        nvgFillColor(ctx, color(255, 255, 255, 255, col))
        nvgTextAlign(ctx, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
        boldText(x + w / 2f, y + h / 2f, label)
    }

    private fun drawFieldBackground(col: NVGColor, x: Float, y: Float, w: Float, h: Float) {
        nvgBeginPath(ctx)
        nvgRoundedRect(ctx, x, y, w, h, 8f)
        nvgFillColor(ctx, color(255, 255, 255, 20, col))
        nvgFill(ctx)
    }

    private fun drawHomePage(col: NVGColor, hits: MutableList<HitRect>, screenW: Float) {
        val x = FIELD_MARGIN.toFloat()
        val w = screenW - FIELD_MARGIN * 2f
        var y = CONTENT_Y + 24f
        drawWideButton(col, x, y, w, 48f, "다이얼러", primary = true)
        hits += HitRect(x, y, w, 48f, PhoneAction.Navigate(PhonePage.DIALER))
        y += 60f
        drawWideButton(col, x, y, w, 48f, "메시지", primary = true)
        hits += HitRect(x, y, w, 48f, PhoneAction.Navigate(PhonePage.MESSAGES))
        y += 60f
        drawWideButton(col, x, y, w, 48f, "지도", primary = true)
        hits += HitRect(x, y, w, 48f, PhoneAction.OpenMap)
        y += 60f
        drawWideButton(col, x, y, w, 48f, "상점", primary = true)
        hits += HitRect(x, y, w, 48f, PhoneAction.OpenShop)
        y += 60f
        drawWideButton(col, x, y, w, 48f, "탄약 상점", primary = true)
        hits += HitRect(x, y, w, 48f, PhoneAction.OpenAmmoShop)
    }

    private fun drawDialerPage(col: NVGColor, hits: MutableList<HitRect>, screenW: Float) {
        drawFieldBackground(col, FIELD_MARGIN.toFloat(), DIALER_FIELD_Y.toFloat(), screenW - FIELD_MARGIN * 2f, DIALER_FIELD_HEIGHT.toFloat())

        val rows = listOf("123", "456", "789", "*0#")
        val keySize = 76f
        val gap = 12f
        val gridWidth = keySize * 3 + gap * 2
        val startX = (screenW - gridWidth) / 2f
        var y = DIALER_FIELD_Y + DIALER_FIELD_HEIGHT + 20f
        for (row in rows) {
            var x = startX
            for (digit in row) {
                drawSmallButton(col, x, y, keySize, keySize, digit.toString())
                hits += HitRect(x, y, keySize, keySize, PhoneAction.KeypadDigit(digit))
                x += keySize + gap
            }
            y += keySize + gap
        }

        val bx = FIELD_MARGIN.toFloat()
        val bw = screenW - FIELD_MARGIN * 2f
        y += 8f
        drawWideButton(col, bx, y, (bw - 8f) / 2f, 44f, "지우기", primary = false)
        hits += HitRect(bx, y, (bw - 8f) / 2f, 44f, PhoneAction.KeypadBackspace)
        drawWideButton(col, bx + (bw + 8f) / 2f, y, (bw - 8f) / 2f, 44f, "통화", primary = true)
        hits += HitRect(bx + (bw + 8f) / 2f, y, (bw - 8f) / 2f, 44f, PhoneAction.Call)
        y += 54f
        drawWideButton(col, bx, y, bw, 40f, "뒤로", primary = false)
        hits += HitRect(bx, y, bw, 40f, PhoneAction.Navigate(PhonePage.HOME))
    }

    private fun drawMessagesPage(col: NVGColor, hits: MutableList<HitRect>, screenW: Float) {
        drawFieldBackground(col, FIELD_MARGIN.toFloat(), MESSAGE_TO_FIELD_Y.toFloat(), screenW - FIELD_MARGIN * 2f, MESSAGE_TO_FIELD_HEIGHT.toFloat())
        drawFieldBackground(col, FIELD_MARGIN.toFloat(), MESSAGE_BODY_FIELD_Y.toFloat(), screenW - FIELD_MARGIN * 2f, MESSAGE_BODY_FIELD_HEIGHT.toFloat())

        nvgFontFace(ctx, "phone")
        nvgFontSize(ctx, 12f)
        nvgFillColor(ctx, color(190, 190, 190, 255, col))
        nvgTextAlign(ctx, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
        val noteY = MESSAGE_BODY_FIELD_Y + MESSAGE_BODY_FIELD_HEIGHT + 16f
        boldText(FIELD_MARGIN.toFloat(), noteY, "메시지 기능은 아직 구현되지 않았습니다.")

        val bx = FIELD_MARGIN.toFloat()
        val bw = screenW - FIELD_MARGIN * 2f
        var y = noteY + 24f
        drawWideButton(col, bx, y, bw, 44f, "보내기", primary = true)
        hits += HitRect(bx, y, bw, 44f, PhoneAction.SendMessage)
        y += 54f
        drawWideButton(col, bx, y, bw, 40f, "뒤로", primary = false)
        hits += HitRect(bx, y, bw, 40f, PhoneAction.Navigate(PhonePage.HOME))
    }
}
