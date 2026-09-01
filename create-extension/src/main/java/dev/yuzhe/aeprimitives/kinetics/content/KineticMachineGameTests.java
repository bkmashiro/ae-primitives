package dev.yuzhe.aeprimitives.kinetics.content;

import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.kinetics.AePrimitivesKinetics;
import dev.yuzhe.aeprimitives.kinetics.compat.create.CreateSequenceImporter;
import dev.yuzhe.aeprimitives.operation.BoundOperationPattern;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AePrimitivesKinetics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KineticMachineGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void pressRunsCreatePressingRecipe(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_PRESS.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "ME press did not accept Create's iron pressing recipe");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "ME press did not consume its input");
        helper.assertTrue(hasQueuedOutput(machine), "ME press did not queue the pressed result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crusherRunsCreateCrushingRecipe(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_CRUSHER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "ME crusher did not accept Create's raw iron crushing recipe");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "ME crusher did not consume its input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterCatalystRunsCreateSplashingRecipe(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_CATALYST_CHAMBER.get());
        var remainder = machine.installCatalyst(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(remainder.isPresent() && remainder.get().is(Items.BUCKET),
                "Water catalyst did not return an empty bucket");
        machine.inventory().setStackInSlot(0, new ItemStack(Items.GRAVEL));
        helper.assertTrue(machine.completeCycle(helper.getLevel()),
                "Catalyst chamber did not accept Create's splashing recipe");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(),
                "Catalyst chamber did not consume its recipe input");
        helper.assertTrue(machine.catalystId().isPresent(), "Catalyst was consumed as recipe input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsupportedCatalystDoesNotMutateChamber(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_CATALYST_CHAMBER.get());
        helper.assertTrue(machine.installCatalyst(new ItemStack(Items.DIAMOND)).isEmpty(),
                "Unsupported item was accepted as a catalyst");
        helper.assertTrue(machine.catalystId().isEmpty(), "Rejected catalyst changed chamber state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullOutputBufferDoesNotConsumeInput(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_PRESS.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        for (int slot = 1; slot < machine.inventory().getSlots(); slot++) {
            machine.inventory().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.assertTrue(!machine.completeCycle(helper.getLevel()), "ME press accepted work with no output space");
        helper.assertTrue(machine.inventory().getStackInSlot(0).is(Items.IRON_INGOT), "ME press consumed blocked input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void advancedSidecarIsTierMatchedAndOneSided(GameTestHelper helper) {
        var owner = place(helper, KineticsContent.ME_PRESS.get());
        var otherPos = MACHINE.offset(2, 0, 0);
        helper.setBlock(otherPos, KineticsContent.ME_PRESS.get());
        var other = helper.<KineticMachineBlockEntity>getBlockEntity(otherPos);
        var sidecarPos = MACHINE.offset(1, 0, 0);
        helper.setBlock(sidecarPos, ModContent.ADVANCED_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        helper.assertTrue(owner.parallelLanes() == 3, "Advanced sidecar did not add two owner lanes");
        helper.assertTrue(other.parallelLanes() == 1, "One sidecar bound to both adjacent machines");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mismatchedAndRemovedSidecarsDoNotContribute(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_PRESS.get());
        var sidecarPos = MACHINE.offset(1, 0, 0);
        helper.setBlock(sidecarPos, ModContent.BASIC_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        helper.assertTrue(machine.parallelLanes() == 1, "Basic sidecar accelerated an advanced machine");
        helper.setBlock(sidecarPos, ModContent.ADVANCED_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        helper.assertTrue(machine.parallelLanes() == 3, "Replacement did not invalidate owner topology");
        helper.destroyBlock(sidecarPos);
        helper.assertTrue(machine.parallelLanes() == 1, "Removed sidecar remained cached");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spatialLanesRunPressIndependently(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_PRESS.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "ME press did not complete three independent lanes");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "ME press did not consume one input per lane");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spatialLanesRunCrusherIndependently(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_CRUSHER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.DIORITE, 3));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "ME crusher did not complete three independent lanes");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "ME crusher did not consume one input per lane");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spatialLanesRunCatalystChamberIndependently(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_CATALYST_CHAMBER.get());
        machine.installCatalyst(new ItemStack(Items.WATER_BUCKET));
        machine.inventory().setStackInSlot(0, new ItemStack(Items.GRAVEL, 3));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "Catalyst chamber did not complete three independent lanes");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "Catalyst chamber did not consume one input per lane");
        helper.assertTrue(machine.catalystId().isPresent(), "Parallel lanes consumed the shared catalyst");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void basinRunsItemMixingAndCompactingRecipes(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_BASIN_PROCESSOR.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.ANDESITE));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.IRON_NUGGET));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Basin did not mix andesite alloy");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty()
                && machine.inventory().getStackInSlot(1).isEmpty(), "Basin did not consume mixing ingredients");
        for (int slot = 0; slot < 9; slot++) machine.inventory().setStackInSlot(slot, new ItemStack(Items.SNOW_BLOCK));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Basin did not compact nine snow blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void basinConsumesAndProducesFluids(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_BASIN_PROCESSOR.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.DIRT));
        machine.fluids().fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Basin did not run a fluid-input mud recipe");
        helper.assertTrue(machine.fluids().getFluidInTank(0).isEmpty(), "Basin did not consume recipe fluid");

        machine.inventory().setStackInSlot(0, new ItemStack(Items.HONEY_BLOCK));
        helper.setBlock(MACHINE.below(), BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("create", "blaze_burner"))
                .defaultBlockState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Heated Basin recipe did not run above a kindled burner");
        var fluidOutput = machine.fluids().drain(1000, IFluidHandler.FluidAction.SIMULATE);
        helper.assertTrue(!fluidOutput.isEmpty() && fluidOutput.getAmount() == 1000,
                "Basin did not retain its fluid result for network extraction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void basinHonorsCreateHeatAndSpatialLanes(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_BASIN_PROCESSOR.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
        machine.inventory().setStackInSlot(1, new ItemStack(BuiltInRegistries.ITEM
                .get(ResourceLocation.fromNamespaceAndPath("create", "zinc_ingot"))));
        helper.assertTrue(!machine.completeCycle(helper.getLevel()), "Heated brass recipe ran without a heat source");
        helper.setBlock(MACHINE.below(), BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("create", "blaze_burner"))
                .defaultBlockState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Basin ignored a valid Create heat source");

        machine.inventory().setStackInSlot(0, new ItemStack(Items.ANDESITE, 3));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.IRON_NUGGET, 3));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "Basin did not execute three independently committed spatial lanes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fillingStationFillsAcrossSpatialLanes(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_FILLING_STATION.get());
        var honey = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("create", "honey"));
        machine.inventory().setStackInSlot(0, new ItemStack(Items.GLASS_BOTTLE, 3));
        machine.fluids().fill(new FluidStack(honey, 750), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "Filling station did not complete three independent lanes");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(),
                "Filling station did not consume one container per lane");
        helper.assertTrue(machine.fluids().getFluidInTank(0).isEmpty(),
                "Filling station did not consume fluid linearly with lane count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fillingStationEmptiesContainersToFluidOutput(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_FILLING_STATION.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.HONEY_BOTTLE));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Filling station did not run an Emptying recipe");
        var output = machine.fluids().drain(250, IFluidHandler.FluidAction.SIMULATE);
        helper.assertTrue(output.getAmount() == 250
                        && BuiltInRegistries.FLUID.getKey(output.getFluid()).equals(
                                ResourceLocation.fromNamespaceAndPath("create", "honey")),
                "Emptying recipe did not retain its fluid output");
        helper.assertTrue(hasQueuedOutput(machine), "Emptying recipe did not retain its container result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deployerConsumesHeldIngredientsAcrossSpatialLanes(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_DEPLOYER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM
                .get(ResourceLocation.fromNamespaceAndPath("create", "shaft")), 3));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.OAK_PLANKS, 3));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "Deployer did not execute three independent spatial lanes");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty()
                        && machine.inventory().getStackInSlot(1).isEmpty(),
                "Deployer did not consume processed and held ingredients linearly");
        helper.assertTrue(countOutput(machine, BuiltInRegistries.ITEM
                        .get(ResourceLocation.fromNamespaceAndPath("create", "cogwheel"))) == 3,
                "Deployer did not queue one result per lane");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deployerPreservesKeptToolIdentity(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_DEPLOYER.get());
        var axe = new ItemStack(Items.DIAMOND_AXE);
        axe.setDamageValue(17);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.EXPOSED_COPPER_GRATE));
        machine.inventory().setStackInSlot(1, axe.copy());
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "Deployer did not run a tool application recipe");
        helper.assertTrue(ItemStack.isSameItemSameComponents(machine.inventory().getStackInSlot(1), axe)
                        && machine.inventory().getStackInSlot(1).getDamageValue() == 17,
                "Keep-held recipe changed the installed tool identity or durability");
        helper.assertTrue(countOutput(machine, Items.COPPER_GRATE) == 1,
                "Deployer did not queue the tool application result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void importedSequencesCompleteThroughOperationMachines(GameTestHelper helper) {
        var press = placeAt(helper, new BlockPos(1, 1, 1), KineticsContent.ME_PRESS.get());
        var filling = placeAt(helper, new BlockPos(3, 1, 1), KineticsContent.ME_FILLING_STATION.get());
        var deployer = placeAt(helper, new BlockPos(5, 1, 1), KineticsContent.ME_DEPLOYER.get());
        for (var machine : java.util.List.of(press, filling, deployer)) {
            helper.assertTrue(appeng.api.implementations.blockentities.ICraftingMachine.of(
                            helper.getLevel(), machine.getBlockPos(), Direction.NORTH) == machine,
                    "AE could not discover the operation machine target");
        }

        var sturdy = importSequence(helper, ResourceLocation.fromNamespaceAndPath("create", "sequenced_assembly/sturdy_sheet"));
        runImportedSequence(helper, sturdy.steps(), press, filling, deployer);
        var track = importSequence(helper, ResourceLocation.fromNamespaceAndPath("create", "sequenced_assembly/track"));
        runImportedSequence(helper, track.steps(), press, filling, deployer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dispatchedOperationsUseIndependentSpatialLanes(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_PRESS.get());
        var input = new dev.yuzhe.aeprimitives.operation.OperationInput(
                java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.IRON_INGOT), 1)), null);
        var step = new OperationStepSpec(
                ResourceLocation.fromNamespaceAndPath("aeprimitives_kinetics", "parallel_press_test"),
                com.simibubi.create.AllRecipeTypes.PRESSING.getId(),
                java.util.List.of(input),
                java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.GOLD_INGOT), 1)));
        var pattern = new BoundOperationPattern(step, ModContent.OPERATION_PATTERN.get());
        for (int lane = 0; lane < 3; lane++) {
            var holders = holdersFor(step);
            helper.assertTrue(machine.pushPattern(pattern, holders, Direction.NORTH),
                    "Spatial lane " + lane + " rejected an independent operation");
        }
        helper.assertTrue(!machine.pushPattern(pattern, holdersFor(step), Direction.NORTH),
                "Machine accepted more dispatched operations than its spatial lane count");
        helper.assertTrue(machine.calculateStressApplied() == KineticMachineKind.PRESS.stressImpact() * 3,
                "Dispatched operation stress did not scale with active lanes");
        helper.assertTrue(machine.completeDispatchedPlans() == 3,
                "Spatial lanes did not complete independently");
        helper.assertTrue(countAnySlot(machine, Items.GOLD_INGOT) == 3,
                "Spatial lanes did not preserve one output per operation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void secondaryMachinesRunTheirCreateRecipes(GameTestHelper helper) {
        var saw = placeAt(helper, new BlockPos(1, 1, 1), KineticsContent.ME_SAW.get());
        saw.inventory().setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "andesite_alloy"))));
        helper.assertTrue(saw.completeCycle(helper.getLevel()), "ME saw did not run Create cutting");
        helper.assertTrue(countAnySlot(saw, BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "shaft"))) == 6,
                "ME saw did not preserve the cutting recipe count");

        var mill = placeAt(helper, new BlockPos(3, 1, 1), KineticsContent.ME_MILL.get());
        mill.inventory().setStackInSlot(0, new ItemStack(Items.DRIPSTONE_BLOCK));
        helper.assertTrue(mill.completeCycle(helper.getLevel()), "ME mill did not run Create milling");
        helper.assertTrue(countAnySlot(mill, Items.CLAY_BALL) == 1,
                "ME mill did not retain the milling result");

        var polisher = placeAt(helper, new BlockPos(5, 1, 1), KineticsContent.ME_POLISHER.get());
        polisher.inventory().setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "rose_quartz"))));
        helper.assertTrue(polisher.completeCycle(helper.getLevel()), "ME polisher did not run Create polishing");
        helper.assertTrue(countAnySlot(polisher, BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "polished_rose_quartz"))) == 1,
                "ME polisher did not retain the polishing result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void millUsesIndependentSpatialLanes(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_MILL.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.RED_TULIP, 3));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "ME mill did not execute three independent milling lanes");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(),
                "ME mill did not consume one input per lane");
        helper.assertTrue(countAnySlot(machine, Items.RED_DYE) >= 6,
                "ME mill did not preserve guaranteed output per independent lane");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void secondaryOperationsDispatchOnlyToMatchingMachines(GameTestHelper helper) {
        var machines = java.util.List.of(
                placeAt(helper, new BlockPos(1, 1, 1), KineticsContent.ME_SAW.get()),
                placeAt(helper, new BlockPos(3, 1, 1), KineticsContent.ME_MILL.get()),
                placeAt(helper, new BlockPos(5, 1, 1), KineticsContent.ME_POLISHER.get()));
        var kinds = java.util.List.of(KineticMachineKind.SAW, KineticMachineKind.MILL, KineticMachineKind.POLISHER);
        for (int index = 0; index < machines.size(); index++) {
            var machine = machines.get(index);
            var kind = kinds.get(index);
            var input = new dev.yuzhe.aeprimitives.operation.OperationInput(
                    java.util.List.of(new appeng.api.stacks.GenericStack(
                            appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), 1)), null);
            var step = new OperationStepSpec(
                    ResourceLocation.fromNamespaceAndPath("aeprimitives_kinetics", "secondary_" + kind.id()),
                    kind.recipeType().getId(), java.util.List.of(input),
                    java.util.List.of(new appeng.api.stacks.GenericStack(
                            appeng.api.stacks.AEItemKey.of(Items.STONE), 1)));
            helper.assertTrue(machine.pushPattern(
                            new BoundOperationPattern(step, ModContent.OPERATION_PATTERN.get()),
                            holdersFor(step), Direction.NORTH),
                    kind + " rejected its matching operation pattern");
            helper.assertTrue(machine.completeDispatchedPlans() == 1 && countAnySlot(machine, Items.STONE) == 1,
                    kind + " did not complete its dispatched operation");
            helper.assertTrue(!kind.acceptsOperation(com.simibubi.create.AllRecipeTypes.CRUSHING.getId()),
                    kind + " incorrectly collapsed crushing into its operation family");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void activeLanesScaleStressLinearly(GameTestHelper helper) {
        var machine = parallelMachine(helper, KineticsContent.ME_PRESS.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        machine.tick();
        helper.assertTrue(machine.activeLanes() == 3, "Machine did not expose three active lanes");
        helper.assertTrue(machine.calculateStressApplied() == KineticMachineKind.PRESS.stressImpact() * 3,
                "Create stress did not scale linearly with active lanes");
        helper.succeed();
    }

    private static KineticMachineBlockEntity parallelMachine(GameTestHelper helper, KineticMachineBlock block) {
        var machine = place(helper, block);
        helper.setBlock(MACHINE.offset(1, 0, 0), ModContent.ADVANCED_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        return machine;
    }

    @GameTest(template = "empty")
    public static void idlePressCanBePackagedButBusyPressCannot(GameTestHelper helper) {
        var press = place(helper, KineticsContent.ME_PRESS.get());
        helper.assertTrue(press.canPackIntoMachineSpace(), "idle ME press rejected machine-space packaging");
        press.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(!press.canPackIntoMachineSpace(), "press with inventory was packaged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crusherAndConfiguredCatalystChamberCanBePackaged(GameTestHelper helper) {
        var crusher = placeAt(helper, new BlockPos(1, 1, 1), KineticsContent.ME_CRUSHER.get());
        helper.assertTrue(crusher.canPackIntoMachineSpace(), "idle ME crusher rejected machine-space packaging");

        var chamber = placeAt(helper, new BlockPos(3, 1, 1), KineticsContent.ME_CATALYST_CHAMBER.get());
        chamber.installCatalyst(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(chamber.canPackIntoMachineSpace(), "configured catalyst chamber rejected packaging");
        var configuration = chamber.writeMachineSpaceConfiguration(helper.getLevel().registryAccess());
        helper.assertTrue(configuration.contains("catalystId") && configuration.contains("catalystStack"),
                "packaged catalyst chamber lost catalyst identity");

        var restored = placeAt(helper, new BlockPos(5, 1, 1), KineticsContent.ME_CATALYST_CHAMBER.get());
        helper.assertTrue(restored.restoreMachineSpaceConfiguration(configuration, helper.getLevel().registryAccess()),
                "catalyst configuration could not be restored");
        helper.assertTrue(restored.catalystId().equals(chamber.catalystId())
                        && ItemStack.isSameItemSameComponents(restored.catalystStack(), chamber.catalystStack()),
                "restored catalyst chamber changed its catalyst configuration");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void factoryRunsPackagedPressThroughKineticPort(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(1, 1, 1);
        BlockPos portPos = factoryPos.east();
        helper.setBlock(factoryPos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        helper.setBlock(portPos, KineticsContent.FACTORY_PORT.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(factoryPos);
        var port = helper.<KineticFactoryPortBlockEntity>getBlockEntity(portPos);
        port.setSpeed(64);
        factory.inventory().setStackInSlot(0, pressComponent(helper));
        factory.inventory().setStackInSlot(
                dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0),
                new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> {
            port.setSpeed(64);
            factory.scheduleExternalWork();
            for (int tick = 0; tick < 70; tick++) factory.serverTick();
            var output = factory.inventory().getStackInSlot(
                    dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(0, 0));
            helper.assertTrue(output.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "iron_sheet"))),
                    "packaged press produced no iron sheet; active=" + factory.getMainNode().isActive()
                            + ", scheduled=" + factory.isScheduled() + ", progress=" + factory.laneProgress(0)
                            + ", portSpeed=" + port.getSpeed() + ", portLanes=" + port.activeLaneCount()
                            + ", output=" + output);
            helper.assertTrue(factory.inventory().getStackInSlot(
                            dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0)).isEmpty(),
                    "packaged press did not consume one input");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void packagedPressWaitsWithoutKineticPort(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(1, 1, 1);
        helper.setBlock(factoryPos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(factoryPos);
        factory.inventory().setStackInSlot(0, pressComponent(helper));
        int inputSlot = dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0);
        factory.inventory().setStackInSlot(inputSlot, new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> {
            helper.assertTrue(factory.getMainNode().isActive(), "factory ME node did not become active");
            factory.scheduleExternalWork();
            factory.serverTick();
            helper.assertTrue(factory.laneProgress(0) == 0 && factory.inventory().getStackInSlot(inputSlot).is(Items.IRON_INGOT),
                    "packaged press ran without a kinetic resource port");
            helper.assertTrue(factory.menuData().get(2)
                            == dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.LaneStatus.WAITING_RESOURCE.ordinal(),
                    "factory did not expose the missing kinetic resource state");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void packagedPressStressScalesPerActiveFactoryLane(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(1, 1, 1);
        BlockPos portPos = factoryPos.east();
        helper.setBlock(factoryPos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        helper.setBlock(portPos, KineticsContent.FACTORY_PORT.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(factoryPos);
        var port = helper.<KineticFactoryPortBlockEntity>getBlockEntity(portPos);
        port.setSpeed(16);
        for (int lane = 0; lane < 2; lane++) {
            factory.inventory().setStackInSlot(lane, pressComponent(helper));
            factory.inventory().setStackInSlot(
                    dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(lane, 0),
                    new ItemStack(Items.IRON_INGOT));
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(port.activeLaneCount() == 2, "factory port did not track two independent active lanes");
            helper.assertTrue(port.calculateStressApplied() == KineticMachineKind.PRESS.stressImpact() * 2,
                    "packaged lane stress did not scale linearly");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void factoryRunsPackagedCrusherAndCatalystChamber(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(1, 1, 1);
        BlockPos portPos = factoryPos.east();
        helper.setBlock(factoryPos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        helper.setBlock(portPos, KineticsContent.FACTORY_PORT.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(factoryPos);
        var port = helper.<KineticFactoryPortBlockEntity>getBlockEntity(portPos);
        port.setSpeed(64);
        factory.inventory().setStackInSlot(0, machineComponent(helper, KineticsContent.ME_CRUSHER.get(), null));

        var chamber = placeAt(helper, new BlockPos(5, 1, 1), KineticsContent.ME_CATALYST_CHAMBER.get());
        chamber.installCatalyst(new ItemStack(Items.WATER_BUCKET));
        factory.inventory().setStackInSlot(1, machineComponent(helper, KineticsContent.ME_CATALYST_CHAMBER.get(), chamber));
        factory.inventory().setStackInSlot(
                dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0),
                new ItemStack(Items.RAW_IRON));
        factory.inventory().setStackInSlot(
                dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(1, 0),
                new ItemStack(Items.MAGMA_BLOCK));

        helper.succeedWhen(() -> {
            port.setSpeed(64);
            factory.scheduleExternalWork();
            for (int tick = 0; tick < 70; tick++) factory.serverTick();
            helper.assertTrue(factory.inventory().getStackInSlot(
                            dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0)).isEmpty(),
                    "packaged crusher did not consume its input");
            helper.assertTrue(factory.inventory().getStackInSlot(
                            dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(1, 0)).isEmpty(),
                    "packaged catalyst chamber did not consume its input");
            helper.assertTrue(!factory.inventory().getStackInSlot(
                            dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(0, 0)).isEmpty(),
                    "packaged crusher produced no result");
            helper.assertTrue(!factory.inventory().getStackInSlot(
                            dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(1, 0)).isEmpty(),
                    "packaged catalyst chamber produced no result");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void factoryPortSumsMixedKineticLaneStress(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(1, 1, 1);
        BlockPos portPos = factoryPos.east();
        helper.setBlock(factoryPos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        helper.setBlock(portPos, KineticsContent.FACTORY_PORT.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(factoryPos);
        var port = helper.<KineticFactoryPortBlockEntity>getBlockEntity(portPos);
        port.setSpeed(16);
        factory.inventory().setStackInSlot(0, pressComponent(helper));
        factory.inventory().setStackInSlot(1, machineComponent(helper, KineticsContent.ME_CRUSHER.get(), null));
        factory.inventory().setStackInSlot(
                dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0),
                new ItemStack(Items.IRON_INGOT));
        factory.inventory().setStackInSlot(
                dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(1, 0),
                new ItemStack(Items.RAW_IRON));
        helper.succeedWhen(() -> {
            helper.assertTrue(port.activeLaneCount() == 2, "factory port did not track both mixed lanes");
            helper.assertTrue(port.calculateStressApplied()
                            == KineticMachineKind.PRESS.stressImpact() + KineticMachineKind.CRUSHER.stressImpact(),
                    "factory port did not sum each machine kind's stress");
        });
    }

    @GameTest(template = "empty")
    public static void factoryCrusherLanesRollProbabilityIndependently(GameTestHelper helper) {
        var component = machineComponent(helper, KineticsContent.ME_CRUSHER.get(), null);
        var envelope = dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem.read(component);
        var inputs = new net.neoforged.neoforge.items.ItemStackHandler[] {
                new net.neoforged.neoforge.items.ItemStackHandler(3),
                new net.neoforged.neoforge.items.ItemStackHandler(3)
        };
        for (var laneInputs : inputs) laneInputs.setStackInSlot(0, new ItemStack(Items.RAW_IRON));

        long seed = 78431L;
        var recipe = KineticProcessBehavior.CreateRecipe.findRecipe(
                KineticMachineKind.CRUSHER, helper.getLevel(), new ItemStack(Items.RAW_IRON));
        var expectedRandom = net.minecraft.util.RandomSource.create(seed);
        var bonus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:experience_nugget"));
        int expectedBonus = 0;
        for (int lane = 0; lane < 2; lane++) {
            for (ItemStack result : recipe.rollResults(expectedRandom)) if (result.is(bonus)) expectedBonus += result.getCount();
        }

        helper.getLevel().random.setSeed(seed);
        int actualBonus = 0;
        for (int lane = 0; lane < 2; lane++) {
            var context = new dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutor.LaneContext(
                    helper.getLevel(), MACHINE, lane, envelope, inputs[lane]);
            var plan = KineticVirtualLaneExecutor.INSTANCE.prepare(context);
            helper.assertTrue(plan != null, "crusher lane did not resolve its recipe");
            for (ItemStack result : plan.complete(inputs[lane])) if (result.is(bonus)) actualBonus += result.getCount();
            helper.assertTrue(inputs[lane].getStackInSlot(0).isEmpty(), "crusher lane did not consume exactly one input");
        }
        helper.assertTrue(actualBonus == expectedBonus,
                "executor did not perform one sequential probability roll per completed crusher lane");
        helper.succeed();
    }

    private static ItemStack pressComponent(GameTestHelper helper) {
        return machineComponent(helper, KineticsContent.ME_PRESS.get(), null);
    }

    private static ItemStack machineComponent(
            GameTestHelper helper, KineticMachineBlock block, KineticMachineBlockEntity configuredMachine) {
        var configuration = configuredMachine == null
                ? new net.minecraft.nbt.CompoundTag()
                : configuredMachine.writeMachineSpaceConfiguration(helper.getLevel().registryAccess());
        configuration.putString("kind", block.kind().id());
        var envelope = dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope.capture(
                BuiltInRegistries.BLOCK.getKey(block), block.defaultBlockState(), configuration);
        return dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem.create(
                ModContent.MACHINE_SPACE_COMPONENT.get(), envelope);
    }

    private static KineticMachineBlockEntity place(GameTestHelper helper, KineticMachineBlock block) {
        helper.setBlock(MACHINE, block);
        return helper.getBlockEntity(MACHINE);
    }

    private static KineticMachineBlockEntity placeAt(
            GameTestHelper helper, BlockPos pos, KineticMachineBlock block) {
        helper.setBlock(pos, block);
        return helper.getBlockEntity(pos);
    }

    private static dev.yuzhe.aeprimitives.sequence.SequencePatternSpec importSequence(
            GameTestHelper helper, ResourceLocation id) {
        var holder = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        helper.assertTrue(holder != null && holder.value() instanceof SequencedAssemblyRecipe,
                "Missing Create sequenced assembly recipe " + id);
        var result = CreateSequenceImporter.compile(id, (SequencedAssemblyRecipe) holder.value());
        helper.assertTrue(result.successful(), "Could not import " + id + ": " + result.error());
        return result.sequence();
    }

    private static void runImportedSequence(
            GameTestHelper helper,
            java.util.List<OperationStepSpec> steps,
            KineticMachineBlockEntity press,
            KineticMachineBlockEntity filling,
            KineticMachineBlockEntity deployer) {
        appeng.api.stacks.GenericStack previous = null;
        for (var step : steps) {
            var holders = new KeyCounter[step.inputs().size()];
            for (int input = 0; input < holders.length; input++) {
                var selected = input == 0 && previous != null
                        ? previous
                        : step.inputs().get(input).alternatives().getFirst();
                holders[input] = new KeyCounter();
                holders[input].add(selected.what(), selected.amount());
            }
            var machine = machineFor(step, press, filling, deployer);
            var pattern = new BoundOperationPattern(step, ModContent.OPERATION_PATTERN.get());
            helper.assertTrue(machine.pushPattern(pattern, holders, Direction.NORTH),
                    "Machine rejected imported operation " + step.operation());
            for (var holder : holders) {
                holder.removeZeros();
                helper.assertTrue(holder.isEmpty(), "Machine did not claim all dispatched inputs");
            }
            helper.assertTrue(machine.completeDispatchedPlans() == 1,
                    "Imported operation did not complete in its machine lane");
            previous = step.outputs().getFirst();
            helper.assertTrue(extractOutput(machine, previous),
                    "Machine did not emit the sequence component expected by the next AE pattern");
        }
    }

    private static KeyCounter[] holdersFor(OperationStepSpec step) {
        var holders = new KeyCounter[step.inputs().size()];
        for (int input = 0; input < holders.length; input++) {
            var selected = step.inputs().get(input).alternatives().getFirst();
            holders[input] = new KeyCounter();
            holders[input].add(selected.what(), selected.amount());
        }
        return holders;
    }

    private static KineticMachineBlockEntity machineFor(
            OperationStepSpec step,
            KineticMachineBlockEntity press,
            KineticMachineBlockEntity filling,
            KineticMachineBlockEntity deployer) {
        if (KineticMachineKind.PRESS.acceptsOperation(step.operation())) return press;
        if (KineticMachineKind.FILLING.acceptsOperation(step.operation())) return filling;
        if (KineticMachineKind.DEPLOYER.acceptsOperation(step.operation())) return deployer;
        throw new IllegalArgumentException("No acceptance machine for " + step.operation());
    }

    private static boolean extractOutput(
            KineticMachineBlockEntity machine, appeng.api.stacks.GenericStack expected) {
        if (!(expected.what() instanceof appeng.api.stacks.AEItemKey itemKey)) return false;
        for (int slot = 0; slot < machine.inventory().getSlots(); slot++) {
            var stack = machine.inventory().getStackInSlot(slot);
            if (!itemKey.matches(stack) || stack.getCount() < expected.amount()) continue;
            machine.inventory().extractItem(slot, (int) expected.amount(), false);
            return true;
        }
        return false;
    }

    private static boolean hasQueuedOutput(KineticMachineBlockEntity machine) {
        for (int slot = 1; slot < machine.inventory().getSlots(); slot++) {
            if (!machine.inventory().getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static int countOutput(KineticMachineBlockEntity machine, Item item) {
        int count = 0;
        for (int slot = 2; slot < machine.inventory().getSlots(); slot++) {
            var stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countAnySlot(KineticMachineBlockEntity machine, Item item) {
        int count = 0;
        for (int slot = 0; slot < machine.inventory().getSlots(); slot++) {
            var stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private KineticMachineGameTests() {}
}
