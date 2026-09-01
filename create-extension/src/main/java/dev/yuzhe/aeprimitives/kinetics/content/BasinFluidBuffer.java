package dev.yuzhe.aeprimitives.kinetics.content;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** External fills become recipe inputs; external drains expose completed outputs. */
final class BasinFluidBuffer implements IFluidHandler {
    static final int TANKS = 4;
    static final int CAPACITY = 4_000;
    private final FluidTank[] inputs = tanks();
    private final FluidTank[] outputs = tanks();
    private final Runnable changed;

    BasinFluidBuffer(Runnable changed) {
        this.changed = changed;
    }

    FluidStack input(int tank) { return inputs[tank].getFluid(); }
    FluidStack firstInput() {
        for (var tank : inputs) if (!tank.isEmpty()) return tank.getFluid().copy();
        return FluidStack.EMPTY;
    }

    boolean canQueue(List<FluidStack> stacks) {
        var simulated = new FluidTank[TANKS];
        for (int i = 0; i < TANKS; i++) {
            simulated[i] = new FluidTank(CAPACITY);
            simulated[i].setFluid(outputs[i].getFluid().copy());
        }
        for (var stack : stacks) if (fillInto(simulated, stack, FluidAction.EXECUTE) != stack.getAmount()) return false;
        return true;
    }

    void queue(List<FluidStack> stacks) {
        for (var stack : stacks) fillInto(outputs, stack, FluidAction.EXECUTE);
        changed.run();
    }

    void consumeInput(int tank, int amount) {
        inputs[tank].drain(amount, FluidAction.EXECUTE);
        changed.run();
    }

    void write(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < TANKS; i++) {
            tag.put("input" + i, inputs[i].writeToNBT(registries, new CompoundTag()));
            tag.put("output" + i, outputs[i].writeToNBT(registries, new CompoundTag()));
        }
    }

    void read(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < TANKS; i++) {
            if (tag.contains("input" + i)) inputs[i].readFromNBT(registries, tag.getCompound("input" + i));
            if (tag.contains("output" + i)) outputs[i].readFromNBT(registries, tag.getCompound("output" + i));
        }
    }

    @Override public int getTanks() { return TANKS * 2; }
    @Override public FluidStack getFluidInTank(int tank) { return tank < TANKS ? inputs[tank].getFluid() : outputs[tank - TANKS].getFluid(); }
    @Override public int getTankCapacity(int tank) { return CAPACITY; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank < TANKS; }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        int filled = fillInto(inputs, resource, action);
        if (filled > 0 && action.execute()) changed.run();
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        int remaining = resource.getAmount();
        var drained = resource.copyWithAmount(0);
        for (var tank : outputs) {
            if (remaining == 0 || !FluidStack.isSameFluidSameComponents(resource, tank.getFluid())) continue;
            var part = tank.drain(remaining, action);
            drained.grow(part.getAmount());
            remaining -= part.getAmount();
        }
        if (!drained.isEmpty() && action.execute()) changed.run();
        return drained;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (var tank : outputs) {
            if (tank.isEmpty()) continue;
            var drained = tank.drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute()) changed.run();
            return drained;
        }
        return FluidStack.EMPTY;
    }

    private static int fillInto(FluidTank[] tanks, FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        int remaining = resource.getAmount();
        for (var tank : tanks) {
            if (remaining == 0) break;
            if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) continue;
            int accepted = tank.fill(resource.copyWithAmount(remaining), action);
            remaining -= accepted;
        }
        return resource.getAmount() - remaining;
    }

    private static FluidTank[] tanks() {
        var result = new FluidTank[TANKS];
        for (int i = 0; i < result.length; i++) result[i] = new FluidTank(CAPACITY);
        return result;
    }
}
