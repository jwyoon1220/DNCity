// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Progress sink: the Swing UI and the `--install` CLI both implement it. Called from worker threads. */
interface Progress {
    fun status(text: String)
    fun fraction(value: Double)
    fun log(line: String)
}

object Platform {
    val osName: String = System.getProperty("os.name").lowercase().let {
        when {
            "win" in it -> "windows"
            "mac" in it || "darwin" in it -> "osx"
            else -> "linux"
        }
    }
    val is64Bit: Boolean = System.getProperty("os.arch").contains("64")
    val isArm: Boolean = System.getProperty("os.arch").lowercase().let { "aarch64" in it || "arm" in it }

    fun defaultRoot(): Path {
        System.getProperty("dncity.launcher.home")?.let { return Path.of(it) }
        val home = System.getProperty("user.home")
        return when (osName) {
            "windows" -> Path.of(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "DNCity")
            "osx" -> Path.of(home, "Library", "Application Support", "DNCity")
            else -> Path.of(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "dncity")
        }
    }

    /** The java binary of the JVM running the launcher (the jpackage runtime when installed). */
    fun bundledJava(): String {
        val exe = if (osName == "windows") "bin/java.exe" else "bin/java"
        val own = File(System.getProperty("java.home"), exe)
        if (own.isFile) return own.absolutePath
        // A runtime stripped of its launchers: fall back to JAVA_HOME, then whatever `java` is on the PATH.
        System.getenv("JAVA_HOME")?.let { File(it, exe).takeIf(File::isFile)?.let { f -> return f.absolutePath } }
        return "java"
    }
}

/** All on-disk locations, mirroring the vanilla `.minecraft` layout. */
class Paths(val root: Path, val gameDir: Path) {
    val versions: Path = root.resolve("versions")
    val libraries: Path = root.resolve("libraries")
    val assets: Path = root.resolve("assets")
    val installers: Path = root.resolve("installers")
    val work: Path = root.resolve("work")

    fun library(relative: String): Path = libraries.resolve(relative)
    fun versionJson(id: String): Path = versions.resolve(id).resolve("$id.json")
    fun versionJar(id: String): Path = versions.resolve(id).resolve("$id.jar")
}

val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
fun JsonObject.arr(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

/** `group:artifact:version[:classifier][@ext]` -> `group/path/artifact/version/artifact-version[-classifier].ext`. */
fun mavenPath(coordinate: String): String {
    val parts = coordinate.split("@", limit = 2)
    val core = parts[0]
    val ext = parts.getOrNull(1) ?: "jar"
    val p = core.split(":")
    require(p.size >= 3) { "Bad maven coordinate: $coordinate" }
    val group = p[0]
    val artifact = p[1]
    val version = p[2]
    val classifier = p.getOrNull(3)?.let { "-$it" } ?: ""
    return "${group.replace('.', '/')}/$artifact/$version/$artifact-$version$classifier.$ext"
}

fun sha1Hex(path: Path): String {
    val md = MessageDigest.getInstance("SHA-1")
    Files.newInputStream(path).use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

class Download(val url: String, val path: Path, val sha1: String? = null)

object Http {
    private const val UA = "DNCity-Launcher/1.0"
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private fun request(url: String) = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", UA).timeout(Duration.ofMinutes(5)).GET().build()

    fun string(url: String): String {
        val res = client.send(request(url), HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()} for $url")
        return res.body()
    }

    class Response(val status: Int, val body: String)

    /** POST that returns error statuses too (the auth servers explain failures in the body). */
    fun post(url: String, body: String, contentType: String, accept: String = "application/json", auth: String? = null): Response {
        val b = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", UA).header("Content-Type", contentType).header("Accept", accept)
            .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body))
        if (auth != null) b.header("Authorization", auth)
        val res = client.send(b.build(), HttpResponse.BodyHandlers.ofString())
        return Response(res.statusCode(), res.body())
    }

    fun postForm(url: String, fields: Map<String, String>): Response = post(
        url,
        fields.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, Charsets.UTF_8) + "=" + java.net.URLEncoder.encode(it.value, Charsets.UTF_8) },
        "application/x-www-form-urlencoded",
    )

    fun getAuth(url: String, bearer: String): Response {
        val req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA)
            .header("Authorization", "Bearer $bearer").timeout(Duration.ofSeconds(30)).GET().build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        return Response(res.statusCode(), res.body())
    }

    /** Skips the download when [dest] exists (and matches [sha1] when given). Atomic via a .part file. */
    fun download(url: String, dest: Path, sha1: String? = null) {
        if (Files.isRegularFile(dest) && (sha1 == null || sha1Hex(dest).equals(sha1, ignoreCase = true))) return
        dest.parent?.let { Files.createDirectories(it) }
        val part = dest.resolveSibling(dest.fileName.toString() + ".part")
        var last: Exception? = null
        for (attempt in 1..3) {
            try {
                val res = client.send(request(url), HttpResponse.BodyHandlers.ofInputStream())
                if (res.statusCode() != 200) {
                    res.body().close()
                    throw IOException("HTTP ${res.statusCode()} for $url")
                }
                res.body().use { Files.copy(it, part, StandardCopyOption.REPLACE_EXISTING) }
                if (sha1 != null && !sha1Hex(part).equals(sha1, ignoreCase = true)) {
                    throw IOException("SHA-1 mismatch for $url")
                }
                Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("다운로드 실패: $url (${last?.message})", last)
    }

    /** Downloads in parallel and reports 0..1 through [progress]. Duplicate target paths are fetched once. */
    fun downloadAll(items: List<Download>, progress: Progress, threads: Int = 8) {
        val unique = items.distinctBy { it.path }
        if (unique.isEmpty()) return
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(r, "dl").apply { isDaemon = true } }
        val done = AtomicInteger()
        try {
            val futures = unique.map {
                pool.submit {
                    download(it.url, it.path, it.sha1)
                    progress.fraction(done.incrementAndGet().toDouble() / unique.size)
                }
            }
            try {
                futures.forEach { it.get() }
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
