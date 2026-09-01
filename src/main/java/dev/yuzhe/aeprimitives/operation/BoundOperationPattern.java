package dev.yuzhe.aeprimitives.operation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import dev.yuzhe.aeprimitives.operation.OperationInput;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

/** A concrete, short-lived AE processing pattern derived from an operation capability. */
public final class BoundOperationPattern implements IPatternDetails {
    private final OperationStepSpec step;
    private final AEItemKey definition;
    private final IInput[] inputs;

    public BoundOperationPattern(OperationStepSpec step, ItemLike definitionItem) {
        this.step = step;
        var stack = new ItemStack(definitionItem);
        var data = new CompoundTag();
        data.putString("boundOperation", step.operation().toString());
        data.putString("boundRecipe", step.recipeId().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        definition = AEItemKey.of(stack);
        inputs = step.inputs().stream().map(ExactInput::new).toArray(IInput[]::new);
    }

    public OperationStepSpec step() { return step; }
    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return inputs.clone(); }
    @Override public List<GenericStack> getOutputs() { return step.outputs(); }

    private record ExactInput(OperationInput input) implements IInput {
        @Override public GenericStack[] getPossibleInputs() {
            return input.alternatives().toArray(GenericStack[]::new);
        }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey key, Level level) {
            return input.alternatives().stream().anyMatch(candidate -> candidate.what().equals(key));
        }
        @Override public AEKey getRemainingKey(AEKey key) { return input.remainingKey(); }
    }
}
