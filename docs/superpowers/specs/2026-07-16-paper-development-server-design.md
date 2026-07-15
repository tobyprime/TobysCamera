# Paper Development Server Design

## Goal

Provide a one-command local development server for the Folia-compatible plugin.  The
development runtime will be Paper; production compatibility with Folia remains
unchanged.

## Approach

The `folia` Gradle module will use Paper's official `paperweight.userdev` plugin and
its matching `paperDevBundle`.  Paperweight owns the development server download,
cache, working directory and plugin loading.  This avoids a custom server-download or
copy task.

The module will expose Paperweight's `:folia:runServer` task.  The root project will
provide a `runServer` lifecycle alias that depends on it, so local development starts
with:

```powershell
.\gradlew.bat runServer
```

## Compatibility

The plugin continues to compile against Folia's API and retains `folia-supported:
true` in `plugin.yml`.  Paper is used only as the local development runtime.  The
packaged plugin remains suitable for its intended Folia deployment.

## Verification

`runServer` must be listed by Gradle, build the plugin before launch, and start the
Paper development server with the plugin available.  Existing unit tests must remain
green.
