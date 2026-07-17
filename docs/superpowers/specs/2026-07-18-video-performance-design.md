# Video Performance Design

## Goal

Keep video upload encoding off the Fabric client tick thread and keep video-frame disk reads and GZIP decompression off Folia's global region scheduler.

## Client upload encoding

`VideoUploadController` will own a single-worker, bounded asynchronous frame encoder. The client tick can request the next retained frame, poll for its result, and packetize completed palette tiles, but it must not call `VideoEncoder.frame` itself. Encoding remains sequential and is limited to one requested frame so the client cannot accumulate unbounded raw palette data. Upload order, chunk sizes, rate limiting, and cancellation semantics remain unchanged.

The encoder abstraction will accept a `VideoEncoder` and expose an asynchronous request/poll boundary that can be unit-tested without Minecraft rendering. Closing or rejecting an upload must cancel outstanding work and close the temporary recording exactly as before.

## Folia playback loading

`MapVideoService` will distinguish loading a tile into its thread-safe cache from applying an already-cached tile to a Bukkit map renderer. Applying pixels remains on the global scheduler. A cache miss will return immediately to `VideoPlaybackService`; that service deduplicates requests by video, map, and frame, schedules file I/O through Folia's async scheduler, and tries again on a later global tick once the cache contains the tile.

The global tick will only read in-memory values, mutate renderers, and schedule map packets. Load failures retain the existing once-per-minute logging behavior. The request key is removed on both success and failure, allowing later retries.

## Tests

Client tests will prove that frame work is submitted to an injected background executor and that the caller observes no frame until the task is run. Playback tests will prove that a cache miss is non-blocking, a preload makes the tile available, and only cached pixels are applied to a renderer-facing operation.

## Non-goals

This change does not alter frame rate limits, palette quality, storage layout, cache capacity, upload protocol, player delivery behavior, or film accounting.
