package dev.yuzhe.aeprimitives.operation;

import static org.assertj.core.api.Assertions.assertThat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import dev.yuzhe.aeprimitives.operation.OperationInput;
import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class BoundOperationRegistryTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    @Test
    void oneCapabilityPublishesOnlyRecipesReferencedByLiveSequences() {
        var registry = new BoundOperationRegistry();
        registry.replaceSequences(List.of(new SequencePatternSpec(id("one"), List.of(
                new OperationStepSpec(id("iron_sheet"), id("pressing"),
                        List.of(OperationInput.exact(Items.IRON_INGOT, 1)),
                        List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)))))));

        var patterns = registry.patternsFor(OperationPatternSpec.all(id("pressing")), Items.PAPER);

        assertThat(patterns).hasSize(1);
        assertThat(patterns.getFirst().step().recipeId()).isEqualTo(id("iron_sheet"));
    }

    @Test
    void revisionChangesOnlyWhenSequencesChange() {
        var registry = new BoundOperationRegistry();
        int initial = registry.revision();
        registry.replaceSequences(List.of());
        assertThat(registry.revision()).isEqualTo(initial);

        registry.replaceSequences(List.of(new SequencePatternSpec(id("empty"), List.of())));
        assertThat(registry.revision()).isEqualTo(initial + 1);
    }
}
