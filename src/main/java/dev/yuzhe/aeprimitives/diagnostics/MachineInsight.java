package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record MachineInsight(
        ResourceLocation identity,
        List<OperationPatternSpec> operations,
        List<MachineInsightRequirement> requirements,
        int maxParallelCapacity,
        String blockedReason,
        long revision) {
    public MachineInsight {
        if (identity == null) throw new IllegalArgumentException("machine identity is required");
        operations = List.copyOf(operations);
        requirements = List.copyOf(requirements);
        if (maxParallelCapacity < 1) throw new IllegalArgumentException("parallel capacity must be positive");
        blockedReason = blockedReason == null ? "" : blockedReason;
    }
}
