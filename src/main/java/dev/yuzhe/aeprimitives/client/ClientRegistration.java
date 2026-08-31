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

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class ClientRegistration {
    public static void register(IEventBus bus) {
        bus.addListener(ClientRegistration::setup);
        bus.addListener(ClientRegistration::screens);
        bus.addListener(ClientRegistration::reloadListeners);
    }
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModContent.RESOURCE_GENERATOR.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.GROWTH_CHAMBER.get(), RenderType.translucent());
            BlockEntityRenderers.register(ModContent.MACHINE_ENTITY.get(), PrimitiveMachineRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModContent.RESONANCE_COIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.RESONANCE_CORE.get(), RenderType.translucent());
        });
    }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), PrimitiveMachineScreen::new);
    }
    private static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                SimpleMachineAnimations.clearCache());
    }

    private ClientRegistration() {}
}
