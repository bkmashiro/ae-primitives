package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.GridHelper;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AePrimitivesPneumatic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PneumaticCraftGameTests {
    @GameTest(template = "empty")
    public static void pressurePortPersistsNativeAirState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PneumaticContent.REINFORCED_IMPORT.get());
        var port = helper.<PressurePortBlockEntity>getBlockEntity(pos);
        port.airHandler().setPressure(12.0f);
        var saved = port.saveWithoutMetadata(helper.getLevel().registryAccess());
        port.airHandler().setPressure(0.0f);
        port.loadTag(saved, helper.getLevel().registryAccess());
        helper.assertTrue(Math.abs(port.airHandler().getPressure() - 12.0f) < 0.01f,
                "pressure port did not restore its native air state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void normalDriveStoresAndExportsTieredCompressedAir(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(3, 1, 3);
        BlockPos importPos = drivePos.west(2);
        BlockPos exportPos = drivePos.east(2);

        helper.setBlock(importPos, PneumaticContent.BASIC_IMPORT.get());
        helper.setBlock(drivePos.west(), AEBlocks.ENERGY_CELL.block());
        helper.setBlock(exportPos, PneumaticContent.BASIC_EXPORT.get());
        helper.setBlock(drivePos.east(), AEBlocks.ENERGY_CELL.block());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        helper.setBlock(drivePos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());

        var drive = helper.<DriveBlockEntity>getBlockEntity(drivePos);
        var importer = helper.<PressurePortBlockEntity>getBlockEntity(importPos);
        var exporter = helper.<PressurePortBlockEntity>getBlockEntity(exportPos);
        var fixtureConnected = new AtomicBoolean();
        var cellInstalled = new AtomicBoolean();
        var imported = new AtomicBoolean();
        var ticks = new AtomicInteger();

        helper.onEachTick(() -> {
            int tick = ticks.incrementAndGet();
            var node = importer.getActionableNode();
            if (node == null || node.getGrid() == null) {
                if (tick == 220) helper.fail("pressure import port never joined an active ME grid");
                return;
            }
            if (!fixtureConnected.get() && drive.getMainNode().getNode() != null
                    && exporter.getMainNode().getNode() != null) {
                if (drive.getMainNode().getGrid() != node.getGrid()) {
                    GridHelper.createConnection(node, drive.getMainNode().getNode());
                }
                if (exporter.getMainNode().getGrid() != node.getGrid()) {
                    GridHelper.createConnection(node, exporter.getMainNode().getNode());
                }
                fixtureConnected.set(true);
                return;
            }
            if (!cellInstalled.get() && drive.getMainNode().isOnline()) {
                drive.getInternalInventory().setItemDirect(0,
                        new ItemStack(PneumaticContent.BASIC_AIR_CELL.get()));
                drive.onChangeInventory(null, 0);
                importer.airHandler().setPressure(4.0f);
                cellInstalled.set(true);
                return;
            }
            var storage = node.getGrid().getStorageService().getInventory();
            long stored = storage.extract(AirKey.of(AirPressureTier.BASIC), Long.MAX_VALUE,
                    Actionable.SIMULATE, IActionSource.empty());
            if (!imported.get() && stored >= 100) {
                imported.set(true);
                importer.airHandler().setPressure(0);
            }
            if (imported.get() && exporter.airHandler().getPressure() > 0.05f) {
                helper.assertTrue(exporter.airHandler().getAir() > 0,
                        "export port reported pressure without receiving air from ME storage");
                helper.assertTrue(storage.extract(AirKey.of(AirPressureTier.REINFORCED), Long.MAX_VALUE,
                                Actionable.SIMULATE, IActionSource.empty()) == 0,
                        "basic storage cell accepted reinforced compressed air");
                helper.succeed();
            }
            if (tick == 220) {
                var bank = importer.bankSnapshot();
                var cellStack = drive.getInternalInventory().getStackInSlot(0);
                var cell = appeng.api.storage.StorageCells.getCellInventory(cellStack, null);
                long directCapacity = cell == null ? -1 : cell.insert(AirKey.of(AirPressureTier.BASIC),
                        Long.MAX_VALUE / 4, Actionable.SIMULATE, IActionSource.empty());
                helper.fail("air bridge stalled: input=" + importer.airHandler().getAir()
                        + "/" + importer.airHandler().getPressure()
                        + ", stored=" + bank.stored() + ", capacity=" + bank.capacity()
                        + ", bankPressure=" + bank.pressureBar()
                        + ", output=" + exporter.airHandler().getAir() + "/" + exporter.airHandler().getPressure()
                        + ", driveOnline=" + drive.getMainNode().isOnline()
                        + ", sameGrid=" + (drive.getMainNode().getGrid() == node.getGrid())
                        + ", driveCell=" + cellStack
                        + ", handled=" + appeng.api.storage.StorageCells.isCellHandled(cellStack)
                        + ", directCapacity=" + directCapacity);
            }
        });
    }

    @GameTest(template = "empty")
    public static void airCellsEnforceTheirPressureDomain(GameTestHelper helper) {
        var basic = PneumaticContent.BASIC_AIR_CELL.get();
        var reinforced = PneumaticContent.REINFORCED_AIR_CELL.get();
        helper.assertTrue(!basic.isBlackListed(basic.getDefaultInstance(), AirKey.of(AirPressureTier.BASIC)),
                "basic air cell rejected basic compressed air");
        helper.assertTrue(basic.isBlackListed(basic.getDefaultInstance(), AirKey.of(AirPressureTier.REINFORCED)),
                "basic air cell accepted reinforced compressed air");
        helper.assertTrue(reinforced.isBlackListed(reinforced.getDefaultInstance(), AirKey.of(AirPressureTier.BASIC)),
                "reinforced air cell mixed the basic pressure domain");
        helper.assertTrue(!reinforced.isBlackListed(reinforced.getDefaultInstance(), AirKey.of(AirPressureTier.REINFORCED)),
                "reinforced air cell rejected reinforced compressed air");
        helper.succeed();
    }

    private PneumaticCraftGameTests() {
    }
}
