pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://maven.neoforged.net/releases") }
    }

}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

includeBuild("mods/TACZ-1.21.1") {
    dependencySubstitution {
        substitute(module("com.tacz:tacz-neoforge-1.21.1")).using(project(":"))
    }
}

includeBuild("mods/First-Aid-New") {
    dependencySubstitution {
        substitute(module("ichttt.mods.firstaid:firstaid")).using(project(":"))
    }
}

// engine/audio (miniaudio JNI bridge -- see AGENTS.md) is a standalone Gradle project, wired in
// here the same way as the TACZ/First Aid submodules above so its NativeAudio class and native
// library resource land on the main mod's classpath.
includeBuild("engine/audio") {
    dependencySubstitution {
        substitute(module("engine:audio")).using(project(":"))
    }
}

// engine/fmod (hand-written JNI FMOD bindings -- see AGENTS.md) was previously only reachable
// via TACZ's own includeBuild (mods/TACZ-1.21.1/settings.gradle.kts) -- included here too so the
// root project can depend on it directly, for FMODCoreSystem-based direct audio-file playback
// (io.github.jwyoon1220.dncity.music.AudioPlayer) independent of TACZ's own Studio system.
// Gradle deduplicates composite builds included from multiple places by build identity (the
// build's own root directory), so this doesn't create a second, conflicting instance of the
// module -- both this and TACZ's includeBuild resolve to the same one.
includeBuild("engine/fmod") {
    dependencySubstitution {
        substitute(module("engine:fmod")).using(project(":"))
    }
}

// engine/window (Win32 child-window/AWT-reparenting JNI bridge -- see AGENTS.md) follows the
// same standalone-composite-build pattern as engine/audio/engine/fmod.
includeBuild("engine/window") {
    dependencySubstitution {
        substitute(module("engine:window")).using(project(":"))
    }
}

// engine/kcp (C++/JNI port of this mod's KCP-over-UDP transport -- see AGENTS.md and this
// module's own build.gradle.kts doc comment) follows the same standalone-composite-build pattern
// as engine/audio/engine/fmod/engine/window.
includeBuild("engine/kcp") {
    dependencySubstitution {
        substitute(module("engine:kcp")).using(project(":"))
    }
}

// engine/browserhost (standalone JCEF/AWT child-process host -- see its own build.gradle.kts doc
// and AGENTS.md's "Architecture: window overlay" section) is included WITHOUT a
// dependencySubstitution, same reasoning as mods/ModernUI-MC below: it's never consumed as a
// library dependency of the root project (it only ever runs as an independent `java -jar`
// process with its own self-contained shadow jar) -- this include exists purely so the root
// build's stageBrowserHostJar task (see root build.gradle.kts) can depend on its `shadowJar` task
// output.
includeBuild("engine/browserhost")

// engine/launcher (standalone Swing game launcher). Included only so its tasks (packageApp, packageInstaller, ...) show up in
// the IDE's Gradle tool window; nothing depends on it through the composite. The root `buildLauncher` task runs it as a nested
// build instead, because the launcher needs the root project's jars as input (see root build.gradle.kts).
includeBuild("engine/launcher")

// Automobility (mods/Automobility) -- this fork strips it down to a single neoforge subproject
// (see mods/Automobility/settings.gradle.kts) since upstream's common/fabric setup pulled in
// fabric-loom, which needs a newer Gradle than this repo runs.
includeBuild("mods/Automobility") {
    dependencySubstitution {
        substitute(module("io.github.foundationgames:automobility")).using(project(":neoforge"))
    }
}

// SuperbWarfare (mods/SuperbWarfare) -- tracks upstream (Mercurows/SuperbWarfare) directly on its
// "1.21" branch rather than the SunsetDN fork used for the other submodules above: the SunsetDN
// fork only has an old Forge/Minecraft 1.20.1 branch, while upstream's own "1.21" branch is
// already a pure NeoForge/Minecraft 1.21.1 build (single project, no Forge/Fabric split), so no
// porting was needed.
includeBuild("mods/SuperbWarfare") {
    dependencySubstitution {
        substitute(module("com.atsuishio.superbwarfare:superbwarfare")).using(project(":"))
    }
}

