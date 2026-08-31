package dev.yuzhe.aeprimitives.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class MachineDefinitionTest {
    @Test
    void machineTiersAndUpgradeLimitsStayInTheDefinition() {
        assertThat(MachineKind.BATCH.tier()).isEqualTo(MachineTier.BASIC);
        assertThat(MachineKind.BATCH.maxSpeedCards()).isZero();
        assertThat(MachineKind.DRIPSTONE.tier()).isEqualTo(MachineTier.ADVANCED);
        assertThat(MachineKind.DRIPSTONE.maxSpeedCards()).isEqualTo(2);
        assertThat(MachineKind.FOUNDRY.tier()).isEqualTo(MachineTier.ULTIMATE);
        assertThat(MachineKind.FOUNDRY.maxSpeedCards()).isEqualTo(2);
    }

    @Test
    void everyMachineHasAValidDefinition() {
        assertThat(MachineKind.values()).allSatisfy(kind -> {
            assertThat(kind.processingTicks()).isPositive();
            assertThat(kind.maxSpeedCards()).isBetween(0, 4);
            assertThat(kind.tier()).isNotNull();
        });
    }
}
