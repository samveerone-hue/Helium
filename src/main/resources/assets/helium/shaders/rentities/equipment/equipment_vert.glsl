#version 460 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
layout(location = 3) in vec4 aColor;
layout(location = 4) in float aPackedLightBits;

uniform mat4 uViewProjection;

out vec2 vTexCoord;
out vec4 vColor;
out float vLight;

void main() {
    int packedLight = floatBitsToInt(aPackedLightBits);
    float blockLight = float((packedLight >> 4) & 15) / 15.0;
    float skyLight = float((packedLight >> 20) & 15) / 15.0;
    vLight = max(blockLight, skyLight);
    vTexCoord = aTexCoord;
    vColor = aColor;
    gl_Position = uViewProjection * vec4(aPosition, 1.0);
}
