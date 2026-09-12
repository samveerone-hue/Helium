package com.helium.rentities.entities;

import net.minecraft.client.render.entity.state.EntityRenderState;

/** Thread-local marker for feature renderers belonging to a body already queued by Rentities. */
public final class RentitiesEquipmentContext {
    private static final ThreadLocal<EntityRenderState> CURRENT = new ThreadLocal<>();

    private RentitiesEquipmentContext() {}

    public static void mark(EntityRenderState state) {
        CURRENT.set(state);
    }

    public static boolean isCurrent(Object state) {
        return CURRENT.get() == state;
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
