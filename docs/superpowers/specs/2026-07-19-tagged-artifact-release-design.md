# Tagged Artifact Release Design

## Goal

Publish the client Mod and Paper/Folia plugin independently from GitHub Actions, using immutable tags on `main` as the release authority.

## Tag contract

- `mod-v<version>` publishes only the two Fabric artifacts.
- `plugin-v<version>` publishes only the Paper/Folia plugin artifact.
- `<version>` is Semantic Versioning without the leading `v`. Prerelease versions are allowed, for example `0.1.0-rc.1`.
- A tag must point to a commit reachable from `origin/main`. Tags created from feature branches fail before Gradle is invoked.

## Build and publication flow

The release workflow reacts only to pushes of `mod-v*` and `plugin-v*` tags. A small Bash helper parses and validates the tag, exposes `kind`, `version`, and `prerelease` as step outputs, and verifies `main` ancestry after a full checkout.

The workflow calls `./gradlew verifyModules -Pmod_version=<version>`. Gradle already applies this property to the Fabric mod metadata, plugin metadata, and final artifact filenames. It then selects only the relevant final artifact(s), calculates SHA-256 checksum files, and creates a GitHub Release named after the pushed tag. Prerelease SemVer tags create prerelease GitHub Releases.

## Local development

`gradle.properties` remains `0.1.0-SNAPSHOT` for normal local builds. No release-version bump is committed merely to publish a release; CI supplies the immutable tag-derived version as a Gradle project property.

## Verification

The helper has shell tests covering valid Mod and plugin tags, prerelease recognition, invalid tag rejection, and a `main`-ancestry failure. The workflow uses the helper, so those tests cover the value boundary between Git tags and Gradle. Local verification also builds each release kind with `verifyModules` and inspects the selected files.
