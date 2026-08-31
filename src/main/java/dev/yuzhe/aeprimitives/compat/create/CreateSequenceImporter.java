package dev.yuzhe.aeprimitives.compat.create;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.yuzhe.aeprimitives.operation.OperationInput;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Compiles Create's recipe-backed sequence into the concrete dependency chain AE expects. */
public final class CreateSequenceImporter {
    public static Result compile(ResourceLocation recipeId, SequencedAssemblyRecipe recipe) {
        if (recipe.getSequence().isEmpty()) return Result.failure("empty_sequence");
        if (recipe.resultPool.size() != 1) return Result.failure("probabilistic_result_pool");

        int loops = recipe.getLoops();
        int stepsPerLoop = recipe.getSequence().size();
        int totalSteps = Math.multiplyExact(loops, stepsPerLoop);
        var steps = new ArrayList<OperationStepSpec>(totalSteps);
        var current = ingredientInput(recipe.getIngredient());
        if (current == null) return Result.failure("empty_initial_ingredient");

        for (int absolute = 0; absolute < totalSteps; absolute++) {
            ProcessingRecipe<?, ?> processing = recipe.getSequence().get(absolute % stepsPerLoop).getRecipe();
            var inputs = new ArrayList<OperationInput>();
            inputs.add(current);

            var ingredients = processing.getIngredients();
            for (int index = 1; index < ingredients.size(); index++) {
                var input = ingredientInput(ingredients.get(index));
                if (input == null) return Result.failure("empty_step_ingredient");
                if (processing instanceof ItemApplicationRecipe application && index == 1 && application.shouldKeepHeldItem()) {
                    input = new OperationInput(input.alternatives(), input.alternatives().getFirst().what());
                }
                inputs.add(input);
            }
            for (var fluid : processing.getFluidIngredients()) {
                var options = new ArrayList<GenericStack>();
                for (var stack : fluid.getFluids()) options.add(new GenericStack(AEFluidKey.of(stack), fluid.amount()));
                if (options.isEmpty()) return Result.failure("empty_fluid_ingredient");
                inputs.add(new OperationInput(options, null));
            }

            boolean last = absolute + 1 == totalSteps;
            ItemStack output;
            if (last) {
                output = recipe.resultPool.getFirst().getStack().copy();
            } else {
                output = recipe.getTransitionalItem().copy();
                output.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                        new SequencedAssemblyRecipe.SequencedAssembly(
                                recipeId,
                                absolute + 1,
                                (absolute + 1f) / totalSteps));
            }
            if (output.isEmpty()) return Result.failure("empty_step_output");

            var operation = processing.getTypeInfo().getId();
            var syntheticId = recipeId.withSuffix("/operation_" + absolute);
            steps.add(new OperationStepSpec(
                    syntheticId,
                    operation,
                    inputs,
                    List.of(new GenericStack(AEItemKey.of(output), output.getCount()))));
            current = new OperationInput(List.of(new GenericStack(AEItemKey.of(output), output.getCount())), null);
        }

        return Result.success(new SequencePatternSpec(recipeId, steps));
    }

    private static OperationInput ingredientInput(Ingredient ingredient) {
        var unique = new LinkedHashMap<AEItemKey, GenericStack>();
        for (var stack : ingredient.getItems()) {
            if (!stack.isEmpty()) {
                var key = AEItemKey.of(stack);
                unique.putIfAbsent(key, new GenericStack(key, Math.max(1, stack.getCount())));
            }
        }
        return unique.isEmpty() ? null : new OperationInput(List.copyOf(unique.values()), null);
    }

    public record Result(SequencePatternSpec sequence, String error) {
        public static Result success(SequencePatternSpec sequence) { return new Result(sequence, null); }
        public static Result failure(String error) { return new Result(null, error); }
        public boolean successful() { return sequence != null; }
    }

    private CreateSequenceImporter() {}
}
