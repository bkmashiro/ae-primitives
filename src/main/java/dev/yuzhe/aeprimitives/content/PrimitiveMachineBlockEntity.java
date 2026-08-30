package dev.yuzhe.aeprimitives.content;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.recipes.transform.TransformRecipe;
import appeng.recipes.transform.TransformRecipeInput;
import dev.yuzhe.aeprimitives.menu.PrimitiveMachineMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class PrimitiveMachineBlockEntity extends AENetworkedBlockEntity implements MenuProvider {
    private static final int OUTPUT_START = 3;
    private static final int OUTPUT_END = 12;
    private final ItemStackHandler inventory = new ItemStackHandler(12) {
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null) markForUpdate();
        }
    };
    private final MachineSource source = new MachineSource(() -> getMainNode().getNode());
    private int progress;

    public PrimitiveMachineBlockEntity(BlockPos pos, BlockState state) {
        this(ModContent.MACHINE_ENTITY.get(), pos, state);
    }

    public PrimitiveMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2.0);
    }

    public ItemStackHandler inventory() { return inventory; }
    public MachineKind kind() { return ((PrimitiveMachineBlock) getBlockState().getBlock()).kind(); }
    public int progress() { return progress; }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || !getMainNode().isActive()) return;
        flushOutputs();
        if (!hasOutputRoom()) return;
        if (++progress < kind().processingTicks()) return;
        progress = 0;
        switch (kind()) {
            case FORTUNE -> processFortune(server);
            case TRANSFORMATION -> processTransformation(server);
            case GENERATOR -> queueAll(List.of(new ItemStack(Items.COBBLESTONE)));
        }
    }

    private void processFortune(ServerLevel server) {
        var input = inventory.getStackInSlot(0);
        if (!(input.getItem() instanceof BlockItem blockItem)) return;
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchantments = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        tool.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), 3);
        var drops = Block.getDrops(blockItem.getBlock().defaultBlockState(), server, worldPosition, null, null, tool);
        if (drops.isEmpty() || !canQueueAll(drops)) return;
        input.shrink(1);
        queueAll(drops);
    }

    private void processTransformation(ServerLevel server) {
        var available = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 3; slot++) {
            var stack = inventory.getStackInSlot(slot);
            for (int i = 0; i < stack.getCount(); i++) available.add(stack.copyWithCount(1));
        }
        for (var holder : server.getRecipeManager().getAllRecipesFor(TransformRecipe.TYPE)) {
            var recipe = holder.value();
            if (!recipe.getCircumstance().isFluid()) continue;
            var remaining = new ArrayList<>(available);
            var consumedSlots = new ArrayList<Integer>();
            boolean matched = true;
            for (var ingredient : recipe.getIngredients()) {
                int found = -1;
                for (int i = 0; i < remaining.size(); i++) if (ingredient.test(remaining.get(i))) { found = i; break; }
                if (found < 0) { matched = false; break; }
                var matchedStack = remaining.remove(found);
                for (int slot = 0; slot < 3; slot++) {
                    if (!consumedSlots.contains(slot) && ItemStack.isSameItemSameComponents(inventory.getStackInSlot(slot), matchedStack)) {
                        consumedSlots.add(slot); break;
                    }
                }
            }
            if (!matched) continue;
            var result = recipe.assemble(new TransformRecipeInput(available), server.registryAccess());
            if (result.isEmpty() || !canQueueAll(List.of(result))) return;
            for (var slot : consumedSlots) inventory.extractItem(slot, 1, false);
            queueAll(List.of(result));
            return;
        }
    }

    private void flushOutputs() {
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            var stack = inventory.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    key, stack.getCount(), source, Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    private boolean hasOutputRoom() {
        for (int i = OUTPUT_START; i < OUTPUT_END; i++) if (inventory.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    private boolean canQueueAll(List<ItemStack> stacks) {
        var copy = new ItemStackHandler(OUTPUT_END - OUTPUT_START);
        for (int i = 0; i < copy.getSlots(); i++) copy.setStackInSlot(i, inventory.getStackInSlot(OUTPUT_START + i).copy());
        for (var stack : stacks) {
            var rest = stack.copy();
            for (int i = 0; i < copy.getSlots() && !rest.isEmpty(); i++) rest = copy.insertItem(i, rest, false);
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    private void queueAll(List<ItemStack> stacks) {
        for (var stack : stacks) {
            var rest = stack.copy();
            for (int i = OUTPUT_START; i < OUTPUT_END && !rest.isEmpty(); i++) rest = inventory.insertItem(i, rest, false);
        }
    }

    @Override public Component getDisplayName() { return Component.translatable("block.aeprimitives." + kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PrimitiveMachineMenu(id, playerInventory, this);
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("progress", progress);
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        progress = tag.getInt("progress");
    }
}
