package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Immutable analytical result. Negative duration bounds mean that throughput is unknown. */
public record CraftingForecast(
        ResourceLocation sequence,
        int sourceRevision,
        boolean providersComplete,
        List<ProcessResourceView> knownInputs,
        ForecastPrecision inputPrecision,
        List<MachineInsightRequirement> knownExternalRequirements,
        ForecastPrecision externalPrecision,
        int safeParallelCapacity,
        int bottleneckStep,
        ResourceLocation bottleneckOperation,
        long minimumCompletionTicks,
        long maximumCompletionTicks,
        ForecastPrecision completionPrecision) {
    public CraftingForecast {
        knownInputs = List.copyOf(knownInputs);
        knownExternalRequirements = List.copyOf(knownExternalRequirements);
        if (safeParallelCapacity < 0) throw new IllegalArgumentException("parallel capacity cannot be negative");
        if (completionPrecision == ForecastPrecision.UNKNOWN
                && (minimumCompletionTicks != -1 || maximumCompletionTicks != -1)) {
            throw new IllegalArgumentException("unknown completion must not invent duration bounds");
        }
    }
}
