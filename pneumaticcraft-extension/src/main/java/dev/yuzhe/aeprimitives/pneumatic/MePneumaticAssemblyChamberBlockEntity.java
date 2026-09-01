package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import me.desht.pneumaticcraft.api.crafting.recipe.PressureChamberRecipe;
import me.desht.pneumaticcraft.common.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class MePneumaticAssemblyChamberBlockEntity extends AENetworkedBlockEntity {
    public static final int INPUT_START = 0;
    public static final int INPUT_SLOTS = 6;
    public static final int OUTPUT_START = 6;
    public static final int OUTPUT_SLOTS = 6;
    public static final int HEAD_SLOT = 12;
    private static final long CAPACITY_PROBE = Long.MAX_VALUE / 4;

    private final ItemStackHandler inventory = new ItemStackHandler(13) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < OUTPUT_START) return true;
            return slot == HEAD_SLOT && stack.getItem() instanceof PneumaticAssemblyHeadItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private PneumaticAssemblyStatus status = PneumaticAssemblyStatus.IDLE;

    public MePneumaticAssemblyChamberBlockEntity(BlockPos pos, BlockState state) {
        super(PneumaticContent.PNEUMATIC_ASSEMBLY_CHAMBER_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(4.0);
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public boolean installHead(ItemStack offered) {
        if (!(offered.getItem() instanceof PneumaticAssemblyHeadItem)
                || !inventory.getStackInSlot(HEAD_SLOT).isEmpty()) return false;
        inventory.setStackInSlot(HEAD_SLOT, offered.copyWithCount(1));
        return true;
    }

    public Optional<ItemStack> removeHead() {
        var current = inventory.getStackInSlot(HEAD_SLOT);
        if (current.isEmpty()) return Optional.empty();
        inventory.setStackInSlot(HEAD_SLOT, ItemStack.EMPTY);
        return Optional.of(current.copy());
    }

    public PneumaticAssemblyStatus status() {
        return status;
    }

    public AirPressureTier installedTier() {
        ItemStack stack = inventory.getStackInSlot(HEAD_SLOT);
        return stack.getItem() instanceof PneumaticAssemblyHeadItem head ? head.tier() : null;
    }

    public PressurePortBlockEntity.BankSnapshot bankSnapshot() {
        if (!getMainNode().isActive() || getMainNode().getGrid() == null) {
            return PressurePortBlockEntity.BankSnapshot.EMPTY;
        }
        AirPressureTier tier = installedTier();
        if (tier == null) return PressurePortBlockEntity.BankSnapshot.EMPTY;
        var grid = getMainNode().getGrid();
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);
        long stored = storage.extract(AirKey.of(tier), CAPACITY_PROBE, Actionable.SIMULATE, source);
        long free = storage.insert(AirKey.of(tier), CAPACITY_PROBE, Actionable.SIMULATE, source);
        long capacity = Long.MAX_VALUE - stored < free ? Long.MAX_VALUE : stored + free;
        return new PressurePortBlockEntity.BankSnapshot(stored, capacity,
                AirBankMath.pressure(stored, capacity, tier.ratingBar()));
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        flushOutputsToMe();
        if (!getMainNode().isActive() || getMainNode().getGrid() == null) {
            status = PneumaticAssemblyStatus.NO_GRID;
            return;
        }
        if (installedTier() == null) {
            status = PneumaticAssemblyStatus.NO_HEAD;
            return;
        }
        processOneRecipe();
    }

    private void processOneRecipe() {
        var grid = getMainNode().getGrid();
        var source = IActionSource.ofMachine(this);
        var storage = grid.getStorageService().getInventory();
        var headTier = installedTier();
        var nextStatus = PneumaticAssemblyStatus.NO_RECIPE;

        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.PRESSURE_CHAMBER.get())) {
            PressureChamberRecipe recipe = holder.value();
            var workingInputs = copyInputs();
            var ingredientSlots = new IntArrayList(recipe.findIngredients(workingInputs));
            if (ingredientSlots.isEmpty()) continue;

            float requiredPressure = recipe.getCraftingPressure(workingInputs, ingredientSlots);
            var recipeTier = PneumaticAssemblyMath.tierFor(requiredPressure);
            if (recipeTier.isEmpty()) {
                nextStatus = PneumaticAssemblyStatus.UNSUPPORTED_PRESSURE;
                continue;
            }
            if (recipeTier.get() != headTier) {
                nextStatus = PneumaticAssemblyStatus.WRONG_HEAD;
                continue;
            }

            Plan plan = buildPlan(recipe, workingInputs, ingredientSlots, requiredPressure, headTier);
            if (plan == null) {
                nextStatus = PneumaticAssemblyStatus.OUTPUT_BLOCKED;
                continue;
            }

            var bank = bankSnapshot();
            if (!PneumaticAssemblyMath.canPayAtPressure(bank.stored(), bank.capacity(), plan.airCost,
                    plan.requiredPressure, plan.tier)) {
                nextStatus = PneumaticAssemblyStatus.INSUFFICIENT_AIR;
                continue;
            }
            long available = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, AirKey.of(plan.tier),
                    plan.airCost, source, Actionable.SIMULATE);
            if (available != plan.airCost) {
                nextStatus = PneumaticAssemblyStatus.INSUFFICIENT_AIR;
                continue;
            }
            long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, AirKey.of(plan.tier),
                    plan.airCost, source, Actionable.MODULATE);
            if (extracted != plan.airCost) {
                if (extracted > 0) {
                    storage.insert(AirKey.of(plan.tier), extracted, Actionable.MODULATE, source);
                }
                nextStatus = PneumaticAssemblyStatus.INSUFFICIENT_AIR;
                continue;
            }

            applyPlan(plan);
            status = PneumaticAssemblyStatus.WORKING;
            setChanged();
            return;
        }
        status = nextStatus;
    }

    private Plan buildPlan(PressureChamberRecipe recipe, ItemStackHandler workingInputs,
                           IntArrayList ingredientSlots, float requiredPressure, AirPressureTier tier) {
        List<ItemStack> before = snapshot(workingInputs, 0, INPUT_SLOTS);
        List<ItemStack> outputs = recipe.craftRecipe(workingInputs, ingredientSlots, false);
        if (outputs.isEmpty() || outputs.stream().anyMatch(ItemStack::isEmpty)) return null;

        long consumed = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack original = before.get(slot);
            ItemStack remaining = workingInputs.getStackInSlot(slot);
            if (!remaining.isEmpty() && !ItemStack.isSameItemSameComponents(original, remaining)) return null;
            if (remaining.getCount() > original.getCount()) return null;
            consumed += original.getCount() - remaining.getCount();
        }
        if (consumed <= 0) return null;

        long produced = 0;
        var workingOutputs = new ItemStackHandler(OUTPUT_SLOTS);
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            workingOutputs.setStackInSlot(slot, inventory.getStackInSlot(OUTPUT_START + slot).copy());
        }
        for (ItemStack output : outputs) {
            produced += output.getCount();
            if (!ItemHandlerHelper.insertItemStacked(workingOutputs, output.copy(), false).isEmpty()) return null;
        }
        long airCost = PneumaticAssemblyMath.airCost(consumed, produced);
        if (airCost <= 0) return null;
        return new Plan(snapshot(workingInputs, 0, INPUT_SLOTS), snapshot(workingOutputs, 0, OUTPUT_SLOTS),
                tier, requiredPressure, airCost);
    }

    private ItemStackHandler copyInputs() {
        var copy = new ItemStackHandler(INPUT_SLOTS);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            copy.setStackInSlot(slot, inventory.getStackInSlot(INPUT_START + slot).copy());
        }
        return copy;
    }

    private static List<ItemStack> snapshot(ItemStackHandler handler, int start, int count) {
        var result = new ArrayList<ItemStack>(count);
        for (int slot = 0; slot < count; slot++) result.add(handler.getStackInSlot(start + slot).copy());
        return List.copyOf(result);
    }

    private void applyPlan(Plan plan) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            inventory.setStackInSlot(INPUT_START + slot, plan.inputsAfter.get(slot).copy());
        }
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            inventory.setStackInSlot(OUTPUT_START + slot, plan.outputsAfter.get(slot).copy());
        }
    }

    private void flushOutputsToMe() {
        if (!getMainNode().isActive() || getMainNode().getGrid() == null) return;
        var grid = getMainNode().getGrid();
        var source = IActionSource.ofMachine(this);
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            int inventorySlot = OUTPUT_START + slot;
            ItemStack stack = inventory.getStackInSlot(inventorySlot);
            if (stack.isEmpty()) continue;
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    key, stack.getCount(), source, Actionable.MODULATE);
            if (inserted > 0) inventory.extractItem(inventorySlot, (int) inserted, false);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
    }

    private record Plan(List<ItemStack> inputsAfter, List<ItemStack> outputsAfter, AirPressureTier tier,
                        float requiredPressure, long airCost) {
    }

    public enum PneumaticAssemblyStatus {
        IDLE,
        NO_GRID,
        NO_HEAD,
        NO_RECIPE,
        WRONG_HEAD,
        UNSUPPORTED_PRESSURE,
        INSUFFICIENT_AIR,
        OUTPUT_BLOCKED,
        WORKING
    }
}
