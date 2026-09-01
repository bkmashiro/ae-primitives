package dev.yuzhe.aeprimitives.powah.content;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
public final class MeEnergizingChamberBlock extends BaseEntityBlock {
 private static final MapCodec<MeEnergizingChamberBlock> CODEC=simpleCodec(x->new MeEnergizingChamberBlock());
 public MeEnergizingChamberBlock(){super(BlockBehaviour.Properties.of().strength(4.0f).requiresCorrectToolForDrops());}
 protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
 public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new MeEnergizingChamberBlockEntity(p,s);}
 protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
 public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide?null:createTickerHelper(t,PowahContent.ENERGIZING_CHAMBER_ENTITY.get(),MeEnergizingChamberBlockEntity::serverTick);}
 @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos neighborPos,boolean moved){super.neighborChanged(state,level,pos,block,neighborPos,moved);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof MeEnergizingChamberBlockEntity machine){machine.invalidateSpatialParallelism();machine.markGridTopologyDirty();}}
}
