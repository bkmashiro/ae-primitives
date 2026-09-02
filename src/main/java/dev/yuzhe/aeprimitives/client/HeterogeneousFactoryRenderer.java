package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yuzhe.aeprimitives.content.FactoryVisualLane;
import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Renders host-mod block models from the server-authored bounded lane snapshot. */
public final class HeterogeneousFactoryRenderer implements BlockEntityRenderer<HeterogeneousFactoryBlockEntity> {
    public HeterogeneousFactoryRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HeterogeneousFactoryBlockEntity factory, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        for (FactoryVisualLane lane : factory.visualLanes()) {
            if (lane.machineId() == null) continue;
            Block machine = BuiltInRegistries.BLOCK.getOptional(lane.machineId()).orElse(null);
            if (machine == null || machine == Blocks.AIR) continue;
            int column = lane.lane() & 1;
            int row = lane.lane() >> 1;
            double x = column == 0 ? 0.29 : 0.71;
            double y = row == 0 ? 0.31 : 0.69;
            renderStatusPedestal(lane, x, y - 0.13, pose, buffers, light, overlay);
            renderMiniature(factory, lane, machine, x, y, partialTick, pose, buffers, light, overlay);
        }
    }

    private static void renderMiniature(HeterogeneousFactoryBlockEntity factory, FactoryVisualLane lane,
                                        Block machine, double x, double y, float partialTick, PoseStack pose,
                                        MultiBufferSource buffers, int light, int overlay) {
        float scale = 0.22f;
        double bob = lane.status() == HeterogeneousFactoryBlockEntity.LaneStatus.RUNNING
                ? Math.sin((factory.getLevel().getGameTime() + partialTick + lane.lane() * 4) * 0.2) * 0.018 : 0;
        float rotation = lane.status() == HeterogeneousFactoryBlockEntity.LaneStatus.RUNNING
                ? (factory.getLevel().getGameTime() + partialTick) * 2.5f : lane.lane() * 90.0f;
        pose.pushPose();
        pose.translate(x, y + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                machine.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, overlay);
        pose.popPose();
    }

    private static void renderStatusPedestal(FactoryVisualLane lane, double x, double y, PoseStack pose,
                                             MultiBufferSource buffers, int light, int overlay) {
        Block indicator = switch (lane.status()) {
            case RUNNING -> Blocks.LIME_CONCRETE;
            case WAITING_INPUT, WAITING_RESOURCE, OFFLINE -> Blocks.YELLOW_CONCRETE;
            case BLOCKED_OUTPUT, INVALID -> Blocks.RED_CONCRETE;
            case EMPTY -> Blocks.GRAY_CONCRETE;
        };
        pose.pushPose();
        pose.translate(x - 0.09, y, 0.41);
        pose.scale(0.18f, 0.055f, 0.18f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                indicator.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, overlay);
        pose.popPose();
    }
}
