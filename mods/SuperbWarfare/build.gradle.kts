plugins {
    idea
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.80"
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"

    id("com.google.devtools.ksp") version "2.1.20-2.0.1"
}

tasks.named<Wrapper>("wrapper") {
    // Define wrapper values here so as to not have to always do so when updating gradlew.properties.
    // Switching this to Wrapper.DistributionType.ALL will download the full gradle sources that comes with
    // documentation attached on cursor hover of gradle classes and methods. However, this comes with increased
    // file size for Gradle. If you do switch this to ALL, run the Gradle wrapper task twice afterwards.
    // (Verify by checking gradle/wrapper/gradle-wrapper.properties to see if distributionUrl now points to `-all`)
    distributionType = Wrapper.DistributionType.BIN
}

version = "${project.property("mod_version")}-mc${project.property("minecraft_version")}"
group = "com.atsuishio.superbwarfare"

// This project's own runtimeClasspath hits the exact same three-way commons-io conflict the root
// project's build.gradle.kts already forces past (see that file's comment on its own
// `configurations.all` block): FancyModLoader pins `strictly 2.11.0`, Minecraft's own dependency
// set pins `strictly 2.15.1`, and this project's `engine:fmod:1.0` dependency (below) requests
// 2.18.0 -- three mutually-exclusive constraints Gradle can't satisfy at once. The root project's
// force doesn't reach here since this is a separate Gradle project (an included build), so it
// needs its own copy of the same force.
configurations.all {
    resolutionStrategy.force("commons-io:commons-io:2.15.1")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
    }
    maven {
        url = uri("https://maven.createmod.net")
    }
    flatDir {
        dir("libs")
    }
    maven {
        url = uri("https://maven.theillusivec4.top/")
        content {
            includeGroup("top.theillusivec4.curios")
        }
    }
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroupByRegex("software\\.bernie.*")
            includeGroup("com.eliotlash.mclib")
        }
    }
    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
        content {
            includeGroup("mezz.jei")
            includeGroup("vazkii.patchouli")
        }
    }
    maven {
        url = uri("https://maven.shedaniel.me/")
        content {
            includeGroup("me.shedaniel.cloth")
        }
    }
    maven {
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        url = uri("https://maven.ryanhcode.dev/releases")
    }
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content {
            includeGroup("thedarkcolour")
        }
    }
    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.MCModderAnchor")
        }
    }
    // Required to resolve TACZ's (mods/TACZ-1.21.1) transitive forgified-fabric-api dependency
    // (pulled in via Sodium/Iris) when Gradle resolves this project's own configurations --
    // same reason the root build.gradle.kts declares this repo.
    maven {
        url = uri("https://maven.su5ed.dev/releases")
        content { includeGroup("org.sinytra.forgified-fabric-api") }
    }
}

base {
    archivesName.set(project.property("mod_id") as String)
}

// Mojang ships Java 21 to end users starting in 1.20.5, so mods should target Java 21.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    // Don't use withSourcesJar() — sourcesJar is created manually below
    // and wired only into publishing, not build (saves ~46s)
}

// Sources JAR — only built during publishing, not during dev build/runClient
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    group = "build"
}

