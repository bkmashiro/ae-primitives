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
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.recipe.PureDaisyRecipe;
import vazkii.botania.common.block.block_entity.flower.misc.PureDaisyBlockEntity;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

public final class PureDaisyInterfaceBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost {
    private static final BlockPos[] RING = {
            new BlockPos(-1, 0, -1), new BlockPos(-1, 0, 0), new BlockPos(-1, 0, 1), new BlockPos(0, 0, 1),
            new BlockPos(1, 0, 1), new BlockPos(1, 0, 0), new BlockPos(1, 0, -1), new BlockPos(0, 0, -1)
    };
    private static final IGridNodeListener<PureDaisyInterfaceBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(PureDaisyInterfaceBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL).setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
    private final ItemStackHandler inventory = new ItemStackHandler(10) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && stack.getItem() instanceof BlockItem; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final List<OwnedPosition> owned = new ArrayList<>();

    public PureDaisyInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(BotaniaContent.PURE_DAISY_INTERFACE_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PureDaisyInterfaceBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !be.mainNode.isActive()) return;
        be.recoverCompleted(server);
        be.dispatchAvailable(server);
    }

    public ItemStackHandler inventory() { return inventory; }
    public int ownedCountForTest() { return owned.size(); }
    public BlockPos ownedPositionForTest() { return owned.isEmpty() ? null : ringPosition(owned.getFirst().ringIndex); }
    public int ownedRecipeTimeForTest(ServerLevel level) {
        var recipe = owned.isEmpty() ? null : recipe(level, owned.getFirst().recipeId);
        return recipe == null ? 0 : recipe.getTime();
    }
    public void dispatchForTest(ServerLevel level) { dispatchAvailable(level); }
    public void recoverForTest(ServerLevel level) { recoverCompleted(level); }

    private void dispatchAvailable(ServerLevel level) {
        var flower = boundFlower();
        if (flower == null || inventory.getStackInSlot(0).isEmpty()) return;
        var source = inventory.getStackInSlot(0);
        if (!(source.getItem() instanceof BlockItem blockItem)) return;
        for (int index = 0; index < RING.length && !source.isEmpty(); index++) {
            if (isOwned(index)) continue;
            BlockPos target = flower.getEffectivePos().offset(RING[index]);
            if (!level.getBlockState(target).isAir()) continue;
            BlockState inputState = blockItem.getBlock().defaultBlockState();
            RecipeHolder<PureDaisyRecipe> holder = findRecipe(level, target, inputState);
            if (holder == null || !level.setBlock(target, inputState, Block.UPDATE_ALL)) continue;
            inventory.extractItem(0, 1, false);
            owned.add(new OwnedPosition(index, holder.id()));
            setChanged();
            source = inventory.getStackInSlot(0);
        }
    }

    private void recoverCompleted(ServerLevel level) {
        var flower = boundFlower();
        if (flower == null) return;
        Iterator<OwnedPosition> iterator = owned.iterator();
        while (iterator.hasNext()) {
            var plan = iterator.next();
            BlockPos target = flower.getEffectivePos().offset(RING[plan.ringIndex]);
            BlockState state = level.getBlockState(target);
            PureDaisyRecipe recipe = recipe(level, plan.recipeId);
            if (recipe == null || state.isAir()) {
                iterator.remove();
                setChanged();
                continue;
            }
            if (recipe.matches(level, target, state)) continue;
            if (!recipe.getOutput().test(state)) {
                iterator.remove();
                setChanged();
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target));
            if (drops.isEmpty() || !canQueueAll(drops)) continue;
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            drops.forEach(this::queue);
            iterator.remove();
            setChanged();
        }
    }

    @Nullable private PureDaisyBlockEntity boundFlower() {
        if (level == null) return null;
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockPos flowerPos = worldPosition.relative(facing, 2);
        if (!(level.getBlockEntity(flowerPos) instanceof PureDaisyBlockEntity flower)) return null;
        return flower.getEffectivePos().equals(flowerPos) ? flower : null;
    }

    @Nullable private RecipeHolder<PureDaisyRecipe> findRecipe(ServerLevel level, BlockPos pos, BlockState state) {
        for (var holder : level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.PURE_DAISY_TYPE)) {
            if (holder.value().matches(level, pos, state)) return holder;
        }
        return null;
    }

    @Nullable private PureDaisyRecipe recipe(ServerLevel level, ResourceLocation id) {
        var holder = level.getRecipeManager().byKey(id).orElse(null);
        return holder != null && holder.value() instanceof PureDaisyRecipe recipe ? recipe : null;
    }

    private BlockPos ringPosition(int index) {
        var flower = boundFlower();
        return flower == null ? null : flower.getEffectivePos().offset(RING[index]);
    }
    private boolean isOwned(int index) { return owned.stream().anyMatch(plan -> plan.ringIndex == index); }

    private boolean canQueueAll(List<ItemStack> stacks) {
        ItemStack[] slots = new ItemStack[9];
        for (int i = 0; i < slots.length; i++) slots[i] = inventory.getStackInSlot(i + 1).copy();
        for (ItemStack stack : stacks) if (!insert(slots, stack.copy())) return false;
        return true;
    }
    private void queue(ItemStack stack) {
        for (int slot = 1; slot < 10 && !stack.isEmpty(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) {
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
                inventory.setStackInSlot(slot, stack.copyWithCount(moved));
                stack.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                if (moved > 0) {
                    ItemStack grown = current.copy();
                    grown.grow(moved);
                    inventory.setStackInSlot(slot, grown);
                    stack.shrink(moved);
                }
            }
        }
    }
    private static boolean insert(ItemStack[] slots, ItemStack stack) {
        for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
            ItemStack current = slots[i];
            if (current.isEmpty()) {
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
                slots[i] = stack.copyWithCount(moved);
                stack.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                if (moved > 0) {
                    current.grow(moved);
                    stack.shrink(moved);
                }
            }
        }
        return stack.isEmpty();
    }

    @Override public void onLoad() { super.onLoad(); if (!level.isClientSide) mainNode.create(level, worldPosition); }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.put("inventory", inventory.serializeNBT(registries));
        ListTag list = new ListTag();
        for (var plan : owned) list.add(plan.save());
        tag.put("owned", list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        owned.clear();
        for (var entry : tag.getList("owned", 10)) owned.add(OwnedPosition.load((CompoundTag) entry));
    }
    @Override public IGridNode getGridNode(Direction direction) { return mainNode.isReady() ? mainNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }

    private record OwnedPosition(int ringIndex, ResourceLocation recipeId) {
        CompoundTag save() { var tag = new CompoundTag(); tag.putInt("ring", ringIndex); tag.putString("recipe", recipeId.toString()); return tag; }
        static OwnedPosition load(CompoundTag tag) { return new OwnedPosition(tag.getInt("ring"), ResourceLocation.parse(tag.getString("recipe"))); }
    }
}
