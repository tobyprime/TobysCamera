# Modrinth Publishing Design

## Goal

Publish Mod and Paper/Folia plugin artifacts to one Modrinth project through Gradle Minotaur, and synchronize one bilingual project body on every release-tag publication.

## Canonical body

`MODRINTH.md` replaces `MODRINTH_en_US.md` and `MODRINTH_zh_CN.md`. It contains the English guide first, a Markdown horizontal rule, then the Chinese guide. It is the only source for the Modrinth project description.

## Artifact-to-version mapping

Every JAR creates its own Modrinth version. The Modrinth `versionNumber` and `versionName` are exactly the JAR filename without `.jar`, so independent uploads in the same project do not conflict.

- `mod-vX.Y.Z` uploads only the two Fabric JARs, each as a separate version with loader `fabric` and its matching Minecraft version.
- `plugin-vX.Y.Z` uploads only the Paper/Folia plugin JAR as a separate version with loaders `paper` and `folia`.
- All versions have Minotaur `versionType=release`.

For example, a Mod run can publish `tobyscamera-0.1.0+build.4+mc1.21.11` and `tobyscamera-0.1.0+build.4+mc26.1`; a plugin run can publish `tobyscamera-plugin-0.1.0+build.5`.

## Gradle architecture

The root project applies Minotaur for `modrinthSyncBody`, while every generated Fabric version project and the `folia` project applies Minotaur for its own upload task. Root aggregate tasks `publishModrinthMod` and `publishModrinthPlugin` select the correct upload tasks and require a shared configuration-validation task.

All Minotaur tasks read `MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID` from environment variables. The validation task fails before a network operation if either variable is blank; it never prints the token. The root body-sync task runs before either aggregate publish task.

## CI flow

The existing tag workflow keeps its build and GitHub Release behavior. After selecting artifacts, it invokes `publishModrinthMod` for a Mod tag or `publishModrinthPlugin` for a plugin tag. The workflow maps `secrets.MODRINTH_TOKEN` and `vars.MODRINTH_PROJECT_ID` to the Gradle environment variables.

## Verification

Local tests verify aggregate task selection, artifact-derived version-number inputs, loader/game-version metadata, and missing-environment validation without contacting Modrinth. Release CI receives real credentials only from GitHub and performs the network publication.
