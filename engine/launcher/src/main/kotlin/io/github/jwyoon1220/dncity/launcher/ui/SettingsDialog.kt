// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher.ui

import io.github.jwyoon1220.dncity.launcher.Settings
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JTextField

/** Advanced settings. Edits [settings] in place and returns true from [showDialog] when saved. */
class SettingsDialog(owner: Frame, private val settings: Settings) : JDialog(owner, "설정", Dialog.ModalityType.APPLICATION_MODAL) {

    private val javaField = JTextField(settings.javaPath).styled()
    private val jvmArgs = JTextField(settings.extraJvmArgs).styled()
    private val gameDir = JTextField(settings.gameDir).styled()
    private var saved = false

    init {
        setIconImages(listOf(Theme.icon()))
        val body = JPanel(GridBagLayout()).apply {
            background = Theme.bgBottom
            border = BorderFactory.createEmptyBorder(18, 20, 8, 20)
        }
        var row = 0
        fun add(title: String, hint: String, field: JTextField, extra: javax.swing.JComponent? = null) {
            val c = GridBagConstraints().apply { gridy = row; insets = Insets(6, 0, 6, 10); anchor = GridBagConstraints.WEST }
            body.add(label(title), c.apply { gridx = 0 })
            field.toolTipText = hint
            body.add(field, GridBagConstraints().apply {
                gridx = 1; gridy = row; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(6, 0, 6, 6)
            })
            if (extra != null) body.add(extra, GridBagConstraints().apply { gridx = 2; gridy = row; insets = Insets(6, 0, 6, 0) })
            row++
        }

        add("Java 경로", "비워두면 런처에 포함된 Java 21 사용", javaField, FlatButton("찾기").apply {
            addActionListener { choose(javaField, false) }
        })
        add("추가 JVM 인자", "예: -XX:+UseG1GC", jvmArgs)
        add("게임 폴더", "비워두면 런처 폴더의 game/", gameDir, FlatButton("찾기").apply {
            addActionListener { choose(gameDir, true) }
        })

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 10)).apply {
            isOpaque = false
            add(FlatButton("취소").apply { addActionListener { dispose() } })
            add(FlatButton("저장", primary = true).apply {
                preferredSize = Dimension(96, 34)
                addActionListener { apply(); saved = true; dispose() }
            })
        }
        contentPane = JPanel(BorderLayout()).apply {
            background = Theme.bgBottom
            add(body, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        setSize(600, 270)
        setLocationRelativeTo(owner)
    }

    private fun choose(target: JTextField, dirsOnly: Boolean) {
        val fc = JFileChooser()
        if (dirsOnly) fc.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) target.text = fc.selectedFile.absolutePath
    }

    private fun apply() {
        settings.javaPath = javaField.text.trim()
        settings.extraJvmArgs = jvmArgs.text.trim()
        settings.gameDir = gameDir.text.trim()
    }

    fun showDialog(): Boolean {
        isVisible = true
        return saved
    }
}
