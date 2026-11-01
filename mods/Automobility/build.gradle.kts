plugins {
	id("java")
}

allprojects {
	apply(plugin = "java")
	apply(plugin = "maven-publish")
}

subprojects {
	apply(plugin = "java-library")
	apply(plugin = "maven-publish")

	repositories {
		mavenCentral()
		mavenLocal()

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
					name = "Parchment"
					url = uri("https://maven.parchmentmc.org")
				}
			}
			filter {
				includeGroup("org.parchmentmc.data")
			}
		}
	}

	java.toolchain.languageVersion = JavaLanguageVersion.of("21")

	tasks {
		withType<JavaCompile> {
			options.encoding = "UTF-8"
			options.release.set(21)
		}
		withType<GenerateModuleMetadata>().configureEach {
			enabled = false
		}

		jar {
			destinationDirectory = rootDir.resolve(project.name).resolve("build").resolve("libs")

			from(rootDir.resolve("LICENSE"))

			duplicatesStrategy = DuplicatesStrategy.EXCLUDE
		}
	}

	version = "${properties["mod_version"].toString()}+${rootProject.properties["minecraft_version"]}-${project.name}"
	group = properties["maven_group"].toString()

	base {
		archivesName = "${rootProject.properties["archives_base_name"]}"
	}

	dependencies {
		compileOnly("org.jetbrains:annotations:26.0.1")
		compileOnly("de.javagl:obj:0.4.0")
	}
}

tasks.jar {
	enabled = false
}