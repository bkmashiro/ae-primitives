package dev.yuzhe.aeprimitives.commissioning;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.MachineSpacePackable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Registry boundary: production objects are converted to copied envelopes before pure providers run. */
public final class CommissioningProviders {
    private static final CopyOnWriteArrayList<DeterministicCommissioningProvider> PROVIDERS =
            new CopyOnWriteArrayList<>(List.of(CorePrimitiveCommissioningProvider.INSTANCE));

    public static void register(DeterministicCommissioningProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) PROVIDERS.add(provider);
    }

    public static List<CommissioningReport> commission(BlockEntity blockEntity) {
        if (!(blockEntity instanceof MachineSpacePackable packable) || blockEntity.getLevel() == null) return List.of();
        var state = blockEntity.getBlockState();
        var envelope = MachineSpaceEnvelope.capture(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()), state,
                packable.writeMachineSpaceConfiguration(blockEntity.getLevel().registryAccess()));
        return commission(envelope);
    }

    public static List<CommissioningReport> commission(MachineSpaceEnvelope envelope) {
        if (envelope == null) return List.of();
        var copy = new MachineSpaceEnvelope(envelope.version(), envelope.blockId(),
                envelope.blockState().copy(), envelope.configuration().copy());
        for (var provider : PROVIDERS) {
            if (provider.supports(copy)) return List.copyOf(provider.commission(copy));
        }
        return List.of(CommissioningEngine.rejected(copy.blockId(),
                CommissioningStatus.UNSUPPORTED_MACHINE, "no_commissioning_provider"));
    }

    private CommissioningProviders() {
    }
}
