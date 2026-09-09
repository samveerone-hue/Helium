package com.helium.particle;

import java.util.Locale;

/** Classifies low-visual-impact particle families once per implementation class. */
public final class ParticleLodClassifier {
    private static final ClassValue<Boolean> TYPES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            String n = type.getName().toLowerCase(Locale.ROOT);
            return n.contains("rain") || n.contains("snow") || n.contains("cloud")
                    || n.contains("ash") || n.contains("drip") || n.contains("spore")
                    || n.contains("smoke") || n.contains("dust");
        }
    };
    private ParticleLodClassifier() {}
    public static boolean shouldApply(Class<?> type) { return type != null && TYPES.get(type); }
}
