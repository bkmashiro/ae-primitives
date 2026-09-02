package dev.yuzhe.aeprimitives.commissioning;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Small in-memory input ledger. It has no bridge to item handlers, AE storage, players or worlds. */
final class VirtualCommissioningInventory {
    private static final int MAX_KEYS = 32;
    private final Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();

    void seed(ResourceLocation id, long amount) {
        if (id == null || amount <= 0) throw new IllegalArgumentException("invalid synthetic input");
        if (!amounts.containsKey(id) && amounts.size() >= MAX_KEYS) {
            throw new IllegalArgumentException("synthetic input inventory is full");
        }
        amounts.merge(id, amount, Math::addExact);
    }

    boolean require(ResourceLocation id, long amount, boolean retained) {
        long available = amounts.getOrDefault(id, 0L);
        if (available < amount) return false;
        if (!retained) amounts.put(id, available - amount);
        return true;
    }

    Map<ResourceLocation, Long> snapshot() {
        return Map.copyOf(amounts);
    }
}
