package dev.yuzhe.aeprimitives.sequence;

import static org.assertj.core.api.Assertions.assertThat;

import appeng.api.stacks.AEItemKey;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class SequencePatternDecodersTest {
    @Test
    void delegatesRecipeDecodingToAnOptionalExtension() {
        var definition = AEItemKey.of(Items.PAPER);
        var recipeId = ResourceLocation.fromNamespaceAndPath("example", "sequence");
        var spec = new SequencePatternSpec(recipeId, List.of());
        var expected = new SequencePatternDetails(definition, spec);
        SequencePatternDecoders.register((candidate, level, id) ->
                candidate.equals(definition) && id.equals(recipeId) ? expected : null);

        assertThat(SequencePatternDecoders.decode(definition, null, recipeId)).isSameAs(expected);
    }
}
