# Real Camera Shaderpack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Iris-compatible shaderpack that simulates camera exposure, shutter, ISO, white balance, contrast, grain, vignette, and simple shutter smear as post-processing controls.

**Architecture:** Add `shaderpacks/TobysRealCamera` as an independent shaderpack directory with no dependency on the TobysCamera mod. A PowerShell validation script provides repository-local verification, and the Iris shader files implement one final post-process pass.

**Tech Stack:** Iris/OptiFine-style shaderpack layout, GLSL 1.20 shader programs, `shaders.properties`, PowerShell static validation.

---

## File Structure

- Create `shaderpacks/TobysRealCamera/README.md`: install notes, option meanings, and manual verification steps.
- Create `shaderpacks/TobysRealCamera/shaders/shaders.properties`: Iris option screens, sliders, defaults, and profiles.
- Create `shaderpacks/TobysRealCamera/shaders/final.vsh`: full-screen final-pass vertex shader.
- Create `shaderpacks/TobysRealCamera/shaders/final.fsh`: post-process camera simulation fragment shader.
- Create `shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1`: static validation for required files, options, and processing functions.

### Task 1: Static Validator

**Files:**
- Create: `shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1`

- [ ] **Step 1: Write the failing validator**

```powershell
$ErrorActionPreference = "Stop"

$packRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$requiredFiles = @(
    "README.md",
    "shaders/shaders.properties",
    "shaders/final.vsh",
    "shaders/final.fsh"
)

$options = @(
    "APERTURE_FSTOP",
    "SHUTTER_SPEED",
    "ISO_VALUE",
    "EXPOSURE_COMP",
    "WHITE_BALANCE_K",
    "CONTRAST",
    "FILM_GRAIN",
    "VIGNETTE",
    "MOTION_BLUR_AMOUNT"
)

foreach ($file in $requiredFiles) {
    $path = Join-Path $packRoot $file
    if (-not (Test-Path $path)) {
        throw "Missing required file: $file"
    }
}

$properties = Get-Content -Raw (Join-Path $packRoot "shaders/shaders.properties")
$fragment = Get-Content -Raw (Join-Path $packRoot "shaders/final.fsh")
$vertex = Get-Content -Raw (Join-Path $packRoot "shaders/final.vsh")

foreach ($option in $options) {
    if ($properties -notmatch [regex]::Escape($option)) {
        throw "Missing shader option in shaders.properties: $option"
    }
    if ($fragment -notmatch "#define\s+$([regex]::Escape($option))\b") {
        throw "Missing fallback define in final.fsh: $option"
    }
}

$requiredSymbols = @(
    "applyCameraExposure",
    "whiteBalanceTint",
    "applyWhiteBalance",
    "applyHighlightRolloff",
    "applyContrast",
    "applyFilmGrain",
    "applyVignette",
    "sampleShutterSmear"
)

foreach ($symbol in $requiredSymbols) {
    if ($fragment -notmatch "$([regex]::Escape($symbol))\s*\(") {
        throw "Missing fragment shader function: $symbol"
    }
}

if ($vertex -notmatch "varying\s+vec2\s+texcoord") {
    throw "final.vsh must pass texcoord to final.fsh"
}

Write-Host "TobysRealCamera shaderpack validation passed."
```

- [ ] **Step 2: Run validator to verify it fails**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1
```

Expected: FAIL because required shaderpack files are missing.

### Task 2: Shaderpack Metadata and Documentation

**Files:**
- Create: `shaderpacks/TobysRealCamera/README.md`
- Create: `shaderpacks/TobysRealCamera/shaders/shaders.properties`

- [ ] **Step 1: Add user documentation**

Create `README.md` with install steps, supported Iris options, defaults, and the note that depth of field is intentionally not part of version one.

- [ ] **Step 2: Add Iris option metadata**

Create `shaders.properties` with a single Camera screen, slider declarations for all numeric options, and named profiles for neutral, bright, low-light, warm, and cool looks.

- [ ] **Step 3: Run validator to verify it still fails**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1
```

Expected: FAIL because `final.vsh` and `final.fsh` are still missing.

### Task 3: GLSL Final Pass

**Files:**
- Create: `shaderpacks/TobysRealCamera/shaders/final.vsh`
- Create: `shaderpacks/TobysRealCamera/shaders/final.fsh`

- [ ] **Step 1: Add full-screen vertex shader**

Use GLSL 1.20, pass `texcoord`, and use `ftransform()` for compatibility with Iris/OptiFine-style full-screen passes.

- [ ] **Step 2: Add fragment shader fallback options**

Define all camera controls with conservative defaults matching the design:

```glsl
#define APERTURE_FSTOP 5.6
#define SHUTTER_SPEED 0.008
#define ISO_VALUE 100.0
#define EXPOSURE_COMP 0.0
#define WHITE_BALANCE_K 6500.0
#define CONTRAST 1.05
#define FILM_GRAIN 0.08
#define VIGNETTE 0.18
#define MOTION_BLUR_AMOUNT 0.0
```

- [ ] **Step 3: Implement processing functions**

Implement these functions in `final.fsh`: `applyCameraExposure`, `whiteBalanceTint`, `applyWhiteBalance`, `applyHighlightRolloff`, `applyContrast`, `applyFilmGrain`, `applyVignette`, and `sampleShutterSmear`.

- [ ] **Step 4: Run validator to verify it passes**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1
```

Expected: PASS with `TobysRealCamera shaderpack validation passed.`

### Task 4: Repository Verification

**Files:**
- Inspect: `shaderpacks/TobysRealCamera/**`
- Inspect: git status

- [ ] **Step 1: Confirm no mod source files changed**

Run:

```powershell
git status --short
```

Expected: only `shaderpacks/TobysRealCamera/**`, this plan, and unrelated pre-existing files appear.

- [ ] **Step 2: Review shaderpack file list**

Run:

```powershell
rg --files shaderpacks/TobysRealCamera
```

Expected:

```text
shaderpacks/TobysRealCamera/README.md
shaderpacks/TobysRealCamera/shaders/shaders.properties
shaderpacks/TobysRealCamera/shaders/final.vsh
shaderpacks/TobysRealCamera/shaders/final.fsh
shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1
```

- [ ] **Step 3: Final validation**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File shaderpacks/TobysRealCamera/tests/validate_shaderpack.ps1
```

Expected: PASS.
