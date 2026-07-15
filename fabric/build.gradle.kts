plugins {
    `java-library`
}

dependencies {
    implementation(project(":common"))
    compileOnly("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    compileOnly("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}
