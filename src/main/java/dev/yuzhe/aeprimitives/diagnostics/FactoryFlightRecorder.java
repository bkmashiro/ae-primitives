package dev.yuzhe.aeprimitives.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Owner-local bounded transition history. */
public final class FactoryFlightRecorder {
    public static final int MAX_EVENTS = 32;
    private static final int MAX_DETAIL = 64;
    private final ArrayDeque<FactoryDiagnosticEvent> events = new ArrayDeque<>();
    private final String[] lastSignatures;
    private long nextSequence;

    public FactoryFlightRecorder(int lanes) {
        if (lanes < 1 || lanes > 64) throw new IllegalArgumentException("invalid lane count");
        lastSignatures = new String[lanes];
    }

    public void transition(int lane, DiagnosticEventType type, ResourceLocation cause, String detail) {
        checkLane(lane);
        String signature = type + "|" + cause + "|" + bounded(detail);
        if (signature.equals(lastSignatures[lane])) return;
        lastSignatures[lane] = signature;
        append(lane, type, cause, detail);
    }

    public void event(int lane, DiagnosticEventType type, ResourceLocation cause, String detail) {
        checkLane(lane);
        append(lane, type, cause, detail);
    }

    public List<FactoryDiagnosticEvent> snapshot() {
        return List.copyOf(events);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("nextSequence", nextSequence);
        var list = new ListTag();
        for (var event : events) {
            var entry = new CompoundTag();
            entry.putLong("sequence", event.sequence());
            entry.putInt("lane", event.lane());
            entry.putString("type", event.type().name());
            if (event.cause() != null) entry.putString("cause", event.cause().toString());
            entry.putString("detail", bounded(event.detail()));
            list.add(entry);
        }
        tag.put("events", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        events.clear();
        java.util.Arrays.fill(lastSignatures, null);
        nextSequence = Math.max(0, tag.getLong("nextSequence"));
        var list = tag.getList("events", Tag.TAG_COMPOUND);
        int start = Math.max(0, list.size() - MAX_EVENTS);
        for (int index = start; index < list.size(); index++) {
            var entry = list.getCompound(index);
            int lane = entry.getInt("lane");
            if (lane < 0 || lane >= lastSignatures.length) continue;
            DiagnosticEventType type;
            try {
                type = DiagnosticEventType.valueOf(entry.getString("type"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ResourceLocation cause = ResourceLocation.tryParse(entry.getString("cause"));
            events.addLast(new FactoryDiagnosticEvent(Math.max(0, entry.getLong("sequence")), lane,
                    type, cause, bounded(entry.getString("detail"))));
        }
        if (!events.isEmpty()) nextSequence = Math.max(nextSequence, events.getLast().sequence() + 1);
    }

    private void append(int lane, DiagnosticEventType type, ResourceLocation cause, String detail) {
        events.addLast(new FactoryDiagnosticEvent(nextSequence++, lane, type, cause, bounded(detail)));
        while (events.size() > MAX_EVENTS) events.removeFirst();
    }

    private static String bounded(String detail) {
        if (detail == null) return "";
        return detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }

    private void checkLane(int lane) {
        if (lane < 0 || lane >= lastSignatures.length) throw new IndexOutOfBoundsException(lane);
    }
}
