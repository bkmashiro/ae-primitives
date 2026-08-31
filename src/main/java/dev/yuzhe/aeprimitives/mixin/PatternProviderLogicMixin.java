package dev.yuzhe.aeprimitives.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import dev.yuzhe.aeprimitives.operation.OperationPatternDetails;
import dev.yuzhe.aeprimitives.sequence.SequencePatternDetails;
import dev.yuzhe.aeprimitives.sequence.SequenceRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Expands our declarative patterns while leaving AE's normal provider transport untouched. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin {
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private Set<AEKey> patternInputs;

    @Inject(method = "updatePatterns", at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;requestUpdate(Lappeng/api/networking/IManagedGridNode;)V"
    ))
    private void aeprimitives$expandPatterns(CallbackInfo ci) {
        var sequences = patterns.stream()
                .filter(SequencePatternDetails.class::isInstance)
                .map(SequencePatternDetails.class::cast)
                .map(SequencePatternDetails::sequence)
                .toList();
        var operations = patterns.stream()
                .filter(OperationPatternDetails.class::isInstance)
                .map(OperationPatternDetails.class::cast)
                .toList();

        var self = (PatternProviderLogic) (Object) this;
        SequenceRuntime.registerOperationProvider(self, !operations.isEmpty());
        SequenceRuntime.update(self, sequences);
        if (sequences.isEmpty() && operations.isEmpty()) return;

        // Update the live sequence catalog first so a provider containing both marker types
        // can expose the newly bound operations in this same update pass.
        var boundOperations = new ArrayList<IPatternDetails>();
        for (var operation : operations) {
            boundOperations.addAll(SequenceRuntime.boundPatterns().patternsFor(
                    operation.spec(), operation.definition().getItem()));
        }

        var expanded = new ArrayList<IPatternDetails>();
        for (var pattern : patterns) {
            if (!(pattern instanceof SequencePatternDetails) && !(pattern instanceof OperationPatternDetails)) {
                expanded.add(pattern);
            }
        }
        expanded.addAll(boundOperations);
        patterns.clear();
        patterns.addAll(expanded);
        patternInputs.clear();
        for (var pattern : patterns) {
            for (var input : pattern.getInputs()) {
                for (var possible : input.getPossibleInputs()) {
                    patternInputs.add(possible.what().dropSecondary());
                }
            }
        }
    }
}
