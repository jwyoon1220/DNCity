// AGENT-DONE(claude): dncity-launcher
// DNCity game launcher: a standalone Swing app (no Compose/FlatLaf, UI is hand-painted) that
// installs vanilla Minecraft + NeoForge and starts the game. Structured after CmlLib.Core
// (version manifest -> version json (inheritsFrom merge) -> libraries/assets -> installer
// processors -> launch argument builder), reimplemented in Kotlin on java.net.http + Gson.
//
// Packaging (jpackage, part of `assemble`, so also of `build` and of the root project's `build`):
//   * packageApp       -> build/dist/DNCity/   an "app-image" folder: DNCity.exe on Windows,
//                         bin/DNCity (ELF) on Linux, DNCity.app on macOS, with a bundled runtime.
//   * packageInstaller -> build/installer/DNCity-<version>.msi  (Windows only). jpackage needs the
//                         WiX Toolset v3 (candle.exe/light.exe) to make an MSI; when WiX is not
//                         found the task is skipped with a warning and the app-image is still built.
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.3.20"
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "engine"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
}

val launcherMain = "io.github.jwyoon1220.dncity.launcher.MainKt"

application {
    mainClass.set(launcherMain)
}

tasks.shadowJar {
    archiveBaseName.set("dncity-launcher")
    archiveClassifier.set("all")
    archiveVersion.set("")
}

tasks.test {
    useJUnitPlatform()
}

// ---------------------------------------------------------------- jpackage

val os: OperatingSystem = OperatingSystem.current()
val appName = "DNCity"
val appVersion = project.version.toString()
val jpackageInput = layout.buildDirectory.dir("jpackage-input")
val distDir = layout.buildDirectory.dir("dist")
val installerDir = layout.buildDirectory.dir("installer")

// The bundled runtime also runs Minecraft itself (unless the user points the launcher at another
// Java), so it needs the modules Minecraft/NeoForge use, not only what the launcher's own jar needs.
val runtimeModules = listOf(
    // java.se = every standard java.* module (log4j needs java.rmi, mods use java.sql/java.management/java.desktop, ...).
    // A hand-picked list failed in the installed launcher ("Module java.rmi not found, required by log4j.core"),
    // which a dev machine with a full JDK never shows.
    "java.se",
    "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.management", "jdk.management.agent",
    "jdk.naming.dns", "jdk.naming.rmi", "jdk.zipfs", "jdk.jfr", "jdk.httpserver", "jdk.net", "jdk.attach",
    "jdk.charsets", "jdk.localedata", "jdk.security.auth", "jdk.security.jgss", "jdk.xml.dom", "jdk.dynalink",
    "jdk.jdi", "jdk.accessibility",
).joinToString(",")

fun jpackageExecutable(): String {
    val home = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
        .get().metadata.installationPath.asFile
    return File(home, if (os.isWindows) "bin/jpackage.exe" else "bin/jpackage").absolutePath
}

fun findWix(): File? {
    val exe = "candle.exe"
    System.getenv("PATH")?.split(File.pathSeparator)?.forEach {
        if (File(it, exe).isFile) return File(it)
    }
    System.getenv("WIX")?.let { if (File(it, "bin/$exe").isFile) return File(it, "bin") }
    listOf(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")).filterNotNull().forEach { pf ->
        File(pf).listFiles { f -> f.isDirectory && f.name.startsWith("WiX Toolset v3") }?.forEach {
            if (File(it, "bin/$exe").isFile) return File(it, "bin")
        }
    }
    return null
}

// `bundled-mods/` is filled by the ROOT project's `stageLauncherMods` task (dncity + every mod of the pack) right
// before it runs this build; jpackage copies it next to the launcher jar, where Installer.bundledModsDir() finds it.
val stageJpackageInput = tasks.register<Sync>("stageJpackageInput") {
    from(tasks.shadowJar)
    from(layout.projectDirectory.dir("bundled-mods")) { into("bundled-mods") }
    into(jpackageInput)
}

val packageApp = tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Builds the launcher as a self-contained app folder (DNCity.exe / bin/DNCity) with jpackage."
    dependsOn(stageJpackageInput)
    inputs.dir(jpackageInput)
    outputs.dir(distDir)
    doFirst {
        delete(distDir.get().dir(appName))
        distDir.get().asFile.mkdirs()
        executable = jpackageExecutable()
        val a = mutableListOf(
            "--type", "app-image",
            "--name", appName,
            "--app-version", appVersion,
            "--vendor", "DNCity",
            "--input", jpackageInput.get().asFile.absolutePath,
            "--main-jar", "dncity-launcher-all.jar",
            "--main-class", launcherMain,
            "--dest", distDir.get().asFile.absolutePath,
            "--add-modules", runtimeModules,
            "--java-options", "-Dfile.encoding=UTF-8",
            // jpackage's default jlink options include --strip-native-commands, which leaves the runtime without
            // bin/java.exe -- but this runtime also starts Minecraft (GameLauncher). Replacing the list keeps java.exe.
            "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        )
        args(a)
    }
}

val packageInstaller = tasks.register<Exec>("packageInstaller") {
    group = "distribution"
    description = "Builds a Windows MSI from the app folder (needs the WiX Toolset v3; skipped when it is missing)."
    dependsOn(packageApp)
    inputs.dir(distDir)
    outputs.dir(installerDir)
    onlyIf {
        when {
            !os.isWindows -> { logger.lifecycle("packageInstaller: MSI is Windows-only, skipped."); false }
            findWix() == null -> {
                logger.warn("packageInstaller: WiX Toolset v3 (candle.exe) not found -> MSI skipped. " +
                    "Install it (winget install WiXToolset.WiXToolset) and re-run; the app folder was still built.")
                false
            }
            else -> true
        }
    }
    doFirst {
        installerDir.get().asFile.mkdirs()
        executable = jpackageExecutable()
        // jpackage looks for WiX on PATH.
        findWix()?.let { environment("PATH", it.absolutePath + File.pathSeparator + System.getenv("PATH")) }
        args(
            "--type", "msi",
            "--name", appName,
            "--app-version", appVersion,
            "--vendor", "DNCity",
            "--app-image", distDir.get().dir(appName).asFile.absolutePath,
            "--dest", installerDir.get().asFile.absolutePath,
            "--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install",
            "--win-menu-group", appName,
            "--win-upgrade-uuid", "6f3c1d52-8a4e-4b7a-9d21-5c0e7b9a1f43",
        )
    }
}

tasks.assemble {
    dependsOn(packageApp, packageInstaller)
}
