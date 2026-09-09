#version 150

// GPU sun shadow map generator. Each output texel is one sun-parallel ray through the voxel cube;
// marching outward along the sun we record the FIRST (most-sunward) SOLID surface depth and the
// cumulative LEAF optical depth. Because this is a true per-ray first-surface test, a sun-facing
// vertical wall stores its own (shallow) depth and reads as lit — the per-column CPU bake could not
// do that and produced "shadow growing up a wall". Re-running every frame makes the shadow track the
// sun smoothly instead of snapping on a coarse re-bake timer.
//
// Layout matches rt.frag: R = solid surface depth along the sun from the cube centre (empty column =
// large negative => lit), G = accumulated leaf optical depth. Output to an RG16F target sampled by
// rt.frag as uShadow.

uniform sampler3D uVox;

uniform vec3 uSunDir;
uniform vec3 uSunT;
uniform vec3 uSunB;
uniform vec3 uShadowCentre;
uniform vec3 uVoxOrigin;
uniform float uVoxSize;
uniform float uShadowHalf;

in vec2 uv;

out vec4 fragColor;

const float STEP = 0.6;
// March range must exceed the voxel cube diagonal: a ±32 cube spans up to 64·√3 ≈ 111 blocks, so
// 0.6·190 ≈ 114 keeps the far corner reachable even at grazing sun angles.
const int STEPS = 190;

// Same voxel-texel convention as rt.frag: buffer index = y*size*size + x*size + z (z fastest), so
// world (x,y,z) maps to texcoords (s,t,r) = (z,x,y). Snap to the voxel centre for NEAREST.
float occupancy(vec3 world) {
    vec3 v = (world - uVoxOrigin) / uVoxSize;
    if (any(lessThan(v, vec3(0.0))) || any(greaterThan(v, vec3(1.0)))) {
        return 0.0;
    }
    vec3 snapped = (floor(v * uVoxSize) + 0.5) / uVoxSize;
    return texture(uVox, snapped.zxy).a;
}

// Ray / AABB slab test: bounds the sun-ray parameter t (blocks along uSunDir from uShadowCentre) to
// where the ray actually crosses the cube, so sky/ocean columns don't march the whole 90-block range.
void cubeRange(vec3 ro, out float t0, out float t1) {
    t0 = -1.0e9;
    t1 = 1.0e9;
    vec3 lo = uVoxOrigin;
    vec3 hi = uVoxOrigin + vec3(uVoxSize);
    for (int a = 0; a < 3; a++) {
        float o = a == 0 ? ro.x : (a == 1 ? ro.y : ro.z);
        float d = a == 0 ? uSunDir.x : (a == 1 ? uSunDir.y : uSunDir.z);
        float mn = a == 0 ? lo.x : (a == 1 ? lo.y : lo.z);
        float mx = a == 0 ? hi.x : (a == 1 ? hi.y : hi.z);
        if (abs(d) < 1.0e-6) {
            if (o < mn || o > mx) {
                t1 = -1.0e9; // parallel and outside -> miss
            }
        } else {
            float ta = (mn - o) / d;
            float tb = (mx - o) / d;
            if (ta > tb) {
                float tmp = ta;
                ta = tb;
                tb = tmp;
            }
            t0 = max(t0, ta);
            t1 = min(t1, tb);
        }
    }
}

void main() {
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 ro = uShadowCentre + uSunT * (ndc.x * uShadowHalf) + uSunB * (ndc.y * uShadowHalf);

    float t0;
    float t1;
    cubeRange(ro, t0, t1);

    float solid = -10000.0; // empty column: no solid, so never occludes
    float leaf = 0.0;
    if (t1 > t0) {
        float t = t1;
        for (int i = 0; i < STEPS; i++) {
            if (t < t0) {
                break;
            }
            float occ = occupancy(ro + uSunDir * t);
            if (occ > 0.5) {
                solid = t;
                break;
            }
            leaf += occ * STEP;
            t -= STEP;
        }
    }

    fragColor = vec4(solid, leaf, 0.0, 1.0);
}
