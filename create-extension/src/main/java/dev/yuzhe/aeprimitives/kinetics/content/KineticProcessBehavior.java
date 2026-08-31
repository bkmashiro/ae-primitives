package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;

interface KineticProcessBehavior {
    boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input);

    List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input);

    static KineticProcessBehavior forKind(KineticMachineKind kind) {
        return kind == KineticMachineKind.FAN ? Fan.INSTANCE : CreateRecipe.INSTANCE;
    }

    enum CreateRecipe implements KineticProcessBehavior {
        INSTANCE;

        @Override
        public boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            return findRecipe(machine.kind(), level, input) != null;
        }

        @Override
        public List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            var recipe = findRecipe(machine.kind(), level, input);
            return recipe == null ? null : recipe.rollResults(level.random);
        }

        @SuppressWarnings("unchecked")
        private static ProcessingRecipe<SingleRecipeInput, ?> findRecipe(
                KineticMachineKind kind, ServerLevel level, ItemStack input) {
            if (kind.recipeType() == null) return null;
            return (ProcessingRecipe<SingleRecipeInput, ?>) kind.recipeType()
                    .find(new SingleRecipeInput(input.copyWithCount(1)), level)
                    .map(holder -> holder.value())
                    .orElse(null);
        }
    }

    enum Fan implements KineticProcessBehavior {
        INSTANCE;

        @Override
        public boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            var type = processingType(machine);
            return type != null && type.canProcess(input.copyWithCount(1), level);
        }

        @Override
        public List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            var type = processingType(machine);
            if (type == null || !type.canProcess(input.copyWithCount(1), level)) return null;
            return type.process(input.copyWithCount(1), level);
        }

        private static FanProcessingType processingType(KineticMachineBlockEntity machine) {
            return machine.catalystDefinition()
                    .map(definition -> {
                        try {
                            return FanProcessingType.parse(definition.fanProcessingType());
                        } catch (RuntimeException ignored) {
                            return null;
                        }
                    })
                    .orElse(null);
        }
    }
}
