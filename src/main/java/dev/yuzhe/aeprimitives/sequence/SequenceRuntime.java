package dev.yuzhe.aeprimitives.sequence;

import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternProviderLogic;
import dev.yuzhe.aeprimitives.diagnostics.OperationProviderView;
import dev.yuzhe.aeprimitives.diagnostics.ProcessDiagnosticModel;
import dev.yuzhe.aeprimitives.diagnostics.ProcessDiagnosticSnapshot;
import dev.yuzhe.aeprimitives.operation.BoundOperationRegistry;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Tracks live sequence and operation catalogs from provider update events; it never scans the world. */
public final class SequenceRuntime {
    private record OperationProviderState(BlockEntity blockEntity, List<OperationPatternSpec> operations) {
        private OperationProviderState {
            operations = List.copyOf(operations);
        }
    }

    private static final WeakHashMap<PatternProviderLogic, List<SequencePatternSpec>> SEQUENCE_SOURCES =
            new WeakHashMap<>();
    private static final WeakHashMap<PatternProviderLogic, OperationProviderState> OPERATION_PROVIDERS =
            new WeakHashMap<>();
    private static final BoundOperationRegistry BOUND = new BoundOperationRegistry();
    private static boolean refreshing;
    private static int diagnosticRevision;

    public static synchronized BoundOperationRegistry boundPatterns() {
        return BOUND;
    }

    public static synchronized void updateOperationProvider(
            PatternProviderLogic provider,
            BlockEntity blockEntity,
            List<OperationPatternSpec> operations) {
        OperationProviderState previous;
        if (operations.isEmpty()) {
            previous = OPERATION_PROVIDERS.remove(provider);
        } else {
            previous = OPERATION_PROVIDERS.put(provider, new OperationProviderState(blockEntity, operations));
        }
        var current = OPERATION_PROVIDERS.get(provider);
        if (!java.util.Objects.equals(previous, current)) diagnosticRevision++;
    }

    public static synchronized void update(PatternProviderLogic owner, List<SequencePatternSpec> sequences) {
        List<SequencePatternSpec> previous;
        if (sequences.isEmpty()) previous = SEQUENCE_SOURCES.remove(owner);
        else previous = SEQUENCE_SOURCES.put(owner, List.copyOf(sequences));
        var current = SEQUENCE_SOURCES.get(owner);
        if (!java.util.Objects.equals(previous, current)) diagnosticRevision++;
        rebuild(owner);
    }

    public static synchronized void remove(PatternProviderLogic owner) {
        boolean changed = OPERATION_PROVIDERS.remove(owner) != null;
        boolean removedSequence = SEQUENCE_SOURCES.remove(owner) != null;
        if (changed || removedSequence) diagnosticRevision++;
        if (removedSequence) rebuild(owner);
    }

    public static synchronized ProcessDiagnosticSnapshot snapshot(IGrid grid) {
        var sequences = new ArrayList<SequencePatternSpec>();
        SEQUENCE_SOURCES.forEach((provider, specs) -> {
            if (provider.getGrid() == grid) sequences.addAll(specs);
        });
        var providers = new ArrayList<OperationProviderView>();
        OPERATION_PROVIDERS.forEach((provider, state) -> {
            if (provider.getGrid() != grid) return;
            if (state.blockEntity().isRemoved()) return;
            var level = state.blockEntity().getLevel();
            if (level == null) return;
            providers.add(new OperationProviderView(
                    level.dimension().location().toString(),
                    state.blockEntity().getBlockPos(),
                    provider.isBusy(),
                    state.operations()));
        });
        return ProcessDiagnosticModel.build(diagnosticRevision, sequences, providers);
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
            for (var provider : List.copyOf(OPERATION_PROVIDERS.keySet())) {
                if (provider != owner) provider.updatePatterns();
            }
        } finally {
            refreshing = false;
        }
    }

    private SequenceRuntime() {
    }
}
