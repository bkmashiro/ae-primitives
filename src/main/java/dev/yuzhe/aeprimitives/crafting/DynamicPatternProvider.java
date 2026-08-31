package dev.yuzhe.aeprimitives.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class DynamicPatternProvider implements ICraftingProvider {
    private static final Set<DynamicPatternProvider> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private final PrimitiveMachineBlockEntity machine;

    public DynamicPatternProvider(PrimitiveMachineBlockEntity machine) {
        this.machine = machine;
        synchronized (LIVE) {
            LIVE.add(this);
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (!machine.isPatternProviderMode()) return List.of();
        return List.copyOf(LazyPatternRegistry.patternsFor(machine.kind()));
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
        return details instanceof LazyPrimitivePattern pattern && machine.acceptPattern(pattern, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return machine.isPatternBusy();
    }

    public void refresh() {
        machine.refreshPatternProvider();
    }

    public static void refreshAll() {
        synchronized (LIVE) {
            for (var provider : List.copyOf(LIVE)) provider.refresh();
        }
    }
}
