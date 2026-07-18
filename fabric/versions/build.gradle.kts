import java.util.zip.ZipFile
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    id("fabric-loom")
}

val targetMinecraftVersion = property("minecraft_version").toString()
val minecraftVersionRange = property("minecraft_version_range").toString()
val fabricLoaderVersion = property("fabric_loader_version").toString()
val fabricApiVersion = property("fabric_api_version").toString()
val targetJavaVersion = property("java_version").toString().toInt()
val artifactFileName = "tobyscamera-${rootProject.version}+mc$targetMinecraftVersion.jar"

loom {
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName.set("tobyscamera.refmap.json")
    }
}

val generatedRoot = layout.buildDirectory.dir("generated/versionedSrc/$targetMinecraftVersion")
val generatedMainRoot = generatedRoot.map { it.dir("main") }
val generatedTestRoot = generatedRoot.map { it.dir("test") }

val syncVersionedMainSources = tasks.register<Sync>("syncVersionedMainSources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    into(generatedMainRoot)
    from(layout.projectDirectory.dir("src/main/java")) { into("java") }
    from(layout.projectDirectory.dir("src/main/resources")) { into("resources") }
    from(rootProject.layout.projectDirectory.dir("fabric/src/main/java")) { into("java") }
    from(rootProject.layout.projectDirectory.dir("fabric/src/main/resources")) { into("resources") }
}

val syncVersionedTestSources = tasks.register<Sync>("syncVersionedTestSources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    into(generatedTestRoot)
    from(layout.projectDirectory.dir("src/test/java")) { into("java") }
    from(layout.projectDirectory.dir("src/test/resources")) { into("resources") }
    from(rootProject.layout.projectDirectory.dir("fabric/src/test/java")) { into("java") }
    from(rootProject.layout.projectDirectory.dir("fabric/src/test/resources")) { into("resources") }
}

the<SourceSetContainer>().apply {
    named("main") {
        java.setSrcDirs(listOf(generatedMainRoot.map { it.dir("java") }))
        resources.setSrcDirs(listOf(generatedMainRoot.map { it.dir("resources") }))
    }
    named("test") {
        java.setSrcDirs(listOf(generatedTestRoot.map { it.dir("java") }))
        resources.setSrcDirs(listOf(generatedTestRoot.map { it.dir("resources") }))
    }
}

tasks.named("compileJava") { dependsOn(syncVersionedMainSources) }
tasks.named("processResources") { dependsOn(syncVersionedMainSources) }
tasks.named("compileTestJava") { dependsOn(syncVersionedTestSources) }
tasks.named("processTestResources") { dependsOn(syncVersionedTestSources) }

dependencies {
    implementation(project(":common"))
    minecraft("com.mojang:minecraft:$targetMinecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    withSourcesJar()
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    })
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    inputs.properties(
        "version" to rootProject.version,
        "minecraft_version_range" to minecraftVersionRange,
        "fabric_loader_version" to fabricLoaderVersion,
        "java_version" to targetJavaVersion
    )
    filesMatching("fabric.mod.json") {
        expand(
            "version" to rootProject.version,
            "minecraft_version_range" to minecraftVersionRange,
            "fabric_loader_version" to fabricLoaderVersion,
            "java_version" to targetJavaVersion
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    if (name == "jar" || name == "remapJar") {
        archiveFileName.set(artifactFileName)
    }
}

tasks.jar {
    from(project(":common").the<SourceSetContainer>()["main"].output)
}

tasks.register("verifyPublishedJar") {
    dependsOn(tasks.named("remapJar"))
    doLast {
        val jar = layout.buildDirectory.file("libs/$artifactFileName").get().asFile
        ZipFile(jar).use { archive ->
            check(archive.getEntry("dev/tobyscamera/common/protocol/CameraPacket.class") != null) {
                "Published Fabric JAR is missing the embedded common protocol classes"
            }
            val modJsonEntry = requireNotNull(archive.getEntry("fabric.mod.json")) {
                "Published Fabric JAR is missing fabric.mod.json"
            }
            val modJson = archive.getInputStream(modJsonEntry).bufferedReader().use { it.readText() }
            check(modJson.contains("\"minecraft\": \"$minecraftVersionRange\"")) {
                "Published Fabric JAR has the wrong Minecraft dependency range"
            }
            check(modJson.contains("\"version\": \"${rootProject.version}\"")) {
                "Published Fabric JAR has the wrong mod version"
            }
        }
    }
}

tasks.register<Copy>("buildAndCollect") {
    dependsOn(tasks.named("remapJar"))
    from(layout.buildDirectory.file("libs/$artifactFileName"))
    into(rootProject.layout.buildDirectory.dir("libs/$targetMinecraftVersion"))
}