// ModernUI-MC (mods/ModernUI-MC) -- unlike the submodules above, this one is included WITHOUT a
// dependencySubstitution: its neoforge subproject builds with Architectury Loom (a completely
// separate toolchain from this repo's net.neoforged.moddev-based ones, with its own independent
// Minecraft artifact resolution), and publishes several jar variants from one project (plain,
// shadow, sources, and the self-contained "universal" one -- see
// mods/ModernUI-MC/neoforge/build.gradle's remapJar/shadowJar tasks). A substitution would
// silently resolve to whichever jar happens to be the project's default artifact, not
// necessarily the "universal" one this repo actually wants to distribute -- so it's just
// included as a composite build (for its own build/run tasks and IDE integration) and its
// "universal" jar copied by collectDistributionJars in the root build.gradle.kts, the same way
// Sodium/Iris are, rather than substituted into a dependency.
includeBuild("mods/ModernUI-MC")

// Sodium and Iris (mods/sodium, mods/Iris -- SunsetDN forks) are pinned to OLD commits, not their
// branch tips: mods/TACZ-1.21.1 and mods/SuperbWarfare both depend on the exact same
// "maven.modrinth:sodium"/"maven.modrinth:iris" coordinates for their own compat mixins (pinned to
// Sodium mc1.21.1-0.6.13-neoforge), so substituting that one coordinate pair covers both consumers
// at once (SuperbWarfare's own extra "curse.maven:sodium-394468"/"curse.maven:irisshaders-455508"
// pulls, mods/SuperbWarfare/build.gradle.kts, were removed instead of substituted -- redundant once
// the shared coordinate above is covered, and substituting curse.maven coordinates here on top of
// the maven.modrinth ones hit an unrelated Gradle composite-build resolution bug where Sodium's own
// forgified-fabric-api dependency got resolved through Iris's "common" project's repositories
// instead of Sodium's own).
//
// The branch tips of these forks target a much newer, incompatible Sodium internal API
// (SunsetDN/Iris's own SODIUM_DEPENDENCY_NEO pins net.caffeinemc:sodium-neoforge-mod:0.8.12-beta.1,
// post their "sodium 0.8" commit) -- confirmed by hand: runClient crashed on a
// MixinPreProcessorException from Iris's own compat mixin needing a Sodium field the old pinned
// Modrinth version doesn't have. Both submodules are checked out at the last commit before that
// Sodium-0.8 rewrite (mods/sodium @ tag mc1.21.1-0.6.13, mods/Iris @ b4b2c22c1, whose own
// SODIUM_DEPENDENCY_NEO resolves to Sodium 0.6.9 -- same generation as the pinned
// mc1.21.1-0.6.13-neoforge everything else already expects) so their compat mixins target the same
// API generation the rest of the pack (TACZ, SuperbWarfare, and third-party jars like "sable") was
// actually built against.
//
// Both forks' "neoforge" subprojects' default jar already contains their real classes at this
// generation (unlike the current tip's service/mod jar split -- confirmed by hand), so a plain
// project substitution is enough to put exactly one real Sodium and one real Iris on the classpath
// -- multiple jars all claiming the same mod ID (before SuperbWarfare's extra pulls were removed)
// is what caused a runClient NoClassDefFoundError for a real Sodium class that existed in one of
// the stacked jars but not whichever one NeoForge actually picked.
// Substitutes the exact "group:artifact:version" coordinate, not just "group:artifact" --
// Modrinth's maven scheme reuses the exact same "maven.modrinth:sodium"/"maven.modrinth:iris"
// coordinate for every loader and Minecraft version of a project (version strings are really
// opaque file IDs), so a bare group:artifact substitution also silently intercepted mods/Iris's OWN
// internal Fabric-flavored SODIUM_DEPENDENCY_FABRIC reference inside its "common" project (used
// only for Fabric Loom's own remap tooling, a different, more constrained resolution context) --
// confirmed by hand: that misdirected it into resolving OUR NeoForge Sodium project's
// forgified-fabric-api dependency through common's own, narrower repositories, which don't have it,
// breaking runClient's dependency resolution outright. Pinning to the exact version leaves that
// internal Fabric reference alone.
includeBuild("mods/sodium") {
    dependencySubstitution {
        substitute(module("maven.modrinth:sodium:mc1.21.1-0.6.13-neoforge")).using(project(":neoforge"))
    }
}

