# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Codex/ChatGPT, and others) when
working with code in this repository.

## Agent completion markers

When an agent fully implements a file (not a stub/skeleton — a real, working implementation), it
adds a one-line marker comment as the very **first line** of the file, in that language's comment
syntax, naming the agent and a short label for the unit of work, e.g.:

- Kotlin/Java: `// AGENT-DONE(claude): fsr2-scaffold`
- Properties/`.txt`/shader source: `# AGENT-DONE(claude): fsr2-scaffold` (or `//` if the format
  uses C-style comments)

The label after the colon should match across every file that's part of the same logical unit of
work, so another agent can grep for it to see the whole set. A file with **no** marker is either
untouched or a deliberate stub (signatures/structure only, `TODO()` bodies) left for another agent
to finish — treat the absence of a marker as "needs work," not "nobody looked at this." An agent
picking up work should grep for existing `AGENT-DONE` markers first and skip those files rather
than redoing them, unless the user explicitly asks for a revision.

## Overview

DNCity is a NeoForge (Minecraft 1.21.1) mod written in Kotlin, using Kotlin for Forge. The
current feature under active development is voice chat (item, tuning UI, networking, and the
domain model for modes/bands/frequencies), replacing a previously-removed Simple Voice Chat
integration with two tiers: close-range (Opus, full-duplex, no PTT) and radio (codec2, PTT,
half-duplex, frequency/range-gated — see "Architecture: the radio system"). Two other mods
(TACZ, First Aid) are vendored as plain source folders (composite builds) the project depends on; TACZ (a gun
mod) plays its weapon sounds through DNCity's own bundled FMOD Studio banks
(`src/main/resources/fmod/*.bank`) rather than TACZ's normal vanilla-sound-engine OGG files — see
"Composite builds / vendored mods" below. Beyond TACZ/First Aid, a growing pile of other mods
(Automobility, SuperbWarfare, ModernUI-MC, Sodium, Iris, VoxelMap, Sound Physics Remastered,
Distant Horizons) are vendored the same way, mostly as gameplay/rendering content rather than
things DNCity's own code calls into — see "Composite builds / vendored mods" below. Native modules
exist under `engine/`: `engine/audio` (miniaudio capture/playback + the codec2 JNI bridge),
`engine/fmod` (hand-written JNI FMOD Studio bindings), `engine/window` (Win32 child-window/JCEF
overlay bridge), and `engine/browserhost` (a standalone JCEF host process, not JNI) are all wired
into the main build.

## Build & run commands

Use the Gradle wrapper (`./gradlew` on bash, `gradlew.bat` on cmd/PowerShell — PowerShell can
also invoke `./gradlew`).

- `./gradlew build` — compile and build the mod jar.
- `./gradlew runClient` — launch a dev client (`run/` game directory).
- `./gradlew runClient2` — launch a second dev client with a distinct username/UUID and its
  own game directory (`run-client2/`), for testing multiplayer-only features (radio,
  proximity chat) by connecting two clients to the same world/LAN game at once.
- `./gradlew runServer` — launch a dev dedicated server (`--nogui`).
- `./gradlew runGameTestServer` — launches `GameTestServer` and runs all registered gametests,
  then exits (namespace filtered via `neoforge.enabledGameTestNamespaces` = `dncity`).
- `./gradlew runData` — data generation; outputs to `src/generated/resources/`, with
  `src/main/resources/` as the existing-data source.
- `generateModMetadata` runs automatically on IDE sync and expands `${...}` placeholders in
  `src/main/templates/META-INF/neoforge.mods.toml` (mod id/version/authors/description, MC and
  Neo version ranges) into `build/generated/sources/modMetadata`. Edit the template, not a
  generated copy.

There is no separate lint/test task beyond `runGameTestServer`; this is a Minecraft mod, so
most verification is done by running a dev client.

## Toolchain

The whole repo (root project, TACZ, First Aid, `engine/audio`,
`engine/fmod`) is pinned to **Java 21** — what Mojang ships to end users for Minecraft 1.21.1,
so it's what players actually have. Don't lower `sourceCompatibility`/`targetCompatibility` in
any of these modules independently; keep them unified.

This was **Java 25** until 2026-08-26: `engine/fmod`'s bindings used to be jextract-generated
`java.lang.foreign` (Panama FFI) code, which needed `SymbolLookup.findOrThrow()` (added in JDK
24) — and `java.lang.foreign` itself only stabilized out of preview in JDK 22 — so the whole
module required JDK 24+ just to launch. TACZ loads `engine/fmod`'s classes directly at runtime
(`FmodWeaponSoundManager`), so that requirement cascaded to the whole repo
(`UnsupportedClassVersionError` on an older JVM otherwise). `engine/fmod` now uses hand-written
JNI instead (see its own entry below), which has no such floor — don't reintroduce a
jextract/FFM-based binding without solving that the same way, or this section (and the Java 21
pin across every module) needs revisiting.

## Composite builds / vendored mods

