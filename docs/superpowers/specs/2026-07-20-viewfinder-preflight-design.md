# Viewfinder Preflight Design

## Goal

Add a persistent default print-size decision to the live viewfinder and show the resulting map count, film cost, and remaining number of shots before the shutter is pressed. Simplify the live HUD so it does not present capture/upload states as camera modes.

## Interaction

- The composition controls gain a persistent `Print size` selector limited to the held camera's current maximum side length. It is saved with the photographer's existing local viewfinder settings.
- A live HUD shows the persistent default's `width × height` map layout, total film cost, current film count, camera maximum, and how many photos at this layout the remaining film can still produce. Film-free cameras show `No film required` instead.
- The preview keeps its existing temporary resolution selector. It starts from the persisted viewfinder default but changes only the current photo; retaking returns to the persistent viewfinder choice unchanged.
- Remove `CAP`, `WAIT`, `UPL`, and the redundant `PHOTO` mode label. Upload progress remains a transient progress indicator.

## Boundaries

The change does not alter camera tags, server-side film accounting, upload protocol, shaders, or image processing. The client merely makes the existing server-valid maximum and the selected print layout visible before capture.
