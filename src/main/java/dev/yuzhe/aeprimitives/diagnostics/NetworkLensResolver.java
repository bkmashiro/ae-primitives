package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.space.FactoryResourcePort;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelHost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Resolves only the clicked owner and its six loaded immediate ownership edges. */
public final class NetworkLensResolver {
    public static Result resolve(ServerLevel level, BlockPos owner) {
        var targets = new ArrayList<NetworkLensTarget>();
        var ownerState = level.getBlockState(owner);
        ResourceLocation ownerId = BuiltInRegistries.BLOCK.getKey(ownerState.getBlock());
        targets.add(NetworkLensTarget.world(owner, NetworkLensTargetKind.MACHINE, ownerId, -1,
                "inspected machine"));
        var ownerEntity = level.getBlockEntity(owner);
        SpatialParallelHost spatialHost = ownerEntity instanceof SpatialParallelHost host ? host : null;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = owner.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            var state = level.getBlockState(adjacent);
            if (spatialHost != null && state.getBlock() instanceof SpatialParallelBlock sidecar
                    && sidecar.tier() == spatialHost.spatialParallelTier()
                    && adjacent.relative(state.getValue(SpatialParallelBlock.FACING)).equals(owner)) {
                targets.add(NetworkLensTarget.world(adjacent, NetworkLensTargetKind.SPATIAL_BINDING,
                        BuiltInRegistries.BLOCK.getKey(sidecar), -1, "bound spatial parallel"));
            }
            if (ownerEntity instanceof HeterogeneousFactoryBlockEntity
                    && level.getBlockEntity(adjacent) instanceof FactoryResourcePort port
                    && owner.equals(port.lensOwner())) {
                targets.add(NetworkLensTarget.world(adjacent, NetworkLensTargetKind.RESOURCE_PORT,
                        port.lensResourceId(), -1, "bound factory resource port"));
            }
        }
        if (ownerEntity instanceof HeterogeneousFactoryBlockEntity factory) {
            for (var report : factory.craftingAutopsies()) {
                ResourceLocation identity = report.target() == null
                        ? ResourceLocation.fromNamespaceAndPath("aeprimitives", "unknown_cause") : report.target();
                targets.add(NetworkLensTarget.textual(NetworkLensTargetKind.VIRTUAL_LANE, identity,
                        report.lane(), report.causeType().name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        return new Result(List.copyOf(targets));
    }

    public record Result(List<NetworkLensTarget> targets) {
        public Result {
            targets = List.copyOf(targets);
        }
    }

    private NetworkLensResolver() {
    }
}
