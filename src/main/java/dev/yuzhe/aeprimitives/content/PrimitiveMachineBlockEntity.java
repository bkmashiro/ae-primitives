package dev.yuzhe.aeprimitives.content;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.recipes.transform.TransformRecipe;
import appeng.recipes.transform.TransformRecipeInput;
import appeng.core.definitions.AEItems;
import dev.yuzhe.aeprimitives.crafting.DynamicPatternProvider;
import dev.yuzhe.aeprimitives.crafting.LazyPrimitivePattern;
import dev.yuzhe.aeprimitives.menu.PrimitiveMachineMenu;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class PrimitiveMachineBlockEntity extends AENetworkedBlockEntity implements MenuProvider, IUpgradeableObject {
    private static final int OUTPUT_START = 3;
    private static final int OUTPUT_END = 12;
    private final ItemStackHandler inventory = new ItemStackHandler(12) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= OUTPUT_START || acceptingPatternInputs || !isPatternProviderMode();
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < OUTPUT_START && isPatternProviderMode() && !acceptingPatternInputs) return stack;
            return super.insertItem(slot, stack, simulate);
        }
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null) markForUpdate();
        }
    };
    private final MachineSource source = new MachineSource(() -> getMainNode().getNode());
    private final DynamicPatternProvider patternProvider = new DynamicPatternProvider(this);
    private final IUpgradeInventory upgrades;
    private int progress;
    private float compostProgress;
    private boolean structureDirty = true;
    private boolean formed;
    private boolean patternJobActive;
    private boolean acceptingPatternInputs;

    public PrimitiveMachineBlockEntity(BlockPos pos, BlockState state) {
        this(ModContent.MACHINE_ENTITY.get(), pos, state);
    }

    public PrimitiveMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        upgrades = UpgradeInventories.forMachine(state.getBlock(), 4, this::onUpgradesChanged);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(2.0)
                .addService(ICraftingProvider.class, patternProvider);
    }

    public ItemStackHandler inventory() { return inventory; }
    public MachineKind kind() { return ((PrimitiveMachineBlock) getBlockState().getBlock()).kind(); }
    public int progress() { return progress; }
    public float compostProgress() { return compostProgress; }
    public boolean isFormed() { return formed; }
    public boolean isPatternProviderMode() {
        return kind().supportsPatternProvider() && getInstalledUpgrades(ModContent.PATTERN_PROVIDER_CARD.get()) > 0;
    }
    public boolean isPatternBusy() {
        if (patternJobActive) return true;
        for (int slot = 0; slot < OUTPUT_END; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }
    @Override public IUpgradeInventory getUpgrades() { return upgrades; }
    public void markStructureDirty() { structureDirty = true; }

    private void onUpgradesChanged() {
        progress = 0;
        getMainNode().setIdlePowerUsage(2.0 * speedMultiplier() * speedMultiplier());
        refreshPatternProvider();
        setChanged();
        if (level != null) markForUpdate();
    }

    private int speedMultiplier() {
        return 1 << Math.min(kind().maxSpeedCards(), getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        if (kind() == MachineKind.FOUNDRY) {
            if (structureDirty) updateFoundryStructure(server);
            if (!formed) return;
        }
        if (!getMainNode().isActive()) return;
        flushOutputs();
        if (isPatternProviderMode() && !patternJobActive) {
            evacuateInputs();
            progress = 0;
            return;
        }
        if (!hasOutputRoom()) return;
        progress += speedMultiplier();
        if (progress < kind().processingTicks()) {
            if ((progress & 3) == 0) markForUpdate();
            return;
        }
        progress = 0;
        if (patternJobActive) {
            boolean completed = switch (kind()) {
                case GROWTH -> processGrowth();
                case CONCRETE, SOIL, DRIPSTONE, OXIDATION, CROP, TREE, GROWTH_RACK, BEE, COOLING ->
                        processPrimitiveRecipe();
                default -> false;
            };
            if (completed) finishPatternJob();
        } else switch (kind()) {
            case FORTUNE -> processFortune(server);
            case TRANSFORMATION -> processTransformation(server);
            case GENERATOR -> queueAll(List.of(new ItemStack(Items.COBBLESTONE)));
            case GROWTH -> processGrowth();
            case COMPOST -> processCompost();
            case FOUNDRY -> {
                for (int parallel = 0; parallel < 4 && processTransformation(server); parallel++) {}
            }
            case CONCRETE, SOIL, DRIPSTONE, OXIDATION, CROP, TREE, GROWTH_RACK, BEE, BATCH, COOLING ->
                    processPrimitiveRecipe();
        }
        markForUpdate();
    }

    private boolean processPrimitiveRecipe() {
        var plan = PrimitiveMachineRecipes.find(kind(), inventory);
        if (plan == null || !canQueueAll(plan.outputs())) return false;
        plan.apply(inventory);
        queueAll(plan.outputs());
        return true;
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

    private boolean processTransformation(ServerLevel server) {
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
            if (result.isEmpty() || !canQueueAll(List.of(result))) return false;
            for (var slot : consumedSlots) inventory.extractItem(slot, 1, false);
            queueAll(List.of(result));
            return true;
        }
        return false;
    }

    private boolean processGrowth() {
        var dust = inventory.getStackInSlot(0);
        var sand = inventory.getStackInSlot(1);
        if (!sand.is(Items.SAND)) return false;
        ItemStack result;
        if (dust.is(AEItems.CERTUS_QUARTZ_DUST.asItem())) {
            result = new ItemStack(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem(), 2);
        } else if (dust.is(AEItems.FLUIX_DUST.asItem())) {
            result = new ItemStack(AEItems.FLUIX_CRYSTAL.asItem(), 2);
        } else {
            return false;
        }
        if (!canQueueAll(List.of(result))) return false;
        dust.shrink(1);
        sand.shrink(1);
        queueAll(List.of(result));
        return true;
    }

    public boolean acceptPattern(LazyPrimitivePattern pattern, KeyCounter[] inputHolder) {
        if (!isPatternProviderMode() || pattern.spec().machine() != kind() || isPatternBusy()) return false;
        var expected = pattern.spec().inputs();
        if (inputHolder.length != expected.size() || expected.size() > OUTPUT_START) return false;
        var stacks = new ArrayList<ItemStack>(expected.size());
        for (int slot = 0; slot < expected.size(); slot++) {
            var counter = inputHolder[slot];
            var input = expected.get(slot);
            if (counter == null || counter.size() != 1 || counter.get(input.key()) != input.amount()
                    || input.amount() > input.key().getMaxStackSize()) return false;
            stacks.add(input.key().toStack((int) input.amount()));
        }
        acceptingPatternInputs = true;
        try {
            for (int slot = 0; slot < stacks.size(); slot++) inventory.setStackInSlot(slot, stacks.get(slot));
        } finally {
            acceptingPatternInputs = false;
        }
        patternJobActive = true;
        progress = 0;
        setChanged();
        markForUpdate();
        return true;
    }

    public void refreshPatternProvider() {
        if (getMainNode().isReady()) ICraftingProvider.requestUpdate(getMainNode());
    }

    private void finishPatternJob() {
        var catalysts = new ArrayList<ItemStack>();
        for (int slot = 0; slot < OUTPUT_START; slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) catalysts.add(stack.copy());
        }
        if (!canQueueAll(catalysts)) return;
        for (int slot = 0; slot < OUTPUT_START; slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        queueAll(catalysts);
        patternJobActive = false;
        progress = 0;
    }

    private void evacuateInputs() {
        var pending = new ArrayList<ItemStack>();
        for (int slot = 0; slot < OUTPUT_START; slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) pending.add(stack.copy());
        }
        if (pending.isEmpty() || !canQueueAll(pending)) return;
        for (int slot = 0; slot < OUTPUT_START; slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        queueAll(pending);
    }

    private void processCompost() {
        var input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return;
        float chance = ComposterBlock.COMPOSTABLES.getFloat(input.getItem());
        if (chance <= 0) return;
        var result = CompostAccumulator.add(compostProgress, chance);
        if (result.completed() && !canQueueAll(List.of(new ItemStack(Items.BONE_MEAL)))) return;
        input.shrink(1);
        compostProgress = result.progress();
        if (result.completed()) queueAll(List.of(new ItemStack(Items.BONE_MEAL)));
    }

    private void updateFoundryStructure(ServerLevel server) {
        structureDirty = false;
        boolean next = true;
        for (int y = 0; y <= 1 && next; y++) {
            for (int z = 0; z <= 2 && next; z++) {
                for (int x = -1; x <= 1; x++) {
                    var offset = worldPosition.offset(x, y, z);
                    var expected = expectedFoundryBlock(x, y, z);
                    if (!server.getBlockState(offset).is(expected)) {
                        next = false;
                        break;
                    }
                }
            }
        }
        if (formed != next) {
            formed = next;
            progress = 0;
            setFoundryCoreActive(server, next);
            setChanged();
            markForUpdate();
        }
    }

    private void setFoundryCoreActive(ServerLevel server, boolean active) {
        for (int y = 0; y <= 1; y++) {
            var pos = worldPosition.offset(0, y, 1);
            var state = server.getBlockState(pos);
            if (state.is(ModContent.RESONANCE_CORE.get()) && state.getValue(ResonancePartBlock.ACTIVE) != active) {
                server.setBlockAndUpdate(pos, state.setValue(ResonancePartBlock.ACTIVE, active));
            }
        }
    }

    private Block expectedFoundryBlock(int x, int y, int z) {
        if (x == 0 && y == 0 && z == 0) return ModContent.RESONANCE_CONTROLLER.get();
        if (x == 0 && z == 1) return ModContent.RESONANCE_CORE.get();
        if (Math.abs(x) == 1 && (z == 0 || z == 2)) return ModContent.RESONANCE_COIL.get();
        return ModContent.RESONANCE_CASING.get();
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
        tag.putFloat("compostProgress", compostProgress);
        tag.putBoolean("formed", formed);
        tag.putBoolean("patternJobActive", patternJobActive);
        upgrades.writeToNBT(tag, "upgrades", registries);
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        progress = tag.getInt("progress");
        compostProgress = tag.getFloat("compostProgress");
        formed = tag.getBoolean("formed");
        patternJobActive = tag.getBoolean("patternJobActive");
        upgrades.readFromNBT(tag, "upgrades", registries);
        getMainNode().setIdlePowerUsage(2.0 * speedMultiplier() * speedMultiplier());
        structureDirty = true;
    }
}
