package com.helium.util;

/** Compact signed packing for chunk-section X/Y/Z coordinates. */
public final class ChunkPosUtil {
    private static final long X_MASK = 0x3FFFFFL;
    private static final long Y_MASK = 0xFFFL;
    private ChunkPosUtil() {}
    public static long packPos(int x, int y, int z) {
        return (x & X_MASK) | ((long)(y & Y_MASK) << 22) | ((long)(z & X_MASK) << 34);
    }
    public static int unpackX(long key) {
        int raw=(int)(key&X_MASK); return (raw&0x200000)!=0?raw-0x400000:raw;
    }
    public static int unpackY(long key) {
        int raw=(int)((key>>>22)&Y_MASK); return (raw&0x800)!=0?raw-0x1000:raw;
    }
    public static int unpackZ(long key) {
        int raw=(int)((key>>>34)&X_MASK); return (raw&0x200000)!=0?raw-0x400000:raw;
    }
}
