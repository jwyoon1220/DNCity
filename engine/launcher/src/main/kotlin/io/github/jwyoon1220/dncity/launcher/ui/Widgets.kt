// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher.ui

import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

/** Hand-painted dark theme: no look-and-feel library, everything below is plain Swing + Java2D. */
object Theme {
    val bgTop = Color(0x10131A)
    val bgBottom = Color(0x1B2030)
    val panel = Color(0x1A1E29)
    val panelLine = Color(0x2A3042)
    val field = Color(0x12151D)
    val accent = Color(0x5B8DEF)
    val accentHover = Color(0x7AA2F7)
    val accentDown = Color(0x4676D6)
    val disabled = Color(0x3A4054)
    val text = Color(0xE6E8EE)
    val muted = Color(0x8A90A0)
    val danger = Color(0xE5646E)

    fun font(style: Int, size: Float): Font = Font("Malgun Gothic", style, 12).deriveFont(style, size)

    fun smooth(g: Graphics): Graphics2D {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        return g2
    }

    /** Window/taskbar icon drawn in code (a rounded accent square with a "D"). */
    fun icon(size: Int = 64): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = smooth(img.graphics)
        g.color = accent
        g.fillRoundRect(0, 0, size, size, size / 4, size / 4)
        g.color = Color.WHITE
        g.font = font(Font.BOLD, size * 0.62f)
        val fm = g.fontMetrics
        g.drawString("D", (size - fm.stringWidth("D")) / 2, (size - fm.height) / 2 + fm.ascent)
        g.dispose()
        return img
    }
}

class FlatButton(text: String, private val primary: Boolean = false) : JButton(text) {
    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        foreground = Theme.text
        font = Theme.font(Font.BOLD, 13f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(if (primary) 200 else 96, if (primary) 46 else 34)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = Theme.smooth(g)
        val m = model
        g2.color = when {
            !isEnabled -> Theme.disabled
            primary && m.isPressed -> Theme.accentDown
            primary && m.isRollover -> Theme.accentHover
            primary -> Theme.accent
            m.isPressed -> Theme.panelLine
            m.isRollover -> Color(0x252B3B)
            else -> Theme.panel
        }
        g2.fillRoundRect(0, 0, width, height, 12, 12)
        if (!primary) {
            g2.color = Theme.panelLine
            g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
        }
        g2.color = if (isEnabled) foreground else Theme.muted
        g2.font = font
        val fm = g2.fontMetrics
        g2.drawString(text, (width - fm.stringWidth(text)) / 2, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

class FlatProgress : JComponent() {
    var value: Double = 0.0
        set(v) {
            field = v.coerceIn(0.0, 1.0)
            repaint()
        }

    init {
        preferredSize = Dimension(100, 8)
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = Theme.smooth(g)
        g2.color = Theme.field
        g2.fillRoundRect(0, 0, width, height, height, height)
        val w = (width * value).toInt()
        if (w > 0) {
            g2.color = Theme.accent
            g2.fillRoundRect(0, 0, w.coerceAtLeast(height), height, height, height)
        }
        g2.dispose()
    }
}

fun label(text: String, muted: Boolean = false, size: Float = 13f, style: Int = Font.PLAIN) = JLabel(text).apply {
    foreground = if (muted) Theme.muted else Theme.text
    font = Theme.font(style, size)
}

fun JTextField.styled(): JTextField {
    background = Theme.field
    foreground = Theme.text
    caretColor = Theme.text
    selectionColor = Theme.accent
    selectedTextColor = Color.WHITE
    font = Theme.font(Font.PLAIN, 13f)
    border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.panelLine),
        BorderFactory.createEmptyBorder(7, 10, 7, 10),
    )
    return this
}
