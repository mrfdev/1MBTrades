import java.util.Properties

plugins {
    java
}

data class BuildMetadata(
    val pluginVersion: String,
    val buildNumber: Int,
    val targetJavaVersion: Int,
    val paperApiVersion: String,
    val declaredApiVersion: String
) {
    val paddedBuildNumber: String
        get() = buildNumber.toString().padStart(3, '0')

    val pluginDisplayVersion: String
        get() = "$pluginVersion-$paddedBuildNumber"

    val paperApiDependencyVersion: String
        get() = if (
            paperApiVersion.endsWith("-R0.1-SNAPSHOT")
            || paperApiVersion.contains(".build.")
            || paperApiVersion.contains("-alpha")
            || paperApiVersion.contains("-beta")
            || paperApiVersion.contains("-rc")
            || paperApiVersion.contains("-pre")
        ) {
            paperApiVersion
        } else {
            "$paperApiVersion-R0.1-SNAPSHOT"
        }

    val archiveFileName: String
        get() = "1MB-Trades-v$pluginVersion-$paddedBuildNumber-j$targetJavaVersion-$paperApiVersion.jar"
}

val versionFile = layout.projectDirectory.file("version.properties").asFile

fun loadBuildMetadata(): BuildMetadata {
    val properties = Properties()
    if (versionFile.exists()) {
        versionFile.inputStream().use(properties::load)
    }

    fun property(name: String, fallback: String): String = properties.getProperty(name, fallback).trim()

    return BuildMetadata(
        pluginVersion = property("pluginVersion", "1.0.0"),
        buildNumber = property("buildNumber", "1").toInt(),
        targetJavaVersion = property("targetJavaVersion", "25").toInt(),
        paperApiVersion = property("paperApiVersion", property("targetMinecraftVersion", "1.21.11")),
        declaredApiVersion = property("declaredApiVersion", "1.21.11")
    )
}

fun saveBuildMetadata(metadata: BuildMetadata) {
    val properties = Properties()
    properties.setProperty("pluginVersion", metadata.pluginVersion)
    properties.setProperty("buildNumber", metadata.paddedBuildNumber)
    properties.setProperty("targetJavaVersion", metadata.targetJavaVersion.toString())
    properties.setProperty("paperApiVersion", metadata.paperApiVersion)
    properties.setProperty("declaredApiVersion", metadata.declaredApiVersion)

    versionFile.outputStream().use { output ->
        properties.store(output, "1MB-Trades build metadata")
    }
}

fun parseSemver(version: String): Triple<Int, Int, Int> {
    val parts = version.split(".")
    require(parts.size == 3) { "pluginVersion must use major.minor.patch format, got: $version" }
    return Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

fun bumpPluginVersion(current: String, mode: String): String {
    val (major, minor, patch) = parseSemver(current)
    return when (mode) {
        "major" -> "${major + 1}.0.0"
        "minor" -> "$major.${minor + 1}.0"
        "patch" -> "$major.$minor.${patch + 1}"
        else -> error("Unsupported version bump mode: $mode")
    }
}

val buildMetadata = loadBuildMetadata()

group = "com.onemoreblock"
version = buildMetadata.pluginDisplayVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(buildMetadata.targetJavaVersion))
    }
}

base {
    archivesName.set("1MB-Trades")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${buildMetadata.paperApiDependencyVersion}")
    compileOnly("me.clip:placeholderapi:2.12.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(buildMetadata.targetJavaVersion)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.properties(
        mapOf(
            "pluginDisplayVersion" to buildMetadata.pluginDisplayVersion,
            "pluginVersion" to buildMetadata.pluginVersion,
            "buildNumber" to buildMetadata.paddedBuildNumber,
            "targetJavaVersion" to buildMetadata.targetJavaVersion.toString(),
            "paperApiVersion" to buildMetadata.paperApiVersion,
            "declaredApiVersion" to buildMetadata.declaredApiVersion
        )
    )
    filesMatching(listOf("plugin.yml", "build-info.properties")) {
        expand(
            "version" to project.version,
            "pluginVersion" to buildMetadata.pluginVersion,
            "buildNumber" to buildMetadata.paddedBuildNumber,
            "targetJavaVersion" to buildMetadata.targetJavaVersion.toString(),
            "paperApiVersion" to buildMetadata.paperApiVersion,
            "declaredApiVersion" to buildMetadata.declaredApiVersion
        )
    }
}

