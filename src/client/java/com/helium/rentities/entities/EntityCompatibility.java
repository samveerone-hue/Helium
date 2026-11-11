package com.helium.rentities.entities;

import com.helium.HeliumClient;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class EntityCompatibility {
    private static final Set<String> missingFields = ConcurrentHashMap.newKeySet();

    private EntityCompatibility() {}

    static Object invokeAccessor(MethodHandle handle, Object state) {
        if (handle == null) return null;
        try {
            return handle.invoke(state);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static final class StateAccessor {
        final MethodHandle yaw, yawO, limbSwing, limbSwingAmt, headYaw, headYawO, headPitch, headPitchO, attackProgress;
        final MethodHandle deathTime, swimProgress, hurtTime, sneaking;
        final MethodHandle type, invisible, onGround, inWater;
        final MethodHandle entityId;

        StateAccessor(Class<?> cls) {
            this.yaw = mhFloat(cls, "bodyYaw", "yBodyRot", "field_53329", "M");
            this.yawO = mhFloat(cls, "bodyYawO", "yBodyRotO", "prevYBodyRot");
            this.limbSwing = mhFloat(cls, "field_53446", "walkAnimationPos", "ab");
            this.limbSwingAmt = mhFloat(cls, "field_53447", "walkAnimationSpeed", "ac");
            this.headYaw = mhFloat(cls, "headYaw", "yHeadRot", "field_53448", "ad");
            this.headYawO = mhFloat(cls, "yHeadRotO", "prevYHeadRot");
            this.headPitch = mhFloat(cls, "field_53449", "headPitch", "ae");
            this.headPitchO = mhFloat(cls, "xRotO", "prevXRot");
            this.attackProgress = mhFloat(cls, "field_53450", "attackAnim", "af");
            this.deathTime = mhFloat(cls, "field_53452", "deathTime", "ah");
            this.swimProgress = mhFloat(cls, "field_53451", "swimAmount", "ag");
            this.hurtTime = mhBool(cls, "field_53456", "isHurt", "al");
            this.sneaking = mhBool(cls, "field_53455", "isSneaking", "ak");
            this.type = requireObj(cls, "field_58171", "entityType", "H");
            this.invisible = mhBool(cls, "field_53333", "invisible", "Q");
            this.onGround = mhBool(cls, "field_53334", "onGround", "R");
            this.inWater = mhBool(cls, "field_53335", "inWater", "S");
            this.entityId = mhInt(cls, "id", "field_53328", "N");
        }

        private static void warnUnresolved(Class<?> cls, String logical, String... names) {
            String key = cls.getName() + "#" + logical;
            if (!missingFields.add(key)) return;
            HeliumClient.LOGGER.warn(
                    "[Rentities] Reflection: could not resolve state field '{}' on class {} (tried names: {}). "
                            + "Entities of this type may render with reduced/missing animation.",
                    logical, cls.getName(), Arrays.toString(names));
        }

        private static Field findField(Class<?> cls, Class<?> type, String logical, String... names) {
            for (String name : names) {
                Class<?> current = cls;
                while (current != null) {
                    try {
                        Field field = current.getDeclaredField(name);
                        if (type == null || field.getType() == type) {
                            field.setAccessible(true);
                            return field;
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                    current = current.getSuperclass();
                }
            }
            warnUnresolved(cls, logical, names);
            return null;
        }

        private static MethodHandle mhFloat(Class<?> cls, String... names) {
            return getter(cls, float.class, "float", MethodType.methodType(float.class, Object.class), names);
        }

        private static MethodHandle mhBool(Class<?> cls, String... names) {
            return getter(cls, boolean.class, "boolean", MethodType.methodType(boolean.class, Object.class), names);
        }

        private static MethodHandle mhInt(Class<?> cls, String... names) {
            return getter(cls, int.class, "int", MethodType.methodType(int.class, Object.class), names);
        }

        private static MethodHandle getter(Class<?> cls, Class<?> fieldType, String logical, MethodType methodType,
                                           String... names) {
            Field field = findField(cls, fieldType, logical, names);
            if (field == null) return null;
            try {
                return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                        .unreflectGetter(field).asType(methodType);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static MethodHandle requireObj(Class<?> cls, String... names) {
            Field field = findField(cls, null, "object(mandatory)", names);
            if (field == null) {
                HeliumClient.LOGGER.error(
                        "[Rentities] Mandatory entity-state field missing on {} (tried {}); falling back to vanilla rendering.",
                        cls.getName(), Arrays.toString(names));
                return null;
            }
            try {
                return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                        .unreflectGetter(field).asType(MethodType.methodType(Object.class, Object.class));
            } catch (Exception exception) {
                HeliumClient.LOGGER.error("[Rentities] Failed to create state accessor for {}: {}",
                        field.getName(), exception.toString());
                return null;
            }
        }
    }
}
