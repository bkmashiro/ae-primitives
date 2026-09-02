package dev.yuzhe.aeprimitives.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsight;
import dev.yuzhe.aeprimitives.diagnostics.CraftingForecast;
import dev.yuzhe.aeprimitives.diagnostics.CraftingAutopsy;
import dev.yuzhe.aeprimitives.commissioning.CommissioningReport;
import dev.yuzhe.aeprimitives.commissioning.CommissioningStatus;
import dev.yuzhe.aeprimitives.diagnostics.ForecastPrecision;
import dev.yuzhe.aeprimitives.diagnostics.ProcessDiagnosticSnapshot;
import dev.yuzhe.aeprimitives.diagnostics.ProcessSequenceView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepStatus;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepView;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client-only LDLib2 graph viewer for process diagnostics. */
public final class ProcessAnalyzerClient {
    private static final int READY = 0xff245f50;
    private static final int BUSY = 0xff7a5b20;
    private static final int MISSING = 0xff7a2d36;
    private static final int CYAN = 0xff5bc0be;

    public static void open(ProcessDiagnosticSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new AnalyzerScreen(snapshot));
    }

    private static final class AnalyzerScreen extends ModularUIScreen {
        private final GraphView graph;

        private AnalyzerScreen(ProcessDiagnosticSnapshot snapshot) {
            this(new GraphView(), snapshot);
        }

        private AnalyzerScreen(GraphView graph, ProcessDiagnosticSnapshot snapshot) {
            super(build(graph, snapshot), Component.translatable("screen.aeprimitives.process_analyzer"));
            this.graph = graph;
        }

        @Override
        public void init() {
            super.init();
            graph.fitToChildren(20, 0.55f);
        }
    }

    private static ModularUI build(GraphView graph, ProcessDiagnosticSnapshot snapshot) {
        var root = panel(0xff111820, 0xff3b6572);
        root.layout(layout -> layout.width(400).height(220).paddingAll(8).gapAll(5));

        var title = new Label();
        title.setText(Component.translatable("screen.aeprimitives.process_analyzer.title",
                snapshot.sequences().size(), snapshot.revision()));
        title.layout(layout -> layout.height(16));
        title.textStyle(style -> style.fontSize(13).textColor(0xff9fe7e5).textShadow(true).adaptiveHeight(true));

        var tabs = new UIElement();
        tabs.layout(layout -> layout.height(18).flexDirection(FlexDirection.ROW).gapAll(4));

        var detail = new Label();
        detail.setText(Component.translatable("screen.aeprimitives.process_analyzer.hint"));
        detail.layout(layout -> layout.height(24).paddingAll(4));
        detail.textStyle(style -> style.fontSize(9).textColor(0xffc6d6db).adaptiveHeight(true));
        detail.style(style -> style.background(new ColorRectTexture(0xff18242e)));

        graph.layout(layout -> layout.width(384).height(125));
        graph.style(style -> style.background(new ColorRectTexture(0xff0d1319)));
        graph.graphViewStyle(style -> style
                .allowPan(true).allowZoom(true)
                .minScale(0.35f).maxScale(2.2f)
                .gridSize(24).gridMinPixels(10)
                .gridLineColor(0x183f7180).gridAccentColor(0x383f7180));

        if (snapshot.sequences().isEmpty() && snapshot.machineInsights().isEmpty()
                && snapshot.commissioningReports().isEmpty() && snapshot.autopsies().isEmpty()) {
            detail.setText(Component.translatable("screen.aeprimitives.process_analyzer.empty"));
        } else {
            if (!snapshot.autopsies().isEmpty()) {
                var autopsyTab = tab("autopsy");
                autopsyTab.setOnClick(event -> {
                    event.stopLaterPropagation();
                    renderAutopsies(graph, detail, snapshot.autopsies());
                    graph.fitToChildren(20, 0.72f);
                });
                tabs.addChild(autopsyTab);
            }
            if (!snapshot.commissioningReports().isEmpty()) {
                var commissioningTab = tab("commission");
                commissioningTab.setOnClick(event -> {
                    event.stopLaterPropagation();
                    renderCommissioning(graph, detail, snapshot.commissioningReports());
                    graph.fitToChildren(20, 0.72f);
                });
                tabs.addChild(commissioningTab);
            }
            for (var insight : snapshot.machineInsights()) {
                var tab = tab(insight.identity().getPath());
                tab.setOnClick(event -> {
                    event.stopLaterPropagation();
                    renderInsight(graph, detail, insight);
                    graph.fitToChildren(20, 0.8f);
                });
                tabs.addChild(tab);
            }
            for (var sequence : snapshot.sequences()) {
                String tabName = sequence.id().getPath();
                var tab = tab(tabName);
                tab.textStyle(style -> style.fontSize(9).textColor(sequence.blocked() ? 0xffffa2a7 : 0xff9fe7e5));
                tab.setOnClick(event -> {
                    event.stopLaterPropagation();
                    renderSequence(graph, detail, sequence, forecast(snapshot, sequence));
                    graph.fitToChildren(20, 0.55f);
                });
                tabs.addChild(tab);
            }
            if (!snapshot.autopsies().isEmpty()) {
                renderAutopsies(graph, detail, snapshot.autopsies());
            } else if (!snapshot.machineInsights().isEmpty()) {
                renderInsight(graph, detail, snapshot.machineInsights().getFirst());
            } else if (!snapshot.sequences().isEmpty()) {
                var sequence = snapshot.sequences().getFirst();
                renderSequence(graph, detail, sequence, forecast(snapshot, sequence));
            } else {
                renderCommissioning(graph, detail, snapshot.commissioningReports());
            }
        }

        root.addChildren(title, tabs, graph, detail);
        return ModularUI.of(UI.of(root));
    }

    private static Button tab(String name) {
        var tab = new Button();
        tab.setText(Component.literal(name));
        tab.layout(layout -> layout.width(Math.min(140, 12 + name.length() * 6)).height(16).paddingHorizontal(5));
        tab.textStyle(style -> style.fontSize(9).textColor(0xff9fe7e5));
        tab.buttonStyle(style -> style
                .baseTexture(new ColorRectTexture(0xff23313d))
                .hoverTexture(new ColorRectTexture(0xff304554))
                .pressedTexture(new ColorRectTexture(0xff18242e)));
        tab.style(style -> style.zIndex(2));
        return tab;
    }

    private static void renderInsight(GraphView graph, Label detail, MachineInsight insight) {
        graph.clearAllContentChildren();
        var card = new UIElement();
        card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(18).top(12).width(340).height(92).paddingAll(8).gapAll(4));
        card.style(style -> style.background(new ColorRectTexture(0xff1d3540))
                .overlay(new ColorBorderTexture(-2, 0xff4d8792)));

        var title = new Label();
        title.setText(Component.literal(insight.identity().toString()));
        title.textStyle(style -> style.fontSize(11).textColor(0xff9fe7e5).textShadow(true));
        var operations = new Label();
        operations.setText(Component.literal("Operations: " + (insight.operations().isEmpty() ? "none" :
                insight.operations().stream().map(operation -> operation.operation().getPath())
                        .collect(Collectors.joining(", ")))));
        operations.textStyle(style -> style.fontSize(9).textColor(0xffd7e5e7));
        var resources = new Label();
        resources.setText(Component.literal(insight.requirements().isEmpty() ? "Resources: none declared" :
                insight.requirements().stream().map(requirement -> "%s %s %s%s".formatted(
                                requirement.kind().name().toLowerCase(), requirement.amount(),
                                requirement.unit(), requirement.exact() ? " exact" : " bounded"))
                        .collect(Collectors.joining("  ·  "))));
        resources.textStyle(style -> style.fontSize(9).textColor(0xffc6d6db).adaptiveHeight(true));
        var capacity = new Label();
        capacity.setText(Component.literal("Parallel capacity: " + insight.maxParallelCapacity()
                + (insight.blockedReason().isBlank() ? "" : "  ·  blocked: " + insight.blockedReason())));
        capacity.textStyle(style -> style.fontSize(9).textColor(0xffc6d6db));
        card.addChildren(title, operations, resources, capacity);
        graph.addContentChild(card);
        detail.setText(Component.literal("Read-only machine capability snapshot · revision " + insight.revision()));
    }

    private static void renderCommissioning(
            GraphView graph, Label detail, java.util.List<CommissioningReport> reports) {
        graph.clearAllContentChildren();
        int shown = Math.min(reports.size(), 4);
        for (int index = 0; index < shown; index++) {
            var report = reports.get(index);
            int color = report.status() == CommissioningStatus.READY ? 0xff1d3540 : 0xff4a2830;
            var card = new UIElement();
            int column = index % 2;
            int row = index / 2;
            card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                    .left(8 + column * 188).top(6 + row * 56).width(176).height(48).paddingAll(5).gapAll(2));
            card.style(style -> style.background(new ColorRectTexture(color))
                    .overlay(new ColorBorderTexture(-2,
                            report.status() == CommissioningStatus.READY ? 0xff4d8792 : 0xff9b4d59)));
            var title = new Label();
            title.setText(Component.literal(shortRecipeName(report)));
            title.layout(layout -> layout.width(166).height(10));
            title.textStyle(style -> style.fontSize(9).textColor(0xff9fe7e5).textShadow(true));
            var flow = new Label();
            flow.setText(Component.literal(commissioningFlow(report)));
            flow.layout(layout -> layout.width(166));
            flow.textStyle(style -> style.fontSize(8).textColor(0xffd7e5e7).adaptiveHeight(true));
            card.addChildren(title, flow);
            graph.addContentChild(card);
        }
        long ready = reports.stream().filter(report -> report.status() == CommissioningStatus.READY).count();
        detail.setText(Component.literal("Virtual only · " + ready + "/" + reports.size()
                + " ready · copied config · synthetic inputs · no items created"));
    }

    private static void renderAutopsies(
            GraphView graph, Label detail, java.util.List<CraftingAutopsy> reports) {
        graph.clearAllContentChildren();
        int shown = Math.min(reports.size(), 3);
        for (int index = 0; index < shown; index++) {
            var report = reports.get(index);
            int top = 2 + index * 40;
            var card = new UIElement();
            card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                    .left(8).top(top).width(356).height(38).paddingAll(3).gapAll(1));
            card.style(style -> style.background(new ColorRectTexture(0xff35242d))
                    .overlay(new ColorBorderTexture(-2, 0xffa45b70)));
            var title = new Label();
            title.setText(Component.literal("lane " + (report.lane() + 1) + " · "
                    + report.causeType().name().toLowerCase(java.util.Locale.ROOT)));
            title.layout(layout -> layout.width(348).height(8));
            title.textStyle(style -> style.fontSize(8).textColor(0xffffb8c3).textShadow(true));
            var chain = new Label();
            chain.setText(Component.literal(String.join("\n", report.chain())));
            chain.layout(layout -> layout.width(348).height(21));
            chain.textStyle(style -> style.fontSize(7).textColor(0xffe5d7dc).adaptiveHeight(true));
            card.addChildren(title, chain);
            graph.addContentChild(card);
        }
        detail.setText(Component.literal("Built on request · bounded owner history · no global tracing"));
    }

    private static String shortRecipeName(CommissioningReport report) {
        if (report.recipe() == null) return report.status().name().toLowerCase(java.util.Locale.ROOT);
        String path = report.recipe().getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String commissioningFlow(CommissioningReport report) {
        if (report.status() != CommissioningStatus.READY) return report.message();
        if (report.consumption().size() == 1 && report.outputs().size() == 1) {
            String inputPath = report.consumption().getFirst().id().getPath();
            String outputPath = report.outputs().getFirst().id().getPath();
            if (inputPath.endsWith("_concrete_powder") && outputPath.endsWith("_concrete")) {
                return report.consumption().getFirst().amount() + " powder -> "
                        + report.outputs().getFirst().amount() + " concrete";
            }
        }
        String inputs = report.consumption().stream().limit(2)
                .map(resource -> resource.amount() + " " + shortResourceName(resource.id().getPath())
                        + (resource.retained() ? " retained" : ""))
                .collect(Collectors.joining(" + "));
        String outputs = report.outputs().stream().limit(2)
                .map(resource -> resource.amount() + " " + shortResourceName(resource.id().getPath()))
                .collect(Collectors.joining(" + "));
        return inputs + " -> " + outputs;
    }

    private static String shortResourceName(String path) {
        if (path.endsWith("_concrete_powder")) {
            return path.substring(0, path.length() - "_concrete_powder".length()) + " powder";
        }
        if (path.endsWith("_concrete")) {
            return path.substring(0, path.length() - "_concrete".length()) + " concrete";
        }
        return path.length() <= 18 ? path : path.substring(0, 17) + "…";
    }

    private static void renderSequence(
            GraphView graph, Label detail, ProcessSequenceView sequence, CraftingForecast forecast) {
        graph.clearAllContentChildren();
        for (var edge : sequence.edges()) {
            var destination = sequence.steps().get(edge.toStep());
            var wire = new UIElement();
            wire.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                    .left(130 + edge.fromStep() * 154).top(54).width(24).height(3));
            wire.style(style -> style.background(new ColorRectTexture(
                    destination.status() == ProcessStepStatus.MISSING ? MISSING : CYAN)));
            graph.addContentChild(wire);
        }
        for (var step : sequence.steps()) graph.addContentChild(node(step, detail));
        detail.setText(Component.literal(forecastSummary(sequence, forecast)));
    }

    private static CraftingForecast forecast(ProcessDiagnosticSnapshot snapshot, ProcessSequenceView sequence) {
        return snapshot.forecasts().stream().filter(value -> value.sequence().equals(sequence.id()))
                .findFirst().orElse(null);
    }

    private static String forecastSummary(ProcessSequenceView sequence, CraftingForecast forecast) {
        if (forecast == null) return sequence.id().toString();
        var summary = new StringBuilder(forecast.providersComplete() ? "Ready" : "Blocked")
                .append(" | ");
        if (forecast.knownInputs().isEmpty()) summary.append("inputs unknown");
        else {
            var input = forecast.knownInputs().getFirst();
            summary.append(input.amount()).append(' ').append(input.id().getPath());
            if (forecast.knownInputs().size() > 1) summary.append(" +").append(forecast.knownInputs().size() - 1);
            summary.append(' ').append(forecast.inputPrecision().name().toLowerCase(java.util.Locale.ROOT));
        }
        summary.append(" | parallel ").append(forecast.safeParallelCapacity());
        if (!forecast.knownExternalRequirements().isEmpty()) {
            var requirement = forecast.knownExternalRequirements().getFirst();
            summary.append(" | ext ").append(requirement.amount()).append(' ').append(requirement.unit());
        }
        if (forecast.bottleneckOperation() != null) {
            summary.append(" | bottleneck ").append(forecast.bottleneckOperation().getPath());
        }
        summary.append(" | ETA ").append(forecast.completionPrecision() == ForecastPrecision.UNKNOWN
                ? "?" : forecast.minimumCompletionTicks() + "-" + forecast.maximumCompletionTicks() + "t");
        summary.append(" | r").append(forecast.sourceRevision());
        return summary.toString();
    }

    private static UIElement node(ProcessStepView step, Label detail) {
        int color = switch (step.status()) {
            case READY -> READY;
            case BUSY -> BUSY;
            case MISSING -> MISSING;
        };
        var node = new UIElement();
        node.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(step.index() * 154).top(25).width(130).height(62).paddingAll(6).gapAll(3));
        node.style(style -> style
                .background(new ColorRectTexture(color))
                .overlay(new ColorBorderTexture(-2, color | 0xff000000)));

        var flow = new UIElement();
        flow.layout(layout -> layout.height(20).flexDirection(FlexDirection.ROW).gapAll(3));
        flow.addChildren(icon(step.inputIcon()), arrow(), icon(step.outputIcon()));

        var operation = new Label();
        operation.setText(Component.literal(step.operation().getPath()));
        operation.textStyle(style -> style.fontSize(10).textColor(0xffffffff).textShadow(true));
        var status = new Label();
        status.setText(Component.translatable("screen.aeprimitives.process_analyzer.status." +
                step.status().name().toLowerCase(), step.providers().size()));
        status.textStyle(style -> style.fontSize(9).textColor(0xffffffff));
        node.addChildren(flow, operation, status);
        node.addEventListener(UIEvents.CLICK, event -> detail.setText(stepDetails(step)));
        return node;
    }

    private static UIElement icon(ResourceLocation id) {
        var icon = new UIElement();
        icon.layout(layout -> layout.width(18).height(18));
        if (id == null) {
            icon.style(style -> style.background(new ColorRectTexture(0x55313d46)));
            return icon;
        }
        var item = BuiltInRegistries.ITEM.get(id);
        icon.style(style -> style.background(new ItemStackTexture(new ItemStack(item))));
        return icon;
    }

    private static Label arrow() {
        var arrow = new Label();
        arrow.setText(Component.literal("→"));
        arrow.layout(layout -> layout.width(10).height(18));
        arrow.textStyle(style -> style.fontSize(11).textColor(0xffd7e5e7));
        return arrow;
    }

    private static Component stepDetails(ProcessStepView step) {
        if (step.providers().isEmpty()) {
            return Component.translatable("screen.aeprimitives.process_analyzer.missing", step.operation());
        }
        String providers = step.providers().stream()
                .map(provider -> "%s %d,%d,%d%s".formatted(
                        provider.dimension(), provider.pos().getX(), provider.pos().getY(), provider.pos().getZ(),
                        provider.busy() ? " (busy)" : ""))
                .collect(Collectors.joining("  ·  "));
        String inputs = step.inputs().stream()
                .map(resource -> resource.amount() + " " + resource.id()
                        + (resource.kind().endsWith("alternative") ? " (alternative)" : ""))
                .collect(Collectors.joining(", "));
        String outputs = step.outputs().stream()
                .map(resource -> resource.amount() + " " + resource.id())
                .collect(Collectors.joining(", "));
        return Component.literal((inputs.isBlank() ? "" : "Inputs: " + inputs + "  ·  ")
                + (outputs.isBlank() ? "" : "Outputs: " + outputs + "  ·  ") + providers);
    }

    private static UIElement panel(int background, int border) {
        var panel = new UIElement();
        panel.style(style -> style
                .background(new ColorRectTexture(background))
                .overlay(new ColorBorderTexture(-2, border)));
        return panel;
    }

    private ProcessAnalyzerClient() {
    }
}
