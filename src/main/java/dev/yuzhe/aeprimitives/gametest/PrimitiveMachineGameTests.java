package dev.yuzhe.aeprimitives.gametest;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import dev.yuzhe.aeprimitives.content.ResonancePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AePrimitives.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrimitiveMachineGameTests {
    private static final BlockPos ENERGY = new BlockPos(2, 1, 1);
    private static final BlockPos MACHINE = new BlockPos(3, 1, 1);

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

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void growthChamberGrowsCertusFromDustAndSand(GameTestHelper helper) {
        var machine = setup(helper, ModContent.GROWTH_CHAMBER.get());
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_DUST.stack());
        machine.inventory().setStackInSlot(1, new ItemStack(Items.SAND));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()),
                "growth chamber produced no certus quartz"));
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void resonanceFoundryFormsAndProcessesInParallel(GameTestHelper helper) {
        var machine = setupFoundry(helper);
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack(4));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.REDSTONE, 4));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.QUARTZ, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.isFormed(), "resonance foundry did not form");
            helper.assertTrue(helper.getBlockState(MACHINE.offset(0, 0, 1)).getValue(ResonancePartBlock.ACTIVE),
                    "formed foundry core did not activate");
            helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "foundry did not process four recipes in parallel");
            helper.assertTrue(has(machine, AEItems.FLUIX_CRYSTAL.asItem()), "foundry produced no fluix crystal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void resonanceFoundryStopsWhenPartIsRemoved(GameTestHelper helper) {
        var machine = setupFoundry(helper);
        boolean[] removed = {false};
        helper.runAfterDelay(5, () -> {
            helper.setBlock(MACHINE.offset(1, 1, 2), Blocks.AIR);
            removed[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(removed[0] && !machine.isFormed(),
                    "foundry remained formed after a coil was removed");
            helper.assertTrue(!helper.getBlockState(MACHINE.offset(0, 0, 1)).getValue(ResonancePartBlock.ACTIVE),
                    "broken foundry core remained active");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resonanceFoundryRequiresCompleteStructure(GameTestHelper helper) {
        helper.setBlock(MACHINE.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(MACHINE, ModContent.RESONANCE_CONTROLLER.get());
        var machine = helper.<PrimitiveMachineBlockEntity>getBlockEntity(MACHINE);
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack());
        machine.inventory().setStackInSlot(1, new ItemStack(Items.REDSTONE));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.QUARTZ));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!machine.isFormed(), "incomplete foundry formed");
            helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1,
                    "incomplete foundry consumed input");
            helper.succeed();
        });
    }

    private static PrimitiveMachineBlockEntity setup(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(MACHINE, block);
        return helper.<PrimitiveMachineBlockEntity>getBlockEntity(MACHINE);
    }

    private static PrimitiveMachineBlockEntity setupFoundry(GameTestHelper helper) {
        helper.setBlock(MACHINE.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(MACHINE, ModContent.RESONANCE_CONTROLLER.get());
        for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 2; z++) {
                for (int x = -1; x <= 1; x++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    var block = x == 0 && z == 1
                            ? ModContent.RESONANCE_CORE.get()
                            : Math.abs(x) == 1 && (z == 0 || z == 2)
                            ? ModContent.RESONANCE_COIL.get()
                            : ModContent.RESONANCE_CASING.get();
                    helper.setBlock(MACHINE.offset(x, y, z), block);
                }
            }
        }
        return helper.<PrimitiveMachineBlockEntity>getBlockEntity(MACHINE);
    }

    private static boolean has(PrimitiveMachineBlockEntity machine, net.minecraft.world.item.Item item) {
        for (int slot = 3; slot < 12; slot++) if (machine.inventory().getStackInSlot(slot).is(item)) return true;
        return false;
    }
}
