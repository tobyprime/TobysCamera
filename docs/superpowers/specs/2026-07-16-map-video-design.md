# Dynamic Map Video Design

## Scope

Add a Fabric client recording mode and a Folia/Paper video-map playback system. A finished recording prints as a selectable rectangular grid of normal filled maps. Placed map items animate by changing their pixels at the selected frame rate.

This design preserves existing photo behavior and protocol semantics.

## Recording and confirmation

- The viewfinder has a configurable key binding to switch between photo and video modes.
- Video mode exposes a configurable frame-rate binding/control. The client may select an integer FPS from 1 through the held camera's `tobyscamera:max_video_fps` component, also bounded by the server setting. The absolute server ceiling is 20 FPS because map playback is tick-driven.
- Starting video recording begins fixed-rate world captures. It uses the same clean capture point as photos: before hand rendering, GUI, name tags, and score text.
- Stopping moves to a video confirmation screen. It provides start and end frame controls, final print-size selection, and the existing composition/black-border rules. The chosen grid may be rectangular; it is not fixed to 3x4.
- Captured source frames are written to a client temporary recording directory during recording rather than retained unbounded in heap. Cancel, successful upload, and startup cleanup remove abandoned temporary recordings.
- Confirmation reads only the retained frames, processes each with the selected `PrintLayout`, maps it to the Minecraft palette/dithering mode, and uploads the resulting tiles. The preview decodes the exact encoded data for its currently selected frame.

## Film and limits

- Each retained video frame consumes one film per final map tile. Required film is `frameCount * gridWidth * gridHeight`.
- The server validates the final grid, FPS, and retained-frame count before issuing a video upload grant, then charges the entire required amount atomically. A film-free camera remains exempt.
- Configurable server limits have conservative defaults: `video-max-fps: 10`, `video-max-frames: 100`, `video-max-upload-chunks-per-second: 120`, and an absolute FPS ceiling of 20. These are reloadable with the plugin configuration.
- A camera must carry the client-readable `tobyscamera:video` component to enable video mode. Its `tobyscamera:max_video_fps` component is client-readable and server-validated; the configured server maximum remains the cap when this optional FPS limit is absent.

## Protocol and upload rate limiting

New versioned payloads are distinct from photo uploads:

- `VideoBegin(gridWidth, gridHeight, fps, retainedFrameCount)`
- `VideoGranted(token, expiresAtEpochMillis, tileBytes, maxChunksPerSecond)`
- `VideoTileChunk(token, frameIndex, tileX, tileY, offset, bytes)`
- `VideoFinish(token)`
- `VideoCreated(videoId, gridWidth, gridHeight, fps, frameCount)`

Every tile remains exactly 16,384 bytes and is transferred in the existing 8,192-byte chunks. The client uses the grant's chunk-per-second rate as a token bucket. The server enforces the same window before accepting every chunk. Exceeding it rejects and clears the video session; invalid or expired tokens retain the existing kick behavior.

## Persistence and printed maps

- SQLite stores a video record (owner, timestamp, world/location metadata, dimensions, FPS, frame count) and compressed frame tile blobs keyed by video ID, frame index, and tile coordinate.
- Printing creates one normal map ID per final tile. Each printed map gets root custom-data metadata containing the video ID and its tile coordinate, plus the existing lore fields.
- Video map metadata is copied with the normal map stack, so copies show the same dynamic tile.

## Playback budget

- A global scheduler advances each video according to elapsed time and its own FPS, looping from the final frame to frame zero.
- Map items inside item frames are indexed on entity/chunk lifecycle events. Playback targets individual maps, not complete screens.
- Each playback pass selects at most `video-max-active-map-frames` indexed video maps (default 128), ordered by their minimum squared distance to any online player. Each selected map independently receives the current video tile packet; maps outside the budget retain their last sent image until selected again.
- Display-frame updates are sent only within `video.max-update-distance` blocks (default 128). A player holding a video map receives that map's update directly, even if no item frame is nearby.
- The map renderer/cache serves the selected frame to all tracking viewers. This uses normal map update packets, never modifies the item-frame entity itself.

## Testing

- Client tests cover FPS/frame scheduling, trim-range validation, final-grid selection, and client chunk token-bucket limits.
- Common tests cover protocol round trips and video upload-session completeness/rate limits.
- Server tests cover film cost, grant validation, persistence round trips, current-frame looping, and distance-budget selection.
- The final build runs all tests, Fabric remap/published-JAR verification, and the Folia JAR build.
