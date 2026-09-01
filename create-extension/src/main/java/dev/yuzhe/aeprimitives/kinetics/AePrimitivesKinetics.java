package dev.yuzhe.aeprimitives.kinetics;

import dev.yuzhe.aeprimitives.diagnostics.MachineInsightProviders;
import dev.yuzhe.aeprimitives.kinetics.catalyst.CatalystRegistry;
import dev.yuzhe.aeprimitives.kinetics.client.KineticsClientRegistration;
import dev.yuzhe.aeprimitives.kinetics.compat.create.CreateSequenceDecoder;
import dev.yuzhe.aeprimitives.kinetics.content.KineticsContent;
import dev.yuzhe.aeprimitives.kinetics.content.KineticMachineInsightProvider;
import dev.yuzhe.aeprimitives.kinetics.content.KineticVirtualLaneExecutor;
import dev.yuzhe.aeprimitives.kinetics.network.PatternImportPayload;
import dev.yuzhe.aeprimitives.sequence.SequencePatternDecoders;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutors;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AePrimitivesKinetics.MOD_ID)
public final class AePrimitivesKinetics {
    public static final String MOD_ID = "aeprimitives_kinetics";

    public AePrimitivesKinetics(IEventBus modBus) {
        KineticsContent.register(modBus);
        MachineInsightProviders.register(KineticMachineInsightProvider.INSTANCE);
        VirtualMachineLaneExecutors.register(KineticVirtualLaneExecutor.INSTANCE);
        SequencePatternDecoders.register(CreateSequenceDecoder::decode);
        modBus.addListener(PatternImportPayload::register);
        NeoForge.EVENT_BUS.addListener(AePrimitivesKinetics::addReloadListener);
        if (FMLEnvironment.dist == Dist.CLIENT) KineticsClientRegistration.register(modBus);
    }

    private static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new CatalystRegistry());
    }
}
