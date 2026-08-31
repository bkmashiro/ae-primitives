package dev.yuzhe.aeprimitives.sequence;

import appeng.api.stacks.AEItemKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Extension point for optional mods that compile their own sequence recipe formats. */
public final class SequencePatternDecoders {
    private static final List<Decoder> DECODERS = new CopyOnWriteArrayList<>();

    public static void register(Decoder decoder) {
        if (!DECODERS.contains(decoder)) DECODERS.add(decoder);
    }

    public static SequencePatternDetails decode(AEItemKey definition, Level level, ResourceLocation recipeId) {
        for (var decoder : DECODERS) {
            var details = decoder.decode(definition, level, recipeId);
            if (details != null) return details;
        }
        return null;
    }

    @FunctionalInterface
    public interface Decoder {
        SequencePatternDetails decode(AEItemKey definition, Level level, ResourceLocation recipeId);
    }

    private SequencePatternDecoders() {
    }
}
