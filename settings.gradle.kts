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
