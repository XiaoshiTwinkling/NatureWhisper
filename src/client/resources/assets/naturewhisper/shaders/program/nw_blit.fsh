#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

// Opaque copy; used as the final pass so the blur result fully replaces the main framebuffer
// regardless of any source alpha.
void main() {
    fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
}
