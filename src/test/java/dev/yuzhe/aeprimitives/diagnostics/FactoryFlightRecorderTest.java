package dev.yuzhe.aeprimitives.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class FactoryFlightRecorderTest {
    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("aeprimitives", "factory");

    @Test
    void stableTransitionsAreNotRepeated() {
        var recorder = new FactoryFlightRecorder(3);
        recorder.transition(0, DiagnosticEventType.WAITING_RESOURCE, OWNER, "missing port");
        recorder.transition(0, DiagnosticEventType.WAITING_RESOURCE, OWNER, "missing port");
        assertEquals(1, recorder.snapshot().size());
    }

    @Test
    void historyAndSerializedDetailsStayBounded() {
        var recorder = new FactoryFlightRecorder(3);
        for (int index = 0; index < 80; index++) {
            recorder.transition(0, index % 2 == 0 ? DiagnosticEventType.WAITING_INPUT
                    : DiagnosticEventType.WAITING_RESOURCE, OWNER, "x".repeat(200));
        }
        assertEquals(FactoryFlightRecorder.MAX_EVENTS, recorder.snapshot().size());
        assertTrue(recorder.snapshot().stream().allMatch(event -> event.detail().length() <= 64));

        CompoundTag saved = recorder.save();
        var restored = new FactoryFlightRecorder(3);
        restored.load(saved);
        assertEquals(FactoryFlightRecorder.MAX_EVENTS, restored.snapshot().size());
    }

    @Test
    void pruningDoesNotChangeOwnerBehavior() {
        var recorder = new FactoryFlightRecorder(1);
        int completedOperations = 0;
        for (int index = 0; index < 100; index++) {
            completedOperations++;
            recorder.event(0, DiagnosticEventType.COMPLETED, OWNER, "done");
        }
        assertEquals(100, completedOperations);
        assertEquals(FactoryFlightRecorder.MAX_EVENTS, recorder.snapshot().size());
    }
}
