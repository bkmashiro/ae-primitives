package dev.yuzhe.aeprimitives.pneumatic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class PneumaticAssemblyMathTest {
    @Test
    void mapsPositivePressureToAnExactStorageDomain() {
        assertThat(PneumaticAssemblyMath.tierFor(0.0f)).isEmpty();
        assertThat(PneumaticAssemblyMath.tierFor(-0.5f)).isEmpty();
        assertThat(PneumaticAssemblyMath.tierFor(2.0f)).contains(AirPressureTier.BASIC);
        assertThat(PneumaticAssemblyMath.tierFor(5.0f)).contains(AirPressureTier.BASIC);
        assertThat(PneumaticAssemblyMath.tierFor(5.01f)).contains(AirPressureTier.REINFORCED);
        assertThat(PneumaticAssemblyMath.tierFor(20.0f)).contains(AirPressureTier.REINFORCED);
        assertThat(PneumaticAssemblyMath.tierFor(20.01f)).isEmpty();
    }

    @Test
    void chargesTheNativeInterfaceCostForBothDirections() {
        assertThat(PneumaticAssemblyMath.airCost(1, 1)).isEqualTo(2_000L);
        assertThat(PneumaticAssemblyMath.airCost(4, 3)).isEqualTo(7_000L);
        assertThat(PneumaticAssemblyMath.airCost(0, 0)).isZero();
    }

    @Test
    void refusesOverflowingOrNegativeTransferCounts() {
        assertThat(PneumaticAssemblyMath.airCost(-1, 1)).isEqualTo(-1L);
        assertThat(PneumaticAssemblyMath.airCost(Long.MAX_VALUE, 1)).isEqualTo(-1L);
    }

    @Test
    void requiresTheBankToRemainAtRecipePressureAfterTransfer() {
        assertThat(PneumaticAssemblyMath.canPayAtPressure(8_000, 10_000, 2_000, 2.0f,
                AirPressureTier.BASIC)).isTrue();
        assertThat(PneumaticAssemblyMath.canPayAtPressure(5_000, 10_000, 2_000, 2.0f,
                AirPressureTier.BASIC)).isFalse();
        assertThat(PneumaticAssemblyMath.canPayAtPressure(1_000, 10_000, 2_000, 2.0f,
                AirPressureTier.BASIC)).isFalse();
    }
}
