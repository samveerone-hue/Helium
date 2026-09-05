package com.helium.rentities.entities;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Resolves the texture identifier from a 1.21.11 render-state renderer without
 * depending on the original Rentities static singleton or Mojang mappings.
 */
public final class EntityTextureBootstrap {
    private EntityTextureBootstrap() {}

    public static void bootstrap(EntityBatchRenderer renderer, Map<EntityType<?>, ?> renderers) {
        if (renderer == null || renderers == null) return;

        for (EntityType<?> type : EntityBatchRegistry.REGISTRY_TYPES()) {
            if (EntityBatchRegistry.getCategory(type) == EntityAnimationCategory.CPU_ANIMATED) continue;
            if (renderer.entityTextureLocs.containsKey(type)
                    && renderer.entityGlTexIds.containsKey(type)) continue;

            Object entityRenderer = renderers.get(type);
            if (entityRenderer == null) continue;

            Object state = resolveState(entityRenderer, type);
            if (state == null) continue;

            Object texture = invokeTextureGetter(entityRenderer, state);
            if (texture == null) continue;

            int glId = EntityGlTextureResolver.resolveGlId(texture);
            if (glId > 0) {
                renderer.entityTextureLocs.put(type, texture);
                renderer.entityGlTexIds.put(type, glId);
                renderer.entityTexFailed.remove(type);
            }
        }
    }

    private static Object resolveState(Object renderer, EntityType<?> type) {
        if (!(renderer instanceof EntityRenderer<?, ?>)) return null;
        EntityRenderer<?, ?> entityRenderer = (EntityRenderer<?, ?>) renderer;
        try {
            Method create = find(entityRenderer.getClass(), "createRenderState", 0);
            if (create == null) return null;
            create.setAccessible(true);
            Object state = create.invoke(entityRenderer);

            EntityRenderManager manager = MinecraftClient.getInstance().getEntityRenderDispatcher();
            if (manager == null) return null;

            Entity stub = createDummy(type);
            if (stub == null) return null;

            Method update = findUpdate(entityRenderer.getClass(), stub.getClass(), state.getClass());
            if (update == null) return null;
            update.setAccessible(true);

            Class<?>[] pt = update.getParameterTypes();
            if (pt.length == 3) {
                update.invoke(entityRenderer, stub, state, 0.0f);
            } else {
                update.invoke(entityRenderer, stub, state);
            }
            return state;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Entity createDummy(EntityType<?> type) {
        try {
            Method create = EntityType.class.getMethod("create", net.minecraft.world.World.class);
            return (Entity) create.invoke(type, MinecraftClient.getInstance().world);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findUpdate(Class<?> cls, Class<?> entityClass, Class<?> stateClass) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("updateRenderState") && !m.getName().equals("method_62354")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 3 && pt[0].isAssignableFrom(entityClass)
                        && pt[1].isAssignableFrom(stateClass) && pt[2] == float.class) return m;
                if (pt.length == 2 && pt[0].isAssignableFrom(entityClass)
                        && pt[1].isAssignableFrom(stateClass)) return m;
            }
        }
        return null;
    }

    private static Method find(Class<?> cls, String name, int arity) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
            }
        }
        return null;
    }

    private static Object invokeTextureGetter(Object renderer, Object state) {
        for (String name : new String[]{"getTexture", "method_3885", "getTextureLocation"}) {
            for (Class<?> c = renderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
                    if (!m.getParameterTypes()[0].isAssignableFrom(state.getClass())) continue;
                    try {
                        m.setAccessible(true);
                        return m.invoke(renderer, state);
                    } catch (Throwable ignored) {
                        // Try the next mapped name.
                    }
                }
            }
        }
        return null;
    }
}
