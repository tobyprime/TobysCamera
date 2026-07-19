import java.util.Properties

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.modrinth.minotaur")
}

val artifactVersion = providers.gradleProperty("artifact_version").orElse(rootProject.version.toString()).get()

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(providers.environmentVariable("MODRINTH_PROJECT_ID"))
    versionNumber.set("$artifactVersion+plugin")
    versionName.set("tobyscamera-plugin-$artifactVersion")
    versionType.set("release")
    uploadFile.set(tasks.named("jar"))
    gameVersions.add("1.21.11")
    loaders.addAll("paper", "folia")
}

dependencies {
    implementation(project(":common"))
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("tobyscamera-plugin-$artifactVersion.jar")
    dependsOn(":common:jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
}

tasks.runServer {
    minecraftVersion("1.21.11")
    doFirst {
        val directory = runDirectory.get().asFile
        directory.mkdirs()
        directory.resolve("eula.txt").writeText("eula=true\n")

        val propertiesFile = directory.resolve("server.properties")
        val properties = Properties()
        if (propertiesFile.isFile) {
            propertiesFile.inputStream().use { input -> properties.load(input) }
        }
        properties.setProperty("online-mode", "false")
        propertiesFile.outputStream().use {
            properties.store(it, "Local Paper development server configuration")
        }
    }
}
