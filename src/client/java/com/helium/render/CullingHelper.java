package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import com.helium.util.VersionCompat;

public final class CullingHelper {

    private static volatile Frustum currentfrustum = null;

    private CullingHelper() {}

    public static void setfrustum(Frustum frustum) {
        currentfrustum = frustum;
    }

    public static Frustum getfrustum() {
        return currentfrustum;
    }

    public static boolean isvisible(AABB box) {
        Frustum f = currentfrustum;
        if (f == null) return true;
        return f.isVisible(box);
    }

    public static boolean isvisible(BlockPos pos, int expand) {
        return isvisible(new AABB(
                pos.getX() - expand, pos.getY() - expand, pos.getZ() - expand,
                pos.getX() + 1 + expand, pos.getY() + 1 + expand, pos.getZ() + 1 + expand
        ));
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
        if (client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) return true;
        Vec3 camerapos = VersionCompat.getCameraPosition(client.gameRenderer.getMainCamera());
        return switch (facing) {
            case DOWN -> camerapos.y <= entitypos.y;
            case UP -> camerapos.y >= entitypos.y;
            case NORTH -> camerapos.z <= entitypos.z;
            case SOUTH -> camerapos.z >= entitypos.z;
            case WEST -> camerapos.x <= entitypos.x;
            case EAST -> camerapos.x >= entitypos.x;
        };
    }

    public static boolean issignfacingcamera(Direction facing, Vec3 signpos) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) return true;
        Vec3 camerapos = VersionCompat.getCameraPosition(client.gameRenderer.getMainCamera());
        return switch (facing) {
            case NORTH -> camerapos.z <= signpos.z;
            case SOUTH -> camerapos.z >= signpos.z;
            case WEST -> camerapos.x <= signpos.x;
            case EAST -> camerapos.x >= signpos.x;
            default -> true;
        };
    }
}
