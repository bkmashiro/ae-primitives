package dev.yuzhe.aeprimitives.powah.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yuzhe.aeprimitives.powah.content.MeEnergizingChamberBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Renders only synchronized presentation state; recipe and FE authority remain server-side. */
public final class EnergizingChamberRenderer implements BlockEntityRenderer<MeEnergizingChamberBlockEntity> {
    private static final ResourceLocation BEAM_TEXTURE = ResourceLocation.withDefaultNamespace("block/white_concrete");

    public EnergizingChamberRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MeEnergizingChamberBlockEntity chamber, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var item = chamber.visualItem();
        if (item.isEmpty()) return;

        pose.pushPose();
        pose.translate(.5, .49, .5);
        float angle = chamber.getLevel() == null ? 0
                : (chamber.getLevel().getGameTime() + partialTick) * 2.5f;
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        pose.scale(.48f, .48f, .48f);
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, overlay, pose, buffers, chamber.getLevel(), chamber.getBlockPos().hashCode());
        pose.popPose();

        renderBeam(chamber.visualProgress(), pose, buffers, overlay);
    }

    private static void renderBeam(float progress, PoseStack pose, MultiBufferSource buffers, int overlay) {
        float intensity = Math.max(.12f, Math.min(1, progress));
        float radius = .012f + .035f * intensity;
        int alpha = 48 + Math.round(150 * intensity);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BEAM_TEXTURE);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose transform = pose.last();
        float x0 = .5f - radius, x1 = .5f + radius;
        float z0 = .5f - radius, z1 = .5f + radius;
        float y0 = .2f, y1 = .79f;
        quad(consumer, transform, sprite, x0,y0,.5f, x1,y0,.5f, x1,y1,.5f, x0,y1,.5f,
                0,0,1, alpha, overlay);
        quad(consumer, transform, sprite, .5f,y0,z0, .5f,y0,z1, .5f,y1,z1, .5f,y1,z0,
                1,0,0, alpha, overlay);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose transform, TextureAtlasSprite sprite,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz,
                             float nx,float ny,float nz, int alpha, int overlay) {
        vertex(out,transform,sprite,ax,ay,az,sprite.getU0(),sprite.getV0(),nx,ny,nz,80,235,255,alpha,overlay);
        vertex(out,transform,sprite,bx,by,bz,sprite.getU1(),sprite.getV0(),nx,ny,nz,80,235,255,alpha,overlay);
        vertex(out,transform,sprite,cx,cy,cz,sprite.getU1(),sprite.getV1(),nx,ny,nz,150,250,255,alpha,overlay);
        vertex(out,transform,sprite,dx,dy,dz,sprite.getU0(),sprite.getV1(),nx,ny,nz,150,250,255,alpha,overlay);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose transform, TextureAtlasSprite sprite,
                               float x,float y,float z, float u,float v, float nx,float ny,float nz,
                               int red,int green,int blue,int alpha,int overlay) {
        out.addVertex(transform,x,y,z)
                .setColor(red,green,blue,alpha)
                .setUv(u,v)
                .setOverlay(overlay)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(transform,nx,ny,nz);
    }
}
