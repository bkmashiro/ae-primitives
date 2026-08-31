package dev.yuzhe.aeprimitives.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class ResonancePartBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ResonancePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide()) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -2; dz <= 0; dz++) {
                    if (level.getBlockEntity(pos.offset(dx, dy, dz)) instanceof PrimitiveMachineBlockEntity machine
                            && machine.kind() == MachineKind.FOUNDRY) {
                        machine.markStructureDirty();
                    }
                }
            }
        }
    }
}
