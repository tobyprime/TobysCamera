# Player-local media audit design

## Goal

Replace the global loaded-chunk media audit with scans restricted to chunks within
128 blocks of each online player. The audit must never access a chunk's entities
from another Folia region thread.

## Data flow

Every 200 ticks, schedule one entity task per online player. That task snapshots
the player's world and chunk coordinates, then enumerates the square of loaded
chunks whose closest point can be within 128 blocks. Each candidate chunk is
scheduled through `ServerTaskScheduler.runRegion`; its task reads only that
chunk's entities and reconciles any item frames it contains.

The startup scan remains global because it restores all currently loaded item
frames after a server restart. Runtime audits are player-local only. Duplicate
chunk scans from overlapping player radii are allowed initially; reconciliation
is idempotent and no media is read again when the same source still points to
the same map.

## Safety and verification

The player task reads player state only from the player's entity scheduler. Each
chunk entity query occurs only in that chunk's region scheduler task. Tests
cover the 128-block chunk range and ensure the runtime audit derives its work
from online players rather than all loaded chunks.