tasks.jar {
    archiveFileName.set(buildMetadata.archiveFileName)
    manifest {
        attributes(
            "Implementation-Title" to "1MB-Trades",
            "Implementation-Version" to buildMetadata.pluginDisplayVersion,
            "Build-Number" to buildMetadata.paddedBuildNumber,
            "Target-Java-Version" to buildMetadata.targetJavaVersion.toString(),
            "Paper-Api-Compile-Target" to buildMetadata.paperApiVersion,
            "Declared-Api-Version" to buildMetadata.declaredApiVersion
        )
    }
}

tasks.register("showVersionInfo") {
    group = "versioning"
    description = "Shows the current plugin version metadata."
    doLast {
        println("Plugin version: ${buildMetadata.pluginVersion}")
        println("Build number: ${buildMetadata.paddedBuildNumber}")
        println("Target Java: ${buildMetadata.targetJavaVersion}")
        println("Compile Paper API: ${buildMetadata.paperApiVersion}")
        println("Paper API dependency: io.papermc.paper:paper-api:${buildMetadata.paperApiDependencyVersion}")
        println("Declared api-version floor: ${buildMetadata.declaredApiVersion}")
        println("Archive name: ${buildMetadata.archiveFileName}")
    }
}

val copyReleaseJar = tasks.register<Copy>("copyReleaseJar") {
    group = "build"
    description = "Copies the latest built plugin jar into the project libs/ folder."
    from(tasks.jar)
    into(layout.projectDirectory.dir("libs"))
}

tasks.register("bumpPatchVersion") {
    group = "versioning"
    description = "Bumps the patch version and resets the build number to 001."
    doLast {
        val current = loadBuildMetadata()
        val updated = current.copy(
            pluginVersion = bumpPluginVersion(current.pluginVersion, "patch"),
            buildNumber = 1
        )
        saveBuildMetadata(updated)
        println("Updated plugin version to ${updated.pluginVersion} and reset build number to ${updated.paddedBuildNumber}.")
    }
}

tasks.register("bumpMinorVersion") {
    group = "versioning"
    description = "Bumps the minor version and resets the build number to 001."
    doLast {
        val current = loadBuildMetadata()
        val updated = current.copy(
            pluginVersion = bumpPluginVersion(current.pluginVersion, "minor"),
            buildNumber = 1
        )
        saveBuildMetadata(updated)
        println("Updated plugin version to ${updated.pluginVersion} and reset build number to ${updated.paddedBuildNumber}.")
    }
}

tasks.register("bumpMajorVersion") {
    group = "versioning"
    description = "Bumps the major version and resets the build number to 001."
    doLast {
        val current = loadBuildMetadata()
        val updated = current.copy(
            pluginVersion = bumpPluginVersion(current.pluginVersion, "major"),
            buildNumber = 1
        )
        saveBuildMetadata(updated)
        println("Updated plugin version to ${updated.pluginVersion} and reset build number to ${updated.paddedBuildNumber}.")
    }
}

tasks.register("setPluginVersion") {
    group = "versioning"
    description = "Sets the plugin version with -PnewPluginVersion=x.y.z and resets build number to 001."
    doLast {
        val newPluginVersion = findProperty("newPluginVersion")?.toString()?.trim()
            ?: error("Use -PnewPluginVersion=x.y.z")
        parseSemver(newPluginVersion)

        val current = loadBuildMetadata()
        val updated = current.copy(pluginVersion = newPluginVersion, buildNumber = 1)
        saveBuildMetadata(updated)
        println("Set plugin version to ${updated.pluginVersion} and reset build number to ${updated.paddedBuildNumber}.")
    }
}

tasks.named("build") {
    finalizedBy(copyReleaseJar)
    doLast {
        val current = loadBuildMetadata()
        val next = current.copy(buildNumber = current.buildNumber + 1)
        saveBuildMetadata(next)
        println("Prepared next build number: ${next.paddedBuildNumber}")
    }
}
