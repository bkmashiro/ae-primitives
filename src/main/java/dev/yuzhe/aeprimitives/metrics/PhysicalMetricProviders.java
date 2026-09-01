package dev.yuzhe.aeprimitives.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class PhysicalMetricProviders {
    private static final Map<ResourceLocation, PhysicalMetricProvider> PROVIDERS = new LinkedHashMap<>();

    private PhysicalMetricProviders() {
    }

    public static synchronized void register(PhysicalMetricProvider provider) {
        var previous = PROVIDERS.putIfAbsent(provider.id(), provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("Duplicate physical metric provider: " + provider.id());
        }
    }

    public static synchronized PhysicalMetricProvider get(ResourceLocation id) {
        return PROVIDERS.get(id);
    }

    public static synchronized List<ResourceLocation> ids() {
        var ids = new ArrayList<>(PROVIDERS.keySet());
        ids.sort(ResourceLocation::compareTo);
        return List.copyOf(ids);
    }
}
