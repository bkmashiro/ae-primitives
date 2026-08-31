package dev.yuzhe.aeprimitives.kinetics.client;

import dev.yuzhe.aeprimitives.kinetics.content.KineticsContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class KineticsClientRegistration {
    public static void register(IEventBus bus) {
        bus.addListener(KineticsClientRegistration::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                KineticsContent.MACHINE_ENTITY.get(), CatalystChamberRenderer::new));
    }

    private KineticsClientRegistration() {}
}
