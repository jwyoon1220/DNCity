// AGENT-DONE(claude): dncity-launcher
package io.github.jwyoon1220.dncity.launcher

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** Mojang's `rules` arrays (libraries and launch arguments). Last matching rule wins; no rules = allowed. */
object Rules {
    fun allow(rules: JsonArray?, features: Set<String> = emptySet()): Boolean {
        if (rules == null || rules.size() == 0) return true
        var allowed = false
        for (e in rules) {
            val r = e.asJsonObject
            if (matches(r, features)) allowed = r.str("action") == "allow"
        }
        return allowed
    }

    private fun matches(rule: JsonObject, features: Set<String>): Boolean {
        rule.obj("os")?.let { os ->
            os.str("name")?.let { if (it != Platform.osName) return false }
            os.str("arch")?.let { if (it == "x86" && Platform.is64Bit) return false }
        }
        rule.obj("features")?.let { f ->
            for ((k, v) in f.entrySet()) if ((k in features) != v.asBoolean) return false
        }
        return true
    }
}

/** One resolved library file. [url] is null for files the NeoForge installer generates itself. */
class LibFile(val name: String, val path: Path, val url: String?, val sha1: String?)

object Versions {
    fun read(path: Path): JsonObject = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }

    /** The version's json with `inheritsFrom` merged in (child wins). Adds `_jarId`: the version whose jar is the client. */
    fun resolve(paths: Paths, id: String): JsonObject {
        val child = read(paths.versionJson(id))
        val parentId = child.str("inheritsFrom")
        if (parentId == null) {
            child.addProperty("_jarId", child.str("jar") ?: id)
            return child
        }
        val parent = resolve(paths, parentId)
        val merged = parent.deepCopy()
        for ((k, v) in child.entrySet()) {
            when (k) {
                "libraries" -> merged.add(k, mergeLibraries(v.asJsonArray, parent.arr("libraries")))
                "arguments" -> merged.add(k, mergeArguments(v.asJsonObject, parent.obj("arguments")))
                "inheritsFrom" -> {}
                else -> merged.add(k, v)
            }
        }
        merged.addProperty("_jarId", child.str("jar") ?: parent.str("_jarId") ?: parentId)
        return merged
    }

    private fun libraryKey(name: String): String {
        val p = name.substringBefore('@').split(":")
        return p[0] + ":" + p.getOrElse(1) { "" } + (if (p.size > 3) ":" + p[3] else "")
    }

    private fun mergeLibraries(child: JsonArray, parent: JsonArray?): JsonArray {
        val out = JsonArray()
        val seen = HashSet<String>()
        for (e in child) if (seen.add(libraryKey(e.asJsonObject.str("name") ?: e.toString()))) out.add(e)
        parent?.forEach { e ->
            if (seen.add(libraryKey(e.asJsonObject.str("name") ?: e.toString()))) out.add(e)
        }
        return out
    }

    private fun mergeArguments(child: JsonObject, parent: JsonObject?): JsonObject {
        val out = JsonObject()
        for (kind in listOf("jvm", "game")) {
            val arr = JsonArray()
            parent?.arr(kind)?.forEach { arr.add(it) }
            child.arr(kind)?.forEach { arr.add(it) }
            out.add(kind, arr)
        }
        return out
    }

    /** Parses a `libraries` array into the files that apply to this OS. */
    fun libraries(paths: Paths, libs: JsonArray?): List<LibFile> {
        val out = ArrayList<LibFile>()
        if (libs == null) return out
        for (e in libs) {
            val lib = e.asJsonObject
            if (!Rules.allow(lib.arr("rules"))) continue
            val name = lib.str("name") ?: continue
            val artifact = lib.obj("downloads")?.obj("artifact")
            if (artifact != null) {
                val rel = artifact.str("path") ?: mavenPath(name)
                out += LibFile(name, paths.library(rel), artifact.str("url")?.ifBlank { null }, artifact.str("sha1"))
            } else if (lib.obj("downloads") == null) {
                // Old style / maven style: name + optional base url.
                val rel = mavenPath(name)
                val base = lib.str("url")
                out += LibFile(name, paths.library(rel), base?.let { it.trimEnd('/') + "/" + rel }, null)
            }
            // `downloads` present but without `artifact`: legacy natives-only entry, not used by 1.21.
        }
        return out
    }

    /** `arguments.jvm` / `arguments.game` flattened through rules; plain strings and `{rules, value}` objects. */
    fun arguments(array: JsonArray?, features: Set<String> = emptySet()): List<String> {
        val out = ArrayList<String>()
        if (array == null) return out
        for (e in array) {
            if (e.isJsonPrimitive) {
                out += e.asString
            } else {
                val o = e.asJsonObject
                if (!Rules.allow(o.arr("rules"), features)) continue
                val v: JsonElement = o.get("value") ?: continue
                if (v.isJsonArray) v.asJsonArray.forEach { out += it.asString } else out += v.asString
            }
        }
        return out
    }
}
