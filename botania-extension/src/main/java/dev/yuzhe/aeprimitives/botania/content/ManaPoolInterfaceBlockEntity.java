package dev.yuzhe.aeprimitives.botania.content;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import java.util.EnumSet;
import java.util.HashSet;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

public final class ManaPoolInterfaceBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost {
    private static final IGridNodeListener<ManaPoolInterfaceBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(ManaPoolInterfaceBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
    private final ItemStackHandler inventory = new ItemStackHandler(10) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0; }
        @Override protected void onContentsChanged(int slot) { dirty = true; setChanged(); }
    };
    private boolean dirty = true;
    @Nullable private ResourceLocation recipeId;
    private boolean gridTopologyDirty = true;
    private int gridBootstrapTicks = 20;

    public ManaPoolInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(BotaniaContent.MANA_POOL_INTERFACE_ENTITY.get(), pos, state);
    }

    public ItemStackHandler inventory() { return inventory; }
    public void markDirty() { dirty = true; gridTopologyDirty = true; }
    public boolean hasPlanForTest() { return recipeId != null; }
    public boolean planForTest(ServerLevel level) { return tryPlan(level); }
    public boolean executeForTest(ServerLevel level) { return tryExecute(level); }

    private void refreshGridConnections() {
        var state = BotaniaGridSupport.refreshConnections(level, worldPosition, mainNode, gridTopologyDirty, gridBootstrapTicks);
        gridTopologyDirty = state.dirty();
        gridBootstrapTicks = state.bootstrapTicks();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaPoolInterfaceBlockEntity self) {
        if (!(level instanceof ServerLevel server)) return;
        self.refreshGridConnections();
        BotaniaGridSupport.flushOutputs(self.mainNode, self.inventory, 1, 10);
        if (!self.mainNode.isActive()) return;
        if (self.recipeId != null) self.tryExecute(server);
        else if (self.dirty) {
            self.dirty = false;
            self.tryPlan(server);
        }
    }

    private boolean tryPlan(ServerLevel level) {
        ManaPoolBlockEntity pool = pool();
        ItemStack input = inventory.getStackInSlot(0);
        if (pool == null || input.isEmpty() || recipeId != null) return false;
        ItemStack one = input.copyWithCount(1);
        RecipeHolder<ManaInfusionRecipe> recipe = pool.getMatchingRecipe(one, level.getBlockState(pool.getBlockPos().below()));
        if (recipe == null) return false;
        ItemStack output = recipe.value().getRecipeOutput(level.registryAccess(), one);
        if (output.isEmpty() || !canQueue(output)) return false;
        recipeId = recipe.id();
        setChanged();
        return true;
    }

    private boolean tryExecute(ServerLevel level) {
        ManaPoolBlockEntity pool = pool();
        ItemStack input = inventory.getStackInSlot(0);
        if (pool == null || input.isEmpty() || recipeId == null) {
            clearPlan();
            return false;
        }
        ItemStack one = input.copyWithCount(1);
        RecipeHolder<ManaInfusionRecipe> selected = pool.getMatchingRecipe(one, level.getBlockState(pool.getBlockPos().below()));
        if (selected == null || !selected.id().equals(recipeId)) {
            clearPlan();
            return false;
        }
        ItemStack expected = selected.value().getRecipeOutput(level.registryAccess(), one);
        if (expected.isEmpty() || !canQueue(expected) || pool.getCurrentMana() < selected.value().getManaToConsume()) return false;

        ItemStack extracted = inventory.extractItem(0, 1, false);
        if (extracted.isEmpty()) {
            clearPlan();
            return false;
        }
        BlockPos poolPos = pool.getBlockPos();
        AABB capture = new AABB(poolPos).inflate(0.25, 1.25, 0.25);
        Set<UUID> before = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, capture)) before.add(entity.getUUID());
        ItemEntity nativeInput = new ItemEntity(level, poolPos.getX() + 0.5, poolPos.getY() + 0.5, poolPos.getZ() + 0.5, extracted);
        level.addFreshEntity(nativeInput);
        boolean crafted = pool.collideEntityItem(nativeInput);
        if (!crafted) {
            if (nativeInput.isAlive()) nativeInput.discard();
            restore(extracted);
            return false;
        }

        boolean recovered = false;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, capture)) {
            if (before.contains(entity.getUUID()) || !entity.isAlive()) continue;
            ItemStack stack = entity.getItem();
            if (!ItemStack.isSameItemSameComponents(stack, expected) || stack.getCount() != expected.getCount()) continue;
            if (queue(stack.copy())) {
                entity.discard();
                recovered = true;
                break;
            }
        }
        clearPlan();
        return recovered;
    }

    @Nullable
    private ManaPoolBlockEntity pool() {
        if (level == null) return null;
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return level.getBlockEntity(worldPosition.relative(facing)) instanceof ManaPoolBlockEntity pool ? pool : null;
    }

    private void restore(ItemStack stack) {
        if (!stack.isEmpty()) inventory.insertItem(0, stack, false);
    }

    private boolean canQueue(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 1; slot < 10 && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) {
                remaining.shrink(Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount()));
            }
        }
        return remaining.isEmpty();
    }

    private boolean queue(ItemStack stack) {
        if (!canQueue(stack)) return false;
        ItemStack remaining = stack.copy();
        for (int slot = 1; slot < 10 && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) {
                inventory.setStackInSlot(slot, remaining);
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) {
                int moved = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                ItemStack grown = current.copy();
                grown.grow(moved);
                inventory.setStackInSlot(slot, grown);
                remaining.shrink(moved);
            }
        }
        return remaining.isEmpty();
    }

    private void clearPlan() {
        recipeId = null;
        dirty = true;
        setChanged();
    }

    @Override public void onLoad() {
        super.onLoad();
        gridTopologyDirty = true;
        gridBootstrapTicks = 20;
        if (!level.isClientSide) mainNode.create(level, worldPosition);
    }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        mainNode.saveToNBT(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        recipeId = tag.contains("recipe") ? ResourceLocation.parse(tag.getString("recipe")) : null;
        mainNode.loadFromNBT(tag);
        dirty = true;
    }
    @Override public IGridNode getGridNode(Direction direction) { return mainNode.isReady() ? mainNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }
}
