package io.github.foundationgames.automobility.block;

import com.mojang.serialization.MapCodec;
import io.github.foundationgames.automobility.item.SlopePlacementContext;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.duck.CollisionArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class SlopeBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, SpecialAutomobileColliderBlock {
    public static final VoxelShape NORTH_BOTTOM_SHAPE;
    public static final VoxelShape SOUTH_BOTTOM_SHAPE;
    public static final VoxelShape EAST_BOTTOM_SHAPE;
    public static final VoxelShape WEST_BOTTOM_SHAPE;
    public static final VoxelShape NORTH_TOP_SHAPE;
    public static final VoxelShape SOUTH_TOP_SHAPE;
    public static final VoxelShape EAST_TOP_SHAPE;
    public static final VoxelShape WEST_TOP_SHAPE;

    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final MapCodec<SlopeBlock> CODEC = Block.simpleCodec(SlopeBlock::new);

    public SlopeBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM).setValue(WATERLOGGED, false));
    }

    @Override
    public CollisionArea getCollisionArea(BlockState state, Level level, BlockPos pos, double downStretch) {
        var bounds = new AABB(pos.getX(), pos.getY() - 4 * downStretch, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);

        double originY = 0.25;
        if (state.getValue(HALF) == Half.TOP) {
            originY = 0.75;
        }

        double zSlope = 0;
        switch (state.getValue(FACING)) {
            case NORTH -> zSlope = 0.5;
            case SOUTH -> zSlope = -0.5;
        }

        double xSlope = 0;
        switch (state.getValue(FACING)) {
            case EAST -> xSlope = -0.5;
            case WEST -> xSlope = 0.5;
        }

        return new CollisionArea.SlopeArea(bounds, xSlope, zSlope, originY);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return ctx instanceof SlopePlacementContext slopeCtx ?
                super.getStateForPlacement(ctx)
                        .setValue(FACING, slopeCtx.getSlopeFacing())
                        .setValue(WATERLOGGED, ctx.getLevel().getBlockState(ctx.getClickedPos()).is(Blocks.WATER))
                        .setValue(HALF, slopeCtx.getSlopeHalf())
                :
                super.getStateForPlacement(ctx)
                        .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                        .setValue(WATERLOGGED, ctx.getLevel().getBlockState(ctx.getClickedPos()).is(Blocks.WATER))
                        .setValue(HALF, ctx.getClickLocation().y - ctx.getClickedPos().getY() > 0.5 ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, HALF, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(HALF)) {
            case BOTTOM -> switch (state.getValue(FACING)) {
                case NORTH -> NORTH_BOTTOM_SHAPE;
                case SOUTH -> SOUTH_BOTTOM_SHAPE;
                case WEST -> WEST_BOTTOM_SHAPE;
                case EAST -> EAST_BOTTOM_SHAPE;
                default -> Shapes.empty();
            };
            case TOP -> switch (state.getValue(FACING)) {
                case NORTH -> NORTH_TOP_SHAPE;
                case SOUTH -> SOUTH_TOP_SHAPE;
                case WEST -> WEST_TOP_SHAPE;
                case EAST -> EAST_TOP_SHAPE;
                default -> Shapes.empty();
            };
            default -> Shapes.empty();
        };
    }

    public static VoxelShape slopeStep(Direction dir, double height) {
        return switch (dir) {
            case NORTH -> Block.box(0, 0, 0, 16, height, 2);
            case SOUTH -> Block.box(0, 0, 14, 16, height, 16);
            case EAST -> Block.box(14, 0, 0, 16, height, 16);
            case WEST -> Block.box(0, 0, 0, 2, height, 16);
            default -> Shapes.empty();
        };
    }

    static {
        var shapes = new ArrayList<VoxelShape>();
        for (var dir : AUtils.HORIZONTAL_DIRS) {
            for (int i = 0; i < 2; i++) {
                double ox = switch (dir) {
                    case WEST -> 2;
                    case EAST -> -2;
                    default -> 0;
                };
                double oz = switch (dir) {
                    case NORTH -> 2;
                    case SOUTH -> -2;
                    default -> 0;
                };
                var finalShape = Shapes.empty();
                for (int j = 0; j < 8; j++) {
                    finalShape = Shapes.or(finalShape, slopeStep(dir, (i * 8) + j).move((ox * j) / 16, 0, (oz * j) / 16));
                }
                shapes.add(finalShape);
            }
        }
        NORTH_BOTTOM_SHAPE = shapes.get(0);
        NORTH_TOP_SHAPE = shapes.get(1);
        SOUTH_BOTTOM_SHAPE = shapes.get(2);
        SOUTH_TOP_SHAPE = shapes.get(3);
        EAST_BOTTOM_SHAPE = shapes.get(4);
        EAST_TOP_SHAPE = shapes.get(5);
        WEST_BOTTOM_SHAPE = shapes.get(6);
        WEST_TOP_SHAPE = shapes.get(7);
    }
}
