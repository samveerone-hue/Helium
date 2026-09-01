package com.helium.util;

import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Small compatibility surface for the dedicated Minecraft 1.21.11 build.
 *
 * <p>This branch targets 1.21.11 only, so there is no reason to retain
 * intermediary-name reflection fallbacks for pre-1.21.11 camera APIs.</p>
 */
public final class VersionCompat {

    private VersionCompat() {}

    public static Identifier createIdentifier(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    public static Vec3d getCameraPosition(Camera camera) {
        return camera.getCameraPos();
    }
}
