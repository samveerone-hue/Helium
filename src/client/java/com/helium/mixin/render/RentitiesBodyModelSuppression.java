package com.helium.mixin.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.entity.state.EntityRenderState;

/**
 * Per-render bridge used to keep a Rentities-batched living entity's base body
 * out of vanilla's model command queue while leaving feature renderers intact.
 *
 * The bridge is deliberately identity-based: a feature renderer can use the
 * same render state, but it cannot consume the marker unless it submits the
 * exact vanilla context model that Rentities already replaced.
 */
public final class RentitiesBodyModelSuppression {
    private static final ThreadLocal<Marker> CURRENT = new ThreadLocal<>();

    private RentitiesBodyModelSuppression() {
    }

    public static void mark(EntityRenderState state, Model<?> model) {
        CURRENT.set(new Marker(state, model));
    }

    public static boolean consumeIfMatches(Model<?> model, Object state) {
        Marker marker = CURRENT.get();
        if (marker == null || marker.state != state || marker.model != model) {
            return false;
        }

        CURRENT.remove();
        return true;
    }

    public static void clear() {
        CURRENT.remove();
    }

    private record Marker(EntityRenderState state, Model<?> model) {
    }
}
