package dev.yuzhe.aeprimitives.menu;

import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class PrimitiveMachineMenu extends AbstractContainerMenu {
    private final PrimitiveMachineBlockEntity machine;
    public PrimitiveMachineMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, (PrimitiveMachineBlockEntity) playerInventory.player.level().getBlockEntity(data.readBlockPos()));
    }
    public PrimitiveMachineMenu(int id, Inventory playerInventory, PrimitiveMachineBlockEntity machine) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.machine = machine;
        for (int i=0;i<3;i++) addSlot(new SlotItemHandler(machine.inventory(), i, 26 + i*20, 36));
        for (int i=0;i<9;i++) addSlot(new SlotItemHandler(machine.inventory(), 3+i, 86 + (i%3)*20, 26 + (i/3)*20));
        var upgrades = machine.getUpgrades().toContainer();
        for (int i=0;i<4;i++) addSlot(new Slot(upgrades, i, 16 + i*20, 68));
        for (int row=0;row<3;row++) for (int col=0;col<9;col++)
            addSlot(new Slot(playerInventory, col + row*9 + 9, 8 + col*18, 105 + row*18));
        for (int col=0;col<9;col++) addSlot(new Slot(playerInventory, col, 8 + col*18, 163));
    }
    public PrimitiveMachineBlockEntity machine() { return machine; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return machine != null && player.distanceToSqr(machine.getBlockPos().getCenter()) < 64; }
}
