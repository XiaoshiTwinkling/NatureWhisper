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

    // Sky dome gradient, mirroring the Java drawGradientSky. The horizon band derives from the sky
    // colour (so it follows day/night); the whole sky dims at night. Below the horizon the colour
    // falls off smoothly from the horizon tone (no hard seam or black band at the boundary).
    vec3 zenith = nwSkyColor(sunAlt);
    float day = nwSkyBrightness(sunAlt);
    float night = 1.0 - day;
    vec3 horizon = mix(zenith, vec3(0.84, 0.80, 0.72), 0.55);
    vec3 belowHorizon = vec3(0.03, 0.04, 0.08);

    // Java's dome spans -8..90; clamp so nothing extrapolates past the modelled range.
    float elevCol = clamp(elevation, -8.0, 90.0);

    vec3 col;
    if (elevCol < 0.0) {
        // Smooth fall from the horizon tone at 0 deg down to the dark below-horizon tone at -8 deg.
        col = mix(horizon, belowHorizon, clamp((-elevCol) / 8.0, 0.0, 1.0));
    } else {
        col = mix(horizon, zenith, clamp(elevCol / 90.0, 0.0, 1.0));
    }
    // Dim toward night like Java: lerp each channel toward 25% of itself as night deepens.
    col = mix(col, col * 0.25, night * 0.85);

    // Warm glow toward the sun (direction-dependent, like Java): only near the sun's azimuth and low
    // elevation, scaled by day. Compute the sun azimuth from the world-space sun direction.
    float sunAz = degrees(atan(sunWorld.x, -sunWorld.z));
    float viewAz = degrees(atan(viewW.x, -viewW.z));
    float azDelta = abs(mod(viewAz - sunAz + 540.0, 360.0) - 180.0);
    float warm = exp(-azDelta / 30.0) * exp(-abs(elevCol) / 18.0) * day;
    col += vec3(0.6, 0.32, 0.06) * warm;

    // DIAGNOSTIC: tint by timeAngle so we can tell whether the uniform is actually reaching this
    // shader. If the sky hue shifts as in-game time passes, timeAngle flows and the bug is in the
    // sun-direction/palette math; if it stays fixed grey, the uniform is not being fed at all.
    // REMOVE after confirming.
    col = mix(col, vec3(0.2, 0.5, 1.0) * (0.3 + 0.7 * fract(timeAngle)), 0.8);

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, 1.0);
}
#endif
