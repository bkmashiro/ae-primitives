package dev.yuzhe.aeprimitives.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class CraftingForecastEngineTest {
    private static final ResourceLocation PRESSING = id("create", "pressing");
    private static final ResourceLocation FILLING = id("create", "filling");
    private static final ResourceLocation IRON = id("minecraft", "iron_ingot");
    private static final ResourceLocation NUGGET = id("minecraft", "iron_nugget");

    @Test
    void producesRepeatableConservativeForecastFromImmutableViews() {
        var sequence = new ProcessSequenceView(id("aeprimitives", "line"), List.of(
                step(0, PRESSING, 2,
                        List.of(new ProcessResourceView("item", IRON, 1)),
                        List.of(new ProcessResourceView("item", NUGGET, 1))),
                step(1, FILLING, 1,
                        List.of(new ProcessResourceView("item", NUGGET, 1),
                                new ProcessResourceView("item", IRON, 2)), List.of())),
                List.of(new ProcessEdgeView(0, 1)));

        var first = CraftingForecastEngine.forecast(11, List.of(sequence));
        var second = CraftingForecastEngine.forecast(11, List.of(sequence));

        assertThat(first).isEqualTo(second);
        assertThat(first).singleElement().satisfies(forecast -> {
            assertThat(forecast.sourceRevision()).isEqualTo(11);
            assertThat(forecast.providersComplete()).isTrue();
            assertThat(forecast.knownInputs()).containsExactly(new ProcessResourceView("item", IRON, 3));
            assertThat(forecast.inputPrecision()).isEqualTo(ForecastPrecision.EXACT);
            assertThat(forecast.safeParallelCapacity()).isEqualTo(1);
            assertThat(forecast.bottleneckStep()).isEqualTo(1);
            assertThat(forecast.bottleneckOperation()).isEqualTo(FILLING);
            assertThat(forecast.completionPrecision()).isEqualTo(ForecastPrecision.UNKNOWN);
            assertThat(forecast.minimumCompletionTicks()).isEqualTo(-1);
            assertThat(forecast.maximumCompletionTicks()).isEqualTo(-1);
        });
    }

    @Test
    void includesExactExternalContractFromTheKnownProviderTarget() {
        var sequence = new ProcessSequenceView(id("aeprimitives", "press"),
                List.of(step(0, PRESSING, 1, List.of(new ProcessResourceView("item", IRON, 1)), List.of())),
                List.of());
        var rotation = new MachineInsightRequirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE,
                id("create", "stress_impact"), 8, "SU", true);
        var insight = new MachineInsight(id("aeprimitives_kinetics", "me_press"),
                List.of(OperationPatternSpec.all(PRESSING)), List.of(rotation), 8, "", 1);
        var provider = new OperationProviderView("minecraft:overworld", BlockPos.ZERO, false,
                List.of(OperationPatternSpec.all(PRESSING)), insight);

        var forecast = CraftingForecastEngine.forecast(13, List.of(sequence), List.of(provider)).getFirst();

        assertThat(forecast.knownExternalRequirements()).containsExactly(rotation);
        assertThat(forecast.externalPrecision()).isEqualTo(ForecastPrecision.EXACT);
    }

    @Test
    void missingProviderBlocksCapacityWithoutInventingDuration() {
        var sequence = new ProcessSequenceView(id("aeprimitives", "blocked"),
                List.of(step(0, PRESSING, 0,
                        List.of(new ProcessResourceView("item-alternative", IRON, 1)), List.of())),
                List.of());

        var forecast = CraftingForecastEngine.forecast(12, List.of(sequence)).getFirst();

        assertThat(forecast.providersComplete()).isFalse();
        assertThat(forecast.safeParallelCapacity()).isZero();
        assertThat(forecast.bottleneckOperation()).isEqualTo(PRESSING);
        assertThat(forecast.knownInputs()).isEmpty();
        assertThat(forecast.inputPrecision()).isEqualTo(ForecastPrecision.BOUNDED);
        assertThat(forecast.completionPrecision()).isEqualTo(ForecastPrecision.UNKNOWN);
    }

    private static ProcessStepView step(
            int index, ResourceLocation operation, int providerCount,
            List<ProcessResourceView> inputs, List<ProcessResourceView> outputs) {
        var providers = java.util.stream.IntStream.range(0, providerCount)
                .mapToObj(value -> new ProcessProviderView("minecraft:overworld", new BlockPos(value, 0, 0), false))
                .toList();
        return new ProcessStepView(index, id("aeprimitives", "recipe_" + index), operation,
                null, null, providers.isEmpty() ? ProcessStepStatus.MISSING : ProcessStepStatus.READY,
                providers, inputs, outputs);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
