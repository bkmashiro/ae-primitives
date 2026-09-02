package dev.yuzhe.aeprimitives.commissioning;

import net.minecraft.resources.ResourceLocation;

/** A non-collectible description used only by deterministic commissioning. */
public record CommissioningResource(String kind, ResourceLocation id, long amount, boolean retained) {
    public CommissioningResource {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("resource kind is required");
        if (id == null) throw new IllegalArgumentException("resource id is required");
        if (amount <= 0) throw new IllegalArgumentException("resource amount must be positive");
    }
}
