package dev.yuzhe.aeprimitives.content;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class HeterogeneousFactoryBlock extends AEBaseEntityBlock<HeterogeneousFactoryBlockEntity> {
    public HeterogeneousFactoryBlock(Properties properties) {
        super(properties);
    }

    void bind(BlockEntityType<HeterogeneousFactoryBlockEntity> type) {
        setBlockEntity(HeterogeneousFactoryBlockEntity.class, type, null,
                (level, pos, state, blockEntity) -> blockEntity.serverTick());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var factory = getBlockEntity(level, pos);
            if (factory != null) serverPlayer.openMenu(factory, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
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
