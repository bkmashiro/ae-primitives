package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricDisplayBlockEntity;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricState;
import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PhysicalMetricDisplayRenderer implements BlockEntityRenderer<PhysicalMetricDisplayBlockEntity> {
    private final Font font;

    public PhysicalMetricDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(PhysicalMetricDisplayBlockEntity display, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var sample = display.visibleSample();
        if (sample == null) return;
        var facing = display.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        pose.pushPose();
        pose.translate(0.5, 0.67, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0.0, 0.0, 0.501);
        pose.scale(0.009f, -0.009f, 0.009f);

        int color = switch (sample.state()) {
            case NORMAL -> 0x80F4FF;
            case WARNING -> 0xFFD75A;
            case CRITICAL -> 0xFF665C;
            case UNAVAILABLE -> 0x8A929A;
        };
        var label = Component.translatable(sample.labelKey());
        var value = sample.state() == PhysicalMetricState.UNAVAILABLE
                ? Component.translatable("metric.aeprimitives.unavailable")
                : Component.literal(format(sample.value()) + (sample.unit().isBlank() ? "" : " " + sample.unit()));
        drawCentered(label, -18.0f, 0xB9C5CC, pose, buffers, packedLight);
        drawCentered(value, 4.0f, color, pose, buffers, packedLight);
        pose.popPose();
    }

    private void drawCentered(Component text, float y, int color, PoseStack pose,
                              MultiBufferSource buffers, int packedLight) {
        font.drawInBatch(text, -font.width(text) / 2.0f, y, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
    }

    private static String format(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000.0) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (absolute >= 1_000.0) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        if (absolute >= 100.0) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
