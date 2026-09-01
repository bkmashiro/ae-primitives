package dev.yuzhe.aeprimitives.farmersdelight.content;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import dev.yuzhe.aeprimitives.content.MachineTier;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelHost;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipeInput;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

public final class MeCuttingBoardBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost, SpatialParallelHost {
    static final int INPUT_SLOT = 0;
    static final int TOOL_SLOT = 1;
    private static final int OUTPUT_START = 2;
    private static final int OUTPUT_END = 18;
    private static final int WORK_TICKS = 20;
    private static final IGridNodeListener<MeCuttingBoardBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(MeCuttingBoardBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode gridNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL).setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(1.0);
    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_END) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < OUTPUT_START; }
        @Override protected void onContentsChanged(int slot) {
            if (slot < OUTPUT_START) recipeDirty = true;
            setChanged();
        }
    };
    private boolean recipeDirty = true;
    private boolean recipeAvailable;
    private boolean parallelTopologyDirty = true;
    private int parallelLanes = 1;
    private int workTicks;

    public MeCuttingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersDelightContent.CUTTING_BOARD_ENTITY.get(), pos, state);
    }
    public ItemStackHandler inventory() { return inventory; }
    public int parallelLanes() { refreshParallelTopology(); return parallelLanes; }
    @Override public MachineTier spatialParallelTier() { return MachineTier.BASIC; }
    @Override public int maxSpatialParallelLanes() { return 4; }
    @Override public void invalidateSpatialParallelism() { parallelTopologyDirty = true; setChanged(); }

    private void refreshParallelTopology() {
        if (!parallelTopologyDirty || level == null || level.isClientSide) return;
        int lanes = 1;
        for (var direction : Direction.values()) {
            var state = level.getBlockState(worldPosition.relative(direction));
            if (state.getBlock() instanceof SpatialParallelBlock sidecar
                    && state.getValue(SpatialParallelBlock.FACING) == direction.getOpposite()
                    && sidecar.tier() == MachineTier.BASIC) lanes += sidecar.addedLanes();
        }
        parallelLanes = Math.min(maxSpatialParallelLanes(), lanes);
        parallelTopologyDirty = false;
    }

    static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MeCuttingBoardBlockEntity machine) {
        var server = (ServerLevel) level;
        machine.flushOutputs();
        if (machine.recipeDirty) machine.refreshRecipe(server);
        if (!machine.gridNode.isActive() || !machine.recipeAvailable) { machine.workTicks = 0; return; }
        if (++machine.workTicks < WORK_TICKS) return;
        machine.workTicks = 0;
        machine.completeCycles(server, machine.parallelLanes());
    }

    private void refreshRecipe(ServerLevel level) {
        recipeAvailable = findRecipe(level) != null;
        recipeDirty = false;
    }

    int completeCycles(ServerLevel level, int laneLimit) {
        int completed = 0;
        for (int lane = 0; lane < laneLimit; lane++) {
            var recipe = findRecipe(level);
            if (recipe == null) break;
            var outputs = recipe.rollResults(level.random, 0);
            if (!canQueueAll(outputs)) break;
            inventory.extractItem(INPUT_SLOT, 1, false);
            damageTool(level);
            queueAll(outputs);
            completed++;
        }
        if (completed > 0) { recipeDirty = true; setChanged(); }
        return completed;
    }

    private CuttingBoardRecipe findRecipe(ServerLevel level) {
        var input = inventory.getStackInSlot(INPUT_SLOT);
        var tool = inventory.getStackInSlot(TOOL_SLOT);
        if (input.isEmpty() || tool.isEmpty()) return null;
        return level.getRecipeManager().getRecipeFor(ModRecipeTypes.CUTTING.get(),
                        new CuttingBoardRecipeInput(input.copyWithCount(1), tool), level)
                .map(holder -> holder.value()).orElse(null);
    }

    private void damageTool(ServerLevel level) {
        var tool = inventory.getStackInSlot(TOOL_SLOT);
        tool.hurtAndBreak(1, level, null, item -> {});
        inventory.setStackInSlot(TOOL_SLOT, tool);
    }

    private boolean canQueueAll(List<ItemStack> outputs) {
        var simulated = new ItemStack[16];
        for (int slot = 2; slot < 18; slot++) simulated[slot - 2] = inventory.getStackInSlot(slot).copy();
        for (var output : outputs) {
            var remainder = output.copy();
            for (int i = 0; i < simulated.length && !remainder.isEmpty(); i++) {
                var current = simulated[i];
                if (current.isEmpty()) {
                    int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                    simulated[i] = remainder.copyWithCount(moved);
                    remainder.shrink(moved);
                } else if (ItemStack.isSameItemSameComponents(current, remainder)) {
                    int moved = Math.min(remainder.getCount(), current.getMaxStackSize() - current.getCount());
                    current.grow(moved);
                    remainder.shrink(moved);
                }
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private void queueAll(List<ItemStack> outputs) {
        for (var output : outputs) {
            var remainder = output.copy();
            for (int slot = 2; slot < 18 && !remainder.isEmpty(); slot++) {
                var current = inventory.getStackInSlot(slot);
                if (current.isEmpty()) {
                    int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                    inventory.setStackInSlot(slot, remainder.copyWithCount(moved));
                    remainder.shrink(moved);
                } else if (ItemStack.isSameItemSameComponents(current, remainder)) {
                    int moved = Math.min(remainder.getCount(), current.getMaxStackSize() - current.getCount());
                    current.grow(moved);
                    remainder.shrink(moved);
                }
            }
        }
    }

    private void flushOutputs() {
        var grid = gridNode.getGrid();
        if (grid == null) return;
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            var stack = inventory.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(), key,
                    stack.getCount(), IActionSource.ofMachine(this), Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    @Override public IGridNode getGridNode(Direction direction) { return gridNode.isReady() ? gridNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return gridNode.getNode(); }
    @Override public void onLoad() {
        super.onLoad(); parallelTopologyDirty = true;
        if (level != null && !level.isClientSide && !gridNode.isReady()) {
            gridNode.setVisualRepresentation(getBlockState().getBlock());
            gridNode.create(level, worldPosition);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("workTicks", workTicks);
        var node = new CompoundTag(); gridNode.saveToNBT(node); tag.put("aeNode", node);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        workTicks = tag.getInt("workTicks");
        if (tag.contains("aeNode")) gridNode.loadFromNBT(tag.getCompound("aeNode"));
        recipeDirty = true; parallelTopologyDirty = true;
    }
    @Override public void setRemoved() { gridNode.destroy(); super.setRemoved(); }
}
