import org.gradle.language.jvm.tasks.ProcessResources
import org.slf4j.event.Level

plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev") version "2.0.144"
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

val minecraft_version: String by project
val minecraft_version_range: String by project
val neo_version: String by project
val neo_version_range: String by project
val loader_version_range: String by project
val parchment_mappings_version: String by project
val parchment_minecraft_version: String by project
val mod_id: String by project
val mod_name: String by project
val mod_license: String by project
val mod_version: String by project
val mod_group_id: String by project
val mod_authors: String by project
val mod_description: String by project
val mods_tacz_version: String by project
val mods_firstaid_version: String by project
val mods_automobility_version: String by project
val mods_superbwarfare_version: String by project

// Sodium and Iris used to be pinned here and pulled from Modrinth for TACZ's own compat code
// (mods/TACZ-1.21.1/gradle/libs.versions.toml) -- both are now built from source instead (see
// mods/sodium, mods/Iris, both SunsetDN forks) and substituted straight into TACZ's dependency via
// settings.gradle.kts's dependencySubstitution, so there's no version to pin here anymore.

version = mod_version
group = mod_group_id

// Minecraft's own dependency set (via net.neoforged:minecraft-dependencies) pins
// commons-io to 2.15.1 with a strict constraint. engine/fmod requests 2.18.0, which
// conflicts with that constraint; force everything onto the version Minecraft ships,
// since commons-io is source/binary compatible across these minor releases.
//
configurations.all {
    resolutionStrategy.force(
        "commons-io:commons-io:2.15.1",
    )
}

// Patchouli (a runtimeOnly dependency of mods/SuperbWarfare, for its guide book only; nothing here or in SuperbWarfare's
// code references it) crashes the dev client in FMLCommonSetupEvent: `ServiceConfigurationError: IXplatAbstractions:
// NeoForgeXplatImpl not a subtype` (the jar is both a mod module and on the classpath, so ServiceLoader on a worker thread
// sees a second copy of its interface). Left out of the dev runtime only; distribution jars are unaffected.
configurations.configureEach {
    if (isCanBeResolved && name.contains("untime", ignoreCase = true)) {
        exclude(group = "curse.maven", module = "patchouli-306770")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content { includeGroup("thedarkcolour") }
    }

    // Required to resolve TACZ's (mods/TACZ-1.21.1) own dependencies, since
    // Gradle resolves the whole project graph using the consuming project's
    // (this one's) repositories rather than the included build's.
    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.rtyley")
            includeGroup("com.github.FiguraMC.luaj")
            includeGroup("com.github.mcmodderanchor")
            includeGroup("com.github.MCModderAnchor")
        }
    }
    maven { url = uri("https://maven.shedaniel.me") }
    maven { url = uri("https://maven.kosmx.dev") }
    maven { url = uri("https://maven.blamejared.com") }
    // Required to resolve SuperbWarfare's (mods/SuperbWarfare) own dependencies, for the same
    // reason as the jitpack/shedaniel/blamejared repos above.
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroupByRegex("software\\.bernie.*")
            includeGroup("com.eliotlash.mclib")
        }
    }
    maven {
        url = uri("https://maven.theillusivec4.top/")
        content { includeGroup("top.theillusivec4.curios") }
    }
    maven { url = uri("https://maven.createmod.net") }
    maven { url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") }
    maven { url = uri("https://maven.ryanhcode.dev/releases") }
    // opus-jni-rust (com.plasmoverse:opus-jni-rust) -- existing Rust/jni-rs Opus JNI binding
    // (github.com/plasmoapp/opus-jni-rust, from the Plasmo Voice team) used for the close-range
    // voice chat tier's Opus codec, in place of hand-writing new JNI bindings.
    maven {
        url = uri("https://repo.plasmoverse.com/releases")
        content { includeGroup("com.plasmoverse") }
    }
    maven {
        url = uri("https://maven.architectury.dev")
        content { includeGroup("dev.architectury") }
    }
    maven {
        url = uri("https://maven.latvian.dev/releases")
        content {
            includeGroup("dev.latvian.mods")
            includeGroup("dev.latvian.apps")
        }
    }
    // Required to resolve Sodium's (mods/sodium) and Iris's (mods/Iris) own dependencies, for the
    // same reason as the jitpack/shedaniel/blamejared repos above -- su5ed.dev hosts the
    // forgified-fabric-api modules both jarJar-embed, and caffeinemc.net hosts the published
    // Sodium artifact Iris compiles against (compileOnly, not substituted -- see
    // settings.gradle.kts's comment on this includeBuild).
    maven {
        url = uri("https://maven.su5ed.dev/releases")
        content { includeGroup("org.sinytra.forgified-fabric-api") }
    }
    maven {
        url = uri("https://maven.caffeinemc.net/releases")
        content { includeGroup("net.caffeinemc") }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "CurseForge"
                url = uri("https://cursemaven.com")
            }
        }
        filter { includeGroup("curse.maven") }
    }
    flatDir { dirs("mods/TACZ-1.21.1/libs") }
    flatDir { dirs("mods/SuperbWarfare/libs") }
}

base {
    archivesName.set(mod_id)
}

