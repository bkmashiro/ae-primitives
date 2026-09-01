package dev.yuzhe.aeprimitives.powah.content;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.items.ItemStackHandler;
import owmii.powah.block.energizing.EnergizingRecipe;
import owmii.powah.recipe.Recipes;

/** Runs packaged Energizing Chambers against an explicit, exclusive FE port. */
public final class EnergizingVirtualLaneExecutor implements VirtualMachineLaneExecutor {
    public static final EnergizingVirtualLaneExecutor INSTANCE = new EnergizingVirtualLaneExecutor();

    @Override public boolean supports(MachineSpaceEnvelope envelope) {
        return envelope.blockId().equals(BuiltInRegistries.BLOCK.getKey(PowahContent.ENERGIZING_CHAMBER.get()));
    }

    @Override public LanePlan prepare(LaneContext context) {
        ItemStack emitter = context.envelope().configuration().contains("emitter")
                ? ItemStack.parseOptional(context.level().registryAccess(),
                context.envelope().configuration().getCompound("emitter")) : ItemStack.EMPTY;
        int rate = MeEnergizingChamberBlockEntity.emitterRate(emitter) * emitter.getCount();
        if (rate <= 0) return null;
        EnergizingRecipe recipe = findRecipe(context.level(), context.inputs());
        if (recipe == null) return null;
        ItemStack output = recipe.getResultItem(context.level().registryAccess()).copy();
        int required = Math.max(1, (int) Math.ceil(recipe.getScaledEnergy()));
        EnergizingFactoryEnergyPortBlockEntity port = findPort(context);
        return new LanePlan() {
            @Override public int durationTicks() { return required; }
            @Override public int workPerTick() {
                return port == null ? 0 : port.extractForLane(context.factoryPos(), context.lane(), rate, false);
            }
            @Override public double idleAePower() { return 4.0; }
            @Override public List<ItemStack> previewOutputs() { return List.of(output.copy()); }
            @Override public void setActive(boolean active) {
                if (port != null) port.requestLane(context.factoryPos(), context.lane(), active);
            }
            @Override public boolean resourcesAvailable() {
                return port != null && port.extractForLane(context.factoryPos(), context.lane(), 1, true) > 0;
            }
            @Override public List<ItemStack> complete(ItemStackHandler inputs) {
                EnergizingRecipe current = findRecipe(context.level(), inputs);
                if (current == null) return null;
                ItemStack currentOutput = current.getResultItem(context.level().registryAccess()).copy();
                if (!ItemStack.isSameItemSameComponents(output, currentOutput)
                        || Math.max(1, (int) Math.ceil(current.getScaledEnergy())) != required) return null;
                for (int slot = 0; slot < 6; slot++)
                    if (!inputs.getStackInSlot(slot).isEmpty()) inputs.extractItem(slot, 1, false);
                return List.of(currentOutput);
            }
        };
    }

    @Override public void release(LaneContext context) {
        EnergizingFactoryEnergyPortBlockEntity port = findPort(context);
        if (port != null) port.requestLane(context.factoryPos(), context.lane(), false);
    }

    private static EnergizingFactoryEnergyPortBlockEntity findPort(LaneContext context) {
        for (Direction direction : Direction.values()) {
            if (context.level().getBlockEntity(context.factoryPos().relative(direction))
                    instanceof EnergizingFactoryEnergyPortBlockEntity port) return port;
        }
        return null;
    }

    private static EnergizingRecipe findRecipe(net.minecraft.server.level.ServerLevel level, ItemStackHandler inputs) {
        return level.getRecipeManager().getRecipeFor(Recipes.ENERGIZING.get(), new OrbInput(inputs), level)
                .map(holder -> holder.value()).orElse(null);
    }

    private record OrbInput(ItemStackHandler inventory) implements RecipeInput {
        @Override public ItemStack getItem(int index) {
            return index == 0 ? ItemStack.EMPTY : inventory.getStackInSlot(index - 1).copy();
        }
        @Override public int size() { return 7; }
    }

    private EnergizingVirtualLaneExecutor() {}
}
