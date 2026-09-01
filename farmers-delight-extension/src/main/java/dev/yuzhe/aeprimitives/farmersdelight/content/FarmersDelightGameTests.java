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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

    private static int count(MeCuttingBoardBlockEntity machine, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < machine.inventory().getSlots(); slot++) {
            if (machine.inventory().getStackInSlot(slot).is(item)) total += machine.inventory().getStackInSlot(slot).getCount();
        }
        return total;
    }
}
