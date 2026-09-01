package dev.yuzhe.aeprimitives.botania.content;

import dev.yuzhe.aeprimitives.botania.AePrimitivesBotania;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vazkii.botania.api.block.PetalApothecary;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.PetalApothecaryBlock;
import vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;
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

    @GameTest(template = "empty")
    public static void petalInterfaceUsesRealWaterFilledApothecary(GameTestHelper helper) {
        BlockPos apothecaryPos = new BlockPos(3, 1, 3);
        BlockPos interfacePos = apothecaryPos.east();
        helper.setBlock(apothecaryPos, BotaniaBlocks.ALL_APOTHECARIES[0].defaultBlockState()
                .setValue(PetalApothecaryBlock.FLUID, PetalApothecary.State.WATER));
        helper.setBlock(interfacePos, BotaniaContent.PETAL_APOTHECARY_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (PetalApothecaryInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        for (int slot = 0; slot < 4; slot++) machine.inventory().setStackInSlot(slot, new ItemStack(item("botania:white_petal")));
        machine.inventory().setStackInSlot(4, new ItemStack(item("minecraft:wheat_seeds")));

        helper.assertTrue(machine.craftForTest((ServerLevel) helper.getLevel()), "a complete real recipe should dispatch");
        helper.assertTrue(count(machine, "botania:pure_daisy") == 1, "the native apothecary result should be recovered");
        var apothecary = (PetalApothecaryBlockEntity) helper.getBlockEntity(apothecaryPos);
        helper.assertTrue(apothecary.isEmpty(), "the real apothecary should consume its native ingredients");
        helper.assertTrue(apothecary.getFluid() == PetalApothecary.State.EMPTY, "the real apothecary should consume its water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void petalInterfaceDoesNotBypassMissingWater(GameTestHelper helper) {
        BlockPos apothecaryPos = new BlockPos(3, 1, 3);
        BlockPos interfacePos = apothecaryPos.east();
        helper.setBlock(apothecaryPos, BotaniaBlocks.ALL_APOTHECARIES[0]);
        helper.setBlock(interfacePos, BotaniaContent.PETAL_APOTHECARY_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (PetalApothecaryInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        for (int slot = 0; slot < 4; slot++) machine.inventory().setStackInSlot(slot, new ItemStack(item("botania:white_petal")));
        machine.inventory().setStackInSlot(4, new ItemStack(item("minecraft:wheat_seeds")));

        helper.assertTrue(!machine.craftForTest((ServerLevel) helper.getLevel()), "the interface must not invent apothecary water");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1, "blocked dispatch must not consume ingredients");
        helper.assertTrue(count(machine, "botania:pure_daisy") == 0, "a dry apothecary must not produce an output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runicInterfaceWaitsForNativeManaAndCompletion(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos altarPos = new BlockPos(3, 1, 3), interfacePos = altarPos.east();
        helper.setBlock(altarPos, BotaniaBlocks.runeAltar);
        helper.setBlock(interfacePos, BotaniaContent.RUNIC_ALTAR_INTERFACE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (RunicAltarInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        String[] inputs = {"botania:mana_powder", "botania:manasteel_ingot", "minecraft:bone_meal", "minecraft:sugar_cane", "minecraft:fishing_rod", "botania:livingrock"};
        for (int i = 0; i < inputs.length; i++) machine.inventory().setStackInSlot(i, new ItemStack(item(inputs[i])));
        helper.assertTrue(machine.startForTest(level), "valid rune ingredients should dispatch into the real altar");
        var altar = (RunicAltarBlockEntity) helper.getBlockEntity(altarPos);
        RunicAltarBlockEntity.serverTick(level, altarPos, altar.getBlockState(), altar);
        helper.assertTrue(altar.getTargetMana() == 5200, "real rune recipe must retain its native mana cost");
        altar.receiveMana(5199);
        helper.assertTrue(!machine.finishForTest(level), "interface must not complete an underfunded altar");
        altar.receiveMana(1);
        helper.assertTrue(machine.finishForTest(level), "fully funded native altar should complete");
        helper.assertTrue(count(machine, "botania:rune_water") == 2 && altar.isEmpty(), "native output should be recovered after altar completion");
        helper.succeed();
    }

    private static int count(PureDaisyInterfaceBlockEntity machine, String id) {
        Item item = item(id);
        int count = 0;
        for (int slot = 1; slot < 10; slot++) {
            ItemStack stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int count(PetalApothecaryInterfaceBlockEntity machine, String id) {
        Item item = item(id);
        int count = 0;
        for (int slot = 16; slot < 25; slot++) {
            ItemStack stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static int count(RunicAltarInterfaceBlockEntity machine, String id) {
        Item item = item(id); int count = 0;
        for (int slot = 16; slot < 25; slot++) { ItemStack stack = machine.inventory().getStackInSlot(slot); if (stack.is(item)) count += stack.getCount(); }
        return count;
    }
}
