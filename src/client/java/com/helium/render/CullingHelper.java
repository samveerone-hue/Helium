package com.helium.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CullingHelper {
    private static volatile Frustum currentfrustum;

    private CullingHelper() {}

    public static void setfrustum(Frustum frustum) { currentfrustum = frustum; }
    public static Frustum getfrustum() { return currentfrustum; }

    public static boolean isvisible(AABB box) {
        Frustum f = currentfrustum;
        return f == null || f.isVisible(box);
    }

    public static boolean isvisible(BlockPos pos, int expand) {
        return isvisible(new AABB(
                pos.getX() - expand, pos.getY() - expand, pos.getZ() - expand,
                pos.getX() + 1 + expand, pos.getY() + 1 + expand, pos.getZ() + 1 + expand));
    }

    public static boolean shouldcullback(BlockPos pos, Direction facing) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockState state = client.level.getBlockState(behind);
        return state.canOcclude() && state.isCollisionShapeFullBlock(client.level, behind);
    }

    public static boolean isfacingcamera(Direction facing, Vec3 entitypos) {
        Minecraft client = Minecraft.getInstance();
        GameRenderer renderer = client.gameRenderer;
        if (renderer == null || renderer.mainCamera() == null) return true;
        Vec3 cameraPos = renderer.mainCamera().position();
        return switch (facing) {
            case DOWN -> cameraPos.y <= entitypos.y;
            case UP -> cameraPos.y >= entitypos.y;
            case NORTH -> cameraPos.z <= entitypos.z;
            case SOUTH -> cameraPos.z >= entitypos.z;
            case WEST -> cameraPos.x <= entitypos.x;
            case EAST -> cameraPos.x >= entitypos.x;
        };
    }

    public static boolean issignfacingcamera(Direction facing, Vec3 signpos) {
        Minecraft client = Minecraft.getInstance();
        GameRenderer renderer = client.gameRenderer;
        if (renderer == null || renderer.mainCamera() == null) return true;
        Vec3 cameraPos = renderer.mainCamera().position();
        return switch (facing) {
            case NORTH -> cameraPos.z <= signpos.z;
            case SOUTH -> cameraPos.z >= signpos.z;
            case WEST -> cameraPos.x <= signpos.x;
            case EAST -> cameraPos.x >= signpos.x;
            default -> true;
        };
    }
}
