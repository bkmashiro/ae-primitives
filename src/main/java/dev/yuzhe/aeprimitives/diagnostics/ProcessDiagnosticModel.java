package dev.yuzhe.aeprimitives.diagnostics;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Pure projection from the event-maintained runtime catalog to a UI-safe diagnostic graph. */
public final class ProcessDiagnosticModel {
    public static ProcessDiagnosticSnapshot build(
            int revision,
            List<SequencePatternSpec> sequences,
            List<OperationProviderView> operationProviders) {
        var orderedSequences = sequences.stream()
                .sorted(Comparator.comparing(sequence -> sequence.id().toString()))
                .map(sequence -> buildSequence(sequence, operationProviders))
                .toList();
        return new ProcessDiagnosticSnapshot(revision, orderedSequences);
    }

    private static ProcessSequenceView buildSequence(
            SequencePatternSpec sequence,
            List<OperationProviderView> operationProviders) {
        var steps = new ArrayList<ProcessStepView>();
        var edges = new ArrayList<ProcessEdgeView>();
        for (int index = 0; index < sequence.steps().size(); index++) {
            var step = sequence.steps().get(index);
            var providers = operationProviders.stream()
                    .filter(provider -> provider.operations().stream().anyMatch(spec ->
                            spec.operation().equals(step.operation()) && spec.accepts(step.recipeId())))
                    .sorted(Comparator.comparing(OperationProviderView::dimension)
                            .thenComparingLong(provider -> provider.pos().asLong()))
                    .map(provider -> new ProcessProviderView(provider.dimension(), provider.pos(), provider.busy()))
                    .toList();
            var status = providers.isEmpty()
                    ? ProcessStepStatus.MISSING
                    : providers.stream().allMatch(ProcessProviderView::busy)
                    ? ProcessStepStatus.BUSY
                    : ProcessStepStatus.READY;
            var inputIcon = step.inputs().stream()
                    .flatMap(input -> input.alternatives().stream())
                    .map(ProcessDiagnosticModel::itemIcon)
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            var outputIcon = step.outputs().stream()
                    .map(ProcessDiagnosticModel::itemIcon)
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            var inputs = step.inputs().stream()
                    .flatMap(input -> input.alternatives().stream()
                            .map(stack -> resource(stack, input.alternatives().size() > 1)))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            var outputs = step.outputs().stream()
                    .map(stack -> resource(stack, false))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            steps.add(new ProcessStepView(
                    index, step.recipeId(), step.operation(), inputIcon, outputIcon, status, providers,
                    inputs, outputs));
            if (index > 0) edges.add(new ProcessEdgeView(index - 1, index));
        }
        return new ProcessSequenceView(sequence.id(), steps, edges);
    }

    private static ResourceLocation itemIcon(GenericStack stack) {
        if (!(stack.what() instanceof AEItemKey itemKey)) return null;
        return BuiltInRegistries.ITEM.getKey(itemKey.getItem());
    }

    private static ProcessResourceView resource(GenericStack stack, boolean alternative) {
        if (stack.what() instanceof AEItemKey itemKey) {
            return new ProcessResourceView(alternative ? "item-alternative" : "item",
                    BuiltInRegistries.ITEM.getKey(itemKey.getItem()), stack.amount());
        }
        if (stack.what() instanceof AEFluidKey fluidKey) {
            return new ProcessResourceView(alternative ? "fluid-alternative" : "fluid",
                    BuiltInRegistries.FLUID.getKey(fluidKey.getFluid()), stack.amount());
        }
        return null;
    }

    private ProcessDiagnosticModel() {
    }
}
