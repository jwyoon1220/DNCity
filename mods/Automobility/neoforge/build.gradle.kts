import org.slf4j.event.Level

plugins {
    id("idea")
    id("net.neoforged.moddev") version "2.0.28-beta"
}

apply(plugin = "net.neoforged.moddev")

dependencies {
    implementation("de.javagl:obj:0.4.0")
    jarJar("de.javagl:obj:0.4.0")
    additionalRuntimeClasspath("de.javagl:obj:0.4.0")

    // MixinExtras -- used by one merged-in mixin (formerly common/, see settings.gradle.kts'
    // comment on the common/fabric -> neoforge merge). NeoForge's own Mixin service already
    // ships a MixinExtras version at runtime, but the compile-time annotations still need to
    // resolve.
    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")
}

neoForge {
    version = rootProject.properties["neoforge_version"].toString()

    parchment {
        minecraftVersion = rootProject.properties["minecraft_version"].toString()
        mappingsVersion = rootProject.properties["parchment_release"].toString()
    }

    runs {
        create("Client") {
            client()
        }
        create("Server") {
            server()
        }
        create("Datagen") {
            data()

            programArguments.addAll(
                "--mod", rootProject.properties["archives_base_name"].toString(),
                "--all",
                "--output", file("src/generated/resources/").getAbsolutePath(),
                "--existing", file("src/main/resources/").getAbsolutePath())
        }

        configureEach {
            // Older moddev (2.0.28-beta) doesn't expose disableIdeRun() -- ideName = "" is the
            // documented equivalent for this version.
            ideName = ""
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = Level.DEBUG
        }
    }

    mods {
        create(rootProject.properties["archives_base_name"].toString()) {
            sourceSet(sourceSets.main.get())
        }
    }

    accessTransformers {
        file("src/main/resources/META-INF/accesstransformer.cfg")
    }
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}

tasks {
    named("compileTestJava").configure {
        enabled = false
    }

    val notNeoTask: (Task) -> Boolean = { !it.name.startsWith("neo") && !it.name.startsWith("compileService") }
    // Captured as a plain String, not read from `rootProject` inside the task closure below --
    // the configuration cache can't serialize a live Gradle script object (Project) reference
    // captured by a task's action.
    val modVersion = rootProject.properties["mod_version"].toString()

    withType<ProcessResources>().matching(notNeoTask).configureEach {
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(mapOf("version" to modVersion))
        }
    }
}
