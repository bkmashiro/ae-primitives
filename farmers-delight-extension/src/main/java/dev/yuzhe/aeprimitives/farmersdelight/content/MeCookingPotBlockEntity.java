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
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

public final class MeCookingPotBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost,
        SpatialParallelHost, HeatableBlockEntity {
    static final int INPUT_START = 0;
    static final int INPUT_END = 6;
    static final int CONTAINER_SLOT = 6;
    static final int OUTPUT_START = 7;
    static final int OUTPUT_END = 18;
    private static final IGridNodeListener<MeCookingPotBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(MeCookingPotBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode gridNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
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
    private boolean gridTopologyDirty = true;
    private int gridBootstrapTicks = 20;
    private int parallelLanes = 1;
    private int workTicks;

    public MeCookingPotBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersDelightContent.COOKING_POT_ENTITY.get(), pos, state);
    }

    public ItemStackHandler inventory() { return inventory; }
    public int parallelLanes() { refreshParallelTopology(); return parallelLanes; }
    @Override public MachineTier spatialParallelTier() { return MachineTier.BASIC; }
    @Override public int maxSpatialParallelLanes() { return 4; }
    @Override public void invalidateSpatialParallelism() { parallelTopologyDirty = true; setChanged(); }
    void markGridTopologyDirty() { gridTopologyDirty = true; }

    private void refreshGridConnections() {
        if (level == null || level.isClientSide || !gridNode.isReady()
                || (!gridTopologyDirty && gridBootstrapTicks <= 0)) return;
        if (gridBootstrapTicks > 0) gridBootstrapTicks--;
        var node = gridNode.getNode();
        boolean pendingNeighbor = false;
        for (var direction : Direction.values()) {
            if (node.getInWorldConnections().containsKey(direction)) continue;
            var neighborPos = worldPosition.relative(direction);
            var host = GridHelper.getNodeHost(level, neighborPos);
            if (host == null) continue;
            var neighbor = GridHelper.getExposedNode(level, neighborPos, direction.getOpposite());
            if (neighbor == null) {
                pendingNeighbor = true;
            } else if (neighbor != node && node.getConnections().stream()
                    .noneMatch(connection -> connection.getOtherSide(node) == neighbor)) {
                GridHelper.createConnection(node, neighbor);
            }
        }
        gridTopologyDirty = pendingNeighbor;
    }

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

    static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MeCookingPotBlockEntity machine) {
        var server = (ServerLevel) level;
        machine.refreshGridConnections();
        machine.flushOutputs();
        if (machine.recipeDirty) machine.refreshRecipe(server);
        var recipe = machine.findRecipe(server);
        if (!machine.gridNode.isActive() || !machine.recipeAvailable || recipe == null || !machine.isHeated(level, pos)) {
            machine.workTicks = 0;
            return;
        }
        if (++machine.workTicks < recipe.getCookTime()) return;
        machine.workTicks = 0;
        machine.completeCycles(server, machine.parallelLanes());
    }

    private void refreshRecipe(ServerLevel level) {
        recipeAvailable = findRecipe(level) != null;
        recipeDirty = false;
    }

    int completeCycles(ServerLevel level, int laneLimit) {
        if (!isHeated(level, worldPosition)) return 0;
        int completed = 0;
        for (int lane = 0; lane < laneLimit; lane++) {
            var recipe = findRecipe(level);
            if (recipe == null) break;
            var outputs = collectOutputs(recipe, level);
            if (outputs == null || !canQueueAll(outputs)) break;
            for (int slot = INPUT_START; slot < INPUT_END; slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty()) inventory.extractItem(slot, 1, false);
            }
            if (!recipe.getOutputContainer().isEmpty()) inventory.extractItem(CONTAINER_SLOT, 1, false);
            queueAll(outputs);
            completed++;
        }
        if (completed > 0) { recipeDirty = true; setChanged(); }
        return completed;
    }

    private CookingPotRecipe findRecipe(ServerLevel level) {
        var inputs = recipeInputs();
        return level.getRecipeManager().getRecipeFor(ModRecipeTypes.COOKING.get(), new RecipeWrapper(inputs), level)
                .map(holder -> holder.value())
                .filter(recipe -> containerAvailable(recipe.getOutputContainer()))
                .orElse(null);
    }

    private ItemStackHandler recipeInputs() {
        var inputs = new ItemStackHandler(INPUT_END - INPUT_START);
        for (int slot = INPUT_START; slot < INPUT_END; slot++) {
            inputs.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        return inputs;
    }

    private boolean containerAvailable(ItemStack required) {
        if (required.isEmpty()) return true;
        var available = inventory.getStackInSlot(CONTAINER_SLOT);
        return available.getCount() >= required.getCount() && ItemStack.isSameItemSameComponents(available, required);
    }

    private List<ItemStack> collectOutputs(CookingPotRecipe recipe, ServerLevel level) {
        var output = recipe.assemble(new RecipeWrapper(recipeInputs()), level.registryAccess());
        if (output.isEmpty()) return null;
        var outputs = new ArrayList<ItemStack>();
        outputs.add(output);
        for (int slot = INPUT_START; slot < INPUT_END; slot++) {
            var input = inventory.getStackInSlot(slot);
            if (input.isEmpty()) continue;
            var remainder = input.getCraftingRemainingItem();
            if (remainder.isEmpty()) {
                var override = CookingPotBlockEntity.INGREDIENT_REMAINDER_OVERRIDES.get(input.getItem());
                if (override != null) remainder = new ItemStack(override);
            }
            if (!remainder.isEmpty()) outputs.add(remainder.copy());
        }
        return outputs;
    }

    private boolean canQueueAll(List<ItemStack> outputs) {
        var simulated = new ItemStack[OUTPUT_END - OUTPUT_START];
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            simulated[slot - OUTPUT_START] = inventory.getStackInSlot(slot).copy();
        }
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
            for (int slot = OUTPUT_START; slot < OUTPUT_END && !remainder.isEmpty(); slot++) {
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
        super.onLoad();
        parallelTopologyDirty = true;
        gridTopologyDirty = true;
        gridBootstrapTicks = 20;
        if (level != null && !level.isClientSide && !gridNode.isReady()) {
            gridNode.setVisualRepresentation(getBlockState().getBlock());
            gridNode.create(level, worldPosition);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("workTicks", workTicks);
        var node = new CompoundTag();
        gridNode.saveToNBT(node);
        tag.put("aeNode", node);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        workTicks = tag.getInt("workTicks");
        if (tag.contains("aeNode")) gridNode.loadFromNBT(tag.getCompound("aeNode"));
        recipeDirty = true;
        parallelTopologyDirty = true;
    }
    @Override public void setRemoved() { gridNode.destroy(); super.setRemoved(); }
}
