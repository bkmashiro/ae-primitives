package dev.yuzhe.aeprimitives.powah.content;


import appeng.api.networking.GridFlags;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.api.storage.StorageHelper;
import dev.yuzhe.aeprimitives.content.MachineTier;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelHost;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import owmii.powah.block.energizing.EnergizingRecipe;
import owmii.powah.recipe.Recipes;

public final class MeEnergizingChamberBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost, SpatialParallelHost {
    private static final int INPUTS = 6;
    private static final int OUTPUT_START = 6;

    private static final IGridNodeListener<MeEnergizingChamberBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(MeEnergizingChamberBlockEntity owner, IGridNode node) { owner.setChanged(); }
    };
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(4.0);
    private final ItemStackHandler inventory = new ItemStackHandler(18) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < INPUTS || slot == 17 && emitterRate(stack) > 0; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final EnergyStorage energy = new EnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE) {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            return super.receiveEnergy(Math.min(amount, totalEmitterRate()), simulate);
        }
    };
    private final List<Plan> plans = new ArrayList<>();
    private boolean topologyDirty = true;
    private boolean gridTopologyDirty = true;
    private int gridBootstrapTicks = 20;
    private int cachedLanes = 1;

    public MeEnergizingChamberBlockEntity(BlockPos pos, BlockState state) { super(PowahContent.ENERGIZING_CHAMBER_ENTITY.get(), pos, state); }
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MeEnergizingChamberBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.refreshGridConnections();
        be.flushOutputsToMe();
        if (!be.mainNode.isActive()) return;
        be.refreshTopology();
        be.startPlans(server);
        for (var plan : be.plans) {
            if (plan.complete()) continue;
            int wanted = (int) Math.min(be.emitterRatePerModule(), plan.energyRequired - plan.energyPaid);
            plan.energyPaid += be.energy.extractEnergy(wanted, false);
        }
        be.flushCompleted();
        be.setChanged();
    }
    public ItemStackHandler inventory() { return inventory; }
    public IEnergyStorage energy() { return energy; }
    public MachineTier spatialParallelTier() { return MachineTier.ADVANCED; }
    public int maxSpatialParallelLanes() { return 8; }
    public void invalidateSpatialParallelism() { topologyDirty = true; setChanged(); }
    public void markGridTopologyDirty() { gridTopologyDirty = true; }
    public int laneCountForTest() { refreshTopology(); return cachedLanes; }
    public int activePlansForTest() { return plans.size(); }
    public double totalRequiredFeForTest() { return plans.stream().mapToDouble(p -> p.energyRequired).sum(); }
    public double totalPaidFeForTest() { return plans.stream().mapToDouble(p -> p.energyPaid).sum(); }
    public void startPlansForTest(ServerLevel level) { refreshTopology(); startPlans(level); }
    public void runExternalEnergyTickForTest() {
        for (var plan : plans) {
            if (plan.complete()) continue;
            int wanted = (int) Math.min(emitterRatePerModule(), plan.energyRequired - plan.energyPaid);
            plan.energyPaid += energy.extractEnergy(wanted, false);
        }
        flushCompleted();
    }

    private void refreshTopology() {
        if (!topologyDirty || level == null || level.isClientSide) return;
        int lanes = 1;
        for (var direction : Direction.values()) {
            var state = level.getBlockState(worldPosition.relative(direction));
            if (state.getBlock() instanceof SpatialParallelBlock sidecar
                    && state.getValue(SpatialParallelBlock.FACING) == direction.getOpposite()
                    && sidecar.tier() == spatialParallelTier()) lanes += sidecar.addedLanes();
        }
        cachedLanes = Math.min(maxSpatialParallelLanes(), lanes);
        topologyDirty = false;
    }
    private void refreshGridConnections() {
        if (level == null || level.isClientSide || !mainNode.isReady()
                || (!gridTopologyDirty && gridBootstrapTicks <= 0)) return;
        if (gridBootstrapTicks > 0) gridBootstrapTicks--;
        var node = mainNode.getNode();
        boolean pendingNeighbor = false;
        for (var direction : Direction.values()) {
            if (node.getInWorldConnections().containsKey(direction)) continue;
            var host = GridHelper.getNodeHost(level, worldPosition.relative(direction));
            if (host == null) continue;
            var neighbor = GridHelper.getExposedNode(level, worldPosition.relative(direction), direction.getOpposite());
            if (neighbor == null) pendingNeighbor = true;
            else if (neighbor != node && node.getConnections().stream()
                    .noneMatch(connection -> connection.getOtherSide(node) == neighbor)) GridHelper.createConnection(node, neighbor);
        }
        gridTopologyDirty = pendingNeighbor;
    }
    private void flushOutputsToMe() {
        if (!mainNode.isActive()) return;
        var grid = mainNode.getGrid();
        for (int slot = OUTPUT_START; slot < 17; slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    key, stack.getCount(), IActionSource.empty(), Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }
    private void startPlans(ServerLevel level) {
        while (plans.size() < Math.min(cachedLanes, emitterCount())) {
            var recipe = level.getRecipeManager().getRecipeFor(Recipes.ENERGIZING.get(), new OrbInput(inventory), level).orElse(null);
            if (recipe == null) return;
            var value = recipe.value();
            var output = value.getResultItem(level.registryAccess()).copy();
            if (!canQueue(output)) return;
            for (int slot = 0; slot < INPUTS; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) inventory.extractItem(slot, 1, false);
            plans.add(new Plan(output, value.getScaledEnergy(), 0));
        }
    }
    private int emitterCount() { return emitterRatePerModule() == 0 ? 0 : inventory.getStackInSlot(17).getCount(); }
    private int emitterRatePerModule() { return emitterRate(inventory.getStackInSlot(17)); }
    private int totalEmitterRate() { return emitterRatePerModule() * emitterCount(); }
    private static int emitterRate(ItemStack stack) {
        if (stack.is(PowahContent.BASIC_EMITTER.get())) return 400;
        if (stack.is(PowahContent.NIOTIC_EMITTER.get())) return 10_000;
        if (stack.is(PowahContent.NITRO_EMITTER.get())) return 200_000;
        return 0;
    }
    private void flushCompleted() {
        Iterator<Plan> it = plans.iterator();
        while (it.hasNext()) {
            var plan = it.next();
            if (!plan.complete() || !canQueue(plan.output)) continue;
            queue(plan.output.copy());
            it.remove();
        }
    }
    private boolean canQueue(ItemStack output) {
        int remaining = output.getCount();
        for (int slot = OUTPUT_START; slot < 17; slot++) {
            var current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(current, output)) remaining -= current.getMaxStackSize() - current.getCount();
            if (remaining <= 0) return true;
        }
        return false;
    }
    private void queue(ItemStack output) {
        for (int slot = OUTPUT_START; slot < 17 && !output.isEmpty(); slot++) {
            var current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) {
                int moved = Math.min(output.getCount(), output.getMaxStackSize());
                inventory.setStackInSlot(slot, output.copyWithCount(moved));
                output.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, output)) {
                int moved = Math.min(output.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(moved);
                output.shrink(moved);
            }
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (!level.isClientSide) mainNode.create(level, worldPosition);
        topologyDirty = true;
        gridTopologyDirty = true;
        gridBootstrapTicks = 20;
    }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); mainNode.saveToNBT(tag); tag.put("inventory", inventory.serializeNBT(registries)); tag.put("energy", energy.serializeNBT(registries));
        var list = new ListTag(); for (var plan : plans) list.add(plan.save(registries)); tag.put("plans", list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); mainNode.loadFromNBT(tag);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        plans.clear(); for (var entry : tag.getList("plans", 10)) plans.add(Plan.load((CompoundTag) entry, registries)); topologyDirty = true;
    }
    public IGridNode getGridNode(Direction direction) { return mainNode.isReady() ? mainNode.getNode() : null; }
    public IGridNode getActionableNode() { return mainNode.getNode(); }
    public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }

    private record OrbInput(ItemStackHandler inventory) implements RecipeInput {
        public ItemStack getItem(int index) { return index == 0 ? ItemStack.EMPTY : inventory.getStackInSlot(index - 1).copy(); }
        public int size() { return INPUTS + 1; }
    }
    private static final class Plan {
        final ItemStack output; final double energyRequired; double energyPaid;
        Plan(ItemStack output, double required, double paid) { this.output = output; this.energyRequired = required; this.energyPaid = paid; }
        boolean complete() { return energyPaid + 1.0e-6 >= energyRequired; }
        CompoundTag save(HolderLookup.Provider registries) { var tag = new CompoundTag(); tag.put("output", output.saveOptional(registries)); tag.putDouble("required", energyRequired); tag.putDouble("paid", energyPaid); return tag; }
        static Plan load(CompoundTag tag, HolderLookup.Provider registries) { return new Plan(ItemStack.parseOptional(registries, tag.getCompound("output")), tag.getDouble("required"), tag.getDouble("paid")); }
    }
}
