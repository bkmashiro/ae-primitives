package dev.yuzhe.aeprimitives.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessAnalyzerPreviewCommandTest {
    @Test
    void previewSequencesConnectAdjacentStepOutputsToInputs() {
        for (var sequence : ProcessAnalyzerPreviewCommand.snapshot().sequences()) {
            for (int index = 1; index < sequence.steps().size(); index++) {
                assertThat(sequence.steps().get(index).inputIcon())
                        .as("%s step %d input", sequence.id(), index)
                        .isEqualTo(sequence.steps().get(index - 1).outputIcon());
            }
        }
    }
}
