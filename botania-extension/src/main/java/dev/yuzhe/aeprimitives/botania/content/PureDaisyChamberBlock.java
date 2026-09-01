package dev.yuzhe.aeprimitives.botania.content;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
public final class PureDaisyChamberBlock extends BaseEntityBlock {
 public static final MapCodec<PureDaisyChamberBlock> CODEC=simpleCodec(p->new PureDaisyChamberBlock());
 public PureDaisyChamberBlock(){super(BlockBehaviour.Properties.of().strength(4,8).requiresCorrectToolForDrops());}
 protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
 public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
 public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new PureDaisyChamberBlockEntity(p,s);}
 @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide?null:createTickerHelper(t,BotaniaContent.PURE_DAISY_CHAMBER_ENTITY.get(),PureDaisyChamberBlockEntity::serverTick);}
}
