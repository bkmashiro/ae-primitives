package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** One transactional Filling or Emptying operation. */
record FillingProcessPlan(FillingRecipe filling, EmptyingRecipe emptying, int[] fluidUse) {
    int availableRuns(KineticMachineBlockEntity machine, int cap) {
        int runs = Math.min(cap, machine.inventory().getStackInSlot(0).getCount());
        for (int tank = 0; tank < fluidUse.length; tank++) {
            if (fluidUse[tank] > 0) {
                runs = Math.min(runs, machine.basinFluids().input(tank).getAmount() / fluidUse[tank]);
            }
        }
        return runs;
    }

    static FillingProcessPlan find(KineticMachineBlockEntity machine, ServerLevel level) {
        var input = machine.inventory().getStackInSlot(0);
        if (input.isEmpty()) return null;
        var recipeInput = new SingleRecipeInput(input.copyWithCount(1));

        for (var holder : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.FILLING.getType())) {
            Object value = holder.value();
            if (!(value instanceof FillingRecipe recipe) || !recipe.matches(recipeInput, level)) continue;
            var use = allocateFluid(machine, recipe.getRequiredFluid());
            if (use != null) return new FillingProcessPlan(recipe, null, use);
        }
        for (var holder : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.EMPTYING.getType())) {
            Object value = holder.value();
            if (!(value instanceof EmptyingRecipe recipe) || !recipe.matches(recipeInput, level)) continue;
            return new FillingProcessPlan(null, recipe, new int[BasinFluidBuffer.TANKS]);
        }
        return null;
    }

    boolean commit(KineticMachineBlockEntity machine, ServerLevel level) {
        var recipe = filling != null ? filling : emptying;
        var itemOutputs = recipe.rollResults(level.random);
        List<FluidStack> fluidOutputs = emptying == null || emptying.getResultingFluid().isEmpty()
                ? List.of() : List.of(emptying.getResultingFluid().copy());
        if (!machine.canQueueAll(itemOutputs) || !machine.basinFluids().canQueue(fluidOutputs)) return false;
        machine.inventory().extractItem(0, 1, false);
        for (int tank = 0; tank < fluidUse.length; tank++) {
            if (fluidUse[tank] > 0) machine.basinFluids().consumeInput(tank, fluidUse[tank]);
        }
        machine.queueAll(itemOutputs);
        machine.basinFluids().queue(fluidOutputs);
        return true;
    }

    private static int[] allocateFluid(KineticMachineBlockEntity machine, SizedFluidIngredient ingredient) {
        var use = new int[BasinFluidBuffer.TANKS];
        int remaining = ingredient.amount();
        for (int tank = 0; tank < use.length && remaining > 0; tank++) {
            var stack = machine.basinFluids().input(tank);
            if (!ingredient.test(stack)) continue;
            int taken = Math.min(remaining, stack.getAmount());
            use[tank] = taken;
            remaining -= taken;
        }
        return remaining == 0 ? use : null;
    }
}
