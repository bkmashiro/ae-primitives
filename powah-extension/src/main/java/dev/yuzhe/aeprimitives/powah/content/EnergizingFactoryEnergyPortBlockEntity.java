package dev.yuzhe.aeprimitives.powah.content;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Explicit FE bridge for packaged Energizing Chamber lanes. */
public final class EnergizingFactoryEnergyPortBlockEntity extends BlockEntity {
    private final boolean[] activeLanes = new boolean[HeterogeneousFactoryBlockEntity.LANE_COUNT];
    private BlockPos owner;
    private final EnergyStorage energy = new EnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE) {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) wakeFactory();
            return received;
        }
    };

    public EnergizingFactoryEnergyPortBlockEntity(BlockPos pos, BlockState state) {
        super(PowahContent.ENERGIZING_FACTORY_ENERGY_PORT_ENTITY.get(), pos, state);
    }

    public IEnergyStorage energy() { return energy; }

    public boolean requestLane(BlockPos factoryPos, int lane, boolean active) {
        if (lane < 0 || lane >= activeLanes.length || !isAdjacentFactory(factoryPos)) return false;
        if (active && owner != null && !owner.equals(factoryPos)) return false;
        if (active) owner = factoryPos.immutable();
        activeLanes[lane] = active;
        if (!active && activeLaneCount() == 0) owner = null;
        setChanged();
        return true;
    }

    public int extractForLane(BlockPos factoryPos, int lane, int amount, boolean simulate) {
        if (lane < 0 || lane >= activeLanes.length || !activeLanes[lane]
                || owner == null || !owner.equals(factoryPos) || !isAdjacentFactory(factoryPos)) return 0;
        return energy.extractEnergy(amount, simulate);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        Arrays.fill(activeLanes, false);
        owner = null;
    }

    @Override public void setRemoved() {
        Arrays.fill(activeLanes, false);
        owner = null;
        super.setRemoved();
    }

    private int activeLaneCount() {
        int count = 0;
        for (boolean active : activeLanes) if (active) count++;
        return count;
    }

    private boolean isAdjacentFactory(BlockPos factoryPos) {
        return level != null && worldPosition.distManhattan(factoryPos) == 1
                && level.getBlockEntity(factoryPos) instanceof HeterogeneousFactoryBlockEntity;
    }

    void wakeFactory() {
        if (level == null || level.isClientSide) return;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof HeterogeneousFactoryBlockEntity factory)
                factory.scheduleExternalWork();
        }
    }
}
