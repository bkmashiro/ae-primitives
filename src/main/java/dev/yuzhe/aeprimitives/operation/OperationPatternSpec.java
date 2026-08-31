package dev.yuzhe.aeprimitives.operation;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A compact declaration that a provider can execute an operation family. */
public record OperationPatternSpec(
        ResourceLocation operation,
        Set<ResourceLocation> allowedRecipes,
        Set<ResourceLocation> deniedRecipes) {

    public OperationPatternSpec {
        if (operation == null) throw new IllegalArgumentException("operation is required");
        allowedRecipes = Set.copyOf(allowedRecipes);
        deniedRecipes = Set.copyOf(deniedRecipes);
    }

    public static OperationPatternSpec all(ResourceLocation operation) {
        return new OperationPatternSpec(operation, Set.of(), Set.of());
    }

    public boolean accepts(ResourceLocation recipeId) {
        return !deniedRecipes.contains(recipeId)
                && (allowedRecipes.isEmpty() || allowedRecipes.contains(recipeId));
    }
}
