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
            case BIPED, QUADRUPED, BIRD, CREEPER,
                 FLOATING, FLOATING_SPINNING, SHULKER, STRIDER,
                 AQUATIC_LEGS, SWIMMING, FROG, GOAT, SNIFFER, ARMADILLO -> 6;
            case HORSE -> 7;
            case ARTHROPOD -> 8;
            case INSECT -> 4;
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
                for (int bone = 0; bone < requiredBones; bone++) {
                    parts[bone] = findPart(root, candidates[bone]);
                    if (parts[bone] == null) return null;
                }
                PoseBinding binding = new PoseBinding(category, stateClass, setAngles, parts);
                BINDINGS.put(model, binding);
                return binding;
            } catch (Throwable ignored) { return null; }
        }
    }

    private static String[][] candidates(EntityAnimationCategory category) {
        return switch (category) {
            case BIPED, FLOATING, FLOATING_SPINNING, SHULKER, STRIDER -> new String[][] {
                    {"head", "neck"}, {"body", "torso"}, {"left_arm", "leftArm", "left_wing"},
                    {"right_arm", "rightArm", "right_wing"}, {"left_leg", "leftLeg"}, {"right_leg", "rightLeg"}
            };
            case QUADRUPED, GOAT, SNIFFER, ARMADILLO, AQUATIC_LEGS, SWIMMING, FROG -> new String[][] {
                    {"head"}, {"body", "upper_body"},
                    {"left_front_leg", "left_arm", "leftArm", "leg1"}, {"right_front_leg", "right_arm", "rightArm", "leg2"},
                    {"left_hind_leg", "left_leg", "leftLeg", "leg3"}, {"right_hind_leg", "right_leg", "rightLeg", "leg4"}
            };
            case HORSE -> new String[][] {
                    {"head"}, {"body", "upper_body"}, {"front_left_leg", "left_front_leg"}, {"front_right_leg", "right_front_leg"},
                    {"back_left_leg", "left_hind_leg"}, {"back_right_leg", "right_hind_leg"}, {"tail"}
            };
            case BIRD -> new String[][] {{"head"}, {"body"}, {"left_wing"}, {"right_wing"}, {"left_leg"}, {"right_leg"}};
            case ARTHROPOD -> new String[][] {
                    {"head"}, {"body"}, {"right_middle_front_leg"}, {"left_middle_front_leg"}, {"right_middle_leg"}, {"left_middle_leg"},
                    {"right_back_leg", "right_middle_hind_leg"}, {"left_back_leg", "left_middle_hind_leg"}
            };
            case INSECT -> new String[][] {{"body", "torso"}, {"right_wing"}, {"left_wing"}, {"front_legs", "middle_legs", "back_legs"}};
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
        for (String name : new String[]{"children", "field_3661", "n"}) {
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (Map.class.isAssignableFrom(f.getType())) { f.setAccessible(true); return f; }
                } catch (NoSuchFieldException ignored) {}
                c = c.getSuperclass();
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
