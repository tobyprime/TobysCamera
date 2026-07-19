# Virtual Map Delivery Scheduler Design

## Goal

Prevent a large camera-map exhibition from creating an unbounded burst of tile
reads and 16 KiB map packets.  Visible maps load progressively while preserving
hand and nearby-frame priority.

## Configuration

The plugin configuration exposes hot-reloadable delivery limits:

```yaml
virtual-map-delivery:
  max-concurrent-reads: 12
  per-player-maps-per-tick: 4
  per-player-bytes-per-tick: 65536
  global-bytes-per-tick: 2097152
```

Every full map packet consumes 16,384 bytes.  Configuration values must be
positive; invalid reload values leave the currently running limits unchanged.

## Ownership and Requests

`MediaMapActivationListener` remains the source-of-truth for hand and
viewer-chunk lifetimes.  It supplies `VirtualStillMapService` a source type and
frame distance: main hand has the highest priority, off hand is next, then
item frames in ascending player-to-frame chunk distance.

`VirtualStillMapService` owns source-to-map attachments.  Instead of starting a
read itself, it registers one delivery demand per attached `(player UUID,
virtual map ID)`.  Multiple sources for that pair are deduplicated and the
effective priority is the best active source.  A sent map ID is remembered
while at least one matching source remains active.

Removing a source removes its demand.  When no sources remain for a player and
map ID, the scheduler removes queued work.  An in-flight read cannot be
interrupted safely, but its completion is discarded unless the same demand is
still active.

## Scheduling and Data Flow

On each global tick, `VirtualMapDeliveryScheduler` selects eligible requests in
priority order.  Within equal priority, selection rotates fairly among players
so one player cannot monopolize the global budget.  A request is eligible only
when it stays inside the per-player map and byte limits and the global byte
limit.  It starts a read only after it is selected; scheduling a request never
populates the media tile cache by itself.

At most `max-concurrent-reads` reads run at once.  Read completion is returned
to the global scheduler.  Completion rechecks the current demand and consumes
the tick budgets immediately before sending the full packet.  A failure is
reported through the existing media-load failure logger and leaves the demand
eligible for a later retry rather than sending stale or partial data.

The scheduler exposes cancellation by source, player disconnect, and map demand
key.  `VirtualStillMapService.clear()` cancels every outstanding demand before
dropping attachment references.

## Verification

Unit tests cover request-key deduplication, hand/frame/distance ordering,
round-robin equal-priority fairness, all per-player/global budget boundaries,
the concurrent-read ceiling, and cancellation before and after a deferred read
completes.  Integration-facing service tests cover source removal, hand swap,
chunk unload, and player quit suppressing unsent packets.  Focused Folia tests
then verify plugin wiring and configuration parsing.
