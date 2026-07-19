# Plugin Runtime Status Design

## Goal

Add an operator-only `/tobyscamera status` command that reports the plugin's live
upload and map-rendering state, the successful-upload totals for the current
plugin run, and totals for photos stored on disk.

## Scope

The command keeps the existing `/tobyscamera reload` subcommand unchanged and
adds the `tobyscamera.status` permission with an `op` default.  It accepts no
additional arguments.

Its output contains these groups:

1. **Rendering now**: the number of distinct photo IDs with at least one active
   virtual-map attachment, and the number of active map attachments.  A photo
   viewed by multiple players counts once as a photo; every active tile or bag
   preview map counts as one attachment.
2. **Uploading now**: the number of incomplete upload sessions, the sum of
   their declared grid tiles, and the reserved upload-memory bytes and limit.
3. **This plugin run**: successful, persisted uploads since `onEnable`, as
   photo and tile totals.  Reloading configuration does not reset these values;
   disabling or restarting the plugin does.
4. **Stored on disk**: total persisted photos and tiles in the photo database.

## Architecture

`UploadCoordinator` exposes an immutable, synchronized snapshot of incomplete
uploads and reserved bytes.  It does not own completed-upload counters because
a completed network upload can still fail while being persisted.

A plugin-owned runtime-status service owns the current-run and stored totals.
On enable it initializes the stored totals with one SQLite aggregate query.  On
each successful `MapPhotoService.persist` operation it atomically increments
both the current-run and stored totals by one photo and the record's tile count.
The status command reads only immutable in-memory snapshots and never performs
disk I/O.  Offline database edits are reconciled at the next plugin enable.

`VirtualStillMapService` exposes an immutable snapshot derived from its active
attachments.  Attachments retain the media identity needed to report distinct
photo IDs and the number of active map IDs; detaching the final source removes
the attachment from that snapshot.

`TobysCameraPlugin` combines these snapshots and formats the command response.
No status state is persisted and no protocol change is required.

## Error Handling

If the startup aggregate query fails, plugin initialization fails alongside the
existing storage initialization failure.  A failed photo persistence does not
alter any counter.  Unauthorized status callers receive the existing
permission-denied response pattern.

## Testing

Unit tests cover:

- upload snapshots for multiple active grid sizes and reserved bytes;
- active virtual-map snapshots, including deduplication of one photo referenced
  by multiple sources and removal after the last detach;
- runtime totals initialized from stored totals and incremented only after a
  successful persistence notification;
- SQLite aggregate totals for empty and multi-photo repositories;
- command permission, subcommand dispatch, and formatted status output.
