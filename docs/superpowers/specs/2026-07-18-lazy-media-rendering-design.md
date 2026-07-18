# Lazy media rendering design

## Goal

Persistent photos and videos remain on disk regardless of their total count.
Startup must not enumerate stored media or load historical pixels. The plugin
only attaches media while its source is a player's main hand, off hand, or an
item frame in a loaded chunk.

## Identity and activation

Camera map items carry media ID, media kind, and tile coordinates in root
custom data; photo bags carry their media ID, kind, and preview map ID. The
database continues to persist map IDs with each media record, but these
records are looked up only when a bag is unpacked or an active media item
requires rendering. No database media index is retained in memory.

At enable, the plugin examines frames in currently loaded chunks. It reacts to
frame/entity lifecycle events and to player join, quit, held-slot and off-hand
changes. It never scans player inventories, containers, or ender chests.

## Loading and release

An active source produces a transient attachment keyed by map ID. The
attachment has a renderer and a media descriptor derived from the item, with a
set of currently active source IDs. It starts an asynchronous tile or preview
read and applies the result on the next server tick after the read completes;
no server or region thread waits for storage I/O. Cold disk I/O has no
physically enforceable one-tick completion limit.

When the last source disappears, the attachment removes the plugin renderer
from the map view, clears its pixel reference, invalidates in-flight results,
and deletes all service-side strong references. A completed stale request must
not restore data. Plugin disable and runtime reload use the same idempotent
release operation. A low-frequency loaded-frame audit closes event gaps caused
by other plugins mutating an item frame directly.

Video playback keeps only transient active-map state and its current/in-flight
frames. Historical video metadata and pixels are not loaded or cached at
startup.

## Verification

Focused tests cover no startup `loadAll`, parser validity, main/off-hand and
loaded-frame activation, inactive release, stale-read suppression, and
on-demand post-restart attachment. Existing repository tests continue to
prove persistent media can be reopened.
