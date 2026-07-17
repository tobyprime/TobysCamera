#version 120

#define APERTURE_FSTOP 5.6 // [1.4 2.0 2.8 4.0 5.6 8.0 11.0 16.0 22.0]
#define SHUTTER_SPEED 0.008 // [0.000125 0.00025 0.0005 0.001 0.002 0.004 0.008 0.016 0.033 0.066 0.125]
#define ISO_VALUE 100.0 // [25.0 50.0 100.0 200.0 400.0 800.0 1600.0 3200.0]
#define EXPOSURE_COMP 0.0 // [-3.0 -2.0 -1.0 -0.5 0.0 0.5 1.0 2.0 3.0]
#define WHITE_BALANCE_K 6500.0 // [2500.0 3200.0 4200.0 5600.0 6500.0 7500.0 9000.0 12000.0]
#define CONTRAST 1.05 // [0.75 0.9 1.0 1.05 1.15 1.3 1.5]
#define FILM_GRAIN 0.08 // [0.0 0.04 0.08 0.12 0.18 0.25 0.35]
#define VIGNETTE 0.18 // [0.0 0.08 0.12 0.18 0.24 0.32 0.45]
#define MOTION_BLUR_AMOUNT 0.0 // [0.0 0.05 0.12 0.25 0.4 0.65 1.0]

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;

varying vec2 texcoord;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec3 saturate(vec3 value) {
    return clamp(value, vec3(0.0), vec3(1.0));
}

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec3 applyCameraExposure(vec3 color) {
    float aperture = max(float(APERTURE_FSTOP), 0.7);
    float shutter = max(float(SHUTTER_SPEED), 0.000125);
    float iso = max(float(ISO_VALUE), 25.0);
    float exposureComp = clamp(float(EXPOSURE_COMP), -6.0, 6.0);

    float baseline = 0.008 / (5.6 * 5.6);
    float cameraExposure = (shutter * (iso / 100.0)) / (aperture * aperture);
    float exposure = clamp((cameraExposure / baseline) * pow(2.0, exposureComp), 0.02, 32.0);

    return color * exposure;
}

vec3 whiteBalanceTint(float kelvin) {
    float temperature = clamp(kelvin, 1000.0, 40000.0) / 100.0;
    float red;
    float green;
    float blue;

    if (temperature <= 66.0) {
        red = 255.0;
        green = 99.4708025861 * log(max(temperature, 1.0)) - 161.1195681661;
        if (temperature <= 19.0) {
            blue = 0.0;
        } else {
            blue = 138.5177312231 * log(max(temperature - 10.0, 1.0)) - 305.0447927307;
        }
    } else {
        red = 329.698727446 * pow(max(temperature - 60.0, 1.0), -0.1332047592);
        green = 288.1221695283 * pow(max(temperature - 60.0, 1.0), -0.0755148492);
        blue = 255.0;
    }

    return saturate(vec3(red, green, blue) / 255.0);
}

vec3 applyWhiteBalance(vec3 color) {
    vec3 selected = max(whiteBalanceTint(float(WHITE_BALANCE_K)), vec3(0.05));
    vec3 neutral = max(whiteBalanceTint(6500.0), vec3(0.05));
    vec3 correction = neutral / selected;
    return color * correction;
}

vec3 applyHighlightRolloff(vec3 color) {
    return color / (vec3(1.0) + color);
}

vec3 applyContrast(vec3 color) {
    float contrast = clamp(float(CONTRAST), 0.25, 2.5);
    return saturate((color - vec3(0.5)) * contrast + vec3(0.5));
}

float hashNoise(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 applyFilmGrain(vec3 color, vec2 uv) {
    float grainControl = clamp(float(FILM_GRAIN), 0.0, 1.0);
    float isoGain = clamp(log2(max(float(ISO_VALUE), 25.0) / 100.0 + 1.0), 0.0, 5.0);
    float amount = grainControl * (0.35 + isoGain * 0.18);
    float noise = hashNoise(uv * vec2(viewWidth, viewHeight) + frameTimeCounter * 41.0) - 0.5;
    float luminanceMask = mix(1.15, 0.55, saturate(luma(color)));
    return saturate(color + noise * amount * luminanceMask);
}

vec3 applyVignette(vec3 color, vec2 uv) {
    float amount = clamp(float(VIGNETTE), 0.0, 1.0);
    vec2 centered = uv * 2.0 - 1.0;
    centered.x *= max(viewWidth / max(viewHeight, 1.0), 1.0);
    float radius = dot(centered, centered);
    float vignette = 1.0 - amount * smoothstep(0.15, 1.65, radius);
    return color * vignette;
}

vec3 sampleShutterSmear(vec2 uv) {
    float shutter = clamp(float(SHUTTER_SPEED), 0.000125, 0.125);
    float blurControl = clamp(float(MOTION_BLUR_AMOUNT), 0.0, 1.0);
    float slowShutter = smoothstep(0.008, 0.066, shutter);
    float radius = blurControl * slowShutter * 3.0;
    vec2 texel = vec2(1.0 / max(viewWidth, 1.0), 1.0 / max(viewHeight, 1.0));
    vec2 direction = normalize(vec2(1.0, 0.35)) * texel * radius;

    vec3 color = texture2D(colortex0, uv).rgb * 0.40;
    color += texture2D(colortex0, clamp(uv + direction, 0.0, 1.0)).rgb * 0.20;
    color += texture2D(colortex0, clamp(uv - direction, 0.0, 1.0)).rgb * 0.20;
    color += texture2D(colortex0, clamp(uv + direction * 2.0, 0.0, 1.0)).rgb * 0.10;
    color += texture2D(colortex0, clamp(uv - direction * 2.0, 0.0, 1.0)).rgb * 0.10;
    return color;
}

void main() {
    vec3 color = sampleShutterSmear(texcoord);
    color = applyCameraExposure(color);
    color = applyWhiteBalance(color);
    color = applyHighlightRolloff(color);
    color = applyContrast(color);
    color = applyFilmGrain(color, texcoord);
    color = applyVignette(color, texcoord);

    gl_FragColor = vec4(saturate(color), 1.0);
}
