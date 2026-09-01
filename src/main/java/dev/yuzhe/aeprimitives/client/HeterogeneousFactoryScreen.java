package dev.yuzhe.aeprimitives.client;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.menu.HeterogeneousFactoryMenu;
import dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class HeterogeneousFactoryScreen extends AbstractContainerScreen<HeterogeneousFactoryMenu> {
    private static final int BACKGROUND = 0xff151922;
    private static final int PANEL = 0xff252b38;
    private static final int BORDER = 0xff596274;
    private static final int SLOT = 0xff0d1017;
    private static final int ACCENT = 0xff9b63d5;

    public HeterogeneousFactoryScreen(HeterogeneousFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 248;
        imageHeight = 210;
        inventoryLabelX = 43;
        inventoryLabelY = 116;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, BORDER);
        graphics.fill(leftPos + 8, topPos + 14, leftPos + 240, topPos + 111, PANEL);
        outline(graphics, leftPos + 8, topPos + 14, 232, 97, BORDER);
        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
            int x = leftPos + 61 + lane * 32;
            slot(graphics, x, topPos + 17, lane == menu.selectedLane() ? ACCENT : BORDER);
        }
        for (int offset = 0; offset < HeterogeneousFactoryBlockEntity.LANE_BUFFER_SLOTS; offset++) {
            slot(graphics, leftPos + 60 + offset * 18, topPos + 60, BORDER);
            slot(graphics, leftPos + 60 + offset * 18, topPos + 88, BORDER);
        }
        int lane = menu.selectedLane();
        int duration = menu.laneDuration(lane);
        int width = duration <= 0 ? 0 : Math.min(126, menu.laneProgress(lane) * 126 / duration);
        graphics.fill(leftPos + 60, topPos + 105, leftPos + 186, topPos + 109, SLOT);
        graphics.fill(leftPos + 60, topPos + 105, leftPos + 60 + width, topPos + 109, ACCENT);

        graphics.fill(leftPos + 39, topPos + 124, leftPos + 209, topPos + 207, PANEL);
        outline(graphics, leftPos + 39, topPos + 124, 170, 83, BORDER);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            slot(graphics, leftPos + 42 + col * 18, topPos + 127 + row * 18, BORDER);
        for (int col = 0; col < 9; col++) slot(graphics, leftPos + 42 + col * 18, topPos + 185, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xffe7edf7, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffb9c2d2, false);
        int lane = menu.selectedLane();
        var component = menu.getSlot(lane).getItem();
        var envelope = MachineSpaceComponentItem.read(component);
        Component name = envelope == null
                ? Component.translatable("gui.aeprimitives.factory.lane", lane + 1)
                : Component.translatable("block." + envelope.blockId().getNamespace() + "." + envelope.blockId().getPath());
        graphics.drawCenteredString(font, font.plainSubstrByWidth(name.getString(), 220), 124, 42, 0xffe2e7ef);
        Component status = Component.translatable("gui.aeprimitives.factory.status." + menu.laneStatus(lane).id());
        graphics.drawCenteredString(font, status, 124, 78,
                menu.laneStatus(lane) == HeterogeneousFactoryBlockEntity.LaneStatus.BLOCKED_OUTPUT ? 0xffff7d7d : 0xffaeb8c8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
                int x = leftPos + 61 + lane * 32;
                if (mouseX >= x && mouseX < x + 18 && mouseY >= topPos + 17 && mouseY < topPos + 35) {
                    if (minecraft != null && minecraft.gameMode != null)
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, lane);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void slot(GuiGraphics graphics, int x, int y, int border) {
        graphics.fill(x, y, x + 18, y + 18, border);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
