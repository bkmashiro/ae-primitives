package dev.yuzhe.aeprimitives.diagnostics;

import net.minecraft.resources.ResourceLocation;

public record ProcessResourceView(String kind, ResourceLocation id, long amount) {
    public ProcessResourceView {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("resource kind is required");
        if (id == null) throw new IllegalArgumentException("resource id is required");
        if (amount < 0) throw new IllegalArgumentException("resource amount must not be negative");
    }
}
