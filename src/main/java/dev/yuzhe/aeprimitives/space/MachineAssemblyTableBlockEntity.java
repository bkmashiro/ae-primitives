package dev.yuzhe.aeprimitives.space;

import dev.yuzhe.aeprimitives.content.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class MachineAssemblyTableBlockEntity extends BlockEntity {
    private final ItemStackHandler componentSlot = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(ModContent.MACHINE_SPACE_COMPONENT.get()); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    public MachineAssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ASSEMBLY_TABLE_ENTITY.get(), pos, state);
    }

    public ItemStackHandler componentSlot() { return componentSlot; }

    public boolean operate() {
        if (level == null || level.isClientSide) return false;
        return componentSlot.getStackInSlot(0).isEmpty() ? pack() : unpack();
    }

    private boolean pack() {
        BlockPos target = targetPos();
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (!(blockEntity instanceof MachineSpacePackable packable) || !packable.canPackIntoMachineSpace()) return false;
        BlockState state = level.getBlockState(target);
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        MachineSpaceEnvelope envelope = MachineSpaceEnvelope.capture(
                blockId, state, packable.writeMachineSpaceConfiguration(level.registryAccess()));
        ItemStack component = MachineSpaceComponentItem.create(ModContent.MACHINE_SPACE_COMPONENT.get(), envelope);
        if (!level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState())) return false;
        componentSlot.setStackInSlot(0, component);
        setChanged();
        return true;
    }

    private boolean unpack() {
        BlockPos target = targetPos();
        if (!level.getBlockState(target).isAir()) return false;
        ItemStack component = componentSlot.getStackInSlot(0);
        MachineSpaceEnvelope envelope = MachineSpaceComponentItem.read(component);
        if (envelope == null) return false;
        BlockState state = envelope.resolveState(level.registryAccess());
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(envelope.blockId())) return false;
        if (!level.setBlockAndUpdate(target, state)) return false;
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (!(blockEntity instanceof MachineSpacePackable packable)
                || !packable.restoreMachineSpaceConfiguration(envelope.configuration(), level.registryAccess())) {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            return false;
        }
        componentSlot.setStackInSlot(0, ItemStack.EMPTY);
        setChanged();
        return true;
    }

    private BlockPos targetPos() {
        return worldPosition.relative(getBlockState().getValue(MachineAssemblyTableBlock.FACING));
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("component", componentSlot.serializeNBT(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("component")) componentSlot.deserializeNBT(registries, tag.getCompound("component"));
    }
}
