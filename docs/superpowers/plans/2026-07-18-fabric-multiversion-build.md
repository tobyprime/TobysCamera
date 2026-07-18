# Fabric 多 Minecraft 版本构建 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从同一共享 Fabric 客户端源码树构建并发布 Minecraft 1.21.11 与 26.1 两个兼容 JAR。

**Architecture:** `:fabric` 只保存共享实现；`:fabric-1.21.11` 与 `:fabric-26.1` 是由版本清单动态注册的实际 Loom 项目。每个版本构建先同步版本覆盖目录、再同步共享源码（重复文件保留前者），在生成的 main/test 源集上编译；26.1 仅覆盖新的提取式 GUI 与相机渲染状态 API 所需类。

**Tech Stack:** Gradle 9 Kotlin DSL、Fabric Loom 1.17、Fabric API、Java 21/25、Mixin、JUnit 5。

---

## File structure

- `gradle.properties`: 唯一 `mod_version`、支持版本清单及全局测试版本。
- `settings.gradle.kts`: 解析支持版本并注册 `:fabric-<mcVersion>` 子项目。
- `build.gradle.kts`: 根版本、Java 默认工具链与两版本 Fabric 聚合验证。
- `fabric/versions/build.gradle.kts`: 两个 Fabric 子项目共享的 Loom、源码同步、依赖、JAR 命名与验证逻辑。
- `fabric/versions/{1.21.11,26.1}/gradle.properties`: 版本专有 Minecraft、Fabric 与 Java 属性。
- `fabric/versions/26.1/src/main/java/...`: 26.1 GUI、相机渲染和入口点的同路径覆盖类。
- `fabric/src/main/resources/fabric.mod.json`: 展开目标 Minecraft 范围、Loader 下限与统一 Mod 版本。
- `folia/build.gradle.kts`: 采用根 `mod_version` 的规范 JAR 名称。
- `README.md`: 双客户端 JAR、工具链与产物位置说明。

### Task 1: Register the two Fabric build targets

**Files:**
- Modify: `gradle.properties`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `fabric/versions/1.21.11/gradle.properties`
- Create: `fabric/versions/26.1/gradle.properties`

- [ ] **Step 1: Write the failing project-layout check**

Add a root task requiring both exact project paths and `remapJar` tasks:

```kotlin
tasks.register("verifyFabricTargets") {
    dependsOn(":fabric-1.21.11:remapJar", ":fabric-26.1:remapJar")
    doLast {
        listOf("1.21.11", "26.1").forEach { version ->
            check(findProject(":fabric-$version") != null) { "Missing Fabric target $version" }
        }
    }
}
```

- [ ] **Step 2: Run the new check**

Run: `./gradlew.bat verifyFabricTargets --no-daemon --console=plain`

Expected: FAIL because neither versioned Fabric project is registered.

- [ ] **Step 3: Register projects and create version properties**

Replace root Fabric properties with:

```properties
mod_version=0.1.0-SNAPSHOT
supported_mc_versions=1.21.11,26.1
junit_version=5.13.4
```

In `settings.gradle.kts`, retain `include("common", "fabric", "folia")`, parse `supported_mc_versions`, and register `:fabric-$version` at `fabric/versions/$version` with `buildFileName = "../build.gradle.kts"`. Set root `version` from `mod_version`; do not force Java 21 on versioned Fabric projects.

Create:

```properties
# fabric/versions/1.21.11/gradle.properties
minecraft_version=1.21.11
minecraft_version_range=1.21.11
fabric_loader_version=0.18.4
fabric_api_version=0.141.4+1.21.11
java_version=21
```

```properties
# fabric/versions/26.1/gradle.properties
minecraft_version=26.1
minecraft_version_range=>=26.1 <26.2
fabric_loader_version=0.18.5
fabric_api_version=0.144.4+26.1
java_version=25
```

- [ ] **Step 4: Verify the registered targets**

Run: `./gradlew.bat verifyFabricTargets --no-daemon --console=plain`

Expected: configuration reaches both targets, then fails only because their shared build file is not present.

- [ ] **Step 5: Commit**

