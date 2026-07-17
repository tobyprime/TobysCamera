# Video Playback Index Design

## Goal

Remove all server-side photo-bag preview generation and reduce video playback selection from global scans to nearby spatial-index queries.

## Preview policy

Every supported bag has a persisted 16,384-byte client preview. `MapPhotoService` and `MapVideoService` read it directly. Missing, null, or incorrectly sized data is unsupported legacy media: preview restoration fails without reading media tiles. `MapPreviewEncoder` is removed.

## Playback index

`VideoPlaybackService` keeps item-frame video maps by world/chunk and keeps players by world/chunk. Player movement, frame indexing, inventory updates, join, and quit update the relevant buckets. Each tick only visits chunks within the configured viewing radius around current players, deduplicates map IDs, selects the nearest maps under `activeLimit`, and builds viewer lists from the same nearby buckets plus direct holders. No tick-time traversal over every frame is allowed.

## Validation

Tests prove no legacy preview fallback reads tiles, and prove index results handle world separation, chunk boundaries, hand-held maps, de-duplication, distance limit, and active limit.
