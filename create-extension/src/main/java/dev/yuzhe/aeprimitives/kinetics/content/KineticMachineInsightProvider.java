package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsight;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightProvider;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirement;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirementKind;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Read-only capability description; it never calls the virtual lane executor. */
public final class KineticMachineInsightProvider implements MachineInsightProvider {
    public static final KineticMachineInsightProvider INSTANCE = new KineticMachineInsightProvider();
    private static final ResourceLocation STRESS_IMPACT = ResourceLocation.fromNamespaceAndPath("create", "stress_impact");
    private static final ResourceLocation MINIMUM_SPEED = ResourceLocation.fromNamespaceAndPath("create", "minimum_speed");

    @Override
    public MachineInsight inspectLive(BlockEntity blockEntity) {
        if (!(blockEntity instanceof KineticMachineBlockEntity machine)) return null;
        return describe(BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()), machine.kind(),
                machine.getSpeed() == 0 ? "no_rotation" : "", machine.activeLanes());
    }

    @Override
    public MachineInsight inspectEnvelope(MachineSpaceEnvelope envelope) {
        var kind = resolveKind(envelope.configuration().getString("kind"));
        return kind == null ? null : describe(envelope.blockId(), kind, "", 0);
    }

    private static MachineInsight describe(ResourceLocation identity, KineticMachineKind kind,
                                           String blockedReason, long revision) {
        var operations = operations(kind).stream().map(OperationPatternSpec::all).toList();
        var requirements = new java.util.ArrayList<MachineInsightRequirement>();
        requirements.add(new MachineInsightRequirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE,
                STRESS_IMPACT, kind.stressImpact(), "SU", true));
        requirements.add(new MachineInsightRequirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE,
                MINIMUM_SPEED, KineticMachineBlockEntity.MIN_SPEED, "RPM", true));
        if (kind == KineticMachineKind.FAN) {
            requirements.add(new MachineInsightRequirement(MachineInsightRequirementKind.CATALYST,
                    ResourceLocation.fromNamespaceAndPath("aeprimitives_kinetics", "installed_catalyst"),
                    1, "item", false));
        }
        return new MachineInsight(identity, operations, requirements, kind.maxParallelLanes(),
                blockedReason, revision);
    }

    private static List<ResourceLocation> operations(KineticMachineKind kind) {
        return switch (kind) {
            case PRESS -> List.of(AllRecipeTypes.PRESSING.getId());
            case CRUSHER -> List.of(AllRecipeTypes.CRUSHING.getId());
            case BASIN -> List.of(AllRecipeTypes.MIXING.getId(), AllRecipeTypes.COMPACTING.getId());
            case FILLING -> List.of(AllRecipeTypes.FILLING.getId(), AllRecipeTypes.EMPTYING.getId());
            case DEPLOYER -> List.of(AllRecipeTypes.DEPLOYING.getId());
            case SAW -> List.of(AllRecipeTypes.CUTTING.getId());
            case MILL -> List.of(AllRecipeTypes.MILLING.getId());
            case POLISHER -> List.of(AllRecipeTypes.SANDPAPER_POLISHING.getId());
            case FAN -> List.of();
        };
    }

    private static KineticMachineKind resolveKind(String id) {
        for (var kind : KineticMachineKind.values()) if (kind.id().equals(id)) return kind;
        return null;
    }

    private KineticMachineInsightProvider() {
    }
}
