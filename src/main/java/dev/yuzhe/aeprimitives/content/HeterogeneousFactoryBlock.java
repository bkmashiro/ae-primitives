package dev.yuzhe.aeprimitives.content;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class HeterogeneousFactoryBlock extends AEBaseEntityBlock<HeterogeneousFactoryBlockEntity> {
    public HeterogeneousFactoryBlock(Properties properties) {
        super(properties);
    }

    void bind(BlockEntityType<HeterogeneousFactoryBlockEntity> type) {
        setBlockEntity(HeterogeneousFactoryBlockEntity.class, type, null,
                (level, pos, state, blockEntity) -> blockEntity.serverTick());
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HeterogeneousFactoryBlockEntity factory) {
            for (int slot = 0; slot < factory.inventory().getSlots(); slot++) {
                var stack = factory.inventory().getStackInSlot(slot);
                if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
