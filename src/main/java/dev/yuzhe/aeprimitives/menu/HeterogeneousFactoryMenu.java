package dev.yuzhe.aeprimitives.menu;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.content.ModContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class HeterogeneousFactoryMenu extends AbstractContainerMenu {
    public static final int MACHINE_SLOTS = HeterogeneousFactoryBlockEntity.INVENTORY_END;
    private static final int DATA_PER_LANE = 3;
    private final HeterogeneousFactoryBlockEntity factory;
    private final ContainerData laneData;
    private int selectedLane;

    public HeterogeneousFactoryMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory,
                (HeterogeneousFactoryBlockEntity) playerInventory.player.level().getBlockEntity(data.readBlockPos()),
                new SimpleContainerData(HeterogeneousFactoryBlockEntity.LANE_COUNT * DATA_PER_LANE));
    }

    public HeterogeneousFactoryMenu(int id, Inventory playerInventory, HeterogeneousFactoryBlockEntity factory) {
        this(id, playerInventory, factory, factory.menuData());
    }

    private HeterogeneousFactoryMenu(int id, Inventory playerInventory, HeterogeneousFactoryBlockEntity factory,
                                     ContainerData laneData) {
        super(ModContent.HETEROGENEOUS_FACTORY_MENU.get(), id);
        this.factory = factory;
        this.laneData = laneData;

        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++)
            addSlot(new SlotItemHandler(factory.inventory(), lane, 62 + lane * 32, 18));
        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
            for (int offset = 0; offset < HeterogeneousFactoryBlockEntity.LANE_INPUT_SLOTS; offset++)
                addSlot(new LaneSlot(factory, HeterogeneousFactoryBlockEntity.inputSlot(lane, offset),
                        25 + (offset % 8) * 18, 54 + (offset / 8) * 18, lane, this));
        }
        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
            for (int offset = 0; offset < HeterogeneousFactoryBlockEntity.LANE_BUFFER_SLOTS; offset++)
                addSlot(new LaneSlot(factory, HeterogeneousFactoryBlockEntity.outputSlot(lane, offset),
                        178 + (offset % 2) * 18, 54 + (offset / 2) * 18, lane, this));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 43 + col * 18, 128 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 43 + col * 18, 186));
        addDataSlots(laneData);
        addDataSlot(new DataSlot() {
            @Override public int get() { return selectedLane; }
            @Override public void set(int value) {
                if (value >= 0 && value < HeterogeneousFactoryBlockEntity.LANE_COUNT) selectedLane = value;
            }
        });
    }

    public HeterogeneousFactoryBlockEntity factory() { return factory; }
    public int selectedLane() { return selectedLane; }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= HeterogeneousFactoryBlockEntity.LANE_COUNT) return false;
        selectedLane = id;
        return true;
    }

    public int laneProgress(int lane) { return laneData.get(lane * DATA_PER_LANE); }
    public int laneDuration(int lane) { return laneData.get(lane * DATA_PER_LANE + 1); }

    public HeterogeneousFactoryBlockEntity.LaneStatus laneStatus(int lane) {
        int value = laneData.get(lane * DATA_PER_LANE + 2);
        var statuses = HeterogeneousFactoryBlockEntity.LaneStatus.values();
        return value >= 0 && value < statuses.length ? statuses[value] : HeterogeneousFactoryBlockEntity.LaneStatus.INVALID;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack moving = original.copy();
        boolean moved;
        if (index < MACHINE_SLOTS) {
            moved = moveItemStackTo(original, MACHINE_SLOTS, slots.size(), true);
        } else if (original.is(ModContent.MACHINE_SPACE_COMPONENT.get())) {
            moved = moveItemStackTo(original, 0, HeterogeneousFactoryBlockEntity.LANE_COUNT, false);
        } else {
            int start = HeterogeneousFactoryBlockEntity.LANE_COUNT
                    + selectedLane * HeterogeneousFactoryBlockEntity.LANE_INPUT_SLOTS;
            moved = moveItemStackTo(original, start,
                    start + HeterogeneousFactoryBlockEntity.LANE_INPUT_SLOTS, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, original);
        return moving;
    }

    @Override
    public boolean stillValid(Player player) {
        return factory != null && player.distanceToSqr(factory.getBlockPos().getCenter()) < 64;
    }

    private static final class LaneSlot extends SlotItemHandler {
        private final int lane;
        private final HeterogeneousFactoryMenu menu;

        private LaneSlot(HeterogeneousFactoryBlockEntity factory, int inventorySlot, int x, int y,
                         int lane, HeterogeneousFactoryMenu menu) {
            super(factory.inventory(), inventorySlot, x, y);
            this.lane = lane;
            this.menu = menu;
        }

        @Override public boolean isActive() { return lane == menu.selectedLane; }
        @Override public boolean mayPickup(Player player) { return isActive() && super.mayPickup(player); }
        @Override public boolean mayPlace(ItemStack stack) { return isActive() && super.mayPlace(stack); }
    }
}
