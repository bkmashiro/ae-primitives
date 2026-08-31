package dev.yuzhe.aeprimitives.kinetics.catalyst;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record CatalystVisual(Kind kind, Optional<ResourceLocation> resource, Optional<Integer> tint) {
    public enum Kind { ITEM, BLOCK, FLUID }

    public static CatalystVisual item() {
        return new CatalystVisual(Kind.ITEM, Optional.empty(), Optional.empty());
    }
}
