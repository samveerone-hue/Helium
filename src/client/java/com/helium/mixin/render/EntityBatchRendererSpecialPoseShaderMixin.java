package com.helium.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the existing entity vertex shader at load time rather than replacing
 * the shader source. This preserves every existing special animation path while
 * adding four pose slots for the mesh baker's ten-bone ABI.
 */
@Mixin(targets = "com.helium.rentities.entities.EntityBatchRenderer")
public abstract class EntityBatchRendererSpecialPoseShaderMixin {
    @Inject(method = "loadShader", at = @At("RETURN"), require = 0)
    private void helium$extendSpecialPoseShader(String path, CallbackInfoReturnable<String> cir) {
        if (path == null || !path.endsWith("entity/entity_vert.glsl")) return;

        String shader = cir.getReturnValue();
        if (shader == null || shader.contains("exactPose6")) return;

        shader = shader.replace(
                "    vec4 armorStandRightLegPose;\n\n    int packedLight;",
                "    vec4 armorStandRightLegPose;\n    vec4 exactPose6;\n    vec4 exactPose7;\n    vec4 exactPose8;\n    vec4 exactPose9;\n\n    int packedLight;");

        String exactFunction = """
\nmat4 exactModelBone(int b, EntityInstance inst) {
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

        shader = shader.replace(
                "// --- BIPED / HUMANOID ---",
                exactFunction + "// --- BIPED / HUMANOID ---");

        shader = shader.replace(
                "if ((inst.flags & FLAG_ARMOR_STAND) != 0)\n        return getArmorStandBone(bone, inst);",
                "if ((inst.flags & FLAG_ARMOR_STAND) != 0)\n        return exactModelBone(bone, inst);");

        cir.setReturnValue(shader);
    }
}
