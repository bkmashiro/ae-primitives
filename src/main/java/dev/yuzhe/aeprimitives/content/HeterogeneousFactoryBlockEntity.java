package dev.yuzhe.aeprimitives.content;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutors;
import dev.yuzhe.aeprimitives.diagnostics.CraftingAutopsy;
import dev.yuzhe.aeprimitives.diagnostics.CraftingAutopsyEngine;
import dev.yuzhe.aeprimitives.diagnostics.DiagnosticEventType;
import dev.yuzhe.aeprimitives.diagnostics.FactoryFlightRecorder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import dev.yuzhe.aeprimitives.menu.HeterogeneousFactoryMenu;

public final class HeterogeneousFactoryBlockEntity extends AENetworkedBlockEntity implements MenuProvider {
    public static final int LANE_COUNT = 4;
    public static final int LANE_BUFFER_SLOTS = 7;
    public static final int LANE_INPUT_SLOTS = 16;
    public static final int COMPONENT_START = 0;
    public static final int COMPONENT_END = 4;
    public static final int INPUT_START = 4;
    public static final int INPUT_END = INPUT_START + LANE_COUNT * LANE_BUFFER_SLOTS;
    public static final int OUTPUT_START = INPUT_END;
    public static final int OUTPUT_END = OUTPUT_START + LANE_COUNT * LANE_BUFFER_SLOTS;
    public static final int EXTENDED_INPUT_START = OUTPUT_END;
    public static final int INVENTORY_END = EXTENDED_INPUT_START
            + LANE_COUNT * (LANE_INPUT_SLOTS - LANE_BUFFER_SLOTS);
    private static final net.minecraft.resources.ResourceLocation FACTORY_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aeprimitives", "heterogeneous_spatial_factory");
    private static final net.minecraft.resources.ResourceLocation INPUT_CAUSE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aeprimitives", "lane_input");
    private static final net.minecraft.resources.ResourceLocation RESOURCE_CAUSE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aeprimitives", "external_resource");
    private static final net.minecraft.resources.ResourceLocation OUTPUT_CAUSE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aeprimitives", "output_buffer");
    private static final net.minecraft.resources.ResourceLocation RELOAD_CAUSE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aeprimitives", "reload_recovery");

