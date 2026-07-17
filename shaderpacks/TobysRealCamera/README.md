# Tobys Real Camera

Tobys Real Camera is a standalone Minecraft shaderpack for Iris. It simulates real camera controls as a post-processing pass and is independent from the TobysCamera mod.

## Install

1. Copy the `TobysRealCamera` folder into `.minecraft/shaderpacks`.
2. Launch Minecraft with Iris installed.
3. Open Video Settings, Shader Packs.
4. Select `TobysRealCamera`.
5. Open Shader Pack Settings to adjust the camera controls.

The folder can also be zipped as `TobysRealCamera.zip` as long as the zip root contains the `shaders` folder.

## Camera Controls

- `APERTURE_FSTOP`: Simulated f-number. Lower values brighten the image; higher values darken it.
- `SHUTTER_SPEED`: Simulated shutter duration in seconds. Slower speeds brighten the image and can strengthen shutter smear.
- `ISO_VALUE`: Simulated ISO sensitivity. Higher ISO brightens the image and increases grain when grain is enabled.
- `EXPOSURE_COMP`: Exposure compensation in stops.
- `WHITE_BALANCE_K`: White balance in Kelvin. Lower values warm the image; higher values cool it.
- `CONTRAST`: Midtone contrast multiplier.
- `FILM_GRAIN`: Film grain amount.
- `VIGNETTE`: Edge darkening amount.
- `MOTION_BLUR_AMOUNT`: Strength of simple screen-space shutter smear.

## Defaults

The neutral defaults are f/5.6, 1/125 s, ISO 100, 6500 K, 0 EV, mild contrast, low grain, mild vignette, and disabled motion blur. These settings are intended to keep the image close to vanilla while making each control available for manual tuning.

## Scope

Version one is a post-processing camera simulator. Aperture affects exposure only. It does not implement depth of field, physical bokeh, autofocus, automatic metering, or integration with the TobysCamera mod.

## Validation

From the repository root, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1
```

Expected output:

```text
TobysRealCamera shaderpack validation passed.
```
