package dev.yuzhe.aeprimitives.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompostAccumulatorTest {
    @Test
    void preservesVanillaYieldAcrossCycles() {
        float progress = 0;
        int completed = 0;
        for (int item = 0; item < 24; item++) {
            var result = CompostAccumulator.add(progress, 0.3f);
            progress = result.progress();
            if (result.completed()) completed++;
        }

        assertThat(completed).isEqualTo(1);
        assertThat(progress).isCloseTo(0.2f, within(0.0001f));
    }

    @Test
    void oneHundredPercentInputsCompleteAfterSevenItems() {
        float progress = 0;
        CompostAccumulator.Result result = null;
        for (int item = 0; item < 7; item++) {
            result = CompostAccumulator.add(progress, 1.0f);
            progress = result.progress();
        }

        assertThat(result).isNotNull();
        assertThat(result.completed()).isTrue();
        assertThat(progress).isZero();
    }

    private static org.assertj.core.data.Offset<Float> within(float value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
