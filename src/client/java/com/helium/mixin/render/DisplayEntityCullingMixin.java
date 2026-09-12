package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives transformed Display entities a conservative visibility box when vanilla has no useful
 * physical width/height to derive one from. This keeps culling correct for rotated/scaled displays
 * without touching their actual render transform.
 */
@Mixin(DisplayEntity.class)
public abstract class DisplayEntityCullingMixin {
    private static final Vector3f[] CORNERS = new Vector3f[]{
            new Vector3f(-1.0f, -1.0f, -1.0f),
            new Vector3f(-1.0f, -1.0f, 2.0f),
            new Vector3f(-1.0f, 2.0f, -1.0f),
            new Vector3f(-1.0f, 2.0f, 2.0f),
            new Vector3f(2.0f, -1.0f, -1.0f),
            new Vector3f(2.0f, -1.0f, 2.0f),
            new Vector3f(2.0f, 2.0f, -1.0f),
            new Vector3f(2.0f, 2.0f, 2.0f)
    };
    private static final ThreadLocal<Vector3f> SCRATCH = ThreadLocal.withInitial(Vector3f::new);

    @Shadow private Box visibilityBoundingBox;
    @Shadow private static AffineTransformation getTransformation(DataTracker dataTracker) { return null; }
    @Shadow private float getDisplayWidth() { return 0.0f; }
    @Shadow private float getDisplayHeight() { return 0.0f; }

    @Inject(method = "updateVisibilityBoundingBox", at = @At("TAIL"), require = 0)
    private void helium$updateTransformedBounds(CallbackInfo ci) {
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.entityCulling) return;

            // Preserve vanilla's normal width/height based bounds when they are meaningful.
            if (getDisplayWidth() != 0.0f && getDisplayHeight() != 0.0f) return;

            DisplayEntity entity = (DisplayEntity) (Object) this;
            AffineTransformation transformation = getTransformation(entity.getDataTracker());
            if (transformation == null) return;

            Matrix4fc matrix = transformation.getMatrix();
            Vector3f scratch = SCRATCH.get();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            for (Vector3f corner : CORNERS) {
                matrix.transformPosition(corner, scratch);
                minX = Math.min(minX, scratch.x);
                minY = Math.min(minY, scratch.y);
                minZ = Math.min(minZ, scratch.z);
                maxX = Math.max(maxX, scratch.x);
                maxY = Math.max(maxY, scratch.y);
                maxZ = Math.max(maxZ, scratch.z);
            }

            // Keep a small safety margin so precision and interpolation changes cannot clip a
            // display exactly on a culling plane.
            final double margin = 1.0 / 256.0;
            this.visibilityBoundingBox = new Box(
                    entity.getX() + minX - margin,
                    entity.getY() + minY - margin,
                    entity.getZ() + minZ - margin,
                    entity.getX() + maxX + margin,
                    entity.getY() + maxY + margin,
                    entity.getZ() + maxZ + margin
            );
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("display entity transformed culling bounds unavailable", t);
        }
    }
}
