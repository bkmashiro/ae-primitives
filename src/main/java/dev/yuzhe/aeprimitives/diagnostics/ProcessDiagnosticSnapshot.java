package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;

public record ProcessDiagnosticSnapshot(
        int revision,
        List<ProcessSequenceView> sequences,
        List<MachineInsight> machineInsights) {
    public ProcessDiagnosticSnapshot {
        sequences = List.copyOf(sequences);
        machineInsights = List.copyOf(machineInsights);
    }

    public ProcessDiagnosticSnapshot(int revision, List<ProcessSequenceView> sequences) {
        this(revision, sequences, List.of());
    }

    public static ProcessDiagnosticSnapshot empty() {
        return new ProcessDiagnosticSnapshot(0, List.of(), List.of());
    }
}
