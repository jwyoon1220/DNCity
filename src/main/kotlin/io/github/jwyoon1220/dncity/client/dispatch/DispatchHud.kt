// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.client.dispatch

import io.github.jwyoon1220.dncity.Dncity
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.LayeredDraw
import net.minecraft.resources.ResourceLocation
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Top-left dispatch card: red/blue siren stripe, "DISPATCH" tag, crime and stars, suspect, the radio line that was read
 * out, and where it is relative to the player (distance and compass direction, or the dimension when it is elsewhere).
 * Slides in from the left, stays [SHOW_MS], fades out. Fed by [DispatchClientState].
 */
object DispatchHud : LayeredDraw.Layer {
    val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Dncity.ID, "dispatch_hud")

    private const val SHOW_MS = 12_000L
    private const val SLIDE_MS = 350L
    private const val FADE_MS = 800L
    private const val W = 190
    private const val PAD = 5
    private val DIRECTIONS = arrayOf("남", "남서", "서", "북서", "북", "북동", "동", "남동")

    override fun render(graphics: GuiGraphics, delta: DeltaTracker) {
        val call = DispatchClientState.latest ?: return
        val age = System.currentTimeMillis() - DispatchClientState.latestAtMs
        if (age > SHOW_MS) return
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.player == null) return

        val alpha = if (age > SHOW_MS - FADE_MS) (SHOW_MS - age).toFloat() / FADE_MS else 1f
        val slide = (age.toFloat() / SLIDE_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - slide) * (1f - slide)
        val font = mc.font

        val lines = font.split(net.minecraft.network.chat.Component.literal(call.text), W - PAD * 2 - 4)
        val shown = lines.take(3)
        val h = PAD * 2 + 10 + 11 + 11 + shown.size * 10 + 11
        val x = (-W + (W + 6) * eased).roundToInt()
        val y = 6

        fun argb(a: Int, rgb: Int) = ((a * alpha).toInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

        graphics.fill(x, y, x + W, y + h, argb(190, 0x101218))
        // siren stripe: red and blue halves swap every half second
        val flip = (System.currentTimeMillis() / 500) % 2 == 0L
        graphics.fill(x, y, x + W / 2, y + 3, argb(255, if (flip) 0xE5322D else 0x2D5BE5))
        graphics.fill(x + W / 2, y, x + W, y + 3, argb(255, if (flip) 0x2D5BE5 else 0xE5322D))
        graphics.fill(x, y + 3, x + 2, y + h, argb(255, 0xE5322D))

        var ty = y + 3 + PAD
        val tx = x + PAD + 3
        graphics.drawString(font, "DISPATCH", tx, ty, argb(255, 0x7FA6FF), false)
        graphics.drawString(font, "#${call.id}", x + W - PAD - font.width("#${call.id}"), ty, argb(255, 0x8A8F99), false)
        ty += 11

        graphics.drawString(font, call.crime, tx, ty, argb(255, 0xFFFFFF), false)
        val stars = "★".repeat(call.stars.coerceIn(0, 5))
        graphics.drawString(font, stars, x + W - PAD - font.width(stars), ty, argb(255, 0xFFC93C), false)
        ty += 11

        graphics.drawString(font, "용의자  ${call.suspect}", tx, ty, argb(255, 0xC8CCD4), false)
        ty += 11

        for (line in shown) {
            graphics.drawString(font, line, tx, ty, argb(255, 0xA9AEB8), false)
            ty += 10
        }

        graphics.drawString(font, where(call), tx, ty, argb(255, 0x6FE39A), false)
    }

    /** "북동 320m" for a call in the player's dimension, the dimension name otherwise. Also used by the phone app. */
    fun where(call: io.github.jwyoon1220.dncity.network.DispatchCallPayload): String {
        val player = Minecraft.getInstance().player ?: return ""
        if (player.level().dimension().location().toString() != call.dimension) return call.dimension.substringAfter(':')
        val dx = call.x + 0.5 - player.x
        val dz = call.z + 0.5 - player.z
        val dist = sqrt(dx * dx + dz * dz).roundToInt()
        // atan2(-dx, dz): 0 = south (+Z), clockwise towards west, matching Minecraft's yaw
        val deg = Math.toDegrees(atan2(-dx, dz))
        val idx = (((deg + 360 + 22.5) % 360) / 45).toInt() % 8
        return "${DIRECTIONS[idx]} ${dist}m"
    }
}
