# TobysCamera

Fabric 1.21.11 client Mod + Folia 1.21.11+ plugin. Only the photographer installs the Mod; completed pictures are ordinary `filled_map` items visible to every vanilla client.

## Build

The build requires Java 21. When a local proxy is needed, set Gradle JVM properties, for example:

```powershell
$env:GRADLE_OPTS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890'
.\gradlew.bat clean test build --no-daemon
```

The Fabric and Folia jars are generated in `fabric/build/libs` and `folia/build/libs`.

## Server setup and manual verification

1. Put the Folia jar in `plugins/`, start the server once, then configure `plugins/TobysCamera/config.yml` as required.
2. Give the photographer the Fabric jar only. Other players do not need it.
3. Give a tagged camera item. The plugin reads Paper PDC from the item's `custom_data`; a command-compatible example is:

   ```mcfunction
   /give @s minecraft:spyglass[custom_data={PublicBukkitValues:{"tobyscamera:camera":1b}}]
   ```

4. Hold the camera and press `P` to open the square viewfinder. Use `[` / `]` to zoom, `G` to switch guides, and left click to take a picture. The server should play a shutter sound and issue a short-lived upload Token.
5. The client captures its rendered frame and opens a preview. Select **Use photo** to upload map palette tiles or **Retake** to return to the viewfinder.
6. Verify the resulting 1x1, 2x2 and 4x4 `filled_map` items can be held or put in item frames by an unmodded player.
7. Restart the server and verify the same maps still render. Fill the photographer's inventory to verify overflow drops at their feet; disconnect during delivery to verify the pending delivery is retried on join.
8. Press `P` twice quickly to verify the rate-limit response. Use a packet-debug client to submit an unknown, expired, reused, or foreign Token and verify the player is disconnected.

## Scope

This core release deliberately excludes camera settings, selectable resolution and film consumption. The protocol is versioned and reserves room for those later additions.
