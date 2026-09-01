package dev.yuzhe.aeprimitives.farmersdelight.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CookingFactoryHeatPortBlock extends BaseEntityBlock {
    public static final MapCodec<CookingFactoryHeatPortBlock> CODEC = simpleCodec(CookingFactoryHeatPortBlock::new);

    public CookingFactoryHeatPortBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CookingFactoryHeatPortBlockEntity(pos, state);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                               BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, block, neighborPos, moved);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CookingFactoryHeatPortBlockEntity port)
            port.wakeFactory();
    }
}
