package dev.yuzhe.aeprimitives.space;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Optional-mod boundary for running a packaged machine without materializing its block entity.
 * Implementations remain in their owning extension and are invoked once per factory lane.
 */
public interface VirtualMachineLaneExecutor {
    boolean supports(MachineSpaceEnvelope envelope);

    LanePlan prepare(LaneContext context);

    default void release(LaneContext context) {}

    record LaneContext(ServerLevel level, BlockPos factoryPos, int lane,
                       MachineSpaceEnvelope envelope, ItemStackHandler inputs) {}

    interface LanePlan {
        int durationTicks();

        int workPerTick();

        double idleAePower();

        /** Outputs used only for capacity checks. This method must not consume randomness. */
        List<ItemStack> previewOutputs();

        /** Registers or clears the extension resource demand for this lane. */
        void setActive(boolean active);

        /** True only while the original machine resource contract can currently run. */
        boolean resourcesAvailable();

        /**
         * Completes exactly one operation. The input snapshot is mutated only when the operation
         * succeeds, and probabilistic outputs must be rolled independently by this invocation.
         */
        List<ItemStack> complete(ItemStackHandler inputs);
    }
}
