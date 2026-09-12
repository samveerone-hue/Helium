#version 460 core

in vec2 vTexCoord;
in vec4 vColor;
in flat int vPackedLight;
in float vLightEmission;

uniform sampler2D uTexture;
uniform sampler2D uLightMap;
uniform int uHasLightMap;

void main() {
    vec4 tex = texture(uTexture, vTexCoord) * vColor;
    if (tex.a < 0.05) discard;

    float blockLight = float((vPackedLight >> 4) & 15) / 15.0;
    float skyLight = float((vPackedLight >> 20) & 15) / 15.0;
    vec3 color;
    if (uHasLightMap != 0) {
        vec3 tint = texture(uLightMap, vec2(blockLight, skyLight)).rgb;
        color = tex.rgb * tint;
    } else {
        float light = max(blockLight, skyLight);
        color = tex.rgb * light;
    }

    if (vLightEmission > 0.0) {
        color = mix(color, tex.rgb, clamp(vLightEmission, 0.0, 1.0));
    }
    fragColor = vec4(color, tex.a);
}
