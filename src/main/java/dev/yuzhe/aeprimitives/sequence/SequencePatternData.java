package dev.yuzhe.aeprimitives.sequence;

import appeng.api.stacks.AEItemKey;
import dev.yuzhe.aeprimitives.compat.create.CreateSequenceImporter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class SequencePatternData {
    private static final String RECIPE = "sequenceRecipe";

    public static ItemStack encode(ItemStack stack, ResourceLocation recipeId) {
        var tag = new CompoundTag();
        tag.putString(RECIPE, recipeId.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static ItemStack encode(Item item, ResourceLocation recipeId) {
        return encode(new ItemStack(item), recipeId);
    }

    public static SequencePatternDetails decode(AEItemKey definition, Level level) {
        var data = definition.get(DataComponents.CUSTOM_DATA);
        if (data == null || level == null) return null;
        var id = ResourceLocation.tryParse(data.copyTag().getString(RECIPE));
        if (id == null || !ModList.get().isLoaded("create")) return null;
        var holder = level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null || !(holder.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe recipe)) return null;
        var result = CreateSequenceImporter.compile(id, recipe);
        return result.successful() ? new SequencePatternDetails(definition, result.sequence()) : null;
    }

    private SequencePatternData() {}
}
