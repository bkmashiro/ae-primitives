package dev.yuzhe.aeprimitives.kinetics.content;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class KineticMachineBlock extends KineticBlock implements IBE<KineticMachineBlockEntity> {
    private final KineticMachineKind kind;

    public KineticMachineBlock(KineticMachineKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public KineticMachineKind kind() { return kind; }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == Direction.Axis.Y;
    }

    @Override public Class<KineticMachineBlockEntity> getBlockEntityClass() { return KineticMachineBlockEntity.class; }
    @Override public BlockEntityType<? extends KineticMachineBlockEntity> getBlockEntityType() { return KineticsContent.MACHINE_ENTITY.get(); }
    @Override protected MapCodec<? extends KineticBlock> codec() { return simpleCodec(properties -> new KineticMachineBlock(kind, properties)); }
}
