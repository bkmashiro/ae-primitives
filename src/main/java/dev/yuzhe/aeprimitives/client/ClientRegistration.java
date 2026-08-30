package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.menu.PrimitiveMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientRegistration {
    public static void register(IEventBus bus) {
        bus.addListener(ClientRegistration::setup);
        bus.addListener(ClientRegistration::screens);
        bus.addListener(ClientRegistration::renderers);
    }
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModContent.RESOURCE_GENERATOR.get(), RenderType.translucent()));
    }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), PrimitiveMachineScreen::new);
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MACHINE_ENTITY.get(), PrimitiveMachineRenderer::new);
    }
    private ClientRegistration() {}
}
