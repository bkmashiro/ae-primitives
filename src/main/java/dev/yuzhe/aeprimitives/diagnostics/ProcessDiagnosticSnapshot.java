package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.commissioning.CommissioningReport;
import java.util.List;

public record ProcessDiagnosticSnapshot(
        int revision,
        List<ProcessSequenceView> sequences,
        List<MachineInsight> machineInsights,
        List<CraftingForecast> forecasts,
        List<CommissioningReport> commissioningReports) {
    public ProcessDiagnosticSnapshot {
        sequences = List.copyOf(sequences);
        machineInsights = List.copyOf(machineInsights);
        forecasts = List.copyOf(forecasts);
        commissioningReports = List.copyOf(commissioningReports);
    }

    public ProcessDiagnosticSnapshot(int revision, List<ProcessSequenceView> sequences) {
        this(revision, sequences, List.of(), CraftingForecastEngine.forecast(revision, sequences), List.of());
    }

    public ProcessDiagnosticSnapshot(
            int revision, List<ProcessSequenceView> sequences, List<MachineInsight> machineInsights) {
        this(revision, sequences, machineInsights, CraftingForecastEngine.forecast(revision, sequences), List.of());
    }

    public ProcessDiagnosticSnapshot(
            int revision, List<ProcessSequenceView> sequences, List<MachineInsight> machineInsights,
            List<CraftingForecast> forecasts) {
        this(revision, sequences, machineInsights, forecasts, List.of());
    }

    public static ProcessDiagnosticSnapshot empty() {
        return new ProcessDiagnosticSnapshot(0, List.of(), List.of(), List.of(), List.of());
    }
}
