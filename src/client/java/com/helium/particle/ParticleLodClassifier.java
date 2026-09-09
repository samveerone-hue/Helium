package com.helium.particle;

import java.util.Locale;

/** Cached classification for low-visual-impact particle families. */
public final class ParticleLodClassifier {
    private static final ClassValue<Boolean> LOD_TYPES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            String name = type.getName().toLowerCase(Locale.ROOT);
            return name.contains("rain") || name.contains("snow")
                    || name.contains("cloud") || name.contains("ash")
                    || name.contains("drip") || name.contains("spore")
                    || name.contains("smoke") || name.contains("dust");
        }
    };
    private ParticleLodClassifier() {}
    public static boolean shouldApply(Class<?> type) { return type != null && LOD_TYPES.get(type); }
}
