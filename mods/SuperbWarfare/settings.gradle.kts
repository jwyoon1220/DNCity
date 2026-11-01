pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://maven.neoforged.net/releases") }
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":ksp")

// SBW's rifle/handgun/shotgun/sniper guns consume TACZ ammo items (TaczAmmoStrategy) instead of
// their own; this makes TACZ's classes visible at SBW's own compile time. Mirrors the same
// includeBuild/dependencySubstitution pattern the root DNCity build already uses for TACZ.
includeBuild("../TACZ-1.21.1") {
    dependencySubstitution {
        substitute(module("com.tacz:tacz-neoforge-1.21.1")).using(project(":"))
    }
}

// engine/fmod (hand-written JNI FMOD Studio bindings) — used by SbwFmodWeaponSoundManager to
// play gun sounds from DNCity's bundled Weapons.bank, mirroring TACZ's own FMOD integration.
// Same pattern as TACZ's own settings.gradle.kts: the root DNCity settings.gradle.kts substitution
// only applies to projects that declare it themselves.
includeBuild("../../engine/fmod") {
    dependencySubstitution {
        substitute(module("engine:fmod")).using(project(":"))
    }
}