package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.caffeinemc.mods.sodium.api.blockentity.BlockEntityRenderHandler;
import net.caffeinemc.mods.sodium.api.blockentity.BlockEntityRenderPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class HeliumBlockEntityCulling {
    private static volatile boolean registered;

    private HeliumBlockEntityCulling() {}
    public static boolean isRegistered() { return registered; }

    @SuppressWarnings("unchecked")
    public static void register() {
        if (registered) return;
        registered = true;

        BlockEntityRenderPredicate<BlockEntity> predicate = (world, pos, entity) -> {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.blockEntityCulling) return true;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return true;
            double dist = client.player.blockPosition().distSqr(pos);
            double maxDist = config.blockEntityCullDistance * config.blockEntityCullDistance;
            return dist <= maxDist;
        };

        try {
            BlockEntityRenderHandler handler = BlockEntityRenderHandler.instance();
            String[] ids = {
                    "chest", "sign", "hanging_sign", "banner", "bell", "campfire",
                    "enchanting_table", "end_portal", "end_gateway", "decorated_pot",
                    "bed", "shulker_box", "skull", "conduit"
            };
            for (String id : ids) {
                BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE
                        .getValue(Identifier.withDefaultNamespace(id));
                if (type != null) {
                    handler.addRenderPredicate((BlockEntityType<BlockEntity>) type, predicate);
                }
            }
            HeliumClient.LOGGER.info("block entity culling registered via sodium api");
        } catch (Throwable e) {
            HeliumClient.LOGGER.warn("failed to register block entity culling", e);
        }
    }
}
