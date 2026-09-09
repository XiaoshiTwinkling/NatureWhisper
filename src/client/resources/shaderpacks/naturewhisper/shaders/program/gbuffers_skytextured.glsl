// gbuffers_skytextured.glsl — vanilla sun/moon sprite pass. Iris draws the vanilla sun/moon quads
// and passes the current body in `renderStage`; we sample the ORIGINAL vanilla texture (so the sun/
// moon look is the vanilla sprite — goal 1) and tint it with the NatureWhisper palette using the
// SAME timeAngle-derived sun direction that positions the quad, so dimming always matches the sky.
// Nether / End pass through unchanged.

uniform sampler2D texture;

uniform int renderStage;

uniform float timeAngle;
uniform float rainStrength;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;

uniform vec3 cameraPosition;

varying vec2 texCoord;

#include "/lib/nw_celestial.glsl"
#include "/lib/nw_palette.glsl"

#ifndef MC_RENDER_STAGE_SUN
#define MC_RENDER_STAGE_SUN 1
#endif
#ifndef MC_RENDER_STAGE_MOON
#define MC_RENDER_STAGE_MOON 1
#endif

#ifdef VSH
void main() {
    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    gl_Position = ftransform();
}
#endif

#ifdef FSH
void main() {
    vec4 albedo = texture2D(texture, texCoord);

    #ifdef OVERWORLD
    vec3 sunView = nwSunDirView(timeAngle, gbufferModelView);
    vec3 sunWorld = nwToWorldDir(sunView, gbufferModelViewInverse);
    float sunAlt = nwAltitudeDeg(sunWorld);

    float cloudDim = 1.0 - rainStrength * 0.8;

    if (renderStage == MC_RENDER_STAGE_SUN) {
        // Sun: fade with the real (Iris) sun altitude so a setting sun dims; full when high.
        float dayFade = clamp((sunAlt + 4.0) / 12.0, 0.0, 1.0);
        albedo.rgb *= cloudDim * mix(0.5, 1.15, dayFade);
        albedo.a = clamp(albedo.a * mix(0.5, 0.95, dayFade), 0.0, 1.0);
    } else if (renderStage == MC_RENDER_STAGE_MOON) {
        // Moon: sun/moon quads are added (effective brightness = rgb × alpha), and our night sky is
        // deliberately dark, so a full-alpha white moon would glare like a spotlight. Keep it soft:
        // lower both rgb and alpha; brighten only as the night deepens.
        float night = clamp((-sunAlt - 2.0) / 16.0, 0.0, 1.0);
        float moonLevel = mix(0.18, 0.55, night);
        albedo.rgb *= cloudDim * 0.75 * moonLevel;
        albedo.a *= moonLevel;
    } else {
        // Any other textured sky: keep vanilla-ish, dimmed at night.
        albedo.rgb *= 0.4 + 0.6 * nwSkyBrightness(sunAlt);
    }
    #endif

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = albedo;
}
#endif
