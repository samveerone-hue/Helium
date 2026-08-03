package com.helium.util;

import net.minecraft.client.Camera;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public final class VersionCompat {

    private static Method legacyCameraPos = null;
    private static boolean cameraFallbackResolved = false;

    private VersionCompat() {}

    public static Identifier createIdentifier(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Vec3 getCameraPosition(Camera camera) {
        try {
            return camera.position();
        } catch (NoSuchMethodError e) {
            return getCameraPositionLegacy(camera);
        }
    }

    private static Vec3 getCameraPositionLegacy(Camera camera) {
        if (!cameraFallbackResolved) {
            cameraFallbackResolved = true;
            try {
                String mapped = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getMappingResolver()
                        .mapMethodName("intermediary",
                                "net.minecraft.class_4184",
                                "method_19326",
                                "()Lnet/minecraft/class_243;");
                legacyCameraPos = Camera.class.getMethod(mapped);
            } catch (Throwable ignored) {}
        }

        if (legacyCameraPos != null) {
            try {
                return (Vec3) legacyCameraPos.invoke(camera);
            } catch (Throwable ignored) {}
        }

        return Vec3.ZERO;
    }
}
