package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Resolves only the clicked owner and its six immediate ownership edges. */
public final class NetworkLensResolver {
    public static Result resolve(ServerLevel level, BlockPos owner) {
        var targets = new ArrayList<NetworkLensTarget>();
        targets.add(new NetworkLensTarget(owner, NetworkLensTargetKind.MACHINE, "inspected machine"));
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = owner.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            var state = level.getBlockState(adjacent);
            if (state.getBlock() instanceof SpatialParallelBlock
                    && adjacent.relative(state.getValue(SpatialParallelBlock.FACING)).equals(owner)) {
                targets.add(new NetworkLensTarget(adjacent, NetworkLensTargetKind.SPATIAL_BINDING,
                        "bound spatial parallel"));
            }
        }
        ResourceLocation fallback = null;
        int lane = -1;
        if (level.getBlockEntity(owner) instanceof HeterogeneousFactoryBlockEntity factory) {
            var reports = factory.craftingAutopsies();
            if (!reports.isEmpty()) {
                var report = reports.getFirst();
                fallback = report.target();
                lane = report.lane();
                targets.set(0, new NetworkLensTarget(owner, NetworkLensTargetKind.BLOCKED_CAUSE,
                        report.causeType().name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        return new Result(List.copyOf(targets), fallback, lane);
    }

    public record Result(List<NetworkLensTarget> targets, ResourceLocation textualTarget, int lane) {
        public Result {
            targets = List.copyOf(targets);
        }
    }

    private NetworkLensResolver() {
    }
}
