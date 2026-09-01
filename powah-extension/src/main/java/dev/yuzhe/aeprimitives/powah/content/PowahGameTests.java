package dev.yuzhe.aeprimitives.powah.content;

import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.powah.AePrimitivesPowah;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AePrimitivesPowah.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowahGameTests {
    @GameTest(template = "empty")
    public static void energizingUsesExactEnergyAcrossIndependentSpatialLanes(GameTestHelper helper) {
        var pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        helper.setBlock(pos.east(), ModContent.ADVANCED_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 3));
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.NIOTIC_EMITTER.get(), 3));
        helper.assertTrue(machine.energy().receiveEnergy(Integer.MAX_VALUE, true) == 30000,
                "three niotic emitters must expose three times the real rod transfer limit");
        helper.assertTrue(machine.laneCountForTest() == 3, "advanced sidecar should expose three lanes");
        machine.startPlansForTest((ServerLevel) helper.getLevel());
        helper.assertTrue(machine.activePlansForTest() == 3, "three complete ingredient sets should become independent plans");
        helper.assertTrue(Math.abs(machine.totalRequiredFeForTest() - 30000.0) < 0.01,
                "three lanes must require three times the recipe energy");
        helper.assertTrue(machine.energy().receiveEnergy(29999, false) == 29999,
                "external FE buffer should accept energy within aggregate emitter throughput");
        machine.runExternalEnergyTickForTest();
        helper.assertTrue(count(machine, "powah:steel_energized") == 4,
                "only two independently funded lanes should complete");
        helper.assertTrue(machine.activePlansForTest() == 1, "the underfunded lane must remain queued");
        helper.assertTrue(machine.energy().receiveEnergy(1, false) == 1, "the final FE must come from the external connection");
        machine.runExternalEnergyTickForTest();
        helper.assertTrue(count(machine, "powah:steel_energized") == 6,
                "three exact recipe executions should emit six energized steel");
        helper.assertTrue(machine.activePlansForTest() == 0 && machine.energy().getEnergyStored() == 0,
                "all external FE must be accounted for exactly");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidOrExtraInputsDoNotReserveEnergy(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.DIRT));
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.BASIC_EMITTER.get()));
        machine.startPlansForTest((ServerLevel) helper.getLevel());
        helper.assertTrue(machine.activePlansForTest() == 0, "an unmatched extra slot created an energy plan");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1
                        && machine.inventory().getStackInSlot(1).getCount() == 1
                        && machine.inventory().getStackInSlot(2).getCount() == 1,
                "failed matching consumed an input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedOutputConsumesNothing(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.NIOTIC_EMITTER.get()));
        for (int slot = 6; slot < 17; slot++) machine.inventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        machine.startPlansForTest((ServerLevel) helper.getLevel());
        helper.assertTrue(machine.activePlansForTest() == 0, "blocked output created an energy plan");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1
                        && machine.inventory().getStackInSlot(1).getCount() == 1,
                "blocked output consumed recipe inputs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paidEnergyAndPendingOutputSurviveReload(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.NIOTIC_EMITTER.get()));
        machine.startPlansForTest((ServerLevel) helper.getLevel());
        machine.energy().receiveEnergy(5000, false);
        machine.runExternalEnergyTickForTest();
        helper.assertTrue(machine.totalPaidFeForTest() == 5000, "partial FE was not assigned to the plan");
        CompoundTag saved = new CompoundTag();
        machine.saveAdditional(saved, helper.getLevel().registryAccess());
        var restored = new MeEnergizingChamberBlockEntity(pos, machine.getBlockState());
        restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.activePlansForTest() == 1 && restored.totalPaidFeForTest() == 5000,
                "paid FE or pending output was lost across reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void completedRecipeReturnsThroughNormalMeStorage(GameTestHelper helper) {
        var pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        helper.setBlock(pos.above(), appeng.core.definitions.AEBlocks.ENERGY_CELL.block());
        BlockPos drivePos = pos.above(2);
        helper.setBlock(drivePos, appeng.core.definitions.AEBlocks.DRIVE.block());
        helper.setBlock(pos.above(3), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(drivePos);
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_64K.stack());
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.NIOTIC_EMITTER.get()));
        machine.energy().receiveEnergy(10000, false);
        Item output = item("powah:steel_energized");
        helper.onEachTick(() -> {
            var node = machine.getActionableNode();
            if (node == null || node.getGrid() == null) return;
            long stored = node.getGrid().getStorageService().getInventory().extract(
                    appeng.api.stacks.AEItemKey.of(output), 2, appeng.api.config.Actionable.SIMULATE,
                    appeng.api.networking.security.IActionSource.empty());
            if (stored == 2) helper.succeed();
        });
    }

    private static int count(MeEnergizingChamberBlockEntity machine, String id) {
        Item item = item(id);
        int total = 0;
        for (int slot = 6; slot < 17; slot++) {
            var stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
}
