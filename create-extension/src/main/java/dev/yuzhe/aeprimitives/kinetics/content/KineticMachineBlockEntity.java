package dev.yuzhe.aeprimitives.kinetics.content;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.yuzhe.aeprimitives.content.MachineTier;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelHost;
import dev.yuzhe.aeprimitives.kinetics.catalyst.CatalystDefinition;
import dev.yuzhe.aeprimitives.kinetics.catalyst.CatalystRegistry;
import dev.yuzhe.aeprimitives.kinetics.catalyst.CatalystVisual;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class KineticMachineBlockEntity extends KineticBlockEntity implements IInWorldGridNodeHost, IActionHost, SpatialParallelHost {
    public static final int BASIN_INPUT_SLOTS = 9;
    private static final int INVENTORY_SLOTS = 18;
    private static final float WORK_PER_RECIPE = 4096.0f;
    private static final float MIN_SPEED = 16.0f;
    private static final IGridNodeListener<KineticMachineBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override public void onSaveChanges(KineticMachineBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }
            };

    private final IManagedGridNode gridNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(2.0);
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return behavior().acceptsInput(slot);
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); sendData(); }
    };
    private final BasinFluidBuffer basinFluids = new BasinFluidBuffer(() -> { setChanged(); sendData(); });
    private float work;
    private ResourceLocation catalystId;
    private ItemStack catalystStack = ItemStack.EMPTY;
    private CatalystVisual catalystVisual = CatalystVisual.item();
    private boolean parallelTopologyDirty = true;
    private int parallelLanes = 1;
    private int activeLanes;

    public KineticMachineBlockEntity(BlockPos pos, BlockState state) {
        this(KineticsContent.MACHINE_ENTITY.get(), pos, state);
    }

    public KineticMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public KineticMachineKind kind() {
        return ((KineticMachineBlock) getBlockState().getBlock()).kind();
    }

    private KineticProcessBehavior behavior() {
        return KineticProcessBehavior.forKind(kind());
    }

    public ItemStackHandler inventory() { return inventory; }
    public IFluidHandler fluids() { return basinFluids; }
    public net.neoforged.neoforge.fluids.FluidStack basinFluidVisual() { return basinFluids.firstInput(); }
    BasinFluidBuffer basinFluids() { return basinFluids; }
    public float workFraction() { return Math.min(1.0f, work / WORK_PER_RECIPE); }
    public Optional<ResourceLocation> catalystId() { return Optional.ofNullable(catalystId); }
    public ItemStack catalystStack() { return catalystStack.copy(); }
    public CatalystVisual catalystVisual() { return catalystVisual; }
    public int parallelLanes() {
        refreshParallelTopology();
        return parallelLanes;
    }
    public int activeLanes() { return activeLanes; }

    @Override public MachineTier spatialParallelTier() { return kind().tier(); }
    @Override public int maxSpatialParallelLanes() { return kind().maxParallelLanes(); }
    @Override public void invalidateSpatialParallelism() {
        parallelTopologyDirty = true;
        setChanged();
    }

    private void refreshParallelTopology() {
        if (!parallelTopologyDirty || level == null || level.isClientSide()) return;
        int lanes = 1;
        for (var direction : Direction.values()) {
            var state = level.getBlockState(worldPosition.relative(direction));
            if (state.getBlock() instanceof SpatialParallelBlock sidecar
                    && state.getValue(SpatialParallelBlock.FACING) == direction.getOpposite()
                    && sidecar.tier() == spatialParallelTier()) {
                lanes += sidecar.addedLanes();
            }
        }
        int updated = Math.min(maxSpatialParallelLanes(), lanes);
        parallelTopologyDirty = false;
        if (updated != parallelLanes) {
            parallelLanes = updated;
            setChanged();
            sendData();
        }
    }

    public Optional<ItemStack> installCatalyst(ItemStack offered) {
        if (kind() != KineticMachineKind.FAN || catalystId != null) return Optional.empty();
        var definition = CatalystRegistry.find(offered).orElse(null);
        if (definition == null) return Optional.empty();
        catalystId = definition.id();
        catalystStack = offered.copyWithCount(1);
        catalystVisual = definition.display();
        work = 0;
        setChanged();
        sendData();
        return Optional.of(definition.installRemainder()
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY));
    }

    public Optional<ItemStack> removeCatalyst() {
        if (kind() != KineticMachineKind.FAN || catalystId == null) return Optional.empty();
        var returned = CatalystRegistry.get(catalystId)
                .flatMap(CatalystDefinition::removalResult)
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(ItemStack::new)
                .orElseGet(() -> catalystStack.copy());
        catalystId = null;
        catalystStack = ItemStack.EMPTY;
        catalystVisual = CatalystVisual.item();
        work = 0;
        setChanged();
        sendData();
        return Optional.of(returned);
    }

    public boolean canRun() {
        return gridNode.isActive() && !isOverStressed() && Math.abs(getSpeed()) >= MIN_SPEED;
    }

    @Override public void initialize() {
        super.initialize();
        parallelTopologyDirty = true;
        if (level != null && !level.isClientSide && !gridNode.isReady()) {
            gridNode.setVisualRepresentation(getBlockState().getBlock());
            gridNode.create(level, worldPosition);
        }
    }

    @Override public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server)) return;
        flushOutputs();
        flushFluidOutputs();
        var behavior = behavior();
        int requestedLanes = behavior.requestedLanes(this, server, parallelLanes());
        updateActiveLanes(requestedLanes);
        if (!canRun()) return;
        if (requestedLanes == 0) { work = 0; return; }
        work += Math.abs(getSpeed());
        if (work < WORK_PER_RECIPE) return;
        if (behavior.completeCycles(this, server, requestedLanes) > 0) {
            work = 0;
            setChanged();
            sendData();
        }
    }

    boolean completeCycle(ServerLevel server) {
        return completeCycles(server, 1) == 1;
    }

    int completeCycles(ServerLevel server, int laneLimit) {
        int completed = behavior().completeCycles(this, server, laneLimit);
        if (completed > 0) setChanged();
        return completed;
    }

    private void updateActiveLanes(int lanes) {
        if (lanes == activeLanes) return;
        activeLanes = lanes;
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        setChanged();
        sendData();
    }


    Optional<CatalystDefinition> catalystDefinition() {
        return catalystId == null ? Optional.empty() : CatalystRegistry.get(catalystId);
    }

    private int outputStart() { return behavior().outputStart(); }
    private int outputEnd() { return behavior().outputEnd(); }

    private void flushOutputs() {
        var grid = gridNode.getGrid();
        if (grid == null) return;
        for (int slot = outputStart(); slot < outputEnd(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(
                    grid.getEnergyService(), grid.getStorageService().getInventory(), key,
                    stack.getCount(), IActionSource.ofMachine(this), Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    private void flushFluidOutputs() {
        if (!behavior().supportsFluids()) return;
        var grid = gridNode.getGrid();
        if (grid == null) return;
        for (int tank = 0; tank < BasinFluidBuffer.TANKS; tank++) {
            var stack = basinFluids.getFluidInTank(BasinFluidBuffer.TANKS + tank);
            var key = AEFluidKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(
                    grid.getEnergyService(), grid.getStorageService().getInventory(), key,
                    stack.getAmount(), IActionSource.ofMachine(this), Actionable.MODULATE);
            if (inserted > 0) basinFluids.drain(stack.copyWithAmount((int) inserted), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    boolean canQueueAll(List<ItemStack> outputs) {
        var copy = new ItemStackHandler(outputEnd() - outputStart());
        for (int i = 0; i < copy.getSlots(); i++) copy.setStackInSlot(i, inventory.getStackInSlot(i + outputStart()).copy());
        for (var output : outputs) {
            var rest = output.copy();
            for (int slot = 0; slot < copy.getSlots() && !rest.isEmpty(); slot++) rest = copy.insertItem(slot, rest, false);
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    void queueAll(List<ItemStack> outputs) {
        for (var output : outputs) {
            var rest = output.copy();
            for (int slot = outputStart(); slot < outputEnd() && !rest.isEmpty(); slot++) {
                var existing = inventory.getStackInSlot(slot);
                if (existing.isEmpty()) {
                    inventory.setStackInSlot(slot, rest);
                    rest = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(existing, rest)) {
                    int moved = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
                    if (moved > 0) {
                        var merged = existing.copy();
                        merged.grow(moved);
                        inventory.setStackInSlot(slot, merged);
                        rest.shrink(moved);
                    }
                }
            }
        }
    }

    @Override public float calculateStressApplied() { return kind().stressImpact() * Math.max(1, activeLanes); }
    @Override public IGridNode getGridNode(Direction direction) { return gridNode.isReady() ? gridNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return gridNode.getNode(); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putFloat("work", work);
        tag.putInt("parallelLanes", parallelLanes);
        tag.putInt("activeLanes", activeLanes);
        var fluidsTag = new CompoundTag();
        basinFluids.write(fluidsTag, registries);
        tag.put("basinFluids", fluidsTag);
        if (catalystId != null) {
            tag.putString("catalystId", catalystId.toString());
            tag.put("catalystStack", catalystStack.saveOptional(registries));
            tag.putString("catalystVisualKind", catalystVisual.kind().name());
            catalystVisual.resource().ifPresent(id -> tag.putString("catalystVisualResource", id.toString()));
            catalystVisual.tint().ifPresent(color -> tag.putInt("catalystVisualColor", color));
        }
        var nodeTag = new CompoundTag();
        gridNode.saveToNBT(nodeTag);
        tag.put("aeNode", nodeTag);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        work = tag.getFloat("work");
        parallelLanes = Math.max(1, tag.getInt("parallelLanes"));
        activeLanes = Math.max(0, tag.getInt("activeLanes"));
        if (!clientPacket) parallelTopologyDirty = true;
        if (tag.contains("basinFluids")) basinFluids.read(tag.getCompound("basinFluids"), registries);
        catalystId = tag.contains("catalystId") ? ResourceLocation.tryParse(tag.getString("catalystId")) : null;
        catalystStack = tag.contains("catalystStack")
                ? ItemStack.parseOptional(registries, tag.getCompound("catalystStack")) : ItemStack.EMPTY;
        if (catalystId != null) {
            try {
                var kind = CatalystVisual.Kind.valueOf(tag.getString("catalystVisualKind"));
                var resource = tag.contains("catalystVisualResource")
                        ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString("catalystVisualResource"))) : Optional.<ResourceLocation>empty();
                var tint = tag.contains("catalystVisualColor")
                        ? Optional.of(tag.getInt("catalystVisualColor")) : Optional.<Integer>empty();
                catalystVisual = new CatalystVisual(kind, resource, tint);
            } catch (RuntimeException ignored) {
                catalystVisual = CatalystVisual.item();
            }
        } else {
            catalystVisual = CatalystVisual.item();
        }
        if (tag.contains("aeNode")) gridNode.loadFromNBT(tag.getCompound("aeNode"));
    }

    @Override public void remove() {
        gridNode.destroy();
        super.remove();
    }
}
