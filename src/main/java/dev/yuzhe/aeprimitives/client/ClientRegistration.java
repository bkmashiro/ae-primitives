package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.menu.PrimitiveMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class ClientRegistration {
    public static void register(IEventBus bus) {
        bus.addListener(ClientRegistration::setup);
        bus.addListener(ClientRegistration::screens);
        bus.addListener(ClientRegistration::reloadListeners);
        NeoForge.EVENT_BUS.addListener(NetworkLensClient::render);
    }
    private static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("ponder")) {
            dev.yuzhe.aeprimitives.compat.ponder.AePrimitivesPonderPlugin.register();
        }
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModContent.RESOURCE_GENERATOR.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.GROWTH_CHAMBER.get(), RenderType.translucent());
            BlockEntityRenderers.register(ModContent.MACHINE_ENTITY.get(), PrimitiveMachineRenderer::new);
            BlockEntityRenderers.register(ModContent.PHYSICAL_METRIC_DISPLAY_ENTITY.get(), PhysicalMetricDisplayRenderer::new);
            BlockEntityRenderers.register(ModContent.HETEROGENEOUS_FACTORY_ENTITY.get(), HeterogeneousFactoryRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.RESONANCE_COIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.RESONANCE_CORE.get(), RenderType.translucent());
        });
    }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), PrimitiveMachineScreen::new);
        event.register(ModContent.HETEROGENEOUS_FACTORY_MENU.get(), HeterogeneousFactoryScreen::new);
    }
    private static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                {
                    SimpleMachineAnimations.clearCache();
                    NetworkLensClient.clear();
                });
    }

    private ClientRegistration() {}
}
