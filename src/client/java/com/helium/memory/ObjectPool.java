package com.helium.memory;

import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

import java.util.ArrayDeque;

public final class ObjectPool {

    private static final ThreadLocal<ArrayDeque<BlockPos.Mutable>> BLOCK_POS_POOL =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Vector3f>> VEC3F_POOL =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static int maxPoolSize = 512;

    private ObjectPool() {}

    public static void init(int poolSize) {
        maxPoolSize = poolSize;
    }

    public static BlockPos.Mutable borrowBlockPos() {
        ArrayDeque<BlockPos.Mutable> pool = BLOCK_POS_POOL.get();
        BlockPos.Mutable pos = pool.pollFirst();
        return pos != null ? pos : new BlockPos.Mutable();
    }

    public static void returnBlockPos(BlockPos.Mutable pos) {
        ArrayDeque<BlockPos.Mutable> pool = BLOCK_POS_POOL.get();
        if (pool.size() < maxPoolSize) {
            pos.set(0, 0, 0);
            pool.offerFirst(pos);
        }
    }

    public static Vector3f borrowVec3f() {
        ArrayDeque<Vector3f> pool = VEC3F_POOL.get();
        Vector3f vec = pool.pollFirst();
        return vec != null ? vec.set(0, 0, 0) : new Vector3f();
    }

    public static void returnVec3f(Vector3f vec) {
        ArrayDeque<Vector3f> pool = VEC3F_POOL.get();
        if (pool.size() < maxPoolSize) {
            pool.offerFirst(vec);
        }
    }
}
