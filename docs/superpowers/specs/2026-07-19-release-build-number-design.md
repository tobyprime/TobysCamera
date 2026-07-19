# Release Build Number Design

## Goal

Keep every published Mod and plugin metadata version to a numeric `X.Y.Z`, while adding a GitHub Actions-generated build identifier only to release artifact filenames.

## Tag contract

- Valid release tags are exactly `mod-vX.Y.Z` and `plugin-vX.Y.Z`.
- `X`, `Y`, and `Z` are non-negative decimal integers without leading zeroes except `0`.
- Tags with prerelease or build suffixes, including `-rc.1` and `+build.1`, are rejected. Build numbering belongs to CI, not the tag.
- Every valid tag creates a normal GitHub Release. No tag creates a prerelease release.

## Version flow

The tag parser exposes the numeric release version. The workflow supplies it to Gradle as `mod_version` and creates a second value, `artifact_version`, equal to `<mod_version>+build.<github.run_number>`.

`mod_version` is the only value written into `fabric.mod.json` and `plugin.yml`. `artifact_version` is used only by JAR filename and collected-artifact verification tasks. Thus a `mod-v1.2.3` run with number `42` produces `tobyscamera-1.2.3+build.42+mc1.21.11.jar`, `tobyscamera-1.2.3+build.42+mc26.1.jar`, and `tobyscamera-plugin-1.2.3+build.42.jar`, while all embedded metadata reports `1.2.3`.

## Verification

Shell tests reject prerelease/build tag suffixes and expose only the numeric version. Gradle verification uses explicit `mod_version` and `artifact_version` values to assert both filename and embedded metadata behavior. The GitHub workflow test uses separate Mod and plugin tags and checks that each normal Release contains only its selected artifact type and checksum file.
