package dev.yuzhe.aeprimitives.sequence;

import appeng.helpers.patternprovider.PatternProviderLogic;
import dev.yuzhe.aeprimitives.operation.BoundOperationRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Tracks live sequence catalogs and refreshes only providers that advertise operation capabilities. */
public final class SequenceRuntime {
    private static final WeakHashMap<PatternProviderLogic, List<SequencePatternSpec>> SEQUENCE_SOURCES = new WeakHashMap<>();
    private static final Set<PatternProviderLogic> OPERATION_PROVIDERS = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final BoundOperationRegistry BOUND = new BoundOperationRegistry();
    private static boolean refreshing;

    public static synchronized BoundOperationRegistry boundPatterns() { return BOUND; }

    public static synchronized void registerOperationProvider(PatternProviderLogic provider, boolean present) {
        if (present) OPERATION_PROVIDERS.add(provider);
        else OPERATION_PROVIDERS.remove(provider);
    }

    public static synchronized void update(PatternProviderLogic owner, List<SequencePatternSpec> sequences) {
        if (sequences.isEmpty()) SEQUENCE_SOURCES.remove(owner);
        else SEQUENCE_SOURCES.put(owner, List.copyOf(sequences));
        rebuild(owner);
    }

    public static synchronized void remove(PatternProviderLogic owner) {
        OPERATION_PROVIDERS.remove(owner);
        if (SEQUENCE_SOURCES.remove(owner) != null) rebuild(owner);
    }

    private static void rebuild(PatternProviderLogic owner) {
        var combined = new ArrayList<SequencePatternSpec>();
        SEQUENCE_SOURCES.values().forEach(combined::addAll);
        combined.sort(Comparator.comparing(spec -> spec.id().toString()));
        int before = BOUND.revision();
        BOUND.replaceSequences(combined);
        if (BOUND.revision() == before || refreshing) return;

        refreshing = true;
        try {
            for (var provider : List.copyOf(OPERATION_PROVIDERS)) {
                if (provider != owner) provider.updatePatterns();
            }
        } finally {
            refreshing = false;
        }
    }

    private SequenceRuntime() {}
}
