package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import java.util.EnumSet;
import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

public final class PressurePortBlockEntity extends AENetworkedBlockEntity {
    private static final int TRANSFER_PER_TICK = 200;
    private static final long CAPACITY_PROBE = Long.MAX_VALUE / 4;

    private final IAirHandlerMachine airHandler;
    private int lastPressureBucket = -1;

    public PressurePortBlockEntity(BlockPos pos, BlockState state) {
        super(PneumaticContent.PRESSURE_PORT_ENTITY.get(), pos, state);
        var port = (PressurePortBlock) state.getBlock();
        var factory = PneumaticRegistry.getInstance().getAirHandlerMachineFactory();
        airHandler = port.tier() == AirPressureTier.REINFORCED
                ? factory.createTierTwoAirHandler(1_000)
                : factory.createTierOneAirHandler(1_000);
        airHandler.setConnectableFaces(EnumSet.allOf(Direction.class));
        airHandler.enableSafetyVenting(pressure -> pressure >= tier().ratingBar(), Direction.UP);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(1.0);
    }

    public IAirHandlerMachine airHandler() { return airHandler; }
    public AirPressureTier tier() { return ((PressurePortBlock) getBlockState().getBlock()).tier(); }
    public PressurePortMode mode() { return ((PressurePortBlock) getBlockState().getBlock()).mode(); }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        airHandler.tick(this);
        if (getMainNode().isActive() && getMainNode().getGrid() != null) {
            if (mode() == PressurePortMode.IMPORT) importAir(); else exportAir();
        }
        int pressureBucket = Math.round(airHandler.getPressure() * 10.0f);
        if (pressureBucket != lastPressureBucket) {
            lastPressureBucket = pressureBucket;
            setChanged();
            markForUpdate();
        }
    }

    public BankSnapshot bankSnapshot() {
        if (!getMainNode().isActive() || getMainNode().getGrid() == null) return BankSnapshot.EMPTY;
        return bankSnapshot(getMainNode().getGrid().getStorageService().getInventory());
    }

    private BankSnapshot bankSnapshot(MEStorage storage) {
        var key = AirKey.of(tier());
        var source = IActionSource.ofMachine(this);
        long stored = storage.extract(key, CAPACITY_PROBE, Actionable.SIMULATE, source);
        long free = storage.insert(key, CAPACITY_PROBE, Actionable.SIMULATE, source);
        long capacity = saturatedAdd(stored, free);
        float pressure = AirBankMath.pressure(stored, capacity, tier().ratingBar());
        return new BankSnapshot(stored, capacity, pressure);
    }

    private void importAir() {
        var grid = getMainNode().getGrid();
        var storage = grid.getStorageService().getInventory();
        var bank = bankSnapshot(storage);
        float difference = airHandler.getPressure() - bank.pressureBar();
        if (difference <= 0.01f || airHandler.getAir() <= 0 || bank.capacity() <= bank.stored()) return;
        int pressureLimited = AirBankMath.equalizingTransfer(airHandler.getPressure(), bank.pressureBar(),
                airHandler.getVolume(), TRANSFER_PER_TICK, Math.min(airHandler.getAir(), bank.capacity() - bank.stored()));
        long requested = pressureLimited;
        var key = AirKey.of(tier());
        var source = IActionSource.ofMachine(this);
        long accepted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, key, requested,
                source, Actionable.SIMULATE);
        if (accepted <= 0) return;
        airHandler.addAir(-(int) accepted);
        long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, key, accepted,
                source, Actionable.MODULATE);
        if (inserted < accepted) airHandler.addAir((int) (accepted - inserted));
    }

    private void exportAir() {
        var grid = getMainNode().getGrid();
        var storage = grid.getStorageService().getInventory();
        var bank = bankSnapshot(storage);
        float difference = bank.pressureBar() - airHandler.getPressure();
        if (difference <= 0.01f || bank.stored() <= 0) return;
        int pressureLimited = AirBankMath.equalizingTransfer(bank.pressureBar(), airHandler.getPressure(),
                airHandler.getVolume(), TRANSFER_PER_TICK, bank.stored());
        long requested = pressureLimited;
        var key = AirKey.of(tier());
        var source = IActionSource.ofMachine(this);
        long available = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, key, requested,
                source, Actionable.SIMULATE);
        if (available <= 0) return;
        long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, key, available,
                source, Actionable.MODULATE);
        if (extracted > 0) airHandler.addAir((int) extracted);
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) return Long.MAX_VALUE;
        return first + second;
    }

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("air", airHandler.serializeNBT());
    }

    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        Tag saved = tag.get("air");
        if (saved instanceof CompoundTag compound) airHandler.deserializeNBT(compound);
    }

    public record BankSnapshot(long stored, long capacity, float pressureBar) {
        public static final BankSnapshot EMPTY = new BankSnapshot(0, 0, 0.0f);
    }
}
