package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Read-only extension point kept separate from virtual-lane execution. */
public interface MachineInsightProvider {
    default MachineInsight inspectLive(BlockEntity blockEntity) {
        return null;
    }

    default MachineInsight inspectEnvelope(MachineSpaceEnvelope envelope) {
        return null;
    }
}
