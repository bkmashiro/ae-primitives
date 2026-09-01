package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** One deployer application without fake-player world interaction. */
record DeployerProcessPlan(ItemApplicationRecipe recipe) {
    static DeployerProcessPlan find(KineticMachineBlockEntity machine, ServerLevel level) {
        var processed = machine.inventory().getStackInSlot(KineticMachineBlockEntity.PROCESS_INPUT_SLOT);
        var held = machine.inventory().getStackInSlot(KineticMachineBlockEntity.TOOL_SLOT);
        if (processed.isEmpty() || held.isEmpty()) return null;
        var input = new ItemStackHandler(2);
        input.setStackInSlot(0, processed.copyWithCount(1));
        input.setStackInSlot(1, held.copyWithCount(1));
        var wrapper = new RecipeWrapper(input);
        for (var holder : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.DEPLOYING.getType())) {
            Object value = holder.value();
            if (value instanceof ItemApplicationRecipe recipe && recipe.matches(wrapper, level)) {
                return new DeployerProcessPlan(recipe);
            }
        }
        return null;
    }

    int availableRuns(KineticMachineBlockEntity machine, int cap) {
        int runs = Math.min(cap, machine.inventory().getStackInSlot(KineticMachineBlockEntity.PROCESS_INPUT_SLOT).getCount());
        if (recipe.shouldKeepHeldItem()) return runs;
        var held = machine.inventory().getStackInSlot(KineticMachineBlockEntity.TOOL_SLOT);
        if (held.isDamageableItem()) {
            return Math.min(runs, Math.max(0, held.getMaxDamage() - held.getDamageValue()));
        }
        return Math.min(runs, held.getCount());
    }

    boolean commit(KineticMachineBlockEntity machine, ServerLevel level) {
        var outputs = recipe.rollResults(level.random);
        var remainder = toolRemainder(machine.inventory().getStackInSlot(KineticMachineBlockEntity.TOOL_SLOT));
        if (!machine.canQueueAll(outputs) || (!remainder.isEmpty() && !machine.canQueueAll(List.of(remainder)))) {
            return false;
        }
        machine.inventory().extractItem(KineticMachineBlockEntity.PROCESS_INPUT_SLOT, 1, false);
        if (!recipe.shouldKeepHeldItem()) consumeHeld(machine);
        machine.queueAll(outputs);
        if (!remainder.isEmpty()) machine.queueAll(List.of(remainder));
        return true;
    }

    private ItemStack toolRemainder(ItemStack held) {
        if (recipe.shouldKeepHeldItem() || held.isDamageableItem()) return ItemStack.EMPTY;
        return held.getCraftingRemainingItem();
    }

    private void consumeHeld(KineticMachineBlockEntity machine) {
        var held = machine.inventory().getStackInSlot(KineticMachineBlockEntity.TOOL_SLOT);
        if (held.isDamageableItem()) {
            var damaged = held.copy();
            damaged.setDamageValue(damaged.getDamageValue() + 1);
            if (damaged.getDamageValue() >= damaged.getMaxDamage()) damaged = ItemStack.EMPTY;
            machine.inventory().setStackInSlot(KineticMachineBlockEntity.TOOL_SLOT, damaged);
        } else {
            machine.inventory().extractItem(KineticMachineBlockEntity.TOOL_SLOT, 1, false);
        }
    }
}
