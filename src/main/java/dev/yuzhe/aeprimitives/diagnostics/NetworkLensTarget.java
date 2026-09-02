package dev.yuzhe.aeprimitives.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** A lens result can be a loaded world target or a textual virtual identity. */
public record NetworkLensTarget(
        @Nullable BlockPos pos,
        NetworkLensTargetKind kind,
        ResourceLocation identity,
        int lane,
        String label) {
    public NetworkLensTarget {
        if (kind == null || identity == null) throw new IllegalArgumentException("lens identity is required");
        if (lane < -1) throw new IllegalArgumentException("invalid lane");
        label = label == null ? "" : label.substring(0, Math.min(64, label.length()));
    }

    public static NetworkLensTarget world(
            BlockPos pos, NetworkLensTargetKind kind, ResourceLocation identity, int lane, String label) {
        if (pos == null) throw new IllegalArgumentException("world target needs a position");
        return new NetworkLensTarget(pos.immutable(), kind, identity, lane, label);
    }

    public static NetworkLensTarget textual(
            NetworkLensTargetKind kind, ResourceLocation identity, int lane, String label) {
        return new NetworkLensTarget(null, kind, identity, lane, label);
    }
}
