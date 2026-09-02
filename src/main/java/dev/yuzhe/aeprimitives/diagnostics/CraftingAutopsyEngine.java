package dev.yuzhe.aeprimitives.diagnostics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Builds short explanations on request; the recorder never stores whole graphs. */
public final class CraftingAutopsyEngine {
    private static final EnumSet<DiagnosticEventType> ACTIONABLE = EnumSet.of(
            DiagnosticEventType.WAITING_INPUT,
            DiagnosticEventType.WAITING_RESOURCE,
            DiagnosticEventType.BLOCKED_OUTPUT,
            DiagnosticEventType.RECOVERED);

    public static List<CraftingAutopsy> explain(
            ResourceLocation owner, List<FactoryDiagnosticEvent> events) {
        var latest = new LinkedHashMap<Integer, FactoryDiagnosticEvent>();
        for (var event : events) {
            if (ACTIONABLE.contains(event.type())) latest.put(event.lane(), event);
            else if (event.type() == DiagnosticEventType.STARTED || event.type() == DiagnosticEventType.COMPLETED) {
                latest.remove(event.lane());
            }
        }
        var reports = new ArrayList<CraftingAutopsy>();
        for (var event : latest.values()) {
            reports.add(new CraftingAutopsy(owner, event.lane(), event.sequence(), event.type(), event.cause(),
                    chain(event)));
        }
        return List.copyOf(reports);
    }

    private static List<String> chain(FactoryDiagnosticEvent event) {
        String lane = "lane " + (event.lane() + 1);
        return switch (event.type()) {
            case BLOCKED_OUTPUT -> List.of(lane + " completed work", "pending output retained",
                    "output buffer has no capacity");
            case WAITING_RESOURCE -> List.of(lane + " plan is ready", "external resource unavailable",
                    event.detail().isBlank() ? "restore the required resource port" : event.detail());
            case WAITING_INPUT -> List.of(lane + " is configured", "no matching synthetic input plan",
                    "supply the required lane input");
            case RECOVERED -> List.of(lane + " owned state loaded", "progress or pending output restored",
                    "event-driven scheduler resumed");
            default -> throw new IllegalArgumentException("event is not actionable: " + event.type());
        };
    }

    private CraftingAutopsyEngine() {
    }
}
