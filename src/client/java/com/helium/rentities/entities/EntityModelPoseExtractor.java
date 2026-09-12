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

/**
 * Extracts the rotations Minecraft's own model animation code produced for the
 * current render state and puts the six primary bone rotations into the Rentities
 * instance payload.
 *
 * This deliberately calls Model#setAngles(state) instead of recreating vanilla
 * animation formulas in the shader. The mesh is already baked in the same bone
 * coordinate space, so the GPU only needs the resulting Euler rotations.
 */
public final class EntityModelPoseExtractor {
    private static volatile Field childrenField;
    private static final Map<Model<?>, PoseBinding> BINDINGS = new WeakHashMap<>();
    private static final Object BINDING_LOCK = new Object();

    private EntityModelPoseExtractor() {}

    /**
     * Writes exact model rotations into the existing six pose slots. Returns true
     * only when all six slots were resolved, so unsupported model families keep their
     * existing category-specific animation path.
     */
    public static boolean writeExactPose(long ptr, Object state, EntityAnimationCategory category) {
        if (!(state instanceof EntityRenderState renderState)) return false;
        if (!supportsExactPose(category)) return false;

        try {
            EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
            if (dispatcher == null) return false;

            EntityRenderer<?, ?> renderer = dispatcher.getRenderer(renderState);
            if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return false;

            Model<?> model = livingRenderer.getModel();
            if (model == null) return false;

            PoseBinding binding = getBinding(model, renderState.getClass(), category);
            if (binding == null) return false;

            model.resetTransforms();
            try {
                binding.setAngles.invoke(model, renderState);
                for (int bone = 0; bone < 6; bone++) {
                    ModelPart part = binding.parts[bone];
                    writePose(ptr + EntityInstance.OFFSET_ARMOR_STAND_HEAD_POSE + bone * 16L,
                            part.xRot, part.yRot, part.zRot);
                }
                return true;
            } finally {
                // Entity models are shared renderer instances. Never leave the temporary
                // extracted pose behind for the next vanilla or batched draw.
                model.resetTransforms();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PoseBinding getBinding(Model<?> model, Class<?> stateClass, EntityAnimationCategory category) {
        synchronized (BINDING_LOCK) {
            PoseBinding existing = BINDINGS.get(model);
            if (existing != null && existing.category == category && existing.stateClass == stateClass) {
                return existing;
            }

            try {
                Method setAngles = findSetAngles(model.getClass(), stateClass);
                if (setAngles == null) return null;
                setAngles.setAccessible(true);

                ModelPart root = model.getRootPart();
                if (root == null) return null;

                String[][] candidates = candidates(category);
                ModelPart[] parts = new ModelPart[6];
                for (int bone = 0; bone < 6; bone++) {
                    parts[bone] = findPart(root, candidates[bone]);
                    if (parts[bone] == null) return null;
                }

                PoseBinding binding = new PoseBinding(category, stateClass, setAngles, parts);
                BINDINGS.put(model, binding);
                return binding;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static boolean supportsExactPose(EntityAnimationCategory category) {
        return category == EntityAnimationCategory.BIPED
                || category == EntityAnimationCategory.QUADRUPED
                || category == EntityAnimationCategory.HORSE
                || category == EntityAnimationCategory.BIRD
                || category == EntityAnimationCategory.CREEPER;
    }

    private static String[][] candidates(EntityAnimationCategory category) {
        return switch (category) {
            case BIPED -> new String[][] {
                    {"head"}, {"body"}, {"left_arm"}, {"right_arm"}, {"left_leg"}, {"right_leg"}
            };
            case QUADRUPED -> new String[][] {
                    {"head"}, {"body", "upper_body"},
                    {"left_front_leg", "leg1"}, {"right_front_leg", "leg2"},
                    {"left_hind_leg", "leg3"}, {"right_hind_leg", "leg4"}
            };
            case HORSE -> new String[][] {
                    {"head"}, {"body"},
                    {"front_left_leg"}, {"front_right_leg"},
                    {"back_left_leg"}, {"back_right_leg"}
            };
            case BIRD -> new String[][] {
                    {"head"}, {"body"}, {"left_wing"}, {"right_wing"}, {"left_leg"}, {"right_leg"}
            };
            case CREEPER -> new String[][] {
                    {"head"}, {"body"}, {"leg1"}, {"leg2"}, {"leg3"}, {"leg4"}
            };
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
        for (String name : names) {
            ModelPart part = findPartRecursive(root, name);
            if (part != null) return part;
        }
        return null;
    }

    private static ModelPart findPartRecursive(ModelPart part, String wanted) {
        if (part == null) return null;
        try {
            Field field = childrenField;
            if (field == null) {
                field = findChildrenField(part.getClass());
                if (field == null) return null;
                childrenField = field;
            }

            @SuppressWarnings("unchecked")
            Map<String, ModelPart> children = (Map<String, ModelPart>) field.get(part);
            if (children == null) return null;

            ModelPart direct = children.get(wanted);
            if (direct != null) return direct;

            for (ModelPart child : children.values()) {
                ModelPart found = findPartRecursive(child, wanted);
                if (found != null) return found;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findChildrenField(Class<?> cls) {
        for (String name : new String[]{"children", "field_3661", "n"}) {
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {
                }
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

    private record PoseBinding(
            EntityAnimationCategory category,
            Class<?> stateClass,
            Method setAngles,
            ModelPart[] parts) {}
}
