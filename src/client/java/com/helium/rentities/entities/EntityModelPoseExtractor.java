package com.helium.rentities.entities;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;
import java.util.WeakHashMap;

/** Captures exact vanilla ModelPart rotations for supported model families. */
public final class EntityModelPoseExtractor {
    private static final Object BINDING_LOCK = new Object();
    private static final Map<Model<?>, PoseBinding> BINDINGS = new WeakHashMap<>();

    private EntityModelPoseExtractor() {}

    private static final class PoseBinding {
        final java.lang.invoke.MethodHandle setAngles;
        final ModelPart[] parts;

        PoseBinding(java.lang.invoke.MethodHandle setAngles, ModelPart[] parts) {
            this.setAngles = setAngles;
            this.parts = parts;
        }
    }

    public static boolean writeExactPose(long ptr, EntityRenderState renderState, EntityAnimationCategory category) {
        if (renderState == null || category == null) return false;
        if (!isSupported(category)) return false;

        try {
            EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
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
                    if (part == null) return false;
                    writePose(
                            ptr + EntityInstance.OFFSET_ARMOR_STAND_HEAD_POSE + bone * 16L,
                            part.pitch,
                            part.yaw,
                            part.roll);
                }
                return true;
            } finally {
                model.resetTransforms();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSupported(EntityAnimationCategory category) {
        return category == EntityAnimationCategory.BIPED
                || category == EntityAnimationCategory.QUADRUPED
                || category == EntityAnimationCategory.HORSE
                || category == EntityAnimationCategory.BIRD
                || category == EntityAnimationCategory.CREEPER;
    }

    private static PoseBinding getBinding(Model<?> model, Class<?> stateClass, EntityAnimationCategory category) {
        synchronized (BINDING_LOCK) {
            PoseBinding binding = BINDINGS.get(model);
            if (binding != null) return binding;

            java.lang.invoke.MethodHandle setAngles = resolveSetAngles(model);
            if (setAngles == null) return null;

            ModelPart[] parts = resolveParts(model, category);
            if (parts == null) return null;
            binding = new PoseBinding(setAngles, parts);
            BINDINGS.put(model, binding);
            return binding;
        }
    }

    private static java.lang.invoke.MethodHandle resolveSetAngles(Model<?> model) {
        try {
            for (Class<?> cls = model.getClass(); cls != null; cls = cls.getSuperclass()) {
                for (var method : cls.getDeclaredMethods()) {
                    if (!method.getName().equals("setAngles") || method.getParameterCount() != 1) continue;
                    method.setAccessible(true);
                    return java.lang.invoke.MethodHandles.lookup()
                            .unreflect(method)
                            .asType(java.lang.invoke.MethodType.methodType(void.class, Object.class));
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ModelPart[] resolveParts(Model<?> model, EntityAnimationCategory category) {
        try {
            ModelPart[] parts = new ModelPart[6];
            switch (category) {
                case BIPED -> {
                    parts[0] = findPart(model, "head");
                    parts[1] = findPart(model, "body");
                    parts[2] = findPart(model, "left_arm", "leftArm");
                    parts[3] = findPart(model, "right_arm", "rightArm");
                    parts[4] = findPart(model, "left_leg", "leftLeg");
                    parts[5] = findPart(model, "right_leg", "rightLeg");
                }
                case QUADRUPED, HORSE, BIRD -> {
                    parts[0] = findPart(model, "head");
                    parts[1] = findPart(model, "body");
                    parts[2] = findPart(model, "left_front_leg", "leftFrontLeg", "left_wing", "leftWing");
                    parts[3] = findPart(model, "right_front_leg", "rightFrontLeg", "right_wing", "rightWing");
                    parts[4] = findPart(model, "left_hind_leg", "leftHindLeg", "left_leg", "leftLeg");
                    parts[5] = findPart(model, "right_hind_leg", "rightHindLeg", "right_leg", "rightLeg");
                }
                case CREEPER -> {
                    parts[0] = findPart(model, "head");
                    parts[1] = findPart(model, "body");
                    parts[2] = findPart(model, "right_hind_leg", "rightLeg");
                    parts[3] = findPart(model, "left_hind_leg", "leftLeg");
                    parts[4] = parts[2];
                    parts[5] = parts[3];
                }
                default -> {
                    return null;
                }
            }
            for (ModelPart part : parts) if (part == null) return null;
            return parts;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ModelPart findPart(Model<?> model, String... names) {
        try {
            for (String name : names) {
                try {
                    return model.getRootPart().getChild(name);
                } catch (Throwable ignored) {}
                for (ModelPart part : model.getParts()) {
                    try {
                        if (part == model.getRootPart()) continue;
                        if (part.equals(model.getRootPart().getChild(name))) return part;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void writePose(long address, float pitch, float yaw, float roll) {
        MemoryUtil.memPutFloat(address, pitch);
        MemoryUtil.memPutFloat(address + 4L, yaw);
        MemoryUtil.memPutFloat(address + 8L, roll);
        MemoryUtil.memPutFloat(address + 12L, 0.0f);
    }
}
