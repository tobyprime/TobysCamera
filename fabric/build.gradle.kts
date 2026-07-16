import java.util.zip.ZipFile
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("fabric-loom")
}

loom {
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName.set("tobyscamera.refmap.json")
    }
}

dependencies {
    implementation(project(":common"))
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.register("verifyPublishedJar") {
    dependsOn(tasks.named("remapJar"))
    doLast {
        val jar = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar").get().asFile
        ZipFile(jar).use {
            check(it.getEntry("dev/tobyscamera/common/protocol/CameraPacket.class") != null) {
                "Published Fabric JAR is missing the embedded common protocol classes"
            }
        }
    }
}

tasks.jar {
    from(project(":common").the<SourceSetContainer>()["main"].output)
}

// The root runServer task is reserved for the Paper plugin development server.
// Fabric development for this client-only module uses runClient.
tasks.named("runServer") {
    enabled = false
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
