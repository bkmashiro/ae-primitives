package dev.yuzhe.aeprimitives.operation;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class OperationPatternData {
    private static final String OPERATION = "operation";
    private static final String RECIPE = "recipe";

    public static ItemStack encode(ItemStack stack, ResourceLocation operation) {
        return encode(stack, OperationPatternSpec.all(operation));
    }

    public static ItemStack encode(ItemStack stack, OperationPatternSpec spec) {
        var tag = new CompoundTag();
        tag.putString(OPERATION, spec.operation().toString());
        if (spec.allowedRecipes().size() == 1) {
            tag.putString(RECIPE, spec.allowedRecipes().iterator().next().toString());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static ItemStack encode(Item item, OperationPatternSpec spec) {
        return encode(new ItemStack(item), spec);
    }

    public static OperationPatternDetails decode(AEItemKey definition) {
        var data = definition.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        var operation = ResourceLocation.tryParse(tag.getString(OPERATION));
        if (operation == null) return null;
        ResourceLocation recipe = tag.contains(RECIPE)
                ? ResourceLocation.tryParse(tag.getString(RECIPE))
                : null;
        var spec = recipe == null
                ? OperationPatternSpec.all(operation)
                : new OperationPatternSpec(operation, java.util.Set.of(recipe), java.util.Set.of());
        return new OperationPatternDetails(definition, spec);
    }

    private OperationPatternData() {}
}
