package dev.yuzhe.aeprimitives.kinetics.content;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** One pattern-provider dispatch. Inputs are owned here until this independent lane completes. */
final class DispatchedOperationPlan {
    private static final String ITEMS = "items";
    private static final String FLUIDS = "fluids";
    private static final String OUTPUTS = "outputs";
    private static final String REMAINDERS = "remainders";
    private static final String WORK = "work";

    private final List<ItemStack> items;
    private final List<FluidStack> fluids;
    private final List<ItemStack> outputs;
    private final List<ItemStack> remainders;
    private float work;

    private DispatchedOperationPlan(
            List<ItemStack> items,
            List<FluidStack> fluids,
            List<ItemStack> outputs,
            List<ItemStack> remainders,
            float work) {
        this.items = copyItems(items);
        this.fluids = copyFluids(fluids);
        this.outputs = copyItems(outputs);
        this.remainders = copyItems(remainders);
        this.work = work;
    }

    static DispatchedOperationPlan claim(OperationStepSpec step, KeyCounter[] holders) {
        if (holders.length != step.inputs().size()) return null;
        var items = new ArrayList<ItemStack>();
        var fluids = new ArrayList<FluidStack>();
        var remainders = new ArrayList<ItemStack>();
        var claims = new ArrayList<Claim>();

        for (int index = 0; index < holders.length; index++) {
            var holder = holders[index];
            var entry = holder.getFirstEntry();
            if (entry == null) return null;
            var input = step.inputs().get(index);
            var selected = input.alternatives().stream()
                    .filter(candidate -> candidate.what().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (selected == null || selected.amount() <= 0 || entry.getLongValue() < selected.amount()) return null;
            if (selected.amount() > Integer.MAX_VALUE) return null;
            int amount = (int) selected.amount();

            if (entry.getKey() instanceof AEItemKey itemKey) {
                var stack = itemKey.toStack(amount);
                if (stack.isEmpty() || amount > stack.getMaxStackSize()) return null;
                items.add(stack);
                if (input.remainingKey() instanceof AEItemKey remaining) {
                    remainders.add(remaining.toStack(amount));
                } else if (input.remainingKey() != null) {
                    return null;
                }
            } else if (entry.getKey() instanceof AEFluidKey fluidKey) {
                if (input.remainingKey() != null || amount > BasinFluidBuffer.CAPACITY) return null;
                fluids.add(fluidKey.toStack(amount));
            } else {
                return null;
            }
            claims.add(new Claim(holder, entry.getKey(), selected.amount()));
        }

        var outputs = new ArrayList<ItemStack>();
        for (var output : step.outputs()) {
            if (!(output.what() instanceof AEItemKey key)
                    || output.amount() <= 0
                    || output.amount() > Integer.MAX_VALUE) return null;
            int amount = (int) output.amount();
            var stack = key.toStack(amount);
            if (stack.isEmpty() || amount > stack.getMaxStackSize()) return null;
            outputs.add(stack);
        }

        claims.forEach(claim -> claim.holder().remove(claim.key(), claim.amount()));
        return new DispatchedOperationPlan(items, fluids, outputs, remainders, 0);
    }

    void advance(float amount) {
        work = Math.min(KineticMachineBlockEntity.WORK_PER_RECIPE, work + Math.max(0, amount));
    }

    boolean ready() {
        return work >= KineticMachineBlockEntity.WORK_PER_RECIPE;
    }

    boolean canComplete(KineticMachineBlockEntity machine) {
        var queued = new ArrayList<ItemStack>(outputs.size() + remainders.size());
        queued.addAll(outputs);
        queued.addAll(remainders);
        return machine.canQueueAll(queued);
    }

    void complete(KineticMachineBlockEntity machine) {
        var queued = new ArrayList<ItemStack>(outputs.size() + remainders.size());
        queued.addAll(outputs);
        queued.addAll(remainders);
        machine.queueAll(queued);
    }

    List<ItemStack> recoverableItems() {
        var result = new ArrayList<ItemStack>(items.size());
        result.addAll(copyItems(items));
        return result;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put(ITEMS, saveItems(items, registries));
        tag.put(OUTPUTS, saveItems(outputs, registries));
        tag.put(REMAINDERS, saveItems(remainders, registries));
        var fluidTags = new ListTag();
        for (var fluid : fluids) fluidTags.add(fluid.saveOptional(registries));
        tag.put(FLUIDS, fluidTags);
        tag.putFloat(WORK, work);
        return tag;
    }

    static DispatchedOperationPlan load(CompoundTag tag, HolderLookup.Provider registries) {
        var fluids = new ArrayList<FluidStack>();
        var fluidTags = tag.getList(FLUIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidTags.size(); i++) {
            var fluid = FluidStack.parseOptional(registries, fluidTags.getCompound(i));
            if (!fluid.isEmpty()) fluids.add(fluid);
        }
        return new DispatchedOperationPlan(
                loadItems(tag.getList(ITEMS, Tag.TAG_COMPOUND), registries),
                fluids,
                loadItems(tag.getList(OUTPUTS, Tag.TAG_COMPOUND), registries),
                loadItems(tag.getList(REMAINDERS, Tag.TAG_COMPOUND), registries),
                Math.max(0, tag.getFloat(WORK)));
    }

    private static ListTag saveItems(List<ItemStack> stacks, HolderLookup.Provider registries) {
        var result = new ListTag();
        for (var stack : stacks) result.add(stack.saveOptional(registries));
        return result;
    }

    private static List<ItemStack> loadItems(ListTag tags, HolderLookup.Provider registries) {
        var result = new ArrayList<ItemStack>();
        for (int i = 0; i < tags.size(); i++) {
            var stack = ItemStack.parseOptional(registries, tags.getCompound(i));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    private static List<ItemStack> copyItems(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static List<FluidStack> copyFluids(List<FluidStack> stacks) {
        return stacks.stream().map(FluidStack::copy).toList();
    }

    private record Claim(KeyCounter holder, appeng.api.stacks.AEKey key, long amount) {}
}
