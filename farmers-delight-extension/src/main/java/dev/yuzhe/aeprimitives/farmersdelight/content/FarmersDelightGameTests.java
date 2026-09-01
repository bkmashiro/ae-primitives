package dev.yuzhe.aeprimitives.farmersdelight.content;

import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.farmersdelight.AePrimitivesFarmersDelight;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.registry.ModBlocks;

@GameTestHolder(AePrimitivesFarmersDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmersDelightGameTests {
    private static final BlockPos MACHINE = new BlockPos(3, 1, 3);

    @GameTest(template = "empty")
    public static void cuttingBoardRunsRealRecipeWithIndependentSpatialLanes(GameTestHelper helper) {
        helper.setBlock(MACHINE, FarmersDelightContent.ME_CUTTING_BOARD.get());
        helper.setBlock(MACHINE.east(), ModContent.BASIC_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        helper.setBlock(MACHINE.west(), ModContent.BASIC_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.EAST));
        var machine = helper.<MeCuttingBoardBlockEntity>getBlockEntity(MACHINE);
        var cabbage = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "cabbage"));
        var leaves = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "cabbage_leaf"));
        var knife = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "flint_knife"));
        machine.inventory().setStackInSlot(MeCuttingBoardBlockEntity.INPUT_SLOT, new ItemStack(cabbage, 3));
        machine.inventory().setStackInSlot(MeCuttingBoardBlockEntity.TOOL_SLOT, new ItemStack(knife));
        int beforeDamage = machine.inventory().getStackInSlot(MeCuttingBoardBlockEntity.TOOL_SLOT).getDamageValue();
        helper.assertTrue(machine.parallelLanes() == 3, "Basic spatial blocks did not add independent cutting lanes");
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "ME cutting board did not complete three real cutting recipes");
        helper.assertTrue(machine.inventory().getStackInSlot(MeCuttingBoardBlockEntity.INPUT_SLOT).isEmpty(),
                "Cutting inputs were not consumed once per lane");
        helper.assertTrue(count(machine, leaves) == 6, "Cutting outputs were not preserved per lane");
        helper.assertTrue(machine.inventory().getStackInSlot(MeCuttingBoardBlockEntity.TOOL_SLOT).getDamageValue() == beforeDamage + 3,
                "Installed knife durability did not scale linearly with completed lanes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedOutputConsumesNothing(GameTestHelper helper) {
        helper.setBlock(MACHINE, FarmersDelightContent.ME_CUTTING_BOARD.get());
        var machine = helper.<MeCuttingBoardBlockEntity>getBlockEntity(MACHINE);
        var cabbage = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "cabbage"));
        var knife = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "flint_knife"));
        machine.inventory().setStackInSlot(0, new ItemStack(cabbage));
        machine.inventory().setStackInSlot(1, new ItemStack(knife));
        for (int slot = 2; slot < 18; slot++) machine.inventory().setStackInSlot(slot, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), 1) == 0, "Blocked output unexpectedly completed");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1, "Blocked output consumed the input");
        helper.assertTrue(machine.inventory().getStackInSlot(1).getDamageValue() == 0, "Blocked output damaged the knife");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookingPotRequiresRealHeatAndConsumesContainersPerLane(GameTestHelper helper) {
        helper.setBlock(MACHINE, FarmersDelightContent.ME_COOKING_POT.get());
        var machine = helper.<MeCookingPotBlockEntity>getBlockEntity(MACHINE);
        var beefStew = item("farmersdelight", "beef_stew");
        fillBeefStew(machine, 1);
        helper.assertTrue(machine.completeCycles(helper.getLevel(), 1) == 0,
                "ME cooking pot completed without a Farmer's Delight heat source");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1,
                "Unheated cooking consumed an ingredient");
        helper.setBlock(MACHINE.below(), ModBlocks.STOVE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), 1) == 1,
                "ME cooking pot did not complete the real beef stew recipe over a stove");
        helper.assertTrue(count(machine, beefStew) == 1, "Cooked meal was not queued");
        helper.assertTrue(machine.inventory().getStackInSlot(MeCookingPotBlockEntity.CONTAINER_SLOT).isEmpty(),
                "Recipe output container was not consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookingPotPreservesIngredientRemainders(GameTestHelper helper) {
        helper.setBlock(MACHINE.below(), ModBlocks.STOVE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        helper.setBlock(MACHINE, FarmersDelightContent.ME_COOKING_POT.get());
        var machine = helper.<MeCookingPotBlockEntity>getBlockEntity(MACHINE);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.MILK_BUCKET));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.SUGAR));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.COCOA_BEANS));
        machine.inventory().setStackInSlot(3, new ItemStack(Items.COCOA_BEANS));
        machine.inventory().setStackInSlot(MeCookingPotBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertTrue(machine.completeCycles(helper.getLevel(), 1) == 1,
                "ME cooking pot did not resolve the real hot cocoa recipe");
        helper.assertTrue(count(machine, Items.BUCKET) == 1, "Milk bucket remainder was lost");
        helper.assertTrue(count(machine, item("farmersdelight", "hot_cocoa")) == 1,
                "Hot cocoa result was not queued");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookingPotBlockedOutputIsTransactional(GameTestHelper helper) {
        helper.setBlock(MACHINE.below(), ModBlocks.STOVE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        helper.setBlock(MACHINE, FarmersDelightContent.ME_COOKING_POT.get());
        var machine = helper.<MeCookingPotBlockEntity>getBlockEntity(MACHINE);
        fillBeefStew(machine, 1);
        for (int slot = MeCookingPotBlockEntity.OUTPUT_START; slot < MeCookingPotBlockEntity.OUTPUT_END; slot++) {
            machine.inventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertTrue(machine.completeCycles(helper.getLevel(), 1) == 0, "Blocked cooking unexpectedly completed");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1, "Blocked cooking consumed beef");
        helper.assertTrue(machine.inventory().getStackInSlot(1).getCount() == 1, "Blocked cooking consumed carrot");
        helper.assertTrue(machine.inventory().getStackInSlot(2).getCount() == 1, "Blocked cooking consumed potato");
        helper.assertTrue(machine.inventory().getStackInSlot(MeCookingPotBlockEntity.CONTAINER_SLOT).getCount() == 1,
                "Blocked cooking consumed the bowl");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookingPotSpatialLanesScaleInputsAndContainersLinearly(GameTestHelper helper) {
        helper.setBlock(MACHINE.below(), ModBlocks.STOVE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        helper.setBlock(MACHINE, FarmersDelightContent.ME_COOKING_POT.get());
        helper.setBlock(MACHINE.east(), ModContent.BASIC_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.WEST));
        helper.setBlock(MACHINE.west(), ModContent.BASIC_SPATIAL_PARALLEL.get().defaultBlockState()
                .setValue(SpatialParallelBlock.FACING, Direction.EAST));
        var machine = helper.<MeCookingPotBlockEntity>getBlockEntity(MACHINE);
        fillBeefStew(machine, 3);
        helper.assertTrue(machine.parallelLanes() == 3, "Cooking pot did not accept basic spatial lanes");
        helper.assertTrue(machine.completeCycles(helper.getLevel(), machine.parallelLanes()) == 3,
                "Cooking pot did not complete one independent recipe per lane");
        helper.assertTrue(count(machine, item("farmersdelight", "beef_stew")) == 3,
                "Cooking lane outputs did not scale linearly");
        for (int slot = 0; slot < 3; slot++) {
            helper.assertTrue(machine.inventory().getStackInSlot(slot).isEmpty(),
                    "Cooking lane inputs did not scale linearly");
        }
        helper.assertTrue(machine.inventory().getStackInSlot(MeCookingPotBlockEntity.CONTAINER_SLOT).isEmpty(),
                "Cooking lane containers did not scale linearly");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void cookingPotReturnsMealThroughNormalMeStorage(GameTestHelper helper) {
        helper.setBlock(MACHINE.below(), ModBlocks.STOVE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        helper.setBlock(MACHINE, FarmersDelightContent.ME_COOKING_POT.get());
        BlockPos bridge = MACHINE.above();
        BlockPos drivePos = bridge.above();
        helper.setBlock(bridge, appeng.core.definitions.AEBlocks.ENERGY_CELL.block());
        helper.setBlock(drivePos, appeng.core.definitions.AEBlocks.DRIVE.block());
        helper.setBlock(drivePos.above(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(drivePos);
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_64K.stack());
        var machine = helper.<MeCookingPotBlockEntity>getBlockEntity(MACHINE);
        fillBeefStew(machine, 1);
        var target = appeng.api.stacks.AEItemKey.of(item("farmersdelight", "beef_stew"));
        helper.onEachTick(() -> {
            var node = machine.getActionableNode();
            if (node == null || node.getGrid() == null || target == null) return;
            long stored = node.getGrid().getStorageService().getInventory().extract(target, 1,
                    appeng.api.config.Actionable.SIMULATE, appeng.api.networking.security.IActionSource.empty());
            if (stored == 1) helper.succeed();
        });
    }

    private static void fillBeefStew(MeCookingPotBlockEntity machine, int count) {
        machine.inventory().setStackInSlot(0, new ItemStack(Items.BEEF, count));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.CARROT, count));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.POTATO, count));
        machine.inventory().setStackInSlot(MeCookingPotBlockEntity.CONTAINER_SLOT, new ItemStack(Items.BOWL, count));
    }

    private static net.minecraft.world.item.Item item(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static int count(MeCuttingBoardBlockEntity machine, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < machine.inventory().getSlots(); slot++) {
            if (machine.inventory().getStackInSlot(slot).is(item)) total += machine.inventory().getStackInSlot(slot).getCount();
        }
        return total;
    }

    private static int count(MeCookingPotBlockEntity machine, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < machine.inventory().getSlots(); slot++) {
            if (machine.inventory().getStackInSlot(slot).is(item)) total += machine.inventory().getStackInSlot(slot).getCount();
        }
        return total;
    }
}
