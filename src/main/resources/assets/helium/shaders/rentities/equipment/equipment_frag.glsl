#version 460 core

in vec2 vTexCoord;
in vec4 vColor;
in float vLight;

uniform sampler2D uTexture;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uTexture, vTexCoord) * vColor;
    if (tex.a < 0.05) discard;
    fragColor = vec4(tex.rgb * vLight, tex.a);
}
