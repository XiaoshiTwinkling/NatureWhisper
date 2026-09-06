#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float BlurAngle;
uniform float BlurRadius;

out vec4 fragColor;

// Directional smear along BlurAngle (degrees); BlurRadius is the blur length in texels.
void main() {
    float angle = radians(BlurAngle);
    vec2 dir = vec2(cos(angle), sin(angle));

    int radius = int(floor(BlurRadius));
    if (radius <= 0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    vec2 step = dir * (1.0 / InSize);
    vec3 acc = vec3(0.0);
    for (int i = -radius; i <= radius; i++) {
        vec2 uv = clamp(texCoord + step * float(i), 0.0, 1.0);
        acc += texture(DiffuseSampler, uv).rgb;
    }
    fragColor = vec4(acc / float(radius * 2 + 1), 1.0);
}
