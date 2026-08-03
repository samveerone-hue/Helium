package com.helium.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.helium.mixin.crafting.KeyMappingAccessor;
import org.lwjgl.glfw.GLFW;

public final class CraftingInputHelper {

    private CraftingInputHelper() {}

    private static boolean iskeypressed(int keycode) {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, keycode);
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
        InputConstants.Key key = ((KeyMappingAccessor) keyBinding).helium$getBoundKey();
        return key == null ? InputConstants.UNKNOWN.getValue() : key.getValue();
    }
}
