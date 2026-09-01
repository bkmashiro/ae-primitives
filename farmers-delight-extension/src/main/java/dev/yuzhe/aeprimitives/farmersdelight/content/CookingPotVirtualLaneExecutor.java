package dev.yuzhe.aeprimitives.farmersdelight.content;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/** Runs packaged ME Cooking Pots while preserving Farmer's Delight recipe and heat contracts. */
public final class CookingPotVirtualLaneExecutor implements VirtualMachineLaneExecutor {
    public static final CookingPotVirtualLaneExecutor INSTANCE = new CookingPotVirtualLaneExecutor();

    @Override public boolean supports(MachineSpaceEnvelope envelope) {
        return envelope.blockId().equals(BuiltInRegistries.BLOCK.getKey(FarmersDelightContent.ME_COOKING_POT.get()));
    }

    @Override public LanePlan prepare(LaneContext context) {
        CookingPotRecipe recipe = findRecipe(context);
        if (recipe == null) return null;
        List<ItemStack> outputs = outputs(recipe, context);
        if (outputs == null) return null;
        CookingFactoryHeatPortBlockEntity port = findPort(context);
        return new LanePlan() {
            @Override public int durationTicks() { return recipe.getCookTime(); }
            @Override public int workPerTick() { return 1; }
            @Override public double idleAePower() { return 2.0; }
            @Override public List<ItemStack> previewOutputs() { return copy(outputs); }
            @Override public void setActive(boolean active) {
                if (port != null) port.requestLane(context.factoryPos(), context.lane(), active);
            }
            @Override public boolean resourcesAvailable() {
                return port != null && port.canRunLane(context.factoryPos());
            }
            @Override public List<ItemStack> complete(ItemStackHandler inputs) {
                CookingPotRecipe current = findRecipe(context.level(), inputs);
                if (current == null) return null;
                List<ItemStack> completed = outputs(current, context.level(), inputs);
                if (completed == null) return null;
                for (int slot = 0; slot < 6; slot++)
                    if (!inputs.getStackInSlot(slot).isEmpty()) inputs.extractItem(slot, 1, false);
                if (!current.getOutputContainer().isEmpty()) inputs.extractItem(6, 1, false);
                return completed;
            }
        };
    }

    @Override public void release(LaneContext context) {
        CookingFactoryHeatPortBlockEntity port = findPort(context);
        if (port != null) port.requestLane(context.factoryPos(), context.lane(), false);
    }

    private static CookingFactoryHeatPortBlockEntity findPort(LaneContext context) {
        for (Direction direction : Direction.values()) {
            if (context.level().getBlockEntity(context.factoryPos().relative(direction))
                    instanceof CookingFactoryHeatPortBlockEntity port) return port;
        }
        return null;
    }

    private static CookingPotRecipe findRecipe(LaneContext context) {
        return findRecipe(context.level(), context.inputs());
    }

    private static CookingPotRecipe findRecipe(net.minecraft.server.level.ServerLevel level, ItemStackHandler inputs) {
        var ingredients = ingredients(inputs);
        return level.getRecipeManager().getRecipeFor(ModRecipeTypes.COOKING.get(), new RecipeWrapper(ingredients), level)
                .map(holder -> holder.value())
                .filter(recipe -> containerAvailable(recipe.getOutputContainer(), inputs.getStackInSlot(6)))
                .orElse(null);
    }

    private static ItemStackHandler ingredients(ItemStackHandler inputs) {
        var ingredients = new ItemStackHandler(6);
        for (int slot = 0; slot < 6; slot++) ingredients.setStackInSlot(slot, inputs.getStackInSlot(slot).copy());
        return ingredients;
    }

    private static boolean containerAvailable(ItemStack required, ItemStack available) {
        return required.isEmpty() || available.getCount() >= required.getCount()
                && ItemStack.isSameItemSameComponents(available, required);
    }

    private static List<ItemStack> outputs(CookingPotRecipe recipe, LaneContext context) {
        return outputs(recipe, context.level(), context.inputs());
    }

    private static List<ItemStack> outputs(CookingPotRecipe recipe, net.minecraft.server.level.ServerLevel level,
                                           ItemStackHandler inputs) {
        ItemStack result = recipe.assemble(new RecipeWrapper(ingredients(inputs)), level.registryAccess());
        if (result.isEmpty()) return null;
        var outputs = new ArrayList<ItemStack>();
        outputs.add(result);
        for (int slot = 0; slot < 6; slot++) {
            ItemStack input = inputs.getStackInSlot(slot);
            if (input.isEmpty()) continue;
            ItemStack remainder = input.getCraftingRemainingItem();
            if (remainder.isEmpty()) {
                var override = CookingPotBlockEntity.INGREDIENT_REMAINDER_OVERRIDES.get(input.getItem());
                if (override != null) remainder = new ItemStack(override);
            }
            if (!remainder.isEmpty()) outputs.add(remainder.copy());
        }
        return List.copyOf(outputs);
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        var copy = new ArrayList<ItemStack>(stacks.size());
        for (ItemStack stack : stacks) copy.add(stack.copy());
        return List.copyOf(copy);
    }

    private CookingPotVirtualLaneExecutor() {}
}
