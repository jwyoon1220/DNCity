// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher.ui

import io.github.jwyoon1220.dncity.launcher.AuthException
import io.github.jwyoon1220.dncity.launcher.GameLauncher
import io.github.jwyoon1220.dncity.launcher.MicrosoftAuth
import io.github.jwyoon1220.dncity.launcher.StoredAccount
import io.github.jwyoon1220.dncity.launcher.Installer
import io.github.jwyoon1220.dncity.launcher.Paths
import io.github.jwyoon1220.dncity.launcher.Progress
import io.github.jwyoon1220.dncity.launcher.Session
import io.github.jwyoon1220.dncity.launcher.Settings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/** The launcher window. All widgets are plain Swing components painted by [Theme]/[FlatButton]. */
class LauncherFrame(private val root: Path) : JFrame("DNCity") {

    private val settings = Settings.load(root)
    private val ram = FlatSlider(2, 16, (settings.ramMb / 1024).coerceIn(2, 16))
    private val avatar = Avatar()
    private val accountSub = label("", muted = true, size = 12f)
    private val logScroll = JScrollPane()
    private val logButton = FlatButton("로그")
    private val ramLabel = label("", muted = true)
    private val status = label("준비됨", muted = true)
    private val progressBar = FlatProgress()
    private val log = JTextArea().apply {
        isEditable = false
        lineWrap = true
        background = Theme.field
        foreground = Theme.muted
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
    }
    private val play = FlatButton("플레이", primary = true)
    private val settingsButton = FlatButton("설정")
    private val versionLine = label("", muted = true)
    private var stored: StoredAccount? = StoredAccount.load(root)
    private val accountLabel = label("", size = 16f, style = Font.BOLD)
    private val accountButton = FlatButton("Microsoft 로그인").apply { preferredSize = Dimension(130, 36) }
    private var busy = false

    private val progress = object : Progress {
        override fun status(text: String) = SwingUtilities.invokeLater { status.text = text }
        override fun fraction(value: Double) = SwingUtilities.invokeLater { progressBar.value = value }
        override fun log(line: String) = SwingUtilities.invokeLater { appendLog(line) }
    }

