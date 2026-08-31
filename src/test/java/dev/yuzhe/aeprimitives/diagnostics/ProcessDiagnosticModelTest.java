package dev.yuzhe.aeprimitives.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuzhe.aeprimitives.operation.OperationInput;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ProcessDiagnosticModelTest {
    private static final ResourceLocation PRESSING = id("create", "pressing");
    private static final ResourceLocation FILLING = id("create", "filling");
    private static final ResourceLocation PRESS_IRON = id("create", "pressing/iron_sheet");
    private static final ResourceLocation PRESS_GOLD = id("create", "pressing/gold_sheet");
    private static final ResourceLocation FILL = id("create", "filling/test");

    @Test
    void marksMissingAndReadyOperationsWithoutPollingMachines() {
        var sequence = sequence();
        var providers = List.of(new OperationProviderView(
                "minecraft:overworld", new BlockPos(4, 5, 6), false,
                List.of(OperationPatternSpec.all(PRESSING))));

        var snapshot = ProcessDiagnosticModel.build(7, List.of(sequence), providers);

        assertThat(snapshot.revision()).isEqualTo(7);
        assertThat(snapshot.sequences()).singleElement().satisfies(view -> {
            assertThat(view.steps()).extracting(ProcessStepView::status)
                    .containsExactly(ProcessStepStatus.READY, ProcessStepStatus.MISSING);
            assertThat(view.steps().getFirst().providers()).singleElement().satisfies(provider -> {
                assertThat(provider.dimension()).isEqualTo("minecraft:overworld");
                assertThat(provider.pos()).isEqualTo(new BlockPos(4, 5, 6));
            });
            assertThat(view.edges()).containsExactly(new ProcessEdgeView(0, 1));
        });
    }

    @Test
    void exactOperationPatternsOnlyAdvertiseTheirSelectedRecipe() {
        var provider = new OperationProviderView(
                "minecraft:overworld", BlockPos.ZERO, false,
                List.of(new OperationPatternSpec(PRESSING, Set.of(PRESS_GOLD), Set.of())));

        var snapshot = ProcessDiagnosticModel.build(1, List.of(sequence()), List.of(provider));

        assertThat(snapshot.sequences().getFirst().steps().getFirst().status())
                .isEqualTo(ProcessStepStatus.MISSING);
    }

    @Test
    void reportsBusyWhenEveryMatchingProviderIsBusy() {
        var provider = new OperationProviderView(
                "minecraft:the_nether", new BlockPos(1, 2, 3), true,
                List.of(OperationPatternSpec.all(PRESSING)));

        var snapshot = ProcessDiagnosticModel.build(2, List.of(sequence()), List.of(provider));

        assertThat(snapshot.sequences().getFirst().steps().getFirst().status())
                .isEqualTo(ProcessStepStatus.BUSY);
    }

    private static SequencePatternSpec sequence() {
        var press = new OperationStepSpec(
                PRESS_IRON, PRESSING,
                List.of(OperationInput.exact(Items.IRON_INGOT, 1)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 1)));
        var fill = new OperationStepSpec(
                FILL, FILLING,
                List.of(OperationInput.exact(Items.IRON_INGOT, 1)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
        return new SequencePatternSpec(id("aeprimitives", "test"), List.of(press, fill));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
