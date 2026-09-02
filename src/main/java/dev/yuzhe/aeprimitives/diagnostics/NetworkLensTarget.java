package dev.yuzhe.aeprimitives.diagnostics;

import net.minecraft.core.BlockPos;

public record NetworkLensTarget(BlockPos pos, NetworkLensTargetKind kind, String label) {
    public NetworkLensTarget {
        pos = pos.immutable();
        if (kind == null) throw new IllegalArgumentException("target kind is required");
        label = label == null ? "" : label;
    }
}
