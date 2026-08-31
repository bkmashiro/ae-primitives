package dev.yuzhe.aeprimitives.kinetics.catalyst;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record CatalystDefinition(
        ResourceLocation id,
        List<String> activation,
        String fanProcessingType,
        Optional<ResourceLocation> installRemainder,
        Optional<ResourceLocation> removalResult,
        CatalystVisual display) {

    public CatalystDefinition {
        activation = List.copyOf(activation);
    }

    public static CatalystDefinition parse(ResourceLocation id, JsonObject json) {
        if (!json.has("activation") || !json.get("activation").isJsonArray()) {
            throw new IllegalArgumentException(id + " requires an activation array");
        }
        var activation = new ArrayList<String>();
        for (var entry : json.getAsJsonArray("activation")) {
            var value = entry.getAsString();
            if (value.isBlank() || value.equals("#")) throw new IllegalArgumentException(id + " has an invalid activation entry");
            activation.add(value);
        }
        if (activation.isEmpty()) throw new IllegalArgumentException(id + " requires at least one activation entry");
        if (!json.has("fan_processing_type")) {
            throw new IllegalArgumentException(id + " requires fan_processing_type");
        }
        var fanType = json.get("fan_processing_type").getAsString();
        ResourceLocation.parse(fanType);

        var installRemainder = optionalId(json, "installation_remainder").or(() -> optionalId(json, "install_remainder"));
        var removalResult = optionalId(json, "removal_result");
        var display = CatalystVisual.item();
        if (json.has("display")) {
            var displayJson = json.getAsJsonObject("display");
            var kind = CatalystVisual.Kind.valueOf(displayJson.get("kind").getAsString().toUpperCase(Locale.ROOT));
            var resource = optionalId(displayJson, "resource").or(() -> optionalId(displayJson, "id"));
            if (kind != CatalystVisual.Kind.ITEM && resource.isEmpty()) {
                throw new IllegalArgumentException(id + " display kind " + kind + " requires resource");
            }
            var tint = Optional.<Integer>empty();
            if (displayJson.has("color")) {
                var hex = displayJson.get("color").getAsString().replace("#", "");
                long value = Long.parseLong(hex, 16);
                if (hex.length() == 6) value |= 0xff000000L;
                if (hex.length() != 6 && hex.length() != 8) throw new IllegalArgumentException(id + " display color must be RRGGBB or AARRGGBB");
                tint = Optional.of((int) value);
            }
            display = new CatalystVisual(kind, resource, tint);
        }
        return new CatalystDefinition(id, activation, fanType, installRemainder, removalResult, display);
    }

    private static Optional<ResourceLocation> optionalId(JsonObject json, String key) {
        return json.has(key) ? Optional.of(ResourceLocation.parse(json.get(key).getAsString())) : Optional.empty();
    }
}
