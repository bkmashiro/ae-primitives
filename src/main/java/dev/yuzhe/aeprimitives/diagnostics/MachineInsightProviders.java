package dev.yuzhe.aeprimitives.diagnostics;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class MachineInsightProviders {
    private static final List<MachineInsightProvider> PROVIDERS = new ArrayList<>();

    public static synchronized void register(MachineInsightProvider provider) {
        if (!PROVIDERS.contains(provider)) PROVIDERS.add(provider);
    }

    public static synchronized MachineInsight inspect(BlockEntity blockEntity) {
        for (var provider : PROVIDERS) {
            var insight = provider.inspectLive(blockEntity);
            if (insight != null) return insight;
        }
        return null;
    }

    public static synchronized MachineInsight inspect(MachineSpaceEnvelope envelope) {
        for (var provider : PROVIDERS) {
            var insight = provider.inspectEnvelope(envelope);
            if (insight != null) return insight;
        }
        return null;
    }

    private MachineInsightProviders() {
    }
}
