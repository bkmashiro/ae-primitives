package dev.yuzhe.aeprimitives.powah.client;

import dev.yuzhe.aeprimitives.powah.content.PowahContent;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class PowahClientRegistration {
    public static void register(IEventBus bus) {
        bus.addListener(PowahClientRegistration::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BlockEntityRenderers.register(
                PowahContent.ENERGIZING_CHAMBER_ENTITY.get(), EnergizingChamberRenderer::new));
    }

    private PowahClientRegistration() {}
}
