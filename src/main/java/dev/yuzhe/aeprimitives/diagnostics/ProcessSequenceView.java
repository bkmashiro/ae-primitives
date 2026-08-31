package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record ProcessSequenceView(
        ResourceLocation id,
        List<ProcessStepView> steps,
        List<ProcessEdgeView> edges) {
    public ProcessSequenceView {
        steps = List.copyOf(steps);
        edges = List.copyOf(edges);
    }

    public boolean blocked() {
        return steps.stream().anyMatch(step -> step.status() == ProcessStepStatus.MISSING);
    }
}
