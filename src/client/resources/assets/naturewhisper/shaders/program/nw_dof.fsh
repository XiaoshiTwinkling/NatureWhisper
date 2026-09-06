#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float Near;
uniform float Far;
uniform float Focus;
uniform float Strength;
uniform float NearBlur;

out vec4 fragColor;

// Turns the non-linear depth attachment value into a linear view-space distance.
float linearizeDepth(float d) {
    return (2.0 * Near * Far) / (Far + Near - (2.0 * d - 1.0) * (Far - Near));
}

void main() {
    vec3 sharp = texture(DiffuseSampler, texCoord).rgb;
    float viewZ = linearizeDepth(texture(DepthSampler, texCoord).r);
    if (viewZ <= Near || viewZ >= Far) {
        fragColor = vec4(sharp, 1.0);
        return;
    }

    // When aiming at something (NearBlur = 0) only pixels farther than the focus plane blur, so the
    // focused block and nearer content stay sharp. When looking far beyond the probe range
    // (NearBlur = 1) the far scene is sharp and only the near foreground gets a gentle blur.
    float cocFar = max(0.0, 1.0 / Focus - 1.0 / viewZ);
    float cocNear = max(0.0, 1.0 / viewZ - 1.0 / Focus);
    float coc = cocFar + NearBlur * 0.3 * cocNear;
    float radius = clamp(coc * Strength * 26.0, 0.0, 8.0);
    int ir = int(radius + 0.5);
    if (ir < 1) {
        fragColor = vec4(sharp, 1.0);
        return;
    }

    vec2 oneTexel = 1.0 / InSize;
    vec3 acc = sharp;
    int taps = 1;
    for (int k = 0; k < 12; k++) {
        float a = float(k) * 3.14159265 * 2.0 / 12.0;
        vec2 dir = vec2(cos(a), sin(a)) * oneTexel;
        vec2 inner = clamp(texCoord + dir * (radius * 0.55), 0.0, 1.0);
        vec2 outer = clamp(texCoord + dir * radius, 0.0, 1.0);
        acc += texture(DiffuseSampler, inner).rgb;
        acc += texture(DiffuseSampler, outer).rgb;
        taps += 2;
    }
    fragColor = vec4(acc / float(taps), 1.0);
}
