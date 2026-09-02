package dev.yuzhe.aeprimitives.network;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.client.NetworkLensClient;
import dev.yuzhe.aeprimitives.diagnostics.NetworkLensResolver;
import dev.yuzhe.aeprimitives.diagnostics.NetworkLensTarget;
import dev.yuzhe.aeprimitives.diagnostics.NetworkLensTargetKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A short-lived single-player overlay request; the server retains no lens state. */
public record NetworkLensPayload(
        ResourceLocation dimension,
        List<NetworkLensTarget> targets,
        ResourceLocation textualTarget,
        int lane,
        int durationTicks) implements CustomPacketPayload {
    public static final Type<NetworkLensPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AePrimitives.MOD_ID, "network_lens"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkLensPayload> STREAM_CODEC = StreamCodec.of(
            NetworkLensPayload::encode, NetworkLensPayload::decode);
    private static final int MAX_TARGETS = 7;
    private static final int MAX_DURATION = 200;

    public NetworkLensPayload {
        targets = List.copyOf(targets.subList(0, Math.min(targets.size(), MAX_TARGETS)));
        lane = Math.max(-1, lane);
        durationTicks = Math.max(1, Math.min(durationTicks, MAX_DURATION));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, NetworkLensPayload::handle);
    }

    public static void send(ServerPlayer player, BlockPos owner) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || player.distanceToSqr(owner.getX() + 0.5, owner.getY() + 0.5, owner.getZ() + 0.5) > 4096.0) {
            return;
        }
        var resolved = NetworkLensResolver.resolve(level, owner);
        PacketDistributor.sendToPlayer(player, new NetworkLensPayload(level.dimension().location(),
                resolved.targets(), resolved.textualTarget(), resolved.lane(), 100));
    }

    private static void handle(NetworkLensPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NetworkLensClient.activate(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NetworkLensPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarInt(payload.targets.size());
        for (var target : payload.targets) {
            buffer.writeBlockPos(target.pos());
            buffer.writeEnum(target.kind());
            buffer.writeUtf(target.label(), 64);
        }
        buffer.writeBoolean(payload.textualTarget != null);
        if (payload.textualTarget != null) buffer.writeResourceLocation(payload.textualTarget);
        buffer.writeVarInt(payload.lane + 1);
        buffer.writeVarInt(payload.durationTicks);
    }

    private static NetworkLensPayload decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = Math.min(buffer.readVarInt(), MAX_TARGETS);
        var targets = new ArrayList<NetworkLensTarget>(count);
        for (int index = 0; index < count; index++) {
            targets.add(new NetworkLensTarget(buffer.readBlockPos(), buffer.readEnum(NetworkLensTargetKind.class),
                    buffer.readUtf(64)));
        }
        ResourceLocation textual = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        int lane = buffer.readVarInt() - 1;
        int duration = buffer.readVarInt();
        return new NetworkLensPayload(dimension, targets, textual, lane, duration);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
