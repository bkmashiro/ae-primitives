package dev.yuzhe.aeprimitives.pneumatic;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MePneumaticAssemblyChamberBlock
        extends AEBaseEntityBlock<MePneumaticAssemblyChamberBlockEntity> {
    public MePneumaticAssemblyChamberBlock(Properties properties) {
        super(properties);
    }

    void bind(BlockEntityType<MePneumaticAssemblyChamberBlockEntity> type) {
        setBlockEntity(MePneumaticAssemblyChamberBlockEntity.class, type, null,
                (level, pos, state, blockEntity) -> blockEntity.serverTick());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        var machine = getBlockEntity(level, pos);
        if (machine == null || !(stack.getItem() instanceof PneumaticAssemblyHeadItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!machine.installHead(stack)) return ItemInteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        var machine = getBlockEntity(level, pos);
        if (machine == null) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var removed = machine.removeHead();
        if (removed.isEmpty()) return InteractionResult.PASS;
        player.getInventory().placeItemBackInInventory(removed.get());
        return InteractionResult.CONSUME;
    }
}
