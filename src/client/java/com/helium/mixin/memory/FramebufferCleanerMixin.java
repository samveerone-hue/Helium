package com.helium.mixin.memory;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.Cleaner;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(RenderTarget.class)
public abstract class FramebufferCleanerMixin {

    @Unique
    private static final Cleaner HELIUM_CLEANER = Cleaner.create();

    @Unique
    private static boolean helium$resolved = false;

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static boolean helium$isModernApi = false;

    @Unique
    private static Field helium$fboField = null;

    @Unique
    private static Field helium$colorField = null;

    @Unique
    private static Field helium$depthField = null;

    @Unique
    private static Method helium$gpuTextureClose = null;

    @Unique
    private Cleaner.Cleanable helium$cleanable;

    @Unique
    private static void helium$resolve() {
        if (helium$resolved) return;
        helium$resolved = true;

        try {
            Class.forName("com.mojang.blaze3d.textures.GpuTexture");
            helium$isModernApi = true;

            helium$colorField = helium$findField(new String[]{"colorAttachment", "field_1475"});
            helium$depthField = helium$findField(new String[]{"depthAttachment", "field_56739"});

            try {
                Class<?> gpuTexClass = Class.forName("com.mojang.blaze3d.textures.GpuTexture");
                helium$gpuTextureClose = gpuTexClass.getDeclaredMethod("close");
                helium$gpuTextureClose.setAccessible(true);
            } catch (Throwable ignored) {}
        } catch (ClassNotFoundException e) {
            helium$isModernApi = false;

            helium$fboField = helium$findIntField(new String[]{"fbo", "field_1042"});
            helium$colorField = helium$findIntField(new String[]{"colorAttachment", "field_1041"});
            helium$depthField = helium$findIntField(new String[]{"depthAttachment", "field_1044"});
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$registerCleaner(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.framebufferCleaner) return;

            helium$resolve();

            if (helium$isModernApi) {
                helium$registerModernCleaner();
            } else {
                helium$registerLegacyCleaner();
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("framebuffer cleaner init failed ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Unique
    private void helium$registerLegacyCleaner() {
        int fboCapture = helium$getIntField(helium$fboField);
        int colorCapture = helium$getIntField(helium$colorField);
        int depthCapture = helium$getIntField(helium$depthField);

        if (fboCapture <= 0 && colorCapture <= 0 && depthCapture <= 0) return;

        helium$cleanable = HELIUM_CLEANER.register(this, () -> {
            try {
                if (colorCapture > 0) GL11.glDeleteTextures(colorCapture);
                if (depthCapture > 0) GL11.glDeleteTextures(depthCapture);
                if (fboCapture > 0) GL30.glDeleteFramebuffers(fboCapture);
            } catch (Throwable ignored) {}
        });
    }

    @Unique
    private void helium$registerModernCleaner() {
        if (helium$gpuTextureClose == null) return;

        Object colorTex = helium$getObjectField(helium$colorField);
        Object depthTex = helium$getObjectField(helium$depthField);

        if (colorTex == null && depthTex == null) return;

        final Method closeMethod = helium$gpuTextureClose;
        helium$cleanable = HELIUM_CLEANER.register(this, () -> {
            try {
                if (colorTex != null) closeMethod.invoke(colorTex);
            } catch (Throwable ignored) {}
            try {
                if (depthTex != null) closeMethod.invoke(depthTex);
            } catch (Throwable ignored) {}
        });
    }

    @Inject(method = "destroyBuffers", at = @At("HEAD"), require = 0)
    private void helium$cancelCleanerOnExplicitDelete(CallbackInfo ci) {
        if (helium$cleanable != null) {
            helium$cleanable.clean();
            helium$cleanable = null;
        }
    }

    @Unique
    private static Field helium$findField(String[] names) {
        for (String name : names) {
            try {
                Field f = RenderTarget.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    @Unique
    private static Field helium$findIntField(String[] names) {
        for (String name : names) {
            try {
                Field f = RenderTarget.class.getDeclaredField(name);
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    @Unique
    private int helium$getIntField(Field field) {
        if (field == null) return -1;
        try {
            return field.getInt(this);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @Unique
    private Object helium$getObjectField(Field field) {
        if (field == null) return null;
        try {
            return field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