```bash
git add gradle.properties settings.gradle.kts build.gradle.kts fabric/versions/1.21.11/gradle.properties fabric/versions/26.1/gradle.properties
git commit -m "build: register Fabric Minecraft targets"
```

### Task 2: Build versioned sources and deterministic artifacts

**Files:**
- Delete: `fabric/build.gradle.kts`
- Create: `fabric/versions/build.gradle.kts`
- Modify: `fabric/src/main/resources/fabric.mod.json`
- Modify: `build.gradle.kts`
- Modify: `folia/build.gradle.kts`

- [ ] **Step 1: Write the failing final-JAR metadata check**

Make `verifyPublishedJar` read `fabric.mod.json` from `remapJar.archiveFile`; it must assert both the embedded `CameraPacket.class` and:

```kotlin
check(modJson.contains("\\\"minecraft\\\": \\\"${property("minecraft_version_range")}\\\""))
check(modJson.contains("\\\"version\\\": \\\"${rootProject.version}\\\""))
```

- [ ] **Step 2: Verify the check cannot run yet**

Run: `./gradlew.bat :fabric-1.21.11:verifyPublishedJar --no-daemon --console=plain`

Expected: FAIL because no versioned Loom/source-set configuration exists.

- [ ] **Step 3: Implement the shared versioned Loom build**

Apply `fabric-loom`, retain the refmap settings, and read Minecraft, Loader, Fabric API, Java and version range from version-project properties. Create `syncVersionedMainSources`/`syncVersionedTestSources` tasks using `DuplicatesStrategy.EXCLUDE`; add version paths before shared paths:

```kotlin
from(layout.projectDirectory.dir("src/main/java")) { into("java") }
from(layout.projectDirectory.dir("src/main/resources")) { into("resources") }
from(rootProject.layout.projectDirectory.dir("fabric/src/main/java")) { into("java") }
from(rootProject.layout.projectDirectory.dir("fabric/src/main/resources")) { into("resources") }
```

Point `sourceSets.main` and `sourceSets.test` to `build/generated/versionedSrc/<minecraft_version>/...`, make compilation/resource tasks depend on the sync tasks, embed `project(":common")` in `jar`, and select the configured Java toolchain for compilation and JavaExec.

Set both `jar` and `remapJar` names to:

```kotlin
archiveFileName.set("tobyscamera-${rootProject.version}+mc${property("minecraft_version")}.jar")
```

Create `buildAndCollect` to copy the remapped JAR to `build/libs/<minecraft_version>/`.

- [ ] **Step 4: Expand metadata and normalize Folia output**

Pass `version`, `minecraft_version_range`, and `fabric_loader_version` to Fabric resource expansion. Set the Fabric metadata dependency values to `${minecraft_version_range}` and `>=${fabric_loader_version}`. In Folia use:

```kotlin
tasks.jar { archiveFileName.set("tobyscamera-folia-${rootProject.version}.jar") }
```

- [ ] **Step 5: Verify 1.21.11**

Run: `./gradlew.bat :fabric-1.21.11:test :fabric-1.21.11:verifyPublishedJar --no-daemon --console=plain`

Expected: BUILD SUCCESSFUL and `build/libs/1.21.11/tobyscamera-0.1.0-SNAPSHOT+mc1.21.11.jar` embeds the common protocol class.

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts fabric/build.gradle.kts fabric/versions/build.gradle.kts fabric/src/main/resources/fabric.mod.json folia/build.gradle.kts
git commit -m "build: produce versioned Fabric artifacts"
```

### Task 3: Port only the 26.1 rendering boundary

**Files:**
- Create: `fabric/versions/26.1/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Create: `fabric/versions/26.1/src/main/java/dev/tobyscamera/fabric/mixin/CameraMixin.java`
- Create: `fabric/versions/26.1/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderOverlay.java`
- Create: `fabric/versions/26.1/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderControlsScreen.java`
- Create: `fabric/versions/26.1/src/main/java/dev/tobyscamera/fabric/viewfinder/PreviewScreen.java`
- Create: `fabric/versions/26.1/src/main/java/dev/tobyscamera/fabric/viewfinder/VideoPreviewScreen.java`
- Create: `fabric/versions/26.1/src/test/java/dev/tobyscamera/fabric/viewfinder/PreviewScreenTest.java`
- Create: `fabric/versions/26.1/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderOverlayTest.java`

