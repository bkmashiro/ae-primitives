package dev.yuzhe.aeprimitives.client;

import dev.yuzhe.aeprimitives.content.MachineKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

final class SimpleMachineAnimationsTest {
    @Test
    void samplesGeneratedFortuneToolTrack() {
        assertThat(SimpleMachineAnimations.sample(
                MachineKind.FORTUNE, "work", "runtime:tool", "translate_y", 0, 99)).isZero();
        assertThat(SimpleMachineAnimations.sample(
                MachineKind.FORTUNE, "work", "runtime:tool", "translate_y", 0.5f, 99)).isEqualTo(-0.16f);
        assertThat(SimpleMachineAnimations.sample(
                MachineKind.FORTUNE, "work", "runtime:tool", "translate_y", 1, 99)).isZero();
    }

    @Test
    void harnessPhaseOverrideSelectsAnExactDebugPose() {
        System.setProperty("mcvisualharness.animationPhase", "0.5");
        try {
            assertThat(SimpleMachineAnimations.sample(
                    MachineKind.FORTUNE, "work", "runtime:tool", "translate_y", 0, 0))
                    .isCloseTo(-0.16f, within(0.0001f));
        } finally {
            System.clearProperty("mcvisualharness.animationPhase");
        }
    }
}
