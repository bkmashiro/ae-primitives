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
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import net.neoforged.neoforge.items.ItemStackHandler;

public final class KineticMachineBlockEntity extends KineticBlockEntity implements IInWorldGridNodeHost, IActionHost {
    private static final int OUTPUT_START = 1;
    private static final int OUTPUT_END = 10;
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
    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_END) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0; }
        @Override protected void onContentsChanged(int slot) { setChanged(); sendData(); }
    };
    private float work;
    private ResourceLocation catalystId;
    private ItemStack catalystStack = ItemStack.EMPTY;
    private CatalystVisual catalystVisual = CatalystVisual.item();

    public KineticMachineBlockEntity(BlockPos pos, BlockState state) {
        this(KineticsContent.MACHINE_ENTITY.get(), pos, state);
    }

    public KineticMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public KineticMachineKind kind() {
        return ((KineticMachineBlock) getBlockState().getBlock()).kind();
    }

    public ItemStackHandler inventory() { return inventory; }
    public float workFraction() { return Math.min(1.0f, work / WORK_PER_RECIPE); }
    public Optional<ResourceLocation> catalystId() { return Optional.ofNullable(catalystId); }
    public ItemStack catalystStack() { return catalystStack.copy(); }
    public CatalystVisual catalystVisual() { return catalystVisual; }

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
        if (level != null && !level.isClientSide && !gridNode.isReady()) {
            gridNode.setVisualRepresentation(getBlockState().getBlock());
            gridNode.create(level, worldPosition);
        }
    }

    @Override public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server)) return;
        flushOutputs();
        if (!canRun()) return;
        var input = inventory.getStackInSlot(0);
        if (input.isEmpty()) { work = 0; return; }
        if (!hasRecipe(server, input)) { work = 0; return; }
        work += Math.abs(getSpeed());
        if (work < WORK_PER_RECIPE) return;
        if (completeCycle(server)) {
            work = 0;
            sendData();
        }
    }

    boolean completeCycle(ServerLevel server) {
        var input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return false;
        var outputs = rollOutputs(server, input);
        if (outputs == null || !canQueueAll(outputs)) return false;
        inventory.extractItem(0, 1, false);
        queueAll(outputs);
        setChanged();
        return true;
    }

    private boolean hasRecipe(ServerLevel server, ItemStack input) {
        return KineticProcessBehavior.forKind(kind()).canProcess(this, server, input);
    }

    private List<ItemStack> rollOutputs(ServerLevel server, ItemStack input) {
        return KineticProcessBehavior.forKind(kind()).process(this, server, input);
    }

    Optional<CatalystDefinition> catalystDefinition() {
        return catalystId == null ? Optional.empty() : CatalystRegistry.get(catalystId);
    }

    private void flushOutputs() {
        var grid = gridNode.getGrid();
        if (grid == null) return;
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            var stack = inventory.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(
                    grid.getEnergyService(), grid.getStorageService().getInventory(), key,
                    stack.getCount(), IActionSource.ofMachine(this), Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    private boolean canQueueAll(List<ItemStack> outputs) {
        var copy = new ItemStackHandler(OUTPUT_END - OUTPUT_START);
        for (int i = 0; i < copy.getSlots(); i++) copy.setStackInSlot(i, inventory.getStackInSlot(i + OUTPUT_START).copy());
        for (var output : outputs) {
            var rest = output.copy();
            for (int slot = 0; slot < copy.getSlots() && !rest.isEmpty(); slot++) rest = copy.insertItem(slot, rest, false);
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    private void queueAll(List<ItemStack> outputs) {
        for (var output : outputs) {
            var rest = output.copy();
            for (int slot = OUTPUT_START; slot < OUTPUT_END && !rest.isEmpty(); slot++) {
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

    @Override public float calculateStressApplied() { return kind().stressImpact(); }
    @Override public IGridNode getGridNode(Direction direction) { return gridNode.isReady() ? gridNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return gridNode.getNode(); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putFloat("work", work);
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
