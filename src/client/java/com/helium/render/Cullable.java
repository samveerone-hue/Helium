package com.helium.render;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

public interface Cullable {
    boolean helium$shouldCullSide(BlockState state, BlockGetter view, BlockPos pos, Direction facing);
}
