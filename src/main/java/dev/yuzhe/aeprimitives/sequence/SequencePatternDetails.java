package dev.yuzhe.aeprimitives.sequence;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

/** Marker decoded from a sequence macro; it is compiled and removed by the provider integration. */
public record SequencePatternDetails(AEItemKey definition, SequencePatternSpec sequence) implements IPatternDetails {
    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return new IInput[0]; }
    @Override public List<GenericStack> getOutputs() { return List.of(); }
}
