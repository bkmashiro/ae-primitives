package dev.yuzhe.aeprimitives.content;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Immutable, server-authored lane state consumed only by factory presentation. */
public record FactoryVisualLane(
        int lane,
        @Nullable ResourceLocation machineId,
        HeterogeneousFactoryBlockEntity.LaneStatus status,
        int progressStep) {
    public static final int PROGRESS_STEPS = 16;

    public FactoryVisualLane {
        if (lane < 0 || lane >= HeterogeneousFactoryBlockEntity.LANE_COUNT) {
            throw new IllegalArgumentException("invalid lane");
        }
        if (status == null || progressStep < 0 || progressStep >= PROGRESS_STEPS) {
            throw new IllegalArgumentException("invalid visual lane state");
        }
    }
}
