package dev.yuzhe.aeprimitives.kinetics.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirement;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirementKind;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class KineticMachineInsightProviderTest {
    @Test
    void describesPackagedPressWithoutExecutingItsLane() {
        var configuration = new CompoundTag();
        configuration.putString("kind", KineticMachineKind.PRESS.id());
        var envelope = new MachineSpaceEnvelope(MachineSpaceEnvelope.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("aeprimitives_kinetics", "me_press"),
                new CompoundTag(), configuration);

        var insight = KineticMachineInsightProvider.INSTANCE.inspectEnvelope(envelope);

        assertThat(insight).isNotNull();
        assertThat(insight.operations()).extracting(spec -> spec.operation().toString())
                .containsExactly("create:pressing");
        assertThat(insight.requirements()).allSatisfy(requirement ->
                assertThat(requirement.kind()).isEqualTo(MachineInsightRequirementKind.EXTERNAL_RESOURCE));
        assertThat(insight.requirements()).extracting(requirement -> requirement.id().toString())
                .containsExactly("create:stress_impact", "create:minimum_speed");
        assertThat(insight.requirements()).extracting(MachineInsightRequirement::amount)
                .containsExactly(8.0, 16.0);
        assertThat(insight.requirements()).extracting(MachineInsightRequirement::unit)
                .containsExactly("SU", "RPM");
        assertThat(insight.maxParallelCapacity()).isEqualTo(8);
    }
}