// Unified on Java 21 across the whole repo (root, TACZ, First Aid, engine/audio, engine/fmod) --
// what Mojang ships to end users for Minecraft 1.21.1, so it's what players actually have.
// This was Java 25 until 2026-08-26: engine/fmod's old jextract/java.lang.foreign bindings
// needed JDK 24+ (SymbolLookup.findOrThrow, added in JDK 24; java.lang.foreign itself only
// stabilized out of preview in JDK 22), and TACZ loads engine/fmod's classes directly at
// runtime, so the requirement cascaded to the whole repo (UnsupportedClassVersionError
// otherwise). engine/fmod now uses hand-written JNI instead (see its build.gradle.kts/
// CMakeLists.txt), which has no such floor -- don't reintroduce a jextract/FFM-based binding
// without solving this the same way, or this comment (and the Java 21 pin) will need reverting.
java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
tasks.withType<JavaCompile> {
    options.release.set(21)
}
kotlin.jvmToolchain(21)

neoForge {
    // Specify the version of NeoForge to use.
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }

    // This line is optional. Access Transformers are automatically detected
    // accessTransformers.add("src/main/resources/META-INF/accesstransformer.cfg")

    // Default run configurations.
    // These can be tweaked, removed, or duplicated as needed.
    runs {
        create("client") {
            // IDE run-config generation goes through the "idea-ext" Gradle plugin, whose
            // Groovy-compiled classes (JavaRunConfiguration.toMap, RunConfigurations.groovy) call
            // a DefaultGroovyMethods.collect(Collection, Closure) overload that Gradle 9.4.1's
            // bundled Groovy runtime no longer provides that way -- a NoSuchMethodError during
            // IntelliJ sync (ModDevGradle 2.0.144 only officially supports Gradle 8.8). Disabled
            // here since these runs are launched via ./gradlew runClient/runServer/etc. anyway
            // (see AGENTS.md), not via IDE-generated run configurations.
            disableIdeRun()
            client()
            jvmArgument("-Xmx10G")
            jvmArgument("-Xms4G")
            // short GC pauses (JFR 2026-11-05: 4 GB heap, 34 GCs/min, pauses up to 85 ms)
            jvmArgument("-XX:+UnlockExperimentalVMOptions") // G1NewSizePercent/G1ReservePercent are experimental flags
            jvmArgument("-XX:MaxGCPauseMillis=40")
            jvmArgument("-XX:G1NewSizePercent=30")
            jvmArgument("-XX:G1ReservePercent=20")
            jvmArgument("-XX:+ParallelRefProcEnabled")
            // WindowOverlay.ensureAwtAvailable() resets GraphicsEnvironment's cached `headless`
            // field via reflection (see its doc) so BrowserOverlay/JCEF can use AWT despite
            // Main.java forcing java.awt.headless=true at boot -- needs java.awt's internals
            // opened. NeoForge's ModLauncher actually loads this mod into a *named* module
            // ("dncity", not the unnamed module -- confirmed from a stack trace reading
            // "TRANSFORMER/dncity@1.0-SNAPSHOT..."), so ALL-UNNAMED alone doesn't cover it;
            // both target modules are listed (comma-separated target-module list per --add-opens
            // syntax) since which module ModLauncher uses isn't being relied on further.
            jvmArgument("--add-opens=java.desktop/java.awt=ALL-UNNAMED,dncity")

            // Unattended CPU profiling: `./gradlew runClient -Pdncity.quickPlay="New World" -Pdncity.jfr=<file.jfr>`
            // opens that singleplayer world straight away and records a Java Flight Recording
            // (`-Pdncity.jfrDelay`/`-Pdncity.jfrDuration`, default 150s/90s, so it starts after world load).
            providers.gradleProperty("dncity.quickPlay").orNull?.let {
                programArgument("--quickPlaySingleplayer")
                programArgument(it)
            }
            providers.gradleProperty("dncity.jfr").orNull?.let {
                val delay = providers.gradleProperty("dncity.jfrDelay").orNull ?: "150s"
                val duration = providers.gradleProperty("dncity.jfrDuration").orNull ?: "90s"
                jvmArgument("-XX:StartFlightRecording=delay=$delay,duration=$duration,settings=profile,filename=$it")
            }

            // Comma-separated list of namespaces to load gametests from. Empty = all namespaces.
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        // Second dev client with a different username/uuid and its own game directory (so it
        // doesn't fight the "client" run over run/options.txt, logs, etc. if launched at the
        // same time) -- for testing multiplayer-only features (radio, proximity chat) locally
        // by connecting two clients to the same server/LAN world at once.
        create("client2") {
            disableIdeRun()
            client()
            jvmArgument("-Xmx10G")
            jvmArgument("-Xms4G")
            // short GC pauses (JFR 2026-11-05: 4 GB heap, 34 GCs/min, pauses up to 85 ms)
            jvmArgument("-XX:+UnlockExperimentalVMOptions") // G1NewSizePercent/G1ReservePercent are experimental flags
            jvmArgument("-XX:MaxGCPauseMillis=40")
            jvmArgument("-XX:G1NewSizePercent=30")
            jvmArgument("-XX:G1ReservePercent=20")
            jvmArgument("-XX:+ParallelRefProcEnabled")
            jvmArgument("--add-opens=java.desktop/java.awt=ALL-UNNAMED,dncity")
            devLogin = false
            gameDirectory = file("run-client2")
            programArgument("--username")
            programArgument("Client2")
            programArgument("--uuid")
            programArgument("00000000-0000-0000-0000-000000000002")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("server") {
            disableIdeRun()
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        // This run config launches GameTestServer and runs all registered gametests, then exits.
        // By default, the server will crash when no gametests are provided.
        // The gametest system is also enabled by default for other run configs under the /test command.
        create("gameTestServer") {
            disableIdeRun()
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("data") {
            disableIdeRun()
            data()

            // example of overriding the workingDirectory set in configureEach above, uncomment if you want to use it
            // gameDirectory = project.file("run-data")

            // Specify the modid for data generation, where to output the resulting resource, and where to look for existing resources.
            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }

        // applies to all the run configs above
        configureEach {
            // Redirects the whole run JVM's temp directory off of the default OS temp root
            // (AppData\Local\Temp on Windows). Needed for opus-jni-rust: its own OpusLibrary.load()
            // extracts its native DLL into a fresh subdirectory of java.io.tmpdir
            // (Files.createTempDirectory) and loads it from there -- confirmed by hand that this
            // dev machine's Application Control policy (AppLocker/WDAC) silently blocks that
            // (UnsatisfiedLinkError on OpusEncoder.createNative even though extraction/System.load
            // themselves report no error), the same class of problem already documented in
            // AGENTS.md for engine/fmod's FModLoad and engine/audio's NativeLibrary -- except here
            // the fix can't be "extract to the temp root, not a subdirectory" since it's
            // opus-jni-rust's own code doing the extracting, not ours. Must be a real `-D` JVM
            // argument (not a post-launch System.setProperty) so it's in effect before the JVM's
            // internal temp-file helpers cache `java.io.tmpdir` on first use. This directory must
            // actually exist (see mkdirs() call below) -- Files.createTempDirectory's parent must
            // already exist.
            val devTmpDir = file("$rootDir/.gradle/devRunTmp")
            devTmpDir.mkdirs()
            jvmArgument("-Djava.io.tmpdir=${devTmpDir.absolutePath}")

            // Recommended logging data for a userdev environment
            // The markers can be added/remove as needed separated by commas.
            // "SCAN": For mods scan.
            // "REGISTRIES": For firing of registry events.
            // "REGISTRYDUMP": For getting the contents of all registries.
            systemProperty("forge.logging.markers", "REGISTRIES")

            // Recommended logging level for the console
            // You can set various levels here.
            // Please read: https://stackoverflow.com/questions/2031163/when-to-use-the-different-log-levels
            logLevel = Level.DEBUG

            // Plain (non-mod) jarJar'd library dependencies -- see the `dependencies` block's
            // comment on engine:audio for why this is needed on top of `implementation`/`jarJar`.
            // Each RunModel's `additionalRuntimeClasspathConfiguration` is a real per-run
            // Configuration (named e.g. "clientAdditionalRuntimeClasspath"), not a DSL function,
            // so dependencies are added to it by name via the project's DependencyHandler.
            // (engine:audio is plain Java specifically so it has no transitive kotlin-stdlib to
            // fight over here -- an earlier Kotlin version of it hit a "reads more than one
            // module named kotlin.stdlib" JPMS error from ending up in a different module layer
            // than the copy already on the main classpath. See engine/audio/build.gradle.kts.)
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "engine:audio:1.0")
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "engine:window:1.0")
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "engine:kcp:1.0")
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "com.plasmoverse:opus-jni-rust:1.0.4")
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "org.bouncycastle:bcpg-jdk18on:1.78.1")
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "org.bouncycastle:bcprov-jdk18on:1.78.1")
            // Same reason as the three above -- confirmed by hand: without this, runClient throws
            // NoClassDefFoundError on NanoVGGL3 even though it's on the compile classpath fine
            // (compileKotlin/jarJar packaging don't need this; only the dev-run classpath does).
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "org.lwjgl:lwjgl-nanovg:3.3.3")
            project.dependencies.add(additionalRuntimeClasspathConfiguration.name, "org.lwjgl:lwjgl-nanovg:3.3.3:natives-windows")
        }
    }

    mods {
        // define mod <-> source bindings
        // these are used to tell the game which sources are for which mod
        // mostly optional in a single mod project
        // but multi mod projects should define one per mod
        create(mod_id) {
            sourceSet(sourceSets["main"])
        }
    }
}

