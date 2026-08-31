package dev.yuzhe.aeprimitives.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.yuzhe.aeprimitives.diagnostics.ProcessDiagnosticSnapshot;
import dev.yuzhe.aeprimitives.diagnostics.ProcessSequenceView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepStatus;
import dev.yuzhe.aeprimitives.diagnostics.ProcessStepView;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

        if (snapshot.sequences().isEmpty()) {
            detail.setText(Component.translatable("screen.aeprimitives.process_analyzer.empty"));
        } else {
            for (var sequence : snapshot.sequences()) {
                var tab = new Button();
                String tabName = sequence.id().getPath();
                tab.setText(Component.literal(tabName));
                tab.layout(layout -> layout.width(Math.min(140, 12 + tabName.length() * 6)).height(16).paddingHorizontal(5));
                tab.textStyle(style -> style.fontSize(9).textColor(sequence.blocked() ? 0xffffa2a7 : 0xff9fe7e5));
                tab.buttonStyle(style -> style
                        .baseTexture(new ColorRectTexture(0xff23313d))
                        .hoverTexture(new ColorRectTexture(0xff304554))
                        .pressedTexture(new ColorRectTexture(0xff18242e)));
                tab.style(style -> style.zIndex(2));
                tab.setOnClick(event -> {
                    event.stopLaterPropagation();
                    renderSequence(graph, detail, sequence);
                    graph.fitToChildren(20, 0.55f);
                });
                tabs.addChild(tab);
            }
            renderSequence(graph, detail, snapshot.sequences().getFirst());
        }

        root.addChildren(title, tabs, graph, detail);
        return ModularUI.of(UI.of(root));
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

        var operation = new Label();
        operation.setText(Component.literal(step.operation().getPath()));
        operation.textStyle(style -> style.fontSize(12).textColor(0xffffffff).textShadow(true));
        var recipe = new Label();
        recipe.setText(Component.literal(step.recipe().getPath()));
        recipe.textStyle(style -> style.fontSize(8).textColor(0xffd7e5e7));
        var status = new Label();
        status.setText(Component.translatable("screen.aeprimitives.process_analyzer.status." +
                step.status().name().toLowerCase(), step.providers().size()));
        status.textStyle(style -> style.fontSize(9).textColor(0xffffffff));
        node.addChildren(operation, recipe, status);
        node.addEventListener(UIEvents.CLICK, event -> detail.setText(stepDetails(step)));
        return node;
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
        return Component.literal(providers);
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
