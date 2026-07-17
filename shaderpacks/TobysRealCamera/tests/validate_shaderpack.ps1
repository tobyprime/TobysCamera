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
