package com.helium.rentities.entities;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class EntityModelPoseExtractor {
    private static volatile Field childrenField;
    private static final Map<Model<?>, PoseBinding> BINDINGS = new WeakHashMap<>();
    private static final Object BINDING_LOCK = new Object();

    private EntityModelPoseExtractor() {}

    public static boolean writeExactPose(long ptr, Object state, EntityAnimationCategory category) {
        if (!(state instanceof EntityRenderState renderState)) return false;
        int requiredBones = requiredBoneCount(category);
        if (requiredBones <= 0) return false;

        try {
            EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
            if (dispatcher == null) return false;
            EntityRenderer<?, ?> renderer = dispatcher.getRenderer(renderState);
            if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return false;
            Model<?> model = livingRenderer.getModel();
            if (model == null) return false;
            PoseBinding binding = getBinding(model, renderState.getClass(), category, requiredBones);
            if (binding == null) return false;

            model.resetTransforms();
            try {
                binding.setAngles.invoke(model, renderState);
                for (int bone = 0; bone < 10; bone++) writePose(ptr + poseOffset(bone), 0.0f, 0.0f, 0.0f);
                for (int bone = 0; bone < requiredBones; bone++) {
                    ModelPart part = binding.parts[bone];
                    if (part == null) continue;
                    writePose(ptr + poseOffset(bone), part.pitch, part.yaw, part.roll);
                }
                return true;
            } finally {
                model.resetTransforms();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long poseOffset(int bone) { return EntityInstance.OFFSET_EXACT_POSE_0 + bone * 16L; }

    private static int requiredBoneCount(EntityAnimationCategory category) {
        return switch (category) {
            case BIPED, CREEPER,
                 FLOATING, FLOATING_SPINNING, SHULKER, STRIDER,
                 AQUATIC_LEGS, SWIMMING -> 6;
            case QUADRUPED, GOAT, SNIFFER, ARMADILLO -> 7; // tail slot when present
            case FROG -> 7;     // head/body/arms/legs + independently animated tongue
            case HORSE -> 7;
            case BIRD -> 8;
            case ARTHROPOD -> 8;
            case INSECT -> 6;   // body + 2 wings + 3 independently posed leg groups
            case WORM, SLIME -> 1;
            case FISH -> 2;
            case GHAST -> 10;
            default -> 0;
        };
    }

    private static PoseBinding getBinding(Model<?> model, Class<?> stateClass, EntityAnimationCategory category, int requiredBones) {
        synchronized (BINDING_LOCK) {
            PoseBinding existing = BINDINGS.get(model);
            if (existing != null && existing.category == category && existing.stateClass == stateClass && existing.parts.length == requiredBones) return existing;
            try {
                Method setAngles = findSetAngles(model.getClass(), stateClass);
                if (setAngles == null) return null;
                setAngles.setAccessible(true);
                ModelPart root = model.getRootPart();
                if (root == null) return null;
                String[][] candidates = candidates(category);
                ModelPart[] parts = new ModelPart[requiredBones];
                int found = 0;
                for (int bone = 0; bone < requiredBones; bone++) {
                    parts[bone] = findPart(root, candidates[bone]);
                    if (parts[bone] != null) found++;
                    if (parts[bone] == null && !optionalBone(category, bone)) {
                        // Bird and quadruped-family rigs intentionally allow sparse tails/tips.
                        if (category != EntityAnimationCategory.BIRD
                                && category != EntityAnimationCategory.QUADRUPED
                                && category != EntityAnimationCategory.GOAT
                                && category != EntityAnimationCategory.SNIFFER
                                && category != EntityAnimationCategory.ARMADILLO) return null;
                    }
                }
                int minimum = (category == EntityAnimationCategory.BIRD
                        || category == EntityAnimationCategory.QUADRUPED
                        || category == EntityAnimationCategory.GOAT
                        || category == EntityAnimationCategory.SNIFFER
                        || category == EntityAnimationCategory.ARMADILLO) ? 6 : requiredBones;
                if (found < minimum) return null;
                PoseBinding binding = new PoseBinding(category, stateClass, setAngles, parts);
                BINDINGS.put(model, binding);
                return binding;
            } catch (Throwable ignored) { return null; }
        }
    }

    private static boolean optionalBone(EntityAnimationCategory category, int bone) {
        if (category == EntityAnimationCategory.BIRD) return bone == 6 || bone == 7;
        return (category == EntityAnimationCategory.QUADRUPED
                || category == EntityAnimationCategory.GOAT
                || category == EntityAnimationCategory.SNIFFER
                || category == EntityAnimationCategory.ARMADILLO) && bone == 6;
    }

    private static String[][] candidates(EntityAnimationCategory category) {
        return switch (category) {
            case BIPED, FLOATING, FLOATING_SPINNING, SHULKER, STRIDER -> new String[][] {
                    {"head", "neck"}, {"body", "torso"}, {"left_arm", "leftArm", "left_wing"},
                    {"right_arm", "rightArm", "right_wing"}, {"left_leg", "leftLeg"}, {"right_leg", "rightLeg"}
            };
            case QUADRUPED, GOAT, SNIFFER, ARMADILLO, AQUATIC_LEGS, SWIMMING -> new String[][] {
                    {"head"}, {"body", "upper_body"},
                    {"left_front_leg", "left_arm", "leftArm", "leg1"}, {"right_front_leg", "right_arm", "rightArm", "leg2"},
                    {"left_hind_leg", "left_leg", "leftLeg", "leg3"}, {"right_hind_leg", "right_leg", "rightLeg", "leg4"}, {"tail"}
            };
            case FROG -> new String[][] {
                    {"head"}, {"body", "croaking_body"}, {"left_arm", "leftArm"}, {"right_arm", "rightArm"},
                    {"left_leg", "leftLeg"}, {"right_leg", "rightLeg"}, {"tongue"}
            };
            case HORSE -> new String[][] {
                    {"head"}, {"body", "upper_body"}, {"front_left_leg", "left_front_leg"}, {"front_right_leg", "right_front_leg"},
                    {"back_left_leg", "left_hind_leg"}, {"back_right_leg", "right_hind_leg"}, {"tail"}
            };
            case BIRD -> new String[][] {
                    {"head"}, {"body"},
                    {"left_wing", "left_wing_base"}, {"right_wing", "right_wing_base"},
                    {"left_leg", "tail_base"}, {"right_leg", "tail_tip"},
                    {"left_wing_tip"}, {"right_wing_tip"}
            };
            case ARTHROPOD -> new String[][] {
                    {"head"}, {"body"}, {"right_middle_front_leg", "right_front_leg"}, {"left_middle_front_leg", "left_front_leg"},
                    {"right_middle_leg"}, {"left_middle_leg"}, {"right_back_leg", "right_middle_hind_leg", "right_hind_leg"},
                    {"left_back_leg", "left_middle_hind_leg", "left_hind_leg"}
            };
            case INSECT -> new String[][] {
                    {"body", "torso"}, {"right_wing"}, {"left_wing"}, {"front_legs"}, {"middle_legs"}, {"back_legs"}
            };
            case WORM -> new String[][] {{"body", "segment"}};
            case FISH -> new String[][] {{"body"}, {"tail"}};
            case SLIME -> new String[][] {{"cube", "inside_cube"}};
            case GHAST -> new String[][] {{"body"}, {"tentacle0"}, {"tentacle1"}, {"tentacle2"}, {"tentacle3"}, {"tentacle4"}, {"tentacle5"}, {"tentacle6"}, {"tentacle7"}, {"tentacle8"}};
            case CREEPER -> new String[][] {{"head"}, {"body"}, {"leg1", "right_hind_leg"}, {"leg2", "left_hind_leg"}, {"leg3", "right_front_leg"}, {"leg4", "left_front_leg"}};
            default -> throw new IllegalArgumentException("Unsupported exact pose category: " + category);
        };
    }

    private static Method findSetAngles(Class<?> cls, Class<?> stateClass) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("setAngles") || m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0].isAssignableFrom(stateClass)) return m;
            }
        }
        return null;
    }

    private static ModelPart findPart(ModelPart root, String[] names) {
        for (String name : names) { ModelPart part = findPartRecursive(root, name); if (part != null) return part; }
        return null;
    }

    private static ModelPart findPartRecursive(ModelPart part, String wanted) {
        if (part == null) return null;
        try {
            Field field = childrenField;
            if (field == null) { field = findChildrenField(part.getClass()); if (field == null) return null; childrenField = field; }
            @SuppressWarnings("unchecked") Map<String, ModelPart> children = (Map<String, ModelPart>) field.get(part);
            if (children == null) return null;
            ModelPart direct = children.get(wanted);
            if (direct != null) return direct;
            for (ModelPart child : children.values()) { ModelPart found = findPartRecursive(child, wanted); if (found != null) return found; }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Field findChildrenField(Class<?> cls) {
        for (Field f : cls.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static void writePose(long ptr, float pitch, float yaw, float roll) {
        MemoryUtil.memPutFloat(ptr, pitch);
        MemoryUtil.memPutFloat(ptr + 4L, yaw);
        MemoryUtil.memPutFloat(ptr + 8L, roll);
        MemoryUtil.memPutFloat(ptr + 12L, 0.0f);
    }

    private record PoseBinding(EntityAnimationCategory category, Class<?> stateClass, Method setAngles, ModelPart[] parts) {}
}
