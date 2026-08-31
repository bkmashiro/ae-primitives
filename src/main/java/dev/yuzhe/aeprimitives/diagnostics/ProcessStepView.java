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
        List<ProcessProviderView> providers) {
    public ProcessStepView {
        providers = List.copyOf(providers);
    }
}
