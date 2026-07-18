# TobysCamera

Fabric 1.21.11 and 26.1 client Mod + Paper and Folia 1.21.11+ plugin. Only the photographer installs the Mod; completed pictures are ordinary `filled_map` items visible to every vanilla client.

## Build

The build requires Java 21 and Java 25 toolchains. When a local proxy is needed, set Gradle JVM properties, for example:

```powershell
$env:GRADLE_OPTS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890'
.\gradlew.bat verifyModules --no-daemon
```

The release artifacts are generated as:

- `build/libs/1.21.11/tobyscamera-<mod_version>+mc1.21.11.jar`
- `build/libs/26.1/tobyscamera-<mod_version>+mc26.1.jar`
- `folia/build/libs/tobyscamera-plugin-<mod_version>.jar`

## Local Paper development server

Run `./gradlew runServer` (or `./gradlew.bat runServer` on Windows) to build the
plugin and start a local Paper 1.21.11 server. This development server automatically
loads the plugin JAR, accepts the EULA for this local project run, and sets
`online-mode=false`. The generated JAR is the same artifact for both Paper and Folia;
use normal production authentication settings when deploying it.

## Server setup and manual verification

1. Put the plugin JAR in the `plugins/` directory of either Paper or Folia, start the server once, then configure `plugins/TobysCamera/config.yml` as required.
2. Give the photographer the Fabric jar only. Other players do not need it.
3. Give a tagged camera item. The plugin reads Paper PDC from the item's `custom_data`; a command-compatible example is:

   ```mcfunction
   /give @s minecraft:spyglass[custom_data={PublicBukkitValues:{"tobyscamera:camera":1b}}]
   ```

4. Hold the camera and right-click to open the square viewfinder. Use `-` / `=` to zoom, `G` to switch guides, and `Enter` to take a picture. The shutter key appears under the **TobysCamera** category in Minecraft's Controls settings and can be rebound. The server should play a shutter sound and issue a short-lived upload Token.
5. The client captures its rendered frame and opens a preview. Select **Use photo** to upload map palette tiles or **Retake** to return to the viewfinder.
6. Verify the resulting 1x1, 2x2 and 4x4 `filled_map` items can be held or put in item frames by an unmodded player.
7. Restart the server and verify the same maps still render. Fill the photographer's inventory to verify overflow drops at their feet; disconnect during delivery to verify the pending delivery is retried on join.
8. Press `P` twice quickly to verify the rate-limit response. Use a packet-debug client to submit an unknown, expired, reused, or foreign Token and verify the player is disconnected.

## Video maps

Only cameras with the `tobyscamera:video` component can record video. `tobyscamera:video_max_grid_size` and `tobyscamera:video_max_frames` independently limit a video's map size and retained frame count; `tobyscamera:max_video_fps` limits its frame rate. While holding one, `V` (rebindable in **TobysCamera** controls) switches between photo and video modes. In video mode, `]` cycles only tick-aligned `1 / 5 / 10 / 20 FPS` rates. Press the shutter once to start and again to stop. The confirmation screen trims the retained frame range, chooses the final rectangular map layout and dithering, then uploads exactly the palette bytes that it previews. Floyd–Steinberg dithering is enabled by default.

The plugin's reloadable `video:` configuration has these defaults:

```yaml
video:
  max-fps: 10                    # Hard ceiling: 20
  max-frames: 100
  max-upload-chunks-per-second: 120
  max-active-map-frames: 128
  max-update-distance: 128
```

After editing `plugins/TobysCamera/config.yml`, run `/tobyscamera reload` (permission `tobyscamera.reload`, default OP). Existing maps and database connections remain active; new upload grants and the playback budget use the new values.

Each retained frame costs one film for every final map tile: a 12-frame 2×3 video costs 72 film. Cameras marked `tobyscamera:no_film_required` remain free. Placed video maps loop independently at their own FPS; each server pass updates at most the nearest 128 individual maps and only sends display-frame updates within `video.max-update-distance` blocks (default: 128). A held video map also receives its updates directly.

### Magic photo camera

A camera tagged with `tobyscamera:magic_photo` is film-free but can be used for only one valid photo upload. The server removes one held magic camera as soon as it accepts that upload; failed validation does not consume it. The behavior is server-side only, so no client mod update or extra item component is required.

### Video manual verification

1. Give yourself a tagged camera with `tobyscamera:video`, enough film (or `tobyscamera:no_film_required`), and optionally `tobyscamera:max_video_fps`. Hold it, right-click, press `V`, choose an FPS with `]`, then press the configured shutter key to start and stop a short recording.
2. In confirmation, move the start/end controls to remove at least one frame from each side. Select a non-square composition (for example 4:3) and a print size that produces a rectangular map layout. The displayed preview must match the final map palette, including black borders and dithering.
3. Confirm printing. Check that the server grants the upload, the client throttles rather than freezing, and every delivered `filled_map` has matching grid position, photographer, location and time lore.
4. Place the delivered maps in item frames. They should loop at the selected FPS after a server restart. Place more than `video.max-active-map-frames` maps at varied distances; only the nearest configured number should receive refreshes each pass.

## Scope

This core release deliberately excludes camera settings, selectable resolution and film consumption. The protocol is versioned and reserves room for those later additions.