// Include resources generated by data generators.
sourceSets["main"].resources.srcDir("src/generated/resources")

// Externally-published (curse.maven) mods that mods/SuperbWarfare's own build.gradle.kts pulls in
// as plain `implementation` dependencies (not jarJar'd into SuperbWarfare's own jar) -- Create
// (used by SuperbWarfare's compat/ponder/ tutorial scenes) and Sable (used by
// compat/sable/SableCompatHandler.kt). create-aeronautics was tried here too, but removed --
// nothing in SuperbWarfare's own source uses it, and it jarJars its own simulated/offroad addons
// which all require Sable 2.x, which itself requires a newer Sodium than the fork this repo
// vendors (mods/sodium, 0.6.13) -- confirmed by hand via the exact NeoForge mod-loading-failure
// chain this caused. Composite-build project dependencies (like SuperbWarfare here) propagate
// `implementation`-scoped deps onto this project's own runtimeClasspath, so runClient already
// sees Create/Sable fine -- but collectDistributionJars below only copies each submodule's OWN
// built jar, never what that submodule depends on, so a packaged `./gradlew build` output was
// missing these even though runClient worked. Re-declared here (same coordinates
// SuperbWarfare/build.gradle.kts pulls -- keep them in sync by hand if SuperbWarfare's own
// versions change) purely to give collectDistributionJars a resolvable configuration to copy
// from; Gradle resolves/dedupes to the same jars either way.
val bundledExternalMods: Configuration by configurations.creating

