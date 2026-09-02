package dev.yuzhe.aeprimitives.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yuzhe.aeprimitives.diagnostics.NetworkLensTargetKind;
import dev.yuzhe.aeprimitives.network.NetworkLensPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Client-only ephemeral overlay. Nothing is persisted or broadcast. */
public final class NetworkLensClient {
    private static Active active;

    public static void activate(NetworkLensPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        active = new Active(payload, minecraft.level.getGameTime() + payload.durationTicks());
        String detail = payload.textualTarget() == null ? "world target" : payload.textualTarget().toString();
        if (payload.lane() >= 0) detail = "lane " + (payload.lane() + 1) + " · " + detail;
        minecraft.gui.setOverlayMessage(Component.literal("ME Network Lens · " + detail), false);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        if (active == null || minecraft.level == null
                || minecraft.level.getGameTime() >= active.expiresAt
                || !minecraft.level.dimension().location().equals(active.payload.dimension())) {
            active = null;
            return;
        }
        PoseStack poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        var consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        for (var target : active.payload.targets()) {
            float red = target.kind() == NetworkLensTargetKind.BLOCKED_CAUSE ? 1.0f : 0.2f;
            float green = target.kind() == NetworkLensTargetKind.SPATIAL_BINDING ? 0.85f : 0.45f;
            float blue = target.kind() == NetworkLensTargetKind.MACHINE ? 1.0f : 0.55f;
            LevelRenderer.renderLineBox(poses, consumer, new AABB(target.pos()).inflate(0.03),
                    red, green, blue, 0.95f);
        }
        poses.popPose();
    }

    static void clear() {
        active = null;
    }

    private record Active(NetworkLensPayload payload, long expiresAt) {
    }

    private NetworkLensClient() {
    }
}
