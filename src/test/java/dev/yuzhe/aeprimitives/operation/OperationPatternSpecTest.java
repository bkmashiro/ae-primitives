package dev.yuzhe.aeprimitives.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class OperationPatternSpecTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    @Test
    void broadCapabilityAcceptsAnyRecipeForItsOperation() {
        var spec = OperationPatternSpec.all(id("pressing"));

        assertThat(spec.accepts(id("iron_sheet"))).isTrue();
        assertThat(spec.accepts(id("copper_sheet"))).isTrue();
    }

    @Test
    void allowAndDenyFiltersStaySmallAndDeterministic() {
        var spec = new OperationPatternSpec(id("pressing"), Set.of(id("iron_sheet"), id("copper_sheet")), Set.of(id("copper_sheet")));

        assertThat(spec.accepts(id("iron_sheet"))).isTrue();
        assertThat(spec.accepts(id("copper_sheet"))).isFalse();
        assertThat(spec.accepts(id("gold_sheet"))).isFalse();
    }
}
