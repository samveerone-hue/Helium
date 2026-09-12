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

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void helium$addSpecialAliases(CallbackInfo ci) {
        // FrogEntityModel uses camelCase child keys while the generic quadruped
        // map primarily uses the snake_case keys shared by older models.
        QUADRUPED_BONES.put("leftArm", 2);
        QUADRUPED_BONES.put("rightArm", 3);
        QUADRUPED_BONES.put("leftLeg", 4);
        QUADRUPED_BONES.put("rightLeg", 5);
    }
}
