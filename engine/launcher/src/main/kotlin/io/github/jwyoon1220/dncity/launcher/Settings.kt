// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Persisted launcher settings (`<root>/launcher.json`). Every field has a default, so Gson can fill gaps. */
class Settings {
    var username: String = "Player"
    var ramMb: Int = 4096

    /** Blank = the runtime bundled with the launcher. */
    var javaPath: String = ""
    var extraJvmArgs: String = ""
    var minecraftVersion: String = "1.21.1"

    /** Same NeoForge the mod itself is built against (gradle.properties `neo_version`). */
    var neoForgeVersion: String = "21.1.248"

    /** Optional pack manifest URL: `{"mods":[{"name":"x.jar","url":"...","sha1":"..."}]}` synced into mods/. */
    var packUrl: String = ""

    /** Blank = `<root>/game`. */
    var gameDir: String = ""

    /** Azure app (client) id for Microsoft login; blank falls back to `-Ddncity.msClientId` / env DNCITY_MS_CLIENT_ID. */
    var msClientId: String = ""

    fun resolvedClientId(): String = msClientId.ifBlank {
        System.getProperty("dncity.msClientId") ?: System.getenv("DNCITY_MS_CLIENT_ID") ?: DEFAULT_CLIENT_ID
    }.trim()

    fun resolvedGameDir(root: Path): Path = if (gameDir.isBlank()) root.resolve("game") else Path.of(gameDir)
    fun resolvedJava(): String = javaPath.ifBlank { Platform.bundledJava() }

    fun save(root: Path) {
        Files.createDirectories(root)
        Files.newBufferedWriter(file(root)).use { gson.toJson(this, it) }
    }

    companion object {
        /**
         * What CmlLib.Core.Auth.Microsoft uses by default (`XboxGameTitles.MinecraftJava`): the official Minecraft Java
         * launcher's id, already accepted by Mojang's API. It is not DNCity's own, so Mojang may block it at any time.
         * DNCity's own Azure app ("Parin", `5ac81d8f-ef78-47ca-827b-78335e5e035c`) can replace it in the settings once
         * Mojang approves it (https://aka.ms/mce-reviewappid).
         */
        const val DEFAULT_CLIENT_ID = "00000000402b5328"

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
