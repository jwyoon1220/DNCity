package io.github.foundationgames.automobility.block;

import io.github.foundationgames.automobility.util.duck.CollisionArea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface SpecialAutomobileColliderBlock {
    CollisionArea getCollisionArea(BlockState state, Level level, BlockPos pos, double downStretch);
}
