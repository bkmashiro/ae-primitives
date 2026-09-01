package dev.yuzhe.aeprimitives.kinetics.content;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Create shaft endpoint that supplies real rotational stress to packaged Kinetics lanes. */
public final class KineticFactoryPortBlock extends KineticBlock implements IBE<KineticFactoryPortBlockEntity> {
    public KineticFactoryPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == Direction.Axis.Y;
    }

    @Override public Class<KineticFactoryPortBlockEntity> getBlockEntityClass() { return KineticFactoryPortBlockEntity.class; }
    @Override public BlockEntityType<? extends KineticFactoryPortBlockEntity> getBlockEntityType() { return KineticsContent.FACTORY_PORT_ENTITY.get(); }
    @Override protected MapCodec<? extends KineticBlock> codec() { return simpleCodec(KineticFactoryPortBlock::new); }
}
