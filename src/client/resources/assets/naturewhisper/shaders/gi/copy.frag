#version 150

uniform sampler2D uScene;

in vec2 uv;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(uScene, uv).rgb, 1.0);
}
