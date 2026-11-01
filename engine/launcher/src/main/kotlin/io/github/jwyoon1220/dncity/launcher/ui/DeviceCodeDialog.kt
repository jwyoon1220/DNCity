// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher.ui

import io.github.jwyoon1220.dncity.launcher.MicrosoftAuth
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.WindowConstants

/** Shows the device code while the user signs in in their browser. Non-modal; [onCancel] fires on cancel/close. */
class DeviceCodeDialog(owner: Frame, code: MicrosoftAuth.DeviceCode, private val onCancel: () -> Unit) : JDialog(owner, "Microsoft 로그인") {
    init {
        setIconImages(listOf(Theme.icon()))
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) = cancel()
        })

        val link = code.verificationUri
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Theme.bgBottom
            border = BorderFactory.createEmptyBorder(22, 26, 8, 26)
            add(label("브라우저에서 아래 주소를 열고 코드를 입력하세요", size = 13f).also { it.alignmentX = 0f })
            add(Box.createVerticalStrut(6))
            add(label(link, muted = true, size = 12f).also { it.alignmentX = 0f })
            add(Box.createVerticalStrut(16))
            add(JLabel(code.userCode, SwingConstants.LEFT).apply {
                foreground = Theme.accentHover
                font = Font(Font.MONOSPACED, Font.BOLD, 40)
                alignmentX = 0f
            })
            add(Box.createVerticalStrut(10))
            add(label("코드는 클립보드에 복사되었습니다. 로그인을 마칠 때까지 기다리는 중…", muted = true, size = 12f).also { it.alignmentX = 0f })
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 10)).apply {
            isOpaque = false
            add(FlatButton("브라우저 열기").apply { preferredSize = Dimension(120, 34); addActionListener { browse(link) } })
            add(FlatButton("취소").apply { addActionListener { cancel() } })
        }
        contentPane = JPanel(BorderLayout()).apply {
            background = Theme.bgBottom
            add(body, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        setSize(520, 280)
        setLocationRelativeTo(owner)

        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code.userCode), null) }
        browse(link)
    }

    private fun browse(url: String) {
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url)) }
    }

    private fun cancel() {
        onCancel()
        dispose()
    }
}
