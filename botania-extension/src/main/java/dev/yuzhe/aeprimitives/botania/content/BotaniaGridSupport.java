package dev.yuzhe.aeprimitives.botania.content;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

final class BotaniaGridSupport {
    record ConnectionState(boolean dirty, int bootstrapTicks) {}

    static ConnectionState refreshConnections(Level level, BlockPos position, IManagedGridNode managed,
                                              boolean dirty, int bootstrapTicks) {
        if (level == null || level.isClientSide || !managed.isReady() || (!dirty && bootstrapTicks <= 0)) {
            return new ConnectionState(dirty, bootstrapTicks);
        }
        int remainingTicks = Math.max(0, bootstrapTicks - 1);
        var node = managed.getNode();
        boolean pendingNeighbor = false;
        for (var direction : Direction.values()) {
            if (node.getInWorldConnections().containsKey(direction)) continue;
            var host = GridHelper.getNodeHost(level, position.relative(direction));
            if (host == null) continue;
            var neighbor = GridHelper.getExposedNode(level, position.relative(direction), direction.getOpposite());
            if (neighbor == null) {
                pendingNeighbor = true;
            } else if (neighbor != node && node.getConnections().stream()
                    .noneMatch(connection -> connection.getOtherSide(node) == neighbor)) {
                GridHelper.createConnection(node, neighbor);
            }
        }
        return new ConnectionState(pendingNeighbor, remainingTicks);
    }

    static void flushOutputs(IManagedGridNode managed, ItemStackHandler inventory, int start, int end) {
        if (!managed.isActive() || managed.getGrid() == null) return;
        var storage = managed.getGrid().getStorageService().getInventory();
        var energy = managed.getGrid().getEnergyService();
        var source = IActionSource.ofMachine(() -> managed.getNode());
        for (int slot = start; slot < end; slot++) {
            var stack = inventory.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = appeng.api.storage.StorageHelper.poweredInsert(energy, storage, key, stack.getCount(), source, Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    private BotaniaGridSupport() {}
}
