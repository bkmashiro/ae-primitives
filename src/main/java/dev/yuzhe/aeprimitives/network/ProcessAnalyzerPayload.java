package dev.yuzhe.aeprimitives.network;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.client.ProcessAnalyzerClient;
import dev.yuzhe.aeprimitives.diagnostics.ProcessDiagnosticSnapshot;
import dev.yuzhe.aeprimitives.diagnostics.ProcessEdgeView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessProviderView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessSequenceView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepStatus;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepView;
import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ProcessAnalyzerPayload(ProcessDiagnosticSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ProcessAnalyzerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AePrimitives.MOD_ID, "process_analyzer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessAnalyzerPayload> STREAM_CODEC = StreamCodec.of(
            ProcessAnalyzerPayload::encode,
            ProcessAnalyzerPayload::decode);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ProcessAnalyzerPayload::handle);
    }

    private static void handle(ProcessAnalyzerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ProcessAnalyzerClient.open(payload.snapshot()));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProcessAnalyzerPayload payload) {
        var snapshot = payload.snapshot();
        buffer.writeVarInt(snapshot.revision());
        buffer.writeVarInt(snapshot.sequences().size());
        for (var sequence : snapshot.sequences()) {
            buffer.writeResourceLocation(sequence.id());
            buffer.writeVarInt(sequence.steps().size());
            for (var step : sequence.steps()) {
                buffer.writeVarInt(step.index());
                buffer.writeResourceLocation(step.recipe());
                buffer.writeResourceLocation(step.operation());
                writeOptionalId(buffer, step.inputIcon());
                writeOptionalId(buffer, step.outputIcon());
                buffer.writeEnum(step.status());
                buffer.writeVarInt(step.providers().size());
                for (var provider : step.providers()) {
                    buffer.writeUtf(provider.dimension());
                    buffer.writeBlockPos(provider.pos());
                    buffer.writeBoolean(provider.busy());
                }
            }
            buffer.writeVarInt(sequence.edges().size());
            for (var edge : sequence.edges()) {
                buffer.writeVarInt(edge.fromStep());
                buffer.writeVarInt(edge.toStep());
            }
        }
    }

    private static ProcessAnalyzerPayload decode(RegistryFriendlyByteBuf buffer) {
        int revision = buffer.readVarInt();
        var sequences = new ArrayList<ProcessSequenceView>();
        int sequenceCount = bounded(buffer.readVarInt(), 256);
        for (int sequenceIndex = 0; sequenceIndex < sequenceCount; sequenceIndex++) {
            var id = buffer.readResourceLocation();
            var steps = new ArrayList<ProcessStepView>();
            int stepCount = bounded(buffer.readVarInt(), 1024);
            for (int stepIndex = 0; stepIndex < stepCount; stepIndex++) {
                int index = buffer.readVarInt();
                var recipe = buffer.readResourceLocation();
                var operation = buffer.readResourceLocation();
                var inputIcon = readOptionalId(buffer);
                var outputIcon = readOptionalId(buffer);
                var status = buffer.readEnum(ProcessStepStatus.class);
                var providers = new ArrayList<ProcessProviderView>();
                int providerCount = bounded(buffer.readVarInt(), 1024);
                for (int providerIndex = 0; providerIndex < providerCount; providerIndex++) {
                    providers.add(new ProcessProviderView(
                            buffer.readUtf(256), buffer.readBlockPos(), buffer.readBoolean()));
                }
                steps.add(new ProcessStepView(
                        index, recipe, operation, inputIcon, outputIcon, status, providers));
            }
            var edges = new ArrayList<ProcessEdgeView>();
            int edgeCount = bounded(buffer.readVarInt(), 1024);
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                edges.add(new ProcessEdgeView(buffer.readVarInt(), buffer.readVarInt()));
            }
            sequences.add(new ProcessSequenceView(id, steps, edges));
        }
        return new ProcessAnalyzerPayload(new ProcessDiagnosticSnapshot(revision, sequences));
    }

    private static void writeOptionalId(RegistryFriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeBoolean(id != null);
        if (id != null) buffer.writeResourceLocation(id);
    }

    private static ResourceLocation readOptionalId(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }

    private static int bounded(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("diagnostic payload count out of bounds: " + value);
        return value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
