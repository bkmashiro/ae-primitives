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
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

public final class RunicAltarInterfaceBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IActionHost, MachineSpacePackable {
    private static final IGridNodeListener<RunicAltarInterfaceBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(RunicAltarInterfaceBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
    private final ItemStackHandler inventory = new ItemStackHandler(25) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 16; }
        @Override protected void onContentsChanged(int slot) { dirty = true; setChanged(); }
    };
    private boolean dirty = true;
    @Nullable private ResourceLocation recipeId;
    private ItemStack reservedReagent = ItemStack.EMPTY;
    private ItemStack expectedOutput = ItemStack.EMPTY;
    private boolean gridTopologyDirty = true;
    private int gridBootstrapTicks = 20;
    private final boolean[] factoryLanes = new boolean[HeterogeneousFactoryBlockEntity.LANE_COUNT];
    @Nullable private BlockPos factoryOwner;
    @Nullable private BlockPos pendingFactoryWake;
    private int factoryOwnerLane = -1;
    private int observedMana = Integer.MIN_VALUE;
    private boolean observedAltarEmpty = true;
    private int factoryHandoffDelay;

    public RunicAltarInterfaceBlockEntity(BlockPos pos, BlockState state) { super(BotaniaContent.RUNIC_ALTAR_INTERFACE_ENTITY.get(), pos, state); }
    public ItemStackHandler inventory() { return inventory; }
    public int comparatorSignal() { return recipeId == null ? 0 : 15; }
    public void markTopologyDirty() {
        dirty = true;
        gridTopologyDirty = true;
        wakeFactory();
    }
    public boolean hasPlanForTest() { return recipeId != null; }
    public boolean startForTest(ServerLevel level) { return tryStart(level); }
    public boolean finishForTest(ServerLevel level) { return tryFinish(level); }


    @Override public boolean canPackIntoMachineSpace() {
        if (recipeId != null || factoryOwner != null || !reservedReagent.isEmpty() || !expectedOutput.isEmpty()) return false;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
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
                && (!factoryOwner.equals(factoryPos) || factoryOwnerLane != lane)) || !portInventoryEmpty())) return false;
        HeterogeneousFactoryBlockEntity releasedFactory = null;
        if (active) {
            factoryOwner = factoryPos.immutable();
            factoryOwnerLane = lane;
            factoryLanes[lane] = true;
        } else if (factoryOwner != null && factoryOwner.equals(factoryPos) && factoryOwnerLane == lane) {
            if (level != null && level.getBlockEntity(factoryOwner) instanceof HeterogeneousFactoryBlockEntity factory)
                releasedFactory = factory;
            factoryLanes[lane] = false;
            RunicAltarBlockEntity currentAltar = altar();
            pendingFactoryWake = factoryHandoffDelay > 0 || currentAltar != null && !currentAltar.isEmpty()
                    ? factoryPos.immutable() : null;
            factoryOwner = null;
            factoryOwnerLane = -1;
        }
        RunicAltarBlockEntity altar = altar();
        observedMana = altar == null ? Integer.MIN_VALUE : altar.getCurrentMana();
        observedAltarEmpty = altar == null || altar.isEmpty();
        setChanged();
        if (releasedFactory != null && pendingFactoryWake == null) releasedFactory.scheduleExternalWork();
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, RunicAltarInterfaceBlockEntity self) {
        if (!(level instanceof ServerLevel server)) return;
        self.tickFactoryHandoff();
        self.observeFactoryResource();
        self.refreshGridConnections();
        BotaniaGridSupport.flushOutputs(self.mainNode, self.inventory, 16, 25);
        if (!self.mainNode.isActive()) return;
        if (self.recipeId != null) self.tryFinish(server);
        else if (self.dirty) { self.dirty = false; self.tryStart(server); }
    }

    private boolean tryStart(ServerLevel level) {
        RunicAltarBlockEntity altar = altar();
        if (altar == null || !altar.isEmpty() || recipeId != null) return false;
        Match match = findMatch(level);
        if (match == null || !canQueue(match.output)) return false;
        List<ItemStack> ingredients = new ArrayList<>();
        for (int slot : match.ingredientSlots) {
            ItemStack one = inventory.extractItem(slot, 1, false);
            if (one.isEmpty()) {
                restore(ingredients);
                return false;
            }
            ingredients.add(one.copyWithCount(1));
        }
        reservedReagent = inventory.extractItem(match.reagentSlot, 1, false);
        if (reservedReagent.isEmpty()) {
            restore(ingredients);
            return false;
        }
        for (int index = 0; index < ingredients.size(); index++) {
            ItemStack one = ingredients.get(index).copy();
            if (!altar.addItem(null, one, null)) {
                restore(one);
                for (int remaining = index + 1; remaining < ingredients.size(); remaining++) {
                    restore(ingredients.get(remaining));
                }
                restore(reservedReagent);
                reservedReagent = ItemStack.EMPTY;
                return false;
            }
        }
        recipeId = match.recipe.id();
        expectedOutput = match.output.copy();
        setChanged();
        return true;
    }

    private boolean tryFinish(ServerLevel level) {
        RunicAltarBlockEntity altar = altar();
        RecipeHolder<RunicAltarRecipe> recipe = resolveRecipe(level);
        if (altar == null || recipe == null || reservedReagent.isEmpty()) return false;
        if (!recipe.value().matches(altar.getRecipeInput(), level)) {
            restore(reservedReagent); clearPlan(); return false;
        }
        if (altar.getTargetMana() <= 0 || !altar.isFull() || !canQueue(expectedOutput)) return false;
        AABB box = new AABB(altar.getBlockPos()).inflate(0.25, 1.0, 0.25);
        Set<UUID> before = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) before.add(entity.getUUID());
        ItemEntity reagent = new ItemEntity(level, altar.getBlockPos().getX() + 0.5, altar.getBlockPos().getY() + 0.5, altar.getBlockPos().getZ() + 0.5, reservedReagent.copy());
        level.addFreshEntity(reagent);
        altar.onUsedByWand(null, ItemStack.EMPTY, Direction.UP);
        if (!altar.isEmpty()) {
            if (reagent.isAlive()) { reservedReagent = reagent.getItem().copy(); reagent.discard(); }
            return false;
        }
        reservedReagent = ItemStack.EMPTY;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (before.contains(entity.getUUID()) || !entity.isAlive()) continue;
            ItemStack stack = entity.getItem().copy();
            if (queue(stack)) entity.discard();
        }
        clearPlan();
        return true;
    }

    @Nullable private Match findMatch(ServerLevel level) {
        return findMatch(level, inventory);
    }

    @Nullable Match findFactoryMatch(ServerLevel level, ItemStackHandler inputs) {
        return findMatch(level, inputs);
    }

    @Nullable private Match findMatch(ServerLevel level, ItemStackHandler source) {
        for (RecipeHolder<RunicAltarRecipe> holder : level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.RUNE_TYPE)) {
            List<Ingredient> ingredients = holder.value().getIngredients();
            int[] counts = new int[16];
            for (int slot = 0; slot < 16; slot++) counts[slot] = source.getStackInSlot(slot).getCount();
            int[] selected = new int[ingredients.size()];
            if (!assign(ingredients, 0, counts, selected, source)) continue;
            int reagentSlot = take(holder.value().getReagent(), counts, source);
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
    private boolean assign(List<Ingredient> ingredients, int index, int[] counts, int[] selected,
                           ItemStackHandler source) {
        if (index == ingredients.size()) return true;
        for (int slot = 0; slot < 16; slot++) {
            if (counts[slot] <= 0 || !ingredients.get(index).test(source.getStackInSlot(slot))) continue;
            counts[slot]--; selected[index] = slot;
            if (assign(ingredients, index + 1, counts, selected, source)) return true;
            counts[slot]++;
        }
        return false;
    }
    private int take(Ingredient ingredient, int[] counts, ItemStackHandler source) {
        for (int slot = 0; slot < 16; slot++) if (counts[slot] > 0 && ingredient.test(source.getStackInSlot(slot))) { counts[slot]--; return slot; }
        return -1;
    }

    boolean beginFactoryRecipe(BlockPos factoryPos, int lane, ItemStackHandler inputs, CompoundTag state) {
        if (!ownsFactoryLane(factoryPos, lane) || !state.isEmpty() || !(level instanceof ServerLevel server)) return false;
        RunicAltarBlockEntity altar = altar();
        Match match = findFactoryMatch(server, inputs);
        if (altar == null || !altar.isEmpty() || match == null || match.output.isEmpty()) return false;
        var ingredients = new ArrayList<ItemStack>();
        for (int slot : match.ingredientSlots) {
            ItemStack one = inputs.extractItem(slot, 1, false);
            if (one.isEmpty()) return false;
            ingredients.add(one.copyWithCount(1));
        }
        ItemStack reagent = inputs.extractItem(match.reagentSlot, 1, false);
        if (reagent.isEmpty()) return false;
        for (ItemStack ingredient : ingredients) {
            if (altar.addItem(null, ingredient.copy(), null)) continue;
            altar.clearContent();
            return false;
        }
        state.putString("recipe", match.recipe.id().toString());
        state.put("reagent", reagent.save(server.registryAccess()));
        state.put("output", match.output.save(server.registryAccess()));
        observedMana = altar.getCurrentMana();
        setChanged();
        return true;
    }

    @Nullable ItemStack factoryExpectedOutput(CompoundTag state, HolderLookup.Provider registries) {
        ItemStack output = ItemStack.parseOptional(registries, state.getCompound("output"));
        return output.isEmpty() ? null : output;
    }

    boolean factoryRecipeReady(BlockPos factoryPos, int lane, CompoundTag state) {
        if (!ownsFactoryLane(factoryPos, lane) || !(level instanceof ServerLevel server)) return false;
        RunicAltarBlockEntity altar = altar();
        RecipeHolder<RunicAltarRecipe> recipe = resolveFactoryRecipe(server, state);
        return altar != null && recipe != null && recipe.value().matches(altar.getRecipeInput(), server)
                && altar.getTargetMana() > 0 && altar.isFull();
    }

    @Nullable ItemStack finishFactoryRecipe(BlockPos factoryPos, int lane, CompoundTag state) {
        if (!factoryRecipeReady(factoryPos, lane, state) || !(level instanceof ServerLevel server)) return null;
        RunicAltarBlockEntity altar = altar();
        ItemStack reagentStack = ItemStack.parseOptional(server.registryAccess(), state.getCompound("reagent"));
        ItemStack expected = factoryExpectedOutput(state, server.registryAccess());
        if (altar == null || reagentStack.isEmpty() || expected == null) return null;
        AABB box = new AABB(altar.getBlockPos()).inflate(0.25, 1.0, 0.25);
        Set<UUID> before = new HashSet<>();
        for (ItemEntity entity : server.getEntitiesOfClass(ItemEntity.class, box)) before.add(entity.getUUID());
        ItemEntity reagent = new ItemEntity(server, altar.getBlockPos().getX() + 0.5,
                altar.getBlockPos().getY() + 0.5, altar.getBlockPos().getZ() + 0.5, reagentStack.copy());
        server.addFreshEntity(reagent);
        altar.onUsedByWand(null, ItemStack.EMPTY, Direction.UP);
        if (!altar.isEmpty()) {
            if (reagent.isAlive()) reagent.discard();
            return null;
        }
        for (ItemEntity entity : server.getEntitiesOfClass(ItemEntity.class, box)) {
            if (before.contains(entity.getUUID()) || !entity.isAlive()) continue;
            ItemStack output = entity.getItem();
            if (!ItemStack.isSameItemSameComponents(output, expected)
                    || output.getCount() != expected.getCount()) continue;
            ItemStack owned = output.copy();
            entity.discard();
            observedMana = altar.getCurrentMana();
            // Botania rejects new altar inputs for 60 ticks after native completion.
            // Delay a few extra ticks so the altar's own ticker has definitely cleared the cooldown.
            factoryHandoffDelay = 65;
            requestFactoryLane(factoryPos, lane, false);
            return owned;
        }
        return null;
    }

    @Nullable private RecipeHolder<RunicAltarRecipe> resolveFactoryRecipe(ServerLevel level, CompoundTag state) {
        ResourceLocation id = ResourceLocation.tryParse(state.getString("recipe"));
        if (id == null) return null;
        var holder = level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null || !(holder.value() instanceof RunicAltarRecipe)) return null;
        @SuppressWarnings("unchecked") RecipeHolder<RunicAltarRecipe> cast =
                (RecipeHolder<RunicAltarRecipe>) (RecipeHolder<?>) holder;
        return cast;
    }
    @Nullable private RecipeHolder<RunicAltarRecipe> resolveRecipe(ServerLevel level) {
        if (recipeId == null) return null;
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof RunicAltarRecipe recipe)) return null;
        @SuppressWarnings("unchecked") RecipeHolder<RunicAltarRecipe> cast = (RecipeHolder<RunicAltarRecipe>) (RecipeHolder<?>) holder;
        return cast;
    }
    @Nullable private RunicAltarBlockEntity altar() {
        if (level == null) return null;
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return level.getBlockEntity(worldPosition.relative(facing)) instanceof RunicAltarBlockEntity altar ? altar : null;
    }
    private void restore(List<ItemStack> stacks) { for (ItemStack stack : stacks) restore(stack); }
    private void restore(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int slot = 0; slot < 16; slot++) { stack = inventory.insertItem(slot, stack, false); if (stack.isEmpty()) return; }
    }
    private boolean canQueue(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 16; slot < 25 && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) remaining.shrink(Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount()));
        }
        return remaining.isEmpty();
    }
    private boolean queue(ItemStack stack) {
        if (!canQueue(stack)) return false;
        ItemStack remaining = stack.copy();
        for (int slot = 16; slot < 25 && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) { inventory.setStackInSlot(slot, remaining); return true; }
            if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) {
                int moved = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                ItemStack grown = current.copy(); grown.grow(moved); inventory.setStackInSlot(slot, grown); remaining.shrink(moved);
            }
        }
        return remaining.isEmpty();
    }
    private void clearPlan() { recipeId = null; reservedReagent = ItemStack.EMPTY; expectedOutput = ItemStack.EMPTY; dirty = true; setChanged(); }

    private boolean portInventoryEmpty() {
        if (recipeId != null || !reservedReagent.isEmpty() || !expectedOutput.isEmpty()) return false;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private boolean isAdjacentFactory(BlockPos factoryPos) {
        return level != null && worldPosition.distManhattan(factoryPos) == 1
                && level.getBlockEntity(factoryPos) instanceof HeterogeneousFactoryBlockEntity;
    }

    private void tickFactoryHandoff() {
        if (factoryHandoffDelay <= 0 || --factoryHandoffDelay > 0 || pendingFactoryWake == null || level == null) return;
        if (level.getBlockEntity(pendingFactoryWake) instanceof HeterogeneousFactoryBlockEntity factory)
            factory.scheduleExternalWork();
        pendingFactoryWake = null;
    }

    private void observeFactoryResource() {
        if ((factoryOwner == null && pendingFactoryWake == null) || level == null || level.isClientSide) return;
        RunicAltarBlockEntity altar = altar();
        int mana = altar == null ? Integer.MIN_VALUE : altar.getCurrentMana();
        boolean altarEmpty = altar == null || altar.isEmpty();
        if (mana != observedMana) {
            observedMana = mana;
            wakeFactory();
        }
        if (altarEmpty != observedAltarEmpty) {
            observedAltarEmpty = altarEmpty;
            wakeFactory();
        }
        if (pendingFactoryWake != null && factoryHandoffDelay <= 0 && altarEmpty) {
            if (level.getBlockEntity(pendingFactoryWake) instanceof HeterogeneousFactoryBlockEntity factory)
                factory.scheduleExternalWork();
            pendingFactoryWake = null;
        }
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
        pendingFactoryWake = null;
        factoryOwnerLane = -1;
        factoryHandoffDelay = 0;
        super.setRemoved();
        mainNode.destroy();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.put("inventory", inventory.serializeNBT(registries));
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        if (!reservedReagent.isEmpty()) tag.put("reagent", reservedReagent.save(registries));
        if (!expectedOutput.isEmpty()) tag.put("output", expectedOutput.save(registries));
        mainNode.saveToNBT(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        recipeId = tag.contains("recipe") ? ResourceLocation.parse(tag.getString("recipe")) : null;
        reservedReagent = ItemStack.parseOptional(registries, tag.getCompound("reagent"));
        expectedOutput = ItemStack.parseOptional(registries, tag.getCompound("output"));
        mainNode.loadFromNBT(tag);
        Arrays.fill(factoryLanes, false);
        factoryOwner = null;
        pendingFactoryWake = null;
        factoryOwnerLane = -1;
        factoryHandoffDelay = 0;
        observedMana = Integer.MIN_VALUE;
        observedAltarEmpty = true;
        dirty = true;
    }
    @Override public IGridNode getGridNode(Direction direction) { return mainNode.isReady() ? mainNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }

    record Match(RecipeHolder<RunicAltarRecipe> recipe, int[] ingredientSlots, int reagentSlot, ItemStack output) {}
    private record ConcreteRecipeInput(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public int size() { return items.size(); }
    }
}
