// nw_celestial.glsl — GLSL port of NatureWhisper's com.xiaoshi.sky.Celestial model.
//
// World conventions: +X east, +Y up, +Z south (north = -Z). Geographic latitude maps to world Z:
// world Z = 0 sits on the Tropic of Cancer (23.5 N); walking north (-Z) raises latitude.
// Epoch: time-of-day 0 = day 0 = spring equinox dawn.
//
// DAY/NIGHT CLOCK: Iris draws the sun/moon quads and drives sunVec from its own day cycle
// (timeAngle/sunAngle). To keep our sky dome and the drawn sun/moon in agreement, the day/night gate
// here is derived from Iris's timeAngle exactly like BSL — NOT from a re-computed frameTimeCounter
// clock. Celestial latitude/season remain available for colour shifts but the sun direction (and
// therefore which body is up) follows Iris so the sky can never say "night" while the sun is drawn.

#ifndef NW_CELESTIAL_GLSL
#define NW_CELESTIAL_GLSL

#define NW_PI           3.141592653589793
#define NW_DEG2RAD      0.017453292519943295
#define NW_TROPIC       23.5
#define NW_OBLIQUITY    23.44
#define NW_TICKS_DAY    24000.0
#define NW_YEAR_DAYS    120.0
#define NW_MOON_PERIOD  27.3
#define NW_BLOCKS_PER_DEG 100000.0

// NatureWhisper's sun path uses world -Z north with no extra tilt.
#define NW_SUN_PATH_ROTATION 0.0

// Sun direction in VIEW space from Iris's timeAngle (0..1 fraction of the vanilla day), matching how
// BSL derives sunVec so it always agrees with the drawn sun quad.
vec3 nwSunDirView(float timeAngle, mat4 gbufferModelView) {
    const vec2 sunRotationData = vec2(cos(NW_SUN_PATH_ROTATION * NW_DEG2RAD), -sin(NW_SUN_PATH_ROTATION * NW_DEG2RAD));
    float ang = fract(timeAngle - 0.25);
    ang = (ang + (cos(ang * NW_PI) * -0.5 + 0.5 - ang) / 3.0) * 2.0 * NW_PI;
    return normalize((gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotationData) * 2000.0, 1.0)).xyz);
}

// Convert a view-space direction to world space (unit).
vec3 nwToWorldDir(vec3 dirView, mat4 gbufferModelViewInverse) {
    return normalize((gbufferModelViewInverse * vec4(dirView, 0.0)).xyz);
}

// World-space altitude (degrees) of a world-space unit direction: +Y is up.
float nwAltitudeDeg(vec3 dirWorld) {
    return degrees(asin(clamp(dirWorld.y, -1.0, 1.0)));
}

// Geographic latitude (radians) for a world Z coordinate (colour-shift use only).
float nwLatitudeRad(vec3 cameraPos) {
    float lat = clamp(NW_TROPIC - cameraPos.z / NW_BLOCKS_PER_DEG, -90.0, 90.0);
    return lat * NW_DEG2RAD;
}

#endif
