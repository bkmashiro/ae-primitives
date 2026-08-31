package dev.yuzhe.aeprimitives.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yuzhe.aeprimitives.content.MachineKind;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleMachineAnimations {
    private static final Map<String, JsonObject> MACHINES = new ConcurrentHashMap<>();

    private SimpleMachineAnimations() {}

    static void clearCache() {
        MACHINES.clear();
    }

    static float sample(MachineKind kind, String animationName, String target, String property,
                        float clockValue, float fallback) {
        JsonObject machine = MACHINES.computeIfAbsent(kind.id(), SimpleMachineAnimations::load);
        JsonObject animations = object(machine, "animations");
        JsonObject animation = object(animations, animationName);
        if (animation == null) return fallback;
        float duration = number(animation, "duration", 1);
        float phase = normalize(clockValue / duration, text(animation, "loop", "clamp"));
        String override = System.getProperty("mcvisualharness.animationPhase");
        if (override != null) {
            try {
                phase = Math.max(0, Math.min(1, Float.parseFloat(override)));
            } catch (NumberFormatException ignored) {
                // A malformed external debug override must not break rendering.
            }
        }
        JsonArray tracks = animation.getAsJsonArray("tracks");
        if (tracks == null) return fallback;
        for (JsonElement element : tracks) {
            JsonObject track = element.getAsJsonObject();
            if (target.equals(text(track, "target", "")) && property.equals(text(track, "property", ""))) {
                return interpolate(track, phase, fallback);
            }
        }
        return fallback;
    }

    static float interpolate(JsonObject track, float phase, float fallback) {
        JsonArray frames = track.getAsJsonArray("keyframes");
        if (frames == null || frames.size() < 2) return fallback;
        JsonArray first = frames.get(0).getAsJsonArray();
        if (phase <= first.get(0).getAsFloat()) return first.get(1).getAsFloat();
        for (int index = 1; index < frames.size(); index++) {
            JsonArray right = frames.get(index).getAsJsonArray();
            if (phase > right.get(0).getAsFloat()) continue;
            JsonArray left = frames.get(index - 1).getAsJsonArray();
            float span = right.get(0).getAsFloat() - left.get(0).getAsFloat();
            float amount = span == 0 ? 0 : (phase - left.get(0).getAsFloat()) / span;
            if ("smoothstep".equals(text(track, "easing", "linear"))) {
                amount = amount * amount * (3 - 2 * amount);
            }
            return left.get(1).getAsFloat() + (right.get(1).getAsFloat() - left.get(1).getAsFloat()) * amount;
        }
        return frames.get(frames.size() - 1).getAsJsonArray().get(1).getAsFloat();
    }

    private static float normalize(float value, String loop) {
        return switch (loop) {
            case "repeat" -> value - (float) Math.floor(value);
            case "pingpong" -> {
                float wrapped = value - (float) Math.floor(value / 2) * 2;
                yield wrapped <= 1 ? wrapped : 2 - wrapped;
            }
            default -> Math.max(0, Math.min(1, value));
        };
    }

    private static JsonObject load(String machineId) {
        String path = "/assets/aeprimitives/animations/" + machineId + ".json";
        try (var stream = SimpleMachineAnimations.class.getResourceAsStream(path)) {
            if (stream == null) return new JsonObject();
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load machine animation " + path, exception);
        }
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonObject()) return null;
        return parent.getAsJsonObject(name);
    }

    private static String text(JsonObject object, String name, String fallback) {
        return object != null && object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private static float number(JsonObject object, String name, float fallback) {
        return object != null && object.has(name) ? object.get(name).getAsFloat() : fallback;
    }
}
