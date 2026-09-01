package dev.yuzhe.aeprimitives.metrics;

import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;

final class PhysicalMetricSelection {
    private PhysicalMetricSelection() {
    }

    static ResourceLocation resolve(Collection<ResourceLocation> available, ResourceLocation selected) {
        if (selected != null) return selected;
        return sorted(available).stream().findFirst().orElse(null);
    }

    static ResourceLocation next(Collection<ResourceLocation> available, ResourceLocation selected) {
        var sorted = sorted(available);
        if (sorted.isEmpty()) return selected;
        int current = selected == null ? -1 : sorted.indexOf(selected);
        return sorted.get(current < 0 || current + 1 == sorted.size() ? 0 : current + 1);
    }

    private static ArrayList<ResourceLocation> sorted(Collection<ResourceLocation> available) {
        var sorted = new ArrayList<>(available);
        sorted.sort(ResourceLocation::compareTo);
        return sorted;
    }
}
