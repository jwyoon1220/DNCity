import java.net.URI

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "tacz"

includeBuild("../../engine/fmod") {
    dependencySubstitution {
        substitute(module("engine:fmod")).using(project(":"))
    }
}