package dev.yuzhe.aeprimitives.diagnostics;

import com.mojang.brigadier.CommandDispatcher;
import dev.yuzhe.aeprimitives.network.ProcessAnalyzerPayload;
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
                        })));
    }

    static ProcessDiagnosticSnapshot snapshot() {
        var overworld = "minecraft:overworld";
        var sequence = new ProcessSequenceView(
                id("demo/alloy-line"),
                List.of(
                        new ProcessStepView(0, id("demo/crush_ore"), id("crushing"),
                                vanilla("iron_ore"), vanilla("raw_iron"), ProcessStepStatus.READY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(4, 64, 2), false))),
                        new ProcessStepView(1, id("demo/wash_dust"), id("washing"),
                                vanilla("red_sand"), vanilla("gold_nugget"), ProcessStepStatus.BUSY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(7, 64, 2), true))),
                        new ProcessStepView(2, id("demo/press_ingot"), id("pressing"),
                                vanilla("iron_ingot"), vanilla("heavy_weighted_pressure_plate"), ProcessStepStatus.MISSING,
                                List.of())),
                List.of(new ProcessEdgeView(0, 1), new ProcessEdgeView(1, 2)));
        var nested = new ProcessSequenceView(
                id("demo/precision-part"),
                List.of(
                        new ProcessStepView(0, id("demo/cut_plate"), id("cutting"),
                                vanilla("copper_ingot"), vanilla("cut_copper"), ProcessStepStatus.READY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(10, 64, 2), false))),
                        new ProcessStepView(1, id("demo/assemble_part"), id("sequence"),
                                vanilla("cut_copper"), vanilla("lightning_rod"), ProcessStepStatus.READY,
                                List.of(new ProcessProviderView(overworld, new BlockPos(12, 64, 2), false)))),
                List.of(new ProcessEdgeView(0, 1)));
        return new ProcessDiagnosticSnapshot(42, List.of(sequence, nested));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aeprimitives", path);
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
