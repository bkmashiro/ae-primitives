package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yuzhe.aeprimitives.content.MachineKind;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PrimitiveMachineRenderer implements BlockEntityRenderer<PrimitiveMachineBlockEntity> {
    public PrimitiveMachineRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(PrimitiveMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.kind() != MachineKind.GENERATOR) return;
        pose.pushPose();
        pose.translate(0.5, 0.48, 0.38);
        pose.scale(0.36f, 0.36f, 0.36f);
        float angle = (be.getLevel() == null ? 0 : (be.getLevel().getGameTime() + partialTick) * 1.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Items.COBBLESTONE), ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