// Plain JVM unit tests (pure logic only: terminal ballistics units/state machines). No Minecraft on their classpath.
tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.10.1")

    // See mods/SuperbWarfare/build.gradle.kts's own `curse.maven:create-328085`/
    // `curse.maven:sable-1312371` lines.
    bundledExternalMods("curse.maven:create-328085:7963363")
    bundledExternalMods("curse.maven:sable-1312371:8007005")

    implementation("thedarkcolour:kotlinforforge-neoforge:5.3.0")

    // Raw Netty API for network.kcp's self-built KCP-over-UDP voice/radio/phone-call transport
    // (VoiceKcpServer/VoiceKcpClient) -- compileOnly, not implementation/jarJar: NeoForge already
    // ships this exact netty-transport jar (Bootstrap/NioDatagramChannel/etc.) transitively at
    // runtime for its own networking, so this is compile-time symbols only. Pinned to the version
    // actually resolved from NeoForge 21.1.248's own dependency graph (confirmed via the IDE's
    // resolved classpath) rather than left to float, so a NeoForge bump can't silently drift this
    // out of sync with what's really on the runtime classpath.
    compileOnly("io.netty:netty-transport:4.1.97.Final")

    // TACZ, included as a composite build submodule (mods/TACZ-1.21.1) so it
    // shares this project's NeoForge version and Minecraft artifact instead
    // of generating its own.
    implementation("com.tacz:tacz-neoforge-1.21.1:${mods_tacz_version}")

    // First Aid, included as a composite build submodule
    // (mods/First-Aid-New/neoforge1.21.1) for the same reason as TACZ.
    implementation("ichttt.mods.firstaid:firstaid:${mods_firstaid_version}")

    // Automobility, included as a composite build submodule (mods/Automobility, neoforge
    // subproject only -- see settings.gradle.kts) for the same reason as TACZ/First Aid.
    implementation("io.github.foundationgames:automobility:${mods_automobility_version}")

    // SuperbWarfare, included as a composite build submodule (mods/SuperbWarfare, tracking
    // upstream's NeoForge/1.21.1 "1.21" branch directly -- see settings.gradle.kts) for the same
    // reason as TACZ/First Aid/Automobility.
    implementation("com.atsuishio.superbwarfare:superbwarfare:${mods_superbwarfare_version}")

    // engine/audio (miniaudio JNI capture/playback bridge), wired in via includeBuild in
    // settings.gradle.kts -- see io.github.jwyoon1220.dncity.audio.NativeAudio. jarJar embeds it
    // (same pattern TACZ uses for engine/fmod) so it's not just a dev-run classpath artifact --
    // it ships inside the mod jar and reaches players. jarJar's embedding only actually takes
    // effect for an already-packaged mod jar (FML's jar-in-jar extraction runs against a real
    // jar file) -- on MC 1.21.1's ModDevGradle, runClient/runClient2/runServer instead launch
    // straight from this project's compiled classes directory, which has no jar to extract from,
    // so a plain library dependency (not a mod, so FML's own mod-file loader won't pick it up
    // either) silently never reaches the dev-run classpath without *also* being added to each
    // run's `additionalRuntimeClasspath` below (see the `runs { configureEach { ... } }` block).
    // (Confirmed by a NoClassDefFoundError on NativeAudio when that was missing; see
    // https://docs.neoforged.net/toolchain/docs/dependencies/nonmclibs/ -- fixed in 1.21.9+,
    // where jarJar deps are added to run classpaths automatically.)
    implementation("engine:audio:1.0")
    jarJar("engine:audio:1.0")

    // Opus codec (close-range voice chat tier -- see io.github.jwyoon1220.dncity.audio.OpusCodec)
    // via opus-jni-rust, a prebuilt multi-platform (win/linux/mac, x86/x86_64/aarch64) JNI
    // binding, jarJar'd for the same reason as engine:audio above (also needs
    // additionalRuntimeClasspath, see the `runs` block).
    implementation("com.plasmoverse:opus-jni-rust:1.0.4")
    jarJar("com.plasmoverse:opus-jni-rust:1.0.4")

    // engine/fmod (hand-written JNI FMOD bindings, see AGENTS.md) -- was already an indirect
    // dependency via TACZ's own jarJar, but that's TACZ's private copy; depending on it directly
    // here gives DNCity its own FMODCoreSystem instance (io.github.jwyoon1220.dncity.music.
    // AudioPlayer) for direct OGG/FLAC/MP3/Opus file playback, independent of TACZ being
    // installed at all. Same version as TACZ's own dependency (1.0), so FML's jar-in-jar
    // deduplication (same group:artifact:version embedded by two mods resolves to one shared
    // copy) applies rather than loading the native library twice.
    implementation("engine:fmod:1.0")
    jarJar("engine:fmod:1.0")

    // engine/window (Win32 child-window/AWT-reparenting JNI bridge, see
    // io.github.jwyoon1220.dncity.client.window and AGENTS.md). Same jarJar +
    // additionalRuntimeClasspath requirement as engine:audio above -- see that entry's comment
    // (also plain Java, no external dependencies of its own).
    implementation("engine:window:1.0")
    jarJar("engine:window:1.0")

    // engine/kcp (C++/JNI port of this mod's KCP-over-UDP transport, see
    // io.github.jwyoon1220.dncity.network.kcp.NativeKcpSession and AGENTS.md). Same jarJar +
    // additionalRuntimeClasspath requirement as engine:audio/engine:window above -- see that
    // entry's comment (also plain Java, no external dependencies of its own).
    implementation("engine:kcp:1.0")
    jarJar("engine:kcp:1.0")

    // ModernUI-MC (mods/ModernUI-MC), for the phone screen's Fragment/View host (see
    // io.github.jwyoon1220.dncity.client.phone.PhoneFragment). Not a dependencySubstitution like TACZ/First
    // Aid/Automobility/SuperbWarfare above -- its "neoforge" subproject builds with Architectury
    // Loom, a different Minecraft mapping/toolchain than this project's net.neoforged.moddev
    // (see settings.gradle.kts's comment on this includeBuild), so a plain project substitution
    // would resolve to its *default* jar task's output, which is Loom-intermediary-mapped and not
    // link-compatible with this project's Mojang-mapped bytecode -- the exact class of bug hit
    // earlier with Sodium's service/mod jar split (see settings.gradle.kts). Only remapJar's
    // "-universal.jar" (same one collectDistributionJars below already depends on) is usable, so
    // it's added as a plain file dependency instead, built by the task dependency further down.
    implementation(fileTree("mods/ModernUI-MC/neoforge/build/libs") {
        include("*-universal.jar")
    })

    // VoxelMap (mods/VoxelMap), for the phone screen's Map app (see
    // io.github.jwyoon1220.dncity.client.phone.nanovg's MAP page), which opens VoxelMap's own
    // fullscreen world map screen (com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap) directly.
    // Same reasoning as ModernUI-MC above for using a plain file dependency instead of a
    // dependencySubstitution: VoxelMap's "neoforge" subproject already bundles ":common"'s
    // compiled classes straight into its own plain `jar` task output (see
    // mods/VoxelMap/neoforge/build.gradle.kts's `tasks.jar` block and settings.gradle.kts's
    // comment on this includeBuild), so there's no separate remap/jarJar step to depend on --
    // just that one jar, built by the task dependency further down.
    implementation(fileTree("mods/VoxelMap/build/libs") {
        include("*.jar")
    })

    // Sound Physics Remastered (mods/sound-physics-remastered) -- not referenced by this repo's
    // own code (unlike ModernUI-MC/VoxelMap above), just a standalone mod that needs to be on the
    // dev runtime's classpath for runClient/runClient2/runServer to actually load it, and built
    // for collectDistributionJars to pick up. Its "neoforge" subproject's shadowJar task (not the
    // plain "jar") is the shaded, distributable jar -- its own shared build script
    // (henkelmax/mod-gradle-scripts's mod.gradle) sets shadowJar's archiveClassifier to "" for
    // neoforge specifically, so it ends up as the one plain-named jar in this directory (see
    // settings.gradle.kts's comment on this submodule's includeBuild).
    implementation(fileTree("mods/sound-physics-remastered/neoforge/build/libs") {
        include("*.jar")
    })

    // Distant Horizons (mods/distant-horizons) -- distant-terrain LOD rendering, not referenced by
    // this repo's own code (same as Sound Physics Remastered above), just a standalone mod that
    // needs to be on the dev runtime's classpath for runClient/runClient2/runServer to load it, and
    // built for collectDistributionJars to pick up. Its "neoforge" subproject's remapJar task
    // produces the unclassified jar NeoForge is meant to load: the shaded shadowJar ("-all.jar")
    // plus META-INF/accesstransformer.cfg converted from DH's access widener (remapJar's
    // atAccessWideners). The "-all.jar" alone lacks that file, so DH's own mixins that read
    // widened fields (MixinOptionsScreen -> HeaderAndFooterLayout.headerFrame) crash with
    // IllegalAccessError -- never consume the "-all.jar" directly. Same reasoning as
    // ModernUI-MC/VoxelMap above for avoiding a plain project substitution.
    implementation(fileTree("mods/distant-horizons/neoforge/build/libs") {
        include("DistantHorizons-neoforge-*.jar")
        exclude("*-all.jar", "*-sources.jar", "*-dev.jar")
    })

    // Lithium (mods/lithium) -- general server/client performance optimizations, not referenced by
    // this repo's own code (same as Sound Physics Remastered/Distant Horizons above), just a
    // standalone mod that needs to be on the dev runtime's classpath for
    // runClient/runClient2/runServer to load it, and built for collectDistributionJars to pick up.
    // Its "neoforge" subproject's plain "jar" task is its full distributable jar (already bundles
    // ":common" -- see settings.gradle.kts's comment on this includeBuild), same shape as
    // Sodium/Iris/VoxelMap above.
    implementation(fileTree("mods/lithium/build/libs") {
        include("*.jar")
    })

    // NanoVG (phone screen's chrome/keypad rendering, see
    // io.github.jwyoon1220.dncity.client.phone.nanovg) -- LWJGL's own prebuilt bindings, classic
    // JNI like the rest of LWJGL 3.3.x (no java.lang.foreign/Panama, so no Java-24-floor risk --
    // see AGENTS.md's "Toolchain" section). Pinned to exactly 3.3.3 since Minecraft 1.21.1's own
    // dependency management pins org.lwjgl:lwjgl itself to `strictly 3.3.3`; lwjgl-nanovg:3.3.3's
    // own POM depends on org.lwjgl:lwjgl:3.3.3, which satisfies that constraint exactly, so no
    // resolutionStrategy.force is needed here (unlike the commons-io force above). natives-windows
    // only, matching this project's existing Windows-only precedent (see engine/window's
    // build.gradle.kts). jarJar'd the same way com.plasmoverse:opus-jni-rust is above, so the
    // natives actually ship inside the distributed mod jar, not just runClient.
    implementation("org.lwjgl:lwjgl-nanovg:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-nanovg:3.3.3:natives-windows")
    jarJar("org.lwjgl:lwjgl-nanovg:3.3.3")
    // NOT jarJar("org.lwjgl:lwjgl-nanovg:3.3.3:natives-windows"): JarJar identifies an embedded jar by group:artifact only,
    // so the classifier jar and the main jar collided, NeoForge kept just the natives one and the game died with
    // "Module org.lwjgl.nanovg not found, required by org.lwjgl.nanovg.natives" (invisible in runClient, where both are
    // on the classpath). The natives jar is re-published below under its own artifact name instead.
    jarJar(":lwjgl-nanovg-natives-windows:3.3.3")

    // BouncyCastle OpenPGP (io.github.jwyoon1220.dncity.security's login-gate PGP
    // challenge/response, see PgpCrypto) -- not shipped by NeoForge/Minecraft, so jarJar'd the
    // same way as the other plain-library deps above (also needs additionalRuntimeClasspath, see
    // the `runs` block, for the same ModDevGradle-doesn't-jarJar-onto-dev-runs reason documented
    // on engine:audio's dependency above).
    implementation("org.bouncycastle:bcpg-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    jarJar("org.bouncycastle:bcpg-jdk18on:1.78.1")
    jarJar("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // bcpg's module (org.bouncycastle.pg) requires org.bouncycastle.util, which is bcutil: a transitive dependency that only
    // runClient's classpath picks up. jarJar does not follow it, so a distributed jar failed with "Module org.bouncycastle.util not found".
    jarJar("org.bouncycastle:bcutil-jdk18on:1.78.1")
}

// The natives-windows jar of lwjgl-nanovg under a distinct artifact name (see the jarJar comment in `dependencies`).
// Copied at configuration time on purpose: Gradle resolves the jarJar configuration while it builds the task graph, i.e.
// before any copy task could have run, and a flatDir file has to exist by then.
run {
    val natives = configurations.detachedConfiguration(
        dependencies.create("org.lwjgl:lwjgl-nanovg:3.3.3:natives-windows"),
    ).apply { isTransitive = false }.singleFile
    val dir = layout.buildDirectory.dir("jarjar-libs").get().asFile
    dir.mkdirs()
    val target = File(dir, "lwjgl-nanovg-natives-windows-3.3.3.jar")
    if (!target.isFile || target.length() != natives.length()) natives.copyTo(target, overwrite = true)
}
repositories {
    flatDir { dirs(layout.buildDirectory.dir("jarjar-libs")) }
}

// The ModernUI-MC universal jar (see the fileTree dependency above) has to actually be built
// before anything compiles against it or runs with it on the classpath.
listOf("compileJava", "compileKotlin", "runClient", "runClient2", "runServer", "runGameTestServer", "runData").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(gradle.includedBuild("ModernUI-MC").task(":ModernUI-NeoForge:remapJar"))
        dependsOn(gradle.includedBuild("VoxelMap").task(":neoforge:jar"))
        dependsOn(gradle.includedBuild("sound-physics-remastered").task(":neoforge:shadowJar"))
        dependsOn(gradle.includedBuild("distant-horizons").task(":neoforge:remapJar"))
        dependsOn(gradle.includedBuild("lithium").task(":neoforge:jar"))
    }
}

