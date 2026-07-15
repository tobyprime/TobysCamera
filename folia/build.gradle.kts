plugins {
    `java-library`
}

dependencies {
    implementation(project(":common"))
    compileOnly("dev.folia:folia-api:${property("folia_api_version")}")
    testImplementation("dev.folia:folia-api:${property("folia_api_version")}")
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
    dependsOn(":common:jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
}
