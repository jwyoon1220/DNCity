// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import io.github.jwyoon1220.dncity.launcher.ui.LauncherFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager

/** Console progress, for `--install` (CI / smoke tests; no window). */
private object ConsoleProgress : Progress {
    private var last = ""
    override fun status(text: String) = println("[status] $text")
    override fun fraction(value: Double) {
        val s = "%3d%%".format((value * 100).toInt())
        if (s != last) { last = s; print("\r$s") ; if (value >= 1.0) println() }
    }
    override fun log(line: String) = println(line)
}

fun main(args: Array<String>) {
    if ("--install" in args) {
        // Installs (and does not start) the configured game; exit code 0 on success.
        val root = Platform.defaultRoot()
        val settings = Settings.load(root)
        val paths = Paths(root, settings.resolvedGameDir(root))
        try {
            val id = Installer(paths, settings.resolvedJava(), ConsoleProgress)
                .installAll(settings.minecraftVersion, settings.neoForgeVersion)
            println("설치 완료: $id")
            println(GameLauncher(paths, settings).buildCommand(id, Session.offline(settings.username)).joinToString(" "))
        } catch (e: Exception) {
            e.printStackTrace()
            System.exit(1)
        }
        return
    }

    if ("--run" in args) {
        // Smoke test: install if needed, start the game, stream its output, exit with its exit code.
        val root = Platform.defaultRoot()
        val settings = Settings.load(root)
        val paths = Paths(root, settings.resolvedGameDir(root))
        val id = Installer(paths, settings.resolvedJava(), ConsoleProgress)
            .installAll(settings.minecraftVersion, settings.neoForgeVersion)
        val proc = GameLauncher(paths, settings).start(id, Session.offline(settings.username))
        proc.inputStream.bufferedReader().forEachLine { println(it) }
        System.exit(proc.waitFor())
    }

    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
        } catch (_: Exception) {
        }
        LauncherFrame(Platform.defaultRoot()).isVisible = true
    }
}
