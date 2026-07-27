# Folia Test Server Design

## Goal

Add an independent local Folia test server for Minecraft 1.21.11. The existing
Paper development server remains available through `runServer`; the new server
is started through the root `runFoliaServer` task.

## Approach

Use the Folia support built into `xyz.jpenilla.run-paper` 3.0.2. Its
`runPaper.folia.registerTask` API creates the module-level `runFolia` task,
downloads the matching Folia server build, and handles the same plugin JAR
detection and runtime setup already used by the Paper task.

The root build registers `runFoliaServer` as a lifecycle alias that depends on
`:folia:runFolia`. This gives users the requested task name without replacing
or renaming the plugin-provided task.

## Runtime Configuration

The Folia task uses Minecraft `1.21.11` and the independent directory
`folia/folia-test-run`. Before launch it creates `eula.txt` with
`eula=true`, loads existing `server.properties` when present, sets
`online-mode=false`, and preserves all other properties. It assigns port
`25566` so the Paper and Folia test servers can be started independently.

Generated Folia runtime state is ignored by Git. No production server files or
the existing Paper runtime directory are changed.

## Compatibility

The packaged plugin remains the same shaded artifact and continues to declare
Folia support. The test server is only a local runtime; production deployments
must use normal authentication settings and their own server directory.

## Documentation

README will document both commands:

```text
./gradlew runServer
./gradlew runFoliaServer
```

The documentation will identify the first command as Paper 1.21.11 and the
second as Folia 1.21.11, and will state that both load the same plugin JAR.

## Verification

Verification will cover:

1. Gradle exposes `runFoliaServer` and its dry run resolves `:folia:runFolia`.
2. The Folia task is configured for Minecraft 1.21.11 and the separate runtime
   directory.
3. The Folia server starts with the plugin enabled and reports Folia 1.21.11
   in its startup log.
4. Existing `common`, `fabric`, and `folia` tests remain green.
