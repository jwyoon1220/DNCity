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
import java.awt.CardLayout
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
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/** The launcher window. All widgets are plain Swing components painted by [Theme]/[FlatButton]. */
class LauncherFrame(private val root: Path) : JFrame("DNCity") {

    private val settings = Settings.load(root)
    private val name = JTextField(settings.username).styled()
    private val ram = JSlider(2, 16, (settings.ramMb / 1024).coerceIn(2, 16)).apply {
        isOpaque = false
        majorTickSpacing = 2
        paintTicks = true
    }
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
    private val accountLabel = label("", size = 14f, style = Font.BOLD)
    private val accountCards = CardLayout()
    private val accountPanel = JPanel(accountCards).apply { isOpaque = false }
    private val accountButton = FlatButton("Microsoft 로그인").apply { preferredSize = Dimension(150, 34) }
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
                g2.dispose()
            }
        }
        add(buildBrand(), BorderLayout.WEST)
        add(buildMain(), BorderLayout.CENTER)

        ram.addChangeListener { ramLabel.text = "${ram.value} GB" }
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

        size = Dimension(920, 580)
        minimumSize = size
        setLocationRelativeTo(null)
    }

    private fun refreshBrand() {
        versionLine.text = "Minecraft ${settings.minecraftVersion}  ·  NeoForge ${settings.neoForgeVersion}"
    }

    private fun buildBrand(): JPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        preferredSize = Dimension(300, 0)
        border = BorderFactory.createEmptyBorder(0, 34, 0, 10)
        val c = GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST; weightx = 1.0 }
        add(label("DNCity", size = 46f, style = Font.BOLD).apply { foreground = Color.WHITE }, c.apply { gridy = 0 })
        add(label("서버 전용 런처", muted = true, size = 14f), c.apply { gridy = 1; insets = Insets(2, 4, 18, 0) })
        refreshBrand()
        add(versionLine, c.apply { gridy = 2; insets = Insets(0, 4, 0, 0) })
    }

    private fun buildMain(): JPanel {
        val card = JPanel(GridBagLayout()).apply {
            background = Theme.panel
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.panelLine),
                BorderFactory.createEmptyBorder(18, 20, 18, 20),
            )
        }
        val full = { y: Int, top: Int ->
            GridBagConstraints().apply {
                gridx = 0; gridy = y; gridwidth = 2; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                insets = Insets(top, 0, 0, 0)
            }
        }
        card.add(label("계정", muted = true, size = 12f), full(0, 0))
        accountPanel.add(name, "offline")
        accountPanel.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 2, 0, 0)
            add(accountLabel, BorderLayout.WEST)
        }, "online")
        card.add(accountPanel, GridBagConstraints().apply {
            gridx = 0; gridy = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(6, 0, 0, 10)
        })
        card.add(accountButton, GridBagConstraints().apply { gridx = 1; gridy = 1; insets = Insets(6, 0, 0, 0) })
        card.add(label("메모리", muted = true, size = 12f), GridBagConstraints().apply {
            gridx = 0; gridy = 2; anchor = GridBagConstraints.WEST; insets = Insets(16, 0, 0, 0)
        })
        card.add(ramLabel, GridBagConstraints().apply {
            gridx = 1; gridy = 2; anchor = GridBagConstraints.EAST; insets = Insets(16, 0, 0, 0)
        })
        card.add(ram, full(3, 4))
        card.add(JScrollPane(log).apply {
            border = BorderFactory.createLineBorder(Theme.panelLine)
            background = Theme.field
            viewport.background = Theme.field
        }, GridBagConstraints().apply {
            gridx = 0; gridy = 4; gridwidth = 2; weightx = 1.0; weighty = 1.0
            fill = GridBagConstraints.BOTH; insets = Insets(16, 0, 0, 0)
        })

        val bottom = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(14, 0, 0, 0)
            add(status, GridBagConstraints().apply { gridx = 0; gridy = 0; weightx = 1.0; anchor = GridBagConstraints.WEST })
            add(progressBar, GridBagConstraints().apply {
                gridx = 0; gridy = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(6, 0, 0, 14)
            })
            add(settingsButton, GridBagConstraints().apply {
                gridx = 1; gridy = 0; gridheight = 2; insets = Insets(0, 0, 0, 10)
            })
            add(play, GridBagConstraints().apply { gridx = 2; gridy = 0; gridheight = 2 })
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(28, 0, 28, 34)
            add(card, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
    }

    private fun refreshAccount() {
        val acc = stored
        if (acc == null) {
            accountCards.show(accountPanel, "offline")
            accountButton.text = "Microsoft 로그인"
        } else {
            accountLabel.text = "${acc.username}  ·  Microsoft"
            accountCards.show(accountPanel, "online")
            accountButton.text = "로그아웃"
        }
        name.isEnabled = !busy && acc == null
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
        settings.username = name.text.trim().ifEmpty { settings.username }
        settings.ramMb = ram.value * 1024
        runCatching { settings.save(root) }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        play.isEnabled = !value
        settingsButton.isEnabled = !value
        name.isEnabled = !value && stored == null
        accountButton.isEnabled = !value
        ram.isEnabled = !value
    }

    private fun play() {
        val user = name.text.trim()
        if (stored == null && !Regex("[A-Za-z0-9_]{3,16}").matches(user)) {
            JOptionPane.showMessageDialog(this, "닉네임은 영문/숫자/_ 3~16자여야 합니다.", "DNCity", JOptionPane.WARNING_MESSAGE)
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
                installer.syncPack(settings.packUrl)

                progress.status("게임 실행 중")
                progress.fraction(1.0)
                val account = stored
                val session = if (account != null) {
                    progress.status("Microsoft 로그인 확인 중")
                    MicrosoftAuth(settings.resolvedClientId()).silentLogin(root, account)
                } else {
                    Session.offline(user)
                }
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
