package dev.yuzhe.aeprimitives.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class CraftingAutopsyEngineTest {
    private static final ResourceLocation OWNER = id("factory");

    @Test
    void explainsOutputBlockage() {
        var report = explain(DiagnosticEventType.BLOCKED_OUTPUT, "output_buffer");
        assertEquals(id("output_buffer"), report.target());
        assertTrue(report.chain().getLast().contains("no capacity"));
    }

    @Test
    void explainsMissingExternalResource() {
        var report = explain(DiagnosticEventType.WAITING_RESOURCE, "external_resource");
        assertEquals(id("external_resource"), report.target());
        assertTrue(report.chain().get(1).contains("unavailable"));
    }

    @Test
    void explainsReloadRecovery() {
        var report = explain(DiagnosticEventType.RECOVERED, "reload_recovery");
        assertTrue(report.chain().getFirst().contains("loaded"));
        assertTrue(report.chain().getLast().contains("resumed"));
    }

    @Test
    void progressClearsStaleBlocker() {
        var events = List.of(
                new FactoryDiagnosticEvent(1, 0, DiagnosticEventType.BLOCKED_OUTPUT, id("output_buffer"), "full"),
                new FactoryDiagnosticEvent(2, 0, DiagnosticEventType.STARTED, OWNER, "running"));
        assertTrue(CraftingAutopsyEngine.explain(OWNER, events).isEmpty());
    }

    private static CraftingAutopsy explain(DiagnosticEventType type, String target) {
        return CraftingAutopsyEngine.explain(OWNER,
                List.of(new FactoryDiagnosticEvent(1, 0, type, id(target), "restore the required resource port")))
                .getFirst();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aeprimitives", path);
    }
}
