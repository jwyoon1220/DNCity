rootProject.name = "Automobility"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")

        mavenCentral()
        gradlePluginPortal()
    }
}

// This fork drops "common" and "fabric" from upstream: "common" was a fabric-loom project
// (needed for a deobfuscated Minecraft + Mixin AP dev environment), and its sources are now
// merged directly into "neoforge" (see neoforge/src/main/java) instead, making this a pure
// NeoForge mod with no Fabric dependency at all.
include("neoforge")
