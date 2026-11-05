// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Persisted launcher settings (`<root>/launcher.json`). Every field has a default, so Gson can fill gaps. */
class Settings {
    var ramMb: Int = 8192

    /** Blank = the runtime bundled with the launcher. */
    var javaPath: String = ""
    var extraJvmArgs: String = ""

    /** Blank = `<root>/game`. */
    var gameDir: String = ""

    // DNCity-only launcher: game versions and the login app are fixed, not user settings.
    val minecraftVersion: String get() = MINECRAFT_VERSION

    /** Same NeoForge the mod itself is built against (gradle.properties `neo_version`). */
    val neoForgeVersion: String get() = NEOFORGE_VERSION

    /** Developer override only: `-Ddncity.msClientId` / env DNCITY_MS_CLIENT_ID. */
    fun resolvedClientId(): String =
        (System.getProperty("dncity.msClientId") ?: System.getenv("DNCITY_MS_CLIENT_ID") ?: DEFAULT_CLIENT_ID).trim()

    fun resolvedGameDir(root: Path): Path = if (gameDir.isBlank()) root.resolve("game") else Path.of(gameDir)
    fun resolvedJava(): String = javaPath.ifBlank { Platform.bundledJava() }

    fun save(root: Path) {
        Files.createDirectories(root)
        Files.newBufferedWriter(file(root)).use { gson.toJson(this, it) }
    }

    companion object {
        const val MINECRAFT_VERSION = "1.21.1"
        const val NEOFORGE_VERSION = "21.1.248"

        /**
         * DNCity's own Azure app ("Parin", tenant `0c6eedd0-d804-4cda-b2b7-0f01a3fb966b`, personal Microsoft accounts).
         * Mojang's `login_with_xbox` answers 403 until the app id is approved (https://aka.ms/mce-reviewappid).
         * The previous default was CmlLib.Core's `XboxGameTitles.MinecraftJava` id `00000000402b5328` (the official
         * launcher's id, not DNCity's); it still works when entered in the settings.
         */
        const val DEFAULT_CLIENT_ID = "5ac81d8f-ef78-47ca-827b-78335e5e035c"

        private fun file(root: Path) = root.resolve("launcher.json")

        fun load(root: Path): Settings = try {
            Files.newBufferedReader(file(root)).use { gson.fromJson(it, Settings::class.java) } ?: Settings()
        } catch (_: Exception) {
            Settings()
        }
    }
}

/** A launch identity. [offline] is CmlLib's `MSession.CreateOfflineSession`; Microsoft sessions come from [MicrosoftAuth]. */
class Session(val username: String, val uuid: UUID, val accessToken: String, val userType: String) {
    val isOnline: Boolean get() = userType == "msa"

    companion object {
        fun offline(username: String) = Session(
            username, UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)), "0", "legacy",
        )
    }
}
