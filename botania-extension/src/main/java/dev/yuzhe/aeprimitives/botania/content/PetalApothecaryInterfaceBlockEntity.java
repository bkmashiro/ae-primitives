package dev.yuzhe.aeprimitives.botania.content;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.space.MachineSpacePackable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.block.PetalApothecary;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

public final class PetalApothecaryInterfaceBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IActionHost, MachineSpacePackable {
    private static final IGridNodeListener<PetalApothecaryInterfaceBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(PetalApothecaryInterfaceBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
    private final ItemStackHandler inventory = new ItemStackHandler(25) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 16; }
        @Override protected void onContentsChanged(int slot) { dirty = true; setChanged(); }
    };
    private boolean dirty = true;
    private boolean gridTopologyDirty = true;
    private int gridBootstrapTicks = 20;
    private final boolean[] factoryLanes = new boolean[HeterogeneousFactoryBlockEntity.LANE_COUNT];
    @Nullable private BlockPos factoryOwner;
    private int factoryOwnerLane = -1;
    private int observedFluid = Integer.MIN_VALUE;
    private boolean observedApothecaryEmpty = true;

    public PetalApothecaryInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(BotaniaContent.PETAL_APOTHECARY_INTERFACE_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PetalApothecaryInterfaceBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.observeFactoryResource();
        be.refreshGridConnections();
        BotaniaGridSupport.flushOutputs(be.mainNode, be.inventory, 16, 25);
        if (!be.dirty || !be.mainNode.isActive() || be.factoryOwner != null) return;
        be.dirty = false;
        be.tryCraft(server);
    }

    public ItemStackHandler inventory() { return inventory; }
    public void markDirty() { dirty = true; gridTopologyDirty = true; wakeFactory(); }
    public boolean craftForTest(ServerLevel level) { return tryCraft(level); }

    @Override public boolean canPackIntoMachineSpace() {
        if (factoryOwner != null) return false;
        return portInventoryEmpty();
    }

    @Override public CompoundTag writeMachineSpaceConfiguration(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override public boolean restoreMachineSpaceConfiguration(CompoundTag configuration,
                                                              HolderLookup.Provider registries) {
        return configuration.isEmpty() && canPackIntoMachineSpace();
    }

    boolean requestFactoryLane(BlockPos factoryPos, int lane, boolean active) {
        if (lane < 0 || lane >= factoryLanes.length || !isAdjacentFactory(factoryPos)) return false;
        if (active && ((factoryOwner != null
                && (!factoryOwner.equals(factoryPos) || factoryOwnerLane != lane))
                || !portInventoryEmpty())) return false;
        if (active) {
            factoryOwner = factoryPos.immutable();
            factoryOwnerLane = lane;
            factoryLanes[lane] = true;
        } else if (factoryOwner != null && factoryOwner.equals(factoryPos) && factoryOwnerLane == lane) {
            factoryLanes[lane] = false;
            factoryOwner = null;
            factoryOwnerLane = -1;
        }
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        observedFluid = fluidOrdinal(apothecary);
        observedApothecaryEmpty = apothecary == null || apothecary.isEmpty();
        setChanged();
        return true;
    }

    boolean ownsFactoryLane(BlockPos factoryPos, int lane) {
        return lane >= 0 && lane < factoryLanes.length && factoryLanes[lane]
                && factoryOwner != null && factoryOwner.equals(factoryPos) && factoryOwnerLane == lane;
    }

    private void refreshGridConnections() {
        var state = BotaniaGridSupport.refreshConnections(level, worldPosition, mainNode, gridTopologyDirty, gridBootstrapTicks);
        gridTopologyDirty = state.dirty();
        gridBootstrapTicks = state.bootstrapTicks();
    }

    private boolean tryCraft(ServerLevel level) {
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        if (apothecary == null || apothecary.getFluid() != PetalApothecary.State.WATER || !apothecary.isEmpty()) return false;
        Match match = findMatch(level, inventory);
        if (match == null || !canQueue(match.output)) return false;
        BlockPos altarPos = apothecary.getBlockPos();
        AABB outputArea = new AABB(altarPos.above());
        Set<UUID> existing = itemEntityIds(level, outputArea);
        for (int slot : match.ingredientSlots) if (!feed(level, apothecary, inventory, slot)) return false;
        if (!match.recipe.value().matches(apothecary.getRecipeInput(), level)) return false;
        if (!feed(level, apothecary, inventory, match.reagentSlot)) return false;
        ItemStack result = captureExactOutput(level, outputArea, existing, match.output);
        if (result.isEmpty()) return false;
        queue(result);
        setChanged();
        return true;
    }

    @Nullable Match findFactoryMatch(ServerLevel level, ItemStackHandler inputs) {
        return findMatch(level, inputs);
    }

    boolean beginFactoryRecipe(BlockPos factoryPos, int lane, ItemStackHandler inputs, CompoundTag state) {
        if (!ownsFactoryLane(factoryPos, lane) || !state.isEmpty() || !(level instanceof ServerLevel server)) return false;
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        Match match = findFactoryMatch(server, inputs);
        if (apothecary == null || apothecary.getFluid() != PetalApothecary.State.WATER
                || !apothecary.isEmpty() || match == null || match.output.isEmpty()) return false;
        List<ItemStack> ingredients = new ArrayList<>();
        for (int slot : match.ingredientSlots) {
            ItemStack one = inputs.extractItem(slot, 1, false);
            if (one.isEmpty()) {
                restore(inputs, ingredients);
                return false;
            }
            ingredients.add(one.copyWithCount(1));
        }
        ItemStack reagent = inputs.extractItem(match.reagentSlot, 1, false);
        if (reagent.isEmpty()) {
            restore(inputs, ingredients);
            return false;
        }
        for (ItemStack ingredient : ingredients) {
            if (feed(server, apothecary, ingredient.copy())) continue;
            apothecary.clearContent();
            restore(inputs, ingredients);
            restore(inputs, List.of(reagent));
            return false;
        }
        if (!match.recipe.value().matches(apothecary.getRecipeInput(), server)) {
            apothecary.clearContent();
            restore(inputs, ingredients);
            restore(inputs, List.of(reagent));
            return false;
        }
        state.putString("recipe", match.recipe.id().toString());
        state.put("reagent", reagent.save(server.registryAccess()));
        state.put("output", match.output.save(server.registryAccess()));
        observedFluid = fluidOrdinal(apothecary);
        observedApothecaryEmpty = apothecary.isEmpty();
        setChanged();
        return true;
    }

    @Nullable ItemStack factoryExpectedOutput(CompoundTag state, HolderLookup.Provider registries) {
        ItemStack output = ItemStack.parseOptional(registries, state.getCompound("output"));
        return output.isEmpty() ? null : output;
    }

    boolean factoryRecipeReady(BlockPos factoryPos, int lane, CompoundTag state) {
        if (!ownsFactoryLane(factoryPos, lane) || !(level instanceof ServerLevel server)) return false;
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        RecipeHolder<PetalApothecaryRecipe> recipe = resolveFactoryRecipe(server, state);
        return apothecary != null && recipe != null
                && apothecary.getFluid() == PetalApothecary.State.WATER
                && !apothecary.isEmpty() && recipe.value().matches(apothecary.getRecipeInput(), server);
    }

    @Nullable ItemStack finishFactoryRecipe(BlockPos factoryPos, int lane, CompoundTag state) {
        if (!factoryRecipeReady(factoryPos, lane, state) || !(level instanceof ServerLevel server)) return null;
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        ItemStack reagent = ItemStack.parseOptional(server.registryAccess(), state.getCompound("reagent"));
        ItemStack expected = factoryExpectedOutput(state, server.registryAccess());
        if (apothecary == null || reagent.isEmpty() || expected == null) return null;
        AABB outputArea = new AABB(apothecary.getBlockPos().above());
        Set<UUID> existing = itemEntityIds(server, outputArea);
        if (!feed(server, apothecary, reagent.copy())) return null;
        ItemStack output = captureExactOutput(server, outputArea, existing, expected);
        if (output.isEmpty()) return null;
        observedFluid = fluidOrdinal(apothecary);
        observedApothecaryEmpty = apothecary.isEmpty();
        requestFactoryLane(factoryPos, lane, false);
        return output;
    }

    @Nullable private RecipeHolder<PetalApothecaryRecipe> resolveFactoryRecipe(ServerLevel level, CompoundTag state) {
        ResourceLocation id = ResourceLocation.tryParse(state.getString("recipe"));
        if (id == null) return null;
        var holder = level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null || !(holder.value() instanceof PetalApothecaryRecipe)) return null;
        @SuppressWarnings("unchecked") RecipeHolder<PetalApothecaryRecipe> cast =
                (RecipeHolder<PetalApothecaryRecipe>) (RecipeHolder<?>) holder;
        return cast;
    }

    private boolean feed(ServerLevel level, PetalApothecaryBlockEntity apothecary,
                         ItemStackHandler source, int slot) {
        ItemStack stack = source.extractItem(slot, 1, false);
        if (stack.isEmpty()) return false;
        if (feed(level, apothecary, stack)) return true;
        restore(source, List.of(stack));
        return false;
    }

    private boolean feed(ServerLevel level, PetalApothecaryBlockEntity apothecary, ItemStack stack) {
        BlockPos pos = apothecary.getBlockPos();
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5, stack);
        return apothecary.collideEntityItem(entity);
    }

    @Nullable private Match findMatch(ServerLevel level, ItemStackHandler source) {
        for (RecipeHolder<PetalApothecaryRecipe> holder : level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.PETAL_TYPE)) {
            List<Ingredient> ingredients = holder.value().getIngredients();
            int[] counts = new int[16];
            for (int slot = 0; slot < 16; slot++) counts[slot] = source.getStackInSlot(slot).getCount();
            int[] selected = new int[ingredients.size()];
            if (!assign(ingredients, 0, counts, selected, source)) continue;
            int reagentSlot = findSlot(holder.value().getReagent(), counts, source);
            if (reagentSlot < 0) continue;
            List<ItemStack> concrete = new ArrayList<>();
            for (int slot : selected) concrete.add(source.getStackInSlot(slot).copyWithCount(1));
            RecipeInput input = new ConcreteRecipeInput(concrete);
            if (!holder.value().matches(input, level)) continue;
            ItemStack output = holder.value().assemble(input, level.registryAccess());
            return new Match(holder, selected, reagentSlot, output);
        }
        return null;
    }

    private boolean assign(List<Ingredient> ingredients, int at, int[] counts, int[] selected,
                           ItemStackHandler source) {
        if (at == ingredients.size()) return true;
        for (int slot = 0; slot < counts.length; slot++) {
            if (counts[slot] <= 0 || !ingredients.get(at).test(source.getStackInSlot(slot))) continue;
            counts[slot]--; selected[at] = slot;
            if (assign(ingredients, at + 1, counts, selected, source)) return true;
            counts[slot]++;
        }
        return false;
    }

    private int findSlot(Ingredient ingredient, int[] counts, ItemStackHandler source) {
        for (int slot = 0; slot < counts.length; slot++)
            if (counts[slot] > 0 && ingredient.test(source.getStackInSlot(slot))) return slot;
        return -1;
    }

    @Nullable private PetalApothecaryBlockEntity boundApothecary() {
        if (level == null) return null;
        BlockPos pos = worldPosition.relative(getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        return level.getBlockEntity(pos) instanceof PetalApothecaryBlockEntity apothecary ? apothecary : null;
    }

    private boolean canQueue(ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 16; slot < 25 && remaining > 0; slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) remaining -= stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, stack)) remaining -= current.getMaxStackSize() - current.getCount();
        }
        return remaining <= 0;
    }

    private void queue(ItemStack stack) {
        for (int slot = 16; slot < 25 && !stack.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) {
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
                inventory.setStackInSlot(slot, stack.copyWithCount(moved));
                stack.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                if (moved > 0) {
                    ItemStack grown = current.copy(); grown.grow(moved);
                    inventory.setStackInSlot(slot, grown);
                    stack.shrink(moved);
                }
            }
        }
    }

    private static Set<UUID> itemEntityIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) ids.add(entity.getUUID());
        return ids;
    }

    private static ItemStack captureExactOutput(ServerLevel level, AABB area, Set<UUID> existing,
                                                ItemStack expected) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (existing.contains(entity.getUUID()) || !entity.isAlive()) continue;
            ItemStack stack = entity.getItem();
            if (!ItemStack.isSameItemSameComponents(stack, expected) || stack.getCount() != expected.getCount()) continue;
            ItemStack result = stack.copy();
            entity.discard();
            return result;
        }
        return ItemStack.EMPTY;
    }

    private static void restore(ItemStackHandler target, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < target.getSlots() && !remaining.isEmpty(); slot++)
                remaining = target.insertItem(slot, remaining, false);
            if (!remaining.isEmpty()) throw new IllegalStateException("transaction rollback overflowed the lane input buffer");
        }
    }

    private boolean portInventoryEmpty() {
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private boolean isAdjacentFactory(BlockPos factoryPos) {
        return level != null && worldPosition.distManhattan(factoryPos) == 1
                && level.getBlockEntity(factoryPos) instanceof HeterogeneousFactoryBlockEntity;
    }

    private void observeFactoryResource() {
        if (factoryOwner == null || level == null || level.isClientSide) return;
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        int fluid = fluidOrdinal(apothecary);
        boolean empty = apothecary == null || apothecary.isEmpty();
        if (fluid != observedFluid || empty != observedApothecaryEmpty) {
            observedFluid = fluid;
            observedApothecaryEmpty = empty;
            wakeFactory();
        }
    }

    private static int fluidOrdinal(@Nullable PetalApothecaryBlockEntity apothecary) {
        return apothecary == null ? Integer.MIN_VALUE : apothecary.getFluid().ordinal();
    }

    private void wakeFactory() {
        if (factoryOwner != null && level != null && !level.isClientSide
                && level.getBlockEntity(factoryOwner) instanceof HeterogeneousFactoryBlockEntity factory)
            factory.scheduleExternalWork();
    }

    @Override public void onLoad() {
        super.onLoad();
        gridTopologyDirty = true;
        gridBootstrapTicks = 20;
        if (!level.isClientSide) mainNode.create(level, worldPosition);
    }

    @Override public void setRemoved() {
        Arrays.fill(factoryLanes, false);
        factoryOwner = null;
        factoryOwnerLane = -1;
        super.setRemoved();
        mainNode.destroy();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.put("inventory", inventory.serializeNBT(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        Arrays.fill(factoryLanes, false);
        factoryOwner = null;
        factoryOwnerLane = -1;
        observedFluid = Integer.MIN_VALUE;
        observedApothecaryEmpty = true;
        dirty = true;
    }

    @Override public IGridNode getGridNode(Direction direction) { return mainNode.isReady() ? mainNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }

    record Match(RecipeHolder<PetalApothecaryRecipe> recipe, int[] ingredientSlots,
                 int reagentSlot, ItemStack output) {}
    private record ConcreteRecipeInput(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public int size() { return items.size(); }
    }
}
