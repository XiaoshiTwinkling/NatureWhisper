// gbuffers_skybasic.glsl — NatureWhisper sky dome. The vanilla sky box geometry is drawn with its
// own per-vertex gradient; we ignore it and instead rebuild the view ray per pixel and colour it
// with the mod's SkyPalette gradient so the dome matches the non-Iris look.
//
// Day/night is driven by Iris's timeAngle-derived sun direction (same source Iris uses to draw the
// sun quad), so the dome never says "night" while the sun is still in the sky.

#ifdef OVERWORLD
#extension GL_ARB_texture_rectangle : enable
#endif

uniform int isEyeInWater;
uniform int renderStage;

uniform float timeAngle;
uniform float rainStrength;
uniform float viewWidth, viewHeight;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjectionInverse;

uniform vec3 cameraPosition;

#include "/lib/nw_celestial.glsl"
#include "/lib/nw_palette.glsl"

#ifdef VSH
void main() {
    gl_Position = ftransform();
}
#endif

#ifdef FSH
// Rebuild the per-pixel view direction (unit, in view space) from the inverse projection.
vec3 nwViewDir() {
    vec4 p = gbufferProjectionInverse * vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    return normalize(p.xyz / p.w);
}

void main() {
    // Sun direction in view space from Iris's own clock, then world space for the altitude.
    vec3 sunView = nwSunDirView(timeAngle, gbufferModelView);
    vec3 sunWorld = nwToWorldDir(sunView, gbufferModelViewInverse);
    float sunAlt = nwAltitudeDeg(sunWorld);

    // View direction in world space.
    vec3 viewW = normalize((gbufferModelViewInverse * vec4(nwViewDir(), 0.0)).xyz);
    float elevation = degrees(asin(clamp(viewW.y, -1.0, 1.0)));

    // Sky dome gradient: zenith is the (day/night-varying) sky colour, horizon is the warm band.
    // Mirrors the Java drawGradientSky: colour = lerp(horizon, zenith, elevation/90).
    vec3 zenith = nwSkyColor(sunAlt);
    vec3 horizonWarm = vec3(0.84, 0.80, 0.72);
    vec3 col = mix(horizonWarm, zenith, clamp((elevation + 5.0) / 35.0, 0.0, 1.0));

    // Warm sun halo toward the low sun (mirrors the Java sun-glow term).
    float dotSun = clamp(dot(viewW, sunWorld), 0.0, 1.0);
    float warm = pow(dotSun, 6.0) * nwSkyBrightness(sunAlt) * 0.35;
    col += vec3(0.85, 0.55, 0.25) * warm;

    // Horizon fades below the horizon.
    float below = clamp((-elevation - 2.0) / 12.0, 0.0, 1.0);
    col = mix(col, vec3(0.03, 0.04, 0.08), below * 0.6);

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, 1.0);
}
#endif
