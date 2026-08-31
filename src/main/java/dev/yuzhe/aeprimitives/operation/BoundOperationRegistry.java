package dev.yuzhe.aeprimitives.operation;

import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/** Shared cache: only recipes referenced by live sequence macros become AE patterns. */
public final class BoundOperationRegistry {
    private List<SequencePatternSpec> sequences = List.of();
    private int revision;

    public synchronized void replaceSequences(List<SequencePatternSpec> next) {
        next = List.copyOf(next);
        if (sequences.equals(next)) return;
        sequences = next;
        revision++;
    }

    public synchronized int revision() { return revision; }

    public synchronized List<BoundOperationPattern> patternsFor(OperationPatternSpec capability, ItemLike definitionItem) {
        var steps = new LinkedHashMap<ResourceLocation, dev.yuzhe.aeprimitives.sequence.OperationStepSpec>();
        for (var sequence : sequences) {
            for (var step : sequence.steps()) {
                if (capability.operation().equals(step.operation()) && capability.accepts(step.recipeId())) {
                    steps.putIfAbsent(step.recipeId(), step);
                }
            }
        }
        var result = new ArrayList<BoundOperationPattern>(steps.size());
        steps.values().forEach(step -> result.add(new BoundOperationPattern(step, definitionItem)));
        return List.copyOf(result);
    }
}
