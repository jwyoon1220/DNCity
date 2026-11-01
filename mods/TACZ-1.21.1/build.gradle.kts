import me.modmuss50.mpp.platforms.modrinth.ModrinthEnvironment

plugins {
    alias(libs.plugins.dotenv)
    alias(libs.plugins.moddev)
    alias(libs.plugins.mod.publish)
}

val id = project.property("mod_id") as String
group = project.property("maven_group") as String
version = project.property("mod_version") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Minecraft's own dependency set (via net.neoforged:minecraft-dependencies) pins commons-io
// to 2.15.1 with a strict constraint, but fancymodloader separately requests 2.11.0 and
// engine/fmod requests 2.18.0. Force everything onto the version Minecraft ships, since
// commons-io is source/binary compatible across these minor releases. Kept in sync with the
// same force in the root project's build.gradle.kts, since whichever project resolves this
// build's dependency graph (root via the composite build, or this project directly, e.g. an
// IDE sync) needs it declared on its own side.
configurations.all {
    resolutionStrategy.force("commons-io:commons-io:2.15.1")
}

neoForge {
    version = libs.versions.neoforge.asProvider().get()
    parchment {
        mappingsVersion = libs.versions.parchment.get()
        minecraftVersion = libs.versions.minecraft.asProvider().get()
    }
    validateAccessTransformers = true

    runs {
        configureEach {
            disableIdeRun()
            systemProperty("forge.logging.console.level", "debug")

            dependencies {
                additionalRuntimeClasspathConfiguration(libs.org.apache.commons.math3)
                additionalRuntimeClasspathConfiguration(libs.com.github.figuraMC.luaj.core)
                additionalRuntimeClasspathConfiguration(libs.com.github.figuraMC.luaj.jse)
                additionalRuntimeClasspathConfiguration(libs.org.apache.bcel)
            }
        }

        create("client") {
            client()
            gameDirectory = file("run/client_a")
        }

        create("client2") {
            client()
            gameDirectory = file("run/client_b")
            programArguments = listOf("--username", "mayday_memory")
        }

        create("server") {
            server()
            gameDirectory = file("run/server")
        }

        create("data") {
            data()
        }
    }

    mods {
        create(id) {
            sourceSet(sourceSets["main"])
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.rtyley")
            includeGroup("com.github.FiguraMC.luaj")
        }
    }
    maven("https://maven.shedaniel.me")
    maven("https://maven.kosmx.dev")
    maven("https://maven.blamejared.com")
    maven {
        url = uri("https://maven.architectury.dev")
        content {
            includeGroup("dev.architectury")
        }
    }
    maven {
        url = uri("https://maven.latvian.dev/releases")
        content {
            includeGroup("dev.latvian.mods")
            includeGroup("dev.latvian.apps")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "CurseForge"
                url = uri("https://cursemaven.com")
            }
        }
        filter {
            includeGroup("curse.maven")
        }
    }
    flatDir {
        dir("libs")
    }
    // Required to resolve Sodium's/Iris's own forgified-fabric-api dependency when Gradle
    // resolves this project's own configurations -- same reason the root build.gradle.kts
    // declares this repo.
    maven {
        url = uri("https://maven.su5ed.dev/releases")
        content { includeGroup("org.sinytra.forgified-fabric-api") }
    }
}

dependencies {
    // Local FMOD Studio bindings (engine/fmod, included as a composite build).
    implementation("engine:fmod:1.0")
    jarJar("engine:fmod:1.0")

    implementation(libs.com.github.mcmodderanchor.simplebedrockmodel)
    jarJar(libs.com.github.mcmodderanchor.simplebedrockmodel)
    compileOnly(libs.com.maydaymemory.mae)

    implementation(libs.org.apache.commons.math3)
    jarJar(libs.org.apache.commons.math3)
    implementation(libs.com.github.figuraMC.luaj.core)
    jarJar(libs.com.github.figuraMC.luaj.core)
    implementation(libs.com.github.figuraMC.luaj.jse)
    jarJar(libs.com.github.figuraMC.luaj.jse)
    implementation(libs.org.apache.bcel)
    jarJar(libs.org.apache.bcel)

    compileOnly(libs.me.shedaniel.cloth.config.neoforge)
    compileOnly(libs.dev.kosmx.player.animation.lib.forge)
    implementation(libs.maven.modrinth.sodium)
    implementation(libs.maven.modrinth.iris)
    implementation(libs.curse.maven.acceleratedrendering)
    compileOnly(libs.maven.modrinth.carry.on)
    compileOnly(libs.maven.modrinth.shoulder.surfing.reloaded)
    compileOnly(libs.mezz.jei.common.api)
    compileOnly(libs.mezz.jei.neoforge.api)
    runtimeOnly(libs.mezz.jei.neoforge)
    compileOnly(libs.curse.maven.framework)
    compileOnly(libs.curse.maven.controllable)
    implementation(libs.dev.latvian.mods.kubejs.neoforge)
    compileOnly(libs.dev.latvian.mods.rhino)
}

java {
    // Java 21 -- what Mojang ships to end users for Minecraft 1.21.1. Was Java 25 until
    // 2026-08-26 (see the root project's build.gradle.kts for the full story): engine/fmod's
    // classes, which FmodWeaponSoundManager calls directly, used to require JDK 24+ (jextract/
    // java.lang.foreign bindings), forcing this project onto the same floor. engine/fmod is now
    // hand-written JNI with no such requirement.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val properties = mapOf(
        "id" to id,
        "version" to project.version,
        "name" to project.property("mod_name") as String,
        "minecraft_version" to libs.versions.minecraft.range.get(),
        "loader_version" to libs.versions.neoforge.range.get()
    )
    filteringCharset = "UTF-8"
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(properties) }
}

publishMods {
    displayName = "[TaCZ] ${project.property("mod_name")} ${project.version}"
    changelog = providers.fileContents(layout.projectDirectory.file("changelog.md")).asText
    file = tasks.jar.get().archiveFile
    additionalFiles.from(tasks.named<Jar>("sourcesJar").get().archiveFile)
    type = STABLE
    modLoaders.add("neoforge")

    modrinth {
        projectId = project.property("modrinth_id") as String
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
            .orElse(provider { env.fetch("MODRINTH_TOKEN") })
        minecraftVersions.add("1.21.1")
        environment = ModrinthEnvironment.CLIENT_AND_SERVER
    }

    curseforge {
        projectId = project.property("curseforge_id") as String
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            .orElse(provider { env.fetch("CURSEFORGE_TOKEN") })
        minecraftVersions.add("1.21.1")
        client = true
        server = true
    }

    github {
        repository = project.property("repository") as String
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
            .orElse(provider { env.fetch("GITHUB_TOKEN") })
        commitish = "neoforge/1.21.1"
        tagName = "neoforge-${project.version}"
    }
}