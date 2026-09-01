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

        if (snapshot.sequences().isEmpty() && snapshot.machineInsights().isEmpty()) {
            detail.setText(Component.translatable("screen.aeprimitives.process_analyzer.empty"));
        } else {
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
                    renderSequence(graph, detail, sequence);
                    graph.fitToChildren(20, 0.55f);
                });
                tabs.addChild(tab);
            }
            if (!snapshot.machineInsights().isEmpty()) {
                renderInsight(graph, detail, snapshot.machineInsights().getFirst());
            } else {
                renderSequence(graph, detail, snapshot.sequences().getFirst());
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

    private static void renderSequence(GraphView graph, Label detail, ProcessSequenceView sequence) {
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
        detail.setText(Component.literal(sequence.id().toString()));
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
