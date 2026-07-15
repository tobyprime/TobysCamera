# Paper Development Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a root `runServer` task that starts the Folia-compatible plugin on a local Paper 1.21.11 development server.

**Architecture:** Apply Paper's official Paperweight user-development plugin to the `folia` module for its development bundle and remapping.  Add Paper's documented Run-Task plugin to provide `:folia:runServer`; a root lifecycle task delegates to it.  Paper 1.21.11 exposes the scheduler APIs used by this plugin, so no runtime adapter is introduced.

**Tech Stack:** Gradle Kotlin DSL, Paperweight Userdev 2.0.0-beta.21, Run-Task 3.0.2, Paper 1.21.11 development bundle, Java 21.

---

### Task 1: Configure Paperweight

**Files:**
- Modify: `folia/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `fabric/build.gradle.kts`

- [x] **Step 1: Add the Paperweight plugin declaration in the root `build.gradle.kts`**

Add the official plugin version alongside the existing Fabric Loom declaration:

```kotlin
id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
```

- [x] **Step 2: Apply Paperweight and its development bundle in `folia/build.gradle.kts`**

Add the plugin to the module and replace the Folia compile-only dependency with:

```kotlin
id("io.papermc.paperweight.userdev")
id("xyz.jpenilla.run-paper") version "3.0.2"

paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

tasks.runServer {
    minecraftVersion("1.21.11")
}
```

In a `doFirst` action, create `run/eula.txt` with `eula=true`, then load or create
`run/server.properties` and set `online-mode=false` while preserving all other values.
Both files are local development runtime state.

Retain the existing project dependency, SQLite runtime dependency, tests, resource expansion and shaded `jar` configuration.

- [x] **Step 3: Add the root task alias in `build.gradle.kts`**

Register the lifecycle task:

```kotlin
tasks.register("runServer") {
    group = "application"
    description = "Starts the Paper development server with the TobysCamera plugin."
    dependsOn(":folia:runServer")
}
```

Disable `fabric:runServer` in `fabric/build.gradle.kts`, because the Fabric module is
client-only and Gradle otherwise selects that task alongside the root task when users
invoke `gradlew runServer` without a path.

- [x] **Step 4: Verify configuration and compilation**

Run:

```powershell
.\gradlew.bat runServer --dry-run --no-daemon
.\gradlew.bat :folia:compileJava --no-daemon
```

Expected: the dry run includes `:folia:runServer`, and compilation succeeds with Paper's API.

- [x] **Step 5: Commit the build configuration**

```powershell
git add settings.gradle.kts build.gradle.kts folia/build.gradle.kts
git commit -m "build: add Paper development server task"
```

### Task 2: Verify the development runtime and document it

**Files:**
- Modify: `README.md`

- [x] **Step 1: Start the development server**

Run:

```powershell
.\gradlew.bat runServer --no-daemon
```

Expected: Paper downloads or reuses its development cache, starts the local server, and logs `Enabling TobysCamera` without a linkage error.

- [x] **Step 2: Stop the server and run the complete automated suite**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: all `common`, `fabric`, and `folia` tests pass.

- [x] **Step 3: Document the development command in `README.md`**

Add:

```markdown
## Local Paper development server

Run `./gradlew runServer` (or `./gradlew.bat runServer` on Windows) to build the
plugin and start Paper's local development server.  This is for development only;
deploy the generated Folia-compatible JAR to a Folia server in production.
```

- [x] **Step 4: Commit documentation**

```powershell
git add README.md
git commit -m "docs: add Paper development server command"
```
