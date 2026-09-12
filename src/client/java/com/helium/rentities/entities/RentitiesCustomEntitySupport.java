package com.helium.rentities.entities;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.EntityType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Conservative adapter for modded living entities whose model follows the normal
 * Minecraft Model/ModelPart + setAngles(renderState) architecture.
 *
 * Only rigs that can be represented by one of the existing Rentities GPU categories
 * are registered. Everything else remains on vanilla rendering.
 */
public final class RentitiesCustomEntitySupport {
    private static volatile Field childrenField;

    private RentitiesCustomEntitySupport() {}

    public static EntityAnimationCategory resolveAndRegister(Object state, EntityType<?> type) {
        if (!(state instanceof EntityRenderState renderState) || type == null) {
            return EntityAnimationCategory.CPU_ANIMATED;
        }

        EntityAnimationCategory existing = EntityBatchRegistry.getCategory(type);
        if (existing != EntityAnimationCategory.CPU_ANIMATED) return existing;

        try {
            EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
            if (dispatcher == null) return EntityAnimationCategory.CPU_ANIMATED;

            EntityRenderer<?, ?> renderer = dispatcher.getRenderer(renderState);
            if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) {
                return EntityAnimationCategory.CPU_ANIMATED;
            }

            Model<?> model = livingRenderer.getModel();
            if (model == null || findSetAngles(model.getClass(), state.getClass()) == null) {
                return EntityAnimationCategory.CPU_ANIMATED;
            }

            ModelPart root = model.getRootPart();
            if (root == null) return EntityAnimationCategory.CPU_ANIMATED;

            Set<String> names = new HashSet<>();
            collectChildren(root, names);
            EntityAnimationCategory category = classify(names);
            if (category == EntityAnimationCategory.CPU_ANIMATED) return category;

            EntityBatchRegistry.registerDynamic(type, category);
            return EntityBatchRegistry.getCategory(type);
        } catch (Throwable ignored) {
            return EntityAnimationCategory.CPU_ANIMATED;
        }
    }

    private static EntityAnimationCategory classify(Set<String> names) {
        if (hasAll(names,
                "head", "body", "left_arm", "right_arm", "left_leg", "right_leg")) {
            return EntityAnimationCategory.BIPED;
        }

        // Horses/camel-style rigs get their dedicated seven-bone binding only when
        // a tail is present. Ordinary four-legged custom mobs use QUADRUPED instead.
        if (hasAll(names, "head", "body", "tail", "front_left_leg", "front_right_leg",
                "back_left_leg", "back_right_leg")
                || hasAll(names, "head", "body", "tail", "left_front_leg", "right_front_leg",
                "left_hind_leg", "right_hind_leg")) {
            return EntityAnimationCategory.HORSE;
        }

        if (hasAll(names, "head", "body", "left_front_leg", "right_front_leg",
                "left_hind_leg", "right_hind_leg")) {
            return EntityAnimationCategory.QUADRUPED;
        }

        if (hasAll(names, "head", "body", "left_wing", "right_wing", "left_leg", "right_leg")) {
            return EntityAnimationCategory.BIRD;
        }

        if (hasAll(names, "head", "body", "right_middle_front_leg", "left_middle_front_leg",
                "right_middle_leg", "left_middle_leg", "right_back_leg", "left_back_leg")) {
            return EntityAnimationCategory.ARTHROPOD;
        }

        if (hasAll(names, "body", "right_wing", "left_wing", "front_legs", "middle_legs", "back_legs")) {
            return EntityAnimationCategory.INSECT;
        }

        if (hasAll(names, "body", "tail")) {
            return EntityAnimationCategory.FISH;
        }

        if (hasAll(names, "cube")) {
            return EntityAnimationCategory.SLIME;
        }

        if (hasAll(names, "body", "tentacle0", "tentacle1", "tentacle2", "tentacle3",
                "tentacle4", "tentacle5", "tentacle6", "tentacle7", "tentacle8")) {
            return EntityAnimationCategory.GHAST;
        }

        if (hasAll(names, "head", "body", "leg1", "leg2", "leg3", "leg4")) {
            return EntityAnimationCategory.CREEPER;
        }

        if (hasAll(names, "head", "body", "left_arm", "right_arm", "left_leg", "right_leg", "tongue")) {
            return EntityAnimationCategory.FROG;
        }

        return EntityAnimationCategory.CPU_ANIMATED;
    }

    private static boolean hasAll(Set<String> names, String... required) {
        for (String name : required) {
            if (!names.contains(name)) return false;
        }
        return true;
    }

    private static void collectChildren(ModelPart part, Set<String> names) {
        if (part == null) return;
        try {
            Field field = childrenField;
            if (field == null) {
                field = findChildrenField(part.getClass());
                if (field == null) return;
                childrenField = field;
            }

            @SuppressWarnings("unchecked")
            Map<String, ModelPart> children = (Map<String, ModelPart>) field.get(part);
            if (children == null) return;

            for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
                names.add(entry.getKey());
                collectChildren(entry.getValue(), names);
            }
        } catch (Throwable ignored) {
        }
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

    private static Method findSetAngles(Class<?> cls, Class<?> stateClass) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("setAngles") || m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0].isAssignableFrom(stateClass)) return m;
            }
        }
        return null;
    }
}