// This block of code expands all declared replace properties in the specified resource targets.
// A missing property will result in an error. Properties are expanded using ${} Groovy notation.
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version" to neo_version,
        "neo_version_range" to neo_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

// Include the output of "generateModMetadata" as an input directory for the build
// this works with both building through Gradle and the IDE.
sourceSets["main"].resources.srcDir(generateModMetadata)
// To avoid having to run "generateModMetadata" manually, make it run on every project reload
neoForge.ideSyncTask(generateModMetadata)

// Stages engine/browserhost's shadow jar as a classpath resource (client/window/
// BrowserHostProcess.kt extracts it to a real temp file and spawns it as a plain `java -jar`
// subprocess at runtime -- see that module's own build.gradle.kts doc for why JCEF/AWT now live
// in a separate child process rather than in-process). Generated into a build directory (not
// checked-in src/main/resources) since it's a large rebuildable binary, same reasoning as
// generateModMetadata's expanded template output above.
val browserHostResourceDir = layout.buildDirectory.dir("generated/browserhostResources")
val stageBrowserHostJar = tasks.register<Copy>("stageBrowserHostJar") {
    group = "build"
    description = "Copies engine/browserhost's shadow jar into a classpath resource for BrowserHostProcess to extract and spawn at runtime."
    dependsOn(gradle.includedBuild("browserhost").task(":shadowJar"))
    from(file("engine/browserhost/build/libs/browserhost-all.jar"))
    into(browserHostResourceDir.map { it.dir("browserhost") })
}
sourceSets["main"].resources.srcDir(browserHostResourceDir)
tasks.named("processResources") {
    dependsOn(stageBrowserHostJar)
}

