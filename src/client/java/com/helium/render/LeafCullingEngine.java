package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
    
public final class LeafCullingEngine {

    public enum CullingMode {
        OFF, FAST, VERTICAL, STATE, CHECK, GAP, DEPTH, RANDOM
    }

    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final IntegerProperty DISTANCE = LeavesBlock.DISTANCE;

    private LeafCullingEngine() {}

    public static CullingMode getmode() {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) return CullingMode.OFF;
        try {
            return CullingMode.valueOf(config.leafCullingMode.toUpperCase());
        } catch (Exception e) {
            return CullingMode.FAST;
        }
    }

    public static boolean isEnabled() {
        return getmode() != CullingMode.OFF;
    }

    public static int getamount() {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) return 2;
        return Math.max(1, Math.min(4, config.leafCullingDepth));
    }

    public static boolean isleaflike(Block block) {
        if (block instanceof LeavesBlock) return true;
        if (block instanceof MangroveRootsBlock) {
            HeliumConfig config = HeliumClient.getConfig();
            return config != null && config.leafCullingMangroveRoots;
        }
        return false;
    }

    public static boolean areLeavesOpaque() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.options == null) return false;
            return !client.options.cutoutLeaves().get();
        } catch (Throwable t) {
            return false;
        }
    }

    //--// mode: FAST / VERTICAL — handled in isSideInvisible mixin directly

    //--// mode: STATE — cull based on distance property modulo

    public static Optional<Boolean> shoulddrawstate(BlockGetter view, BlockState sidestate, BlockPos thispos,
                                                     BlockPos sidepos, Direction side) {
        if (isleaflike(sidestate.getBlock())) {
            try {
                if (sidestate.hasProperty(DISTANCE) && sidestate.getValue(DISTANCE) % 3 != 1) {
                    return Optional.of(false);
                }
            } catch (Throwable ignored) {}
            return Optional.empty();
        }
        return Optional.of(true);
    }

    //--// mode: CHECK — cull if block is fully surrounded by leaves/solid

    public static Optional<Boolean> shoulddrawcheck(BlockGetter view, BlockState sidestate, BlockPos thispos,
                                                     BlockPos sidepos, Direction side) {
        if (isleaflike(sidestate.getBlock()) || issidesolid(sidestate, side.getOpposite())) {
            boolean surrounded = true;
            for (Direction dir : ALL_DIRECTIONS) {
                if (dir != side) {
                    BlockPos neighborpos = thispos.relative(dir);
                    BlockState neighborstate = view.getBlockState(neighborpos);
                    surrounded &= isleaflike(neighborstate.getBlock()) || issidesolid(neighborstate, dir.getOpposite());
                }
            }
            return surrounded ? Optional.of(false) : Optional.empty();
        }
        return Optional.of(true);
    }

    //--// mode: GAP — cull based on gap depth

    public static Optional<Boolean> shoulddrawgap(BlockGetter view, BlockState sidestate, BlockPos sidepos,
                                                    Direction side) {
        Direction opposite = side.getOpposite();
        if (isleaflike(sidestate.getBlock()) || issidesolid(sidestate, opposite)) {
            int amount = getamount();
            for (int i = 1; i < (5 - amount); i++) {
                BlockPos pos = sidepos.relative(side, i);
                BlockState state = view.getBlockState(pos);
                if (state == null || !(isleaflike(state.getBlock()) || issidesolid(state, opposite))) {
                    return Optional.of(false);
                }
            }
        }
        return Optional.of(true);
    }

    //--// mode: DEPTH — cull based on depth from nearest air

    public static Optional<Boolean> shoulddrawdepth(BlockGetter view, BlockState sidestate, BlockPos sidepos,
                                                     Direction side) {
        if (isleaflike(sidestate.getBlock()) || issidesolid(sidestate, side.getOpposite())) {
            int amount = getamount();
            for (int i = 1; i < amount + 1; i++) {
                BlockState state = view.getBlockState(sidepos.relative(side, i));
                if (state == null || state.isAir()) {
                    return Optional.of(true);
                }
            }
            return Optional.of(false);
        }
        return Optional.of(true);
    }

    //--// mode: RANDOM — probabilistic culling

    public static Optional<Boolean> shoulddrawrandom(BlockGetter view, BlockState sidestate, BlockPos sidepos,
                                                      Direction side) {
        if (isleaflike(sidestate.getBlock()) || issidesolid(sidestate, side.getOpposite())) {
            int amount = getamount();
            if (ThreadLocalRandom.current().nextInt(1, amount + 2) == 1) {
                return Optional.of(false);
            }
        }
        return Optional.of(true);
    }

    //--// dispatcher for custom modes (STATE/CHECK/GAP/DEPTH/RANDOM)

    public static Optional<Boolean> customshoulddraw(BlockGetter view, BlockState thisstate, BlockState sidestate,
                                                      BlockPos thispos, BlockPos sidepos, Direction side) {
        return switch (getmode()) {
            case STATE -> shoulddrawstate(view, sidestate, thispos, sidepos, side);
            case CHECK -> shoulddrawcheck(view, sidestate, thispos, sidepos, side);
            case GAP -> shoulddrawgap(view, sidestate, sidepos, side);
            case DEPTH -> shoulddrawdepth(view, sidestate, sidepos, side);
            case RANDOM -> shoulddrawrandom(view, sidestate, sidepos, side);
            default -> Optional.empty();
        };
    }

    //--// utility

    private static boolean issidesolid(BlockState state, Direction side) {
        return state.canOcclude() && state.isFaceSturdy(Minecraft.getInstance().level, BlockPos.ZERO, side);
    }

    public static float getconstantrandom(BlockPos pos) {
        long seed = (pos.asLong() ^ 25214903917L) & 281474976710655L;
        seed = seed * 25214903917L + 11L & 281474976710655L;
        return ((int) (seed >> 48 - 24)) * 5.9604645E-8F;
    }
}
