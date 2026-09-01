package dev.yuzhe.aeprimitives.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class MachineInsightTest {
    @Test
    void keepsToolsCatalystsRemaindersAndExternalResourcesDistinct() {
        var requirements = List.of(
                requirement(MachineInsightRequirementKind.TOOL, "test:tool"),
                requirement(MachineInsightRequirementKind.CATALYST, "test:catalyst"),
                requirement(MachineInsightRequirementKind.REMAINDER, "test:remainder"),
                requirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE, "test:power"));
        var insight = new MachineInsight(id("test:machine"),
                List.of(OperationPatternSpec.all(id("test:operation"))), requirements, 4, "", 7);

        assertThat(insight.requirements()).extracting(MachineInsightRequirement::kind)
                .containsExactly(MachineInsightRequirementKind.TOOL,
                        MachineInsightRequirementKind.CATALYST,
                        MachineInsightRequirementKind.REMAINDER,
                        MachineInsightRequirementKind.EXTERNAL_RESOURCE);
        assertThat(insight.operations()).hasSize(1);
        assertThat(insight.maxParallelCapacity()).isEqualTo(4);
        assertThat(insight.revision()).isEqualTo(7);
    }

    private static MachineInsightRequirement requirement(MachineInsightRequirementKind kind, String id) {
        return new MachineInsightRequirement(kind, id(id), 1, "unit", true);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
