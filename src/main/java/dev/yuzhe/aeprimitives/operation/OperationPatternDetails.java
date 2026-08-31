package dev.yuzhe.aeprimitives.operation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

/** Marker decoded from an operation pattern. Pattern Provider expansion replaces it before AE sees it. */
public record OperationPatternDetails(AEItemKey definition, OperationPatternSpec spec) implements IPatternDetails {
    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return new IInput[0]; }
    @Override public List<GenericStack> getOutputs() { return List.of(); }
}
