package dev.yuzhe.aeprimitives.botania.content;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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


public final class PetalApothecaryInterfaceBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost {
    private static final IGridNodeListener<PetalApothecaryInterfaceBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(PetalApothecaryInterfaceBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL).setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
    private final ItemStackHandler inventory = new ItemStackHandler(25) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 16; }
        @Override protected void onContentsChanged(int slot) { dirty = true; setChanged(); }
    };
    private boolean dirty = true;

    public PetalApothecaryInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(BotaniaContent.PETAL_APOTHECARY_INTERFACE_ENTITY.get(), pos, state);
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, PetalApothecaryInterfaceBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !be.dirty || !be.mainNode.isActive()) return;
        be.dirty = false;
        be.tryCraft(server);
    }
    public ItemStackHandler inventory() { return inventory; }
    public void markDirty() { dirty = true; }
    public boolean craftForTest(ServerLevel level) { return tryCraft(level); }

    private boolean tryCraft(ServerLevel level) {
        PetalApothecaryBlockEntity apothecary = boundApothecary();
        if (apothecary == null || apothecary.getFluid() != PetalApothecary.State.WATER || !apothecary.isEmpty()) return false;
        Match match = findMatch(level);
        if (match == null || !canQueue(match.output)) return false;
        BlockPos altarPos = apothecary.getBlockPos();
        AABB outputArea = new AABB(altarPos.above());
        Set<UUID> existing = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, outputArea)) existing.add(entity.getUUID());
        for (int slot : match.ingredientSlots) {
            if (!feed(level, apothecary, slot)) return false;
        }
        if (!match.recipe.value().matches(apothecary.getRecipeInput(), level)) return false;
        if (!feed(level, apothecary, match.reagentSlot)) return false;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, outputArea)) {
            if (!existing.contains(entity.getUUID()) && ItemStack.isSameItemSameComponents(entity.getItem(), match.output)
                    && entity.getItem().getCount() == match.output.getCount()) {
                ItemStack result = entity.getItem().copy();
                entity.discard();
                queue(result);
                setChanged();
                return true;
            }
        }
        return false;
    }

    private boolean feed(ServerLevel level, PetalApothecaryBlockEntity apothecary, int slot) {
        ItemStack stack = inventory.extractItem(slot, 1, false);
        if (stack.isEmpty()) return false;
        BlockPos pos = apothecary.getBlockPos();
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5, stack);
        if (apothecary.collideEntityItem(entity)) return true;
        ItemStack current = inventory.getStackInSlot(slot);
        ItemStack restored = stack.copy();
        if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, restored)) restored.grow(current.getCount());
        inventory.setStackInSlot(slot, restored);
        return false;
    }

    @Nullable private Match findMatch(ServerLevel level) {
        for (RecipeHolder<PetalApothecaryRecipe> holder : level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.PETAL_TYPE)) {
            var ingredients = holder.value().getIngredients();
            int[] counts = new int[16];
            for (int slot = 0; slot < 16; slot++) counts[slot] = inventory.getStackInSlot(slot).getCount();
            int[] selected = new int[ingredients.size()];
            if (!assign(ingredients, 0, counts, selected)) continue;
            int reagentSlot = findSlot(holder.value().getReagent(), counts);
            if (reagentSlot < 0) continue;
            List<ItemStack> concrete = new ArrayList<>();
            for (int slot : selected) concrete.add(inventory.getStackInSlot(slot).copyWithCount(1));
            RecipeInput input = new ConcreteRecipeInput(concrete);
            if (!holder.value().matches(input, level)) continue;
            ItemStack output = holder.value().assemble(input, level.registryAccess());
            return new Match(holder, selected, reagentSlot, output);
        }
        return null;
    }
    private boolean assign(List<Ingredient> ingredients, int at, int[] counts, int[] selected) {
        if (at == ingredients.size()) return true;
        for (int slot = 0; slot < counts.length; slot++) {
            if (counts[slot] <= 0 || !ingredients.get(at).test(inventory.getStackInSlot(slot))) continue;
            counts[slot]--;
            selected[at] = slot;
            if (assign(ingredients, at + 1, counts, selected)) return true;
            counts[slot]++;
        }
        return false;
    }
    private int findSlot(Ingredient ingredient, int[] counts) {
        for (int slot = 0; slot < counts.length; slot++) if (counts[slot] > 0 && ingredient.test(inventory.getStackInSlot(slot))) return slot;
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
                    ItemStack grown = current.copy(); grown.grow(moved); inventory.setStackInSlot(slot, grown); stack.shrink(moved);
                }
            }
        }
    }

    @Override public void onLoad() { super.onLoad(); if (!level.isClientSide) mainNode.create(level, worldPosition); }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); mainNode.saveToNBT(tag); tag.put("inventory", inventory.serializeNBT(registries)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); mainNode.loadFromNBT(tag); if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory")); dirty = true; }
    @Override public IGridNode getGridNode(Direction direction) { return mainNode.isReady() ? mainNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }

    private record Match(RecipeHolder<PetalApothecaryRecipe> recipe, int[] ingredientSlots, int reagentSlot, ItemStack output) {}
    private record ConcreteRecipeInput(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public int size() { return items.size(); }
    }
}
