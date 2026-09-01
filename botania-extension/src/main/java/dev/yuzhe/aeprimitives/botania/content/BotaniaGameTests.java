package dev.yuzhe.aeprimitives.botania.content;

import dev.yuzhe.aeprimitives.botania.AePrimitivesBotania;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.flower.misc.PureDaisyBlockEntity;

@GameTestHolder(AePrimitivesBotania.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BotaniaGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void interfaceLetsRealPureDaisyOwnConversionAndTiming(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos flowerPos = new BlockPos(3, 2, 3);
        BlockPos interfacePos = flowerPos.east(2);
        helper.setBlock(flowerPos.below(), Blocks.DIRT);
        helper.setBlock(flowerPos, BotaniaBlocks.pureDaisy);
        helper.setBlock(interfacePos, BotaniaContent.PURE_DAISY_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (PureDaisyInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        machine.inventory().setStackInSlot(0, new ItemStack(Blocks.STONE, 1));
        machine.dispatchForTest(level);
        helper.assertTrue(machine.ownedCountForTest() == 1, "interface must own exactly the block it dispatched");
        BlockPos target = machine.ownedPositionForTest();
        helper.assertTrue(level.getBlockState(target).is(Blocks.STONE), "input must be placed into the real Pure Daisy ring");
        var flower = (PureDaisyBlockEntity) helper.getBlockEntity(flowerPos);
        int recipeTime = machine.ownedRecipeTimeForTest(level);
        helper.assertTrue(recipeTime > 1, "real Botania recipe time must be available");
        for (int i = 0; i < recipeTime * 8 - 1; i++) flower.tickFlower();
        helper.assertTrue(level.getBlockState(target).is(Blocks.STONE), "interface must not complete Botania's conversion early");
        flower.tickFlower();
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString().equals("botania:livingrock"),
                "the real Pure Daisy must replace the world block");
        machine.recoverForTest(level);
        helper.assertTrue(level.getBlockState(target).isAir(), "completed world result should be recovered");
        helper.assertTrue(count(machine, "botania:livingrock") == 1, "recovered result should enter the interface output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void interfaceNeverClaimsExternalRingChanges(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos flowerPos = new BlockPos(3, 2, 3);
        BlockPos interfacePos = flowerPos.east(2);
        helper.setBlock(flowerPos.below(), Blocks.DIRT);
        helper.setBlock(flowerPos, BotaniaBlocks.pureDaisy);
        helper.setBlock(interfacePos, BotaniaContent.PURE_DAISY_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (PureDaisyInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        machine.inventory().setStackInSlot(0, new ItemStack(Blocks.STONE));
        machine.dispatchForTest(level);
        BlockPos target = machine.ownedPositionForTest();
        level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
        machine.recoverForTest(level);
        helper.assertTrue(machine.ownedCountForTest() == 0, "external mutation should release ownership");
        helper.assertTrue(level.getBlockState(target).is(Blocks.DIRT), "interface must not harvest an unrelated block");
        helper.assertTrue(count(machine, "minecraft:dirt") == 0, "external blocks must never become interface output");
        helper.succeed();
    }

    private static int count(PureDaisyInterfaceBlockEntity machine, String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        int count = 0;
        for (int slot = 1; slot < 10; slot++) {
            var stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
