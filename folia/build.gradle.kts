import java.util.Properties

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
    id("xyz.jpenilla.run-paper") version "3.0.2"
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
    archiveFileName.set("tobyscamera-plugin-${rootProject.version}.jar")
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
