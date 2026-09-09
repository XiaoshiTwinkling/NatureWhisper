#version 150

uniform sampler2D uDepth;
uniform sampler3D uVox;

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
uniform vec3 uSunDir;
uniform float uSunVis;
uniform vec3 uSunColor;

in vec2 uv;

out vec4 fragColor;

float linearize(float d) {
    return (2.0 * uNear * uFar) / (uFar + uNear - (2.0 * d - 1.0) * (uFar - uNear));
}

// Same voxel-texel convention as rt.frag: the buffer index is y*size*size + x*size + z (z fastest),
// so GL stores (s,t,r) = (z,x,y) and a world-space (x,y,z) is sampled as (s,t,r) = (v.z, v.x, v.y).
// Snap to the texel centre so NEAREST never lands on a boundary.
float occupancy(vec3 world) {
    vec3 v = (world - uVoxOrigin) / uVoxSize;
    if (any(lessThan(v, vec3(0.0))) || any(greaterThan(v, vec3(1.0)))) {
        return 0.0;
    }
    vec3 snapped = (floor(v * uVoxSize) + 0.5) / uVoxSize;
    return texture(uVox, snapped.zxy).a;
}

void main() {
    // Stop just before the first surface the camera sees; for sky march a bounded distance so only
    // short-range shafts near the player appear (the voxel grid is only ~24 blocks around him).
    float endZ = linearize(texture(uDepth, uv).r) - 0.6;
    if (endZ > uFar - 1.0) {
        endZ = 48.0;
    }
    vec3 eyeDir = normalize(uCamForward
            + uCamRight * ((uv.x * 2.0 - 1.0) * uTanFovX)
            + uCamUp * (((uv.y * 2.0 - 1.0) * uFlipY) * uTanFovY));

    // Forward scattering peaks toward the sun and falls off to the side.
    float phase = 0.35 + 0.65 * max(dot(eyeDir, uSunDir), 0.0);

    const int STEPS = 28;
    float stepLen = 0.85;
    vec3 sum = vec3(0.0);
    float transmit = 1.0;
    // Base air-scatter density per block. Kept low: sun-lit clear air in the sun's phase band is
    // added over the whole column toward the sun, so a large haze would wash the sun disk out.
    float haze = 0.010;
    float sunScale = uSunVis;

    for (int i = 0; i < STEPS; i++) {
        float t = 0.6 + float(i) * stepLen;
        if (t > endZ) {
            break;
        }
        vec3 pos = uCamPos + eyeDir * t;
        float occ = occupancy(pos);
        if (occ > 0.5) {
            break; // hit a wall/leaf in front of the shaft
        }
        // Sun reach at this sample: a handful of short taps toward the sun. Clear air stays lit;
        // under a full canopy the taps are blocked and nothing accumulates (that contrast is the shaft).
        float blocked = 0.0;
        for (int k = 1; k <= 5; k++) {
            blocked += occupancy(pos + uSunDir * (float(k) * 1.4));
        }
        float sunHere = clamp(1.0 - blocked / 5.0 * 1.8, 0.0, 1.0);

        float scatter = haze * sunScale * phase * sunHere * transmit * stepLen;
        sum += uSunColor * scatter;
        transmit *= (1.0 - occ * 0.9);
        if (transmit < 0.02) {
            break;
        }
    }

    // Cap so the accumulated sun haze never exceeds the source sun colour (would otherwise glare).
    fragColor = vec4(min(sum, uSunColor * 0.35), 1.0);
}
