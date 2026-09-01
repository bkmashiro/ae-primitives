package dev.yuzhe.aeprimitives.pneumatic;

import net.minecraft.world.item.Item;

public final class PneumaticAssemblyHeadItem extends Item {
    private final AirPressureTier tier;

    public PneumaticAssemblyHeadItem(Properties properties, AirPressureTier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public AirPressureTier tier() {
        return tier;
    }
}
