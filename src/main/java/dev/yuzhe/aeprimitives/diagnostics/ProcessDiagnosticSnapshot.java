package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;

public record ProcessDiagnosticSnapshot(
        int revision,
        List<ProcessSequenceView> sequences,
        List<MachineInsight> machineInsights,
        List<CraftingForecast> forecasts) {
    public ProcessDiagnosticSnapshot {
        sequences = List.copyOf(sequences);
        machineInsights = List.copyOf(machineInsights);
        forecasts = List.copyOf(forecasts);
    }

    public ProcessDiagnosticSnapshot(int revision, List<ProcessSequenceView> sequences) {
        this(revision, sequences, List.of(), CraftingForecastEngine.forecast(revision, sequences));
    }

    public ProcessDiagnosticSnapshot(
            int revision, List<ProcessSequenceView> sequences, List<MachineInsight> machineInsights) {
        this(revision, sequences, machineInsights, CraftingForecastEngine.forecast(revision, sequences));
    }

    public static ProcessDiagnosticSnapshot empty() {
        return new ProcessDiagnosticSnapshot(0, List.of(), List.of(), List.of());
    }
}
