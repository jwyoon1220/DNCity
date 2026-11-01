package io.github.foundationgames.automobility.block;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.block.entity.AutopilotSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AutopilotSignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final VoxelShape FLOOR_SHAPE = box(2, 0, 2, 14, 1, 14);
    public static final VoxelShape CEILING_SHAPE = box(2, 15, 2, 14, 16, 14);
    public static final VoxelShape STANDING_SHAPE = box(4, 0, 4, 12, 16, 12);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);
    public static final EnumProperty<Type> TYPE = EnumProperty.create("type", Type.class);

    public static final MapCodec<AutopilotSignBlock> CODEC = simpleCodec(AutopilotSignBlock::new);

    public AutopilotSignBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(ROTATION, 0).setValue(TYPE, Type.STANDING_RIGHT).setValue(POWERED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ROTATION, TYPE, POWERED, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var type = Type.STANDING_RIGHT;
        var face = ctx.getClickedFace();
        if (face == Direction.DOWN) {
            type = Type.CEILING;
        } else if (face == Direction.UP && ctx.getNearestLookingDirection() == Direction.DOWN) {
            type = Type.FLOOR;
        }

        int yaw = (int) (382.5 + ctx.getPlayer().getYRot());
        int rotState = Math.floorMod(((yaw * 8) / 360), 8);
        return defaultBlockState().setValue(TYPE, type).setValue(ROTATION, rotState)
                .setValue(WATERLOGGED, ctx.getLevel().getBlockState(ctx.getClickedPos()).is(Blocks.WATER));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return switch (state.getValue(TYPE)) {
            case STANDING_LEFT, STANDING_RIGHT -> canSupportCenter(level, pos.below(), Direction.UP);
            case FLOOR -> level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
            case CEILING -> level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN);
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(TYPE)) {
            case STANDING_LEFT, STANDING_RIGHT -> STANDING_SHAPE;
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
        };
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), 3);
        }

        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.mayBuild()) {
            var type = state.getValue(TYPE);

            if (type.standing()) {
                if (level.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }

                type = type == Type.STANDING_LEFT ? Type.STANDING_RIGHT : Type.STANDING_LEFT;
                level.setBlockAndUpdate(pos, state.setValue(TYPE, type));

                return InteractionResult.CONSUME;
            }
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    public Vec3 getPathDir(BlockState state) {
        double x = switch (state.getValue(ROTATION)) {
            default -> 0;
            case 1, 2, 3 -> 1;
            case 5, 6, 7 -> -1;
        };
        double z = switch (state.getValue(ROTATION)) {
            default -> 0;
            case 3, 4, 5 -> 1;
            case 7, 0, 1 -> -1;
        };

        double ax = switch (state.getValue(TYPE)) {
            case STANDING_RIGHT -> -z;
            case STANDING_LEFT -> z;
            default -> x;
        };
        double az = switch (state.getValue(TYPE)) {
            case STANDING_RIGHT -> x;
            case STANDING_LEFT -> -x;
            default -> z;
        };

        return new Vec3(ax, 0, az).normalize();
    }

    public Heading getHeading(BlockState state, BlockPos pos) {
        var dir = getPathDir(state);
        var type = state.getValue(TYPE);

        var planeNormal = switch (type) {
            case FLOOR -> new Vec3(0, 1, 0);
            case CEILING -> new Vec3(0, -1, 0);
            case STANDING_RIGHT -> new Vec3(dir.z(), 0, -dir.x());
            case STANDING_LEFT -> new Vec3(-dir.z(), 0, dir.x());
        };

        var origin = Vec3.atCenterOf(pos);

        var planeOrigin = switch (type) {
            case FLOOR -> origin.add(0, -0.5, 0);
            case CEILING -> origin.add(0, 0.5, 0);
            default -> origin;
        };

        if (type.standing()) {
            origin = origin.add(planeNormal.scale(1.5));
        }

        return new Heading(origin, dir, planeOrigin, planeNormal, state.getValue(POWERED));
    }

    public double getDetectBoxOffset(BlockState state) {
        return state.getValue(TYPE).standing() ? 7.5 : 2;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutopilotSignBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide()) {
            return createTickerHelper(type, AutomobilityBlocks.AUTOPILOT_SIGN_ENTITY.require(), AutopilotSignBlockEntity::tick);
        }

        return super.getTicker(level, state, type);
    }

    public record Heading(Vec3 origin, Vec3 dir, Vec3 planeOrigin, Vec3 limitPlane, boolean stop) {
        public static final Codec<Heading> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Vec3.CODEC.fieldOf("path_origin").forGetter(Heading::origin),
                Vec3.CODEC.fieldOf("path_dir").forGetter(Heading::origin),
                Vec3.CODEC.fieldOf("plane_origin").forGetter(Heading::origin),
                Vec3.CODEC.fieldOf("plane_normal").forGetter(Heading::origin),
                Codec.BOOL.fieldOf("stop").forGetter(Heading::stop)
        ).apply(inst, Heading::new));

        public boolean inFrontOfLimitPlane(Vec3 pos) {
            var originToPos = pos.subtract(planeOrigin());

            return originToPos.dot(limitPlane()) >= 0;
        }

        public boolean withinReasonableDistance(Vec3 pos) {
            return origin().distanceToSqr(pos) < (256 * 256);
        }

        public Vec3 pathToPath(Vec3 pos) {
            var originToPos = pos.subtract(origin());
            return originToPos.cross(dir()).cross(dir()).reverse();
        }

        public Tag toNbt() {
            return CODEC.encode(this, NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        }

        public static Heading fromNbt(CompoundTag tag) {
            return CODEC.decode(NbtOps.INSTANCE, tag).map(Pair::getFirst).result().orElse(null);
        }
    }

    public enum Type implements StringRepresentable {
        STANDING_LEFT("standing_left"),
        STANDING_RIGHT("standing_right"),
        FLOOR("floor"),
        CEILING("ceiling");

        public final String id;

        Type(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public boolean standing() {
            return this == STANDING_LEFT || this == STANDING_RIGHT;
        }
    }
}
