package dev.yuzhe.aeprimitives.metrics;

import net.minecraft.resources.ResourceLocation;

public record PhysicalMetricSample(
        ResourceLocation id,
        String labelKey,
        String unit,
        double value,
        double minimum,
        double maximum,
        PhysicalMetricPresentation presentation,
        PhysicalMetricState state) {

    public static PhysicalMetricSample unavailable(ResourceLocation id) {
        return new PhysicalMetricSample(id, "metric.aeprimitives.unavailable", "", 0.0, 0.0, 0.0,
                PhysicalMetricPresentation.DISCRETE, PhysicalMetricState.UNAVAILABLE);
    }
}
