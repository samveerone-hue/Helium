package com.helium.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import com.helium.util.VersionCompat;

public final class CullingHelper {

    private static volatile Frustum currentfrustum = null;
    private static final ThreadLocal<BlockPos.Mutable> BACK_FACE_POS =
            ThreadLocal.withInitial(BlockPos.Mutable::new);

    private CullingHelper() {}

    public static void setfrustum(Frustum frustum) {
        currentfrustum = frustum;
    }

    public static Frustum getfrustum() {
        return currentfrustum;
    }

    public static boolean isvisible(Box box) {
        Frustum f = currentfrustum;
        if (f == null) return true;
        return f.isVisible(box);
    }

    public static boolean isvisible(BlockPos pos, int expand) {
        return isvisible(new Box(
                pos.getX() - expand, pos.getY() - expand, pos.getZ() - expand,
                pos.getX() + 1 + expand, pos.getY() + 1 + expand, pos.getZ() + 1 + expand
        ));
    }

    public static boolean shouldcullback(BlockPos pos, Direction facing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        BlockPos.Mutable behind = BACK_FACE_POS.get().set(pos, facing.getOpposite());
        BlockState state = client.world.getBlockState(behind);
        return state.isOpaque() && state.isFullCube(client.world, behind);
    }

    public static boolean isfacingcamera(Direction facing, Vec3d entitypos) {
        return isfacingcamera(facing, entitypos.x, entitypos.y, entitypos.z);
    }

    public static boolean isfacingcamera(Direction facing, double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return true;
        Vec3d camerapos = VersionCompat.getCameraPosition(client.gameRenderer.getCamera());
        return switch (facing) {
            case DOWN -> camerapos.y <= y;
            case UP -> camerapos.y >= y;
            case NORTH -> camerapos.z <= z;
            case SOUTH -> camerapos.z >= z;
            case WEST -> camerapos.x <= x;
            case EAST -> camerapos.x >= x;
        };
    }

    public static boolean issignfacingcamera(Direction facing, Vec3d signpos) {
        return issignfacingcamera(facing, signpos.x, signpos.y, signpos.z);
    }

    public static boolean issignfacingcamera(Direction facing, double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return true;
        Vec3d camerapos = VersionCompat.getCameraPosition(client.gameRenderer.getCamera());
        return switch (facing) {
            case NORTH -> camerapos.z <= z;
            case SOUTH -> camerapos.z >= z;
            case WEST -> camerapos.x <= x;
            case EAST -> camerapos.x >= x;
            default -> true;
        };
    }
}
