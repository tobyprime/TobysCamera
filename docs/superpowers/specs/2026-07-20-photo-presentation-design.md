# Photo Presentation Design

## Goal

Let photographers name and describe an individual photo before uploading it, and let them persistently choose whether the photo exposes its capture address and photographer name in player-visible lore.

## Confirmed behavior

- The preview screen provides a photo name and a photo description for the current upload. Both start empty every time a preview opens and are never saved as client preferences.
- A non-empty photo name replaces the default display name of both the photo bag and every map extracted from that bag. Empty retains the existing default names.
- A non-empty description is rendered in the lore of the photo bag and every extracted map. Empty descriptions produce no description lore line.
- The preview screen also provides `公开拍摄地址` and `公开拍摄者名称` options. Both default to enabled so existing behavior is unchanged, and are persisted in the existing local `viewfinder.properties` file.
- When either option is disabled, the corresponding address or photographer lore line is absent from both the bag and every extracted map. Capture time remains visible.
- Copies, item-frame placement and recovery, and later bag unpacking retain the name, description, and public/private choices of the original photo. They never adopt the copier's or viewer's local preferences.

## Architecture

The Fabric client owns only the two persistent defaults. It creates an upload-specific presentation value from the two transient text fields and those defaults when the user selects `Use photo`. The common upload protocol carries this presentation value in `UploadBegin`, so the Folia server associates it with the accepted upload token and combines it with server-captured location/time metadata.

The server stores the complete presentation and capture metadata in the photo bag's root custom data. A reconstructed bag reads that data, while bag unpacking passes the same data into map-item creation. This makes presentation deterministic through every item lifecycle path. Copying already clones source item stacks, so the root data and rendered presentation are retained without consulting client settings.

## Data model and validation

- Add a shared immutable upload-presentation value containing `name`, `description`, `publicAddress`, and `publicPhotographer`.
- Normalize name and description by trimming surrounding whitespace; empty normalized values mean no custom name or description.
- Limit each encoded text value to the protocol's existing 512 UTF-8-byte maximum. The client prevents excess input and the codec rejects invalid packet data before it reaches item creation.
- Extend `PhotoMetadata` (or a focused associated value) so the bag, map-item presenter, custom-data reader/writer, placement recovery, and unpacking all receive the same presentation data.
- Persist both visibility flags even when enabled. Persist name and description only when non-empty, while reading absent keys as legacy defaults: empty name/description and both visibility flags enabled.

## Protocol and compatibility

- Extend `Packets.UploadBegin` with the presentation value and update `PacketCodec` to encode/decode it.
- Bump the protocol version because the binary layout of an existing packet changes. Matching client and server releases are already required by the project.
- The client passes the presentation value to `PhotoUploadController.confirm`; the server retains it from upload begin through completion, including token expiry/clear behavior.

## User interface and localization

- Add localized Chinese labels/placeholders for name, description, and the two visibility controls in both supported Fabric version resource sets.
- Place the inputs and visibility controls in the preview screen without allowing them to overlap the preview image, print controls, or action buttons. The preview image's available height may shrink on short screens.
- Selecting `Retake`, closing the preview, or starting a fresh capture discards transient name and description. Reopening a preview reloads only the two persisted visibility defaults.

## Server presentation rules

- `PhotoBagFactory` uses a custom bag name when present, otherwise its existing default. Its lore contains size, then the non-empty description, then the allowed photographer/address lines, capture time, and existing instructions.
- `MapItemPresentation` uses the custom name for each map when present, otherwise its existing default. Its lore contains grid position, then the non-empty description and the allowed metadata lines.
- Admin details may retain full technical capture data for authorized server administration; public-lore suppression is deliberately limited to the user-visible item lore requested here.

## Testing

- Client settings tests prove only public-address and public-photographer defaults round-trip; transient name and description are absent from the settings record and properties file.
- Protocol tests round-trip `UploadBegin` with Chinese text and visibility combinations, reject overlong text, and reject the previous protocol version.
- Upload-controller tests prove the confirmed presentation is sent with upload begin.
- Server factory/presentation tests cover empty versus custom names, empty descriptions, each visibility option independently, and legacy missing custom-data keys.
- Lifecycle tests cover unpacking, item-frame recovery, and copied photo bags, proving names, descriptions, and visibility settings are identical to the source.

## Non-goals

- No server-wide default visibility policy or server-side editing of an existing photo's presentation.
- No persistence of the per-photo name or description in the client configuration.
- No claim that lore suppression prevents administrators or users with direct item-NBT inspection access from reading stored capture metadata.
