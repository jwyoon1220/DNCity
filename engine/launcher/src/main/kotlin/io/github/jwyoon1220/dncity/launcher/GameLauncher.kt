// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import java.io.File
import java.nio.file.Files

/** Builds the java command line for an installed version and starts it (CmlLib's `MinecraftProcessBuilder`). */
class GameLauncher(private val paths: Paths, private val settings: Settings) {

    fun buildCommand(versionId: String, session: Session): List<String> {
        val merged = Versions.resolve(paths, versionId)
        val libs = Versions.libraries(paths, merged.arr("libraries"))
        // An inheriting version (NeoForge) must see the client jar under ITS OWN file name: NeoForge's
        // bootstraplauncher ignores `${version_name}.jar` on the classpath, and the vanilla `1.21.1.jar` left
        // there would clash with the patched `minecraft` module.
        val parentJar = paths.versionJar(merged.str("_jarId") ?: versionId)
        val clientJar = paths.versionJar(versionId)
        if (parentJar != clientJar && !Files.isRegularFile(clientJar)) {
            Files.copy(parentJar, clientJar)
        }

        val classpath = (libs.map { it.path.toString() } + clientJar.toString()).distinct()
        val natives = paths.versions.resolve(versionId).resolve("natives")
        Files.createDirectories(natives)
        Files.createDirectories(paths.gameDir)

        val vars = mapOf(
            "auth_player_name" to session.username,
            "auth_uuid" to session.uuid.toString().replace("-", ""),
            "auth_access_token" to session.accessToken,
            "auth_session" to session.accessToken,
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to session.userType,
            "user_properties" to "{}",
            "version_name" to versionId,
            "version_type" to (merged.str("type") ?: "release"),
            "game_directory" to paths.gameDir.toString(),
            "assets_root" to paths.assets.toString(),
            "game_assets" to paths.assets.toString(),
            "assets_index_name" to (merged.obj("assetIndex")?.str("id") ?: merged.str("assets") ?: "legacy"),
            "natives_directory" to natives.toString(),
            "launcher_name" to "DNCity",
            "launcher_version" to "1.0",
            "classpath" to classpath.joinToString(File.pathSeparator),
            "classpath_separator" to File.pathSeparator,
            "library_directory" to paths.libraries.toString(),
        )

        fun fill(s: String) = Regex("\\$\\{(\\w+)}").replace(s) { m -> vars[m.groupValues[1]] ?: m.value }

        val jvm = Versions.arguments(merged.obj("arguments")?.arr("jvm")).map(::fill).toMutableList()
        if ("-cp" !in jvm && "-classpath" !in jvm) {
            jvm += listOf("-cp", vars.getValue("classpath"))
        }
        if (jvm.none { it.startsWith("-Djava.library.path") }) {
            jvm += "-Djava.library.path=${natives}"
        }

        val game = if (merged.obj("arguments") != null) {
            Versions.arguments(merged.obj("arguments")?.arr("game")).map(::fill)
        } else {
            // Pre-1.13 style single string.
            (merged.str("minecraftArguments") ?: "").split(" ").filter { it.isNotBlank() }.map(::fill)
        }

        val cmd = ArrayList<String>()
        cmd += settings.resolvedJava()
        cmd += "-Xmx${settings.ramMb}M"
        cmd += "-Xms${(settings.ramMb / 4).coerceAtLeast(512)}M"
        // G1 tuned for short pauses: a profile at 4 GB showed 34 GCs/min with pauses up to 85 ms (frame hitches)
        cmd += listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=40", "-XX:G1NewSizePercent=30", "-XX:G1ReservePercent=20", "-XX:+ParallelRefProcEnabled")
        cmd += settings.extraJvmArgs.split(" ").filter { it.isNotBlank() }
        cmd += jvm
        cmd += merged.str("mainClass") ?: error("mainClass 없음: $versionId")
        cmd += game
        return cmd
    }

    fun start(versionId: String, session: Session): Process {
        var cmd = buildCommand(versionId, session)
        // NeoForge's module path makes the line very long; Windows' CreateProcess caps it at 32767 chars,
        // so past a safe size everything after the java binary goes through a JDK `@argfile`.
        if (cmd.sumOf { it.length + 1 } > 20_000) {
            val file = paths.versions.resolve(versionId).resolve("launch.args")
            Files.writeString(file, cmd.drop(1).joinToString("\n") { quoteForArgFile(it) })
            cmd = listOf(cmd[0], "@$file")
        }
        return ProcessBuilder(cmd).directory(paths.gameDir.toFile()).redirectErrorStream(true).start()
    }

    /** JDK argfile syntax: double-quoted, with backslash and quote escaped. */
    private fun quoteForArgFile(arg: String): String =
        "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
