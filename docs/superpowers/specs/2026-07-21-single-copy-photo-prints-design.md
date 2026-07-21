# Single-Copy Photo Prints Design

## Goal

Make newly captured photo bags behave as negatives: they must be copied before
they can become separate photo maps. A copied bag and every map it yields may
not be copied again. Photos and photo bags that predate this change remain
usable and copyable.

## Scope

- Applies to photos only; video support is out of scope and no longer exists.
- Introduces exactly one custom-data key: `tobyscamera:photo_copy`.
- Does not migrate or alter existing player items or stored photo records.

## Item states

### Legacy items

Existing photo bags and photo maps have neither the new copy key nor the
negative presentation. They retain the current behavior: a bag can be unpacked
or expanded onto item frames, and a bag or map can be copied. A copy made after
this change receives the copy key and is therefore not copyable again.

### Newly captured negatives

New capture delivery creates an ordinary photo bag with no new custom-data
key, but adds a visible `底片` lore entry. A negative cannot be unpacked by the
hold interaction and cannot be expanded onto item frames. It can be used as a
source for one copy operation.

The visible lore is deliberately not a second custom-data tag. It is the only
new-negative distinction needed because negatives never expand into member maps
or get reconstructed from them.

### Copies

Every successful copy operation clones the source and adds the only new key,
`tobyscamera:photo_copy`. When the source is a negative bag, the result removes
the `底片` lore and is therefore unpackable. Tagged bags can also be placed and
recovered normally.

Unpacking a tagged bag creates tagged photo maps. Placing such a bag in item
frames propagates the same key to the placed member maps; recovering that frame
grid recreates a tagged bag. Consequently no later conversion can remove the
copy restriction.

## Copying behavior

The copy listener treats the copy key as authoritative:

- A source containing `tobyscamera:photo_copy` produces no result in either a
  crafting table or cartography table, for both bags and ordinary photo maps.
- A source without that key produces a result with the key. Bag copies retain
  their existing blank-map cost; normal maps retain vanilla copy costs.
- Invalid or rejected operations do not consume blank maps or source items.

The listener continues to preserve camera metadata for legacy maps, with the
copy key added only to newly produced results.

## Presentation and interaction

`PhotoBagFactory` owns the literal negative lore entry, recognition helpers,
copy-result construction, and copy-key propagation helpers. This keeps the
copy listener and placement listener from interpreting item lore or raw custom
data independently.

`PhotoBagPlacementListener` rejects both unpack and item-frame expansion for a
negative. It permits legacy and copied bags; when handling a copied bag it asks
the factory to mark every derived map as a copy. Frame recovery reconstructs a
copy-tagged bag when any member carries the key.

## Error handling

- An invalid photo bag continues to be rejected without changing inventory.
- A tagged source fails silently as a non-recipe result, consistent with the
  current crafting interception behavior.
- If photo storage validation fails during unpacking or placement, no bag is
  consumed.

## Testing

Add focused Folia tests covering:

- new capture bags show `底片` but have no copy key;
- negative bags cannot unpack or expand;
- copying a negative produces a tagged, unpackable bag without negative lore;
- tagged bags and tagged maps cannot copy in crafting or cartography;
- maps unpacked or frame-derived from a tagged bag retain the key, including
  after frame recovery;
- legacy bags and maps remain copyable and unpackable, while their newly
  created copies become tagged.

Run the focused Folia tests and the complete Gradle test suite.
