package dev.yuzhe.aeprimitives.sequence;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** A recipe-backed macro. Intermediate stacks form the dependency edges AE already understands. */
public record SequencePatternSpec(ResourceLocation id, List<OperationStepSpec> steps) {
    public SequencePatternSpec {
        if (id == null) throw new IllegalArgumentException("sequence id is required");
        steps = List.copyOf(steps);
    }
}
