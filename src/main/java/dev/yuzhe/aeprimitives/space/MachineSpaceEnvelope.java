package dev.yuzhe.aeprimitives.space;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public record MachineSpaceEnvelope(int version, ResourceLocation blockId, CompoundTag blockState, CompoundTag configuration) {
    public static final int CURRENT_VERSION = 1;

    public static MachineSpaceEnvelope capture(ResourceLocation blockId, BlockState state, CompoundTag configuration) {
        return new MachineSpaceEnvelope(CURRENT_VERSION, blockId, NbtUtils.writeBlockState(state), configuration.copy());
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        tag.putString("block", blockId.toString());
        tag.put("state", blockState.copy());
        tag.put("configuration", configuration.copy());
        return tag;
    }

    public static MachineSpaceEnvelope decode(CompoundTag tag) {
        if (tag.getInt("version") != CURRENT_VERSION || !tag.contains("block") || !tag.contains("state") || !tag.contains("configuration")) return null;
        ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("block"));
        if (blockId == null) return null;
        return new MachineSpaceEnvelope(CURRENT_VERSION, blockId, tag.getCompound("state").copy(), tag.getCompound("configuration").copy());
    }

    public BlockState resolveState(HolderLookup.Provider registries) {
        return NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), blockState);
    }
}
