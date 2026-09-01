package dev.yuzhe.aeprimitives.space;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Optional machine-side contract for safe packaging into a machine space component. */
public interface MachineSpacePackable {
    boolean canPackIntoMachineSpace();
    CompoundTag writeMachineSpaceConfiguration(HolderLookup.Provider registries);
    boolean restoreMachineSpaceConfiguration(CompoundTag configuration, HolderLookup.Provider registries);
}
