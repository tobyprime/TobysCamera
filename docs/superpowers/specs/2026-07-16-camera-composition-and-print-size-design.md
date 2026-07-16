# Camera Composition and Print Size Design

## Goal

Separate camera composition from printing: choose camera roll and aspect ratio before shooting, then choose only a final map resolution after shooting. The server token represents a maximum per-axis map allocation rather than a mandatory square output.

## Composition mode

The viewfinder has a configurable composition key, defaulting to `R`. Pressing it while the viewfinder is open enters a transparent non-pausing composition screen that captures mouse input. The screen exposes:

- a continuous camera-roll slider from `0.0` through `359.9` degrees;
- a fixed aspect-ratio selector: `1:1`, `4:3`, `3:4`, `3:2`, `2:3`, `16:9`, and `9:16`;
- a custom aspect-ratio input that accepts positive `width:height` values.

Pressing the same key or Escape exits the composition screen and retains the selected settings. The roll is applied to the world projection during normal viewfinder rendering and capture. It is camera roll, not a raster rotation, so world rendering fills the entire frame without black corner triangles. There is no crop rectangle and no horizontal or vertical translation control.

## Capture and confirmation

`UploadGranted.gridSize` is renamed conceptually to `maximumGridSize`: it remains the maximum of each output axis and continues to be between one and four. Capture produces and retains a square source image at `maximumGridSize * 128` pixels per side for maximum quality.

The confirmation screen has a print-size selector from `1x` through `maximumGridSize x`. It does not expose aspect ratio or roll controls. Selecting a size does not mutate the retained source frame.

## Map layout and black padding

For a chosen print size `N` and ratio `R`, the client examines every rectangular grid with at least one side equal to `N` and neither axis above `N`. It chooses the grid whose width-to-height ratio has the smallest absolute logarithmic distance from `R`; ties prefer the larger area. Examples: `N=4, R=16:9` selects `4x2`; `N=4, R=3:2` selects `4x3`; `N=4, R=1:1` selects `4x4`.

The captured square image is scaled with contain fitting into the selected grid canvas. Remaining pixels are opaque black. The client encodes every canvas pixel into map colors and uploads its rectangular tile set.

## Protocol and server validation

`UploadBegin` already carries independent width and height. The upload grant and session validation change from `width == maximum && height == maximum` to `1 <= width <= maximum && 1 <= height <= maximum`. The expected tile count, byte budget, persistence, and created photo inventory placement use the submitted rectangular dimensions.

## Verification

Unit tests cover roll/session state, fixed and custom ratio parsing, map-grid selection, black-padding contain compositing, and server rejection of dimensions above the maximum. Integration verification uses a 4x4 grant, a 16:9 composition printed at 4x (four by two maps), and a 1:1 composition printed at 2x (two by two maps).
