package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class KineticFactoryPortBlockEntity extends KineticBlockEntity {
    static final float MIN_SPEED = 16.0f;
    private final float[] laneStress = new float[HeterogeneousFactoryBlockEntity.LANE_COUNT];
    private BlockPos owner;

    public KineticFactoryPortBlockEntity(BlockPos pos, BlockState state) {
        super(KineticsContent.FACTORY_PORT_ENTITY.get(), pos, state);
    }

    public boolean requestLane(BlockPos factoryPos, int lane, float stress) {
        if (lane < 0 || lane >= laneStress.length || stress < 0 || !isAdjacentFactory(factoryPos)) return false;
        if (owner != null && !owner.equals(factoryPos)) return false;
        if (stress > 0) owner = factoryPos.immutable();
        if (laneStress[lane] != stress) {
            laneStress[lane] = stress;
            if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            setChanged();
            sendData();
        }
        if (stress == 0 && activeLaneCount() == 0) owner = null;
        return true;
    }

    public boolean canRunLane(BlockPos factoryPos) {
        return factoryPos.equals(owner) && isAdjacentFactory(factoryPos)
                && !isOverStressed() && Math.abs(getSpeed()) >= MIN_SPEED;
    }

    public int activeLaneCount() {
        int count = 0;
        for (float stress : laneStress) if (stress > 0) count++;
        return count;
    }

    @Override public float calculateStressApplied() {
        float total = 0;
        for (float stress : laneStress) total += stress;
        return total;
    }

    @Override public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        wakeFactory();
    }

    @Override public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        boolean wasRunnable = !isOverStressed() && Math.abs(getSpeed()) >= MIN_SPEED;
        super.updateFromNetwork(maxStress, currentStress, networkSize);
        boolean runnable = !isOverStressed() && Math.abs(getSpeed()) >= MIN_SPEED;
        if (wasRunnable != runnable) wakeFactory();
    }

    @Override public void remove() {
        Arrays.fill(laneStress, 0);
        owner = null;
        wakeFactory();
        super.remove();
    }

    private boolean isAdjacentFactory(BlockPos factoryPos) {
        return level != null && worldPosition.distManhattan(factoryPos) == 1
                && level.getBlockEntity(factoryPos) instanceof HeterogeneousFactoryBlockEntity;
    }

    private void wakeFactory() {
        if (level == null || level.isClientSide()) return;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof HeterogeneousFactoryBlockEntity factory) {
                factory.scheduleExternalWork();
            }
        }
    }
}
