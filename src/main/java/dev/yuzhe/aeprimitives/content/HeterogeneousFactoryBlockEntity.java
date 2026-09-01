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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
    public static final int COMPONENT_START = 0;
    public static final int COMPONENT_END = 4;
    public static final int INPUT_START = 4;
    public static final int INPUT_END = 16;
    public static final int OUTPUT_START = 16;
    public static final int OUTPUT_END = 28;

    private final int[] laneProgress = new int[LANE_COUNT];
    private final MachineSource source = new MachineSource(() -> getMainNode().getNode());
    private boolean scheduled;
    private final ItemStackHandler inventory = new ItemStackHandler(28) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < COMPONENT_END) return stack.is(ModContent.MACHINE_SPACE_COMPONENT.get()) && MachineSpaceComponentItem.read(stack) != null;
            return slot < OUTPUT_START;
        }
        @Override protected void onContentsChanged(int slot) {
            if (slot < COMPONENT_END) laneProgress[slot] = 0;
            scheduled = true;
            refreshLanePower();
            setChanged();
        }
    };

    public HeterogeneousFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.HETEROGENEOUS_FACTORY_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.0);
    }

    public ItemStackHandler inventory() { return inventory; }
    public int laneProgress(int lane) { return laneProgress[lane]; }
    public boolean isScheduled() { return scheduled; }

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
        Lane definition = lane(server, lane);
        return definition == null ? 0 : definition.kind.processingTicks();
    }

    private LaneStatus laneStatus(int lane) {
        if (inventory.getStackInSlot(lane).isEmpty()) return LaneStatus.EMPTY;
        if (!(level instanceof ServerLevel server)) return LaneStatus.OFFLINE;
        Lane definition = lane(server, lane);
        if (definition == null) return LaneStatus.INVALID;
        if (!getMainNode().isActive()) return LaneStatus.OFFLINE;
        var plan = PrimitiveMachineRecipes.find(definition.kind, snapshotInputs(lane));
        if (plan == null) return LaneStatus.WAITING_INPUT;
        return canQueueAll(lane, plan.outputs()) ? LaneStatus.RUNNING : LaneStatus.BLOCKED_OUTPUT;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || !scheduled || !getMainNode().isActive()) return;
        flushOutputs();
        boolean workRemaining = false;
        for (int lane = 0; lane < COMPONENT_END; lane++) {
            Lane definition = lane(server, lane);
            if (definition == null) { laneProgress[lane] = 0; continue; }
            ItemStackHandler laneInventory = snapshotInputs(lane);
            PrimitiveMachineRecipes.Plan plan = PrimitiveMachineRecipes.find(definition.kind, laneInventory);
            if (plan == null || !canQueueAll(lane, plan.outputs())) { laneProgress[lane] = 0; continue; }
            workRemaining = true;
            laneProgress[lane] += definition.speed;
            if (laneProgress[lane] < definition.kind.processingTicks()) continue;
            laneProgress[lane] = 0;
            laneInventory = snapshotInputs(lane);
            plan = PrimitiveMachineRecipes.find(definition.kind, laneInventory);
            if (plan == null || !canQueueAll(lane, plan.outputs())) continue;
            plan.apply(laneInventory);
            commitInputs(lane, laneInventory);
            queueAll(lane, plan.outputs());
        }
        if (!workRemaining) scheduled = false;
        setChanged();
        markForUpdate();
    }

    private Lane lane(ServerLevel server, int slot) {
        MachineSpaceEnvelope envelope = MachineSpaceComponentItem.read(inventory.getStackInSlot(slot));
        if (envelope == null || !envelope.blockId().getNamespace().equals("aeprimitives")) return null;
        MachineKind kind = Arrays.stream(MachineKind.values()).filter(candidate -> candidate.id().equals(envelope.blockId().getPath())).findFirst().orElse(null);
        if (kind == null || !supportsVirtualExecution(kind)) return null;
        var block = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK).get(envelope.blockId());
        if (!(block instanceof PrimitiveMachineBlock machine) || machine.kind() != kind) return null;
        var upgrades = appeng.api.upgrades.UpgradeInventories.forMachine(block, 4, () -> {});
        upgrades.readFromNBT(envelope.configuration(), "upgrades", server.registryAccess());
        int speed = 1 << Math.min(kind.maxSpeedCards(), upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        return new Lane(kind, speed);
    }

    private static boolean supportsVirtualExecution(MachineKind kind) {
        return switch (kind) {
            case CONCRETE, SOIL, DRIPSTONE, OXIDATION, CROP, TREE, GROWTH_RACK, BEE, BATCH, COOLING -> true;
            default -> false;
        };
    }

    public static int inputSlot(int lane, int offset) { return INPUT_START + lane * 3 + offset; }
    public static int outputSlot(int lane, int offset) { return OUTPUT_START + lane * 3 + offset; }

    private ItemStackHandler snapshotInputs(int lane) {
        var snapshot = new ItemStackHandler(12);
        for (int slot = 0; slot < 3; slot++) snapshot.setStackInSlot(slot, inventory.getStackInSlot(inputSlot(lane, slot)).copy());
        return snapshot;
    }

    private void commitInputs(int lane, ItemStackHandler snapshot) {
        for (int slot = 0; slot < 3; slot++) inventory.setStackInSlot(inputSlot(lane, slot), snapshot.getStackInSlot(slot).copy());
    }

    private void refreshLanePower() {
        if (!(level instanceof ServerLevel server)) return;
        double idlePower = 0.0;
        for (int lane = 0; lane < COMPONENT_END; lane++) {
            Lane definition = lane(server, lane);
            if (definition != null) idlePower += 2.0 * definition.speed * definition.speed;
        }
        getMainNode().setIdlePowerUsage(idlePower);
    }

    private boolean canQueueAll(int lane, List<ItemStack> outputs) {
        var simulated = new ItemStackHandler(3);
        for (int slot = 0; slot < 3; slot++) simulated.setStackInSlot(slot, inventory.getStackInSlot(outputSlot(lane, slot)).copy());
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
            for (int offset = 0; offset < 3 && !remaining.isEmpty(); offset++) {
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

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putIntArray("laneProgress", laneProgress);
        tag.putBoolean("scheduled", scheduled);
    }

    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        int[] saved = tag.getIntArray("laneProgress");
        System.arraycopy(saved, 0, laneProgress, 0, Math.min(saved.length, laneProgress.length));
        scheduled = tag.getBoolean("scheduled");
    }

    @Override public void onReady() {
        super.onReady();
        refreshLanePower();
        if (hasInputs()) scheduled = true;
    }

    private boolean hasInputs() {
        for (int slot = INPUT_START; slot < INPUT_END; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }

    private record Lane(MachineKind kind, int speed) {}

    public enum LaneStatus {
        EMPTY("empty"),
        INVALID("invalid"),
        OFFLINE("offline"),
        WAITING_INPUT("waiting_input"),
        BLOCKED_OUTPUT("blocked_output"),
        RUNNING("running");

        private final String id;
        LaneStatus(String id) { this.id = id; }
        public String id() { return id; }
    }
}
