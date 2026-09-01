package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.yuzhe.aeprimitives.kinetics.AePrimitivesKinetics;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class KineticVirtualLaneExecutor implements VirtualMachineLaneExecutor {
    public static final KineticVirtualLaneExecutor INSTANCE = new KineticVirtualLaneExecutor();

    @Override
    public boolean supports(MachineSpaceEnvelope envelope) {
        return envelope.blockId().getNamespace().equals(AePrimitivesKinetics.MOD_ID)
                && envelope.blockId().getPath().equals(KineticMachineKind.PRESS.id())
                && KineticMachineKind.PRESS.id().equals(envelope.configuration().getString("kind"));
    }

    @Override
    public LanePlan prepare(LaneContext context) {
        ItemStack input = context.inputs().getStackInSlot(0);
        if (input.isEmpty()) return null;
        ProcessingRecipe<?, ?> recipe = KineticProcessBehavior.CreateRecipe.findRecipe(
                KineticMachineKind.PRESS, context.level(), input);
        if (recipe == null) return null;
        return new PressPlan(context, recipe);
    }

    @Override
    public void release(LaneContext context) {
        KineticFactoryPortBlockEntity port = findPort(context);
        if (port != null) port.requestLane(context.factoryPos(), context.lane(), false);
    }

    private static KineticFactoryPortBlockEntity findPort(LaneContext context) {
        for (Direction direction : Direction.values()) {
            if (context.level().getBlockEntity(context.factoryPos().relative(direction))
                    instanceof KineticFactoryPortBlockEntity port) return port;
        }
        return null;
    }

    private record PressPlan(LaneContext context, ProcessingRecipe<?, ?> recipe) implements LanePlan {
        @Override public int durationTicks() { return (int) KineticMachineBlockEntity.WORK_PER_RECIPE; }
        @Override public int workPerTick() {
            KineticFactoryPortBlockEntity port = findPort(context);
            return port == null ? 1 : Math.max(1, (int) Math.abs(port.getSpeed()));
        }
        @Override public double idleAePower() { return 2.0; }
        @Override public List<ItemStack> previewOutputs() { return recipe.getRollableResultsAsItemStacks(); }
        @Override public void setActive(boolean active) {
            KineticFactoryPortBlockEntity port = findPort(context);
            if (port != null) port.requestLane(context.factoryPos(), context.lane(), active);
        }
        @Override public boolean resourcesAvailable() {
            KineticFactoryPortBlockEntity port = findPort(context);
            return port != null && port.canRunLane(context.factoryPos());
        }
        @Override public List<ItemStack> complete(ItemStackHandler inputs) {
            ItemStack input = inputs.getStackInSlot(0);
            ProcessingRecipe<?, ?> current = KineticProcessBehavior.CreateRecipe.findRecipe(
                    KineticMachineKind.PRESS, context.level(), input);
            if (current == null) return null;
            List<ItemStack> outputs = current.rollResults(context.level().random);
            inputs.extractItem(0, 1, false);
            return outputs;
        }
    }

    private KineticVirtualLaneExecutor() {}
}
