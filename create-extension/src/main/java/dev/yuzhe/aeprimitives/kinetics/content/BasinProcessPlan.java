package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** A one-lane, fully allocated Basin recipe transaction. */
record BasinProcessPlan(ProcessingRecipe<?, ?> recipe, int[] itemUse, int[] fluidUse) {
    int availableRuns(KineticMachineBlockEntity machine, int cap) {
        int runs = cap;
        for (int slot = 0; slot < itemUse.length; slot++) {
            if (itemUse[slot] > 0) runs = Math.min(runs, machine.inventory().getStackInSlot(slot).getCount() / itemUse[slot]);
        }
        for (int tank = 0; tank < fluidUse.length; tank++) {
            if (fluidUse[tank] > 0) runs = Math.min(runs, machine.basinFluids().input(tank).getAmount() / fluidUse[tank]);
        }
        return runs;
    }

    static BasinProcessPlan find(KineticMachineBlockEntity machine, ServerLevel level) {
        var heat = BasinBlockEntity.getHeatLevelOf(level.getBlockState(machine.getBlockPos().below()));
        for (var type : List.of(AllRecipeTypes.MIXING, AllRecipeTypes.COMPACTING)) {
            for (var holder : level.getRecipeManager().getAllRecipesFor(type.getType())) {
                if (!(holder.value() instanceof ProcessingRecipe<?, ?> recipe)) continue;
                if (!recipe.getRequiredHeat().testBlazeBurner(heat)) continue;
                var itemUse = allocateItems(machine, recipe);
                if (itemUse == null) continue;
                var fluidUse = allocateFluids(machine, recipe);
                if (fluidUse != null) return new BasinProcessPlan(recipe, itemUse, fluidUse);
            }
        }
        return null;
    }

    boolean commit(KineticMachineBlockEntity machine, ServerLevel level) {
        var itemOutputs = recipe.rollResults(level.random);
        var fluidOutputs = new ArrayList<FluidStack>();
        for (var stack : recipe.getFluidResults()) fluidOutputs.add(stack.copy());
        if (!machine.canQueueAll(itemOutputs) || !machine.basinFluids().canQueue(fluidOutputs)) return false;
        for (int slot = 0; slot < itemUse.length; slot++) {
            if (itemUse[slot] > 0) machine.inventory().extractItem(slot, itemUse[slot], false);
        }
        for (int tank = 0; tank < fluidUse.length; tank++) {
            if (fluidUse[tank] > 0) machine.basinFluids().consumeInput(tank, fluidUse[tank]);
        }
        machine.queueAll(itemOutputs);
        machine.basinFluids().queue(fluidOutputs);
        return true;
    }

    private static int[] allocateItems(KineticMachineBlockEntity machine, ProcessingRecipe<?, ?> recipe) {
        var use = new int[KineticMachineBlockEntity.BASIN_INPUT_SLOTS];
        for (var ingredient : recipe.getIngredients()) {
            boolean found = false;
            for (int slot = 0; slot < use.length; slot++) {
                var stack = machine.inventory().getStackInSlot(slot);
                if (stack.getCount() <= use[slot] || !ingredient.test(stack)) continue;
                use[slot]++;
                found = true;
                break;
            }
            if (!found) return null;
        }
        return use;
    }

    private static int[] allocateFluids(KineticMachineBlockEntity machine, ProcessingRecipe<?, ?> recipe) {
        var use = new int[BasinFluidBuffer.TANKS];
        for (var ingredient : recipe.getFluidIngredients()) {
            int remaining = ingredient.amount();
            for (int tank = 0; tank < use.length && remaining > 0; tank++) {
                var stack = machine.basinFluids().input(tank);
                if (!ingredient.test(stack)) continue;
                int available = stack.getAmount() - use[tank];
                int taken = Math.min(remaining, Math.max(0, available));
                use[tank] += taken;
                remaining -= taken;
            }
            if (remaining > 0) return null;
        }
        return use;
    }
}
