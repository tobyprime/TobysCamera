# Paper Development Server Design

## Goal

Provide a one-command local development server for the Folia-compatible plugin.  The
development runtime will be Paper; production compatibility with Folia remains
unchanged.

## Approach

The `folia` Gradle module will use Paper's official `paperweight.userdev` plugin and
its matching `paperDevBundle` for compilation and remapping.  Paper's documentation
recommends the `xyz.jpenilla.run-paper` Run-Task plugin for local server execution;
it owns the development server download, cache, working directory and plugin loading.
This avoids a custom server-download or copy task.

The module will expose Paperweight's `:folia:runServer` task.  The root project will
provide a `runServer` lifecycle alias that depends on it, so local development starts
with:

```powershell
.\gradlew.bat runServer
```

The Fabric module's unused development-server task is disabled so Gradle's unqualified
task selection resolves this command to the Paper server only. Fabric client development
continues to use `:fabric:runClient`.

Before each development start, the task writes `eula=true` and preserves all existing
`server.properties` values while setting `online-mode=false`. These settings apply only
to the ignored local `folia/run` directory.

## Compatibility

The plugin continues to compile against Folia's API and retains `folia-supported:
true` in `plugin.yml`.  Paper is used only as the local development runtime.  The
packaged plugin remains suitable for its intended Folia deployment.

## Verification

`runServer` must be listed by Gradle, build the plugin before launch, and start the
Paper development server with the plugin available.  Existing unit tests must remain
green.
