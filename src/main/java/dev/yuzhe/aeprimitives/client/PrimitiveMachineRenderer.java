package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yuzhe.aeprimitives.content.MachineKind;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

public final class PrimitiveMachineRenderer implements BlockEntityRenderer<PrimitiveMachineBlockEntity> {
    public PrimitiveMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PrimitiveMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.kind() != MachineKind.GENERATOR || be.getLevel() == null) return;

        renderFluidTank(be, pose, buffers, light, overlay, Fluids.WATER.defaultFluidState(), 3 / 16f, 6 / 16f);
        renderFluidTank(be, pose, buffers, light, overlay, Fluids.LAVA.defaultFluidState(), 10 / 16f, 13 / 16f);

        pose.pushPose();
        pose.translate(0.5, 0.48, 0.14);
        pose.scale(0.24f, 0.24f, 0.24f);
        float angle = (be.getLevel().getGameTime() + partialTick) * 1.5f;
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Items.COBBLESTONE), ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }

    private static void renderFluidTank(PrimitiveMachineBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                        int packedLight, int overlay, FluidState fluid, float minX, float maxX) {
        TextureAtlasSprite sprite = FluidSpriteCache.getFluidSprites(be.getLevel(), be.getBlockPos(), fluid)[0];
        int tint = IClientFluidTypeExtensions.of(fluid).getTintColor(fluid, be.getLevel(), be.getBlockPos());
        int red = tint >> 16 & 0xff;
        int green = tint >> 8 & 0xff;
        int blue = tint & 0xff;
        int alpha = fluid.is(Fluids.WATER) ? 210 : 255;
        int fluidLight = fluid.getFluidType().getLightLevel(fluid, be.getLevel(), be.getBlockPos());
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), fluidLight), LightTexture.sky(packedLight));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));

        float minY = 3 / 16f;
        float maxY = 11 / 16f;
        float minZ = 3 / 16f;
        float maxZ = 12 / 16f;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        PoseStack.Pose transform = pose.last();

        quad(vertices, transform, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ,
                0, 0, -1, u0, u1, v0, v1, red, green, blue, alpha, light, overlay);
        quad(vertices, transform, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ,
                0, 0, 1, u0, u1, v0, v1, red, green, blue, alpha, light, overlay);
        quad(vertices, transform, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ,
                -1, 0, 0, u0, u1, v0, v1, red, green, blue, alpha, light, overlay);
        quad(vertices, transform, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ,
                1, 0, 0, u0, u1, v0, v1, red, green, blue, alpha, light, overlay);
        quad(vertices, transform, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                0, 1, 0, u0, u1, v0, v1, red, green, blue, alpha, light, overlay);
        quad(vertices, transform, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                0, -1, 0, u0, u1, v0, v1, red, green, blue, alpha, light, overlay);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose transform,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float normalX, float normalY, float normalZ,
                             float u0, float u1, float v0, float v1,
                             int red, int green, int blue, int alpha, int light, int overlay) {
        vertex(vertices, transform, x0, y0, z0, u0, v0, normalX, normalY, normalZ, red, green, blue, alpha, light, overlay);
        vertex(vertices, transform, x1, y1, z1, u1, v0, normalX, normalY, normalZ, red, green, blue, alpha, light, overlay);
        vertex(vertices, transform, x2, y2, z2, u1, v1, normalX, normalY, normalZ, red, green, blue, alpha, light, overlay);
        vertex(vertices, transform, x3, y3, z3, u0, v1, normalX, normalY, normalZ, red, green, blue, alpha, light, overlay);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose transform, float x, float y, float z,
                               float u, float v, float normalX, float normalY, float normalZ,
                               int red, int green, int blue, int alpha, int light, int overlay) {
        vertices.addVertex(transform, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(transform, normalX, normalY, normalZ);
    }
}
