package dev.yuzhe.aeprimitives.commissioning;

import dev.yuzhe.aeprimitives.content.MachineKind;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlock;
import dev.yuzhe.aeprimitives.crafting.LazyPatternRegistry;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirement;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirementKind;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Deterministic Core machines only. Random and world-native paths are rejected before planning. */
final class CorePrimitiveCommissioningProvider implements DeterministicCommissioningProvider {
    static final CorePrimitiveCommissioningProvider INSTANCE = new CorePrimitiveCommissioningProvider();
    private static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath("ae2", "channel");

    @Override
    public boolean supports(MachineSpaceEnvelope envelope) {
        return BuiltInRegistries.BLOCK.get(envelope.blockId()) instanceof PrimitiveMachineBlock;
    }

    @Override
    public List<CommissioningReport> commission(MachineSpaceEnvelope envelope) {
        var block = BuiltInRegistries.BLOCK.get(envelope.blockId());
        if (!(block instanceof PrimitiveMachineBlock machine)) return List.of();
        MachineKind kind = machine.kind();
        if (kind == MachineKind.FORTUNE || kind == MachineKind.COMPOST) {
            return List.of(CommissioningEngine.rejected(envelope.blockId(),
                    CommissioningStatus.UNSUPPORTED_PROBABILISTIC, "probabilistic_machine"));
        }
        if (kind == MachineKind.TRANSFORMATION || kind == MachineKind.FOUNDRY) {
            return List.of(CommissioningEngine.rejected(envelope.blockId(),
                    CommissioningStatus.UNSUPPORTED_WORLD_NATIVE, "world_or_recipe_manager_process"));
        }
        if (!LazyPatternRegistry.supports(kind)) {
            return List.of(CommissioningEngine.rejected(envelope.blockId(),
                    CommissioningStatus.UNSUPPORTED_MACHINE, "no_deterministic_commissioning_model"));
        }
        var requirements = List.of(new MachineInsightRequirement(
                MachineInsightRequirementKind.EXTERNAL_RESOURCE, CHANNEL, 1, "channel", true));
        return LazyPatternRegistry.patternsFor(kind).stream()
                .map(pattern -> CommissioningEngine.run(envelope.blockId(), pattern.spec(), requirements))
                .toList();
    }

    private CorePrimitiveCommissioningProvider() {
    }
}
