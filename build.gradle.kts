plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

allprojects {
    group = "dev.tobyscamera"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("java") {
        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
    }
}

tasks.register("verifyModules") {
    dependsOn(":common:test", ":fabric:classes", ":folia:classes")
}

tasks.register("runServer") {
    group = "application"
    description = "Starts the Paper development server with the TobysCamera plugin."
    dependsOn(":folia:runServer")
}
