# Client Preview Upload Design

## Goal

Make the Fabric client generate the photo-bag preview from its already encoded palette tiles, upload it before the full photo or video, and persist those exact 128x128 palette pixels on the Folia server.

## Protocol

Add `UploadPreviewChunk(token, offset, data)` for photos and `VideoPreviewChunk(token, offset, data)` for videos. Each preview is exactly one map tile (16,384 bytes) and is transported in normal 8,192-byte chunks. A granted upload accepts preview chunks first; ordinary media chunks before the preview is complete are rejected. `UploadFinish` and `VideoFinish` require both a complete preview and every normal tile.

## Client generation

`MapTileEncoder` downsamples the tile grid to one 128x128 palette tile. The photo uploader derives it from the pending `EncodedPhoto`; the video uploader derives it from encoded retained frame zero. Both controllers schedule preview chunks before any normal tile chunks.

## Server persistence and restoration

Upload sessions retain the client-supplied preview. SQLite stores it as a `preview` BLOB in the photo/video metadata row. Bag creation and restart restoration read this BLOB directly. Existing records without a preview are not treated as new uploads; their preview remains recoverable from existing tiles as a backwards-compatible fallback.

## Validation

Tests cover wire round trips, client ordering, session ordering and completion requirements, SQLite round trips, and bag preview restoration from the persisted data.
