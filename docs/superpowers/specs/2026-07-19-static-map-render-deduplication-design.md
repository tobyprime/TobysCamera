# Static map render deduplication design

## Goal

Avoid repeated `MapCanvas.setPixel` calls for a still camera map whose pixels
have not changed, while preserving the existing lazy attachment and vanilla
map-update behaviour.

## Scope

This change affects only `TileMapRenderer`. It does not change map creation,
map IDs, attachment lifetime, media cache behaviour, or Paper's periodic map
render scheduling.

## Design

`TileMapRenderer` owns a monotonically increasing pixel revision. Replacing or
clearing the renderer's pixels increments that revision. During `render`, the
renderer records the last revision written for each `MapCanvas` using weak
canvas keys.

If a canvas has already received the current revision, `render` returns
without calling `MapCanvas.setPixel`. Otherwise it writes the complete 128 by
128 tile and records the revision for that canvas.

Canvas-local tracking is required instead of one renderer-wide rendered flag:
a renderer must still paint a newly supplied canvas, even if it has already
painted another canvas at the same pixel revision.

## Correctness

Paper retains each renderer canvas's pixel buffer between render callbacks, so
skipping a same-revision repaint preserves the previous composed result.
Changing pixels makes the next callback repaint the full tile. A renderer
detach still removes the renderer as before, so its new attachment starts with
fresh canvas state.

## Verification

Focused unit tests verify that the first render writes all 16,384 pixels, a
second render with unchanged pixels writes none, a replaced pixel array writes
all pixels again, and a second canvas receives its own initial full render.
