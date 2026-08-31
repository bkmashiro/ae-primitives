package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.List;
import net.minecraft.core.BlockPos;

public record OperationProviderView(
        String dimension,
        BlockPos pos,
        boolean busy,
        List<OperationPatternSpec> operations) {
    public OperationProviderView {
        operations = List.copyOf(operations);
    }
}
