package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mixin(PoseStack.class)
public abstract class MatrixStackPoolMixin {

    @Unique
    private static Field helium$stackField = null;

    @Unique
    private static boolean helium$resolved = false;

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static boolean helium$isList = false;

    @Unique
    private static Method helium$copyIntoMethod = null;

    @Unique
    private static Constructor<PoseStack.Pose> helium$noArgCtor = null;

    @Unique
    private final Deque<PoseStack.Pose> helium$pool = new ArrayDeque<>();

    @Unique
    private static boolean helium$hasStackDepth = false;

    @Unique
    private static void helium$resolve() {
        if (helium$resolved) return;
        helium$resolved = true;

        try {
            PoseStack.class.getDeclaredField("stackDepth");
            helium$hasStackDepth = true;
            return;
        } catch (NoSuchFieldException ignored) {}

        try {
            Field f2 = PoseStack.class.getDeclaredField("field_55850");
            if (f2.getType() == int.class) {
                helium$hasStackDepth = true;
                return;
            }
        } catch (NoSuchFieldException ignored) {}

        String[] fieldNames = {"stack", "field_55849", "field_22924"};
        for (String name : fieldNames) {
            try {
                Field f = PoseStack.class.getDeclaredField(name);
                f.setAccessible(true);
                helium$stackField = f;
                Class<?> type = f.getType();
                helium$isList = List.class.isAssignableFrom(type) && !Deque.class.isAssignableFrom(type);
                break;
            } catch (NoSuchFieldException ignored) {}
        }

        try {
            helium$copyIntoMethod = PoseStack.Pose.class.getDeclaredMethod("copy", PoseStack.Pose.class);
            helium$copyIntoMethod.setAccessible(true);
        } catch (NoSuchMethodException ignored) {}

        try {
            helium$noArgCtor = PoseStack.Pose.class.getDeclaredConstructor();
            helium$noArgCtor.setAccessible(true);
        } catch (NoSuchMethodException ignored) {}
    }

    @Unique
    @SuppressWarnings("unchecked")
    private Object helium$getStack() {
        if (helium$stackField == null) return null;
        try {
            return helium$stackField.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private PoseStack.Pose helium$createCopy(PoseStack.Pose source) {
        if (helium$noArgCtor != null && helium$copyIntoMethod != null) {
            try {
                PoseStack.Pose entry = helium$noArgCtor.newInstance();
                helium$copyIntoMethod.invoke(entry, source);
                return entry;
            } catch (Throwable ignored) {}
        }

        try {
            Constructor<PoseStack.Pose> ctor =
                    PoseStack.Pose.class.getDeclaredConstructor(
                            org.joml.Matrix4f.class, org.joml.Matrix3f.class);
            ctor.setAccessible(true);
            return ctor.newInstance(
                    new org.joml.Matrix4f(source.pose()),
                    new org.joml.Matrix3f(source.normal()));
        } catch (Throwable ignored) {}

        return null;
    }

    @Unique
    private void helium$copyData(PoseStack.Pose dest, PoseStack.Pose source) {
        if (helium$copyIntoMethod != null) {
            try {
                helium$copyIntoMethod.invoke(dest, source);
                return;
            } catch (Throwable ignored) {}
        }
        dest.pose().set(source.pose());
        dest.normal().set(source.normal());
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "push", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$pooledPush(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.poseStackPooling) return;

            helium$resolve();
            if (helium$hasStackDepth) return;

            Object stackObj = helium$getStack();
            if (stackObj == null) return;

            PoseStack.Pose top;
            if (helium$isList) {
                List<PoseStack.Pose> list = (List<PoseStack.Pose>) stackObj;
                top = list.get(list.size() - 1);
            } else {
                Deque<PoseStack.Pose> deque = (Deque<PoseStack.Pose>) stackObj;
                top = deque.getLast();
            }

            PoseStack.Pose reused = helium$pool.pollLast();
            if (reused == null) {
                reused = helium$createCopy(top);
                if (reused == null) return;
            } else {
                helium$copyData(reused, top);
            }

            if (helium$isList) {
                ((List<PoseStack.Pose>) stackObj).add(reused);
            } else {
                ((Deque<PoseStack.Pose>) stackObj).addLast(reused);
            }
            ci.cancel();
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("pose stack pooling disabled ({})", t.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "pop", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$pooledPop(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.poseStackPooling) return;

            helium$resolve();
            if (helium$hasStackDepth) return;

            Object stackObj = helium$getStack();
            if (stackObj == null) return;

            int size;
            PoseStack.Pose removed;

            if (helium$isList) {
                List<PoseStack.Pose> list = (List<PoseStack.Pose>) stackObj;
                size = list.size();
                if (size <= 1) return;
                removed = list.remove(size - 1);
            } else {
                Deque<PoseStack.Pose> deque = (Deque<PoseStack.Pose>) stackObj;
                size = deque.size();
                if (size <= 1) return;
                removed = deque.removeLast();
            }

            if (helium$pool.size() < 256) {
                helium$pool.addLast(removed);
            }
            ci.cancel();
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("pose stack pooling disabled ({})", t.getClass().getSimpleName());
            }
        }
    }
}
