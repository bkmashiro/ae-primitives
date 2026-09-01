package dev.yuzhe.aeprimitives.botania.content;

import dev.yuzhe.aeprimitives.botania.AePrimitivesBotania;
import dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem;
import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
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
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

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

    @GameTest(template = "empty")
    public static void manaPoolInterfaceWaitsForExactNativeManaCost(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos poolPos = new BlockPos(3, 1, 3), interfacePos = poolPos.east();
        helper.setBlock(poolPos, BotaniaBlocks.manaPool);
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (ManaPoolInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        var pool = (ManaPoolBlockEntity) helper.getBlockEntity(poolPos);
        machine.inventory().setStackInSlot(0, new ItemStack(item("minecraft:iron_ingot")));
        helper.assertTrue(machine.planForTest(level), "native manasteel recipe should be selected by the real pool");
        pool.receiveMana(2999);
        helper.assertTrue(!machine.executeForTest(level), "the interface must not complete with less than the native mana cost");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1, "waiting must not reserve or consume the input");
        pool.receiveMana(1);
        helper.assertTrue(machine.executeForTest(level), "the real pool should complete at the exact native mana cost");
        helper.assertTrue(pool.getCurrentMana() == 0 && count(machine, "botania:manasteel_ingot") == 1, "native mana and output must be preserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void manaPoolInterfaceUsesTheRealCatalystBelowPool(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos poolPos = new BlockPos(3, 2, 3), interfacePos = poolPos.east();
        helper.setBlock(poolPos, BotaniaBlocks.manaPool);
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (ManaPoolInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        var pool = (ManaPoolBlockEntity) helper.getBlockEntity(poolPos);
        machine.inventory().setStackInSlot(0, new ItemStack(item("minecraft:coal")));
        helper.assertTrue(!machine.planForTest(level), "a conjuration recipe must not exist without its real catalyst");
        helper.setBlock(poolPos.below(), BotaniaBlocks.conjurationCatalyst);
        machine.markDirty();
        helper.assertTrue(machine.planForTest(level), "the real catalyst should enable the native recipe");
        pool.receiveMana(2100);
        helper.assertTrue(machine.executeForTest(level), "the catalyst recipe should execute through the real pool");
        helper.assertTrue(pool.getCurrentMana() == 0 && count(machine, "minecraft:coal") == 2, "conjuration must retain its native cost and output count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyIdleEmptyManaPoolInterfaceCanBePackaged(GameTestHelper helper) {
        BlockPos interfacePos = new BlockPos(3, 1, 3);
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get());
        var machine = (ManaPoolInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        helper.assertTrue(machine.canPackIntoMachineSpace(), "idle empty Mana Pool interface rejected packaging");
        machine.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        helper.assertTrue(!machine.canPackIntoMachineSpace(), "Mana Pool interface with owned input was packaged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void factoryRunsPackagedManaPoolInterfaceThroughRealPool(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(3, 1, 3);
        BlockPos interfacePos = factoryPos.east();
        BlockPos poolPos = interfacePos.east();
        helper.setBlock(factoryPos.above(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(poolPos, BotaniaBlocks.manaPool);
        var factory = (HeterogeneousFactoryBlockEntity) helper.getBlockEntity(factoryPos);
        var pool = (ManaPoolBlockEntity) helper.getBlockEntity(poolPos);
        var envelope = MachineSpaceEnvelope.capture(
                BuiltInRegistries.BLOCK.getKey(BotaniaContent.MANA_POOL_INTERFACE.get()),
                BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState(), new CompoundTag());
        factory.inventory().setStackInSlot(0,
                MachineSpaceComponentItem.create(ModContent.MACHINE_SPACE_COMPONENT.get(), envelope));
        factory.inventory().setStackInSlot(HeterogeneousFactoryBlockEntity.inputSlot(0, 0),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(factory.inventory().getStackInSlot(HeterogeneousFactoryBlockEntity.inputSlot(0, 0)).getCount() == 1,
                    "unfunded real Mana Pool consumed the factory lane input");
            pool.receiveMana(3000);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(countFactoryLane(factory, 0, item("botania:manasteel_ingot")) == 1,
                    "packaged Mana Pool interface did not return the real pool output");
            helper.assertTrue(pool.getCurrentMana() == 0, "packaged Mana Pool interface did not pay exact native mana");
        });
    }

    @GameTest(template = "empty")
    public static void blockedFactoryManaPoolOutputConsumesNothing(GameTestHelper helper) {
        BlockPos factoryPos = new BlockPos(3, 1, 3);
        BlockPos interfacePos = factoryPos.east();
        BlockPos poolPos = interfacePos.east();
        helper.setBlock(factoryPos.above(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(factoryPos, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(poolPos, BotaniaBlocks.manaPool);
        var factory = (HeterogeneousFactoryBlockEntity) helper.getBlockEntity(factoryPos);
        var pool = (ManaPoolBlockEntity) helper.getBlockEntity(poolPos);
        var envelope = MachineSpaceEnvelope.capture(
                BuiltInRegistries.BLOCK.getKey(BotaniaContent.MANA_POOL_INTERFACE.get()),
                BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState(), new CompoundTag());
        factory.inventory().setStackInSlot(0,
                MachineSpaceComponentItem.create(ModContent.MACHINE_SPACE_COMPONENT.get(), envelope));
        factory.inventory().setStackInSlot(HeterogeneousFactoryBlockEntity.inputSlot(0, 0),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        for (int offset = 0; offset < HeterogeneousFactoryBlockEntity.LANE_BUFFER_SLOTS; offset++)
            factory.inventory().setStackInSlot(HeterogeneousFactoryBlockEntity.outputSlot(0, offset),
                    new ItemStack(Blocks.COBBLESTONE, 64));
        pool.receiveMana(3000);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(factory.inventory().getStackInSlot(HeterogeneousFactoryBlockEntity.inputSlot(0, 0)).getCount() == 1,
                    "blocked factory Mana Pool lane consumed its input");
            helper.assertTrue(pool.getCurrentMana() == 3000, "blocked factory Mana Pool lane consumed mana");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockedManaPoolOutputConsumesNothing(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos poolPos = new BlockPos(3, 1, 3), interfacePos = poolPos.east();
        helper.setBlock(poolPos, BotaniaBlocks.manaPool);
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (ManaPoolInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        var pool = (ManaPoolBlockEntity) helper.getBlockEntity(poolPos);
        machine.inventory().setStackInSlot(0, new ItemStack(item("minecraft:iron_ingot")));
        for (int slot = 1; slot < 10; slot++) machine.inventory().setStackInSlot(slot, new ItemStack(Blocks.COBBLESTONE, 64));
        pool.receiveMana(3000);
        helper.assertTrue(!machine.planForTest(level), "Mana Pool planned despite a blocked owned-output buffer");
        helper.assertTrue(machine.inventory().getStackInSlot(0).getCount() == 1,
                "Blocked Mana Pool output consumed the input");
        helper.assertTrue(pool.getCurrentMana() == 3000, "Blocked Mana Pool output consumed mana");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void manaPoolInterfaceNeverStealsPreexistingWorldItems(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos poolPos = new BlockPos(3, 1, 3), interfacePos = poolPos.east();
        helper.setBlock(poolPos, BotaniaBlocks.manaPool);
        helper.setBlock(interfacePos, BotaniaContent.MANA_POOL_INTERFACE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (ManaPoolInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        var pool = (ManaPoolBlockEntity) helper.getBlockEntity(poolPos);
        ItemEntity external = new ItemEntity(level, poolPos.getX() + 0.5, poolPos.getY() + 1.5, poolPos.getZ() + 0.5, new ItemStack(item("botania:manasteel_ingot")));
        level.addFreshEntity(external);
        machine.inventory().setStackInSlot(0, new ItemStack(item("minecraft:iron_ingot")));
        helper.assertTrue(machine.planForTest(level), "native recipe should plan with an unrelated world item nearby");
        pool.receiveMana(3000);
        helper.assertTrue(machine.executeForTest(level), "owned native output should be recovered");
        helper.assertTrue(external.isAlive() && external.getItem().getCount() == 1, "preexisting world items must not be captured");
        helper.assertTrue(count(machine, "botania:manasteel_ingot") == 1, "only the synchronously created native output belongs to the interface");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pureDaisyOwnershipSurvivesReload(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos flowerPos = new BlockPos(3, 2, 3);
        BlockPos interfacePos = flowerPos.east(2);
        helper.setBlock(flowerPos.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(flowerPos, BotaniaBlocks.pureDaisy);
        helper.setBlock(interfacePos, BotaniaContent.PURE_DAISY_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (PureDaisyInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        machine.inventory().setStackInSlot(0, new ItemStack(Blocks.STONE, 1));
        machine.dispatchForTest(level);
        helper.assertTrue(machine.ownedCountForTest() == 1, "Pure Daisy dispatch did not create owned work");
        CompoundTag saved = new CompoundTag();
        machine.saveAdditional(saved, level.registryAccess());
        var restored = new PureDaisyInterfaceBlockEntity(interfacePos, machine.getBlockState());
        restored.loadAdditional(saved, level.registryAccess());
        helper.assertTrue(restored.ownedCountForTest() == 1, "Pure Daisy ownership was lost across reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runicReservationSurvivesReload(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos altarPos = new BlockPos(3, 1, 3), interfacePos = altarPos.east();
        helper.setBlock(altarPos, BotaniaBlocks.runeAltar);
        helper.setBlock(interfacePos, BotaniaContent.RUNIC_ALTAR_INTERFACE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var machine = (RunicAltarInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
        String[] inputs = {"botania:mana_powder", "botania:manasteel_ingot", "minecraft:bone_meal",
                "minecraft:sugar_cane", "minecraft:fishing_rod", "botania:livingrock"};
        for (int i = 0; i < inputs.length; i++) machine.inventory().setStackInSlot(i, new ItemStack(item(inputs[i])));
        helper.assertTrue(machine.startForTest(level), "Runic reservation did not start");
        CompoundTag saved = new CompoundTag();
        machine.saveAdditional(saved, level.registryAccess());
        var restored = new RunicAltarInterfaceBlockEntity(interfacePos, machine.getBlockState());
        restored.loadAdditional(saved, level.registryAccess());
        helper.assertTrue(restored.hasPlanForTest(), "Runic recipe ownership was lost across reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void allInterfacesReturnOwnedOutputsThroughMeStorage(GameTestHelper helper) {
        BlockPos purePos = new BlockPos(2, 1, 3);
        BlockPos petalPos = new BlockPos(5, 1, 3);
        BlockPos runicPos = new BlockPos(8, 1, 3);
        BlockPos manaPos = new BlockPos(11, 1, 3);
        helper.setBlock(purePos, BotaniaContent.PURE_DAISY_INTERFACE.get());
        helper.setBlock(petalPos, BotaniaContent.PETAL_APOTHECARY_INTERFACE.get());
        helper.setBlock(runicPos, BotaniaContent.RUNIC_ALTAR_INTERFACE.get());
        helper.setBlock(manaPos, BotaniaContent.MANA_POOL_INTERFACE.get());
        var pure = (PureDaisyInterfaceBlockEntity) helper.getBlockEntity(purePos);
        var petal = (PetalApothecaryInterfaceBlockEntity) helper.getBlockEntity(petalPos);
        var runic = (RunicAltarInterfaceBlockEntity) helper.getBlockEntity(runicPos);
        var mana = (ManaPoolInterfaceBlockEntity) helper.getBlockEntity(manaPos);
        pure.inventory().setStackInSlot(1, new ItemStack(Blocks.STONE));
        petal.inventory().setStackInSlot(16, new ItemStack(Blocks.DIRT));
        runic.inventory().setStackInSlot(16, new ItemStack(Blocks.COBBLESTONE));
        mana.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        attachStorageGrid(helper, purePos);
        attachStorageGrid(helper, petalPos);
        attachStorageGrid(helper, runicPos);
        attachStorageGrid(helper, manaPos);
        helper.onEachTick(() -> {
            if (stored(pure, Blocks.STONE.asItem()) == 1
                    && stored(petal, Blocks.DIRT.asItem()) == 1
                    && stored(runic, Blocks.COBBLESTONE.asItem()) == 1
                    && stored(mana, net.minecraft.world.item.Items.IRON_INGOT) == 1) helper.succeed();
        });
    }

    private static void attachStorageGrid(GameTestHelper helper, BlockPos interfacePos) {
        helper.setBlock(interfacePos.above(), appeng.core.definitions.AEBlocks.ENERGY_CELL.block());
        BlockPos drivePos = interfacePos.above(2);
        helper.setBlock(drivePos, appeng.core.definitions.AEBlocks.DRIVE.block());
        helper.setBlock(interfacePos.above(3), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(drivePos);
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_64K.stack());
    }

    private static long stored(appeng.api.networking.security.IActionHost host, Item item) {
        var node = host.getActionableNode();
        if (node == null || node.getGrid() == null) return 0;
        return node.getGrid().getStorageService().getInventory().extract(appeng.api.stacks.AEItemKey.of(item), 1,
                appeng.api.config.Actionable.SIMULATE, appeng.api.networking.security.IActionSource.empty());
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

    private static int count(ManaPoolInterfaceBlockEntity machine, String id) {
        Item item = item(id); int count = 0;
        for (int slot = 1; slot < 10; slot++) { ItemStack stack = machine.inventory().getStackInSlot(slot); if (stack.is(item)) count += stack.getCount(); }
        return count;
    }

    private static int countFactoryLane(HeterogeneousFactoryBlockEntity factory, int lane, Item item) {
        int count = 0;
        for (int offset = 0; offset < HeterogeneousFactoryBlockEntity.LANE_BUFFER_SLOTS; offset++) {
            ItemStack stack = factory.inventory().getStackInSlot(HeterogeneousFactoryBlockEntity.outputSlot(lane, offset));
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
