package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.yuzhe.aeprimitives.kinetics.AePrimitivesKinetics;
import dev.yuzhe.aeprimitives.kinetics.catalyst.CatalystRegistry;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class KineticVirtualLaneExecutor implements VirtualMachineLaneExecutor {
    public static final KineticVirtualLaneExecutor INSTANCE = new KineticVirtualLaneExecutor();

    @Override
    public boolean supports(MachineSpaceEnvelope envelope) {
        KineticMachineKind kind = resolveKind(envelope);
        return kind == KineticMachineKind.PRESS || kind == KineticMachineKind.CRUSHER || kind == KineticMachineKind.FAN;
    }

    @Override
    public LanePlan prepare(LaneContext context) {
        ItemStack input = context.inputs().getStackInSlot(0);
        if (input.isEmpty()) return null;
        KineticMachineKind kind = resolveKind(context.envelope());
        if (kind == KineticMachineKind.PRESS || kind == KineticMachineKind.CRUSHER) {
            ProcessingRecipe<?, ?> recipe = KineticProcessBehavior.CreateRecipe.findRecipe(kind, context.level(), input);
            return recipe == null ? null : new KineticPlan(context, kind, recipe, null, null);
        }
        if (kind != KineticMachineKind.FAN) return null;
        var catalyst = resolveCatalyst(context.envelope(), context.level());
        if (catalyst == null || !catalyst.type().canProcess(input.copyWithCount(1), context.level())) return null;
        List<ItemStack> preview = previewFanOutputs(catalyst.processingType(), catalyst.type(), context, input);
        return preview == null ? null : new KineticPlan(context, kind, null, catalyst.type(), preview);
    }

    @Override
    public void release(LaneContext context) {
        KineticFactoryPortBlockEntity port = findPort(context);
        if (port != null) port.requestLane(context.factoryPos(), context.lane(), 0);
    }

    private static KineticMachineKind resolveKind(MachineSpaceEnvelope envelope) {
        if (!envelope.blockId().getNamespace().equals(AePrimitivesKinetics.MOD_ID)) return null;
        String configured = envelope.configuration().getString("kind");
        for (KineticMachineKind kind : KineticMachineKind.values()) {
            if (kind.id().equals(configured) && kind.id().equals(envelope.blockId().getPath())) return kind;
        }
        return null;
    }

    private static Catalyst resolveCatalyst(MachineSpaceEnvelope envelope, net.minecraft.server.level.ServerLevel level) {
        ResourceLocation id = ResourceLocation.tryParse(envelope.configuration().getString("catalystId"));
        var definition = id == null ? null : CatalystRegistry.get(id).orElse(null);
        if (definition == null || !envelope.configuration().contains("catalystStack")) return null;
        ItemStack stack = ItemStack.parseOptional(
                level.registryAccess(),
                envelope.configuration().getCompound("catalystStack"));
        if (stack.isEmpty() || CatalystRegistry.find(stack).map(candidate -> !candidate.id().equals(id)).orElse(true)) return null;
        try {
            FanProcessingType type = FanProcessingType.parse(definition.fanProcessingType());
            return type == null ? null : new Catalyst(definition.fanProcessingType(), type);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<ItemStack> previewFanOutputs(
            String processingType, FanProcessingType type, LaneContext context, ItemStack input) {
        AllRecipeTypes recipeType = processingType.equals(AllRecipeTypes.SPLASHING.getId().toString()) ? AllRecipeTypes.SPLASHING
                : processingType.equals(AllRecipeTypes.HAUNTING.getId().toString()) ? AllRecipeTypes.HAUNTING : null;
        if (recipeType != null) {
            var recipe = findProcessingRecipe(recipeType, context, input);
            return recipe == null ? null : recipe.getRollableResultsAsItemStacks();
        }
        // Create's smoking and blasting fan types wrap deterministic vanilla recipes.
        return type.process(input.copyWithCount(1), context.level());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ProcessingRecipe<?, ?> findProcessingRecipe(
            AllRecipeTypes type, LaneContext context, ItemStack input) {
        return (ProcessingRecipe<?, ?>) context.level().getRecipeManager()
                .getRecipeFor((net.minecraft.world.item.crafting.RecipeType) type.getType(),
                        new SingleRecipeInput(input.copyWithCount(1)), context.level())
                .map(holder -> ((net.minecraft.world.item.crafting.RecipeHolder<?>) holder).value())
                .orElse(null);
    }

    private static KineticFactoryPortBlockEntity findPort(LaneContext context) {
        for (Direction direction : Direction.values()) {
            if (context.level().getBlockEntity(context.factoryPos().relative(direction))
                    instanceof KineticFactoryPortBlockEntity port) return port;
        }
        return null;
    }

    private record KineticPlan(
            LaneContext context,
            KineticMachineKind kind,
            ProcessingRecipe<?, ?> recipe,
            FanProcessingType fanType,
            List<ItemStack> fanPreview) implements LanePlan {
        @Override public int durationTicks() { return (int) KineticMachineBlockEntity.WORK_PER_RECIPE; }
        @Override public int workPerTick() {
            KineticFactoryPortBlockEntity port = findPort(context);
            return port == null ? 1 : Math.max(1, (int) Math.abs(port.getSpeed()));
        }
        @Override public double idleAePower() { return 2.0; }
        @Override public List<ItemStack> previewOutputs() {
            return recipe != null ? recipe.getRollableResultsAsItemStacks() : fanPreview;
        }
        @Override public void setActive(boolean active) {
            KineticFactoryPortBlockEntity port = findPort(context);
            if (port != null) port.requestLane(context.factoryPos(), context.lane(), active ? kind.stressImpact() : 0);
        }
        @Override public boolean resourcesAvailable() {
            KineticFactoryPortBlockEntity port = findPort(context);
            return port != null && port.canRunLane(context.factoryPos());
        }
        @Override public List<ItemStack> complete(ItemStackHandler inputs) {
            ItemStack input = inputs.getStackInSlot(0);
            List<ItemStack> outputs;
            if (kind == KineticMachineKind.FAN) {
                Catalyst current = resolveCatalyst(context.envelope(), context.level());
                if (current == null || !current.type().canProcess(input.copyWithCount(1), context.level())) return null;
                outputs = current.type().process(input.copyWithCount(1), context.level());
            } else {
                ProcessingRecipe<?, ?> current = KineticProcessBehavior.CreateRecipe.findRecipe(kind, context.level(), input);
                if (current == null) return null;
                outputs = current.rollResults(context.level().random);
            }
            if (outputs == null) return null;
            inputs.extractItem(0, 1, false);
            return outputs;
        }
    }

    private record Catalyst(String processingType, FanProcessingType type) {}

    private KineticVirtualLaneExecutor() {}
}
