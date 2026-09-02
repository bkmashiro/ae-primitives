package dev.yuzhe.aeprimitives.commissioning;

import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirement;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Immutable result. It cannot be converted into, inserted as, or dropped as production output. */
public record CommissioningReport(
        ResourceLocation machine,
        ResourceLocation recipe,
        CommissioningStatus status,
        List<CommissioningResource> consumption,
        List<CommissioningResource> outputs,
        List<MachineInsightRequirement> requirements,
        String message) {
    public CommissioningReport {
        if (machine == null || status == null) throw new IllegalArgumentException("commissioning identity is required");
        consumption = List.copyOf(consumption);
        outputs = List.copyOf(outputs);
        requirements = List.copyOf(requirements);
        message = message == null ? "" : message;
        if (status == CommissioningStatus.READY && recipe == null) {
            throw new IllegalArgumentException("ready commissioning needs a recipe");
        }
        if (status != CommissioningStatus.READY && (!consumption.isEmpty() || !outputs.isEmpty())) {
            throw new IllegalArgumentException("rejected commissioning cannot expose a virtual execution result");
        }
    }
}
