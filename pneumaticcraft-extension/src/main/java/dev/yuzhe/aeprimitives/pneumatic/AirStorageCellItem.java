package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.stacks.AEKey;
import appeng.items.storage.BasicStorageCell;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class AirStorageCellItem extends BasicStorageCell {
    private final AirPressureTier tier;

    public AirStorageCellItem(Item.Properties properties, AirPressureTier tier, int bytes, double idleDrain) {
        super(properties.stacksTo(1), idleDrain, bytes, 8, 1, AirKeyType.INSTANCE);
        this.tier = tier;
    }

    public AirPressureTier tier() { return tier; }

    @Override public boolean isBlackListed(ItemStack stack, AEKey key) {
        return !(key instanceof AirKey air) || air.tier() != tier;
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                          List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.aeprimitives_pneumatic.pressure_rating", tier.ratingBar()));
        lines.add(Component.translatable("tooltip.aeprimitives_pneumatic.normal_drive"));
    }
}
