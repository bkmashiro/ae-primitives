package dev.yuzhe.aeprimitives.diagnostics;

import net.minecraft.resources.ResourceLocation;

/** Compact owner-local event. It deliberately contains no item stack or recipe graph. */
public record FactoryDiagnosticEvent(
        long sequence,
        int lane,
        DiagnosticEventType type,
        ResourceLocation cause,
        String detail) {
    public FactoryDiagnosticEvent {
        if (sequence < 0 || lane < 0 || type == null) {
            throw new IllegalArgumentException("invalid diagnostic event");
        }
        detail = detail == null ? "" : detail;
    }
}
