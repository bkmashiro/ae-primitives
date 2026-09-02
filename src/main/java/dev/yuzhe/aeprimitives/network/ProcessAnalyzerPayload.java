package dev.yuzhe.aeprimitives.network;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.client.ProcessAnalyzerClient;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsight;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirement;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirementKind;
import dev.yuzhe.aeprimitives.diagnostics.ProcessDiagnosticSnapshot;
import dev.yuzhe.aeprimitives.diagnostics.ProcessEdgeView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessProviderView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessResourceView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessSequenceView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepStatus;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepView;
import dev.yuzhe.aeprimitives.diagnostics.CraftingForecast;
import dev.yuzhe.aeprimitives.diagnostics.ForecastPrecision;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        event.registrar("4").playToClient(TYPE, STREAM_CODEC, ProcessAnalyzerPayload::handle);
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
                writeResources(buffer, step.inputs());
                writeResources(buffer, step.outputs());
            }
            buffer.writeVarInt(sequence.edges().size());
            for (var edge : sequence.edges()) {
                buffer.writeVarInt(edge.fromStep());
                buffer.writeVarInt(edge.toStep());
            }
        }
        buffer.writeVarInt(snapshot.machineInsights().size());
        for (var insight : snapshot.machineInsights()) {
            buffer.writeResourceLocation(insight.identity());
            buffer.writeVarInt(insight.operations().size());
            for (var operation : insight.operations()) {
                buffer.writeResourceLocation(operation.operation());
                writeIds(buffer, operation.allowedRecipes());
                writeIds(buffer, operation.deniedRecipes());
            }
            buffer.writeVarInt(insight.requirements().size());
            for (var requirement : insight.requirements()) {
                buffer.writeEnum(requirement.kind());
                buffer.writeResourceLocation(requirement.id());
                buffer.writeDouble(requirement.amount());
                buffer.writeUtf(requirement.unit(), 32);
                buffer.writeBoolean(requirement.exact());
            }
            buffer.writeVarInt(insight.maxParallelCapacity());
            buffer.writeUtf(insight.blockedReason(), 256);
            buffer.writeVarLong(insight.revision());
        }
        buffer.writeVarInt(snapshot.forecasts().size());
        for (var forecast : snapshot.forecasts()) {
            buffer.writeResourceLocation(forecast.sequence());
            buffer.writeVarInt(forecast.sourceRevision());
            buffer.writeBoolean(forecast.providersComplete());
            writeResources(buffer, forecast.knownInputs());
            buffer.writeEnum(forecast.inputPrecision());
            buffer.writeVarInt(forecast.knownExternalRequirements().size());
            for (var requirement : forecast.knownExternalRequirements()) {
                buffer.writeEnum(requirement.kind());
                buffer.writeResourceLocation(requirement.id());
                buffer.writeDouble(requirement.amount());
                buffer.writeUtf(requirement.unit(), 32);
                buffer.writeBoolean(requirement.exact());
            }
            buffer.writeEnum(forecast.externalPrecision());
            buffer.writeVarInt(forecast.safeParallelCapacity());
            buffer.writeVarInt(forecast.bottleneckStep());
            writeOptionalId(buffer, forecast.bottleneckOperation());
            buffer.writeVarLong(forecast.minimumCompletionTicks());
            buffer.writeVarLong(forecast.maximumCompletionTicks());
            buffer.writeEnum(forecast.completionPrecision());
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
                var inputs = readResources(buffer);
                var outputs = readResources(buffer);
                steps.add(new ProcessStepView(
                        index, recipe, operation, inputIcon, outputIcon, status, providers, inputs, outputs));
            }
            var edges = new ArrayList<ProcessEdgeView>();
            int edgeCount = bounded(buffer.readVarInt(), 1024);
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                edges.add(new ProcessEdgeView(buffer.readVarInt(), buffer.readVarInt()));
            }
            sequences.add(new ProcessSequenceView(id, steps, edges));
        }
        var insights = new ArrayList<MachineInsight>();
        int insightCount = bounded(buffer.readVarInt(), 256);
        for (int insightIndex = 0; insightIndex < insightCount; insightIndex++) {
            var identity = buffer.readResourceLocation();
            var operations = new ArrayList<dev.yuzhe.aeprimitives.operation.OperationPatternSpec>();
            int operationCount = bounded(buffer.readVarInt(), 256);
            for (int operationIndex = 0; operationIndex < operationCount; operationIndex++) {
                operations.add(new dev.yuzhe.aeprimitives.operation.OperationPatternSpec(
                        buffer.readResourceLocation(), readIds(buffer), readIds(buffer)));
            }
            var requirements = new ArrayList<MachineInsightRequirement>();
            int requirementCount = bounded(buffer.readVarInt(), 1024);
            for (int requirementIndex = 0; requirementIndex < requirementCount; requirementIndex++) {
                requirements.add(new MachineInsightRequirement(
                        buffer.readEnum(MachineInsightRequirementKind.class), buffer.readResourceLocation(),
                        buffer.readDouble(), buffer.readUtf(32), buffer.readBoolean()));
            }
            insights.add(new MachineInsight(identity, operations, requirements, buffer.readVarInt(),
                    buffer.readUtf(256), buffer.readVarLong()));
        }
        var forecasts = new ArrayList<CraftingForecast>();
        int forecastCount = bounded(buffer.readVarInt(), 256);
        for (int forecastIndex = 0; forecastIndex < forecastCount; forecastIndex++) {
            var sequence = buffer.readResourceLocation();
            int sourceRevision = buffer.readVarInt();
            boolean providersComplete = buffer.readBoolean();
            var knownInputs = readResources(buffer);
            var inputPrecision = buffer.readEnum(ForecastPrecision.class);
            var externalRequirements = new ArrayList<MachineInsightRequirement>();
            int externalCount = bounded(buffer.readVarInt(), 1024);
            for (int externalIndex = 0; externalIndex < externalCount; externalIndex++) {
                externalRequirements.add(new MachineInsightRequirement(
                        buffer.readEnum(MachineInsightRequirementKind.class), buffer.readResourceLocation(),
                        buffer.readDouble(), buffer.readUtf(32), buffer.readBoolean()));
            }
            var externalPrecision = buffer.readEnum(ForecastPrecision.class);
            forecasts.add(new CraftingForecast(
                    sequence, sourceRevision, providersComplete, knownInputs, inputPrecision,
                    externalRequirements, externalPrecision, buffer.readVarInt(),
                    buffer.readVarInt(), readOptionalId(buffer), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readEnum(ForecastPrecision.class)));
        }
        return new ProcessAnalyzerPayload(new ProcessDiagnosticSnapshot(revision, sequences, insights, forecasts));
    }

    private static void writeResources(RegistryFriendlyByteBuf buffer,
                                       java.util.List<ProcessResourceView> resources) {
        buffer.writeVarInt(resources.size());
        for (var resource : resources) {
            buffer.writeUtf(resource.kind(), 32);
            buffer.writeResourceLocation(resource.id());
            buffer.writeVarLong(resource.amount());
        }
    }

    private static java.util.List<ProcessResourceView> readResources(RegistryFriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), 1024);
        var resources = new ArrayList<ProcessResourceView>();
        for (int index = 0; index < count; index++) {
            resources.add(new ProcessResourceView(buffer.readUtf(32), buffer.readResourceLocation(),
                    buffer.readVarLong()));
        }
        return resources;
    }

    private static void writeIds(RegistryFriendlyByteBuf buffer, java.util.Set<ResourceLocation> ids) {
        buffer.writeVarInt(ids.size());
        for (var id : ids) buffer.writeResourceLocation(id);
    }

    private static java.util.Set<ResourceLocation> readIds(RegistryFriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), 1024);
        var ids = new LinkedHashSet<ResourceLocation>();
        for (int index = 0; index < count; index++) ids.add(buffer.readResourceLocation());
        return ids;
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
