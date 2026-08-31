package dev.yuzhe.aeprimitives.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.yuzhe.aeprimitives.kinetics.catalyst.CatalystVisual;
import dev.yuzhe.aeprimitives.kinetics.content.KineticMachineBlockEntity;
import dev.yuzhe.aeprimitives.kinetics.content.KineticMachineKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class CatalystChamberRenderer extends KineticBlockEntityRenderer<KineticMachineBlockEntity> {
    public CatalystChamberRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(KineticMachineBlockEntity machine, float partialTicks, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(machine, partialTicks, pose, buffers, light, overlay);
        if (machine.kind() != KineticMachineKind.FAN || machine.catalystId().isEmpty()) return;
        var visual = machine.catalystVisual();
        switch (visual.kind()) {
            case FLUID -> renderFluid(machine, visual, pose, buffers, light, overlay);
            case BLOCK -> renderBlock(machine, visual, pose, buffers, light, overlay);
            case ITEM -> renderItem(machine, pose, buffers, light, overlay);
        }
    }

    private static void renderFluid(KineticMachineBlockEntity machine, CatalystVisual visual, PoseStack pose,
                                    MultiBufferSource buffers, int light, int overlay) {
        var fluidId = visual.resource().orElse(null);
        if (fluidId == null) { renderItem(machine, pose, buffers, light, overlay); return; }
        var fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
        if (fluid == null) { renderItem(machine, pose, buffers, light, overlay); return; }
        var state = fluid.defaultFluidState();
        var extension = IClientFluidTypeExtensions.of(state);
        var texture = extension.getStillTexture(state, machine.getLevel(), machine.getBlockPos());
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        int tint = visual.tint().orElseGet(() -> extension.getTintColor(state, machine.getLevel(), machine.getBlockPos()));
        int alpha = tint >>> 24;
        if (alpha == 0) alpha = 255;
        int red = tint >> 16 & 255;
        int green = tint >> 8 & 255;
        int blue = tint & 255;
        var consumer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        var matrix = pose.last().pose();
        float x0 = .22f, x1 = .78f, z0 = .22f, z1 = .78f, y0 = .22f, y1 = .68f;
        quad(consumer, matrix, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1(), red,green,blue,alpha, light, 0,1,0);
        quad(consumer, matrix, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, sprite.getU0(),sprite.getV1(),sprite.getU1(),sprite.getV0(), red,green,blue,alpha, light, 0,0,-1);
        quad(consumer, matrix, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, sprite.getU0(),sprite.getV1(),sprite.getU1(),sprite.getV0(), red,green,blue,alpha, light, 0,0,1);
        quad(consumer, matrix, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0, sprite.getU0(),sprite.getV1(),sprite.getU1(),sprite.getV0(), red,green,blue,alpha, light, -1,0,0);
        quad(consumer, matrix, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, sprite.getU0(),sprite.getV1(),sprite.getU1(),sprite.getV0(), red,green,blue,alpha, light, 1,0,0);
    }

    private static void quad(VertexConsumer out, org.joml.Matrix4f matrix,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz,
                             float u0,float v0,float u1,float v1,
                             int red,int green,int blue,int alpha,int light,float nx,float ny,float nz) {
        vertex(out,matrix,ax,ay,az,u0,v1,red,green,blue,alpha,light,nx,ny,nz);
        vertex(out,matrix,bx,by,bz,u0,v0,red,green,blue,alpha,light,nx,ny,nz);
        vertex(out,matrix,cx,cy,cz,u1,v0,red,green,blue,alpha,light,nx,ny,nz);
        vertex(out,matrix,dx,dy,dz,u1,v1,red,green,blue,alpha,light,nx,ny,nz);
    }

    private static void vertex(VertexConsumer out, org.joml.Matrix4f matrix, float x,float y,float z,
                               float u,float v,int red,int green,int blue,int alpha,int light,float nx,float ny,float nz) {
        out.addVertex(matrix,x,y,z).setColor(red,green,blue,alpha).setUv(u,v)
                .setOverlay(0).setLight(Math.max(light, LightTexture.FULL_BRIGHT / 2)).setNormal(nx,ny,nz);
    }

    private static void renderBlock(KineticMachineBlockEntity machine, CatalystVisual visual, PoseStack pose,
                                    MultiBufferSource buffers, int light, int overlay) {
        var id = visual.resource().orElse(null);
        var block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) { renderItem(machine, pose, buffers, light, overlay); return; }
        pose.pushPose();
        pose.translate(.275, .23, .275);
        pose.scale(.45f, .45f, .45f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers, light, overlay);
        pose.popPose();
    }

    private static void renderItem(KineticMachineBlockEntity machine, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay) {
        var stack = machine.catalystStack();
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(.5, .48, .5);
        float angle = machine.getLevel() == null ? 0 : (machine.getLevel().getGameTime() % 360) + .5f;
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        pose.scale(.62f, .62f, .62f);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, machine.getLevel(), 0);
        pose.popPose();
    }
}
