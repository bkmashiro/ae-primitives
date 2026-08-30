package dev.yuzhe.aeprimitives.client;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.menu.PrimitiveMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PrimitiveMachineScreen extends AbstractContainerScreen<PrimitiveMachineMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AePrimitives.MOD_ID, "textures/gui/primitive_machine.png");
    public PrimitiveMachineScreen(PrimitiveMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth=176; imageHeight=187; inventoryLabelY=94;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
}
