package dev.yuzhe.aeprimitives.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Pure forecast over an immutable diagnostic snapshot. It has no execution or storage handles. */
public final class CraftingForecastEngine {
    public static List<CraftingForecast> forecast(int revision, List<ProcessSequenceView> sequences) {
        return forecast(revision, sequences, List.of());
    }

    public static List<CraftingForecast> forecast(
            int revision, List<ProcessSequenceView> sequences, List<OperationProviderView> providers) {
        return sequences.stream().map(sequence -> forecast(revision, sequence, providers)).toList();
    }

    static CraftingForecast forecast(int revision, ProcessSequenceView sequence) {
        return forecast(revision, sequence, List.of());
    }

    static CraftingForecast forecast(
            int revision, ProcessSequenceView sequence, List<OperationProviderView> operationProviders) {
        var exactInputs = new LinkedHashMap<ResourceKey, Long>();
        var availableIntermediates = new LinkedHashMap<ResourceKey, Long>();
        var externalRequirements = new LinkedHashMap<RequirementKey, MachineInsightRequirement>();
        boolean hasAlternatives = false;
        boolean hasExternalInsight = false;
        boolean allExternalContractsKnown = true;
        boolean complete = true;
        int safeParallel = Integer.MAX_VALUE;
        int bottleneckStep = -1;
        int bottleneckProviders = Integer.MAX_VALUE;
        net.minecraft.resources.ResourceLocation bottleneckOperation = null;

        for (var step : sequence.steps()) {
            int providers = step.providers().size();
            if (providers == 0) complete = false;
            if (providers < bottleneckProviders) {
                bottleneckProviders = providers;
                bottleneckStep = step.index();
                bottleneckOperation = step.operation();
            }
            safeParallel = Math.min(safeParallel, providers);
            for (var input : step.inputs()) {
                if (input.kind().endsWith("alternative")) {
                    hasAlternatives = true;
                    continue;
                }
                var key = new ResourceKey(input.kind(), input.id());
                long available = availableIntermediates.getOrDefault(key, 0L);
                long consumed = Math.min(available, input.amount());
                if (consumed == available) availableIntermediates.remove(key);
                else availableIntermediates.put(key, available - consumed);
                long external = input.amount() - consumed;
                if (external > 0) exactInputs.merge(key, external, Long::sum);
            }
            for (var output : step.outputs()) {
                if (output.kind().endsWith("alternative")) continue;
                availableIntermediates.merge(new ResourceKey(output.kind(), output.id()), output.amount(), Long::sum);
            }
            var matchingProviders = operationProviders.stream()
                    .filter(provider -> provider.operations().stream().anyMatch(spec ->
                            spec.operation().equals(step.operation()) && spec.accepts(step.recipe())))
                    .toList();
            var contracts = matchingProviders.stream()
                    .map(OperationProviderView::machineInsight)
                    .filter(java.util.Objects::nonNull)
                    .map(insight -> insight.requirements().stream()
                            .filter(requirement -> requirement.kind() == MachineInsightRequirementKind.EXTERNAL_RESOURCE)
                            .toList())
                    .toList();
            if (!matchingProviders.isEmpty() && contracts.size() == matchingProviders.size()) {
                hasExternalInsight = true;
                var first = contracts.getFirst();
                if (contracts.stream().allMatch(first::equals)) {
                    first.forEach(requirement -> externalRequirements.merge(
                            new RequirementKey(requirement.kind(), requirement.id(), requirement.unit(), requirement.exact()),
                            requirement,
                            (left, right) -> left.amount() >= right.amount() ? left : right));
                } else {
                    allExternalContractsKnown = false;
                }
            } else {
                allExternalContractsKnown = false;
            }
        }

        var inputs = new ArrayList<ProcessResourceView>();
        exactInputs.entrySet().stream()
                .sorted(Comparator.comparing((java.util.Map.Entry<ResourceKey, Long> entry) -> entry.getKey().kind())
                        .thenComparing(entry -> entry.getKey().id().toString()))
                .forEach(entry -> inputs.add(new ProcessResourceView(
                        entry.getKey().kind(), entry.getKey().id(), entry.getValue())));
        if (sequence.steps().isEmpty()) {
            safeParallel = 0;
            bottleneckStep = -1;
            bottleneckOperation = null;
        } else if (!complete) {
            safeParallel = 0;
        }

        var externalPrecision = !hasExternalInsight ? ForecastPrecision.UNKNOWN
                : allExternalContractsKnown ? ForecastPrecision.EXACT : ForecastPrecision.BOUNDED;
        return new CraftingForecast(sequence.id(), revision, complete, inputs,
                hasAlternatives ? ForecastPrecision.BOUNDED : ForecastPrecision.EXACT,
                List.copyOf(externalRequirements.values()), externalPrecision,
                safeParallel, bottleneckStep, bottleneckOperation,
                -1, -1, ForecastPrecision.UNKNOWN);
    }

    private record ResourceKey(String kind, net.minecraft.resources.ResourceLocation id) {
    }

    private record RequirementKey(MachineInsightRequirementKind kind,
                                  net.minecraft.resources.ResourceLocation id,
                                  String unit,
                                  boolean exact) {
    }

    private CraftingForecastEngine() {
    }
}
