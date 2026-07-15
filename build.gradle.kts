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
