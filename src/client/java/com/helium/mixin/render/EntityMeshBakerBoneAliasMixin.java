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
    @Shadow @Final private static Map<String, Integer> INSECT_BONES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void helium$addSpecialAliases(CallbackInfo ci) {
        // FrogEntityModel uses camelCase child keys and has an independently animated tongue.
        QUADRUPED_BONES.put("leftArm", 2);
        QUADRUPED_BONES.put("rightArm", 3);
        QUADRUPED_BONES.put("leftLeg", 4);
        QUADRUPED_BONES.put("rightLeg", 5);
        QUADRUPED_BONES.put("tongue", 6);

        // BatEntityModel has separate wing-tip bones; keep them independently animatable.
        BIRD_BONES.put("left_wing_tip", 6);
        BIRD_BONES.put("right_wing_tip", 7);

        // PhantomEntityModel is a wing-base/wing-tip + tail-base/tail-tip rig.
        BIRD_BONES.put("left_wing_base", 2);
        BIRD_BONES.put("right_wing_base", 3);
        BIRD_BONES.put("tail_base", 4);
        BIRD_BONES.put("tail_tip", 5);

        // SpiderEntityModel uses front/hind names plus distinct middle-hind keys.
        ARTHROPOD_BONES.put("right_front_leg", 2);
        ARTHROPOD_BONES.put("left_front_leg", 3);
        ARTHROPOD_BONES.put("right_hind_leg", 6);
        ARTHROPOD_BONES.put("left_hind_leg", 7);
        ARTHROPOD_BONES.put("right_middle_hind_leg", 6);
        ARTHROPOD_BONES.put("left_middle_hind_leg", 7);

        // BeeEntityModel has three separately posed leg groups. Do not collapse
        // middle/back legs into the front-leg bone.
        INSECT_BONES.put("front_legs", 3);
        INSECT_BONES.put("middle_legs", 4);
        INSECT_BONES.put("back_legs", 5);
    }
}
