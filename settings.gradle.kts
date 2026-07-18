pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }

    plugins {
        id("fabric-loom") version "1.17.14"
    }
}

dependencyResolutionManagement {
    // Loom contributes generated/local mapping repositories during Fabric setup.
    // Prefer project repositories so those repositories remain available.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "TobysCamera"

include("common", "fabric", "folia")

providers.gradleProperty("supported_mc_versions")
    .orElse("1.21.11")
    .get()
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .forEach { minecraftVersion ->
        val projectName = "fabric-$minecraftVersion"
        include(projectName)
        project(":$projectName").projectDir = file("fabric/versions/$minecraftVersion")
        val numericVersion = minecraftVersion
            .substringBefore('-')
            .split('.')
            .mapNotNull(String::toIntOrNull)
            .let { parts -> (parts.getOrElse(0) { 0 } * 10_000) + (parts.getOrElse(1) { 0 } * 100) + parts.getOrElse(2) { 0 } }
        project(":$projectName").buildFileName = if (numericVersion > 260_000) {
            "../build-modern.gradle.kts"
        } else {
            "../build.gradle.kts"
        }
    }
