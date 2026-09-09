// nw_palette.glsl — self-contained GLSL port of NatureWhisper's com.xiaoshi.sky.SkyPalette.
// Seasonal sky colour/brightness driven by the real solar altitude, matching the Java side.
//
// The mod's "darker nights" preset (darkerNights + nightDarkness) is frozen on for M1.

#ifndef NW_PALETTE_GLSL
#define NW_PALETTE_GLSL

#define NW_NIGHT_DARKNESS 1.0

vec3 nwLerp(vec3 a, vec3 b, float t) {
    return mix(a, b, clamp(t, 0.0, 1.0));
}

// Sky top colour for a given sun altitude (degrees). Mirrors SkyPalette.skyColor.
vec3 nwSkyColor(float sunAltDeg) {
    float day = clamp((sunAltDeg + 8.0) / 30.0, 0.0, 1.0);
    float dusk = exp(-abs(sunAltDeg) * 0.25);
    vec3 dayTop = vec3(0.45, 0.66, 0.84);
    vec3 nightTop = vec3(0.02, 0.035, 0.09);
    vec3 horizon = vec3(0.86, 0.78, 0.66);
    vec3 base = nwLerp(dayTop, nightTop, 1.0 - day);
    vec3 out_ = nwLerp(base, horizon, dusk * 0.45);
    float scale = 1.0 - NW_NIGHT_DARKNESS * 0.92 * (1.0 - day);
    return out_ * scale;
}

// 0 (night) .. 1 (full day); skylight / fog day factor. Mirrors SkyPalette.skyBrightness.
float nwSkyBrightness(float sunAltDeg) {
    float brightness = clamp((sunAltDeg + 8.0) / 32.0, 0.0, 1.0);
    brightness *= 1.0 - 0.78 * NW_NIGHT_DARKNESS * (1.0 - brightness);
    return brightness;
}

// 0 (day/twilight) .. 1 (fully dark night); stars appear once it is really dark.
float nwStarBrightness(float sunAltDeg) {
    return clamp((-sunAltDeg - 6.0) / 12.0, 0.0, 1.0);
}

#endif
