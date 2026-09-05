package com.helium.particle;

import java.util.Locale;

/**
 * Classifies low-visual-impact particle families for optional spawn-time LOD.
 * The cache is keyed by class identity so classification is performed once per
 * particle implementation, not once per particle instance.
 */
public final class ParticleLodClassifier {

    private static final ClassValue<Boolean> LOD_TYPES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName().toLowerCase(Locale.ROOT);
            return name.contains("rain") || name.contains("snow")
                    || name.contains("cloud") || name.contains("ash")
                    || name.contains("drip") || name.contains("spore")
                    || name.contains("smoke") || name.contains("dust");
        }
    };

    private ParticleLodClassifier() {}

    public static boolean shouldApply(Class<?> type) {
        return type != null && LOD_TYPES.get(type);
    }
}
