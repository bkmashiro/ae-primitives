package dev.yuzhe.aeprimitives.metrics;

import appeng.block.AEBaseEntityBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PhysicalMetricDisplayBlock extends AEBaseEntityBlock<PhysicalMetricDisplayBlockEntity> {
    private static final MapCodec<PhysicalMetricDisplayBlock> CODEC = simpleCodec(PhysicalMetricDisplayBlock::new);

    public PhysicalMetricDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    public void bind(BlockEntityType<PhysicalMetricDisplayBlockEntity> type) {
        setBlockEntity(PhysicalMetricDisplayBlockEntity.class, type, null,
                (level, pos, state, blockEntity) -> blockEntity.serverTick());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        var display = getBlockEntity(level, pos);
        if (display == null) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var selected = display.cycleMetric();
        player.displayClientMessage(selected == null
                ? Component.translatable("message.aeprimitives.metric.none")
                : Component.translatable("message.aeprimitives.metric.selected", selected.toString()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case NORTH -> Shapes.or(box(1, 1, 0, 15, 15, 4), box(6, 6, 4, 10, 10, 16));
            case SOUTH -> Shapes.or(box(1, 1, 12, 15, 15, 16), box(6, 6, 0, 10, 10, 12));
            case WEST -> Shapes.or(box(0, 1, 1, 4, 15, 15), box(4, 6, 6, 16, 10, 10));
            case EAST -> Shapes.or(box(12, 1, 1, 16, 15, 15), box(0, 6, 6, 12, 10, 10));
            default -> Shapes.block();
        };
    }

    @Override
    protected MapCodec<? extends AEBaseEntityBlock<PhysicalMetricDisplayBlockEntity>> codec() {
        return CODEC;
    }
}
