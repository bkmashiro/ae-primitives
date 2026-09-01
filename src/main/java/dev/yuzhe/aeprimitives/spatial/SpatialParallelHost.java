package dev.yuzhe.aeprimitives.spatial;

import dev.yuzhe.aeprimitives.content.MachineTier;

/** Implemented by a machine that owns spatial parallel sidecars. */
public interface SpatialParallelHost {
    MachineTier spatialParallelTier();

    /** Maximum total execution lanes, including the physical machine. */
    int maxSpatialParallelLanes();

    /** Called only when adjacent sidecar topology may have changed. */
    void invalidateSpatialParallelism();
}