neoForge {
    // Specify the version of NeoForge to use.
    version = project.property("neo_version") as String

    parchment {
        mappingsVersion = project.property("parchment_mappings_version") as String
        minecraftVersion = project.property("parchment_minecraft_version") as String
    }

    // This line is optional. Access Transformers are automatically detected
//    accessTransformers = files("src/main/resources/META-INF/accesstransformer.cfg")

    // Default run configurations.
    // These can be tweaked, removed, or duplicated as needed.
    runs {
        create("client") {
            client()

            // Comma-separated list of namespaces to load gametests from. Empty = all namespaces.
            systemProperty("neoforge.enabledGameTestNamespaces", project.property("mod_id") as String)
        }

        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", project.property("mod_id") as String)
        }

        // This run config launches GameTestServer and runs all registered gametests, then exits.
        // By default, the server will crash when no gametests are provided.
        // The gametest system is also enabled by default for other run configs under the /test command.
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", project.property("mod_id") as String)
        }

        create("data") {
            data()

            // example of overriding the workingDirectory set in configureEach above, uncomment if you want to use it
            // gameDirectory = project.file('run-data')

            // Specify the modid for data generation, where to output the resulting resource, and where to look for existing resources.
            programArguments.addAll(
                "--mod",
                project.property("mod_id") as String,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath
            )
        }

        // applies to all the run configs above
        configureEach {
            disableIdeRun()
            jvmArguments = listOf(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+AllowEnhancedClassRedefinition"
            )

            // Recommended logging data for a userdev environment
            // The markers can be added/remove as needed separated by commas.
            // "SCAN": For mods scan.
            // "REGISTRIES": For firing of registry events.
            // "REGISTRYDUMP": For getting the contents of all registries.
            systemProperty("forge.logging.markers", "REGISTRIES")

            // Recommended logging level for the console
            // You can set various levels here.
            // Please read: https://stackoverflow.com/questions/2031163/when-to-use-the-different-log-levels
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        // define mod <-> source bindings
        // these are used to tell the game which sources are for which mod
        // mostly optional in a single mod project
        // but multi mod projects should define one per mod
        create(project.property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Include resources generated by data generators.
sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}

// Sets up a dependency configuration called 'localRuntime'.
// This configuration should be used instead of 'runtimeOnly' to declare
// a dependency that will be present for runtime testing but that is
// "optional", meaning it will not be pulled by dependents of this mod.
configurations {
    create("localRuntime")
    getByName("runtimeClasspath").extendsFrom(getByName("localRuntime"))
}

fun DependencyHandler.jijImplement(dependency: String, maxVersion: String? = null) {
    val (group, name, version) = dependency.split(":")

    val maximumVersion = maxVersion ?: run {
        val versions = version.split(".")
        val firstVersionNumber = versions[0].toIntOrNull()
        val firstVersion = if (firstVersionNumber != null) {
            (firstVersionNumber + 1).toString()
        } else {
            versions[0]
        }

        return@run "$firstVersion." + versions.drop(1).joinToString(".") {
            it.replace(Regex("""^\d+"""), "0")
        }
    }

//    println("$name [$version,$maximumVersion)")

    val dependencyImpl = implementation(group = group, name = name, version = version)
    jarJar(dependencyImpl) {
        version {
            strictly("[$version,$maximumVersion)")
            prefer(version)
        }
    }
}

dependencies {
    ksp(project(":ksp"))
    implementation(project(":ksp"))

    implementation("thedarkcolour:kotlinforforge-neoforge:5.10.0")

    jijImplement("software.bernie.geckolib:geckolib-neoforge-1.21.1:4.7.5")

    // curios
    jijImplement("top.theillusivec4.curios:curios-neoforge:9.2.0+1.21.1")

    // 从ywzj毛来的Rhino
    // NOTE: this used to ALSO be added via add("additionalRuntimeClasspath", ...) alongside
    // jijImplement below. That put org.ywzj:rhino (package org.mozillaa.javascript, jar at
    // mods/SuperbWarfare/libs/rhino-1.8.1-SNAPSHOT.jar via the flatDir repo above -- a plain
    // library jar, no neoforge.mods.toml of its own) on the dev runClient classpath TWICE
    // through two different mechanisms: once as a flat classpath entry (additionalRuntimeClasspath)
    // and once through jarJar's own dependency resolution (jijImplement's underlying
    // implementation(...) call already makes it visible to compile/runtime without needing
    // additionalRuntimeClasspath too). Two physical copies of the same non-mod library jar ended
    // up loaded via different classloaders, so JDK ServiceLoader's org.mozillaa.javascript.Context
    // static init crashed with "RegExpLoaderImpl not a subtype" (interface loaded by one copy,
    // impl resolved from the other) -- confirmed via a runClient crash log where this was the root
    // cause of a NoClassDefFoundError cascading into a world-load failure. jijImplement alone is
    // enough for both compile and the dev run classpath; only removing the duplicate is needed.
    jijImplement("org.ywzj:rhino:1.8.1-SNAPSHOT")

    // SBM
    jijImplement("com.github.MCModderAnchor:SimpleBedrockModel:2.5.1.1-neoforge-mc1.21.1")

    compileOnly("com.maydaymemory:mae:1.1.2") {
        exclude("com.google.code.findbugs", "jsr305")
        exclude("it.unimi.dsi", "fastutil")
        exclude("org.joml", "joml")
    }

    // TACZ 弹药 API（TaczAmmoStrategy）—仅编译期可见，运行时由 DNCity 根工程一并加载 TACZ，
    // SBW 自身不打包/强制依赖 TACZ（独立运行时通过 ModList.isLoaded("tacz") 守卫）。
    // 版本号需与根工程 gradle.properties 的 mods_tacz_version 保持一致。
    compileOnly("com.tacz:tacz-neoforge-1.21.1:1.1.8-hotfix-r6")

    // FMOD Studio JNI bindings (engine/fmod) — SbwFmodWeaponSoundManager uses this to play gun
    // sounds from DNCity's bundled Weapons.bank. Unlike the TACZ dependency above, this one is
    // jarJar'd (not compileOnly) since SBW owns and drives its own independent FMODSystem
    // instance rather than depending on TACZ being present — same two-line shape TACZ's own
    // build.gradle.kts uses for the same coordinate.
    implementation("engine:fmod:1.0")
    jarJar("engine:fmod:1.0")

    // ponder
    jijImplement("net.createmod.ponder:ponder-neoforge:${project.property("ponder_version")}+mc${project.property("minecraft_version")}")

    // 飞轮
    jijImplement("dev.engine-room.flywheel:flywheel-neoforge-${project.property("minecraft_version")}:${project.property("flywheel_version")}")

    // Cloth Config
    jijImplement("me.shedaniel.cloth:cloth-config-neoforge:${project.property("cloth_config_version")}")

    // 可选mod依赖
    compileOnly("mezz.jei:jei-1.21.1-common-api:${project.property("jei_version")}")
    compileOnly("mezz.jei:jei-1.21.1-neoforge-api:${project.property("jei_version")}")
    runtimeOnly("mezz.jei:jei-${project.property("minecraft_version")}-neoforge:${project.property("jei_version")}")
    implementation("curse.maven:jade-324717:6291517")

    // 帕秋莉手册
    compileOnly("curse.maven:patchouli-306770:6164617")
    runtimeOnly("curse.maven:patchouli-306770:6164617")

    // Kubejs
    implementation("curse.maven:kubejs-238086:7278501")
    implementation("curse.maven:rhino-416294:7104526")

//    implementation("curse.maven:cupboard-326652:6078150")
//    implementation("curse.maven:connectivity-470193:6229173")

    // 冷汗
    implementation("curse.maven:cold-sweat-506194:6176789")

    // 真实相机
    compileOnly("curse.maven:real-camera-851574:${project.property("real_camera_id")}")

    // 网络音乐机
    implementation("curse.maven:net-music-978569:6838604")

    // 车万女仆
    implementation("curse.maven:touhou-little-maid-355044:7510722")

    // 测试用mod
    implementation("curse.maven:better-combat-by-daedelus-639842:6532547")
    implementation("curse.maven:playeranimator-658587:6024462")

    implementation("curse.maven:spark-361579:6225208")
    // (was also pulling curse.maven:irisshaders-455508 and curse.maven:sodium-394468 here for
    // client-only runs -- removed as redundant with (and conflicting against) the Sodium/Iris
    // substitution wired for the "maven.modrinth:sodium" coordinate right above, see
    // settings.gradle.kts's comment on mods/sodium's/mods/Iris's includeBuild: having more than one
    // jar claim the same mod ID crashed runClient with a NoClassDefFoundError for whichever real
    // Sodium class didn't happen to be in the jar NeoForge picked.)
    implementation("maven.modrinth:sodium:mc1.21.1-0.6.13-neoforge")

    implementation("curse.maven:create-328085:7963363")
    // Sable
    compileOnly("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0")
    // create-aeronautics (Create: Aeronautics) used to be pulled in here too, but nothing in this
    // module's own source (compat/ponder/, compat/sable/) actually references its API -- confirmed
    // by hand (zero hits for `dev.eriksonn.aeronautics`/`eriksonn` anywhere under src/). It was also
    // the actual cause of a real crash: create-aeronautics jarJars its own `simulated`/`offroad`
    // addons, all three requiring `sable [2.0.0,3.0.0)`, and Sable 2.x itself requires Sodium
    // 0.8.12-alpha.2+ -- far newer than the Sodium fork this repo vendors (mods/sodium, currently
    // 0.6.13). Since nothing here uses aeronautics, dropping it removes the whole forced chain
    // instead of trying to upgrade the vendored Sodium fork just to satisfy an unused dependency.
    // Sable itself stays on the older 1.2.2 line (compatible with 0.6.13 Sodium) since
    // compat/sable/SableCompatHandler.kt does use it for real.
    if (!project.gradle.startParameter.taskNames.any { it.contains("runData") }) {
        implementation("curse.maven:sable-1312371:8007005")
    } else {
        compileOnly("curse.maven:sable-1312371:8007005")
    }
//    implementation(fg.deobf("curse.maven:mmmmmmmmmmmm-225738:6237015"))
//    implementation(fg.deobf("curse.maven:selene-499980:6249659"))
}

// This block of code expands all declared replace properties in the specified resource targets.
// A missing property will result in an error. Properties are expanded using ${} Groovy notation.
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "minecraft_version_range" to project.property("minecraft_version_range"),
        "neo_version" to project.property("neo_version"),
        "neo_version_range" to project.property("neo_version_range"),
        "loader_version_range" to project.property("loader_version_range"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_license" to project.property("mod_license"),
        "mod_version" to project.property("mod_version"),
        "mod_authors" to project.property("mod_authors"),
        "mod_description" to project.property("mod_description")
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
// Include the output of "generateModMetadata" as an input directory for the build
// this works with both building through Gradle and the IDE.
sourceSets.main.get().resources.srcDir(generateModMetadata)
// To avoid having to run "generateModMetadata" manually, make it run on every project reload
neoForge.ideSyncTask(generateModMetadata)

// Fast development build — compiles code only, skips JAR packaging.
// Serves as the compilation prerequisite for runClient and runServer.
// Workflow: devBuild (check errors) → runClient / runServer (launch game)
tasks.register("devBuild") {
    description = "Fast dev build: compile + resources only, no JAR packaging"
    group = "build"
    dependsOn("classes", "processResources")
}

// Wire devBuild as the compilation step for dev run tasks.
// Since runClient/runServer already depend on classes+processResources transitively,
// this is mostly for explicit ordering — but ensures devBuild stays the single
// compilation entry point for all dev workflows.
tasks.named("runClient") { dependsOn("devBuild") }
tasks.named("runServer") { dependsOn("devBuild") }

// Example configuration to allow publishing using the maven-publish plugin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            url = uri(project.projectDir.resolve("repo"))
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8" // Use the UTF-8 charset for Java compilation
}

tasks.processResources {
    from("COPYING", "COPYING.LESSER")
}

tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("generateModMetadata"))
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

kotlin {
    jvmToolchain(21)
}