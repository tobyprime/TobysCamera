# Magic Photo Camera Design

## Goal

Add a server-enforced, single-use camera item. A camera with the
`tobyscamera:magic_photo` custom-data flag requires no film and disappears
when a valid photo upload begins.

## Scope

- The feature is implemented only in the Folia server module.
- Clients keep using the existing tagged-camera protocol and require no new
  component, packet, UI, or resource changes.
- A magic photo camera is also film-free, independently of whether its custom
  data includes `tobyscamera:no_film_required`.

## Server behavior

`CameraFilmService` derives the `tobyscamera:magic_photo` key from the
configured camera namespace and exposes an item predicate for it. The service
also treats a tagged magic photo camera as film-free, so its permitted print
size is governed by the camera/server maximum rather than remaining film.

In `UploadCoordinator.begin`, after the camera, rate-limit, print-size, and
active-upload-memory validations have passed, the server consumes the held
magic photo camera before it grants the upload token. The existing film
consumption remains unchanged for every other camera type. This matches the
current film charging point and prevents a client from retaining a magic
camera after starting a valid upload.

Consumption decrements the selected hand stack by one; Minecraft removes the
stack when its amount reaches zero. The selected hand is the same hand
identified by `heldCamera`, so a magic camera in the main hand takes precedence
over one in the offhand, consistent with current camera selection.

## Failure behavior

No camera is consumed when validation rejects the upload before the
consumption point (missing camera, rate limit, invalid print size, or memory
budget). Once the server has accepted the upload and created its session, the
magic camera is intentionally not restored if the upload later expires, is
incomplete, or is rejected: starting that valid capture is the single use.

## Testing

Add Folia unit tests for the service-level behavior: a tagged magic camera is
film-free and consuming it removes exactly one item. Preserve coverage that a
normal `no_film_required` camera remains film-free without being consumed.
Run the complete Gradle test suite after the change.
