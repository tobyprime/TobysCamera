plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

allprojects {
    group = "dev.tobyscamera"
    version = providers.gradleProperty("mod_version").get()
}

val artifactVersion = providers.gradleProperty("artifact_version")
    .orElse(providers.gradleProperty("mod_version"))
    .get()

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
