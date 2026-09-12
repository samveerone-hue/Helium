package com.helium.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the existing entity vertex shader at load time rather than replacing
 * the shader source. This preserves every existing special animation path while
 * adding four pose slots for the mesh baker's ten-bone ABI and a renderer-scale
 * flag used by state-specific renderers such as Creeper and Phantom.
 */
@Mixin(targets = "com.helium.rentities.entities.EntityBatchRenderer")
public abstract class EntityBatchRendererSpecialPoseShaderMixin {
    @Inject(method = "loadShader", at = @At("RETURN"), require = 0)
    private void helium$extendSpecialPoseShader(String path, CallbackInfoReturnable<String> cir) {
        if (path == null || !path.endsWith("entity/entity_vert.glsl")) return;

        String shader = cir.getReturnValue();
        if (shader == null || shader.contains("exactPose6")) return;

        String poseFields = "    vec4 armorStandRightLegPose;\n" +
                "    vec4 exactPose6;\n" +
                "    vec4 exactPose7;\n" +
                "    vec4 exactPose8;\n" +
                "    vec4 exactPose9;\n\n" +
                "    int packedLight;";
        String oldFields = "    vec4 armorStandRightLegPose;\n\n    int packedLight;";
        if (!shader.contains(oldFields)) return;

        String exactFunction = """

mat4 exactModelBone(int b, EntityInstance inst) {
    vec4 pose = inst.armorStandHeadPose;
    if (b == 1) pose = inst.armorStandBodyPose;
    else if (b == 2) pose = inst.armorStandLeftArmPose;
    else if (b == 3) pose = inst.armorStandRightArmPose;
    else if (b == 4) pose = inst.armorStandLeftLegPose;
    else if (b == 5) pose = inst.armorStandRightLegPose;
    else if (b == 6) pose = inst.exactPose6;
    else if (b == 7) pose = inst.exactPose7;
    else if (b == 8) pose = inst.exactPose8;
    else if (b == 9) pose = inst.exactPose9;
    return pivotRot(getP(inst, b), rotX(pose.x) * rotY(pose.y) * rotZ(pose.z));
}
""";

        String marker = "// --- BIPED / HUMANOID ---";
        String armorBranch = "if ((inst.flags & FLAG_ARMOR_STAND) != 0)\n        return getArmorStandBone(bone, inst);";
        if (!shader.contains(marker) || !shader.contains(armorBranch)) return;

        String scaledMain = """

    if ((inst.flags & 2048) != 0) {
        rotPos.xz *= max(inst.slimeScaleXZ, 0.01);
        rotPos.y *= max(inst.slimeScaleY, 0.01);
    }
""";
        String scaleAnchor = "    if ((inst.materialFlags & FLAG_SLIME) != 0) {";
        if (!shader.contains(scaleAnchor)) return;

        String poseBranch = """
    if ((inst.flags & 4096) != 0) {
        int c = inst.animationCategory;
        // Only use the exact-pose path for rigs that are currently represented
        // as flat independent bones. Hierarchical rigs stay on the mature
        // category-specific animation path until their parent transforms are
        // represented explicitly in the SSBO.
        if (c == ANIM_BIPED
                || c == ANIM_CREEPER
                || c == ANIM_ARTHROPOD
                || c == ANIM_INSECT
                || c == ANIM_FISH
                || c == ANIM_GHAST) {
            return exactModelBone(bone, inst);
        }
    }
""";

        String patched = shader
                .replace(oldFields, poseFields)
                .replace(marker, exactFunction + "\n" + marker)
                .replace(armorBranch, poseBranch + "\n        if ((inst.flags & FLAG_ARMOR_STAND) != 0)\n        return getArmorStandBone(bone, inst);")
                .replace(scaleAnchor, scaledMain + "\n" + scaleAnchor);

        // All-or-nothing patch: never return a half-modified shader. If one of the
        // expected source anchors changes in a future Rentities shader, vanilla's
        // original shader remains intact rather than failing at runtime.
        if (!patched.contains("vec4 exactPose9;")
                || !patched.contains("mat4 exactModelBone")
                || !patched.contains("return exactModelBone(bone, inst);")
                || !patched.contains("(inst.flags & 4096)")) {
            return;
        }

        cir.setReturnValue(patched);
    }
}
