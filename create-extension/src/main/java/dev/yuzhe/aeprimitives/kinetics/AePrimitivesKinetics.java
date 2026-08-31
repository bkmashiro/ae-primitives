package dev.yuzhe.aeprimitives.kinetics;

import dev.yuzhe.aeprimitives.kinetics.compat.create.CreateSequenceDecoder;
import dev.yuzhe.aeprimitives.kinetics.network.PatternImportPayload;
import dev.yuzhe.aeprimitives.sequence.SequencePatternDecoders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AePrimitivesKinetics.MOD_ID)
public final class AePrimitivesKinetics {
    public static final String MOD_ID = "aeprimitives_kinetics";

    public AePrimitivesKinetics(IEventBus modBus) {
        SequencePatternDecoders.register(CreateSequenceDecoder::decode);
        modBus.addListener(PatternImportPayload::register);
    }
}
