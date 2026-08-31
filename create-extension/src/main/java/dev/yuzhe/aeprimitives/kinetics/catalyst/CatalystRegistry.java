package dev.yuzhe.aeprimitives.kinetics.catalyst;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class CatalystRegistry extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, CatalystDefinition> definitions = Map.of();

    public CatalystRegistry() {
        super(GSON, "aeprimitives_kinetics/fan_catalysts");
    }

    public static Optional<CatalystDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public static Optional<CatalystDefinition> find(ItemStack stack) {
        return definitions.values().stream().filter(definition -> matches(definition, stack)).findFirst();
    }

    public static List<CatalystDefinition> all() {
        return List.copyOf(definitions.values());
    }

    static boolean matches(CatalystDefinition definition, ItemStack stack) {
        if (stack.isEmpty()) return false;
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (var activation : definition.activation()) {
            if (activation.startsWith("#")) {
                var tag = ItemTags.create(ResourceLocation.parse(activation.substring(1)));
                if (stack.is(tag)) return true;
            } else if (itemId.equals(ResourceLocation.parse(activation))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        var next = new LinkedHashMap<ResourceLocation, CatalystDefinition>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            try {
                var definition = CatalystDefinition.parse(entry.getKey(), entry.getValue().getAsJsonObject());
                next.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                LOGGER.error("Ignoring invalid fan catalyst {}", entry.getKey(), exception);
            }
        });
        definitions = Map.copyOf(next);
        LOGGER.info("Loaded {} AE Primitives fan catalysts", next.size());
    }
}
