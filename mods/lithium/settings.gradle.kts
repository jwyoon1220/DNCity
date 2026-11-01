// AGENT-DONE(codex): lithium-mixin-plugin-bootstrap
rootProject.name = "lithium"

pluginManagement {
    // The plugin is part of Lithium's source tree. Including it here lets this build resolve the
    // plugin even when Lithium is itself an included build, without requiring mavenLocal state.
    includeBuild("components/mixin-config-plugin")

    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        gradlePluginPortal()
    }
}

include("common")
// "fabric" dropped for DNCity: this repo is NeoForge-only (see AGENTS.md), and configuring it
// alongside "neoforge" inside this repo's own composite build fails resolving the
// net.caffeinemc.mixin-config-plugin plugin ("already on the classpath with an unknown version")
// -- confirmed by hand. Same "strip to one loader" pattern as mods/Automobility's own fork (see
// settings.gradle.kts's comment on that includeBuild) and mods/distant-horizons's fabric-loader
// drop.
include("neoforge")
