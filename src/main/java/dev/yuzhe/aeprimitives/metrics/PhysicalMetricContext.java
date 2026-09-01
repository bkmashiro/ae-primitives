package dev.yuzhe.aeprimitives.metrics;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import net.minecraft.core.BlockPos;

public record PhysicalMetricContext(IGrid grid, IActionSource actionSource, BlockPos observerPos) {
}
