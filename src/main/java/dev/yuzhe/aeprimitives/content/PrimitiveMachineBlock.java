package dev.yuzhe.aeprimitives.content;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PrimitiveMachineBlock extends AEBaseEntityBlock<PrimitiveMachineBlockEntity> {
    private final MachineKind kind;
    public PrimitiveMachineBlock(MachineKind kind, Properties properties) { super(properties); this.kind = kind; }
    public MachineKind kind() { return kind; }
    void bind(BlockEntityType<PrimitiveMachineBlockEntity> type) {
        setBlockEntity(PrimitiveMachineBlockEntity.class, type, null,
                (level, pos, state, be) -> be.serverTick());
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var be = getBlockEntity(level, pos);
            if (be != null) serverPlayer.openMenu(be, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide() && kind == MachineKind.FOUNDRY
                && getBlockEntity(level, pos) instanceof PrimitiveMachineBlockEntity machine) {
            machine.markStructureDirty();
        }
    }
}
