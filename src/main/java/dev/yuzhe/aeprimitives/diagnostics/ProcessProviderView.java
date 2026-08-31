package dev.yuzhe.aeprimitives.diagnostics;

import net.minecraft.core.BlockPos;

public record ProcessProviderView(String dimension, BlockPos pos, boolean busy) {
}
