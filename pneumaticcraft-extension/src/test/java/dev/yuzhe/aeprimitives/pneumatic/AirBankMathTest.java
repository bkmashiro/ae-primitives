package dev.yuzhe.aeprimitives.pneumatic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class AirBankMathTest {
    @Test void derivesPressureFromStoredAirAndOnlineCapacity() {
        assertThat(AirBankMath.pressure(4_000, 8_000, 5.0f)).isEqualTo(2.5f);
        assertThat(AirBankMath.pressure(8_000, 8_000, 5.0f)).isEqualTo(5.0f);
        assertThat(AirBankMath.pressure(0, 8_000, 5.0f)).isZero();
    }

    @Test void transferIsBoundedByPressureDifferenceThroughputAndAvailableAir() {
        assertThat(AirBankMath.equalizingTransfer(4.0f, 2.0f, 1_000, 200, 5_000)).isEqualTo(200);
        assertThat(AirBankMath.equalizingTransfer(2.1f, 2.0f, 1_000, 200, 5_000)).isEqualTo(99);
        assertThat(AirBankMath.equalizingTransfer(4.0f, 2.0f, 1_000, 200, 75)).isEqualTo(75);
        assertThat(AirBankMath.equalizingTransfer(2.0f, 2.0f, 1_000, 200, 5_000)).isZero();
    }
}
