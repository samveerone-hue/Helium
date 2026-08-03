package com.helium.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class CraftingInputHelper {

    private static Field boundkeyfield = null;
    private static boolean boundkeyfieldresolved = false;
    private static Method keypressedmethod = null;
    private static boolean keypressedresolved = false;

    private CraftingInputHelper() {}

    private static boolean iskeypressed(int keycode) {
        Window window = Minecraft.getInstance().getWindow();
        try {
            return InputConstants.isKeyDown(window, keycode);
        } catch (NoSuchMethodError e) {
            try {
                if (!keypressedresolved) {
                    keypressedresolved = true;
                    keypressedmethod = InputConstants.class.getMethod("isKeyDown", com.mojang.blaze3d.platform.Window.class, int.class);
                }
                if (keypressedmethod != null) {
                    return (boolean) keypressedmethod.invoke(null, window.handle(), keycode);
                }
            } catch (Throwable ignored) {}
            return GLFW.glfwGetKey(window.handle(), keycode) == GLFW.GLFW_PRESS;
        }
    }

    public static boolean isshiftdown() {
        return iskeypressed(GLFW.GLFW_KEY_LEFT_SHIFT) || iskeypressed(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean iscontroldown() {
        return iskeypressed(GLFW.GLFW_KEY_LEFT_CONTROL) || iskeypressed(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isaltdown() {
        return iskeypressed(GLFW.GLFW_KEY_LEFT_ALT) || iskeypressed(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean iskeybindingpressed(KeyMapping keyBinding) {
        int code = getboundkeycode(keyBinding);
        if (code == InputConstants.UNKNOWN.getValue()) return false;
        return iskeypressed(code);
    }

    public static boolean isdropkeypressed() {
        return iskeybindingpressed(Minecraft.getInstance().options.keyDrop);
    }

    public static boolean istogglekey(int keycode) {
        return keycode == GLFW.GLFW_KEY_CAPS_LOCK ||
                keycode == GLFW.GLFW_KEY_NUM_LOCK ||
                keycode == GLFW.GLFW_KEY_SCROLL_LOCK;
    }

    private static int getboundkeycode(KeyMapping keyBinding) {
        try {
            if (!boundkeyfieldresolved) {
                boundkeyfieldresolved = true;
                try {
                    boundkeyfield = KeyMapping.class.getDeclaredField("boundKey");
                    boundkeyfield.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    for (Field f : KeyMapping.class.getDeclaredFields()) {
                        if (InputConstants.Key.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            boundkeyfield = f;
                            break;
                        }
                    }
                }
            }
            if (boundkeyfield != null) {
                InputConstants.Key key = (InputConstants.Key) boundkeyfield.get(keyBinding);
                return key.getValue();
            }
        } catch (Throwable ignored) {}
        return InputConstants.UNKNOWN.getValue();
    }
}
