#version 460 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
layout(location = 3) in vec4 aColor;
layout(location = 4) in float aPackedLightBits;

uniform mat4 uViewProjection;

out vec2 vTexCoord;
out vec4 vColor;
out flat int vPackedLight;
out float vLightEmission;

void main() {
    vTexCoord = aTexCoord;
    vColor = aColor;
    vPackedLight = floatBitsToInt(aPackedLightBits);
    vLightEmission = 0.0;
    gl_Position = uViewProjection * vec4(aPosition, 1.0);
}
