package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.kinetics.AePrimitivesKinetics;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
        machine.inventory().setStackInSlot(0, new ItemStack(Items.DIORITE));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "ME crusher did not accept Create's diorite crushing recipe");
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

    private static KineticMachineBlockEntity place(GameTestHelper helper, KineticMachineBlock block) {
        helper.setBlock(MACHINE, block);
        return helper.getBlockEntity(MACHINE);
    }

    private static boolean hasQueuedOutput(KineticMachineBlockEntity machine) {
        for (int slot = 1; slot < machine.inventory().getSlots(); slot++) {
            if (!machine.inventory().getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private KineticMachineGameTests() {}
}
