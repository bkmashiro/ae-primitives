package dev.yuzhe.aeprimitives.diagnostics;

import java.util.List;

public record ProcessDiagnosticSnapshot(int revision, List<ProcessSequenceView> sequences) {
    public ProcessDiagnosticSnapshot {
        sequences = List.copyOf(sequences);
    }

    public static ProcessDiagnosticSnapshot empty() {
        return new ProcessDiagnosticSnapshot(0, List.of());
    }
}
