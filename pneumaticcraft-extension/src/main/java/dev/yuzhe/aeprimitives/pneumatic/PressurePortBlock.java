package dev.yuzhe.aeprimitives.pneumatic;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class PressurePortBlock extends AEBaseEntityBlock<PressurePortBlockEntity> {
    private final AirPressureTier tier;
    private final PressurePortMode mode;

    public PressurePortBlock(AirPressureTier tier, PressurePortMode mode, Properties properties) {
        super(properties);
        this.tier = tier;
        this.mode = mode;
    }

    public AirPressureTier tier() { return tier; }
    public PressurePortMode mode() { return mode; }

    void bind(BlockEntityType<PressurePortBlockEntity> type) {
        setBlockEntity(PressurePortBlockEntity.class, type, null,
                (level, pos, state, blockEntity) -> blockEntity.serverTick());
    }
}