// Example configuration to allow publishing using the maven-publish plugin
publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(layout.projectDirectory.dir("repo"))
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

// Distributing this mod means handing out DNCity's own jar, the TACZ/First Aid submodule jars
// (see AGENTS.md's "Composite builds / submodules" -- they're separate mods, not merged into this
// one), and Sodium/Iris (TACZ is built against them, and this pack is meant to be played with
// them, both now built from source -- see mods/sodium, mods/Iris) together, which by default land
// in several different build/libs directories (each submodule is its own included build). Copy
// all of them into this project's own build/libs so a plain "./gradlew build" leaves one folder
// with everything needed to distribute.
// Sync (not Copy) deliberately -- a plain Copy task never removes files it previously wrote that
// a later run no longer produces (e.g. a stale jar left behind after bumping a dependency's
// version, like sable-1312371-8007005.jar/sable-2.0.5+mc1.21.1.jar coexisting after the Sable
// bump above), which would ship two copies of the same mod ID and crash NeoForge's mod loader
// with a duplicate-mod-id error. Sync deletes anything in the destination that isn't part of
// this run's `from(...)` set.
val collectDistributionJars = tasks.register<Sync>("collectDistributionJars") {
    group = "distribution"
    description = "Copies this project's jar, the TACZ/First Aid/Automobility/SuperbWarfare/ModernUI-MC/Sodium/Iris/VoxelMap/Sound Physics Remastered/Distant Horizons/Lithium submodule jars, and SuperbWarfare's own externally-published mod dependencies (Create/Create: Aeronautics/Sable) into the root build/libs."

    dependsOn(gradle.includedBuild("TACZ-1.21.1").task(":jar"))
    dependsOn(gradle.includedBuild("First-Aid-New").task(":jar"))
    dependsOn(gradle.includedBuild("Automobility").task(":neoforge:jar"))
    dependsOn(gradle.includedBuild("SuperbWarfare").task(":jar"))
    // ModernUI-MC's neoforge subproject is registered under the name "ModernUI-NeoForge" (see
    // mods/ModernUI-MC/settings.gradle), which is also its task-path segment, not "neoforge".
    // remapJar (not the plain jar) produces the self-contained "-universal.jar" this repo wants
    // to distribute -- see settings.gradle.kts's comment on this submodule's includeBuild.
    dependsOn(gradle.includedBuild("ModernUI-MC").task(":ModernUI-NeoForge:remapJar"))
    // Sodium's "neoforge" subproject builds its jarJar-bundled, distributable jar under the plain
    // "jar" task (see mods/sodium/neoforge/build.gradle.kts's tasks.jar block) -- unlike
    // Automobility's "neoforge" subproject, there's no separate remap step since Sodium doesn't
    // use Fabric Loom for the neoforge subproject itself (only "common", for MC/mappings
    // resolution -- see settings.gradle.kts's comment on this submodule's includeBuild).
    dependsOn(gradle.includedBuild("sodium").task(":neoforge:jar"))
    // Iris's "neoforge" subproject's plain "jar" task is its full distributable jar -- unlike
    // Sodium, it compiles "common"'s sources directly into its own main source set rather than
    // jarJar-embedding a separate mod jar (see settings.gradle.kts's comment on this includeBuild).
    dependsOn(gradle.includedBuild("Iris").task(":neoforge:jar"))
    // VoxelMap's "neoforge" subproject's plain "jar" task is its full distributable jar (already
    // bundles ":common" -- see settings.gradle.kts's comment on this includeBuild), same shape as
    // Iris's above.
    dependsOn(gradle.includedBuild("VoxelMap").task(":neoforge:jar"))
    // Sound Physics Remastered's "neoforge" subproject's shadowJar is its distributable jar --
    // see the fileTree dependency above's comment.
    dependsOn(gradle.includedBuild("sound-physics-remastered").task(":neoforge:shadowJar"))
    // Distant Horizons's "neoforge" subproject's remapJar produces the jar this repo distributes
    // (shaded, with its access transformer) -- see the fileTree dependency above's comment.
    dependsOn(gradle.includedBuild("distant-horizons").task(":neoforge:remapJar"))
    // Lithium's "neoforge" subproject's plain "jar" task is its full distributable jar -- see the
    // fileTree dependency above's comment.
    dependsOn(gradle.includedBuild("lithium").task(":neoforge:jar"))

    // Excludes sources/javadoc jars in case either submodule's build ever starts producing them
    // (neither does today) -- only the runtime mod jar belongs in a distribution folder.
    from(file("mods/TACZ-1.21.1/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    from(file("mods/First-Aid-New/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    from(file("mods/Automobility/neoforge/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    from(file("mods/SuperbWarfare/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    // Sodium's neoforge subproject's own "build" dir (its rootProject, not this repo's) --
    // its jar task's destinationDirectory is set explicitly to "<sodium root>/build/mods"
    // (mods/sodium/neoforge/build.gradle.kts), not the default "<subproject>/build/libs".
    from(file("mods/sodium/build/mods")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    // Iris's own "build" dir (its rootProject, i.e. mods/Iris/build, not the neoforge subproject's)
    // -- its jar task's destinationDirectory is set explicitly to "<Iris root>/build/libs"
    // (mods/Iris/neoforge/build.gradle.kts).
    from(file("mods/Iris/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    // Only the "universal" (self-contained, shaded) jar -- the same directory also has plain,
    // "-shadow", and "-sources" variants that aren't meant to be distributed standalone.
    from(file("mods/ModernUI-MC/neoforge/build/libs")) {
        include("*-universal.jar")
    }
    // VoxelMap's "neoforge" subproject's own "build" dir (its rootProject, i.e. mods/VoxelMap/build,
    // not the neoforge subproject's) -- its jar task's destinationDirectory is set explicitly to
    // "<VoxelMap root>/build/libs" (mods/VoxelMap/neoforge/build.gradle.kts), same as Iris.
    from(file("mods/VoxelMap/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    // Sound Physics Remastered's "neoforge" subproject's own build/libs -- shadowJar's
    // archiveClassifier is "" for neoforge, so it's the one plain-named jar here (see the
    // dependency block's comment above).
    from(file("mods/sound-physics-remastered/neoforge/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    // Lithium's "neoforge" subproject's own "build" dir (its rootProject, i.e. mods/lithium/build,
    // not the neoforge subproject's) -- its jar task's destinationDirectory is set explicitly to
    // "<lithium root>/build/libs" (mods/lithium/neoforge/build.gradle.kts), same as Iris/VoxelMap.
    from(file("mods/lithium/build/libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }
    // Distant Horizons's "neoforge" subproject's own build/libs -- only the remapJar output, same
    // reasoning as the fileTree dependency above (the "-all.jar" lacks the access transformer).
    from(file("mods/distant-horizons/neoforge/build/libs")) {
        include("DistantHorizons-neoforge-*.jar")
        exclude("*-all.jar", "*-sources.jar", "*-dev.jar")
    }
    // SuperbWarfare's own externally-published (Create/Create: Aeronautics/Sable) runtime
    // dependencies -- see this file's `bundledExternalMods` configuration declaration above for why.
    from(bundledExternalMods)
    into(layout.buildDirectory.dir("libs"))
    // The destination is also where this project's own `jar` task writes dncity-*.jar -- Sync
    // would otherwise delete it whenever this task happens to run after `jar` (task ordering
    // between the two isn't otherwise constrained; both are just `assemble` dependencies).
    preserve { include("dncity-*.jar") }
}

tasks.named("assemble") {
    dependsOn(collectDistributionJars)
}

// engine/launcher: every root `build` also builds the launcher with this project's mods inside. The mods (dncity itself +
// everything collectDistributionJars gathers) are copied to engine/launcher/bundled-mods, then the launcher is built as a
// nested build (jpackage -> engine/launcher/build/dist/DNCity/DNCity.exe, plus an MSI when the WiX Toolset v3 is installed).
// A nested GradleBuild rather than includeBuild because the launcher needs this project's outputs as input.
val stageLauncherMods = tasks.register<Sync>("stageLauncherMods") {
    group = "distribution"
    description = "Copies dncity and the pack's mod jars into engine/launcher/bundled-mods for the launcher to ship."
    dependsOn(collectDistributionJars, tasks.jar)
    from(layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-sources.jar", "*-javadoc.jar", "*-slim.jar")
    }
    into(file("engine/launcher/bundled-mods"))
}
val buildLauncher = tasks.register<GradleBuild>("buildLauncher") {
    group = "distribution"
    description = "Builds the launcher (app folder, and MSI when WiX is installed) with the mods bundled."
    dir = file("engine/launcher")
    setTasks(listOf("assemble"))
    dependsOn(stageLauncherMods)
}
tasks.named("build") {
    dependsOn(buildLauncher)
}
