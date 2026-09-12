package com.helium.mixin.render;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(targets = "com.helium.rentities.entities.EntityMeshBaker")
public abstract class EntityMeshBakerBoneAliasMixin {
    @Shadow @Final private static Map<String, Integer> QUADRUPED_BONES;
    @Shadow @Final private static Map<String, Integer> BIRD_BONES;
    @Shadow @Final private static Map<String, Integer> ARTHROPOD_BONES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void helium$addSpecialAliases(CallbackInfo ci) {
        // FrogEntityModel uses camelCase child keys.
        QUADRUPED_BONES.put("leftArm", 2);
        QUADRUPED_BONES.put("rightArm", 3);
        QUADRUPED_BONES.put("leftLeg", 4);
        QUADRUPED_BONES.put("rightLeg", 5);

        // BatEntityModel has separate wing-tip bones; keep them independently
        // animatable instead of collapsing the whole wing into one bone.
        BIRD_BONES.put("left_wing_tip", 6);
        BIRD_BONES.put("right_wing_tip", 7);

        // SpiderEntityModel uses front/hind names plus distinct middle-hind keys.
        ARTHROPOD_BONES.put("right_front_leg", 2);
        ARTHROPOD_BONES.put("left_front_leg", 3);
        ARTHROPOD_BONES.put("right_hind_leg", 6);
        ARTHROPOD_BONES.put("left_hind_leg", 7);
        ARTHROPOD_BONES.put("right_middle_hind_leg", 6);
        ARTHROPOD_BONES.put("left_middle_hind_leg", 7);
    }
}
