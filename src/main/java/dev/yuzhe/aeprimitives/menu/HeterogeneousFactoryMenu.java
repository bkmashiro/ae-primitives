package dev.yuzhe.aeprimitives.menu;

import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.content.ModContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class HeterogeneousFactoryMenu extends AbstractContainerMenu {
    public static final int MACHINE_SLOTS = HeterogeneousFactoryBlockEntity.OUTPUT_END;
    private static final int DATA_PER_LANE = 3;
    private final HeterogeneousFactoryBlockEntity factory;
    private final ContainerData laneData;

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

        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
            int x = 15 + lane * 60;
            addSlot(new SlotItemHandler(factory.inventory(), lane, x + 18, 18));
        }
        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
            int x = 15 + lane * 60;
            for (int offset = 0; offset < 3; offset++) {
                addSlot(new SlotItemHandler(factory.inventory(), HeterogeneousFactoryBlockEntity.inputSlot(lane, offset), x + offset * 18, 54));
            }
        }
        for (int lane = 0; lane < HeterogeneousFactoryBlockEntity.LANE_COUNT; lane++) {
            int x = 15 + lane * 60;
            for (int offset = 0; offset < 3; offset++) {
                addSlot(new SlotItemHandler(factory.inventory(), HeterogeneousFactoryBlockEntity.outputSlot(lane, offset), x + offset * 18, 82));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 43 + col * 18, 128 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 43 + col * 18, 186));
        addDataSlots(laneData);
    }

    public HeterogeneousFactoryBlockEntity factory() {
        return factory;
    }

    public int laneProgress(int lane) {
        return laneData.get(lane * DATA_PER_LANE);
    }

    public int laneDuration(int lane) {
        return laneData.get(lane * DATA_PER_LANE + 1);
    }

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
            moved = moveItemStackTo(original, HeterogeneousFactoryBlockEntity.LANE_COUNT,
                    HeterogeneousFactoryBlockEntity.INPUT_END, false);
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
}
