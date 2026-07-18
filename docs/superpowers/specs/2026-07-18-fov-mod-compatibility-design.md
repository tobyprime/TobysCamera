# FOV Mod Compatibility Design

## Goal

Keep third-party FOV/zoom mods, such as Zoomify, functional whenever the
TobysCamera viewfinder is not applying its own zoom.

## Root cause

For the Minecraft 1.21.11 target, `CameraMixin` injects at the return of
`GameRenderer#getFov`. It calls `CallbackInfoReturnable#setReturnValue` for
every invocation, including when `viewfinderZoom()` is `1.0f`. In a cancellable
Mixin injection, this cancels the callback. That prevents later FOV-return
injections from other mods from running, so their zoom effects disappear.

The 26.1 target already returns before changing the projection matrix unless
the viewfinder zoom or roll is active, and needs no change.

## Design

In the 1.21.11 `CameraMixin`, read the viewfinder zoom once. If it is exactly
`1.0f`, return without changing the callback. If it differs from `1.0f`, retain
the existing behavior: divide the current FOV by the viewfinder zoom and set
that value on the callback.

This preserves TobysCamera's active-viewfinder zoom while leaving the FOV
callback uncancelled in all ordinary gameplay, allowing other FOV mods to
compose normally. The change is intentionally limited to the FOV injection;
roll and capture hooks remain unchanged.

## Verification

Add a focused regression test for the 1.21.11 mixin callback. With the default
closed viewfinder (`viewfinderZoom() == 1.0f`), invoking the FOV injection must
leave a `CallbackInfoReturnable<Float>` uncancelled and preserve its original
FOV. Run the focused Fabric 1.21.11 test, then the Fabric 1.21.11 test suite.
