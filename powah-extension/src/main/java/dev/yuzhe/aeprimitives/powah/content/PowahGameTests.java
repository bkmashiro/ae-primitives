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

    @GameTest(template = "empty")
    public static void activeVisualStateUsesConcreteInputAndQuantizedEnergyProgress(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.NIOTIC_EMITTER.get()));
        machine.startPlansForTest((ServerLevel) helper.getLevel());
        machine.energy().receiveEnergy(5000, false);
        machine.runExternalEnergyTickForTest();

        var clientCopy = new MeEnergizingChamberBlockEntity(pos, machine.getBlockState());
        clientCopy.onDataPacket(null, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(machine),
                helper.getLevel().registryAccess());
        helper.assertTrue(clientCopy.visualItem().is(Items.IRON_INGOT),
                "active renderer state did not preserve the concrete primary input");
        helper.assertTrue(Math.abs(clientCopy.visualProgress() - 0.5f) < 0.001f,
                "active renderer state did not expose paid versus required FE");

        var idle = new MeEnergizingChamberBlockEntity(pos, machine.getBlockState());
        idle.setLevel(helper.getLevel());
        clientCopy.onDataPacket(null, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(idle),
                helper.getLevel().registryAccess());
        helper.assertTrue(clientCopy.visualItem().isEmpty() && clientCopy.visualProgress() == 0,
                "inactive renderer update did not clear stale active presentation state");
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

    @GameTest(template = "empty")
    public static void packagedChamberPreservesEmitterAndRejectsOwnedState(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowahContent.ENERGIZING_CHAMBER.get());
        var machine = (MeEnergizingChamberBlockEntity) helper.getBlockEntity(pos);
        machine.inventory().setStackInSlot(17, new ItemStack(PowahContent.NIOTIC_EMITTER.get(), 2));
        helper.assertTrue(machine.canPackIntoMachineSpace(), "configured idle chamber rejected packaging");
        var configuration = machine.writeMachineSpaceConfiguration(helper.getLevel().registryAccess());
        var restored = new MeEnergizingChamberBlockEntity(pos, machine.getBlockState());
        helper.assertTrue(restored.restoreMachineSpaceConfiguration(configuration, helper.getLevel().registryAccess())
                        && restored.inventory().getStackInSlot(17).getCount() == 2,
                "packaged chamber lost its emitter configuration");
        machine.energy().receiveEnergy(1, false);
        helper.assertTrue(!machine.canPackIntoMachineSpace(), "chamber with owned FE was packaged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void factoryRunsPackagedChamberThroughExplicitFePort(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(3, 1, 3);
        helper.setBlock(factoryPos.above(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        BlockPos portPos = factoryPos.east();
        helper.setBlock(portPos, PowahContent.ENERGIZING_FACTORY_ENERGY_PORT.get());
        var port = (EnergizingFactoryEnergyPortBlockEntity) helper.getBlockEntity(portPos);
        port.energy().receiveEnergy(10000, false);
        var configuration = new CompoundTag();
        configuration.put("emitter", new ItemStack(PowahContent.NIOTIC_EMITTER.get()).saveOptional(
                helper.getLevel().registryAccess()));
        var envelope = dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope.capture(
                BuiltInRegistries.BLOCK.getKey(PowahContent.ENERGIZING_CHAMBER.get()),
                PowahContent.ENERGIZING_CHAMBER.get().defaultBlockState(), configuration);
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(factoryPos);
        factory.inventory().setStackInSlot(0, dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem.create(
                ModContent.MACHINE_SPACE_COMPONENT.get(), envelope));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0), new ItemStack(Items.IRON_INGOT));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 1), new ItemStack(Items.GOLD_INGOT));
        helper.succeedWhen(() -> {
            ItemStack output = factory.inventory().getStackInSlot(
                    dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(0, 0));
            helper.assertTrue(output.is(item("powah:steel_energized")) && output.getCount() == 2,
                    "packaged energizing chamber did not complete through the explicit FE port");
            helper.assertTrue(port.energy().getEnergyStored() == 0,
                    "packaged energizing chamber did not pay the exact recipe FE");
            var visual = factory.visualLanes().get(0);
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath("aeprimitives_powah", "me_energizing_chamber")
                            .equals(visual.machineId())
                            && visual.status() == dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.LaneStatus.WAITING_INPUT,
                    "factory visual snapshot did not preserve the completed Powah host machine status");
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
