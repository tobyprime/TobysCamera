# Camera Clean Capture Design

## Goal

Capture the zoomed world view without GUI or first-person hand rendering, while allowing the preview renderer to show arbitrary source dimensions without distortion.

## Capture timing

A client `GameRenderer` mixin will consume a pending camera capture immediately after `renderLevel` returns and before `renderItemInHand` and GUI rendering begin. The screenshot copy is therefore ordered after world rendering but before the hand, HUD, chat, crosshair, and viewfinder overlay.

The viewfinder session remains in `CAPTURING` until the screenshot callback opens the preview. `CAPTURING` is included in the zoom-active states, so `GameRenderer.getFov` applies the selected zoom to the captured frame.

`CaptureService` remains the one-frame-delay scheduler. The client tick only arms the request; it no longer reads the main render target itself.

## Preview layout

`PreviewScreen` derives a content rectangle from the source image aspect ratio and the available preview area. The rectangle uses contain fitting: the entire source is visible, centered, with unused area left as background. No source image is stretched or cropped.

The blit passes independent destination dimensions, source-region dimensions, and texture dimensions. This supports square 128px and 512px images as well as future rectangular images.

## Scope

The current upload protocol and filled-map output remain square-grid only. This change makes capture and preview dimension-safe without changing the map protocol.

## Verification

Unit tests cover preview contain layout for square, landscape, and portrait sources; capture scheduling remains covered by existing tests. Manual verification is a zoomed first-person capture with chat and HUD visible: the resulting preview must be zoomed, have no hand, and have no GUI.
