#version 460 core

const int FLAG_SLIME = 1024;
const int FLAG_MAGMA_CUBE = 2;


in vec2      vTexCoord;
in flat int  vFlags;
in vec3      vNormal;
in float     vHurtAlpha;
in float     vGlintAnim;
in flat int  vPackedLight;
in flat int  vMaterialFlags;

out vec4 fragColor;

// Minecraft entity texture bound directly per draw call — no atlas
uniform sampler2D uEntityTexture;
uniform int uSlimeOverlay;
uniform sampler2D uLightMap;
uniform int uHasLightMap;

#define FLAG_IS_INVISIBLE 64
#define FLAG_HAS_GLINT    4

void main() {
    if ((vFlags & FLAG_IS_INVISIBLE) != 0) discard;

    vec4 tex = texture(uEntityTexture, vTexCoord);

    // Discard fully transparent pixels (entity textures have alpha cutouts)
    if (tex.a < 0.05) discard;

    float blockLight = float((vPackedLight >> 4) & 15) / 15.0;
    float skyLight = float((vPackedLight >> 20) & 15) / 15.0;
    vec3 color;
    if (uHasLightMap != 0) {
        // Sample Minecraft's own lightmap texture -- same gamma curve and warm/cool
        // color tinting vanilla uses, instead of an approximate flat multiplier.
        vec3 tint = texture(uLightMap, vec2(blockLight, skyLight)).rgb;
        color = tex.rgb * tint;
    } else {
        // Fallback if the vanilla light texture couldn't be bound this session.
        float light = max(blockLight, skyLight);
        color = tex.rgb * light;
    }

    // Hurt flash (red overlay)
    if (vHurtAlpha > 0.0) {
        color = mix(color, vec3(1.0, 0.0, 0.0), vHurtAlpha);
    }

    // Enchantment glint
    if ((vFlags & FLAG_HAS_GLINT) != 0) {
        float glint = sin(vTexCoord.x * 20.0 + vGlintAnim) * 0.5 + 0.5;
        color += vec3(0.5, 0.2, 0.8) * glint * 0.3;
    }

    float alpha = tex.a;
    if (uSlimeOverlay != 0 && (vMaterialFlags & FLAG_SLIME) != 0) {
        // Vanilla has a translucent outer slime shell around its opaque inner model.
        alpha *= 0.65;
    }
    fragColor = vec4(color, alpha);
}
