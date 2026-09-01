package dev.yuzhe.aeprimitives.botania.content;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Runs packaged Mana Pool interfaces through an explicit, real Botania pool. */
public final class ManaPoolVirtualLaneExecutor implements VirtualMachineLaneExecutor {
    public static final ManaPoolVirtualLaneExecutor INSTANCE = new ManaPoolVirtualLaneExecutor();

    @Override public boolean supports(MachineSpaceEnvelope envelope) {
        return envelope.blockId().equals(BuiltInRegistries.BLOCK.getKey(BotaniaContent.MANA_POOL_INTERFACE.get()));
    }

    @Override public LanePlan prepare(LaneContext context) {
        ItemStack input = context.inputs().getStackInSlot(0);
        ManaPoolInterfaceBlockEntity port = findPort(context);
        if (port == null || input.isEmpty()) return null;
        RecipeHolder<ManaInfusionRecipe> recipe = port.matchingFactoryRecipe(input);
        if (recipe == null) return null;
        ItemStack output = recipe.value().getRecipeOutput(context.level().registryAccess(), input.copyWithCount(1));
        if (output.isEmpty()) return null;
        return new LanePlan() {
            @Override public int durationTicks() { return 1; }
            @Override public int workPerTick() { return 1; }
            @Override public double idleAePower() { return 2.0; }
            @Override public List<ItemStack> previewOutputs() { return List.of(output.copy()); }
            @Override public void setActive(boolean active) {
                port.requestFactoryLane(context.factoryPos(), context.lane(), active);
            }
            @Override public boolean resourcesAvailable() {
                return port.canRunFactoryLane(context.factoryPos(), context.lane(), recipe,
                        context.inputs().getStackInSlot(0));
            }
            @Override public List<ItemStack> complete(ItemStackHandler inputs) {
                ItemStack currentInput = inputs.getStackInSlot(0);
                RecipeHolder<ManaInfusionRecipe> currentRecipe = port.matchingFactoryRecipe(currentInput);
                if (currentRecipe == null || !currentRecipe.id().equals(recipe.id())) return null;
                ItemStack completed = port.executeFactoryRecipe(currentRecipe, currentInput);
                if (completed == null || !ItemStack.isSameItemSameComponents(output, completed)
                        || output.getCount() != completed.getCount()) return null;
                inputs.extractItem(0, 1, false);
                return List.of(completed);
            }
        };
    }

    @Override public void release(LaneContext context) {
        ManaPoolInterfaceBlockEntity port = findPort(context);
        if (port != null) port.requestFactoryLane(context.factoryPos(), context.lane(), false);
    }

    private static ManaPoolInterfaceBlockEntity findPort(LaneContext context) {
        for (Direction direction : Direction.values()) {
            if (context.level().getBlockEntity(context.factoryPos().relative(direction))
                    instanceof ManaPoolInterfaceBlockEntity port) return port;
        }
        return null;
    }

    private ManaPoolVirtualLaneExecutor() {}
}
