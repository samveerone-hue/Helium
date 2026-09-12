package com.helium.mixin.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import com.helium.rentities.entities.EntityAnimationCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.modify.ModifyConstant;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Mixin(targets = "com.helium.rentities.entities.EntityMeshBaker")
public abstract class EntityMeshBakerBoneAliasMixin {
    @Shadow @Final private static Map<String, Integer> BIPED_BONES;
    @Shadow @Final private static Map<String, Integer> QUADRUPED_BONES;
    @Shadow @Final private static Map<String, Integer> HORSE_BONES;
    @Shadow @Final private static Map<String, Integer> BIRD_BONES;
    @Shadow @Final private static Map<String, Integer> ARTHROPOD_BONES;
    @Shadow @Final private static Map<String, Integer> INSECT_BONES;
    @Shadow @Final private static Map<String, Integer> WORM_BONES;
    @Shadow @Final private static Map<String, Integer> FISH_BONES;
    @Shadow @Final private static Map<String, Integer> SLIME_BONES;
    @Shadow @Final private static Map<String, Integer> GHAST_BONES;
    @Shadow @Final private static Map<String, Integer> CREEPER_BONES;

    @Shadow private float[] bonePivotData;
    @Shadow private boolean[] bonePivotWritten;
    @Shadow private int currentBakingTypeIdx;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void helium$addSpecialAliases(CallbackInfo ci) {
        // Quadruped tails are independent ModelParts on many vanilla animals.
        QUADRUPED_BONES.put("tail", 6);

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

    /**
     * The mesh bake already records the final pivot position for every logical
     * bone. This second pass records the actual ModelPart parent relationship in
     * the pivot vec4's spare W component:
     *   w = -1 for a root-level bone, otherwise parentBone + 1.
     *
     * This is per entity type, so models such as Phantom, Frog, Fox and Bee can
     * retain their real hierarchy without assuming that every "leg" is a child
     * of the body. Standard biped/quadruped parts remain root siblings exactly
     * as vanilla defines them.
     */
    @Inject(method = "extractFromLivingRenderer", at = @At("RETURN"), require = 1)
    private void helium$recordBoneParents(LivingEntityRenderer renderer,
                                           EntityAnimationCategory category,
                                           Object consumer,
                                           Object poseStack,
                                           CallbackInfoReturnable<float[]> cir) {
        if (cir.getReturnValue() == null || cir.getReturnValue().length == 0) return;
        if (renderer == null || category == EntityAnimationCategory.CPU_ANIMATED) return;
        if (currentBakingTypeIdx < 0 || bonePivotData == null) return;

        try {
            EntityModel<?> model = renderer.getModel();
            if (model == null) return;
            ModelPart root = model.getRootPart();
            if (root == null) return;

            Map<String, Integer> boneMap = helium$boneMap(category);
            if (boneMap == null) return;
            helium$walkHierarchy(root, boneMap, -1, true);
        } catch (Throwable ignored) {
            // Hierarchy metadata is an optimization/correctness refinement; leave
            // the already-valid pivot rotations intact if reflection/mappings vary.
        }
    }

    private void helium$walkHierarchy(ModelPart part,
                                      Map<String, Integer> boneMap,
                                      int inheritedBone,
                                      boolean root) {
        Field childrenField = helium$childrenField();
        if (childrenField == null) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, ModelPart> children = (Map<String, ModelPart>) childrenField.get(part);
            if (children == null) return;

            for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
                int bone = boneMap.getOrDefault(entry.getKey(), inheritedBone);
                int parent = (!root && bone >= 0 && bone != inheritedBone && inheritedBone >= 0)
                        ? inheritedBone : -1;

                if (bone >= 0 && bone < 10) {
                    int pivotIndex = currentBakingTypeIdx * 10 + bone;
                    int base = pivotIndex * 4;
                    if (base >= 0 && base + 3 < bonePivotData.length) {
                        bonePivotData[base + 3] = parent >= 0 ? parent + 1.0f : -1.0f;
                    }
                }

                helium$walkHierarchy(entry.getValue(), boneMap, bone, false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static volatile Field HELIUM_CHILDREN_FIELD;

    private static Field helium$childrenField() {
        Field cached = HELIUM_CHILDREN_FIELD;
        if (cached != null) return cached;
        for (String name : new String[]{"children", "field_3661", "n"}) {
            try {
                Field f = ModelPart.class.getDeclaredField(name);
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    HELIUM_CHILDREN_FIELD = f;
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        for (Field f : ModelPart.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                HELIUM_CHILDREN_FIELD = f;
                return f;
            }
        }
        return null;
    }

    private static Map<String, Integer> helium$boneMap(EntityAnimationCategory category) {
        return switch (category) {
            case BIPED, FLOATING, FLOATING_SPINNING, SHULKER, STRIDER -> BIPED_BONES;
            case QUADRUPED, GOAT, SNIFFER, ARMADILLO, AQUATIC_LEGS, SWIMMING, FROG -> QUADRUPED_BONES;
            case HORSE -> HORSE_BONES;
            case BIRD -> BIRD_BONES;
            case ARTHROPOD -> ARTHROPOD_BONES;
            case INSECT -> INSECT_BONES;
            case WORM -> WORM_BONES;
            case FISH -> FISH_BONES;
            case SLIME -> SLIME_BONES;
            case GHAST -> GHAST_BONES;
            case CREEPER -> CREEPER_BONES;
            default -> null;
        };
    }

    /** Keep the on-disk mesh format compatible with the new parent metadata. */
    @ModifyConstant(method = "saveToCacheInternal", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 7))
    private static int helium$cacheWriteVersion(int original) {
        return 8;
    }

    @ModifyConstant(method = "loadFromCache", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 7))
    private static int helium$cacheReadVersion(int original) {
        return 8;
    }
}
