package dev.yuzhe.aeprimitives.sequence;

import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.operation.OperationInput;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** One concrete recipe binding produced while compiling a sequence. */
public record OperationStepSpec(
        ResourceLocation recipeId,
        ResourceLocation operation,
        List<OperationInput> inputs,
        List<GenericStack> outputs) {

    public OperationStepSpec {
        if (recipeId == null || operation == null) throw new IllegalArgumentException("step identity is required");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (outputs.isEmpty()) throw new IllegalArgumentException("a step needs an output");
    }
}
