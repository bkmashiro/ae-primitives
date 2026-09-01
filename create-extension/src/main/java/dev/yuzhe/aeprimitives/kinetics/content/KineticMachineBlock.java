package dev.yuzhe.aeprimitives.kinetics.content;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide() && getBlockEntity(level, pos) instanceof KineticMachineBlockEntity machine) {
            machine.markGridTopologyDirty();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != KineticMachineKind.FAN) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var machine = getBlockEntity(level, pos);
        if (machine == null) return ItemInteractionResult.FAIL;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        var remainder = machine.installCatalyst(stack);
        if (remainder.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!remainder.get().isEmpty()) player.getInventory().placeItemBackInInventory(remainder.get());
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (kind != KineticMachineKind.FAN || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        var machine = getBlockEntity(level, pos);
        if (machine == null) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var removed = machine.removeCatalyst();
        if (removed.isEmpty()) return InteractionResult.PASS;
        if (!removed.get().isEmpty()) player.getInventory().placeItemBackInInventory(removed.get());
        return InteractionResult.CONSUME;
    }

    @Override protected MapCodec<? extends KineticBlock> codec() { return simpleCodec(properties -> new KineticMachineBlock(kind, properties)); }
}
