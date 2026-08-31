package dev.yuzhe.aeprimitives.sequence;

import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Resolves a macro against installed operation capabilities without taking over AE execution. */
public final class SequenceCompiler {
    public static Result compile(SequencePatternSpec sequence, List<OperationPatternSpec> capabilities) {
        var patterns = new LinkedHashMap<ResourceLocation, OperationStepSpec>();
        var missing = new LinkedHashSet<ResourceLocation>();
        for (var step : sequence.steps()) {
            boolean supported = capabilities.stream().anyMatch(capability ->
                    capability.operation().equals(step.operation()) && capability.accepts(step.recipeId()));
            if (supported) patterns.putIfAbsent(step.recipeId(), step);
            else missing.add(step.operation());
        }
        return new Result(List.copyOf(patterns.values()), Set.copyOf(missing));
    }

    public record Result(List<OperationStepSpec> patterns, Set<ResourceLocation> missingOperations) {
        public boolean complete() { return missingOperations.isEmpty(); }
    }

    private SequenceCompiler() {}
}
