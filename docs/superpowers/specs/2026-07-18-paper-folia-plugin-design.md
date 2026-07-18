# Paper and Folia Plugin Compatibility Design

## Goal

Ship one TobysCamera server-plugin JAR that runs on Paper 1.21.11 and Folia 1.21.11.  Existing photo and video upload, persistent map creation, map delivery, and video playback behavior must remain the same on both server types.

## Scope

- Replace direct Folia scheduler use in plugin code with a server-scheduling abstraction.
- Select a Paper or Folia implementation at startup without loading Folia-only execution paths on Paper.
- Keep Folia's correct global, region, and entity scheduling semantics.
- Use Bukkit scheduling for the equivalent work on Paper's single main thread.
- Rename the published server artifact to `tobyscamera-plugin-<mod_version>.jar`.
- Update the documentation to state Paper and Folia support and the single-JAR installation path.

The client modules, protocol, media encoding, database format, and configuration options are unchanged.

## Architecture

`ServerTaskScheduler` is the only server-runtime scheduling dependency used by plugin services.  It exposes four operations:

- Run or schedule repeating global work.
- Run work associated with a player/entity.
- Run delayed player/entity work.
- Run work associated with a world chunk or region.

It returns a project-owned cancellation handle rather than a Folia `ScheduledTask`.  Callers can therefore cancel periodic upload cleanup and video playback without knowing the hosting server type.

At enable time, a small detector identifies Folia before creating a scheduler.  `PaperTaskScheduler` delegates all operations to Bukkit's scheduler.  `FoliaTaskScheduler` delegates global work to the global region scheduler, player work to the entity scheduler, and chunk work to the region scheduler.  Folia-specific implementation classes are isolated from the Paper path.

`TobysCameraPlugin`, the payload gateway, photo-bag placement handling, map update dispatcher, and video playback service receive the abstraction rather than directly calling `Player#getScheduler`, `Server#getGlobalRegionScheduler`, or `Bukkit#getRegionScheduler`.

## Runtime Behavior

On Paper, all scheduled plugin work runs through the Bukkit scheduler and shares the normal server main-thread model.  On Folia, work retains its current ownership: global lifecycle and playback ticks run globally, operations for a player run on that player's entity scheduler, and chunk-sensitive map refreshes run on the relevant region scheduler.

If server detection cannot establish a supported runtime, plugin enable fails with a clear error rather than executing an unsafe fallback.  Exceptions in asynchronous scheduling callbacks continue to use the current upload-rejection and cleanup paths.

`plugin.yml` continues to declare `folia-supported: true`, since the plugin is designed for Folia as well as Paper.

## Build and Distribution

The `folia` Gradle module remains the server-plugin module for source compatibility, but its JAR filename becomes `tobyscamera-plugin-<mod_version>.jar`.  There is no separate Paper artifact.  The Paper development-server task remains available, and documentation gives the same artifact for installation into either Paper or Folia's `plugins/` directory.

## Verification

- Unit-test the scheduling adapters and scheduler-dependent services with fake or mocked backends.
- Build the complete project and inspect the resulting plugin JAR for `plugin.yml` and bundled runtime dependencies.
- Start Paper 1.21.11 with the generated JAR; confirm enablement and a photo/video upload-to-map smoke test.
- Start Folia 1.21.11 with the same generated JAR; repeat the enablement and upload-to-map smoke test.

Success means both server types run the identical artifact without linkage errors and preserve the existing user-facing camera, media, and map behavior.