    init {
        iconImage = Theme.icon()
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) {
                if (busy && JOptionPane.showConfirmDialog(
                        this@LauncherFrame, "작업이 진행 중입니다. 런처를 닫을까요?", "DNCity", JOptionPane.YES_NO_OPTION,
                    ) != JOptionPane.YES_OPTION
                ) return
                saveSettings()
                dispose()
                System.exit(0)
            }
        })

        contentPane = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = Theme.smooth(g)
                g2.paint = GradientPaint(0f, 0f, Theme.bgTop, 0f, height.toFloat(), Theme.bgBottom)
                g2.fillRect(0, 0, width, height)
                g2.paint = java.awt.RadialGradientPaint(
                    width * 0.12f, height * 1.05f, width * 0.75f, floatArrayOf(0f, 1f),
                    arrayOf(Color(0x5B, 0x8D, 0xEF, 90), Color(0x5B, 0x8D, 0xEF, 0)),
                )
                g2.fillRect(0, 0, width, height)
                g2.dispose()
            }
        }
        add(buildBrand(), BorderLayout.WEST)
        add(buildMain(), BorderLayout.CENTER)

        ram.onChange { ramLabel.text = "${ram.value} GB" }
        ramLabel.text = "${ram.value} GB"
        play.addActionListener { play() }
        accountButton.addActionListener { if (stored == null) microsoftLogin() else logout() }
        refreshAccount()
        settingsButton.addActionListener {
            if (SettingsDialog(this, settings).showDialog()) {
                settings.save(root)
                refreshBrand()
            }
        }

        size = Dimension(900, 500)
        minimumSize = size
        setLocationRelativeTo(null)
    }

    private fun refreshBrand() {
        versionLine.text = "Minecraft ${settings.minecraftVersion}  ·  NeoForge ${settings.neoForgeVersion}"
    }

    private fun buildBrand(): JPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        preferredSize = Dimension(320, 0)
        border = BorderFactory.createEmptyBorder(40, 40, 36, 10)
        val c = GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST; weightx = 1.0 }
        add(JLabel(javax.swing.ImageIcon(Theme.icon(56))), c.apply { gridy = 0; insets = Insets(0, 0, 18, 0) })
        add(label("DNCity", size = 40f, style = Font.BOLD).apply { foreground = Color.WHITE }, c.apply { gridy = 1; insets = Insets(0, 0, 0, 0) })
        add(label("서버 전용 런처", muted = true, size = 14f), c.apply { gridy = 2; insets = Insets(2, 2, 0, 0) })
        add(javax.swing.Box.createVerticalGlue(), GridBagConstraints().apply { gridx = 0; gridy = 3; weighty = 1.0 })
        refreshBrand()
        add(versionLine, c.apply { gridy = 4; insets = Insets(0, 2, 0, 0); anchor = GridBagConstraints.SOUTHWEST })
    }

    private fun buildMain(): JPanel {
        val account = RoundPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(16, 18, 16, 18)
            add(avatar, GridBagConstraints().apply { gridx = 0; gridy = 0; gridheight = 2; insets = Insets(0, 0, 0, 14) })
            add(accountLabel, GridBagConstraints().apply { gridx = 1; gridy = 0; weightx = 1.0; anchor = GridBagConstraints.SOUTHWEST })
            add(accountSub, GridBagConstraints().apply { gridx = 1; gridy = 1; weightx = 1.0; anchor = GridBagConstraints.NORTHWEST })
            add(accountButton, GridBagConstraints().apply { gridx = 2; gridy = 0; gridheight = 2 })
        }
        val memory = RoundPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(16, 18, 14, 18)
            add(label("메모리", size = 14f, style = Font.BOLD), GridBagConstraints().apply { gridx = 0; gridy = 0; weightx = 1.0; anchor = GridBagConstraints.WEST })
            add(ramLabel.apply { foreground = Theme.accentHover; font = Theme.font(Font.BOLD, 14f) }, GridBagConstraints().apply { gridx = 1; gridy = 0; anchor = GridBagConstraints.EAST })
            add(ram, GridBagConstraints().apply { gridx = 0; gridy = 1; gridwidth = 2; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(8, 0, 0, 0) })
        }
        logScroll.apply {
            setViewportView(log)
            border = BorderFactory.createLineBorder(Theme.panelLine)
            viewport.background = Theme.field
            preferredSize = Dimension(100, 170)
            isVisible = false
        }
        logButton.addActionListener { showLog(!logScroll.isVisible) }

        val bottom = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(progressBar, GridBagConstraints().apply {
                gridx = 0; gridy = 0; gridwidth = 4; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(0, 0, 12, 0)
            })
            add(status, GridBagConstraints().apply { gridx = 0; gridy = 1; weightx = 1.0; anchor = GridBagConstraints.WEST })
            add(logButton, GridBagConstraints().apply { gridx = 1; gridy = 1; insets = Insets(0, 0, 0, 8) })
            add(settingsButton, GridBagConstraints().apply { gridx = 2; gridy = 1; insets = Insets(0, 0, 0, 8) })
            add(play, GridBagConstraints().apply { gridx = 3; gridy = 1 })
        }

        val stack = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val one = { y: Int, top: Int, fill: Int, wy: Double ->
                GridBagConstraints().apply { gridx = 0; gridy = y; weightx = 1.0; weighty = wy; this.fill = fill; insets = Insets(top, 0, 0, 0) }
            }
            add(account, one(0, 0, GridBagConstraints.HORIZONTAL, 0.0))
            add(memory, one(1, 12, GridBagConstraints.HORIZONTAL, 0.0))
            add(logScroll, one(2, 12, GridBagConstraints.BOTH, 0.0))
            add(javax.swing.Box.createVerticalGlue(), one(3, 0, GridBagConstraints.VERTICAL, 1.0))
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(32, 0, 28, 36)
            add(stack, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
    }

    /** The log is hidden until needed; showing it grows/shrinks the window by its height. */
    private fun showLog(show: Boolean) {
        if (logScroll.isVisible == show) return
        logScroll.isVisible = show
        logButton.text = if (show) "로그 숨기기" else "로그"
        val delta = logScroll.preferredSize.height + 12
        setSize(width, if (show) height + delta else height - delta)
        revalidate()
    }

    private fun refreshAccount() {
        val acc = stored
        if (acc == null) {
            accountLabel.text = "로그인 필요"
            accountSub.text = "Microsoft 계정으로 로그인해야 플레이할 수 있습니다"
            avatar.letter = "?"
            accountButton.text = "Microsoft 로그인"
        } else {
            accountLabel.text = acc.username
            accountSub.text = "Microsoft 계정"
            avatar.letter = acc.username.take(1).uppercase()
            accountButton.text = "로그아웃"
        }
    }

    private fun logout() {
        StoredAccount.clear(root)
        stored = null
        refreshAccount()
        status.text = "로그아웃되었습니다"
    }

    private fun microsoftLogin() {
        saveSettings()
        val auth = try {
            MicrosoftAuth(settings.resolvedClientId())
        } catch (e: AuthException) {
            JOptionPane.showMessageDialog(
                this,
                e.message + "\n\nAzure 포털에서 앱을 등록(퍼블릭 클라이언트 허용)하고, Mojang의 Minecraft API 승인을 받은 클라이언트 ID가 필요합니다.",
                "DNCity", JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        setBusy(true)
        status.text = "Microsoft 로그인 대기 중"
        var cancelled = false
        var dialog: DeviceCodeDialog? = null
        Thread({
            try {
                auth.interactiveLogin(
                    root,
                    onCode = { code ->
                        SwingUtilities.invokeLater {
                            dialog = DeviceCodeDialog(this, code) { cancelled = true }.also { it.isVisible = true }
                        }
                    },
                    cancelled = { cancelled },
                )
                stored = StoredAccount.load(root)
                progress.status("로그인되었습니다: ${stored?.username}")
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showLog(true) }
                progress.status("로그인 실패: ${e.message}")
                progress.log(e.stackTraceToString())
            } finally {
                SwingUtilities.invokeLater {
                    dialog?.dispose()
                    setBusy(false)
                    refreshAccount()
                }
            }
        }, "ms-login").apply { isDaemon = true }.start()
    }

    private fun appendLog(line: String) {
        if (log.document.length > 400_000) log.document.remove(0, 100_000)
        log.append(line + "\n")
        log.caretPosition = log.document.length
    }

    private fun saveSettings() {
        settings.ramMb = ram.value * 1024
        runCatching { settings.save(root) }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        play.isEnabled = !value
        settingsButton.isEnabled = !value
        accountButton.isEnabled = !value
        ram.isEnabled = !value
    }

    private fun play() {
        val account = stored
        if (account == null) {
            status.text = "먼저 Microsoft 로그인을 해주세요"
            JOptionPane.showMessageDialog(this, "플레이하려면 Microsoft 계정으로 로그인해야 합니다.", "DNCity", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        saveSettings()
        setBusy(true)
        progressBar.value = 0.0
        log.text = ""

        Thread({
            try {
                val paths = Paths(root, settings.resolvedGameDir(root))
                val installer = Installer(paths, settings.resolvedJava(), progress)
                val id = installer.installAll(settings.minecraftVersion, settings.neoForgeVersion)
                installer.syncBundledMods()

                progress.status("게임 실행 중")
                progress.fraction(1.0)
                progress.status("Microsoft 로그인 확인 중")
                val session = MicrosoftAuth(settings.resolvedClientId()).silentLogin(root, account)
                val proc = GameLauncher(paths, settings).start(id, session)
                proc.inputStream.bufferedReader().forEachLine { progress.log(it) }
                val code = proc.waitFor()
                progress.status(if (code == 0) "게임이 종료되었습니다" else "게임이 종료되었습니다 (코드 $code)")
            } catch (e: Exception) {
                progress.log(e.stackTraceToString())
                progress.status("오류: ${e.message}")
            } finally {
                SwingUtilities.invokeLater { setBusy(false) }
            }
        }, "launch").apply { isDaemon = true }.start()
    }
}
