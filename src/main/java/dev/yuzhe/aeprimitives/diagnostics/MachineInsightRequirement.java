package dev.yuzhe.aeprimitives.diagnostics;

import net.minecraft.resources.ResourceLocation;

public record MachineInsightRequirement(
        MachineInsightRequirementKind kind,
        ResourceLocation id,
        double amount,
        String unit,
        boolean exact) {
    public MachineInsightRequirement {
        if (kind == null) throw new IllegalArgumentException("requirement kind is required");
        if (id == null) throw new IllegalArgumentException("requirement id is required");
        if (amount < 0) throw new IllegalArgumentException("requirement amount must not be negative");
        unit = unit == null ? "" : unit;
    }
}
