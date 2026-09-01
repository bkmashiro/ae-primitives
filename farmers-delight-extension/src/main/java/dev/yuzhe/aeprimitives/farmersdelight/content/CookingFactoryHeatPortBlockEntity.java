package dev.yuzhe.aeprimitives.farmersdelight.content;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;

/** Explicit Farmer's Delight heat bridge for virtual cooking-pot lanes. */
public final class CookingFactoryHeatPortBlockEntity extends BlockEntity implements HeatableBlockEntity {
    private final boolean[] activeLanes = new boolean[HeterogeneousFactoryBlockEntity.LANE_COUNT];
    private BlockPos owner;

    public CookingFactoryHeatPortBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersDelightContent.COOKING_FACTORY_HEAT_PORT_ENTITY.get(), pos, state);
    }

    public boolean requestLane(BlockPos factoryPos, int lane, boolean active) {
        if (lane < 0 || lane >= activeLanes.length || !isAdjacentFactory(factoryPos)) return false;
        if (active && owner != null && !owner.equals(factoryPos)) return false;
        if (active) owner = factoryPos.immutable();
        activeLanes[lane] = active;
        if (!active && activeLaneCount() == 0) owner = null;
        setChanged();
        return true;
    }

    public boolean canRunLane(BlockPos factoryPos) {
        return level != null && factoryPos.equals(owner) && isAdjacentFactory(factoryPos)
                && isHeated(level, worldPosition);
    }

    @Override public void setRemoved() {
        Arrays.fill(activeLanes, false);
        owner = null;
        super.setRemoved();
    }

    private int activeLaneCount() {
        int count = 0;
        for (boolean active : activeLanes) if (active) count++;
        return count;
    }

    private boolean isAdjacentFactory(BlockPos factoryPos) {
        return level != null && worldPosition.distManhattan(factoryPos) == 1
                && level.getBlockEntity(factoryPos) instanceof HeterogeneousFactoryBlockEntity;
    }

    void wakeFactory() {
        if (level == null || level.isClientSide) return;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof HeterogeneousFactoryBlockEntity factory)
                factory.scheduleExternalWork();
        }
    }
}
