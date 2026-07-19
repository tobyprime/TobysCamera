# TobysCamera

Fabric 1.21.11 and 26.1 client Mod + Paper and Folia 1.21.11+ plugin. Only the photographer installs the Mod; completed pictures are ordinary `filled_map` items visible to every vanilla client.

Keep the client mod and server plugin on the same TobysCamera release. Older clients receive an explicit upload rejection for unsupported camera packets.

Modrinth-ready guides: [English](MODRINTH_en_US.md) | [简体中文](MODRINTH_zh_CN.md).

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

## GitHub releases

GitHub Actions creates normal GitHub Releases only from tags whose commits are on `main`.

- Push `mod-vX.Y.Z` to publish the two Fabric Mod JARs (for Minecraft 1.21.11 and 26.1).
- Push `plugin-vX.Y.Z` to publish the Paper/Folia plugin JAR.

`X.Y.Z` must be a numeric version with no prerelease or build suffix. CI writes this version into Mod and plugin metadata, then appends `+build.<GitHub run number>` only to the JAR filenames. For example, `mod-v1.2.3` can produce `tobyscamera-1.2.3+build.42+mc1.21.11.jar`. Each release also includes a SHA-256 file for every JAR.

For example, after pushing the intended commit to `main`:

```bash
git tag -a mod-v1.2.3 -m "Mod 1.2.3"
git push origin mod-v1.2.3
```

## Local Paper development server

Run `./gradlew runServer` (or `./gradlew.bat runServer` on Windows) to build the
plugin and start a local Paper 1.21.11 server. This development server automatically
loads the plugin JAR, accepts the EULA for this local project run, and sets
`online-mode=false`. The generated JAR is the same artifact for both Paper and Folia;
use normal production authentication settings when deploying it.

## Server setup and manual verification

1. Put the plugin JAR in the `plugins/` directory of either Paper or Folia, start the server once, then configure `plugins/TobysCamera/config.yml` as required.
2. Give the photographer the Fabric jar only. Other players do not need it.
3. Give a tagged camera item. The plugin reads the root fields of the item's `minecraft:custom_data` component (not Bukkit PDC); a command-compatible example is:

   ```mcfunction
   /give @s minecraft:spyglass[minecraft:custom_data={"tobyscamera:camera":1b,"tobyscamera:max_grid_size":4}]
   ```

4. Hold the camera and right-click to open the square viewfinder. Use `-` / `=` to zoom, `G` to switch guides, and the default left-mouse shutter to take a picture. The shutter key appears under the **TobysCamera** category in Minecraft's Controls settings and can be rebound. The server should play a shutter sound and issue a short-lived upload Token.
5. The client captures its rendered frame and opens a preview. Select **Use photo** to upload map palette tiles or **Retake** to return to the viewfinder.
6. Verify the resulting 1x1, 2x2 and 4x4 `filled_map` items can be held or put in item frames by an unmodded player.
7. Restart the server and verify the same maps still render. Fill the photographer's inventory to verify overflow drops at their feet; disconnect during delivery to verify the pending delivery is retried on join.
8. Press `P` twice quickly to verify the rate-limit response. Use a packet-debug client to submit an unknown, expired, reused, or foreign Token and verify the player is disconnected.

Run the same verification workflow on both Paper and Folia with the identical plugin JAR.

After editing `plugins/TobysCamera/config.yml`, run `/tobyscamera reload` (permission `tobyscamera.reload`, default OP). Existing photo maps and database connections remain active; new upload grants use the new values.

### Lazy photo loading

Historical photos are not loaded into the plugin heap during startup. The server reads them only while a tagged map or photo bag is in a player's main hand, off hand, or an item frame in a loaded chunk. Inactive renderers immediately release their pixel arrays; a cold disk read is asynchronous and applies on the tick after it completes, without blocking a server tick.

### Magic photo camera

A camera tagged with `tobyscamera:magic_photo` is film-free but can be used for only one valid photo upload. The server removes one held magic camera as soon as it accepts that upload; failed validation does not consume it. The behavior is server-side only, so no client mod update or extra item component is required.

## Scope

This core release deliberately excludes camera settings, selectable resolution and film consumption. The protocol is versioned and reserves room for those later additions.
