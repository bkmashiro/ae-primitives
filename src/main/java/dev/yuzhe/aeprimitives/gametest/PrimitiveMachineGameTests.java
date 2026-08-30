package dev.yuzhe.aeprimitives.gametest;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AePrimitives.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrimitiveMachineGameTests {
    private static final BlockPos ENERGY = new BlockPos(1, 1, 1);
    private static final BlockPos MACHINE = new BlockPos(2, 1, 1);

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void generatorProducesCobblestone(GameTestHelper helper) {
        var machine = setup(helper, ModContent.RESOURCE_GENERATOR.get());
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.COBBLESTONE), "generator produced no cobblestone"));
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void fortuneChamberUsesBlockLoot(GameTestHelper helper) {
        var machine = setup(helper, ModContent.FORTUNE_CHAMBER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.DIAMOND_ORE));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.DIAMOND), "fortune chamber produced no diamonds"));
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void transformationChamberUsesAe2TransformRecipe(GameTestHelper helper) {
        var machine = setup(helper, ModContent.TRANSFORMATION_CHAMBER.get());
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack());
        machine.inventory().setStackInSlot(1, new ItemStack(Items.REDSTONE));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.QUARTZ));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, AEItems.FLUIX_CRYSTAL.asItem()), "transform chamber produced no fluix crystal"));
    }

    private static PrimitiveMachineBlockEntity setup(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(MACHINE, block);
        return helper.<PrimitiveMachineBlockEntity>getBlockEntity(MACHINE);
    }

    private static boolean has(PrimitiveMachineBlockEntity machine, net.minecraft.world.item.Item item) {
        for (int slot = 3; slot < 12; slot++) if (machine.inventory().getStackInSlot(slot).is(item)) return true;
        return false;
    }
}
