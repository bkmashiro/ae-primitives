package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record CraftingAutopsy(
        ResourceLocation owner,
        int lane,
        long eventSequence,
        DiagnosticEventType causeType,
        ResourceLocation target,
        List<String> chain) {
    public CraftingAutopsy {
        if (owner == null || lane < 0 || eventSequence < 0 || causeType == null) {
            throw new IllegalArgumentException("invalid autopsy identity");
        }
        chain = List.copyOf(chain);
        if (chain.isEmpty()) throw new IllegalArgumentException("autopsy needs a causal chain");
    }
}