    private final int[] laneProgress = new int[LANE_COUNT];
    private final CompoundTag[] externalLaneStates = new CompoundTag[LANE_COUNT];
    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] pendingLaneOutputs = new List[LANE_COUNT];
    private final VirtualMachineLaneExecutor[] activeExecutors = new VirtualMachineLaneExecutor[LANE_COUNT];
    private final VirtualMachineLaneExecutor.LaneContext[] activeContexts =
            new VirtualMachineLaneExecutor.LaneContext[LANE_COUNT];
    private final MachineSource source = new MachineSource(() -> getMainNode().getNode());
    private final FactoryFlightRecorder flightRecorder = new FactoryFlightRecorder(LANE_COUNT);
    private List<FactoryVisualLane> clientVisualLanes = emptyVisualLanes();
    private List<FactoryVisualLane> lastSentVisualLanes = List.of();
    private boolean lastVisualNodeActive;
    private boolean scheduled;
    private long wakeRevision;
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_END) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < COMPONENT_END) return stack.is(ModContent.MACHINE_SPACE_COMPONENT.get()) && MachineSpaceComponentItem.read(stack) != null;
            return slot < OUTPUT_START || slot >= EXTENDED_INPUT_START;
        }
        @Override protected void onContentsChanged(int slot) {
            if (slot < COMPONENT_END) {
                releaseExternalLane(slot);
                laneProgress[slot] = 0;
                flightRecorder.transition(slot, DiagnosticEventType.COMPONENT_CHANGED, FACTORY_ID,
                        inventory.getStackInSlot(slot).isEmpty() ? "component_removed" : "component_changed");
            }
            scheduled = true;
            wakeRevision++;
            refreshLanePower();
            setChanged();
            if (slot < COMPONENT_END && level != null && !level.isClientSide()) syncVisualIfChanged();
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int lane = inputLane(slot);
            if (lane >= 0 && laneInputLocked(lane)) return stack;
            return super.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= COMPONENT_START && slot < COMPONENT_END && laneOwnsState(slot)) return ItemStack.EMPTY;
            int lane = inputLane(slot);
            if (lane >= 0 && laneInputLocked(lane)) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    };

    public HeterogeneousFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.HETEROGENEOUS_FACTORY_ENTITY.get(), pos, state);
        Arrays.fill(pendingLaneOutputs, List.of());
        for (int lane = 0; lane < LANE_COUNT; lane++) externalLaneStates[lane] = new CompoundTag();
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.0);
    }

    public ItemStackHandler inventory() { return inventory; }

    /** Wakes the event-driven lane scheduler after an extension resource changes. */
    public void scheduleExternalWork() {
        scheduled = true;
        wakeRevision++;
        setChanged();
    }

    public int laneProgress(int lane) { return laneProgress[lane]; }
    public boolean isScheduled() { return scheduled; }
    public List<FactoryVisualLane> visualLanes() {
        return level != null && level.isClientSide() ? clientVisualLanes : buildVisualLanes();
    }
    public List<dev.yuzhe.aeprimitives.diagnostics.FactoryDiagnosticEvent> diagnosticEvents() {
        return flightRecorder.snapshot();
    }
    public List<CraftingAutopsy> craftingAutopsies() {
        return CraftingAutopsyEngine.explain(FACTORY_ID, flightRecorder.snapshot());
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override public int get(int index) {
                int lane = index / 3;
                return switch (index % 3) {
                    case 0 -> laneProgress[lane];
                    case 1 -> laneDuration(lane);
                    default -> laneStatus(lane).ordinal();
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return LANE_COUNT * 3; }
        };
    }

    private int laneDuration(int lane) {
        if (!(level instanceof ServerLevel server)) return 0;
        CoreLane core = coreLane(server, lane);
        if (core != null) return core.kind.processingTicks();
        ExternalLane external = externalLane(server, lane);
        return external == null || external.plan == null ? 0 : external.plan.durationTicks();
    }

    private LaneStatus laneStatus(int lane) {
        if (!pendingLaneOutputs[lane].isEmpty()) return LaneStatus.BLOCKED_OUTPUT;
        if (inventory.getStackInSlot(lane).isEmpty()) return LaneStatus.EMPTY;
        if (!(level instanceof ServerLevel server)) return LaneStatus.OFFLINE;
        if (!getMainNode().isActive()) return LaneStatus.OFFLINE;
        CoreLane core = coreLane(server, lane);
        if (core != null) {
            var plan = PrimitiveMachineRecipes.find(core.kind, snapshotInputs(lane));
            if (plan == null) return LaneStatus.WAITING_INPUT;
            return canQueueAll(lane, plan.outputs()) ? LaneStatus.RUNNING : LaneStatus.BLOCKED_OUTPUT;
        }
        ExternalLane external = externalLane(server, lane);
        if (external == null) return LaneStatus.INVALID;
        if (external.plan == null) return LaneStatus.WAITING_INPUT;
        if (!canQueueAll(lane, external.plan.previewOutputs())) return LaneStatus.BLOCKED_OUTPUT;
        return external.plan.resourcesAvailable() ? LaneStatus.RUNNING : LaneStatus.WAITING_RESOURCE;
    }

    private void recordLaneTransitions() {
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            LaneStatus status = laneStatus(lane);
            switch (status) {
                case WAITING_INPUT -> flightRecorder.transition(lane, DiagnosticEventType.WAITING_INPUT,
                        INPUT_CAUSE, "lane input unavailable");
                case WAITING_RESOURCE -> flightRecorder.transition(lane, DiagnosticEventType.WAITING_RESOURCE,
                        RESOURCE_CAUSE, "restore the required resource port");
                case BLOCKED_OUTPUT -> flightRecorder.transition(lane, DiagnosticEventType.BLOCKED_OUTPUT,
                        OUTPUT_CAUSE, "clear the output buffer or ME storage");
                case RUNNING -> flightRecorder.transition(lane, DiagnosticEventType.STARTED,
                        FACTORY_ID, "lane running");
                case EMPTY, INVALID, OFFLINE -> flightRecorder.transition(lane,
                        DiagnosticEventType.COMPONENT_CHANGED, FACTORY_ID, status.id());
            }
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        boolean nodeActive = getMainNode().isActive();
        boolean nodeChanged = nodeActive != lastVisualNodeActive;
        lastVisualNodeActive = nodeActive;
        if (!nodeActive) {
            if (nodeChanged) syncVisualIfChanged();
            return;
        }
        if (nodeChanged) scheduled = true;
        if (!scheduled) return;
        long observedWakeRevision = wakeRevision;
        flushOutputs();
        boolean workRemaining = false;
        for (int lane = 0; lane < COMPONENT_END; lane++) {
            if (!pendingLaneOutputs[lane].isEmpty()) {
                releaseExternalLane(lane);
                if (drainPending(lane)) workRemaining = true;
                continue;
            }
            CoreLane core = coreLane(server, lane);
            if (core != null) {
                releaseExternalLane(lane);
                workRemaining |= tickCoreLane(lane, core);
                continue;
            }
            ExternalLane external = externalLane(server, lane);
            if (external == null) { releaseExternalLane(lane); laneProgress[lane] = 0; continue; }
            workRemaining |= tickExternalLane(lane, external);
        }
        if (!workRemaining && wakeRevision == observedWakeRevision) scheduled = false;
        recordLaneTransitions();
        setChanged();
        syncVisualIfChanged();
    }

    private boolean tickCoreLane(int lane, CoreLane definition) {
        ItemStackHandler laneInventory = snapshotInputs(lane);
        PrimitiveMachineRecipes.Plan plan = PrimitiveMachineRecipes.find(definition.kind, laneInventory);
        if (plan == null || !canQueueAll(lane, plan.outputs())) { laneProgress[lane] = 0; return false; }
        laneProgress[lane] += definition.speed;
        if (laneProgress[lane] < definition.kind.processingTicks()) return true;
        laneProgress[lane] = 0;
        laneInventory = snapshotInputs(lane);
        plan = PrimitiveMachineRecipes.find(definition.kind, laneInventory);
        if (plan == null || !canQueueAll(lane, plan.outputs())) return true;
        plan.apply(laneInventory);
        commitInputs(lane, laneInventory);
        queueAll(lane, plan.outputs());
        flightRecorder.event(lane, DiagnosticEventType.COMPLETED, FACTORY_ID, "core operation completed");
        return true;
    }

    private boolean tickExternalLane(int lane, ExternalLane external) {
        var plan = external.plan;
        if (plan == null || (laneProgress[lane] == 0 && !canQueueAll(lane, plan.previewOutputs()))) {
            releaseExternalLane(lane);
            laneProgress[lane] = 0;
            return false;
        }
        plan.setActive(true);
        activeExecutors[lane] = external.executor;
        activeContexts[lane] = external.context;
        if (!plan.isBegun()) {
            ItemStackHandler inputs = snapshotInputs(lane);
            if (!plan.begin(inputs)) return false;
            commitInputs(lane, inputs);
            setChanged();
        }
        if (!plan.resourcesAvailable()) return false;
        laneProgress[lane] += Math.max(1, plan.workPerTick());
        if (laneProgress[lane] < plan.durationTicks()) return true;
        laneProgress[lane] = 0;
        ItemStackHandler inputs = snapshotInputs(lane);
        ExternalLane current = externalLane((ServerLevel) level, lane, inputs);
        if (current == null || current.plan == null) { releaseExternalLane(lane); return false; }
        List<ItemStack> outputs = current.plan.complete(inputs);
        if (outputs == null) { releaseExternalLane(lane); return false; }
        commitInputs(lane, inputs);
        pendingLaneOutputs[lane] = copyStacks(outputs);
        flightRecorder.event(lane, DiagnosticEventType.COMPLETED, FACTORY_ID, "external operation completed");
        externalLaneStates[lane] = new CompoundTag();
        releaseExternalLane(lane);
        drainPending(lane);
        return true;
    }

    private boolean drainPending(int lane) {
        List<ItemStack> pending = pendingLaneOutputs[lane];
        if (pending.isEmpty() || !canQueueAll(lane, pending)) return false;
        queueAll(lane, pending);
        pendingLaneOutputs[lane] = List.of();
        return true;
    }

    private void releaseExternalLane(int lane) {
        VirtualMachineLaneExecutor executor = activeExecutors[lane];
        VirtualMachineLaneExecutor.LaneContext context = activeContexts[lane];
        activeExecutors[lane] = null;
        activeContexts[lane] = null;
        if (executor != null && context != null) executor.release(context);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        var copy = new ArrayList<ItemStack>(stacks.size());
        for (ItemStack stack : stacks) if (!stack.isEmpty()) copy.add(stack.copy());
        return List.copyOf(copy);
    }

    private CoreLane coreLane(ServerLevel server, int slot) {
        MachineSpaceEnvelope envelope = MachineSpaceComponentItem.read(inventory.getStackInSlot(slot));
        if (envelope == null || !envelope.blockId().getNamespace().equals("aeprimitives")) return null;
        MachineKind kind = Arrays.stream(MachineKind.values()).filter(candidate -> candidate.id().equals(envelope.blockId().getPath())).findFirst().orElse(null);
        if (kind == null || !supportsVirtualExecution(kind)) return null;
        var block = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK).get(envelope.blockId());
        if (!(block instanceof PrimitiveMachineBlock machine) || machine.kind() != kind) return null;
        var upgrades = appeng.api.upgrades.UpgradeInventories.forMachine(block, 4, () -> {});
        upgrades.readFromNBT(envelope.configuration(), "upgrades", server.registryAccess());
        int speed = 1 << Math.min(kind.maxSpeedCards(), upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        return new CoreLane(kind, speed);
    }

    private ExternalLane externalLane(ServerLevel server, int slot) {
        return externalLane(server, slot, snapshotInputs(slot));
    }

    private ExternalLane externalLane(ServerLevel server, int slot, ItemStackHandler inputs) {
        MachineSpaceEnvelope envelope = MachineSpaceComponentItem.read(inventory.getStackInSlot(slot));
        if (envelope == null) return null;
        VirtualMachineLaneExecutor executor = VirtualMachineLaneExecutors.find(envelope);
        if (executor == null) return null;
        var context = new VirtualMachineLaneExecutor.LaneContext(
                server, worldPosition, slot, envelope, inputs, externalLaneStates[slot]);
        return new ExternalLane(executor, context, executor.prepare(context));
    }

    private static boolean supportsVirtualExecution(MachineKind kind) {
        return switch (kind) {
            case CONCRETE, SOIL, DRIPSTONE, OXIDATION, CROP, TREE, GROWTH_RACK, BEE, BATCH, COOLING -> true;
            default -> false;
        };
    }

    public static int inputSlot(int lane, int offset) {
        if (offset < 0 || offset >= LANE_INPUT_SLOTS) throw new IndexOutOfBoundsException(offset);
        return offset < LANE_BUFFER_SLOTS
                ? INPUT_START + lane * LANE_BUFFER_SLOTS + offset
                : EXTENDED_INPUT_START + lane * (LANE_INPUT_SLOTS - LANE_BUFFER_SLOTS)
                        + offset - LANE_BUFFER_SLOTS;
    }
    public static int outputSlot(int lane, int offset) { return OUTPUT_START + lane * LANE_BUFFER_SLOTS + offset; }

    private static int inputLane(int slot) {
        if (slot >= INPUT_START && slot < INPUT_END) return (slot - INPUT_START) / LANE_BUFFER_SLOTS;
        return slot >= EXTENDED_INPUT_START && slot < INVENTORY_END
                ? (slot - EXTENDED_INPUT_START) / (LANE_INPUT_SLOTS - LANE_BUFFER_SLOTS) : -1;
    }

    private boolean laneInputLocked(int lane) {
        return laneProgress[lane] > 0 || !pendingLaneOutputs[lane].isEmpty()
                || !externalLaneStates[lane].isEmpty();
    }

    private boolean laneOwnsState(int lane) {
        if (laneProgress[lane] > 0 || !pendingLaneOutputs[lane].isEmpty()
                || !externalLaneStates[lane].isEmpty() || activeExecutors[lane] != null) return true;
        for (int offset = 0; offset < LANE_INPUT_SLOTS; offset++)
            if (!inventory.getStackInSlot(inputSlot(lane, offset)).isEmpty()) return true;
        for (int offset = 0; offset < LANE_BUFFER_SLOTS; offset++)
            if (!inventory.getStackInSlot(outputSlot(lane, offset)).isEmpty()) return true;
        return false;
    }

    private ItemStackHandler snapshotInputs(int lane) {
        var snapshot = new ItemStackHandler(LANE_INPUT_SLOTS);
        for (int slot = 0; slot < LANE_INPUT_SLOTS; slot++) snapshot.setStackInSlot(slot, inventory.getStackInSlot(inputSlot(lane, slot)).copy());
        return snapshot;
    }

    private void commitInputs(int lane, ItemStackHandler snapshot) {
        for (int slot = 0; slot < LANE_INPUT_SLOTS; slot++) inventory.setStackInSlot(inputSlot(lane, slot), snapshot.getStackInSlot(slot).copy());
    }

    private void refreshLanePower() {
        if (!(level instanceof ServerLevel server)) return;
        double idlePower = 0.0;
        for (int lane = 0; lane < COMPONENT_END; lane++) {
            CoreLane core = coreLane(server, lane);
            if (core != null) {
                idlePower += 2.0 * core.speed * core.speed;
                continue;
            }
            ExternalLane external = externalLane(server, lane);
            if (external != null && external.plan != null) idlePower += external.plan.idleAePower();
        }
        getMainNode().setIdlePowerUsage(idlePower);
    }

    private boolean canQueueAll(int lane, List<ItemStack> outputs) {
        var simulated = new ItemStackHandler(LANE_BUFFER_SLOTS);
        for (int slot = 0; slot < LANE_BUFFER_SLOTS; slot++) simulated.setStackInSlot(slot, inventory.getStackInSlot(outputSlot(lane, slot)).copy());
        for (var output : outputs) {
            var remaining = output.copy();
            for (int slot = 0; slot < simulated.getSlots() && !remaining.isEmpty(); slot++) remaining = simulated.insertItem(slot, remaining, false);
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private void queueAll(int lane, List<ItemStack> outputs) {
        for (var output : outputs) {
            var remaining = output.copy();
            for (int offset = 0; offset < LANE_BUFFER_SLOTS && !remaining.isEmpty(); offset++) {
                int slot = outputSlot(lane, offset);
                var stored = inventory.getStackInSlot(slot);
                if (stored.isEmpty()) {
                    inventory.setStackInSlot(slot, remaining);
                    remaining = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(stored, remaining)) {
                    int moved = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
                    if (moved > 0) {
                        var combined = stored.copyWithCount(stored.getCount() + moved);
                        inventory.setStackInSlot(slot, combined);
                        remaining.shrink(moved);
                    }
                }
            }
        }
    }

    private void flushOutputs() {
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            var stack = inventory.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(), key, stack.getCount(), source, Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    @Override public Component getDisplayName() { return Component.translatable("block.aeprimitives.heterogeneous_spatial_factory"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new HeterogeneousFactoryMenu(id, playerInventory, this);
    }

    private List<FactoryVisualLane> buildVisualLanes() {
        var lanes = new ArrayList<FactoryVisualLane>(LANE_COUNT);
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            MachineSpaceEnvelope envelope = MachineSpaceComponentItem.read(inventory.getStackInSlot(lane));
            var machineId = envelope == null ? null : envelope.blockId();
            LaneStatus status = laneStatus(lane);
            int duration = laneDuration(lane);
            int step = duration <= 0 ? 0 : Math.min(FactoryVisualLane.PROGRESS_STEPS - 1,
                    laneProgress[lane] * FactoryVisualLane.PROGRESS_STEPS / duration);
            lanes.add(new FactoryVisualLane(lane, machineId, status, step));
        }
        return List.copyOf(lanes);
    }

    private static List<FactoryVisualLane> emptyVisualLanes() {
        var lanes = new ArrayList<FactoryVisualLane>(LANE_COUNT);
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            lanes.add(new FactoryVisualLane(lane, null, LaneStatus.EMPTY, 0));
        }
        return List.copyOf(lanes);
    }

    private void syncVisualIfChanged() {
        List<FactoryVisualLane> lanes = buildVisualLanes();
        if (lanes.equals(lastSentVisualLanes)) return;
        lastSentVisualLanes = lanes;
        markForUpdate();
    }

    private CompoundTag visualTag() {
        var tag = new CompoundTag();
        tag.putInt("visualVersion", 1);
        var lanes = new ListTag();
        for (FactoryVisualLane lane : buildVisualLanes()) {
            var laneTag = new CompoundTag();
            laneTag.putInt("lane", lane.lane());
            if (lane.machineId() != null) laneTag.putString("machine", lane.machineId().toString());
            laneTag.putString("status", lane.status().id());
            laneTag.putInt("progressStep", lane.progressStep());
            lanes.add(laneTag);
        }
        tag.put("visualLanes", lanes);
        return tag;
    }

    private void readVisualTag(CompoundTag tag) {
        if (tag.getInt("visualVersion") != 1) {
            clientVisualLanes = emptyVisualLanes();
            return;
        }
        var decoded = new FactoryVisualLane[LANE_COUNT];
        var lanes = tag.getList("visualLanes", Tag.TAG_COMPOUND);
        if (lanes.size() != LANE_COUNT) {
            clientVisualLanes = emptyVisualLanes();
            return;
        }
        for (int i = 0; i < lanes.size(); i++) {
            var laneTag = lanes.getCompound(i);
            int lane = laneTag.getInt("lane");
            if (lane < 0 || lane >= LANE_COUNT || decoded[lane] != null) continue;
            var machineId = laneTag.contains("machine")
                    ? net.minecraft.resources.ResourceLocation.tryParse(laneTag.getString("machine")) : null;
            LaneStatus status = laneStatusFromWire(laneTag.getString("status"));
            int step = Math.max(0, Math.min(FactoryVisualLane.PROGRESS_STEPS - 1,
                    laneTag.getInt("progressStep")));
            decoded[lane] = new FactoryVisualLane(lane, machineId, status, step);
        }
        var result = new ArrayList<FactoryVisualLane>(LANE_COUNT);
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            result.add(decoded[lane] == null
                    ? new FactoryVisualLane(lane, null, LaneStatus.EMPTY, 0) : decoded[lane]);
        }
        clientVisualLanes = List.copyOf(result);
    }

    private static LaneStatus laneStatusFromWire(String id) {
        for (LaneStatus status : LaneStatus.values()) if (status.id().equals(id)) return status;
        return LaneStatus.INVALID;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return visualTag(); }

    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
    getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readVisualTag(tag);
    }

    @Override public void onDataPacket(net.minecraft.network.Connection connection,
                                      net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                      HolderLookup.Provider registries) {
        readVisualTag(packet.getTag());
    }

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putIntArray("laneProgress", laneProgress);
        tag.putBoolean("scheduled", scheduled);
        var pending = new ListTag();
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            if (pendingLaneOutputs[lane].isEmpty()) continue;
            var laneTag = new CompoundTag();
            laneTag.putInt("lane", lane);
            var stacks = new ListTag();
            for (ItemStack stack : pendingLaneOutputs[lane]) stacks.add(stack.save(registries));
            laneTag.put("stacks", stacks);
            pending.add(laneTag);
        }
        tag.put("pendingLaneOutputs", pending);
        var externalStates = new ListTag();
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            if (externalLaneStates[lane].isEmpty()) continue;
            var laneTag = new CompoundTag();
            laneTag.putInt("lane", lane);
            laneTag.put("state", externalLaneStates[lane].copy());
            externalStates.add(laneTag);
        }
        tag.put("externalLaneStates", externalStates);
        tag.put("flightRecorder", flightRecorder.save());
    }

    private void restoreInventory(CompoundTag savedInventory, HolderLookup.Provider registries) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        int savedSize = savedInventory.getInt("Size");
        if (savedSize <= 0) return;
        var restored = new ItemStackHandler(savedSize);
        restored.deserializeNBT(registries, savedInventory);
        if (savedSize == 28) {
            // Original layout: four components, then three inputs and three outputs per lane.
            for (int lane = 0; lane < LANE_COUNT; lane++) {
                inventory.setStackInSlot(lane, restored.getStackInSlot(lane).copy());
                for (int offset = 0; offset < 3; offset++) {
                    inventory.setStackInSlot(inputSlot(lane, offset),
                            restored.getStackInSlot(4 + lane * 3 + offset).copy());
                    inventory.setStackInSlot(outputSlot(lane, offset),
                            restored.getStackInSlot(16 + lane * 3 + offset).copy());
                }
            }
            return;
        }
        // The 60-slot layout and the current layout deliberately share their first 60 slot IDs.
        for (int slot = 0; slot < Math.min(savedSize, inventory.getSlots()); slot++)
            inventory.setStackInSlot(slot, restored.getStackInSlot(slot).copy());
    }

    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        for (int lane = 0; lane < LANE_COUNT; lane++) releaseExternalLane(lane);
        super.loadTag(tag, registries);
        restoreInventory(tag.getCompound("inventory"), registries);
        int[] saved = tag.getIntArray("laneProgress");
        Arrays.fill(laneProgress, 0);
        System.arraycopy(saved, 0, laneProgress, 0, Math.min(saved.length, laneProgress.length));
        scheduled = tag.getBoolean("scheduled");
        Arrays.fill(pendingLaneOutputs, List.of());
        for (int lane = 0; lane < LANE_COUNT; lane++) externalLaneStates[lane] = new CompoundTag();
        var pending = tag.getList("pendingLaneOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            var laneTag = pending.getCompound(i);
            int lane = laneTag.getInt("lane");
            if (lane < 0 || lane >= LANE_COUNT) continue;
            var restored = new ArrayList<ItemStack>();
            var stacks = laneTag.getList("stacks", Tag.TAG_COMPOUND);
            for (int stack = 0; stack < stacks.size(); stack++) {
                ItemStack item = ItemStack.parseOptional(registries, stacks.getCompound(stack));
                if (!item.isEmpty()) restored.add(item);
            }
            pendingLaneOutputs[lane] = List.copyOf(restored);
        }
        var externalStates = tag.getList("externalLaneStates", Tag.TAG_COMPOUND);
        for (int i = 0; i < externalStates.size(); i++) {
            var laneTag = externalStates.getCompound(i);
            int lane = laneTag.getInt("lane");
            if (lane >= 0 && lane < LANE_COUNT && laneTag.contains("state"))
                externalLaneStates[lane] = laneTag.getCompound("state").copy();
        }
        flightRecorder.load(tag.getCompound("flightRecorder"));
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            if (laneProgress[lane] > 0 || !pendingLaneOutputs[lane].isEmpty() || !externalLaneStates[lane].isEmpty()) {
                flightRecorder.event(lane, DiagnosticEventType.RECOVERED, RELOAD_CAUSE,
                        "saved lane state restored");
            }
        }
        if (hasPendingOutputs() || hasExternalState()) scheduled = true;
    }

    @Override public void onReady() {
        super.onReady();
        refreshLanePower();
        if (hasInputs() || hasExternalState()) scheduled = true;
    }

    private boolean hasInputs() {
        for (int slot = INPUT_START; slot < INPUT_END; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        for (int slot = EXTENDED_INPUT_START; slot < INVENTORY_END; slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }

    private boolean hasPendingOutputs() {
        for (List<ItemStack> pending : pendingLaneOutputs) if (!pending.isEmpty()) return true;
        return false;
    }

    private boolean hasExternalState() {
        for (CompoundTag state : externalLaneStates) if (!state.isEmpty()) return true;
        return false;
    }

    @Override public void setRemoved() {
        for (int lane = 0; lane < LANE_COUNT; lane++) releaseExternalLane(lane);
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        for (int lane = 0; lane < LANE_COUNT; lane++) releaseExternalLane(lane);
        super.onChunkUnloaded();
    }

    private record CoreLane(MachineKind kind, int speed) {}
    private record ExternalLane(VirtualMachineLaneExecutor executor,
                                VirtualMachineLaneExecutor.LaneContext context,
                                VirtualMachineLaneExecutor.LanePlan plan) {}

    public enum LaneStatus {
        EMPTY("empty"),
        INVALID("invalid"),
        OFFLINE("offline"),
        WAITING_INPUT("waiting_input"),
        WAITING_RESOURCE("waiting_resource"),
        BLOCKED_OUTPUT("blocked_output"),
        RUNNING("running");

        private final String id;
        LaneStatus(String id) { this.id = id; }
        public String id() { return id; }
    }
}
