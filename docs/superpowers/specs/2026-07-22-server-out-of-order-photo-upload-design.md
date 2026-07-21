# Server-Side Out-of-Order Photo Upload Design

## Goal

Allow the Folia/Paper plugin to complete honest photo uploads when preview, tile, or finish packets are processed in an occasional non-sequential order. The Fabric client and wire protocol remain unchanged.

## Scope

- Modify only the common server-side upload session model and the Folia/Paper upload coordinator and tests.
- Do not modify Fabric client code, packet records, packet encoding, or packet decoding.
- Preserve token ownership, upload TTL, chunk-rate, grid-size, and active-memory enforcement.

## Upload Coverage Model

Each 16,384-byte preview or map tile stores its pixels plus a fixed-size coverage bitmap. A chunk may write any in-bounds byte range regardless of the ranges received before it. Completion depends on complete byte coverage, not arrival order.

For every incoming chunk:

1. Verify that the token belongs to the sending player.
2. Verify tile coordinates where applicable.
3. Require a non-negative offset and a data length from 1 through 8,192 bytes.
4. Require the complete range to fit within the 16,384-byte destination without integer overflow.
5. For every already-covered byte in the range, require the existing byte to equal the incoming byte.
6. Copy and mark previously uncovered bytes; identical duplicate bytes are idempotent.

Conflicting overlaps remain an `UploadFailure` and terminate the invalid session. Unknown and cross-player tokens retain the existing kick behavior.

Tile chunks no longer require the preview to be complete. Preview and tiles can therefore be interleaved.

## Finish Handling

`UploadFinish` records that the client has finished sending instead of immediately rejecting an incomplete session. The coordinator completes the upload when both conditions become true, in either order:

- a finish packet has been received;
- the preview and every tile have full byte coverage.

Completion is performed once. An incomplete session with no later data remains bounded by the existing token TTL and cleanup task.

## Memory Safety

Coverage uses `BitSet`, giving a fixed upper bound of 2,048 bytes per preview or tile rather than attacker-controlled interval objects. Session admission accounts for pixel storage plus bitmap storage: 18,432 bytes for each map tile and for the preview. Existing `upload.max-active-upload-bytes` remains the aggregate admission limit.

No per-chunk collections are retained. Duplicate chunks cannot grow memory or increase completion beyond the fixed destination size.

## Compatibility

The current Fabric client continues to send two 8,192-byte chunks per image in preview-first, tile-order sequence. Those packets remain valid without any client change. The server additionally accepts different processing order for the same packets.

## Tests

Tests will cover:

- tile chunks arriving before preview chunks;
- reversed preview halves and reversed tile halves;
- finish arriving before the final chunks;
- identical duplicate and overlapping chunks being idempotent;
- conflicting overlapping data being rejected;
- negative offsets, empty chunks, oversized chunks, and out-of-bounds ranges being rejected;
- incomplete coverage never completing;
- memory admission including coverage bitmap bytes;
- existing invalid-token and rate-limit behavior remaining intact.