- [ ] **Step 1: Establish the exact 26.1 compiler boundary**

Run: `./gradlew.bat :fabric-26.1:compileJava :fabric-26.1:compileTestJava --no-daemon --console=plain`

Expected: FAIL against 1.21.11-only `GuiGraphics`, `Screen.render`, and `GameRenderer.getFov/getProjectionMatrix` APIs. Pure protocol, image, upload, input, and state classes remain shared.

- [ ] **Step 2: Implement 26.1 extracted GUI overrides**

Copy the four listed viewfinder classes into the 26.1 overlay directory without changing state/layout helpers. Replace every `GuiGraphics` parameter/import with `GuiGraphicsExtractor`; replace `Screen.render(...)` overrides with `Screen.extractRenderState(...)`; preserve the same `fill`, `blit`, text, and widget calls. Copy the entrypoint and retain all lifecycle/network/capture/key behavior; only its HUD callback changes to call `OVERLAY.render(graphics)` using the extracted graphics parameter.

- [ ] **Step 3: Implement 26.1 render-state camera projection**

The versioned `CameraMixin` targets `Camera.extractRenderState` at TAIL. It rebuilds `CameraRenderState.projectionMatrix` with `Camera.PROJECTION_Z_NEAR`, `state.depthFar`, window aspect ratio, active field of view divided by `TobysCameraClient.viewfinderZoom()`, then rotates that matrix by `viewfinderRollRadians()`. It also preserves world capture by injecting `GameRenderer.renderLevel` immediately before `renderItemInHand(CameraRenderState,float,Matrix4fc)`.

- [ ] **Step 4: Keep only behavior tests duplicated**

Copy `PreviewScreenTest` and `ViewfinderOverlayTest` unchanged except for obsolete GUI imports. They must retain the 400x200 landscape, 150x300 portrait, `FILM 00`, and lens-safe-area assertions; no unrelated test copies are added.

- [ ] **Step 5: Verify 26.1**

Run: `./gradlew.bat :fabric-26.1:test :fabric-26.1:verifyPublishedJar --no-daemon --console=plain`

Expected: BUILD SUCCESSFUL; the JAR has `>=26.1 <26.2` metadata and embeds the common protocol class.

- [ ] **Step 6: Commit**

```bash
git add fabric/versions/26.1/src/main/java fabric/versions/26.1/src/test/java
git commit -m "feat: support Fabric 26.1 client rendering"
```

### Task 4: Publish both targets from the root build

**Files:**
- Modify: `README.md`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Write failing aggregate assertions**

Have `verifyModules` assert these exact files:

```kotlin
listOf("1.21.11", "26.1").forEach { minecraftVersion ->
    check(layout.buildDirectory.file("libs/$minecraftVersion/tobyscamera-${project.version}+mc$minecraftVersion.jar").get().asFile.isFile)
}
```

- [ ] **Step 2: Verify aggregation fails before collection is wired**

Run: `./gradlew.bat verifyModules --no-daemon --console=plain`

Expected: FAIL because the aggregate task does not yet build and collect both targets.

- [ ] **Step 3: Wire and document release behavior**

Make `verifyModules` depend on `:fabric-1.21.11:buildAndCollect` and `:fabric-26.1:buildAndCollect`. In README, require Java 21 and Java 25, show `./gradlew.bat verifyModules`, and document the two exact client paths plus `tobyscamera-folia-<mod_version>.jar`.

- [ ] **Step 4: Run final verification**

Run: `./gradlew.bat clean verifyModules --no-daemon --console=plain`

Expected: BUILD SUCCESSFUL; both Fabric JARs are collected, Folia compiles, and all common/Fabric tests pass.

- [ ] **Step 5: Commit**

```bash
git add README.md build.gradle.kts
git commit -m "docs: describe dual-version Fabric builds"
```
