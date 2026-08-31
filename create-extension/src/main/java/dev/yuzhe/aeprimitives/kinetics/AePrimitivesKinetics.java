package dev.yuzhe.aeprimitives.kinetics;

import dev.yuzhe.aeprimitives.kinetics.client.KineticsClientRegistration;
import dev.yuzhe.aeprimitives.kinetics.compat.create.CreateSequenceDecoder;
import dev.yuzhe.aeprimitives.kinetics.content.KineticsContent;
import dev.yuzhe.aeprimitives.kinetics.network.PatternImportPayload;
import dev.yuzhe.aeprimitives.sequence.SequencePatternDecoders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AePrimitivesKinetics.MOD_ID)
public final class AePrimitivesKinetics {
    public static final String MOD_ID = "aeprimitives_kinetics";

    public AePrimitivesKinetics(IEventBus modBus) {
        KineticsContent.register(modBus);
        SequencePatternDecoders.register(CreateSequenceDecoder::decode);
        modBus.addListener(PatternImportPayload::register);
        if (FMLEnvironment.dist == Dist.CLIENT) KineticsClientRegistration.register(modBus);
    }
}
