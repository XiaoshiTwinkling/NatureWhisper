#version 150

uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform sampler3D uVox;
uniform sampler3D uEmission;

uniform float uFlipY;
uniform vec3 uCamPos;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamForward;
uniform float uTanFovX;
uniform float uTanFovY;
uniform float uNear;
uniform float uFar;
uniform vec3 uVoxOrigin;
uniform float uVoxSize;
uniform float uStrength;

in vec2 uv;

out vec4 fragColor;

float linearize(float d) {
    return (2.0 * uNear * uFar) / (uFar + uNear - (2.0 * d - 1.0) * (uFar - uNear));
}

vec3 worldPos(vec2 uv) {
    float z = linearize(texture(uDepth, uv).r);
    vec2 ndc = uv * 2.0 - 1.0;
    ndc.y *= uFlipY;
    return uCamPos + uCamForward * z
            + uCamRight * (ndc.x * uTanFovX * z)
            + uCamUp * (ndc.y * uTanFovY * z);
}

vec3 voxUv(vec3 world) {
    return (world - uVoxOrigin) / uVoxSize;
}

float occupancy(vec3 world) {
    vec3 v = voxUv(world);
    if (any(lessThan(v, vec3(0.0))) || any(greaterThan(v, vec3(1.0)))) {
        return 0.0;
    }
    return texture(uVox, v).a;
}

vec3 emissionAt(vec3 world) {
    vec3 v = voxUv(world);
    if (any(lessThan(v, vec3(0.0))) || any(greaterThan(v, vec3(1.0)))) {
        return vec3(0.0);
    }
    return texture(uEmission, v).rgb;
}

void main() {
    vec3 col = texture(uScene, uv).rgb;
    vec3 wp = worldPos(uv);

    // Mild occlusion from solids directly above (under overhangs / in corners), no full-image darkening.
    float occ = 0.0;
    for (int i = 1; i <= 4; i++) {
        occ += occupancy(wp + vec3(0.0, float(i) * 1.0, 0.0)) * (0.16 / float(i));
    }
    occ = clamp(occ, 0.0, 0.8);
    col *= (1.0 - occ * 0.35);

    // Gentle indirect glow from nearby emissive voxels; purely additive so it can't darken anything.
    vec3 gi = emissionAt(wp);
    for (int i = 1; i <= 3; i++) {
        gi += emissionAt(wp + vec3(0.0, float(i) * 1.0, 0.0)) * (0.3 / float(i));
    }
    float luminance = dot(col, vec3(0.299, 0.587, 0.114));
    col += gi * uStrength * 0.5 * (1.0 - luminance * 0.85);

    fragColor = vec4(col, 1.0);
}
