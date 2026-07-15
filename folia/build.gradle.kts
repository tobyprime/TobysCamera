plugins {
    `java-library`
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:${property("folia_api_version")}")
}