includeBuild("mods/Iris") {
    dependencySubstitution {
        substitute(module("maven.modrinth:iris:1.8.12+1.21.1-neoforge")).using(project(":neoforge"))
    }
}

// VoxelMap (mods/VoxelMap, SunsetDN fork, "1.21.1" branch) -- minimap + fullscreen world map.
// Included WITHOUT a dependencySubstitution, same reasoning as ModernUI-MC above: its "neoforge"
// subproject's own `tasks.jar` already bundles ":common"'s compiled classes/resources directly
// into one self-contained jar (see mods/VoxelMap/neoforge/build.gradle.kts), so there's no
// separate "universal" jarJar step and no library coordinate of ours to substitute -- it's just
// included as a composite build (for its own build/run tasks and IDE integration) and that plain
// jar copied by collectDistributionJars in the root build.gradle.kts, the same way ModernUI-MC's
// universal jar and Sodium/Iris's jars are.
includeBuild("mods/VoxelMap")

// Sound Physics Remastered (mods/sound-physics-remastered, SunsetDN fork, "1.21.1" branch) --
// realistic sound occlusion/reverb/absorption through blocks. Included WITHOUT a
// dependencySubstitution, same reasoning as ModernUI-MC/VoxelMap above: its "neoforge"
// subproject is an Architectury multi-loader module (common/fabric/neoforge/forge, its own
// settings.gradle) built with NeoGradle userdev plus a shaded jar
// (mods/sound-physics-remastered/neoforge/build.gradle's `com.gradleup.shadow` + the shared
// henkelmax/mod-gradle-scripts `mod.gradle` it applies), not this repo's own
// net.neoforged.moddev toolchain -- a plain project substitution would grab whichever jar task
// happens to be the project's default artifact rather than the specific shaded one this repo
// wants, the exact class of bug already documented for Sodium/Iris above. Its `voicechat`
// (Simple Voice Chat) and `cloth_config` mod dependencies are both declared `optional` in its
// own neoforge.mods.toml, so it loads fine standalone without either present -- no conflict with
// this repo having replaced Simple Voice Chat with its own voice/radio system (see AGENTS.md's
// "Architecture: the radio system"); its `implementation "de.maxhenkel.voicechat:voicechat-api"`
// dependency only affects this submodule's OWN isolated build environment (needed to compile
// against SVC's API for its optional integration), not this repo's runtime dependencies, since
// it's pulled in via a plain file dependency below rather than a project dependency.
includeBuild("mods/sound-physics-remastered")

// Distant Horizons (mods/distant-horizons, SunsetDN mirror of upstream jeseibel/distant-horizons
includeBuild("mods/distant-horizons")

// Lithium (mods/lithium, SunsetDN fork, "1.21.1" branch) -- general server/client performance
// optimizations (game logic, not rendering, so it's complementary to Sodium/Iris/Distant Horizons
// above rather than overlapping with them). Included WITHOUT a dependencySubstitution, same
// reasoning as VoxelMap/Sound Physics Remastered above: nothing in this repo declares a
// "maven.modrinth:lithium"/similar coordinate to intercept, and its "neoforge" subproject's own
// `tasks.jar` (mods/lithium/neoforge/build.gradle.kts) already bundles ":common"'s compiled
// classes directly into one self-contained jar with its own explicit
// `destinationDirectory = rootDir.resolve("build").resolve("libs")`, same shape as Sodium's own
// neoforge subproject -- so it's just included as a composite build (for its own build/run tasks
// and IDE integration) and that plain jar copied by collectDistributionJars in the root
// build.gradle.kts. Also brings in its own nested (non-submodule, checked directly into the fork)
// "components/mixin-config-plugin" included build, used only by Lithium's own neoforge subproject
// to auto-generate its mixin config at build time -- no action needed here, it resolves on its own.
includeBuild("mods/lithium")
