package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            steps.add(new ProcessStepView(index, step.recipeId(), step.operation(), status, providers));
            if (index > 0) edges.add(new ProcessEdgeView(index - 1, index));
        }
        return new ProcessSequenceView(sequence.id(), steps, edges);
    }

    private ProcessDiagnosticModel() {
    }
}
