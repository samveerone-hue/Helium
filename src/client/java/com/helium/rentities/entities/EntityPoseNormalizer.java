package com.helium.rentities.entities;

public final class EntityPoseNormalizer {
    private EntityPoseNormalizer() {}

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    public static float clampBodyYaw(float bodyYaw, float headYaw) {
        float diff = wrapDegrees(bodyYaw - headYaw);
        if (diff > 85.0f) return headYaw + 85.0f;
        if (diff < -85.0f) return headYaw - 85.0f;
        return bodyYaw;
    }

    public static float relativeHeadYaw(float bodyYaw, float headYaw) {
        return wrapDegrees(headYaw - bodyYaw);
    }
}
