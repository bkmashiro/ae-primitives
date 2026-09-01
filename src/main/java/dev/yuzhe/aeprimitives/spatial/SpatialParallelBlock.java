package dev.yuzhe.aeprimitives.spatial;

import com.mojang.serialization.MapCodec;
import dev.yuzhe.aeprimitives.content.MachineTier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A passive, one-sided parallel sidecar. The adjacent machine owns all runtime state. */
public final class SpatialParallelBlock extends DirectionalBlock {
    private final MachineTier tier;
    private final int addedLanes;

    public SpatialParallelBlock(MachineTier tier, int addedLanes, Properties properties) {
        super(properties);
        this.tier = tier;
        this.addedLanes = addedLanes;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    public MachineTier tier() { return tier; }
    public int addedLanes() { return addedLanes; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(properties -> new SpatialParallelBlock(tier, addedLanes, properties));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Point back into the face the player attached the sidecar to.
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) return;
        if (oldState.getBlock() instanceof SpatialParallelBlock oldBlock) notifyOwner(level, pos, oldState, oldBlock);
        notifyOwner(level, pos, state, this);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && state.getBlock() != newState.getBlock()) notifyOwner(level, pos, state, this);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void notifyOwner(Level level, BlockPos sidecarPos, BlockState state, SpatialParallelBlock block) {
        var ownerPos = sidecarPos.relative(state.getValue(FACING));
        if (level.getBlockEntity(ownerPos) instanceof SpatialParallelHost host) host.invalidateSpatialParallelism();
    }
}
