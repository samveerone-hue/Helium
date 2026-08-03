package com.helium.platform;

import net.minecraft.util.Util;

import java.io.File;

public final class DeviceDetector {

    private static final Util.OS OS = Util.getPlatform();
    private static final boolean ANDROID = detectAndroid();

    private DeviceDetector() {}

    private static boolean detectAndroid() {
        if (new File("/system/build.prop").exists()) return true;
        if (new File("/system/app").isDirectory()) return true;
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String name = System.getProperty("os.name", "").toLowerCase();
        return name.contains("linux") && arch.contains("aarch64") && new File("/data/data").isDirectory();
    }

    public static boolean isWindows() {
        return OS == Util.OS.WINDOWS;
    }

    public static boolean isLinux() {
        return OS == Util.OS.LINUX && !ANDROID;
    }

    public static boolean isMacOS() {
        return OS == Util.OS.OSX;
    }

    public static boolean isAndroid() {
        return ANDROID;
    }
}
