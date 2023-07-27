import fr.xpdustry.toxopid.dsl.mindustryDependencies
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import java.io.ByteArrayOutputStream

plugins {
    id("net.kyori.indra") version "3.1.2"
    id("net.kyori.indra.publishing") version "3.1.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.ltgt.errorprone") version "3.1.0"
    id("fr.xpdustry.toxopid") version "3.2.0"
    id("com.github.ben-manes.versions") version "0.47.0"
}

val metadata = fr.xpdustry.toxopid.spec.ModMetadata.fromJson(file("${rootProject.rootDir}/plugin.json"))
group = property("props.project-group").toString()
version = metadata.version + if (indraGit.headTag() == null) "-SNAPSHOT" else ""

toxopid {
    compileVersion.set("v${metadata.minGameVersion}")
    platforms.add(fr.xpdustry.toxopid.spec.ModPlatform.HEADLESS)
}

repositories {
    mavenCentral()
    maven("https://maven.xpdustry.com/anuken") {
        name = "xpdustry-anuken"
        mavenContent { releasesOnly() }
    }
}

dependencies {
    mindustryDependencies()

    val jetbrains = "24.0.1"
    compileOnly("org.jetbrains:annotations:$jetbrains")
    testCompileOnly("org.jetbrains:annotations:$jetbrains")

    // Static analysis
    annotationProcessor("com.uber.nullaway:nullaway:0.10.11")
    errorprone("com.google.errorprone:error_prone_core:2.20.0")
}

tasks.withType(JavaCompile::class.java).configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        disable("MissingSummary")
        if (!name.contains("test", true)) {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", project.property("props.root-package").toString())
        }
    }
}

tasks.shadowJar {
    archiveFileName.set("mod-loader.jar")
    archiveClassifier.set("plugin")
    from(file("${rootProject.rootDir}/plugin.json"))
}

// Required if you want to use the Release GitHub action
tasks.create("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.create("createRelease") {
    dependsOn("requireClean")

    doLast {
        // Checks if a signing key is present
        val signing = ByteArrayOutputStream().use { out ->
            exec {
                commandLine("git", "config", "--global", "user.signingkey")
                standardOutput = out
            }.run { exitValue == 0 && out.toString().isNotBlank() }
        }

        exec {
            commandLine(arrayListOf("git", "tag", "v${metadata.version}", "-F", "./CHANGELOG.md", "-a").apply { if (signing) add("-s") })
        }

        exec {
            commandLine("git", "push", "origin", "--tags")
        }
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")
    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")

    mitLicense()

    github("xpdustry", "mod-loader") {
        ci(true)
        issues(true)
        scm(true)
    }

    configurePublications {
        pom {
            organization {
                name.set("Xpdustry")
                url.set("https://www.xpdustry.fr")
            }

            developers {
                developer {
                    id.set("Phinner")
                    timezone.set("Europe/Brussels")
                }
            }
        }
    }
}
