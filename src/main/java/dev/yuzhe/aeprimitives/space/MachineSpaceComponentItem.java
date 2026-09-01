package dev.yuzhe.aeprimitives.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class MachineSpaceComponentItem extends Item {
    private static final String ENVELOPE_KEY = "aeprimitives_machine_space";

    public MachineSpaceComponentItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(Item item, MachineSpaceEnvelope envelope) {
        ItemStack stack = new ItemStack(item);
        CompoundTag root = new CompoundTag();
        root.put(ENVELOPE_KEY, envelope.encode());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    public static MachineSpaceEnvelope read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag root = data.copyTag();
        return root.contains(ENVELOPE_KEY) ? MachineSpaceEnvelope.decode(root.getCompound(ENVELOPE_KEY)) : null;
    }
}
