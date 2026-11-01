// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * Installs everything the game needs under [Paths.root]: vanilla client, libraries, assets and
 * NeoForge (including its installer's client-side processors). Every step is idempotent and only
 * touches the network for files that are missing, so a finished install also starts offline.
 */
class Installer(private val paths: Paths, private val java: String, private val progress: Progress) {

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val ASSET_URL = "https://resources.download.minecraft.net/"
        const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"

        /** Newest stable NeoForge for a Minecraft version (`1.21.1` -> `21.1.x`), or null if the lookup fails. */
        fun latestNeoForge(minecraftVersion: String): String? = try {
            val parts = minecraftVersion.split(".")
            val prefix = "${parts[1]}.${parts.getOrElse(2) { "0" }}."
            Regex("<version>([^<]+)</version>")
                .findAll(Http.string(NEOFORGE_MAVEN + "maven-metadata.xml"))
                .map { it.groupValues[1] }
                .filter { it.startsWith(prefix) && !it.contains("-") }
                .maxByOrNull { v -> v.removePrefix(prefix).toIntOrNull() ?: -1 }
        } catch (_: Exception) {
            null
        }
    }

    /** Installs vanilla + NeoForge and returns the version id to launch. */
    fun installAll(minecraftVersion: String, neoForgeVersion: String): String {
        progress.status("마인크래프트 $minecraftVersion 확인 중")
        installVanilla(minecraftVersion)
        val id = installNeoForge(minecraftVersion, neoForgeVersion)
        progress.status("라이브러리 확인 중")
        val merged = Versions.resolve(paths, id)
        ensureLibraries(Versions.libraries(paths, merged.arr("libraries")))
        ensureAssets(merged)
        return id
    }

    // ------------------------------------------------------------------ vanilla

    fun installVanilla(mc: String) {
        val jsonPath = paths.versionJson(mc)
        if (!Files.isRegularFile(jsonPath)) {
            val manifest = JsonParser.parseString(Http.string(MANIFEST_URL)).asJsonObject
            val entry = manifest.arr("versions")!!.map { it.asJsonObject }.firstOrNull { it.str("id") == mc }
                ?: throw IOException("마인크래프트 버전을 찾을 수 없음: $mc")
            Http.download(entry.str("url")!!, jsonPath, entry.str("sha1"))
        }
        val json = Versions.read(jsonPath)
        val client = json.obj("downloads")?.obj("client") ?: throw IOException("$mc 에 client 다운로드 정보가 없음")
        progress.status("클라이언트 jar 다운로드 중")
        Http.download(client.str("url")!!, paths.versionJar(mc), client.str("sha1"))
    }

    fun ensureLibraries(libs: List<LibFile>) {
        val items = libs.filter { it.url != null }.map { Download(it.url!!, it.path, it.sha1) }
        progress.status("라이브러리 다운로드 중 (${items.size})")
        progress.fraction(0.0)
        Http.downloadAll(items, progress)
    }

    fun ensureAssets(merged: JsonObject) {
        val index = merged.obj("assetIndex") ?: return
        val id = index.str("id") ?: return
        val indexPath = paths.assets.resolve("indexes").resolve("$id.json")
        progress.status("에셋 인덱스 확인 중")
        Http.download(index.str("url")!!, indexPath, index.str("sha1"))
        val objects = Versions.read(indexPath).obj("objects") ?: return
        val items = objects.entrySet().map { (_, v) ->
            val hash = v.asJsonObject.str("hash")!!
            val sub = hash.substring(0, 2)
            // No sha1 here: an existing object is trusted (thousands of files; checked once when fetched).
            Download(ASSET_URL + sub + "/" + hash, paths.assets.resolve("objects").resolve(sub).resolve(hash), null)
        }
        val missing = items.count { !Files.isRegularFile(it.path) }
        if (missing > 0) {
            progress.status("에셋 다운로드 중 ($missing)")
            progress.fraction(0.0)
            Http.downloadAll(items.filter { !Files.isRegularFile(it.path) }, progress, 16)
        }
    }

    // ------------------------------------------------------------------ NeoForge

    fun installNeoForge(mc: String, nf: String): String {
        val id = "neoforge-$nf"
        val marker = paths.versions.resolve(id).resolve(".installed")
        if (Files.isRegularFile(marker) && Files.isRegularFile(paths.versionJson(id))) return id

        progress.status("NeoForge $nf 설치 프로그램 다운로드 중")
        val installer = paths.installers.resolve("neoforge-$nf-installer.jar")
        Http.download("$NEOFORGE_MAVEN$nf/neoforge-$nf-installer.jar", installer)

        ZipFile(installer.toFile()).use { zip ->
            fun text(name: String): String {
                val e = zip.getEntry(name) ?: throw IOException("설치 프로그램에 $name 없음")
                return zip.getInputStream(e).use { String(it.readAllBytes(), Charsets.UTF_8) }
            }

            val versionJson = text("version.json")
            val versionObj = JsonParser.parseString(versionJson).asJsonObject
            if (versionObj.str("id") != id) {
                progress.log("경고: version.json id=${versionObj.str("id")} (기대: $id)")
            }
            Files.createDirectories(paths.versionJson(id).parent)
            Files.writeString(paths.versionJson(id), versionJson)

            // Files shipped inside the installer under maven/ go straight into the libraries folder.
            for (entry in zip.entries()) {
                if (entry.isDirectory || !entry.name.startsWith("maven/")) continue
                val target = paths.library(entry.name.removePrefix("maven/"))
                if (!Files.isRegularFile(target)) {
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                }
            }

            val profile = JsonParser.parseString(text("install_profile.json")).asJsonObject
            progress.status("NeoForge 라이브러리 다운로드 중")
            ensureLibraries(Versions.libraries(paths, profile.arr("libraries")))
            ensureLibraries(Versions.libraries(paths, versionObj.arr("libraries")))

            runProcessors(zip, installer, profile, mc, nf)
        }
        Files.writeString(marker, nf)
        return id
    }

    private fun runProcessors(zip: ZipFile, installer: Path, profile: JsonObject, mc: String, nf: String) {
        val workDir = paths.work.resolve("neoforge-$nf")
        Files.createDirectories(workDir)

        val vars = HashMap<String, String>()
        vars["SIDE"] = "client"
        vars["MINECRAFT_JAR"] = paths.versionJar(mc).toString()
        vars["MINECRAFT_VERSION"] = mc
        vars["ROOT"] = paths.root.toString()
        vars["INSTALLER"] = installer.toString()
        vars["LIBRARY_DIR"] = paths.libraries.toString()

        profile.obj("data")?.entrySet()?.forEach { (key, value) ->
            val raw = value.asJsonObject.str("client") ?: return@forEach
            vars[key] = when {
                raw.startsWith("[") && raw.endsWith("]") -> paths.library(mavenPath(raw.substring(1, raw.length - 1))).toString()
                raw.startsWith("'") && raw.endsWith("'") -> raw.substring(1, raw.length - 1)
                raw.startsWith("/") -> {
                    val entry = zip.getEntry(raw.substring(1)) ?: throw IOException("설치 프로그램에 $raw 없음")
                    val target = workDir.resolve(raw.substring(1))
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                    target.toString()
                }
                else -> raw
            }
        }

        fun sub(arg: String): String {
            if (arg.startsWith("[") && arg.endsWith("]")) {
                return paths.library(mavenPath(arg.substring(1, arg.length - 1))).toString()
            }
            val replaced = Regex("\\{(\\w+)}").replace(arg) { m ->
                vars[m.groupValues[1]] ?: throw IOException("알 수 없는 설치 변수: ${m.value}")
            }
            return if (replaced.length >= 2 && replaced.startsWith("'") && replaced.endsWith("'")) {
                replaced.substring(1, replaced.length - 1)
            } else {
                replaced
            }
        }

        val processors = profile.arr("processors")?.map { it.asJsonObject }
            ?.filter { p -> p.arr("sides")?.let { s -> s.any { it.asString == "client" } } ?: true }
            ?: emptyList()

        processors.forEachIndexed { index, p ->
            progress.status("NeoForge 처리 중 (${index + 1}/${processors.size})")
            progress.fraction(index.toDouble() / processors.size)

            val outputs = p.obj("outputs")?.entrySet()?.associate { (k, v) -> sub(k) to sub(v.asString) }.orEmpty()
            if (outputs.isNotEmpty() && outputs.all { (file, hash) ->
                    val f = Path.of(file)
                    Files.isRegularFile(f) && sha1Hex(f).equals(hash, ignoreCase = true)
                }
            ) {
                progress.log("건너뜀(이미 완료): ${p.str("jar")}")
                return@forEachIndexed
            }

            val jar = paths.library(mavenPath(p.str("jar")!!))
            val classpath = mutableListOf(jar.toString())
            p.arr("classpath")?.forEach { classpath += paths.library(mavenPath(it.asString)).toString() }
            val mainClass = JarFile(jar.toFile()).use { it.manifest?.mainAttributes?.getValue("Main-Class") }
                ?: throw IOException("프로세서 Main-Class 없음: $jar")

            val cmd = mutableListOf(java, "-cp", classpath.joinToString(File.pathSeparator), mainClass)
            p.arr("args")?.forEach { cmd += sub(it.asString) }
            progress.log("프로세서 실행: $mainClass")

            val proc = ProcessBuilder(cmd).directory(paths.root.toFile()).redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().forEachLine { progress.log(it) }
            val code = proc.waitFor()
            if (code != 0) throw IOException("NeoForge 프로세서 실패 (exit $code): $mainClass")
        }
        progress.fraction(1.0)
    }

    // ------------------------------------------------------------------ pack sync

    /** Optional: downloads the mods listed in a pack manifest into `<gameDir>/mods`. */
    fun syncPack(url: String) {
        if (url.isBlank()) return
        progress.status("모드팩 동기화 중")
        val manifest = JsonParser.parseString(Http.string(url)).asJsonObject
        val modsDir = paths.gameDir.resolve("mods")
        val items = manifest.arr("mods")?.map { it.asJsonObject }?.map {
            val name = it.str("name")!!
            require(!name.contains('/') && !name.contains('\\') && name != "..") { "잘못된 모드 파일명: $name" }
            Download(it.str("url")!!, modsDir.resolve(name), it.str("sha1"))
        }.orEmpty()
        Http.downloadAll(items, progress)
    }
}
