package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record ProcessStepView(
        int index,
        ResourceLocation recipe,
        ResourceLocation operation,
        ResourceLocation inputIcon,
        ResourceLocation outputIcon,
        ProcessStepStatus status,
        List<ProcessProviderView> providers,
        List<ProcessResourceView> inputs,
        List<ProcessResourceView> outputs) {
    public ProcessStepView {
        providers = List.copyOf(providers);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public ProcessStepView(
            int index,
            ResourceLocation recipe,
            ResourceLocation operation,
            ResourceLocation inputIcon,
            ResourceLocation outputIcon,
            ProcessStepStatus status,
            List<ProcessProviderView> providers) {
        this(index, recipe, operation, inputIcon, outputIcon, status, providers, List.of(), List.of());
    }
}
