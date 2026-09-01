package dev.yuzhe.aeprimitives.metrics;

import appeng.api.stacks.AEKey;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public interface PhysicalMetricProvider {
    ResourceLocation id();

    default Set<AEKey> watchedStorageKeys() {
        return Set.of();
    }

    PhysicalMetricSample sample(PhysicalMetricContext context);
}
