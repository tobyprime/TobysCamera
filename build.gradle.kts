plugins {
    base
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("com.modrinth.minotaur") version "2.9.0"
}

allprojects {
    group = "dev.tobyscamera"
    version = providers.gradleProperty("mod_version").get()
}

val artifactVersion = providers.gradleProperty("artifact_version")
    .orElse(providers.gradleProperty("mod_version"))
    .get()
val supportedMinecraftVersions = providers.gradleProperty("supported_mc_versions")
    .orElse("1.21.11,26.1")
    .get()
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(providers.environmentVariable("MODRINTH_PROJECT_ID"))
    syncBodyFrom.set(layout.projectDirectory.file("MODRINTH.md").asFile.readText())
}

val validateModrinthConfiguration = tasks.register("validateModrinthConfiguration") {
    group = "publishing"
    description = "Validates the environment variables required for Modrinth publication."
    doLast {
        val missing = listOf("MODRINTH_TOKEN", "MODRINTH_PROJECT_ID")
            .filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) {
            "Missing required Modrinth environment variable(s): ${missing.joinToString(", ")}"
        }
    }
}

tasks.named("modrinthSyncBody") {
    dependsOn(validateModrinthConfiguration)
}

val fabricModrinthTaskPaths = supportedMinecraftVersions.map { minecraftVersion ->
    ":fabric-$minecraftVersion:modrinth"
}

tasks.register("publishModrinthMod") {
    group = "publishing"
    description = "Synchronizes the Modrinth project body and publishes every Fabric artifact."
    dependsOn(validateModrinthConfiguration, "modrinthSyncBody")
    dependsOn(fabricModrinthTaskPaths)
}

tasks.register("publishModrinthPlugin") {
    group = "publishing"
    description = "Synchronizes the Modrinth project body and publishes the Paper/Folia plugin artifact."
    dependsOn(validateModrinthConfiguration, "modrinthSyncBody", ":folia:modrinth")
}

gradle.projectsEvaluated {
    (fabricModrinthTaskPaths + ":folia:modrinth").forEach { taskPath ->
        tasks.findByPath(taskPath)?.mustRunAfter(validateModrinthConfiguration)
    }
}

subprojects {
    plugins.withId("java") {
        if (name.startsWith("fabric-")) return@withId
        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
    }
}

tasks.register("verifyModules") {
    dependsOn(
        ":common:test",
        ":folia:test",
        ":folia:jar",
        ":fabric-1.21.11:test",
        ":fabric-1.21.11:buildAndCollect",
        ":fabric-1.21.11:verifyPublishedJar",
        ":fabric-26.1:test",
        ":fabric-26.1:buildAndCollect",
        ":fabric-26.1:verifyPublishedJar"
    )
    doLast {
        listOf("1.21.11", "26.1").forEach { minecraftVersion ->
            check(layout.buildDirectory.file("libs/$minecraftVersion/tobyscamera-$artifactVersion+mc$minecraftVersion.jar").get().asFile.isFile) {
                "Missing collected Fabric JAR for Minecraft $minecraftVersion"
            }
        }
        check(project(":folia").layout.buildDirectory.file("libs/tobyscamera-plugin-$artifactVersion.jar").get().asFile.isFile) {
            "Missing published Paper/Folia plugin JAR"
        }
    }
}

tasks.register("verifyFabricTargets") {
    dependsOn(":fabric-1.21.11:remapJar", ":fabric-26.1:jar")
    doLast {
        listOf("1.21.11", "26.1").forEach { minecraftVersion ->
            check(findProject(":fabric-$minecraftVersion") != null) {
                "Missing Fabric target $minecraftVersion"
            }
        }
    }
}

tasks.register("runServer") {
    group = "application"
    description = "Starts a Paper development server with the Paper/Folia-compatible TobysCamera plugin."
    dependsOn(":folia:runServer")
}
