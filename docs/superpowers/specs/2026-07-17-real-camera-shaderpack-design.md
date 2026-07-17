# Real Camera Shaderpack Design

## Goal

Create an independent Iris-compatible Minecraft shaderpack that simulates adjustable real camera parameters as post-processing controls. The shaderpack is not coupled to the TobysCamera Fabric/Folia mod and can be installed separately in `.minecraft/shaderpacks`.

## Scope

The first version focuses on post-process camera simulation:

- Aperture f-stop affects exposure brightness.
- Shutter speed affects exposure brightness and optional screen-space temporal smear.
- ISO affects exposure brightness and optional film grain intensity.
- White balance shifts the rendered image toward a chosen color temperature.
- Exposure compensation, contrast, vignette, highlight compression, and grain provide practical photographic tuning.

The first version deliberately excludes depth of field, physical bokeh, autofocus, mod integration, and automatic scene metering. Those capabilities are reserved for a future version and do not change the basic pack layout.

## Architecture

Add a standalone shaderpack under `shaderpacks/TobysRealCamera/`.

The pack contains:

- `shaderpacks/TobysRealCamera/README.md` for install and parameter notes.
- `shaderpacks/TobysRealCamera/shaders/shaders.properties` for Iris shader option metadata.
- `shaderpacks/TobysRealCamera/shaders/final.vsh` for the full-screen pass vertex shader.
- `shaderpacks/TobysRealCamera/shaders/final.fsh` for the post-process fragment shader.
- `shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1` for static validation of required files, options, and shader symbols.

The fragment shader samples the rendered scene color, applies camera exposure math, white balance, highlight rolloff, contrast, vignette, and grain, then writes the final color. The pack avoids Minecraft mod code and avoids dependencies outside the Iris shaderpack interface.

## User Parameters

Expose these Iris options in `shaders.properties`:

- `APERTURE_FSTOP`: f-number used for exposure simulation.
- `SHUTTER_SPEED`: shutter duration in seconds.
- `ISO_VALUE`: ISO sensitivity.
- `EXPOSURE_COMP`: exposure compensation in stops.
- `WHITE_BALANCE_K`: color temperature in Kelvin.
- `CONTRAST`: post-process contrast strength.
- `FILM_GRAIN`: grain strength.
- `VIGNETTE`: vignette strength.
- `MOTION_BLUR_AMOUNT`: screen-space temporal smear strength.

Use conservative defaults that keep vanilla scenes readable:

- f/5.6
- 1/125 s
- ISO 100
- 6500 K white balance
- 0 EV compensation
- moderate contrast
- low grain
- mild vignette
- motion blur disabled by default

## Processing Model

Exposure uses a simple exposure value approximation:

```text
cameraExposure = (shutterSeconds * ISO_VALUE / 100) / (APERTURE_FSTOP * APERTURE_FSTOP)
normalizedExposure = cameraExposure / baselineExposure
evExposure = normalizedExposure * pow(2.0, EXPOSURE_COMP)
```

The baseline is f/5.6, 1/125 s, ISO 100. This keeps the default image close to the unmodified rendered scene while allowing photographic controls to behave in the expected direction.

White balance uses a compact Kelvin-to-RGB approximation and scales the scene color by the inverse of the selected illuminant. Lower values warm the image; higher values cool it.

Motion blur remains intentionally simple for version one. It samples a short horizontal/diagonal pattern from the current color buffer, controlled by `MOTION_BLUR_AMOUNT` and influenced by slower shutter speeds. This is a visual shutter-speed cue, not true velocity-buffer motion blur.

Film grain uses deterministic per-pixel noise based on `frameTimeCounter` and screen coordinates. ISO above 100 increases perceived grain when `FILM_GRAIN` is enabled.

## Error Handling

Invalid runtime option values should be clamped in shader code:

- Aperture never below f/0.7.
- Shutter speed never below 1/8000 s.
- ISO never below 25.
- White balance clamped to a practical 1000 K to 40000 K range.
- Grain, vignette, motion blur, and contrast clamped to non-destructive ranges.

If Iris ignores an option or a user edits properties incorrectly, the GLSL `#define` fallback values keep the shader compiling.

## Testing

Add a static validation script because shaderpacks do not run inside Gradle here. The script checks:

- Required shaderpack files exist.
- `shaders.properties` exposes every intended option.
- The fragment shader declares fallback `#define` values for every option.
- The fragment shader includes the expected processing functions for exposure, white balance, grain, vignette, and motion blur.

Manual verification:

1. Copy or zip `shaderpacks/TobysRealCamera` into `.minecraft/shaderpacks`.
2. Launch Minecraft with Iris.
3. Select the shaderpack.
4. Confirm options appear in shader settings.
5. Change f-stop, shutter speed, ISO, white balance, grain, vignette, and motion blur and verify the visual result changes in the expected direction.

## Acceptance Criteria

- The shaderpack is independent from the TobysCamera mod.
- Iris can discover the pack as a normal shaderpack directory or zip.
- Shader options are grouped and named clearly enough for manual tuning.
- Default settings preserve a readable vanilla-like image.
- Static validation passes from the repository root.
- No existing Fabric, Folia, or common Java code is modified.
