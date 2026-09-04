package com.helium.mixin;

import com.helium.compat.ExternalModCompat;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class HeliumMixinPlugin implements IMixinConfigPlugin {

    private boolean hasOpenGlStateManager = false;
    private boolean hasPlatformGlStateManager = false;
    private boolean hasImmediatelyFast = false;
    private boolean hasModernSodiumApi = false;
    private boolean hasSodium = false;
    private boolean hasIris = false;
    private boolean hasFastServerPings = false;
    private boolean hasOxidizium = false;
    private boolean hasGpuBooster = false;
    private boolean hasMatrixStackDepth = false;

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        hasOpenGlStateManager = classExistsOnClasspath("com/mojang/blaze3d/opengl/GlStateManager.class");
        hasPlatformGlStateManager = classExistsOnClasspath("com/mojang/blaze3d/platform/GlStateManager.class");
        hasImmediatelyFast = loader.isModLoaded("immediatelyfast");
        hasModernSodiumApi = classExistsOnClasspath("net/caffeinemc/mods/sodium/api/config/ConfigEntryPoint.class");
        hasSodium = loader.isModLoaded("sodium");
        hasIris = loader.isModLoaded("iris");
        hasFastServerPings = ExternalModCompat.hasFastServerPings();
        hasOxidizium = ExternalModCompat.hasOxidizium();
        hasGpuBooster = loader.isModLoaded("gpu_booster");
        hasMatrixStackDepth = classFieldExists("net.minecraft.client.util.math.MatrixStack", "stackDepth");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("ServerListWidgetMixin")) return !hasFastServerPings;
        if (mixinClassName.endsWith("MathHelperMixin")) return !hasOxidizium;
        if (mixinClassName.endsWith("MathHelperFloatMixin")) return !hasOpenGlStateManager && !hasOxidizium;
        if (mixinClassName.endsWith("MathHelperDoubleMixin")) return hasOpenGlStateManager && !hasOxidizium;

        if (mixinClassName.endsWith("WindowMixin")) return !hasGpuBooster;
        if (mixinClassName.endsWith("MatrixStackPoolMixin")) return !hasMatrixStackDepth;

        if (mixinClassName.endsWith("GlStateManagerMixin")) {
            return hasOpenGlStateManager && !hasImmediatelyFast;
        }
        if (mixinClassName.endsWith("GlStateManagerLegacyMixin")) {
            return hasPlatformGlStateManager && !hasImmediatelyFast;
        }
        if (mixinClassName.endsWith("SodiumOptionsGUILegacyMixin")) {
            return !hasModernSodiumApi;
        }
        if (mixinClassName.endsWith("SodiumOptionsGUIFallbackMixin")) {
            return hasModernSodiumApi;
        }
        if (mixinClassName.endsWith("SodiumLeafCullingMixin")) {
            return hasSodium;
        }
        if (mixinClassName.endsWith("LeafCullingMixin")) {
            return !hasSodium;
        }
        if (mixinClassName.endsWith("IrisShaderCacheMixin")) {
            return hasIris;
        }
        if (mixinClassName.endsWith("PaintingCullingMixin")) {
            return hasOpenGlStateManager;
        }
        if (mixinClassName.endsWith("ItemFrameCullingMixin")) {
            return hasOpenGlStateManager;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private boolean classExistsOnClasspath(String resourcePath) {
        return getClass().getClassLoader().getResource(resourcePath) != null;
    }

    private boolean classFieldExists(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className, false, getClass().getClassLoader());
            type.getDeclaredField(fieldName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
