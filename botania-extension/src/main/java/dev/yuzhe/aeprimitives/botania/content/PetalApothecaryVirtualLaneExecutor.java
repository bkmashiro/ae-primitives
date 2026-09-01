package dev.yuzhe.aeprimitives.botania.content;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Runs one packaged Petal Apothecary Interface against one exclusive, explicit real apothecary. */
public final class PetalApothecaryVirtualLaneExecutor implements VirtualMachineLaneExecutor {
    public static final PetalApothecaryVirtualLaneExecutor INSTANCE = new PetalApothecaryVirtualLaneExecutor();

    @Override public boolean supports(MachineSpaceEnvelope envelope) {
        return envelope.blockId().equals(BuiltInRegistries.BLOCK.getKey(BotaniaContent.PETAL_APOTHECARY_INTERFACE.get()));
    }

    @Override public LanePlan prepare(LaneContext context) {
        PetalApothecaryInterfaceBlockEntity port = findPort(context);
        ItemStack expected;
        if (context.state().isEmpty()) {
            var match = port == null ? null : port.findFactoryMatch(context.level(), context.inputs());
            expected = match == null ? ItemStack.EMPTY : match.output().copy();
        } else {
            ItemStack restored = port == null ? null
                    : port.factoryExpectedOutput(context.state(), context.level().registryAccess());
            expected = restored == null ? ItemStack.EMPTY : restored.copy();
        }
        if (expected.isEmpty()) return null;
        return new LanePlan() {
            @Override public int durationTicks() { return 2; }
            @Override public int workPerTick() { return 1; }
            @Override public double idleAePower() { return 4.0; }
            @Override public List<ItemStack> previewOutputs() { return List.of(expected.copy()); }
            @Override public boolean isBegun() { return !context.state().isEmpty(); }
            @Override public boolean begin(ItemStackHandler inputs) {
                return port != null && port.beginFactoryRecipe(
                        context.factoryPos(), context.lane(), inputs, context.state());
            }
            @Override public void setActive(boolean active) {
                if (port != null) port.requestFactoryLane(context.factoryPos(), context.lane(), active);
            }
            @Override public boolean resourcesAvailable() {
                return port != null && port.factoryRecipeReady(
                        context.factoryPos(), context.lane(), context.state());
            }
            @Override public List<ItemStack> complete(ItemStackHandler inputs) {
                if (port == null) return null;
                ItemStack output = port.finishFactoryRecipe(
                        context.factoryPos(), context.lane(), context.state());
                return output == null ? null : List.of(output);
            }
        };
    }

    @Override public void release(LaneContext context) {
        PetalApothecaryInterfaceBlockEntity port = findPort(context);
        if (port != null) port.requestFactoryLane(context.factoryPos(), context.lane(), false);
    }

    private static PetalApothecaryInterfaceBlockEntity findPort(LaneContext context) {
        for (Direction direction : Direction.values()) {
            if (context.level().getBlockEntity(context.factoryPos().relative(direction))
                    instanceof PetalApothecaryInterfaceBlockEntity port) return port;
        }
        return null;
    }

    private PetalApothecaryVirtualLaneExecutor() {}
}
