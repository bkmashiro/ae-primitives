package dev.yuzhe.aeprimitives.sequence;

import static org.assertj.core.api.Assertions.assertThat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.operation.OperationInput;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class SequenceCompilerTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    @Test
    void compilesOnlyStepsBackedByInstalledCapabilities() {
        var pressing = OperationPatternSpec.all(id("pressing"));
        var sequence = new SequencePatternSpec(id("precision"), List.of(
                step("deploy_tube", "deploying", Items.GOLD_INGOT, Items.REDSTONE),
                step("press", "pressing", Items.REDSTONE, Items.COMPARATOR)));

        var result = SequenceCompiler.compile(sequence, List.of(pressing));

        assertThat(result.missingOperations()).containsExactly(id("deploying"));
        assertThat(result.patterns()).extracting(p -> p.recipeId()).containsExactly(id("press"));
    }

    @Test
    void deduplicatesRepeatedRecipeBindingsWithoutLosingOrderDependencies() {
        var pressing = OperationPatternSpec.all(id("pressing"));
        var repeated = step("press", "pressing", Items.IRON_INGOT, Items.IRON_BLOCK);
        var sequence = new SequencePatternSpec(id("repeat"), List.of(repeated, repeated));

        var result = SequenceCompiler.compile(sequence, List.of(pressing));

        assertThat(result.complete()).isTrue();
        assertThat(result.patterns()).hasSize(1);
    }

    private static OperationStepSpec step(String recipe, String operation, net.minecraft.world.level.ItemLike input,
                                          net.minecraft.world.level.ItemLike output) {
        return new OperationStepSpec(
                id(recipe), id(operation),
                List.of(OperationInput.exact(input, 1)),
                List.of(new GenericStack(AEItemKey.of(output), 1)));
    }
}
