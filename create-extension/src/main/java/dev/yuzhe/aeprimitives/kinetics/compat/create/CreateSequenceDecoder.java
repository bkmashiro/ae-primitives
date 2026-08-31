package dev.yuzhe.aeprimitives.kinetics.compat.create;

import appeng.api.stacks.AEItemKey;
import dev.yuzhe.aeprimitives.sequence.SequencePatternDetails;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class CreateSequenceDecoder {
    public static SequencePatternDetails decode(AEItemKey definition, Level level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof
                com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe recipe)) {
            return null;
        }
        var result = CreateSequenceImporter.compile(recipeId, recipe);
        return result.successful() ? new SequencePatternDetails(definition, result.sequence()) : null;
    }

    private CreateSequenceDecoder() {
    }
}
