package dev.yuzhe.aeprimitives.diagnostics;

import com.mojang.brigadier.CommandDispatcher;
import dev.yuzhe.aeprimitives.commissioning.CommissioningReport;
import dev.yuzhe.aeprimitives.commissioning.CommissioningResource;
import dev.yuzhe.aeprimitives.commissioning.CommissioningStatus;
import dev.yuzhe.aeprimitives.network.ProcessAnalyzerPayload;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Development-only visual fixture used by Minecraft Visual Harness. */
public final class ProcessAnalyzerPreviewCommand {
    private ProcessAnalyzerPreviewCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aeprimitives")
                .then(Commands.literal("preview-analyzer")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(player, new ProcessAnalyzerPayload(snapshot()));
                            return 1;
                        }))
                .then(Commands.literal("preview-forecast")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var complete = snapshot();
                            PacketDistributor.sendToPlayer(player, new ProcessAnalyzerPayload(
                                    new ProcessDiagnosticSnapshot(complete.revision(), complete.sequences())));
                            return 1;
                        }))
                .then(Commands.literal("preview-commissioning")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(player, new ProcessAnalyzerPayload(
                                    new ProcessDiagnosticSnapshot(0, List.of(), List.of(), List.of(),
                                            commissioningPreview())));
                            return 1;
                        }))
                .then(Commands.literal("preview-autopsy")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(player, new ProcessAnalyzerPayload(
                                    new ProcessDiagnosticSnapshot(0, List.of(), List.of(), List.of(), List.of(),
                                            autopsyPreview())));
                            return 1;
                        })));
    }

    static ProcessDiagnosticSnapshot snapshot() {
        var overworld = "minecraft:overworld";
        var sequence = new ProcessSequenceView(
                id("demo/alloy-line"),
                List.of(
                        previewStep(0, "demo/crush_ore", "crushing", "iron_ore", "raw_iron",
                                ProcessStepStatus.READY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(4, 64, 2), false))),
                        previewStep(1, "demo/smelt_raw_iron", "smelting", "raw_iron", "iron_ingot",
                                ProcessStepStatus.BUSY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(7, 64, 2), true))),
                        previewStep(2, "demo/press_ingot", "pressing", "iron_ingot",
                                "heavy_weighted_pressure_plate", ProcessStepStatus.MISSING, List.of())),
                List.of(new ProcessEdgeView(0, 1), new ProcessEdgeView(1, 2)));
        var nested = new ProcessSequenceView(
                id("demo/precision-part"),
                List.of(
                        previewStep(0, "demo/cut_plate", "cutting", "copper_ingot", "cut_copper",
                                ProcessStepStatus.READY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(10, 64, 2), false))),
                        previewStep(1, "demo/assemble_part", "sequence", "cut_copper", "lightning_rod",
                                ProcessStepStatus.READY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(12, 64, 2), false)))),
                List.of(new ProcessEdgeView(0, 1)));
        var press = new MachineInsight(
                ResourceLocation.fromNamespaceAndPath("aeprimitives_kinetics", "me_press"),
                List.of(OperationPatternSpec.all(ResourceLocation.fromNamespaceAndPath("create", "pressing"))),
                List.of(
                        new MachineInsightRequirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE,
                                ResourceLocation.fromNamespaceAndPath("create", "stress_impact"), 8, "SU", true),
                        new MachineInsightRequirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE,
                                ResourceLocation.fromNamespaceAndPath("create", "minimum_speed"), 16, "RPM", true)),
                8, "", 42);
        return new ProcessDiagnosticSnapshot(42, List.of(sequence, nested), List.of(press));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aeprimitives", path);
    }

    private static List<CommissioningReport> commissioningPreview() {
        var machine = id("concrete_curing_chamber");
        return List.of(
                previewCommissioning(machine, "white", "white_concrete_powder", "white_concrete"),
                previewCommissioning(machine, "orange", "orange_concrete_powder", "orange_concrete"),
                previewCommissioning(machine, "magenta", "magenta_concrete_powder", "magenta_concrete"),
                previewCommissioning(machine, "light_blue", "light_blue_concrete_powder", "light_blue_concrete"),
                previewCommissioning(machine, "yellow", "yellow_concrete_powder", "yellow_concrete"));
    }

    private static CommissioningReport previewCommissioning(
            ResourceLocation machine, String variant, String input, String output) {
        return new CommissioningReport(machine, id("dynamic/concrete_curing_chamber/" + variant),
                CommissioningStatus.READY,
                List.of(new CommissioningResource("item", vanilla(input), 1, false)),
                List.of(new CommissioningResource("item", vanilla(output), 1, false)),
                List.of(new MachineInsightRequirement(MachineInsightRequirementKind.EXTERNAL_RESOURCE,
                        ResourceLocation.fromNamespaceAndPath("ae2", "channel"), 1, "channel", true)),
                "deterministic_virtual_plan");
    }

    private static ProcessStepView previewStep(int index, String recipe, String operation,
                                               String input, String output, ProcessStepStatus status,
                                               List<ProcessProviderView> providers) {
        return new ProcessStepView(index, id(recipe), id(operation), vanilla(input), vanilla(output), status,
                providers, List.of(new ProcessResourceView("item", vanilla(input), 1)),
                List.of(new ProcessResourceView("item", vanilla(output), 1)));
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static List<CraftingAutopsy> autopsyPreview() {
        var owner = id("heterogeneous_spatial_factory");
        return List.of(
                new CraftingAutopsy(owner, 0, 18, DiagnosticEventType.BLOCKED_OUTPUT,
                        id("output_buffer"), List.of("lane 1 completed work", "pending output retained",
                        "output buffer has no capacity")),
                new CraftingAutopsy(owner, 1, 21, DiagnosticEventType.WAITING_RESOURCE,
                        id("external_resource"), List.of("lane 2 plan is ready", "external resource unavailable",
                        "restore the required resource port")),
                new CraftingAutopsy(owner, 2, 24, DiagnosticEventType.RECOVERED,
                        id("reload_recovery"), List.of("lane 3 owned state loaded",
                        "progress or pending output restored", "event-driven scheduler resumed")));
    }
}