All of `mods/*` and `engine/audio/vendor/codec2` are **plain files in this repo** (since 2026-11-01 there are no git
submodules and no `.gitmodules`; the old forks' history only survives in a local `.git/modules/` if still present).
Edit them in place and commit them like any other source; there is no `git submodule update` step and no upstream
link to push to.

`mods/TACZ-1.21.1` and `mods/First-Aid-New` are vendored folders included via
`includeBuild(...)` + `dependencySubstitution` in `settings.gradle.kts`, so their own project
graph resolves using *this* project's repositories (see the `flatDir`/jitpack/maven blocks in
`build.gradle.kts` that exist specifically to satisfy TACZ's dependencies). Their versions in
`gradle.properties` (`mods_tacz_version`, `mods_firstaid_version`) are for readability only —
the included build's own version always wins during substitution.

`engine/fmod` and `engine/audio` are both **standalone Gradle projects** (their own
`settings.gradle.kts`), each wired into the root build via `includeBuild` + `dependencySubstitution`
in `settings.gradle.kts` (same pattern TACZ/First Aid use). `engine/audio` is a direct dependency
of the root project (`implementation`/`jarJar` on `engine:audio:1.0`, see `build.gradle.kts`'s
comments for why both). `engine/fmod` is consumed the same way, but by **TACZ**
(`mods/TACZ-1.21.1/build.gradle.kts` depends on `engine:fmod:1.0`), not the root project directly
— TACZ's `com.tacz.guns.client.sound.fmod` package uses it to play weapon sounds from DNCity's
bundled banks (`FmodBankLocator` locates them via `ModList`/`IModFile.findResource`, since TACZ
and DNCity are separate mods/jars — see "Architecture: TACZ weapon sound (FMOD)" below). Both
modules can still be built/used standalone with `./gradlew -p engine/<fmod|audio> <task>` (they
reuse the root project's Gradle wrapper distribution; neither has its own `gradlew`).

- `engine/fmod` is a **hand-written JNI bridge** (`src/main/cpp/jni_fmod.cpp`, over a minimal,
  hand-declared subset of the FMOD Studio C API in `fmod_min.h`) plus a small Java wrapper
  (`src/main/java/com/iwei20/fmod/studio/FMODSystem.java`) — `System`
  (create/initialize/update/loadBankFile/getEvent/setListenerAttributes/loadPlugin), `Bank`
  (loadSampleData/unload), `EventDescription` (getPath/getId/getParameterNames/createInstance),
  `EventInstance` (start/stop/set3DAttributes/setParameterByName/getPlaybackState/release) — and
  `FModLoad` extracts/loads the bundled native FMOD libraries
  (`src/main/resources/<os>/<arch>/`) from the jar, including this module's own JNI glue DLL
  last. Building a bank-backed `FMODSystem` needs `System.InitFlags.AllowMissingPlugins` if the
  bank references a DSP/spatializer plugin (e.g. Resonance Audio) this build doesn't ship —
  otherwise the *whole* bank fails to load with `FMOD_ERR_DSP_NOTFOUND`; confirmed by hand that
  the flag alone is sufficient (no need to `System.loadPlugin` the actual plugin `.dll`).
  **`fmod_min.h`'s declared API surface is intentionally minimal** — only what `FMODSystem`'s
  public API needs, which is itself only what TACZ's `com.tacz.guns.client.sound.fmod` package
  actually calls, not a general-purpose FMOD binding; extending it means hand-declaring more
  functions/structs the same way (see `fmod_min.h`'s file comment for how the current set's
  correctness was verified without the real FMOD headers on hand: cross-checked byte-for-byte
  against this module's old jextract-generated bindings before they were deleted). The Windows
  build uses the MSYS2 UCRT64 CMake/Clang/Ninja toolchain (auto-detected under
  `C:/msys64/ucrt64/bin`) and links directly against the vendored
  `fmod_vc.lib`/`fmodstudio_vc.lib` import libraries. MinGW's C++ and pthread runtimes are linked
  statically, so the jar does not need MSYS2 runtime DLLs. `FModLoad` extracts this module's own
  DLL to a plain `File.createTempFile` location
  in the OS temp root, deliberately *not* into the same subdirectory as `fmod.dll`/`fmodstudio.dll`
  (unlike those, which must share a directory for Windows to resolve fmodstudio's dependency on
  fmod by name) — on this dev machine, an unsigned freshly-built DLL extracted into a *subdirectory*
  of the temp root got silently blocked by the machine's Application Control policy
  (AppLocker/WDAC), while a temp-root-level file (matching `engine/audio`'s `NativeLibrary`
  extraction pattern) did not.
  <br><br>**Why hand-written JNI, not jextract-generated `java.lang.foreign` bindings** (this
  module's approach until 2026-08-26 — the deleted bindings lived under
  `src/generated/java`/`com.iwei20.fmod.gen.*`, regenerated on Windows because upstream's
  checked-in bindings hardcode `long` as 8 bytes (LP64), which breaks on Windows where `long` is
  4 bytes (LLP64)): that approach needed JDK 24+ (see the "Toolchain" section above) —
  incompatible with players' actual Java 21. `FMODSystem`'s public API was kept identical on
  purpose, so switching needed zero changes in TACZ's `com.tacz.guns.client.sound.fmod` package.
- `engine/audio` is a JNI bridge over two things, both hand-written JNI (not jextract-generated
  FFI like `engine/fmod`) sharing one native library (`dncity_audio.{dll,so,dylib}`, built from
  `engine/audio/src/main/cpp` via `engine/audio/CMakeLists.txt`):
  - [miniaudio](https://miniaud.io) (vendored single-header at `engine/audio/vendor/miniaudio.h`,
    public domain/MIT-0 — see `vendor/README.md`) for local mic capture/speaker playback. Fixed
    mono/16-bit/48kHz PCM moved through lock-free ring buffers (`ma_pcm_rb`, see
    `jni_audio.cpp`), so the realtime audio device thread never attaches to the JVM. A native
    noise gate/VAD runs on captured samples before they hit the ring buffer (attack/release
    envelope follower with hysteresis) — see `NativeAudio.java`'s
    `setNoiseGateThresholdDb`/`isVoiceActive`/`getInputLevelDb`.
  - [codec2](https://github.com/drowe67/codec2) (vendored as plain files at
    `engine/audio/vendor/codec2`, LGPL-2.1) for the radio-voice tier's actual codec (MODE_2400/
    MODE_1200 — see `Codec2.java` and the mod's `voice/Codec2Codec.kt`). Handle-based JNI
    (`jni_codec2.cpp`): one `create`/`destroy` pair per stream, `encode`/`decode` per frame.
  - Both classes' `System.load` goes through the shared `NativeLibrary.java` (package-private):
    the built library is a classpath resource under `natives/<os>-<arch>/` (staged by
    `build.gradle.kts`'s `buildNativeAudio` task), which plain `System.loadLibrary` can't see
    (it only searches `java.library.path`) — `NativeLibrary.load()` extracts it to a temp file
    and `System.load`s the absolute path instead, idempotently so either class's static
    initializer can trigger it first.
  - **Build toolchain: MSYS2 UCRT64 CMake/Clang/Ninja (MinGW-w64), not MSVC** — codec2's
    sources use real C99 `_Complex` arithmetic (`fsk.c`, `fmfsk.c`, `ofdm.c`, ...), which clang
    cannot provide against the MSVC ABI (MSVC's own CRT has never supported `_Complex`, and
    clang's MSVC-target `<complex.h>` is a no-op there — confirmed by hand: `complex float`
    fails to parse at all under `--target=x86_64-pc-windows-msvc`). mingw's libc genuinely
    supports it, so the whole module (not just codec2) builds against mingw instead; see
    `CMakeLists.txt`'s and `build.gradle.kts`'s comments for the exact flags. Gradle auto-detects
    a standard `C:/msys64/ucrt64` installation and invokes its Windows-native `cmake.exe`,
    `clang.exe`, `clang++.exe`, and `ninja.exe` by absolute path, so neither an MSYS shell nor a
    PATH change is required. `jni_audio.cpp`'s cross-thread noise-gate fields use `__atomic_load`/
    `__atomic_store` compiler builtins on plain fields rather than `std::atomic`; the builtins
    keep that cross-thread state independent of C++ standard-library headers. `./gradlew -p
    engine/audio build` (or the root
    build, which depends on it) has been verified working end-to-end on this dev machine,
    including a smoke test that loads the built DLL, calls `NativeAudio.init()`, and round-trips
    a codec2 encode/decode.

Large binary audio assets (`*.bank`) are tracked via Git LFS (see `.gitattributes`).

Beyond TACZ/First Aid/the `engine/*` modules above, `settings.gradle.kts` vendors a growing list
of other mods the same composite-build way, each with its own detailed comment there explaining
*why* it's wired the way it is (jar variant to use, whether a `dependencySubstitution` is safe,
version-pinning reasons) — read that file directly rather than this one for the full story on any
of these; only the load-bearing gotchas are repeated here:

- **Automobility**, **SuperbWarfare** — gameplay content mods, substituted normally (their
  `neoforge`/root project's own jar is the real one).
- **ModernUI-MC**, **VoxelMap**, **Sound Physics Remastered** — included *without* a
  `dependencySubstitution` (each builds with a different toolchain than this repo's own, so a
  plain project substitution would grab the wrong jar variant) and consumed instead as a plain
  file dependency on their specific built jar, kept up to date by an explicit task dependency
  (`build.gradle.kts`'s `listOf("compileJava", "compileKotlin", "runClient", ...).forEach { ... }`
  block). ModernUI-MC backs the phone screen's Fragment/View host
  (`client/phone/PhoneFragment`); VoxelMap backs the phone's Map app.
- **Sodium**, **Iris** — both **pinned to old commits, not their branch tips** (SunsetDN forks
  `mods/sodium` @ tag `mc1.21.1-0.6.13-neoforge`, `mods/Iris` @ commit `b4b2c22c1`), because
  TACZ/SuperbWarfare's own compat mixins target that exact Sodium API generation and the branch
  tips moved to an incompatible Sodium 0.8 rewrite — see `settings.gradle.kts`'s long comment
  above these two `includeBuild` calls before ever bumping either one.
- **Distant Horizons** (`mods/distant-horizons`, far-terrain LOD rendering) — unlike the other
  vendored mods above, this started as a straight mirror of upstream (`SunsetDN/distant-horizons`, itself just
  a fork of `jeseibel/distant-horizons`), tracking the actively-maintained `main` branch rather
  than a pre-flattened single-version branch, because no such branch exists here. Upstream is one
  multiversion Gradle project selected at configure time by the `mcVer` project property (see
  `mods/distant-horizons/settings.gradle`'s `loadVersionProperties()`, reading
  `versionProperties/<version>.properties`) — **pinned to `mcVer=1.21.1` directly in
  `mods/distant-horizons/gradle.properties`** (not passable through DNCity's own composite build
  config) and to `builds_for=neoforge` only in `versionProperties/1.21.1.properties` (upstream
  defaults to `fabric,neoforge`; the fabric loader there needs fabric-loom 1.10, which requires
  Gradle 8.12+, one minor version ahead of this repo's Gradle 8.11 — dropping it avoids bumping
  the whole repo's Gradle version for a loader DNCity never uses). DH's own core logic lives in a
  **second vendored project** nested inside it (`mods/distant-horizons/coreSubProjects`, LGPL). Upstream
  hosts it on GitLab (`gitlab.com/jeseibel/distant-horizons-core`), but the patched copy DNCity
  pins was taken from the same GitHub fork, branch `codex/dncity-core-1.21.1`, and is now
  plain files under `mods/distant-horizons/coreSubProjects`. The vendored commit is pinned to a specific SHA from *before* upstream's own
  Gradle-9 migration (composite builds run every included build under the root's Gradle version,
  8.11 here, and a newer DH commit's `buildSrc` calls a Gradle-9-only API) — don't blindly replace
  these files with a newer upstream commit without re-verifying the whole repo still builds under Gradle
  8.11. A few local patches on top of that pinned commit (in both `mods/distant-horizons` and its
  nested `coreSubProjects`) fix build breakage that's otherwise unrelated to DNCity specifically:
  a dead `maven.vram.io` repo (swapped for the real `maven.su5ed.dev`, needed to resolve
  `org.sinytra.forgified-fabric-api` once DNCity's own Sodium/Iris `dependencySubstitution`
  redirects DH's `maven.modrinth:iris:...` request to the local `mods/Iris` build), a nonexistent
  fossil dependency `com.google.common:google-collect:0.5` (dropped; superseded by the
  `com.google.guava:guava` dependency declared right next to it), and a duplicate-condition
  preprocessor bug in `common/.../ChunkFileReader.java` (two `#elif MC_VER <= MC_1_21_3` branches
  with the same condition — the first, wrong one always shadowed the corrected second one,
  breaking the `ChunkStatus` import for MC 1.21.1 specifically). Included in the composite
  *without* a `dependencySubstitution` (same reasoning as ModernUI-MC/VoxelMap/Sound Physics
  Remastered above — its `neoforge` subproject's `shadowJar` produces a `-all.jar` alongside
  plain/dev/sources variants), consumed as a plain file dependency on its **`remapJar`** output (the
  unclassified jar: the shaded `-all.jar` plus `META-INF/accesstransformer.cfg` converted from DH's access widener; the `-all.jar`
  alone makes DH's `MixinOptionsScreen` crash with `IllegalAccessError` on `HeaderAndFooterLayout.headerFrame`) and kept
  built via the same task-dependency pattern. Iris shader-pack compatibility: DH's own
  `RenderBufferHandler.buildRenderList` (in `coreSubProjects/core`) already detects
  `IrisApi.isRenderingShadowPass()`, but upstream always submits LOD geometry to the shadow pass
  when a shader pack is active — DNCity added a new config entry,
  `Config.Client.Advanced.Graphics.Culling.renderLodsInShadowPass` (default `false`, not present
  upstream), that skips the shadow pass entirely while leaving the normal lighting/gbuffer pass
  untouched, so distant terrain is lit correctly under a shader pack without casting/receiving
  shadows. Default performance settings are also tuned down from upstream for this specific pack
  (`Config.java`): `qualityPresetSetting` MEDIUM→LOW, `threadPresetSetting` BALANCED→LOW_IMPACT
  (25% of logical cores instead of 50%, via `ThreadPresetConfigEventHandler`'s shared
  `numberOfThreads` budget — see `util/threading/ThreadPoolUtil.java`/`PriorityTaskPicker.java`),
  and `lodChunkRenderDistanceRadius` 256→128 chunks — because Sodium/Iris/Flywheel/Veil are
  already competing for the same GPU/CPU budget DH's LOD terrain adds on top of. All three remain
  adjustable per-player in DH's own GUI/config file; only the shipped defaults changed.
  CPU (JFR profile 2026-10-28: DH was ~75% of all Java CPU samples, mostly distant world generation): `ChunkWrapper`
  builds its DH heightmaps on first use instead of in the constructor (wrappers are made on the server thread for every
  chunk load, 8 neighbours each, and most never read a heightmap; this was ~20% of the integrated server thread), and
  `threadPriority` defaults to 3 instead of 5 so DH's pools yield to the render/server threads. An existing
  `DistantHorizons.toml` keeps its old `threadPriority = 5`. Profile a dev client unattended with
  `./gradlew runClient -Pdncity.quickPlay="<world>" -Pdncity.jfr=<file.jfr>` (see `build.gradle.kts`).

## Architecture: the radio system

Package root: `io.github.jwyoon1220.dncity`.

- **`item/RadioItem.kt`** + **`item/component/ModDataComponents.kt`** — the radio item and its
  persistent data component (power state, per-slot list, active slot index). `item/ModItems.kt`
  registers the item variants (`ALL_RADIOS`, added to the tools/utilities creative tab in
  `Dncity.kt`).
- **`radio/RadioSlot.kt`**, **`radio/RadioMode.kt`**, **`radio/RadioBand.kt`** — a radio's tuned
  state (frequency in kHz, mode, enabled flag) and the domain model for modes (AM/USB/FM, each
  with cutoff frequencies and a base noise level — for a future audio backend to consume) and
  bands (ITU designations ELF–THF, each with a kHz range, an optional max range in blocks —
  `null` means unlimited/ground-wave — and a tuning tolerance). `RadioBand.fromFrequencyKhz`
  maps a frequency to its band. `radio/RadioActions.kt` holds the server-side mutations
  (`tune`/`setActive`/`setPowered`) shared by the `/radio` command and the network payload
  handlers.
- **`network/RadioPayloads.kt`** + **`network/RadioNetworking.kt`** — client/server payloads and
  registration for radio state changes.
- **`command/RadioCommand.kt`** — `/radio`-style command registration.
- **`client/RadioScreen.kt`** — the client-side GUI opened when using a radio item
  (`RadioItem.screenOpener`, wired in `Dncity.onClientSetup`).
- **Voice** (`voice/` package) — two tiers sharing one client-side mixer/playback pump
  (`VoiceClientLoop.kt`, `VoiceAudioMixer.kt`), both riding on `engine/audio`'s `NativeAudio` for
  local mic capture/speaker playback:
  - **Close-range**: Opus (`OpusCodec.kt`, via the `opus-jni-rust` prebuilt JNI binding), no PTT,
    auto-sent whenever `NativeAudio`'s native VAD gates open. Server fan-out by distance only
    (`VoiceRelay.kt`); client applies distance-based gain (`CloseRangeVoice.kt`,
    `ClientVoiceReceiver.kt`).
  - **Radio**: PTT-gated (`client/ModKeyMappings.kt`'s `RADIO_PTT`, unbound by default),
    half-duplex with close-range (`VoiceClientLoop`'s capture routing). The codec is chosen by
    `RadioMode` (AM/USB/FM on the tuned slot), not by band: AM/FM use Opus (same `OpusCodec` as
    close-range — the "radio" sound for these two comes entirely from receive-side DSP, not a
    lossy transmit codec), USB uses codec2 (`Codec2Codec.kt`, wrapping `engine/audio`'s `Codec2`
    JNI class — the actual low-bitrate digital-voice codec). `RadioTransmitter.kt` branches
    accordingly; for codec2 it also buffers mic audio across its mode-dependent frame size
    (`Resampler.kt`'s `AntiAliasDownsampler`/`upsample` handle the 48kHz↔8kHz conversion codec2
    needs — the downsampler is a real anti-aliasing filter, not a box-average, since that was
    audibly adding its own coloring on top of codec2's inherent low-bitrate character). Server
    fan-out (`RadioRelay.kt`) is authoritative on both the transmitter's held-radio state and
    every listener's — frequency must match within `RadioBand.tuningToleranceKhz` and, for
    line-of-sight bands, distance must be within `RadioBand.maxRangeBlocks` (VHF-and-up; tuned
    for a ~7.5km²/7500-block-square map — see `RadioBand.kt`'s doc comment). The relay payload
    carries the transmitter's position directly rather than leaving the client to resolve it via
    `Level.getEntity` — that only works within Minecraft's own (much shorter) entity-tracking
    radius, which silently capped every band's real range below what `maxRangeBlocks`/`null`
    said. Client-side (`ClientRadioReceiver.kt`, `RadioDsp.kt`, `RadioVoice.kt`) applies per-mode
    bandpass filtering and mode-flavored static (AM crackle, FM hiss with a capture-effect noise
    curve, USB warble) before handing decoded audio to the shared mixer.

`Dncity.kt` is the `@Mod` entry point: registers the `ModBlocks`/`ModItems`/
`ModDataComponents` deferred registries on the mod bus, hooks command registration and payload
handlers, and dispatches client- vs. server-only setup via `runForDist`.

## Architecture: TACZ weapon sound (FMOD)

TACZ (`mods/TACZ-1.21.1`) plays its own gun sounds — shoot, reload, bolt, draw, put-away —
through FMOD Studio against DNCity's bundled banks (`src/main/resources/fmod/*.bank`) instead of
its normal vanilla-sound-engine OGG files, entirely in TACZ's own code
(`com.tacz.guns.client.sound.fmod` package), using `engine/fmod`'s `FMODSystem` wrapper:

- **`FmodWeaponSoundManager`** — owns the client-side `FMODSystem` (best-effort init: any
  failure falls back to silence, not vanilla sounds, for the actions it covers), loads
  Master/Master.strings/Weapons banks via `FmodBankLocator` (which locates DNCity's bundled
  resources through `ModList`/`IModFile.findResource`, since TACZ and DNCity are separate
  mods/jars), updates the FMOD listener from the client camera and calls `System::update` once
  per client tick, and exposes `playShoot`/`playReloadTactical`/`playReloadEmpty`/`playBolt`/
  `playDraw`/`playPutAway`/`stopLast`. `playShoot` also sets the `IsOutside` local parameter
  (present on Fire events, confirmed by dumping event parameters) from
  `entity.level().canSeeSky(entity.blockPosition())` when the event defines it, so gunshots
  actually sound different indoors vs. outdoors per the bank's own authored acoustics.
- **`WeaponFmodEvents`** — TACZ-gun-id → bank-event-path maps, one per action
  (`GUN_SHOOT_EVENTS`, `GUN_RELOAD_TACTICAL_EVENTS`, `GUN_RELOAD_EMPTY_EVENTS`, plus
  category-keyed `DRAW_EVENTS`/`HOLSTER_EVENTS`). Every weapon folder in the bank names its
  events inconsistently (compare M4A1's `ReloadNormalMagIn` to MP5's `Reload Start` to AK47's
  `AK102MagIn`), so these maps were built by hand from a dump of every event path in the bank,
  not derived programmatically — extending them for a new gun means dumping that gun's mapped
  weapon folder's events the same way (see `WeaponFmodEvents`'s class doc and
  [[project-tacz-fmod-weapon-sound]] memory for the exact method) rather than guessing a naming
  pattern. Guns with no gun-specific reload event in their folder (verified empty, not just
  unmapped — G36/M24-family/M249-family/SR16-family/M32A1/Uzi) fall back to a single generic
  `Shared/Rifles Shared/Mag In` placeholder shared by all such guns.
- **`FmodBankLocator`** — copies a resource from another loaded mod's `IModFile` out to a real
  temp-directory file, since FMOD's native `loadBankFile` needs an actual filesystem path.

`SoundPlayManager` (TACZ's general sound dispatcher) calls straight into
`FmodWeaponSoundManager` for these actions — no vanilla `SoundEvent` playback happens for them at
all, so there's no double-audio concern to manage when changing which bank event an action maps
to.

## Architecture: `/music` (server-sent playback, no resourcepack)

Package `io.github.jwyoon1220.dncity.music` + `network/MusicPayloads.kt` +
`command/MusicCommand.kt`. `/music play <name>` looks for `<name>.{mid,midi,ogg,flac,mp3,opus}`
in `config/dncity/music/` (in that order) and broadcasts it to every online player as chunked
`MusicAssetChunkPayload`s (`MusicAssetSender`/`MusicClientReceiver`, 250KB/packet — soundfonts and
tracks can be large); `/music stop` broadcasts `MusicStopPayload`. No playlists, no per-player
targeting (explicitly out of scope).

Two independent, format-specific client-side players, chosen by extension:
- **`.mid`/`.midi`** — `MidiPlayer`, `javax.sound.midi`'s public API only (`Synthesizer`/
  `Sequencer`), loaded with a soundfont the server pushes on login (`MusicServerEvents`, cached
  from `config/dncity/soundfont.sf2`) — plays on Java Sound's own default output device, not
  mixed into this mod's `VoiceAudioMixer`.
- **`.ogg`/`.flac`/`.mp3`/`.opus`** — `AudioPlayer`, via `engine/fmod`'s `FMODCoreSystem` (Core
  `FMOD::System`, direct `createSound`/`play` file playback — distinct from the Studio-event API
  `FMODSystem` wraps for TACZ weapon sounds above). A **second, independent** FMOD system
  instance from TACZ's own Studio system — deliberately not shared, so this feature doesn't
  depend on TACZ being installed. `AudioPlayer.tick()` (pumping `FMODCoreSystem.update()`) is
  wired into the same per-client-tick hook as `VoiceClientLoop.tick()` in `Dncity.kt`.

`engine:fmod` is consumed directly by the root project (via `includeBuild("engine/fmod")` in
`settings.gradle.kts` plus `implementation`/`jarJar` in `build.gradle.kts`), not only indirectly
through TACZ's own jarJar as before — needed since `AudioPlayer` must work even without TACZ.

## Architecture: window overlay (native window + JCEF)

Package `io.github.jwyoon1220.dncity.client.window`, backed by a new native module,
`engine/window`. Lets other features put an n×m rendering surface on top of the Minecraft window
as a 2D screen-space overlay — either an embedded `javax.swing.JFrame` (for arbitrary Swing
content, including a JCEF browser view) or a completely empty native window handle for external
native rendering to draw into directly. No in-game feature consumes this yet; `/windowtest` and
`/browser` are debug-only commands for exercising it manually.

- **`engine/window`** — a Win32 JNI bridge (`src/main/cpp/jni_window.cpp`), same composite-build
  shape as `engine/audio`/`engine/fmod` (own `settings.gradle.kts`, own `build.gradle.kts`
  wired into the root via `includeBuild` + `dependencySubstitution` +
  `implementation`/`jarJar`/`additionalRuntimeClasspathConfiguration`, exactly like `engine:audio`).
  **Windows-only today** — like `engine/fmod`, it builds with the MSYS2 UCRT64
  CMake/Clang/Ninja toolchain and links `user32`/`gdi32`; unlike `engine/audio`, it has no codec2
  C99 `_Complex` constraint. `NativeWindowLibrary.java` mirrors `engine/audio`'s `NativeLibrary.java`
  exactly (`natives/<os>-<arch>/` resource layout, `File.createTempFile` straight into the OS
  temp root — not a subdirectory, for the same AppLocker/WDAC-safe-extraction reason already
  documented for `engine/fmod`'s `FModLoad`). `NativeWindow.java`'s native declarations
  (`nCreateChild`/`nDestroy`/`nShow`/`nMove`/`nFindWindowByTitle`/`nReparent`/
  `nGetForegroundWindow`/`nSetRoundRectRgn`) are thin and mechanical, same as `NativeAudio`.
  `nSetRoundRectRgn` clips a window to a rounded-rectangle region via
  `CreateRoundRectRgn`/`SetWindowRgn` (phone-UI-style corners, pixels outside the region show
  whatever is behind the window) — `cornerRadius <= 0` restores the normal rectangle. Note
  `SetWindowRgn` takes ownership of the `HRGN` on success (must not be deleted after), but not on
  failure (deleted there to avoid a GDI object leak) — see the function's own comment.
- **`client/window/WindowOverlay.kt`** — `nCreateChild` (a `WS_CHILD` window parented directly
  to the Minecraft/GLFW window, resolved via LWJGL's `GLFWNativeWin32.glfwGetWin32Window` — no
  native code needed for that lookup) underlies both public entry points:
  - `createFrame(x, y, w, h, title, options) { frame -> ... }` builds a uniquely-titled,
    undecorated `JFrame` off-screen, lets the caller populate it via the callback *before*
    showing it (avoids a flash of empty white), locates its real HWND by that unique title
    (`nFindWindowByTitle` — a `FindWindowW`-by-title trick, not JDK internal `--add-opens`
    reflection), and reparents it into the child window (`nReparent`: strips top-level style
    bits, `SetParent`, resizes to fill).
  - `createNativeHandle(x, y, w, h, title, options)` creates the same kind of child window but
    touches no AWT/Swing at all — `.nativeHandle` is the raw HWND for external native rendering.
  - `setCornerRadius(radius)` applies `nSetRoundRectRgn` using the overlay's current
    `width`/`height`; `move(...)` re-applies it automatically when `cornerRadius > 0`, since the
    region is sized in window-local coordinates and a size change invalidates the old one.
- **`client/window/WindowOverlayManager.kt`** — per-tick lifecycle manager (same
  start/stop/tick shape as `voice/VoiceClientLoop`), wired into `Dncity.onClientSetup`'s
  `ClientTickEvent.Post` hook alongside the voice/music tick calls. Since overlays are real
  `WS_CHILD` windows, they already track the parent's position/z-order for free at the OS level
  — the manager's only job is polling `Minecraft.getInstance().screen`'s type each tick and
  applying each overlay's `OverlayOptions` (`hideOnPauseMenu`/`hideOnInventoryScreen`/
  `hideOnAnyOtherScreen`, same `is PauseScreen`/`is InventoryScreen` pattern as
  `client/ui/MaterialScreens.kt`'s `onScreenOpening`). `destroyAll()` runs on
  `ClientPlayerNetworkEvent.LoggingOut` to avoid dangling HWNDs. Visibility rules are plain
  in-memory constructor parameters, not a persisted config — this mod has no `ModConfigSpec`/
  toml-backed config system at all (see `VoiceSettingsScreen`'s own documented precedent).
- **`client/window/BrowserOverlay.kt`** — the web-page use case: `open(x, y, w, h, url, options)`
  lazily builds one shared `org.cef.CefApp` (`me.friwi:jcefmaven`'s `CefAppBuilder().build()`,
  which downloads/installs a full platform Chromium/JCEF runtime — **~150–300MB, one-time
  network + disk cost on first use** — to `jcef-bundle/`), creates a `CefClient`/`CefBrowser`
  per call, and mounts `browser.uiComponent` into a `createFrame`-hosted `JFrame`. Sets
  `WindowOverlay.extraTeardown` so `destroy()` on the returned overlay also releases the JCEF
  browser/client — any caller that tears the overlay down releases those resources, not just
  `BrowserOverlay` itself. `me.friwi:jcefmaven`'s transitive deps (`commons-compress`/`gson`/
  `commons-lang3`/`commons-codec`) conflict with Minecraft's own strict pins on those same
  libraries — forced onto Minecraft's versions in the root `build.gradle.kts`'s
  `configurations.all { resolutionStrategy.force(...) }` block (same fix already used there for
  `commons-io`).
- **`command/WindowCommand.kt`** — `/windowtest frame`, `/windowtest handle`, and
  `/browser open <url>`, debug-only commands in the same Brigadier style as
  `RadioCommand`/`MusicCommand`, registered from `Dncity.onClientSetup` (not the shared
  `init {}` block those two use) because their bodies touch AWT/native APIs and JCEF that must
  never run on a dedicated server. Every handler hops onto the render thread via
  `Minecraft.getInstance().execute {}` before touching `WindowOverlay`/`BrowserOverlay` --
  Brigadier commands run on the integrated server's own thread even in singleplayer, but a
  native Win32 window is thread-affine (its messages are only pumped by whatever thread's
  message loop created it), and Minecraft's own GLFW message pump runs on the render thread.

Three easy-to-hit gotchas, all fixed:
- **`java.awt.HeadlessException`** — `net.minecraft.client.main.Main` (the real client entry
  point) unconditionally sets `System.setProperty("java.awt.headless", "true")` in a static
  initializer at JVM boot (it renders via GLFW, not AWT), which predates any of this mod's code.
  `WindowOverlay.ensureAwtAvailable()` flips it back to `"false"` -- this only works because
  nothing earlier in this mod calls `GraphicsEnvironment.isHeadless()` (which caches its result
  on first call); if some other code path ends up checking headless-ness first, this stops
  working and needs a different fix (e.g. resetting `GraphicsEnvironment`'s cached field via
  reflection, which needs `--add-opens java.desktop/java.awt` and wasn't needed here). **Must run
  before the *first* AWT-touching call in the whole flow, not just before a `JFrame(...)`** --
  confirmed by hand: JCEF's own `CefBrowserWr` constructor calls
  `MouseInfo.getNumberOfButtons()` internally, which hits the same check, and that happens in
  `BrowserOverlay.open()` *before* it ever calls `WindowOverlay.createFrame`. So
  `BrowserOverlay.open()` calls `ensureAwtAvailable()` itself, before creating its `CefClient`/
  `CefBrowser`, rather than relying on `createFrame`'s own call happening in time.
- **Wrong thread** — see `WindowCommand.kt` above; applies to any future caller of
  `WindowOverlay.createFrame`/`createNativeHandle`, not just the debug commands.
- **Blocking the render thread on the JCEF download** — `CefAppBuilder().build()` is a blocking
  network call on first use (~150-300MB Chromium runtime). `BrowserOverlay.open()` therefore does
  *not* run on the render thread like `WindowCommand`'s other handlers -- it spawns its own
  daemon thread for everything through `createBrowser`, and only hops onto the render thread
  (`Minecraft.getInstance().execute {}`) for the final `WindowOverlay.createFrame` call, which is
  the only part that actually needs it (creating the native window). Confirmed by hand: without
  this split, the whole game freezes for the entire download/install duration.
- **`client/render/OverlayCullingManager.kt`** + **`mixin/MixinEntityRenderDispatcher.java`** —
  an entity-render-skip optimization: whichever overlay is currently visible (`WindowOverlay`'s
  `applyVisible`/`move`/`destroy` push their bounds into this manager's single
  `activeOverlayBounds` `Rect`) is a real, opaque native window sitting on top of Minecraft's own
  rendering, so anything drawn underneath it is wasted work. `shouldCull(x, y, z)` projects a
  world point through `RenderSystem.getProjectionMatrix()`/`getModelViewMatrix()` (camera-relative,
  same convention `LevelRenderer` itself uses) to screen pixels and checks it against that
  rectangle; `MixinEntityRenderDispatcher` injects at the `HEAD` of
  `EntityRenderDispatcher.render(...)` and cancels it when true. Deliberately **entity-only, not
  terrain/blocks**: Minecraft frustum-culls terrain per-chunk, not per-pixel, so a screen-space
  rectangle can't skip chunk geometry submission the way it can skip one entity's `render()` call
  — the real payoff is skipping whatever expensive *fragment* work (an Iris shader pack's
  per-pixel lighting on that entity, say) would otherwise run for pixels about to be covered
  anyway, not terrain draw calls. `shouldCull` is a single-point (entity origin) approximation of
  "behind the overlay," not true per-entity bounding-box coverage. Also doesn't special-case
  Iris's shadow-map render pass (a different camera/projection than the main view) — a shadow
  pass entity shouldn't be culled by a rectangle meant for the player's screen, but nothing here
  currently distinguishes the two passes; worth revisiting if shadows visibly break near an
  overlay.

**Known limitations**: Linux/X11 is not implemented (the JNI surface is isolated enough to add
later, but every `jni_window.cpp` export is currently compiled only under `_WIN32`). There is no
mouse/keyboard focus handoff between an overlay and Minecraft's own raw-mouse-look input capture
— once an overlay (or its embedded `JFrame`) has real OS focus, Minecraft keeps reading the same
input stream underneath it. `nGetForegroundWindow` exists for future use here, but note it can't
actually distinguish an overlay's focus from Minecraft's own once the overlay has been reparented
under Minecraft's top-level HWND (`GetForegroundWindow` only returns top-level windows) — a real
fix needs `WM_SETFOCUS`/`WM_KILLFOCUS` forwarding from the child window, not foreground-window
polling; left as documented future work rather than shipped half-working. The entity-culling
Mixin above has the shadow-pass caveat noted there, and (like the rest of this section) is only
exercised by the debug commands today, not a real consumer feature.

## Architecture: FSR2 temporal upscaling (scaffold only — not functional yet)

AMD FSR2-equivalent temporal upscaling (jittered rendering + history accumulation + real
per-object motion-vector-guided reprojection + upscale; FSR3 frame generation is explicitly out
of scope). **Status: scaffolding only** — every file below is either a signature-only stub
(`TODO()`/`throw new UnsupportedOperationException(...)` bodies) or, where noted, real
pure-data/UI code with no working rendering effect yet. None of it is wired into the actual
render loop. No `AGENT-DONE` marker (see this file's "Agent completion markers" section above)
appears on any of these files — they are all open for an agent to pick up and finish.

- **Sodium needs no chunk-mesh/vertex-format changes.** Sodium only gates block-entity
  visibility, not geometry generation; actual entity/block-entity drawing goes through vanilla's
  dispatchers, which Iris wraps. Terrain motion vectors are fully derivable from depth + Iris's
  existing previous-camera-matrix uniforms (`gbufferPreviousModelView/Projection`) — no Sodium
  data needed. The one open Sodium question (whether its frustum culling reads a jittered or
  unjittered projection matrix) is flagged as a TODO in `mods/Iris/.../mixin/MixinFsr2Jitter.java`,
  not resolved — locate Sodium's actual frustum-build call site before implementing jitter.
- **Iris side** (`mods/Iris/common/src/main/java/net/irisshaders/iris/`):
  - `targets/RenderTargets.java` — `motionVectors`/`motionVectorsFb` fields and
    `getMotionVectorsTexture()` added as `TODO(FSR2)`-marked additions to the real, working file
    (not replaced) — a new engine-owned RG16F G-buffer target for per-object screen-space
    velocity, allocated regardless of what the active shaderpack declares.
  - `uniforms/PreviousObjectTransforms.java` (new file) — per-object (`Entity`/`BlockEntity`,
    identity-keyed) last-frame model-matrix cache; the actual motion-vector math's data source.
  - `uniforms/CapturedRenderingState.java` — real (not stubbed) plain data fields/accessors added
    for the current entity's previous model matrix and this/last frame's jitter offset; nothing
    sets or reads them yet.
  - `uniforms/MatrixUniforms.java` — TODO noting that `gbufferPreviousModelView/Projection`
    already exist as GLSL uniforms (reusable for terrain reprojection) but the actual Java-side
    matrix values aren't exposed outside this file yet; needs a small accessor added.
  - `pipeline/transform/transformer/EntityPatcher.java` — `patchMotionVectors(...)` stub, same
    AST-injection shape as the existing `patchOverlayColor`/`patchEntityId`, for injecting a fixed
    motion-vector fragment output into `gbuffers_entities`/`gbuffers_block`/`gbuffers_hand*`
    programs regardless of shaderpack. Not called from anywhere yet.
  - `mixin/entity_render_context/MixinEntityRenderDispatcher.java` — TODO comment at the existing,
    real injection point describing the previous-transform cache lookup/update that needs to
    happen there; not implemented, to avoid touching this mixin's currently-working logic.
  - `mixin/MixinFsr2Jitter.java` (new file) — sub-pixel jitter injection into
    `GameRenderer.getProjectionMatrix`, via a plain `@Inject`/`CallbackInfoReturnable` (not
    MixinExtras' `@ModifyReturnValue`, unused elsewhere in this fork). **Deliberately not
    registered in Iris's mixin config** — implementing this is the highest-risk single piece of
    this whole feature, since `GameRenderer` is also mixed into by Iris's own
    `MixinGameRenderer`, TACZ's `GameRendererMixin`, and SuperbWarfare's `GameRendererMixin`
    (mixin-ordering conflicts with their FOV/recoil adjustments are a real risk, not
    hypothetical) — see the class's own doc comment.
  - `pipeline/Fsr2PassthroughPipelineLoader.java` (new file) — describes, but does not implement,
    a bundled no-op Iris shaderpack DNCity force-loads when FSR2 is enabled and the user has no
    real shaderpack active (confirmed requirement: FSR2 must work with bare Iris, not only with a
    shaderpack loaded, since Iris's gbuffer/composite pipeline — and therefore all of the above —
    only exists while some pipeline is active).
  - `pipeline/FinalPassRenderer.java` — TODO comment at the real `main.bindWrite(true)` call site
    identifying it as the injection point for DNCity's FSR2 pass driver.
- **DNCity side** (`src/main/kotlin/io/github/jwyoon1220/dncity/client/render/fsr/`, new package):
  - `FsrConfig.kt` — quality-tier enum (`FsrQuality`: Ultra Quality/Quality/Balanced/Performance,
    AMD's own preset scale factors 1.3x/1.5x/1.7x/2.0x) plus enabled/sharpness flags. **Fully
    implemented**, not a stub — pure data, no rendering-API dependency.
  - `FsrSettingsScreen.kt` — options screen, structurally mirroring `VoiceSettingsScreen`. Widget
    layout/state-cycling is real; downstream effects (forcing a resize, invalidating history,
    loading the passthrough pipeline) are marked `TODO(FSR2)` at each call site since the classes
    they'd call are themselves still stubs.
  - `FsrRenderTargets.kt` — persistent ping-ponged history buffer(s) at output resolution; all
    methods stubbed.
  - `FsrPassDriver.kt` — the actual per-frame FSR2 invocation (reconstruction/upscale + sharpen),
    driven by a manually-managed `ShaderInstance` rather than vanilla's `PostChain` (FSR2 mixes
    internal-render-resolution and output-resolution buffers within one pass, which `PostChain`'s
    single-shared-resolution model can't express); stubbed, and not yet called from anywhere.
  - `assets/dncity/shaders/fsr2/` — placeholder `fsr2_reconstruct.{vsh,fsh}` and
    `fsr2_sharpen.{vsh,fsh}` (no real shader math yet, just full-screen-triangle boilerplate and
    a documented uniform contract) plus `FSR2-LICENSE.txt` (real, verbatim AMD MIT license text —
    this port is a hand-restructure from AMD's HLSL FSR2 reference into GLSL, not a file-for-file
    copy; fill in the exact upstream commit ported from once the real port is written).

**Required before this is functional, not yet done**: wiring `EntityPatcher.patchMotionVectors`
into the actual per-shader-stage dispatch path, wiring the `MixinEntityRenderDispatcher`/
`MixinBlockEntityRenderDispatcher` previous-transform-cache calls, registering (and finishing)
`MixinFsr2Jitter` in Iris's mixin config, resolving the open Sodium frustum-culling question,
locating/using the real Iris API for force-selecting a shaderpack (`Fsr2PassthroughPipelineLoader`),
and wiring `FsrPassDriver.runFsr2Pass()` into `FinalPassRenderer.renderFinalPass()`'s injection
point. **Mandatory before merging any of this as real, working code**: verify TACZ's guns
(fire/ADS/hip-fire, FMOD sound) and SuperbWarfare's vehicles still render and behave correctly
with FSR2 enabled, given the shared `GameRenderer`/`EntityRenderDispatcher` mixin targets — this
is the single biggest compatibility risk in the whole feature, not a generic "run the game" check.

## Architecture: health engine (`mods/First-Aid-New`, EFT-style limb health)

The player's real health is the per-limb hit points of First Aid's `PlayerDamageModel` (head 35, body
85, arms 60, legs 65, feet 25 by default — `FirstAidConfig.Server.maxHealth*`). Vanilla
`getHealth()` is a **derived compatibility value**, never the source of truth. Work lives in the
`ichttt.mods.firstaid.common.health` package (marker label `tarkov-health-engine`):

- **`VanillaHealthBridge`** — the only place the two meet. `LivingEntityHealthMixin` routes every
  `setHealth` on a `ServerPlayer` into `onExternalSetHealth`, which turns the requested value into
  limb damage (`DamageDistribution.handleDamageTaken`), limb healing (`HealthDistribution`) or a kill
  (`setHealth(0)`), then re-derives the vanilla value (`PlayerDamageModel.syncVanillaHealth`, which now
  converges on the real vanilla value every server tick). `heal()` goes through `EventHandler.onHeal`.
  Interception is skipped while `connection == null || tickCount <= 0` (NBT load, `ServerPlayer#restoreFrom`
  copies vanilla health *before* attachments exist) and inside `CommonUtils.runWithoutSetHealthInterception`.
  The old `SynchedEntityDataWrapper` hack was dead code (excluded in `build.gradle`) and is deleted.
  HUD: client `vanillaHealthBarMode` defaults to `NORMAL` — vanilla hearts show the limb pool mapped onto 20 (`TOTAL_POOL`:
  sum of limb hp / total limb max x max health); per-limb health is First Aid's own body overlay (`overlayMode` PLAYER_MODEL,
  `overlayPosition` TOP_RIGHT, user 2026-10-26: the readout above the hotbar is gone; `HIDE` still draws it in place of the hearts).
- **`HealthUnits`** — engine units (limb hit points) vs vanilla units (what `hurt()/heal()/setHealth()`
  and the item configs' "half heart" speak). `damageScaleMode=AUTO` (default) maps them linearly
  (`total limb hp / vanilla max health`, 20.5 for the defaults), so vanilla-balanced mods keep working;
  `RAW` applies amounts 1:1 (legacy, only sensible with small limb values). Conversion happens in
  `DamageDistribution.distributeDamageOnParts` (after armor math, which is tuned for vanilla units),
  `EventHandler.onHeal`, `DamageablePart.tick` (heal pulses) and the pain/feedback thresholds in
  `PlayerDamageModel`. `/damagePart` speaks limb hit points.
- **`InjuryEngine`** — bleeds (light/heavy, `AbstractDamageablePart.bleedLevel`, drained once per second
  as a percentage of the limb's max hp, stopped by treatment) and fractures (`fractured`, arms/legs/feet only:
  attribute penalties, reduced while painkillers/morphine are active). Rolled in
  `DamageDistribution.handleDamageTaken` from the before/after limb snapshot; bleed-capable sources are the
  `firstaid:causes_bleeding` damage-type tag plus any `Projectile` direct entity. Bleed deaths use the
  `firstaid:bleed_out` damage type. State is in the part NBT and so rides the existing damage-model sync.
  A non-critical limb that empties passes only `limbOverkillFactor` of the excess on.
- **Medical items** — `ItemTreatment` (an `ItemHealing` with its own `canTreat(part)` and a completion hook):
  bandage/plaster close light bleeds, tourniquet (limbs only) any bleed, splint fractures, trauma kit heals
  quickly and closes every bleed. `ItemHealing#canTreat` replaced the "part is not full" checks (server
  `MessageApplyHealingItem`, `GuiHealthScreen`, `ClientEventHandler`). `/firstaid injury <player> ...` sets
  or clears bleeds/fractures; the HUD marks them `B`/`BB`/`F`.
- **Wound care + Tarkov-level kit** (marker `realistic-wound-care`) — per part: `dressedWound` (a stopped bleed stays
  dressed; a *new hit* on that part reopens it, moving does not; only a suture kit closes it), `tourniquetTicks` (a tourniquet
  stops a limb's bleed at once and starts an ischemia clock: 2 h real (TCCC proposed change 25-2, SOURCED) / 6 h limb loss
  (ESTIMATE), run `medicalTimeCompression` (default 20) times faster, i.e. 6 / 18 game minutes; past the safe time the limb
  drains `ischemiaPercentPerSecond`, past the loss time it is destroyed; a dressing strong enough for the wound converts it
  (bandage/plaster: light, hemostatic gauze/trauma kit: heavy), suture takes it off) and `surgeryPenalty`. A non-critical limb at
  0 hp is *destroyed*: `heal()`, medkits, natural regen and bandages skip it; only `surgical_kit` (-30 % max) / `field_surgical_kit`
  (-15 %) restore it to 1 hp; respawn and an external `setHealth(max)` restore everything. New items: `car_first_aid_kit`, `ifak`,
  `afak`, `field_medkit` (`ItemMedkit`: durability = resource pool in limb hp, bleed/fracture cost paid first, then only the hp the
  part needs; `MessageApplyHealingItem` charges the plan's cost), `cat_tourniquet`, `hemostatic_gauze`, `aluminum_splint`,
  `suture_kit`, `surgical_kit`, `field_surgical_kit`; all counts/costs/times are DESIGN. HUD markers: `X` destroyed, `T<min>` /
  `T!` tourniquet, `W` dressed wound. Icons/models/recipes/lang are generated (no generator script kept in the repo).
- **Verification** — `./gradlew runGameTestServer` inside `mods/First-Aid-New` boots a real headless
  server and runs `gametest/FirstAidGameTests` (not shipped: excluded from the jar in `build.gradle`).
  The mock player is built by hand because `GameTestHelper#makeMockServerPlayerInLevel` has a connection
  without NeoForge's payload registry, and it must be ticked via `doTick()` (vanilla ticks real players
  through the network handler, which is what fires `PlayerTickEvent`).

- **TACZ damage** — TACZ bullets are `Projectile`s but move with their own ray logic (never `Projectile#onHit`) and
  their damage types (`tacz:bullet*`) are not in `minecraft:is_projectile`, so First Aid used to treat every hit as
  random-limb. `EventHandler.isProjectileDamage` now also accepts any source whose direct entity is a `Projectile`;
  the hit context is kept for the whole tick because one shot calls `hurt()` twice (normal + armor-piercing part);
  damage types in `firstaid:precise_hit` (default `#tacz:bullets`) use that location even with friendly random
  distribution on. `compat/TaczFirstAidBridge.kt` (registered from `Dncity` only if both mods are loaded) listens to
  `EntityHurtByGunEvent.Pre` and records the hit: TACZ's headshot flag becomes the player's eye position (head),
  otherwise the bullet position. TACZ damage values are vanilla-scaled and go through `HealthUnits` (AUTO); add
  `#tacz:bullets` to the `firstaid:raw_damage` tag to make a gun's damage value mean limb hit points instead
  (a damage of 50 removes 50 hp from the limb hit). Not compiled in this environment (DNCity's root build needs the
  other vendored mods); the First Aid side is covered by a game test.

- **Shells (every projectile is one Shell)** — `ballistics/` in DNCity (not First Aid). `ShellRegistry.of(entity)` is
  the single place where a projectile of any mod (TACZ bullet, SuperbWarfare cannon shell/rocket/missile/bomb) turns
  into a `Shell`; everything downstream (First Aid's damage, future vehicle damage) asks only that. The catalog is data,
  no enum: `data/<ns>/dncity/shells/**.json` (`ammo` = type name only, `entity`, optional `key`/`nbt`/`item`/`tags`,
  `caliber_mm`, `penetration`, `damage_multiplier`, `damage_is_limb_hp`, bleed/fracture/overkill modifiers, `explosive`).
  A projectile gets its shell in this order: (1) a weapon stamped it when firing (`ShellRegistry.stamp(entity, id)`,
  the intended path for modular tank gun modules); (2) it was decided before (`dncity:shell_id` attachment, saved with the
  entity, so the answer never changes mid-flight); (3) it is matched: `entity` = projectile entity type, `key` must equal the
  key resolver's answer (TACZ: `ammoId` via `TaczShellKeys`; default for any `Projectile`: the entity type of the
  vehicle the shooter is riding, `rootVehicle`), `nbt` must be contained in the entity's saved data
  (`{"Type":"AP"}` for `superbwarfare:cannon_shell`); the most specific match wins and is saved as (2). A weapon
  module asks `ShellRegistry.forItem(item)` / `compatible(caliberMm, tag)` instead of hard-coding ammo.
  Size and power: the wound width is `sqrt(caliber/7.62)` (more bleed chance, heavy bleeds at a smaller share of
  the limb, more of an emptied limb's excess passed on); `penetration` is the share of damage that ignores armor.
  `compat/FirstAidShellBridge.kt` registers a `HitProfiles` provider (First Aid's neutral `api.damage` hook) turning the
  shell into a `HitProfile`. Shipped catalog: TACZ ammo (`shells/tacz`), generic SuperbWarfare projectiles, and one shell per
  (vehicle, ammo type) generated from SuperbWarfare's `sbw/vehicles/*.json` (`shells/superbwarfare/vehicle/<vehicle>/`,
  `key` = the vehicle's entity id, `item` = the loading ammo item). The vehicle calibers (M1A2 120, T-90A/ZTZ-99A 125,
  PLZ-05/FH-77BW 155, ...) are assumptions: SuperbWarfare's data has none. Small autocannon AP/HE variants cannot be told
  apart (same entity, same vehicle) and share one shell. SuperbWarfare explosion damage types use the equal distribution
  and its `*_headshot` types force the head via `data/dncity/firstaid/damage_distributions/`. The Kotlin side is not
  compiled in this environment (the root build needs the other vendored mods); the First Aid hook has game tests.

- **Terminal ballistics (War Thunder-style hits, in progress)** — `ballistics/terminal/`. Target design: every projectile (rifle
  round to tank shell) runs the *same pipeline* with a *different penetrator model*: `ProjectileDefinition -> ProjectileState ->
  ImpactContext -> ArmorStack traversal -> PenetratorModel / ArmorEffectModel -> PenetrationResult -> post-penetration ->
  DamageEvent[]`. de Marre is only one possible implementation (full-caliber AP); long rod, shaped charge, APCR/APDS and small arms are
  separate models chosen by `ProjectileDefinition.terminalModel`. **No real penetration formula exists yet** (next step).
  - *Plain JVM core.* The package uses `Ident` and `V3`, never Minecraft types, so it and its tests (`src/test`, `./gradlew test`)
    run without the game. Only `JsonDataRegistry.kt` and `TerminalBallistics.kt` (reload listeners, id conversion) touch Minecraft.
    Parsing is in `fromJson` companions; cross references are checked by `BallisticsValidation`; registries are strict (one broken file
    fails the reload, `-Ddncity.ballistics.lenient=true` skips it in development).
  - *Rules.* SI units inside (JSON may say `_mm`/`_g`, `SiJson` converts). An impact angle is from the surface normal (0 = perpendicular)
    and is always *derived* from the current direction and the local `SurfaceModel` normal, never stored on armor. Materials hold
    physical data only; how well one stops a penetrator is a `ResistancePreset` (solver id + solver-specific parameters) chosen by the
    projectile's terminal model, not a `ke_eff`/`ce_eff` constant. `ArmorMaterial` != `ArmorLayer` != `ArmorConstruction`: a layer is a
    material with thickness and shape (`FLAT`/`CURVED`; manufacturing such as cast/rolled is a separate, not yet modelled axis); a
    construction is the *definition* (Solid, Gap with a real distance, EffectPackage = layers + effect, InternalSpace last);
    ERA/NERA are an `EffectPackage`, never a material. What has been used up on one vehicle is `ArmorRuntimeState` (per vehicle,
    per `EffectSlot`), the traversal only reads it and reports `consumedEffects`; the server `commit`s an accepted result.
  - *Traversal* (`ArmorTraverser`): carries a `ProjectileState` (the physical state: worn rod, speed, axis/yaw, fuze machine) through
    a `TraversalState` (this passage: accumulated flown distance, deposited energy, spall/blast sources, consumed effects, phase).
    Ends with a `TraversalOutcome`: STOPPED_IN_ARMOR, RICOCHETED, SHATTERED, DEFEATED_BY_EFFECT, EXITED_CONSTRUCTION or
    ENTERED_INTERNAL_SPACE (after which residual tracing, spall, modules, crew and fuze are the post-penetration solver's job).
    Gap path = distance / cos(angle) (a gap is air; there is no fill, a medium model would be separate) and counts towards the
    traversal distance and the fuze arming distance. Contracts the traverser enforces on every model: the traversal owns canonical
    state (after a perforation the residual is placed at `perforationPoint`, the path must be finite and > 0; `exitDirection` is derived
    from `residual.velocity`); `ProjectileState.axis` is a unit vector and `deformation` is 0..1; no energy from nowhere
    (`EnergyBudget`: a penetrator model may only use the projectile's energy, an effect may also release its
    `ArmorEffectSpec.storedEnergyJ`, and only in the interaction in which it uses itself up; blasts are TNT mass converted by
    `EnergyAccounting.blastJ`; passive armor has no stored energy); only a `singleUse` effect may report consumption, once; an
    effect cannot end, alter or revive a projectile a layer already ended. An effect says what it did to the projectile with
    `ProjectileChange` (Untouched / Replaced / Destroyed; only Destroyed ends the traversal, as DEFEATED_BY_EFFECT, so "null" never
    means two things). `ArmorEffectSpec.storedEnergyJ` is a *provisional accounting upper bound* (the most a conservation check
    lets that one interaction add), not a physical ERA description and not an amount delivered; a real ERA model will derive it from
    explosive mass and distribute at most that. What a layer absorbed (`depositedEnergyJ`) is NOT available to effects: an observer
    has no mechanical energy except its own stored energy, until layers report an energy breakdown (plastic work, heat, fracture,
    plate motion, spall). Effect hooks: `EffectPhase` BEFORE_PACKAGE, per layer BEFORE_LAYER / (penetration) / AFTER_LAYER (called for
    every solved outcome; observation-only after a layer that did not perforate, the layer's verdict outranks it) and AFTER_PACKAGE
    (only if every layer was perforated); a model is only called for the phases it lists. Once a single-use effect has used itself up,
    its later hooks do not run in that traversal (`EFFECT_SKIPPED_CONSUMED_THIS_TRAVERSAL`) but the layers of the package are still
    solved. Runtime effect state is just INTACT/SPENT. **The traversal API is frozen** until the first real solver exposes a need.
  - *Determinism.* Same `ImpactContext` + `ProjectileState` + runtime state + seed = same result. Solvers must not use `Random`, a clock
    or entity/world state; anything stochastic (spall pattern, fuze failure) draws from `ImpactContext.seed`
    (`ImpactContext.seedFor(shotSeed, constructionId, elementIndex, layerIndex, SeedDomain)` hashes *what* a stream is for, not call order, so adding a layer, phase or domain never shifts another stream).
  - *Server/client.* The server is authoritative for impact and penetration; the shooter client only sends a `ShotClaim` (inputs, never
    results) and later offloads the heavy spall/BVH ray work at the server's request. Not built yet.
  - *Next step (proposal only, not implemented):* `docs/ballistics/full-caliber-ap-proposal.md` (v2) built on `docs/ballistics/full-caliber-ap-sources.md` (what was actually verified, and what was not) and `docs/ballistics/data/` (MIL-DTL-12560K tables + `analyze_12560k.py`). No solver code or calibration numbers exist until it is approved; never cite a number from a search-engine summary, read the source.
  - *Data.* `data/<ns>/dncity/{projectiles,armor_materials,resistance_presets,armor_effects,armor_constructions}`. Shipped: five
    materials (physical constants) and three empty baseline RHA presets. **No projectile, effect or construction data is shipped and
    none may be invented**: test numbers live only in `src/test` (`Fixtures.kt`).
  - Known gaps: no solver, fuze trigger on armor contact, curved-surface `SurfaceModel`, post-penetration, `ShotClaim`/server validation,
    `Shell` -> `ProjectileDefinition` link. The root Gradle build and `./gradlew test` could not be run in the authoring environment; the
    package was compiled with the Kotlin compiler from Gradle's distribution (pure core with no Minecraft classes on the classpath, the
    Minecraft boundary against stubs) and the tests were run that way.
- **Ammo data (War Thunder BLK-style, marker `ammo-data-system`)** — `ballistics/terminal/ammo/`, JSON in `data/<ns>/dncity/ammo/(any depth)/name.json`
  (schema v1, unknown keys are errors). An `AmmoDefinition` is the catalog entry (item/display, compatibility, projectile constants, penetration
  spec, terminal solver id, provenance); `AmmoCompiler` turns it into the frozen `ProjectileDefinition` (the penetration block is NOT part of
  it; `terminal.solver` becomes `terminalModel`). `AmmoType` (AP/APCBC/APHE/APCR/APDS/APFSDS/HE/HEAT) selects the required fields in
  `AmmoValidation.checkStructure` (gun caliber, body diameter and penetrator diameter are three separate numbers).
  `penetration.mode`: **TABLE** (limit thickness by distance x angle, bilinear, `angle_convention` must be `FROM_ARMOR_NORMAL`), **PHYSICS**
  (the `AmmoSolver` named by `terminal.solver`), **HYBRID** (table decides, physics cross-checks, every disagreement is a diagnostic;
  `on_conflict` USE_TABLE|FAIL). `AmmoImpactResolver.resolve` returns `Resolved` or an ordinary-value `Unresolved` (never throws, never
  substitutes another solver): unimplemented solver, material other than the table's `reference_material` (no RHA-equivalence conversion),
  out-of-range (only the author's `out_of_range` CLAMP / EXTRAPOLATE-with-margins changes that), a worn/deformed projectile. A table gives a
  limit only: a table perforation has no invented residual (`result == null`). Without a flight distance the table is read at the distance
  implied by the impact speed and the ammo's own drag (`ExternalBallistics`, flagged). Not a `PenetratorModel` on purpose (same reason as
  `FullCaliberApModel`: that contract cannot decline). Minecraft side: `AmmoRegistry` (reload listener, after `TerminalBallistics`),
  `AmmoItem` (ONE registered item, ammo id in the `ammo_id` data component, name/stack size stamped on the stack: item registration and data
  reload are separate lifecycles), `/ammo list|info|give|test` (`test` runs the real resolver). `-Ddncity.ballistics.production=true`
  refuses tables without criterion/reference material/spec/source or marked FICTIONAL. **No ammo data is shipped**; the fixtures in
  `src/test/resources/ammo` are FICTIONAL structure examples. Not yet built: `FULL_CALIBER_CAPPED_AP`, long-rod and shaped-charge solvers
  (ammo naming them loads and answers UNRESOLVED), ricochet/shatter/spall profile evaluation (stored only), `Shell` -> ammo link.
- **Flight + oblique AP (marker `external-ballistics-si`, `oblique-full-caliber-ap`)** — `ballistics/flight/`: SI point-mass flight
  (`FlightIntegrator`, RK4, quadratic drag + gravity, ISA troposphere `Atmosphere`, `DragModel` Constant or sourced Tabulated Cd(Mach); a Mach or
  altitude outside the model stops the flight as `OutOfModel`, nothing is extrapolated; no wind/Coriolis/spin drift). `MinecraftUnits` is the only
  place for 1 block = 1 m, 1 tick = 0.05 s. Not yet wired into `ExternalBallistics`/`AmmoImpactResolver` or the TACZ/SW bullets (TACZ keeps
  per-tick `1-friction` and per-tick gravity in blocks/tick: plan is to convert to SI at the bridge, not replace its flight). `FullCaliberApPreset`
  has an optional `obliquity` block (`GEOMETRIC_LOS` = pure line-of-sight, flagged optimistic; `COSINE_POWER` needs a named exponent source);
  the angle is a second axis with the author's `max_angle_deg` + margin, beyond which the model declines (no ricochet/shatter model).
  `FullCaliberApPresetRegistry` loads `data/<ns>/dncity/full_caliber_ap_presets`; none are shipped.
- **Slope tables + ricochet (marker `slope-ricochet-tables`)** — two optional preset blocks for `FullCaliberApModel`, structured after
  how War Thunder's damage model organizes the same problem (per-projectile `slopeEffect`/`ricochet` presets; a slope table keyed by
  plate thickness/caliber and angle; ricochet as a probability over angle checked before penetration). Only that *structure* was
  taken (static reading of the game binary's config keys); no WT number, table or code is in this repo, and none may be added.
  `obliquity.basis = TABULATED` + `SlopeTable`: `T_eff = T * f(theta, T/d)`, bilinear, `f(0) = 1`, `f >= 1`, non-decreasing in angle,
  >= 2 rows, no extrapolation in either axis, needs `table_source`. `ricochet` (`RicochetSpec`): probability table from 0 degrees,
  non-decreasing; probability 1 answers RICOCHET even beyond the obliquity range (`RICOCHET_CERTAIN`); 0 < p < 1 is drawn from
  `ImpactContext.seed` (`RICOCHET_DRAWN`, own SplitMix stream); beyond the last angle the model declines. A ricochet is specular with
  one sourced `speed_retention`; `ModelEvaluation.Resolved.ballisticLimitMps` is null for it. Not done: projectile shatter
  (WT's `breakingCriticalSpeed`/`breakingArmorThickness`), armor-side quality/ricochet modifiers, the filler-mass correction for APHE.
- **Small-arms penetration (TACZ bullets, marker `small-arms-penetration`)** — `ballistics/smallarms/` (plain JVM core +
  `SmallArmsData` reload listener + `BallisticBlockCategories`) and `compat/TaczSmallArmsBridge.kt`. Separate from the
  `terminal/` traversal on purpose (it is a first-order energy-per-area model, not a penetrator model). One idea throughout: `E/A`.
  *Blocks*: category from tags `dncity:ballistics/<glass|foliage|wood|soil|stone|metal>` then sound type, else UNKNOWN (stops).
  RESIST materials lose `R * A * path` over the bullet's path through the collision shape; `R` is calibrated from one depth each in
  FM 3-06.11 Table 7-3/7-5 (7.62 mm ball, 25 m; reference round TM 43-0001-27 M80, 149 gr, 2750 fps at 78 ft) in
  `data/dncity/dncity/small_arms/block_materials.json`. GLASS/FOLIAGE pass losslessly (a glass block is a window). *Body armor*:
  `body_armor.json` — NIJ 0101.06 section 2 test threats (sourced) define each level's threshold (max `E/A` of its threats);
  vanilla armor material -> level, enchantment bonus (Protection 0.04, Projectile Protection 0.08 per level) and
  `blunt_damage_fraction` are **design**. Aramid treated as a weak NERA: absorbs `threshold * multiplier * A`, stops below it,
  passes the rest above it. Hit slot: headshot -> HEAD, else hit height (First Aid's 0.15/0.45/0.8 boxes). Damage = TACZ damage x
  block energy fraction x (armor energy fraction, or blunt fraction if stopped); then both TACZ damage parts are dealt as the
  armor-piercing source so vanilla/First Aid armor does not reduce the bullet again; the armor piece loses 1 durability.
  *Armor wear* (marker `armor-wear-suppression`): each ballistic piece carries a `dncity:ballistic_armor` data
  component (integrity 0..1 + hit count, stamped fresh on equip by `LivingEquipmentChangeEvent` so the client knows which pieces
  count). Integrity scales the absorbed `E/A`; each hit removes `wear.integrity_loss_at_threshold` (0.2) times the bullet's `E/A`
  over the piece's undamaged threshold, and `wear.max_hits` (5) hits zero it (`BodyArmorTable.wornIntegrity`; both values are
  design). The HUD draws a red 10-icon row above vanilla's armor row (`client/render/BallisticArmorBar`, head 4/chest 8/legs 6/
  feet 2 half-icons times integrity). Suppression is First Aid's own: `compat/TaczFirstAidBridge` calls
  `PlayerDamageModel.registerAdrenalineNearMiss` at its maximum strength on every TACZ hit on a player (DNCity has no effect of its own).
  *Bullet data*: `small_arms` block in each `shells/tacz/*.json` (mass/diameter/velocity in the source's own units, `source`,
  `source_kind`: MILITARY_SPEC/STANDARD/MANUFACTURER/RETAILER_LISTING/ENCYCLOPEDIA/DERIVED — several are retailer or Wikipedia
  figures, see each file). Real speed = sourced velocity x (TACZ current speed / initial speed). No data -> TACZ default.
  *TACZ fork changes* (`mods/TACZ-1.21.1`): `AmmoHitBlockEvent#setPenetrated(exit, speedFactor)`; `EntityKineticBullet` traces a
  tick in segments (resumes after a penetrated block, `MAX_PENETRATION_SEGMENTS` = 16), defers the speed loss to the end of the
  tick (TACZ's entity search box is spanned by the velocity), ends the tick at the traced end point, and exposes
  `getInitialSpeed()`/`getCurrentSpeed()`. Known limits: no velocity dependence/yaw/break-up, AP cores = ball, non-convex shapes
  (stairs) count their inner air gap as material, client-side tracers do not slow down. Unit tested (`smallarms/SmallArmsTest.kt`);
  **not yet played in a client**.
- **Kotlin gotcha**: block comments nest. A KDoc containing `dir/*.json` or `dir/**.json` opens a nested comment and the file
  fails with "Unclosed comment". Never write a slash followed by a star inside a comment (write `dir/(any depth)/name.json`).

Known limits: the body keeps First Aid's 8 parts (feet exist, there is no separate stomach), so this is
EFT-*style*, not a 7-zone copy. Absolute gun/mob damage values are the modpack's to tune (AUTO scaling only
keeps vanilla-balanced numbers proportional). Verified on a headless server only — nothing here has been
played in a real client (HUD markers, item models, pending-heal UI are unchecked visually).

## Architecture: cartridge items (markers `cartridge-items`, `ammo-round-variants`)

One item per *round* (`dncity:cartridge_<caliber>_<round>`, e.g. `cartridge_556x45_m855`), listed in their own
"Ammunition"/"탄약" creative tab. Source of truth is `src/main/resources/dncity/cartridges.json` (classpath, read at item
registration by `item/cartridge/CartridgeCatalog.kt`; items cannot follow a data-pack reload). Names: English
`<caliber> <US designation> <type>` (designation only where a US military one exists, e.g. `5.56x45 M995 AP`, otherwise
`9x19 RIP`), Korean `<caliber> <type>` (`5.56mm 철갑탄`); every caliber has at least one tracer (`TRACER`, `API_T`, `APHEI_T`;
enforced by the parser).
**After editing the catalog run `python tools/cartridges/generate_cartridge_assets.py`**: it draws the 64x64 icons from
the catalog's dimensions on one shared mm-to-pixel scale (so a 9x19 is drawn at its real size next to a .50 BMG; the
dimensions are cosmetic, not ballistic data), writes the item models and merges the names into `en_us.json`/`ko_kr.json`.
`CartridgeCatalogTest` fails if the generated assets are out of date. 40mm grenades and the RPG rocket have no rounds.

TACZ side (`mods/TACZ-1.21.1`): `compat/TaczCartridgeItem` implements TACZ's `IAmmo` (ammo id = the caliber's TACZ ammo,
so every gun of that caliber loads it) and the new `api/item/IAmmoRound` (tracer flag). TACZ only stores a round
*count*, so `api/item/nbt/LoadedRounds` keeps a parallel, run-length-encoded list of which rounds are in the gun (chamber
first) in the gun's custom data. `AbstractGunItem.findAndExtractInventoryAmmo` takes rounds one at a time in a
4-other : 1-tracer cycle (`RoundFeedCycle`, persisted on the gun) when both kinds are in the inventory, trimming the list
to what is really in the gun first; `ModernKineticGunScriptAPI.shootOnce` pops one round per shot and uses its tracer
flag instead of the gun's `tracer_count_interval` (shotguns: first pellet only); unknown rounds (TACZ's own ammo, ammo
boxes, creative fills) keep TACZ's default. `dropAllAmmo` gives the recorded items back. `EntityKineticBullet.getRoundId()`
carries the fired round's item id for future per-round ballistics; **the shell/small-arms data is still per caliber**, so
AP/HP/FMJ of one caliber currently hit the same (no per-round sourced data yet). Rounds cannot go into TACZ ammo boxes
(a box only stores a count). The TACZ HUD no longer shows magazine/reserve counts and no longer tints the gun icon red
when empty (overheat still does). Not yet played in a client.

## Architecture: vehicle armor penetration + hit camera (marker `vehicle-armor-penetration`)

War Thunder-style outcome (penetration / non-penetration / ricochet) for SuperbWarfare vehicles, plus a hit-camera HUD for
the shooter. Deliberately a first-order line-of-sight model in `ballistics/vehicle/` (plain JVM, `VehicleArmorTest`),
separate from the `terminal/` traversal pipeline, which still has no solver data.

- **Data.** `data/<ns>/dncity/vehicle_armor/<vehicle>.json`: zones (`hull`, `turret`, `tracks`, any other name) with one
  `{ke, ce}` mm RHA-equivalent plate per face (`front/side/rear/top/bottom`, or `all`), as protection against a hit square to
  that face. OBBs map to zones by SuperbWarfare part (`Turret` -> turret, `WheelLeft/Right` -> tracks, the rest hull), or
  explicitly by index in the vehicle's SuperbWarfare `OBB` list (`obb_zones`). What a projectile penetrates is an
  `armor_penetration` block (`kind` KE/HEAT/HE, `mm`, KE: `range_m` + `loss_per_km_mm`, optional `ricochet_deg`) in its shell
  JSON, or per round in `dncity/cartridges.json` for TACZ rounds. **All shipped numbers are community ESTIMATEs (public
  guesses; the real values for modern armor are not public) or DESIGN values**, each with `status`/`kind`, `source` and, for
  estimates, a URL — the user asked for these explicitly (2026-10-16), overriding the "no invented data" rule for this
  feature only. **War Thunder may be used as a reference** (user, 2026-10-26: its values are well balanced): what its official
  wiki publishes, status `WAR_THUNDER` with the URL, used where no public real figure exists (or for a falloff shape); never
  datamined game files, and a real figure wins where there is one. Shipped: M1A2, T-90A, ZTZ-99A, BMP-2, Bradley, LAV-25,
  LAV-AD, LAV-150, PLZ-05, FH-77BW. A vehicle without a file, or a shell
  without `armor_penetration`, keeps SuperbWarfare's damage; a TACZ round without a value cannot get through any plate.
- **Resolution** (`compat/SbwVehicleArmorBridge`, `TaczVehicleArmorBridge`): SuperbWarfare's cancellable
  `ProjectileHitEvent.HitEntity` (every SBW projectile goes through it) and TACZ's `EntityHurtByGunEvent.Pre`. The shot line is
  traced against the vehicle's OBBs (not the collision box); the first box entered gives zone + face, the angle to that face
  gives 1/cos obliquity; KE loses penetration with the shooter's distance. Non-penetration cancels the hit and stops the shell
  (no damage). Ricochet cancels; KE glances off the plate at half speed, HEAT/HE duds. Penetration lets SuperbWarfare's own
  damage run; a vehicle with modules then gets the damage model of `vehicle-damage-model`, one without the old part damage of
  every module on the path inside (`depthBlocks`, DESIGN constants: engine, turret, tracks; "crew" = report only).
- **Hit camera** (`network/VehicleHitPayloads.kt`, `client/render/VehicleHitCamOverlay`): the shooter gets the vehicle's boxes
  in the vehicle's own frame, entry/direction/stop points and the numbers; a 2D panel on the right shows side and top views
  with the shot path (white outside, red inside, yellow ricochet, blue stopped), plate, angle, effective armor vs penetration,
  depth and damaged modules for 5 s. Not yet seen in a client.
- **Soft vehicles** (marker `soft-vehicle-penetration`): `vehicle_armor/soft_*.json` (`"vehicles": [...]` lets one file cover
  several, `"soft": true`) for trucks/pickups, aircraft/helicopters, the Ju 87, the speedboat and the rest (tiny boat, Type 63).
  Sheet metal is 0.5 mm RHA on every face (user's value, 2026-10-25; no sourced RHA figure for car bodies, outer skins are
  0.6-0.8 mm sheet). Engines are 12 mm (DESIGN: stops rifle ball, not .50): the front face of the cars' body box (OBB 5), the
  Ju 87's nose box, the speedboat hull's rear face, and SuperbWarfare's MainEngine/SubEngine boxes, which now map to an `engine`
  zone when a file has one (tank files have none, so their engine boxes stay `hull`).
  On a soft vehicle, a TACZ round without `armor_penetration` gets `ballistics/smallarms/SteelPenetration` (mild-steel depth of
  the `E/A` model's FM 3-06.11 calibration, at its current speed; not converted to RHA): every round goes through sheet metal,
  rifle ball stops in an engine, .50 does not. Never used against armor plate. A vehicle without OBBs (J-16) is traced against
  its collision box as one hull box (no hit cam).
- **Vehicle weapons vs armor**: GAU-8 (A-10, and SuperbWarfare's J-16 gun), 2A42 (Mi-28), AC-130 25/40 mm and 105 mm HE, AH-6
  20 mm, Hydra rockets (as M247 HEDP), Maverick (DESIGN: no published figure), Ataka, Kh-39, bombs, and the pickup/speedboat
  .50 and Ju 87 MG 17 (`vehicle/<v>/projectile_default.json`) now have `armor_penetration`.
- Known gaps: one plate value per box face (no real slopes, weak spots or spaced armor; ERA/NERA, spall and modules: see
  `vehicle-damage-model`), autocannon AP/HE share one
  shell (same entity), artillery AP/CM/WP have no penetration data; a tank's coax and .50 share one entity and keep
  SuperbWarfare's damage; armored cockpits (A-10 tub, Mi-28) are not modelled.

## Architecture: aircraft flight model (marker `aircraft-flight-model`)

War Thunder RB-style flight for SuperbWarfare's real aircraft (A-10A, J-16, Ju 87, AC-130H fixed wing; AH-6, Mi-28 helicopters).
Only the *structure* of War Thunder's flight model was borrowed (static read of config key names in `aces.exe` with REA/a string
scan: per-configuration polars with critical AoA and post-stall branch, flaps polar blending, Mach drag, control effective speed,
`WingCritOverload`/`Vne` limits, engine inertia, helicopter collective/cyclic, vortex ring, ground effect); formulas are textbook
(Helmbold lift slope, parabolic polar, Viterna flat-plate post-stall, momentum theory/Glauert, Cheeseman-Bennett ground effect).
**Numbers are fitted to War Thunder** (user request 2026-10-25): the characteristics War Thunder publishes on its official wiki
(`wiki.warthunder.com/unit/...`, upgraded aircraft, Realistic Battles = 4th value of each row) are targets, status `WAR_THUNDER`
in the data. Never datamined game files (Gaijin's proprietary data); the AC-130H is not in War Thunder and keeps real specs.

- **Core** (`flight/aircraft/`, plain JVM, SI, tested in `src/test/.../flight/aircraft/FlightModelTest.kt`): `Aero.kt`
  (`WingPolar`, `FlapsEffect`, `MachDrag`, `Powerplant.Jet/Propeller`, `ControlAuthority`, `StructuralLimits`), `FixedWingSim`
  (forces in 4 sub-steps; pitch is AoA-commanded: neutral stick = 1 g AoA, the nose follows the flight path plus the AoA error;
  the instructor caps AoA at the critical angle x margin and at the *limit* load, the manual stick (W/S) does not, so it can stall
  and break the wing; roll/yaw rate-commanded; wheel friction on the ground), `HelicopterSim` (power-limited thrust from momentum
  theory, so translational lift emerges; ground effect; vortex ring; two drag areas). Atmosphere: ISA plus the isothermal lower
  stratosphere to 20 km (`FixedWingSim.density`). Loader-side fits (`FlightModelJson`), three targets for three knobs: fixed wing
  `fit_climb` scales the thrust while `derive_cd_min_from` keeps the top speed, `fit_turn_time` fits Oswald (then `cl_max` only if
  Oswald 0.3..1 cannot reach it) to the sustained turn time, alternated 6 rounds (`sustainedTurnTimeS`, `maxClimbRateMps`).
  Helicopter: `fit_climb` scales engine power + derives the drag area, `fit_hover` then raises power so a 1,000 m OGE hover takes
  <= 85 % collective (War Thunder's published heli climb alone leaves too little power to hover; the model climbs faster than
  it: Mi-28 15.2 vs 9.5, AH-6 11.4 vs 7.1 m/s). G limit = War Thunder's wing-break load (ultimate_factor 1.0), instructor stops
  at 90 % (`INSTRUCTOR_LOAD_MARGIN`); `vne_mach` fails the airframe past the Mach limit. Fit results are printed by the tests.
- **Data**: classpath `src/main/resources/dncity/flight_models/*.json` + `index.json` (not a datapack: SuperbWarfare moves a
  piloted aircraft on the pilot's client, so both sides need the same numbers). Every number needs a `provenance` entry
  (SOURCED/DERIVED/ESTIMATE/DESIGN/WAR_THUNDER) and `sources` must be non-empty; the parser rejects unknown keys. Sourced numbers are public
  specs (Wikipedia spec sections, cited per file); the rest are labelled estimates/design. A vehicle without a file keeps
  SuperbWarfare's own flight.
- **SuperbWarfare fork**: `entity/vehicle/utils/ExternalFlightModel.kt` (hook interface, `ExternalFlightModel.model`);
  `VehicleEngineUtils.aircraftEngine` hands supported aircraft to `externalAircraftEngine` (keeps energy, throttle/WEP, flaps,
  gear, airbrake, sounds, control-surface animation) unless the wreck threshold (health < 10 %) is reached;
  `helicopterEngine` keeps its cyclic/attitude/collective logic and only replaces airborne drag and rotor lift. Both add back
  SuperbWarfare's per-tick gravity, which the external model already includes.
- **Bridge** `compat/SbwFlightModelBridge.kt`: attitude in SuperbWarfare's render convention `Ry(-yRot) Rx(xRot) Rz(roll)`
  (nose +Z, right wing -X); the mouse-aim instructor law (bank toward the cursor, pull, level wings when centred) lives here.
  Structural failure (over-G, > 1.08 x Vne) drops the aircraft under 10 % health so SuperbWarfare's wreck fall takes over.
- **Controls (War Thunder style)**: throttle and helicopter collective are Shift (up) / Ctrl (down) (SuperbWarfare's
  `MOVE_SHIFT`/`MOVE_CTRL` -> `downInputDown`/`sprintInputDown`); fixed wing W/S = manual stick (no instructor limiter),
  helicopter W/S = manual cyclic pitch, A/D roll, Q/E rudder (SuperbWarfare's rudder keys now default to Q/E), mouse =
  instructor. While piloting an aircraft/helicopter, Minecraft/SuperbWarfare interaction keys (inventory E, drop Q, swap hands F,
  unload passengers U, disconnect towing Y) only work with Ctrl held (`event/PilotKeyGate.kt` drains their clicks otherwise);
  a Ctrl+key chord sends no throttle-down/rudder/flaps input. Dismount (Alt) is never gated. The pilot's third-person camera follows heading only, not the airframe's pitch
  (`VehicleClientUtils.pitchFreeCamera`; first-person cockpit view and other seats unchanged). The helicopter's attitude response is scaled by
  rotor speed as a 0..1 fraction (`synchedPropellerRot / 0.1`), not the raw value (which tops out at 0.12 and made it ~10x slow).
- **Not yet played in a client.** Known gaps: no autorotation, no torque yaw beyond SuperbWarfare's tail-damage spin, no
  supercharger model (props lose power with density), no per-part wing damage, AoA/G not on the HUD.

## Architecture: NERA (non-explosive reactive armor) and 3D spall (marker `nera-bulge-model`)

`ballistics/terminal/nera/NeraBulgeModel.kt` is the first real `ArmorEffectModel` (id `dncity:nera_bulge`, registered by
`ArmorEffectModels.registerBuiltins()` in `Dncity.kt`). It acts on a 3-layer `effect_package` (plate / elastomer / plate;
`BallisticsValidation` enforces the shape and the elastomer material class) in two hooks on the **back plate**:

- `BEFORE_LAYER`: plate speeds from an impulse balance (front plate towards the shooter, back plate away, both along the plate
  normal, capped by `energy_cap_fraction` of the projectile's energy). The plate velocity across the line of flight is
  `v * sin(theta)` along `normalize(n - (n.d) d)`, in 3D. The rod's head and tail get opposite sideways pushes: net push turns the
  velocity, the couple yaws `ProjectileState.axis`, and `1 - exp(-c (u/u_fail)^2)` of the penetrator breaks off (mass, length,
  integrity drop; the debris leaves as a `SpallSource`). A perpendicular hit has no sideways component and is not disrupted.
- `AFTER_LAYER`: the plate strain decides how far the plates petal; the result is an elliptical `ArmorTear` (long side along the
  trace of the flight line in the plate). A perforated sandwich also throws plug/petal spall off its back, its energy taken from the
  residual projectile. A stopped projectile still tears the tile (the observer returns `Untouched` plus the tear, no energy).

**Tears are runtime state, not data.** `EffectInteractionResult.tears` -> `TraversalState.tears` -> `ArmorRuntimeState.commit`
(per `EffectSlot`, newest 32 kept) -> `ArmorRuntimeView.tearsOf` -> `EffectInvocation.runtime`. A hit inside a tear sees no working
sandwich, one within 1.5 tear radii a weakened one, so NERA is multi-hit and degrades locally; it is never `single_use`.

`SpallFragments.expand(SpallSource, seed, count)` turns the aggregate cone into individual `Fragment`s with a 3D velocity each
(Mott mass distribution, uniform over the spherical cap, one common speed so energy is conserved exactly). Nothing consumes spall
yet; the post-penetration tracer is the intended caller (use `SeedDomain.SPALL`).

**Not calibrated.** Every number in `data/dncity/dncity/armor_effects/nera_bulge.json` is a placeholder in the range the open
literature gives. Not modelled: the back plate's motion lowering the closing speed on it, bulge overlap between hits, plate thinning.
Existing `data/dncity/armor_materials` and `resistance_presets` sit one directory above where `JsonDataRegistry("dncity/...")` looks
(`data/dncity/dncity/...`); no construction uses them yet, so nothing has noticed. Fix before the first real construction.

## Architecture: War Thunder-style vehicle damage (marker `vehicle-damage-model`)

Builds on `vehicle-armor-penetration`: what happens *behind* the plate, plus reactive armor, overmatch and ricochet chance.
Core in `ballistics/vehicle/damage/` (plain JVM, `DamageModelTest`, `M1a2LayoutTest` on SuperbWarfare's real M1A2 boxes,
`ArmorSolverWarThunderTest`). **Constants are DESIGN unless the code cites a source** (searched 2026-10-26, user request): spall
  cone and liner (patent EP0588212B1, Permali), HE filler per calibre (M107, M1 105 mm), blast radius (Kinney-Graham 100 kPa),
  Kontakt-5 / Kontakt-1 ratings (Wikipedia). Everything else (damage, hp, fire, detonation chances, jet loss) is still DESIGN.

- **Modules** (`Modules.kt`): `ModuleType` CREW / AMMO / FUEL / ENGINE / TRANSMISSION / BREECH / TURRET_DRIVE, each with hit points
  and an internal thickness that eats fragments. Placed in fractions of an anchor box (`hull` = the box enclosing all hull OBBs,
  `turret`, `engine` = SuperbWarfare's MainEngine OBB), so templates fit any vehicle. `vehicle_armor` files say `"layout"`
  (`tank`, `ifv`, `car`, `aircraft`; generic, not real layouts) and may list `"modules"` (replace template entries by id). Crew have a
  SuperbWarfare `seat`: the passenger in that seat is hurt with the crew module (KO = killed). Missing anchor -> `fallback` / hull.
- **Plates**: a face may carry `"era": {ke, ce, ke_reduction, ce_reduction, tile_m}` or `"nera": {...}`: extra mm and/or a
  share taken off the incoming penetration (counted as `r * pen` mm) while that spot is intact
  (`VehicleDamageState.reactiveIntact`, OBB index + face + point in the box frame). HEAT and KE >= 20 mm set it off; ERA is then used
  up within `tile_m`, NERA torn (and NERA is weakest square on: 40 % at 0 deg, full from 30 deg). T-90A / ZTZ-99A fronts carry
  Kontakt-5 ratings (+225 KE / +600 CE, moved out of the existing totals; FY-4 has no public figure, assumed K-5 class), hull sides
  and turret roof Kontakt-1 (-52 % HEAT, no KE, 0.13 m tiles; placement DESIGN); the M1A2's NERA split is DESIGN; M1A2 and Bradley
  have `"spall_liner": true` on hull/turret. `ArmorSolver.solve` also takes calibre (overmatch at calibre >= 3x plate: no ricochet,
  slope exponent down to 0.5), `normalization_deg`, and a roll: ricochet chance ramps 0 -> 1 over the last 10 deg before
  `ricochet_deg` (default roll 1.0 keeps the old deterministic behaviour). `armor_penetration` gained `filler_kg`,
  `fuse_delay_m`, `fuse_sensitivity_mm` (APHE); `Warhead.fillerKg` estimates HE filler from calibre when absent.
- **Behind the plate** (`DamageModel.simulate`, pure, seeded): the shell (or HEAT jet, which loses 150 mm/m in air, max 3 m)
  continues through modules; a spall cone (`SpallFragments.expand`: Mott masses, 3D directions; KE 45 deg half angle, overmatch 55,
  jet 12; a liner leaves a third of the angle and 5 % of the fragments; no spall under 14 mm calibre) traces every fragment through modules
  until the far wall (`Interior` = chain of hull/turret boxes); APHE bursts after `fuse_delay_m` if the plate armed it, HE just
  inside: blast radius 2.75 * cbrt(kg) plus a sphere of casing fragments. Then: destroyed ammo detonates (70 %, 90 % with a blast)
  or cooks off into a fire, fuel/engine may burn, crew get knocked out, the vehicle is out with fewer than 2 crew (or 0 of 1).
- **State** (`VehicleDamageState`, `compat/VehicleDamageTracker`): per vehicle on the server, saved in the entity's persistent data
  (`dncity_damage`). Fires tick every 10 ticks: hull health, crew modules + seated passengers, cook-off chance, crew put fires out
  faster. A vehicle SuperbWarfare has repaired to full health (not burning, not a wreck) gets its modules back.
- **Game effects** (`SbwVehicleArmorBridge.applyPenetration`, also for TACZ penetrations now): engine -> MainEngine health,
  transmission -> SubEngine health, breech/turret drive -> turret health; ammo detonation sets health below -max (SuperbWarfare
  wrecks, explodes and discards it in one tick); crew out sets health 0 (wreck). Tracks keep the old part damage.
- **Hit camera** (payload protocol `"2"`): module boxes coloured by state, shell/jet/spall/blast rays, blast radius, ERA/NERA,
  overmatch, ricochet chance, event lines (KO, fire, detonation, destroyed cause). Lang keys `dncity.hitcam.*` in en_us/ko_kr.
- **Not yet played in a client.** Gaps: module damage is not repaired gradually (all at once on full SuperbWarfare health), no
  turret-pop, fires do not spread between modules, crew do not swap seats, aircraft have no wing/control modules, the terminal
  `NeraBulgeModel` is not what vehicles use (they use the mm-equivalent NERA above).

## Architecture: tank rounds as TACZ ammo (marker `tank-ammo`)

Tank guns load real rounds the way TACZ guns load ammo: each round is a TACZ ammo id in DNCity's namespace (`tacz:ammo` with
`AmmoId dncity:m829`), defined in `data/dncity/index/ammo/`, `assets/dncity/display/ammo/` (slot icon only, no 3D model;
icons are generated 32x32 pixel art), crafted at the TACZ gun smith table (`data/dncity/recipe/ammo/`, group `explosives`).
- 120 mm (M1A2): M829, M829A1, M829A2, M829A3 (APFSDS), M830, M830A1 (HEAT); no HE (user, 2026-10-26: high explosive only
  for the Soviet gun). 125 mm (T-90A and ZTZ-99A share it): 3BM42, 3BM46, 3BM60 (APFSDS), 3BK18M, 3BK29M (HEAT; 3BK29M is
  `"tandem": true`: ERA adds nothing against it, NERA still does), 3OF26 (HE-FRAG, 3.4 kg A-IX-2 as `filler_kg`, not
  converted to TNT). Each tank's SuperbWarfare `Cannon.AmmoType` (`mods/SuperbWarfare/.../sbw/vehicles/<tank>.json`) lists
  them as `"@dncity:<id>"` with an `Override` (ShellType AP for rods, HE for HEAT/HE; velocity = 20 x real m/s / 1670;
  damage); the generic `large_shell_ap` / `large_shell_he` are gone from all three, grapeshot stays. SuperbWarfare's
  `TaczAmmoStrategy` (the fork) handles `@ns:id` ammo. The data is generated by a script kept outside the repo; edit the
  JSON directly.
- M829A1's sourced falloff (670 -> 570 mm over 2 km) is steeper than M829's (War Thunder shape), so past ~3 km the A1 dips
  under the M829; nobody publishes data beyond 2 km.
- **Telling rounds apart**: the fork's `GunItem.shootBullet` writes the `@ns:id` ammo into the projectile's persistent data
  (`AmmoId`); `SbwVehicleArmorBridge` registers a key resolver for `superbwarfare:cannon_shell` that returns it (else the
  vehicle type as before), so `shells/superbwarfare/tank_ammo/<id>.json` use `"key": "dncity:<id>"`.
- Values: real public estimates first (Jane's/Wikipedia/valka.cz, `ESTIMATE`), War Thunder wiki where none exists (M830,
  M830A1 penetration; KE falloff per km, `WAR_THUNDER`). Filler masses and SuperbWarfare damage are DESIGN.
- Not yet played in a client: the gun smith table tab, the in-vehicle ammo switch and the slot icons are untested in game.

## Blast injury to riders (marker `blast-injury`)

A warhead with explosive (`Warhead.fillerKg`: HE, HEAT, APHE, TACZ grenades/rockets) that hits a vehicle hurts the riders by
peak overpressure where each one sits (`ballistics/vehicle/damage/BlastInjury`, `BlastInjuryTest`; user, 2026-10-26).
- Overpressure: Kinney-Graham free-air TNT relation (Z = R / W^(1/3), min 0.3 m). Filler masses are used as given (3OF26's
  3.4 kg A-IX-2 is not converted to TNT). Inside a closed hull after a penetration it is doubled (CONFINED_FACTOR 2, the
  acoustic limit of normal reflection).
- Injury: share of max health through FM 8-9 / Glasstone anchors 22 kPa (eardrum, 0 %), 68.9 kPa (lung, 10 %), 193 kPa
  (lethality starts, 40 %), 634 kPa (~100 % lethal, 100 %); the shares between anchors are DESIGN. Those anchors are for
  long-duration waves, so short shell blasts err harsh.
- Where it applies (`SbwVehicleArmorBridge.blastInjuries` / `applyBlast`, also from `TaczVehicleArmorBridge`): penetrated ->
  the burst inside (the damage model's blast centre, else 0.15 m behind the plate); not penetrated -> the burst on the plate,
  in full for riders of an **open-top** vehicle (`"open_top": true` in `vehicle_armor`: soft_cars, soft_speedboat, soft_light), burst on the plate,
  plus an explosion cloud and bang. A closed hull that held still passes a share on (user: "a helmet does not make a hard
  blow painless"): `BlastInjury.transmitted(plate mm)` = 0.1 + 0.9 exp(-mm / 25), DESIGN (no published share; the
  mechanism -- plate flex sets up an internal wave, the jolt throws the crew -- is in KONES 2015 / Slawinski). War Thunder has
  no such rule (its overpressure needs a breach). Ricochets do nothing. Crew modules still
  run; each rider gets one hurt, the worse of the module damage and the blast.
- Not covered: near misses (a shell bursting beside a vehicle without hitting it) — SuperbWarfare's own explosion handles
  those. Not yet tried in a client.

## Removed SuperbWarfare content (marker `sbw-removed-content`)

`compat/SbwRemovedContent` takes out SuperbWarfare's energy / sci-fi weapons (user, 2026-10-26) — annihilator, laser_tower,
prism_tank (+ prismatic_bolt), waveforce_tower, yx_100 (+ swarm_drone) — and **every SuperbWarfare hand-held gun** (user,
same day: TACZ is the small-arms system; it has the RPG-7 and M320 too), with their blueprints. Kept: javelin and igla_9k38
(guided, TACZ has nothing like them), the repair tool, and the vehicles' internal vehicle_gun / empty_gun. The lists live in
`SbwRemovedNames` (no Minecraft classes, so `SbwRemovedContentTest` checks them against SuperbWarfare's own data).
SuperbWarfare's registries are left alone (its code refers to them): recipes are stripped from the recipe manager on server
start and after `/reload` (exact name or `_blueprint...` / `_smithing`, so `rpg` keeps `rpg_rocket_*`), loot through the
`dncity:remove_sbw_content` global loot modifier (DNCity loads AFTER superbwarfare/tacz so it runs after SBW's chest
modifiers), creative tab entries (including vehicle container items by their `EntityType`) are removed, the entities and item
entities never join a level (ones already in a world vanish when loaded), mobs lose a removed gun from their hands (lowest
priority, after SBW arms them), and players lose any they carry on login. The fork's `sbw/mob_guns/skeleton.json` (every
skeleton got an M79 instead of its bow) is deleted. JEI still lists them; a `/give`n gun works until the next login.
Kirov, Tom F6F, Happiest Ghast, Air Sheep and the wheelchair are vehicles, not future tech, and were kept.

## TACZ launcher sounds (marker `tacz-launcher-sounds`)

The TACZ fork plays weapon sounds through FMOD (`client/sound/SoundPlayManager` -> `FmodWeaponSoundManager`), but weapons.bank
has no launcher takes (the RPG-7 borrowed the M32A1's). Guns of TACZ type `rpg` (RPG-7, M320) now skip FMOD for shoot,
reload, bolt, draw and holster and play the gun pack's own OGGs through the vanilla sound engine (the pre-FMOD code), user
request 2026-10-26. `TaczLauncherSoundTest` checks every launcher has those sounds on disk. Not yet heard in a client.

## Far players over LOD terrain (marker `far-entity-render`)

User request 2026-10-27: longer entity distance, and players standing in Distant Horizons' LOD terrain drawn as real models
("Tarkov-style": EFT ties player draw distance to its terrain "Overall Visibility" setting; the same rule here, a player is
drawn as far as the terrain under them).
- **Why vanilla cannot**: `ChunkMap.TrackedEntity.updatePlayer` (1.21.1) only sends an entity within min(tracking range, view
  distance x 16) whose chunk the viewer has (`isChunkTracked`); on the client `Entity.shouldRenderAtSqrDistance` stops at
  bounding-box size x 64 x view scale (a player: 96 blocks at 12 chunks), the entity loop needs a compiled section, Sodium
  culls entities outside its visible sections (`EntityRendererMixin` -> `isEntityVisible`), and the projection's far plane is
  4 x render distance (`GameRenderer.getDepthFar`).
- **Vanilla range** (`client/farentity/EntityRenderDistance` + `mixin/MixinEntityRenderDistance`): living entities, entities
  with passengers and boxes >= 2 blocks render out to the chunk render distance. Tracking ranges are untouched (server
  `entity-broadcast-range-percentage` still governs mobs).
- **Beyond it** (`farentity/FarEntityServer`, `network/FarEntityPayloads.kt`): the client reports DH's LOD radius
  (`FarEntityRangePayload`, 0 without DH); every server tick each viewer gets snapshots (position, body/head/pitch, pose,
  invisibility, light at the feet, root vehicle with its seat; name + 6 equipment slots only when they change) of the players
  in that radius that vanilla does not track (`vanillaTracks` repeats vanilla's test), every 2/4/8 ticks by distance
  (<512/<1024/beyond, DESIGN), plus explicit removals. Cap: `-Ddncity.farEntities.maxBlocks` (default 4096, 0 = off).
- **Drawing** (`client/farentity/FarEntityClient`, `mixin/MixinLevelRendererFarEntities`): each far player is a `RemotePlayer`
  never added to the level, drawn by the normal `EntityRenderDispatcher` just before the entity loop's `endLastBatch()` with
  vanilla's buffer source (so Iris shades it like any entity). Snapshot interpolation lags 2 send intervals; walk animation is
  vanilla's `calculateEntityAnimation` on the interpolated path. Past 90 % of the far plane a proxy is drawn nearer and smaller
  by the same factor (same pixels). Frustum-tested at that compressed position; Sodium's section culling is bypassed. A player
  the level has is always left to vanilla. Riders: the vehicle is made client-side (`EntityType.create`) and the proxy mounted
  on it; SuperbWarfare seats by mounting order and hides crew by seat, so `compat/SbwFarEntityCompat` sends the real seat and
  pins it with `entityIndexOverride`. A vehicle type or proxy whose renderer throws is dropped, never the frame.
- **Occlusion** (`client/farentity/DhLodTerrain`, `FarEntityMath.isOccluded`): DH renders LODs into its own colour+depth
  framebuffer and copies only colour into Minecraft's (`coreSubProjects/.../shaders/apply.frag`), so the depth buffer cannot hide
  a proxy behind a far hill. A background thread marches eye -> head/chest/feet through DH's column data
  (`IDhApiTerrainDataRepo.getColumnDataAtBlockPos`, which blocks, hence the thread) from the vanilla radius to 1.5 blocks short of
  the target, 4 times a second; hidden only if all three lines hit solid LOD (a column without data never occludes). New
  proxies are drawn until an answer says otherwise. That test is for drawing only; what reaches the client is
  decided by the server (next point).
- **Anti-wallhack** (`farentity/FarEntityVisibility`, user 2026-10-27, long range only): the server sends a far player only
  while a line from the viewer's eye -- or a step to either side across the line of sight, or a jump up
  (`FarEntityMath.peekEyes`, 1 block each, DESIGN) -- to the target's head, chest or feet is clear through the *server's* DH
  terrain (`farentity/DhTerrainColumns` on `SERVER_LEVEL`, built from real chunks; DH's lookup only reads its database, it never
  queues generation), re-checked every 200 ms (500 ms beyond 1024 blocks) on a background thread, sent for 1.5 s after the
  last clear line; hidden again -> the viewer gets a removal. **Fails open** (user 2026-10-28, far players were not showing
  up at all): sent before the first answer, for a dimension DH has no world for, when a lookup throws, and always without
  DH on the server (logged once); only a check that found every line blocked hides a player.
  `-Ddncity.farEntities.serverOcclusion=false` turns the check off. Vanilla tracking range (near players) is not filtered.
- **What blocks sight** (`DhTerrainColumns.blocksSight`, both tests): only blocks with a collision shape (DH's `isSolid`),
  minus leaves (DH material LEAVES) and glass/fences/bars/chains/scaffolding/trapdoors (block id). Grass, flowers and other
  plants used to count as full blocks, so a line skimming any meadow was blocked and almost nobody was sent.
- **Not covered / known limits**: DH stores whole blocks, so slabs/snow layers count as full height. A freshly built wall counts once DH
  has re-read the chunk. Near players inside vanilla tracking range are still sent whatever is between. No shadows in Iris's shadow pass for proxies, no swim/elytra
  animation, vehicles keep their default turret/rotor state (only position, yaw, pitch are sent), name tags stay at vanilla's
  64 blocks. Compiled against the real 1.21.1/NeoForge 21.1.248 classes and DH's API; `FarEntityMathTest` (10) passes; the payload
  codec round trip and everything visual are **not yet tried in a client**.

## Network lanes: no head-of-line blocking (marker `network-lanes`)

User request 2026-10-28 ("remove the TCP blocking"). Both of DNCity's transports deliver in order, so one big or lost thing
held everything behind it. Three changes, plus one bug found on the way:
- **Audio off the reliable stream** (`network/kcp/VoiceKcpProtocol`, `VoiceKcpServer`, `VoiceKcpClient`): voice, radio and
  phone audio used to go through the per-player KCP session, which is reliable *and ordered*: one lost segment held every later
  frame until its retransmit (`DncityKcp` RTO >= 30 ms, 200 ms before the first RTT sample). Audio now goes as single datagrams
  `[conv LE][90][message]` (`RAW_DATAGRAM_CMD`; KCP segments carry 81/82 in that byte), never acked or resent; a message over
  1195 bytes falls back to KCP. The server takes a raw datagram only from an authenticated session's current address (and never a
  HELLO). `FreshnessGate` drops a frame that arrives behind a newer one of the same stream (type + speaker, compared on the
  sender's own clock); the old 250 ms staleness check stays. The client sends a `TYPE_KEEPALIVE` (9) over KCP every 5 s:
  idle sessions carried no traffic, so a player quiet for 2 min lost the session to the server's idle timeout silently (no
  radio/voice until relog; "radio sometimes does not work", 2026-10-28), and NAT mappings could lapse. Voice payload registrar is now `"2"` (a `"1"` peer would feed raw
  datagrams into KCP).
- **Music files paced** (`music/MusicAssetSender`): `send` used to queue every 250 KB chunk of a file onto the player's Minecraft
  connection in one loop (soundfont on login, `/music play` for everyone), so game packets waited behind the whole file. Now:
  per-player FIFO of 32 KiB chunks, at most 4 unacknowledged (`MusicAssetAckPayload`, sent by `MusicClientReceiver.handleChunk`
  for every chunk, whichever lane it came on), over the player's UDP session (`TYPE_ASSET_CHUNK`, off the TCP connection) when
  it has one, the Minecraft connection otherwise. A transfer with no ack for 30 s is dropped. Music registrar is `"2"`.
- **Distant Horizons' LOD stream** (`client/network/DhNetworkTuning`): DH sends LODs over the same TCP connection at a fixed
  500 KB/s per player by default. DNCity turns on DH's own `Config.Server.enableAdaptiveTransferSpeed` (off upstream) as an API
  override (`setApiValue`, the user's config file is untouched; `-Ddncity.dhAdaptiveTransfer=false` keeps DH's setting): the client
  measures the LOD bytes it gets each second and asks for that rate (`ClientCongestionControl`, AIMD +50 / -25 KB/s), so the stream
  follows the link. This uses DH's internal `core` package (not relocated in its jar), not its public API.
- **Bug fixed**: `DncityKcp` reassembles a message only when all its fragments fit its 128-segment receive queue (128 x 1176 =
  150,528 bytes); a bigger one never arrives and blocks the session. Verified with a C++ loopback harness on `DncityKcp.h`: 128
  segments delivered, 129 never. `TYPE_LOD_DATA` allowed 1 MiB; reliable messages are now capped at 128 KiB
  (`MAX_RELIABLE_MESSAGE_BYTES`, `encodeLodData` refuses more).
- Not changed: `FarPlayersPayload` and the rest of DNCity's small game payloads stay on the Minecraft connection; DH's own
  network code (it still uses TCP, only paced). Unit tests: `VoiceKcpProtocolTest`. Compiled against 1.21.1/NeoForge with the
  voice/phone/MIDI receivers stubbed; **not yet run in a client or against a real server**.

## Architecture: game launcher (`engine/launcher`, marker `dncity-launcher`)

Standalone Kotlin/Swing launcher (no LAF library: `ui/Widgets.kt` paints everything), structured after CmlLib.Core
(version manifest -> version json with `inheritsFrom` merge -> libraries/assets -> installer processors -> launch
argument builder), on `java.net.http` + Gson. Own Gradle project, included in `settings.gradle.kts` *without*
substitution; the root `build` task depends on its `assemble`.
- **Install** (`Installer`): vanilla client/libraries/assets from Mojang, then NeoForge (`neo_version`, default
  `Settings.neoForgeVersion`): downloads the installer jar, writes its `version.json`, unpacks `maven/`, runs the
  client-side processors from `install_profile.json` (skipped when their outputs' sha1 already match). Idempotent;
  a finished install starts offline. Optional pack manifest URL (`{"mods":[{"name","url","sha1"}]}`) syncs `mods/`.
- **Launch** (`GameLauncher`): rules-filtered jvm/game args, `${...}` substitution. The inherited client jar is copied to
  `versions/<id>/<id>.jar` (NeoForge's bootstraplauncher only ignores `${version_name}.jar`; the vanilla name clashes
  with the patched `minecraft` module). Commands over 20k chars go through a JDK `@argfile` (CreateProcess limit).
- **Accounts** (`Auth.kt`): offline (`Session.offline`, vanilla offline UUID) or Microsoft: OAuth device-code flow -> Xbox Live -> XSTS -> Minecraft services -> profile (same chain as CmlLib/XboxAuthNet). Only the refresh token is kept, in `<root>/account.json`, rotated on every launch. Default client id = `00000000402b5328`, exactly what CmlLib.Core.Auth.Microsoft uses (`XboxGameTitles.MinecraftJava`, the official launcher's id, so Mojang's API accepts it; it is NOT DNCity's and can be blocked). Such 16-hex ids use the Live endpoints (`login.live.com/oauth20_connect.srf` / `oauth20_token.srf`); a GUID id uses Entra v2 `consumers`. DNCity's own Azure app "Parin" (`5ac81d8f-ef78-47ca-827b-78335e5e035c`, needs "Allow public client flows") gets HTTP 403 from `login_with_xbox` until Mojang approves it (https://aka.ms/mce-reviewappid); then set it via the settings field, `-Ddncity.msClientId` or env `DNCITY_MS_CLIENT_ID`. Device-code request verified live; full sign-in not run end to end.
- **Bundled mods**: root `stageLauncherMods` copies dncity + everything `collectDistributionJars` gathers into `engine/launcher/bundled-mods`
  (gitignored), root `buildLauncher` (nested `GradleBuild`, the launcher is also an `includeBuild` only so the IDE shows its tasks) then
  packages it; jpackage puts the folder next to the launcher jar. `Installer.syncBundledMods` mirrors it into `<gameDir>/mods` on every play
  (only files listed in `mods/.dncity-managed` are replaced/removed) and fetches the newest Kotlin for Forge for 1.21.1 from Modrinth
  (SuperbWarfare needs >= 5.8). Packaging bugs only a distributed jar shows, fixed: lwjgl-nanovg natives re-published as their own
  JarJar artifact (same group:artifact as the main jar collided), `bcutil` added to jarJar, TACZ bumped to SimpleBedrockModel 2.5.1.1
  (matches SuperbWarfare; two copies of the mod id crash). Boots to the resource reload with all 14 jars; guns etc. not played.
- **MSI**: needs WiX 3.x (`C:/Program Files (x86)/WiX Toolset v3.*`, found automatically). Output `engine/launcher/build/installer/DNCity-1.0.0.msi`; generated, not installed/run.
- **Data**: `%APPDATA%/DNCity` (`~/.local/share/dncity`, macOS Application Support), override `-Ddncity.launcher.home`.
  Debug CLI: `--install` (install only), `--run` (install + start, game output on stdout).
- **Packaging** (jpackage, part of `assemble`): `build/dist/DNCity/` is a runnable app folder (`DNCity.exe` on Windows,
  `bin/DNCity` ELF on Linux, bundled Java 21 runtime). `build/installer/DNCity-*.msi` is made only on Windows **with the WiX
  Toolset v3** installed (`winget install WiXToolset.WiXToolset`); otherwise `packageInstaller` is skipped with a warning.
  The Linux ELF was not built or run here (Windows machine); no deb/rpm.
- Verified on Windows: full install into an empty dir and a NeoForge boot to the resource reload. The Swing window and
  the MSI were not looked at / built.

## GPU Hi-Z occlusion in Sodium + DH (marker `sodium-gpu-hiz-occlusion`)

User request 2026-11-05: make Sodium's renderer less CPU-bound, Dagor-Engine style, without breaking the Minecraft/Sodium
APIs other mods use; only `mods/sodium` and `mods/distant-horizons` were to change (nothing under `src/`). Modelled on
Dagor's occlusion system (read in GaijinEntertainment/DagorEngine: `prog/engine/lib3d/occlusionSystem.cpp`,
`dag_occlusionRenderer.h` `reprojectHWDepthBuffer`, `prog/dagorInclude/math/dag_occlusionTest.h`,
`prog/daNetGame/shaders/downsample_depth_hzb.dshl`): the GPU reduces depth to a 256x128 farthest-depth image, it is read
back through a ring of 3 CPU-readable targets without waiting, reprojected into the current camera on the CPU, max-mipped
(test mip <= 3) and boxes are tested against it.
- **Sodium** (`client/render/occlusion/`): `GpuDepthCapture` (one full-screen pass over the depth attachment of the bound
  framebuffer right after solid+cutout terrain in `SodiumWorldRenderer.drawChunkLayer`, R32F target, 3 PBOs + fences; GL
  3.2 only; every GL state it touches is read and restored, through `GlStateManager` where Minecraft caches it; any GL
  failure disables it for the session), `HiZBuffer` (pure JVM: reprojection with one combined matrix, 2x2 farthest-wins
  splat, sky splats "open", +1 % depth slack, refuses a capture > 32 blocks away or with > 1/8 zero texels; box test from
  the clip-space interval of the box), `HiZOcclusion` (frame driver). Differences from Dagor are all conservative (see
  `HiZBuffer`'s class doc).
- **Where it culls**: `OcclusionCuller` skips *and stops traversing* through an occluded section (the camera's section
  and its 26 neighbours are never tested); `isEntityVisible` also rejects entities whose box is occluded. Never in Iris's
  shadow pass (`IrisApi.isRenderingShadowPass`, by reflection), never with graph occlusion off (spectator in a block).
  The culling matrix is vanilla `Frustum.matrix` (projection * modelView, verified in the 1.21.1 bytecode) via
  `ViewportProvider.sodium$getClipMatrix()` (a *default* method so Iris's own `ViewportProvider` frustums still compile),
  with the real camera position (the frustum's own one may be moved back by `offsetToFullyIncludeCameraCube`).
- **Staleness**: a culled graph is re-searched when a capture taken after a section rebuild/removal arrives, and at least
  every 8 frames while the camera is still. Something revealed can still appear 2-3 frames late (readback latency).
- **Option**: Sodium Performance page, "Use GPU Occlusion Culling" (`performance.useGpuOcclusionCulling`, default on).
  F3 shows `Hi-Z: on, sections culled/tested, entities N`.
- **Distant Horizons**: `core/render/occlusion/SodiumHiZBridge` calls Sodium's `HiZOcclusion.isReadyPublic()` /
  `isBoxOccludedPublic(6 doubles)` by reflection (that signature is the contract). `RenderBufferHandler.buildRenderList`
  uses it instead of the per-section hardware queries of `BoundingBoxOcclusionCuller` (no GL calls per section) when
  `Culling.useSodiumHiZOcclusion` (default true) and Sodium are on and earth curvature is off. Boxes use the section's real
  vertex Y range (`LodQuadBuilder.computeVertexYRange`, stored as `LodBufferContainer.vertexYRange` on upload) +-1 block,
  else the whole column. Occluders are vanilla terrain only (LODs behind nearer LODs are not culled, same as before).
- **Verified**: Sodium compiles (`./gradlew -p mods/sodium :neoforge:compileJava`), DH core compiles (`./gradlew -p mods/distant-horizons :core:compileJava`); `HiZBuffer` was exercised by a
  standalone harness (wall/half wall/pinhole/sky/zero/teleport/motion cases, ~0.4 ms per reprojection, ~0.2 us per box
  test after warm-up; harness kept outside the repo). **Not yet run in a client**: the GL capture path, the culling
  result on screen, Iris with a shader pack and DH have not been seen.

## War Thunder-style LOD in Distant Horizons (marker `dagor-lod-scaling`)

User request 2026-11-05 ("take War Thunder's good optimizations, LOD too"), only `mods/distant-horizons` changed. Read in
Dagor's public source: terrain LOD splits patches by 3D distance against each patch's own height bounds and culls them
with those bounds (`prog/gameLibs/heightmap/heightmapMetricsCulling.cpp` `recursive_split`, `heightmapCulling.h`
`HeightmapHeightCulling`); `WorldRenderer::updateLodsScaling` (`prog/daNetGame/render/world/worldRenderer.cpp`)
multiplies LOD distances by the shooter camera's zoom, never below the default. Three DH changes, each a config entry in
`Config.Client.Advanced.Graphics.Quality` (default on):
- `altitudeAwareLodDetail`: `LodQuadTree.calculateExpectedDetailLevel` goes through `core/render/LodDetailScaling`; the
  vertical gap between the camera and the highest meshed terrain of the section's 512-block region (cache filled by
  `LodBufferContainer` uploads, max only, so it never oscillates; sections wider than a region get no height) is added
  to DH's horizontal distance. Only a camera above the terrain (aircraft) gets coarser LODs below it.
- `zoomAwareLodDetail`: distance / zoom, zoom = tan(fovSetting/2) / tan(fov/2) (`IMinecraftRenderWrapper.getFovSetting`,
  new default method) rounded down to 1/2/4, applied at once on zoom-in, after 2 s on zoom-out (DESIGN, against tree
  reload thrash). Set each frame in `RenderBufferHandler.buildRenderList` (main pass only).
- `heightBoundFrustumCulling`: after DH's API frustum (world height only), a JOML `FrustumIntersection` test with the
  section's real vertex Y range +-1 block (`LodBufferContainer.vertexYRange`); off with earth curvature or frustum
  culling disabled, never in the shadow pass. The public `IDhApiCullingFrustum` API is unchanged.
- Not done: Dagor's per-patch precomputed height *error* metric (DH has no simplification error per section) and LOD
  geomorphing (DH's shader has no parent-level positions). Verified: `:core:compileJava` + `:common:compileJava`
  (`relocate_natives/*.sh` need the exec bit on Linux for `:core:shadowJar`); `LodDetailScaling` checked by a
  standalone harness (12 cases). **Not yet run in a client.**

## Editing the mod metadata template

`mod_authors`, `mod_description`, and version/range values live in `gradle.properties`, not in
`src/main/templates/META-INF/neoforge.mods.toml` directly — the template only has `${...}`
placeholders expanded by the `generateModMetadata` task.
