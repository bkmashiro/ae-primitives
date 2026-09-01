package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

interface KineticProcessBehavior {
    int DEFAULT_OUTPUT_START = 1;
    int DEFAULT_OUTPUT_END = 10;
    int BASIN_OUTPUT_START = KineticMachineBlockEntity.BASIN_INPUT_SLOTS;
    int BASIN_OUTPUT_END = 18;

    boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input);

    List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input);

    default boolean acceptsInput(int slot) {
        return slot == 0;
    }

    default int outputStart() {
        return DEFAULT_OUTPUT_START;
    }

    default int outputEnd() {
        return DEFAULT_OUTPUT_END;
    }

    default boolean supportsFluids() {
        return false;
    }

    default int requestedLanes(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
        var input = machine.inventory().getStackInSlot(0);
        return !input.isEmpty() && canProcess(machine, level, input)
                ? Math.min(laneLimit, input.getCount())
                : 0;
    }

    default int completeCycles(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
        int completed = 0;
        for (int lane = 0; lane < laneLimit; lane++) {
            var input = machine.inventory().getStackInSlot(0);
            if (input.isEmpty()) break;
            // Invoke processing per lane so probabilistic recipes roll independently.
            var outputs = process(machine, level, input);
            if (outputs == null || !machine.canQueueAll(outputs)) break;
            machine.inventory().extractItem(0, 1, false);
            machine.queueAll(outputs);
            completed++;
        }
        return completed;
    }

    static KineticProcessBehavior forKind(KineticMachineKind kind) {
        return switch (kind) {
            case FAN -> Fan.INSTANCE;
            case BASIN -> Basin.INSTANCE;
            case FILLING -> Filling.INSTANCE;
            case DEPLOYER -> Deployer.INSTANCE;
            default -> CreateRecipe.INSTANCE;
        };
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

        @SuppressWarnings({"rawtypes", "unchecked"})
        static ProcessingRecipe<?, ?> findRecipe(
                KineticMachineKind kind, ServerLevel level, ItemStack input) {
            if (kind.recipeType() == null) return null;
            RecipeInput recipeInput;
            if (kind == KineticMachineKind.SAW) {
                var handler = new ItemStackHandler(1);
                handler.setStackInSlot(0, input.copyWithCount(1));
                recipeInput = new RecipeWrapper(handler);
            } else {
                recipeInput = new SingleRecipeInput(input.copyWithCount(1));
            }
            return (ProcessingRecipe<?, ?>) level.getRecipeManager()
                    .getRecipeFor((net.minecraft.world.item.crafting.RecipeType) kind.recipeType().getType(),
                            recipeInput, level)
                    .map(holder -> ((net.minecraft.world.item.crafting.RecipeHolder<?>) holder).value())
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

    enum Basin implements KineticProcessBehavior {
        INSTANCE;

        @Override
        public boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            return BasinProcessPlan.find(machine, level) != null;
        }

        @Override
        public List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            throw new UnsupportedOperationException("Basin recipes commit through their multi-input process plan");
        }

        @Override
        public boolean acceptsInput(int slot) {
            return slot < KineticMachineBlockEntity.BASIN_INPUT_SLOTS;
        }

        @Override
        public int outputStart() {
            return BASIN_OUTPUT_START;
        }

        @Override
        public int outputEnd() {
            return BASIN_OUTPUT_END;
        }

        @Override
        public boolean supportsFluids() {
            return true;
        }

        @Override
        public int requestedLanes(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
            var plan = BasinProcessPlan.find(machine, level);
            return plan == null ? 0 : plan.availableRuns(machine, laneLimit);
        }

        @Override
        public int completeCycles(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
            int completed = 0;
            for (int lane = 0; lane < laneLimit; lane++) {
                var plan = BasinProcessPlan.find(machine, level);
                if (plan == null || !plan.commit(machine, level)) break;
                completed++;
            }
            return completed;
        }
    }

    enum Filling implements KineticProcessBehavior {
        INSTANCE;

        @Override
        public boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            return FillingProcessPlan.find(machine, level) != null;
        }

        @Override
        public List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            throw new UnsupportedOperationException("Filling recipes commit through their fluid process plan");
        }

        @Override public boolean supportsFluids() { return true; }

        @Override
        public int requestedLanes(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
            var plan = FillingProcessPlan.find(machine, level);
            return plan == null ? 0 : plan.availableRuns(machine, laneLimit);
        }

        @Override
        public int completeCycles(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
            int completed = 0;
            for (int lane = 0; lane < laneLimit; lane++) {
                var plan = FillingProcessPlan.find(machine, level);
                if (plan == null || !plan.commit(machine, level)) break;
                completed++;
            }
            return completed;
        }
    }

    enum Deployer implements KineticProcessBehavior {
        INSTANCE;

        @Override
        public boolean canProcess(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            return DeployerProcessPlan.find(machine, level) != null;
        }

        @Override
        public List<ItemStack> process(KineticMachineBlockEntity machine, ServerLevel level, ItemStack input) {
            throw new UnsupportedOperationException("Deploying recipes commit through their tool process plan");
        }

        @Override public boolean acceptsInput(int slot) { return slot < 2; }
        @Override public int outputStart() { return 2; }

        @Override
        public int requestedLanes(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
            var plan = DeployerProcessPlan.find(machine, level);
            return plan == null ? 0 : plan.availableRuns(machine, laneLimit);
        }

        @Override
        public int completeCycles(KineticMachineBlockEntity machine, ServerLevel level, int laneLimit) {
            int completed = 0;
            for (int lane = 0; lane < laneLimit; lane++) {
                var plan = DeployerProcessPlan.find(machine, level);
                if (plan == null || !plan.commit(machine, level)) break;
                completed++;
            }
            return completed;
        }
    }
}
