package dev.yuzhe.aeprimitives.gametest;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.content.PrimitiveMachineBlockEntity;
import dev.yuzhe.aeprimitives.content.ResonancePartBlock;
import dev.yuzhe.aeprimitives.crafting.LazyPrimitivePattern;
import dev.yuzhe.aeprimitives.operation.BoundOperationPattern;
import dev.yuzhe.aeprimitives.operation.OperationInput;
import dev.yuzhe.aeprimitives.operation.OperationPatternData;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import dev.yuzhe.aeprimitives.sequence.OperationStepSpec;
import dev.yuzhe.aeprimitives.sequence.SequencePatternSpec;
import dev.yuzhe.aeprimitives.sequence.SequenceRuntime;
import appeng.api.stacks.GenericStack;
import java.util.List;
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

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void generatorProducesCobblestone(GameTestHelper helper) {
        var machine = setup(helper, ModContent.RESOURCE_GENERATOR.get());
        machine.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        machine.getUpgrades().setItemDirect(1, AEItems.SPEED_CARD.stack());
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.COBBLESTONE), "generator produced no cobblestone"));
    }

    @GameTest(template = "empty", timeoutTicks = 10000)
    public static void fortuneChamberUsesBlockLoot(GameTestHelper helper) {
        var machine = setup(helper, ModContent.FORTUNE_CHAMBER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.DIAMOND_ORE));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.DIAMOND), "fortune chamber produced no diamonds"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void transformationChamberUsesAe2TransformRecipe(GameTestHelper helper) {
        var machine = setup(helper, ModContent.TRANSFORMATION_CHAMBER.get());
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack());
        machine.inventory().setStackInSlot(1, new ItemStack(Items.REDSTONE));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.QUARTZ));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, AEItems.FLUIX_CRYSTAL.asItem()), "transform chamber produced no fluix crystal"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void growthChamberGrowsCertusFromDustAndSand(GameTestHelper helper) {
        var machine = setup(helper, ModContent.GROWTH_CHAMBER.get());
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_DUST.stack());
        machine.inventory().setStackInSlot(1, new ItemStack(Items.SAND));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()),
                "growth chamber produced no certus quartz"));
    }

    @GameTest(template = "empty", timeoutTicks = 10000)
    public static void compostChamberPreservesVanillaCompostYield(GameTestHelper helper) {
        var machine = setup(helper, ModContent.COMPOST_CHAMBER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.CAKE, 7));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.BONE_MEAL),
                "compost chamber produced no bone meal"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void concreteChamberUsesSpeedCardAndCuresPowder(GameTestHelper helper) {
        var machine = setup(helper, ModContent.CONCRETE_CURING_CHAMBER.get());
        machine.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.RED_CONCRETE_POWDER));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.RED_CONCRETE), "concrete chamber produced no concrete"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void soilProcessorDriesMudIntoClay(GameTestHelper helper) {
        var machine = setup(helper, ModContent.SOIL_PROCESSOR.get());
        machine.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.MUD));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.CLAY), "soil processor produced no clay"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void dripstoneReservoirPreservesSourceAndFillsBucket(GameTestHelper helper) {
        var machine = setup(helper, ModContent.DRIPSTONE_RESERVOIR.get());
        machine.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        machine.getUpgrades().setItemDirect(1, AEItems.SPEED_CARD.stack());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.BUCKET));
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.inventory().getStackInSlot(0).is(Items.LAVA_BUCKET), "reservoir consumed its source bucket");
            helper.assertTrue(has(machine, Items.LAVA_BUCKET), "reservoir filled no lava bucket");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void oxidationChamberAdvancesCopperOneStage(GameTestHelper helper) {
        var machine = setup(helper, ModContent.OXIDATION_CHAMBER.get());
        for (int slot = 0; slot < 4; slot++) machine.getUpgrades().setItemDirect(slot, AEItems.SPEED_CARD.stack());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.COPPER_BLOCK));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.EXPOSED_COPPER), "oxidation chamber advanced no copper"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void cropCultivatorConsumesBoneMealNotSeedStock(GameTestHelper helper) {
        var machine = setup(helper, ModContent.CROP_CULTIVATOR.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.BONE_MEAL, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.inventory().getStackInSlot(0).is(Items.WHEAT_SEEDS), "cultivator consumed seed stock");
            helper.assertTrue(has(machine, Items.WHEAT), "cultivator produced no wheat");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void treeNurseryUsesBoneMealForConservativeLogs(GameTestHelper helper) {
        var machine = setup(helper, ModContent.TREE_NURSERY.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.BONE_MEAL, 8));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.OAK_LOG), "tree nursery produced no logs"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void patternProviderCardPublishesAndRunsLazyTreePattern(GameTestHelper helper) {
        var machine = setup(helper, ModContent.TREE_NURSERY.get());
        machine.getUpgrades().setItemDirect(0, new ItemStack(ModContent.PATTERN_PROVIDER_CARD.get()));
        machine.getUpgrades().setItemDirect(1, AEItems.SPEED_CARD.stack());
        machine.getUpgrades().setItemDirect(2, AEItems.SPEED_CARD.stack());
        boolean[] pushed = {false};

        helper.succeedWhen(() -> {
            var node = machine.getMainNode().getNode();
            helper.assertTrue(node != null && node.isActive(), "tree nursery did not join the AE network");
            var provider = node.getService(ICraftingProvider.class);
            helper.assertTrue(provider != null, "tree nursery published no crafting provider service");

            if (!pushed[0]) {
                var pattern = provider.getAvailablePatterns().stream()
                        .filter(LazyPrimitivePattern.class::isInstance)
                        .map(LazyPrimitivePattern.class::cast)
                        .filter(candidate -> candidate.getPrimaryOutput().what().equals(AEItemKey.of(Items.OAK_LOG)))
                        .findFirst().orElse(null);
                helper.assertTrue(pattern != null, "tree nursery published no oak-log pattern");
                helper.assertTrue(node.getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.OAK_LOG)),
                        "AE crafting service did not index the dynamic oak-log pattern");
                pattern.getInputs();

                var holders = pattern.spec().inputs().stream().map(input -> {
                    var holder = new KeyCounter();
                    holder.add(input.key(), input.amount());
                    return holder;
                }).toArray(KeyCounter[]::new);
                helper.assertTrue(machine.isPatternProviderMode(), "pattern provider card left machine mode");
                helper.assertTrue(!machine.isPatternBusy(), "tree nursery was busy before dispatch");
                helper.assertTrue(pattern.spec().machine() == machine.kind(), "oak pattern belonged to another machine");
                helper.assertTrue(provider.pushPattern(pattern, holders),
                        "tree nursery rejected a valid AE crafting dispatch");
                pushed[0] = true;
                return;
            }

            helper.assertTrue(has(machine, Items.OAK_LOG), "dispatched tree pattern produced no logs");
            helper.assertTrue(has(machine, Items.OAK_SAPLING), "dispatched tree pattern did not return its sapling");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10000)
    public static void patternProviderModeRejectsManualInputs(GameTestHelper helper) {
        var machine = setup(helper, ModContent.TREE_NURSERY.get());
        machine.getUpgrades().setItemDirect(0, new ItemStack(ModContent.PATTERN_PROVIDER_CARD.get()));
        helper.runAfterDelay(5, () -> {
            var offered = new ItemStack(Items.OAK_SAPLING);
            var remainder = machine.inventory().insertItem(0, offered, false);
            helper.assertTrue(remainder.getCount() == 1, "pattern provider mode accepted a manual startup item");
            helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "manual startup item entered the machine");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void operationPatternExpandsOnlySequenceReferencedRecipes(GameTestHelper helper) {
        var operationPos = new BlockPos(3, 1, 1);
        var sequencePos = new BlockPos(4, 1, 1);
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(operationPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(sequencePos, AEBlocks.PATTERN_PROVIDER.block());
        var operationProvider = (PatternProviderBlockEntity) helper.getBlockEntity(operationPos);
        var sequenceProvider = (PatternProviderBlockEntity) helper.getBlockEntity(sequencePos);

        var pressing = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "pressing");
        var operationStack = OperationPatternData.encode(ModContent.OPERATION_PATTERN.get(), OperationPatternSpec.all(pressing));
        operationProvider.getLogic().getPatternInv().setItemDirect(0, operationStack);
        var step = new OperationStepSpec(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "pressing/iron_sheet"), pressing,
                List.of(OperationInput.exact(Items.IRON_INGOT, 1)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
        var sequence = new SequencePatternSpec(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aeprimitives", "test_sequence"), List.of(step));

        helper.succeedWhen(() -> {
            if (operationProvider.getLogic().getPatternInv().getStackInSlot(0).isEmpty()) {
                operationProvider.getLogic().getPatternInv().setItemDirect(0, operationStack.copy());
            }
            SequenceRuntime.update(sequenceProvider.getLogic(), List.of(sequence));
            helper.assertTrue(SequenceRuntime.boundPatterns(operationProvider.getLogic().getGrid()).patternsFor(
                            OperationPatternSpec.all(pressing), ModContent.OPERATION_PATTERN.get()).size() == 1,
                    "live sequence did not enter the bound operation registry");
            helper.assertTrue(OperationPatternData.decode(AEItemKey.of(operationStack)) != null,
                    "encoded operation pattern could not be decoded directly");
            helper.assertTrue(appeng.api.crafting.PatternDetailsHelper.decodePattern(
                            operationProvider.getLogic().getPatternInv().getStackInSlot(0), helper.getLevel()) != null,
                    "AE PatternDetailsHelper did not recognize the operation pattern item");
            operationProvider.getLogic().updatePatterns();
            var patterns = operationProvider.getLogic().getAvailablePatterns();
            long boundCount = patterns.stream().filter(BoundOperationPattern.class::isInstance).count();
            helper.assertTrue(boundCount == 1,
                    "operation pattern expanded to " + boundCount + " bound recipes: "
                            + patterns.stream().map(pattern -> pattern.getClass().getSimpleName()).toList());
            helper.assertTrue(patterns.stream().noneMatch(dev.yuzhe.aeprimitives.operation.OperationPatternDetails.class::isInstance),
                    "abstract operation marker leaked into AE crafting patterns");
            var diagnostic = SequenceRuntime.snapshot(operationProvider.getLogic().getGrid());
            helper.assertTrue(diagnostic.sequences().size() == 1,
                    "process analyzer did not find the sequence on this ME network");
            var diagnosticStep = diagnostic.sequences().getFirst().steps().getFirst();
            helper.assertTrue(diagnosticStep.status() == dev.yuzhe.aeprimitives.diagnostics.ProcessStepStatus.READY,
                    "process analyzer did not report the matching operation provider as ready");
            helper.assertTrue(diagnosticStep.providers().stream()
                            .anyMatch(provider -> provider.pos().equals(helper.absolutePos(operationPos))),
                    "process analyzer did not retain the matching provider position");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void growthRackRetainsMotherPlant(GameTestHelper helper) {
        var machine = setup(helper, ModContent.GROWTH_RACK.get());
        machine.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        machine.getUpgrades().setItemDirect(1, AEItems.SPEED_CARD.stack());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.SUGAR_CANE));
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.inventory().getStackInSlot(0).is(Items.SUGAR_CANE), "growth rack consumed mother plant");
            helper.assertTrue(has(machine, Items.SUGAR_CANE), "growth rack produced no sugar cane");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void apiaryNeedsFlowerAndContainer(GameTestHelper helper) {
        var machine = setup(helper, ModContent.APIARY_CHAMBER.get());
        machine.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        machine.getUpgrades().setItemDirect(1, AEItems.SPEED_CARD.stack());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.POPPY));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.GLASS_BOTTLE));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.HONEY_BOTTLE), "apiary produced no honey bottle"));
    }

    @GameTest(template = "empty", timeoutTicks = 10000)
    public static void batchGateReleasesOnlyACompleteBatch(GameTestHelper helper) {
        var machine = setup(helper, ModContent.BATCH_GATE.get());
        helper.assertTrue(!machine.getUpgrades().isItemValid(0, AEItems.SPEED_CARD.stack()),
                "batch gate accepted a speed card with no timing semantics");
        machine.inventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 8));
        helper.succeedWhen(() -> helper.assertTrue(has(machine, Items.COBBLESTONE), "batch gate released no batch"));
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void coolingPlateQuenchesLavaWithIce(GameTestHelper helper) {
        var machine = setup(helper, ModContent.COOLING_PLATE.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.ICE));
        helper.succeedWhen(() -> {
            helper.assertTrue(has(machine, Items.OBSIDIAN), "cooling plate produced no obsidian");
            helper.assertTrue(has(machine, Items.BUCKET), "cooling plate did not return bucket");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
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

    @GameTest(template = "empty", timeoutTicks = 10000)
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

    @GameTest(template = "empty", timeoutTicks = 1000)
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

    @GameTest(template = "empty")
    public static void machineAssemblyTableRoundTripsConfiguredIdleMachine(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 1, 1);
        BlockPos targetPos = tablePos.east();
        helper.setBlock(tablePos, ModContent.MACHINE_ASSEMBLY_TABLE.get().defaultBlockState()
                .setValue(dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(targetPos, ModContent.GROWTH_CHAMBER.get());
        var original = helper.<PrimitiveMachineBlockEntity>getBlockEntity(targetPos);
        original.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        var table = helper.<dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlockEntity>getBlockEntity(tablePos);

        helper.assertTrue(table.operate(), "idle configured machine was not packaged");
        helper.assertTrue(helper.getBlockState(targetPos).isAir(), "packaged machine remained in the world");
        var envelope = dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem.read(table.componentSlot().getStackInSlot(0));
        helper.assertTrue(envelope != null && envelope.blockId().toString().equals("aeprimitives:growth_chamber"),
                "component did not retain the concrete machine identity");

        helper.assertTrue(table.operate(), "component did not unpack into its empty target");
        var restored = helper.<PrimitiveMachineBlockEntity>getBlockEntity(targetPos);
        helper.assertTrue(restored != null && restored.kind() == dev.yuzhe.aeprimitives.content.MachineKind.GROWTH,
                "unpacked machine type changed");
        helper.assertTrue(restored.getInstalledUpgrades(AEItems.SPEED_CARD) == 1,
                "machine configuration did not survive the round trip");
        helper.assertTrue(table.componentSlot().getStackInSlot(0).isEmpty(), "successful unpack retained the component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineAssemblyTableRejectsNonEmptyMachine(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 1, 1), targetPos = tablePos.east();
        helper.setBlock(tablePos, ModContent.MACHINE_ASSEMBLY_TABLE.get().defaultBlockState()
                .setValue(dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(targetPos, ModContent.GROWTH_CHAMBER.get());
        var machine = helper.<PrimitiveMachineBlockEntity>getBlockEntity(targetPos);
        machine.inventory().setStackInSlot(0, AEItems.CERTUS_QUARTZ_DUST.stack());
        var table = helper.<dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlockEntity>getBlockEntity(tablePos);
        helper.assertTrue(!table.operate(), "non-empty machine was packaged");
        helper.assertTrue(helper.getBlockState(targetPos).is(ModContent.GROWTH_CHAMBER.get())
                && machine.inventory().getStackInSlot(0).is(AEItems.CERTUS_QUARTZ_DUST.asItem()),
                "failed packaging changed the machine or its input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineAssemblyTableRejectsMultiblockController(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 1, 1), targetPos = tablePos.east();
        helper.setBlock(tablePos, ModContent.MACHINE_ASSEMBLY_TABLE.get().defaultBlockState()
                .setValue(dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(targetPos, ModContent.RESONANCE_CONTROLLER.get());
        var table = helper.<dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlockEntity>getBlockEntity(tablePos);
        helper.assertTrue(!table.operate(), "a non-atomic multiblock controller was packaged without its structure");
        helper.assertTrue(helper.getBlockState(targetPos).is(ModContent.RESONANCE_CONTROLLER.get()),
                "rejected multiblock packaging removed the controller");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineAssemblyTableKeepsInvalidComponent(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 1, 1);
        helper.setBlock(tablePos, ModContent.MACHINE_ASSEMBLY_TABLE.get().defaultBlockState()
                .setValue(dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlock.FACING, net.minecraft.core.Direction.EAST));
        var table = helper.<dev.yuzhe.aeprimitives.space.MachineAssemblyTableBlockEntity>getBlockEntity(tablePos);
        table.componentSlot().setStackInSlot(0, new ItemStack(ModContent.MACHINE_SPACE_COMPONENT.get()));
        helper.assertTrue(!table.operate(), "component without a versioned envelope was unpacked");
        helper.assertTrue(table.componentSlot().getStackInSlot(0).is(ModContent.MACHINE_SPACE_COMPONENT.get()),
                "failed unpack consumed the invalid component");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void heterogeneousFactoryRunsIndependentLinearLanes(GameTestHelper helper) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(MACHINE, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(MACHINE);
        factory.inventory().setStackInSlot(0, machineComponent(helper, ModContent.CONCRETE_CURING_CHAMBER.get()));
        factory.inventory().setStackInSlot(1, machineComponent(helper, ModContent.CONCRETE_CURING_CHAMBER.get()));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0), new ItemStack(Items.RED_CONCRETE_POWDER));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(1, 0), new ItemStack(Items.BLUE_CONCRETE_POWDER));
        helper.succeedWhen(() -> {
            helper.assertTrue(factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(0, 0)).is(Items.RED_CONCRETE),
                    "first virtual lane produced no red concrete; active=" + factory.getMainNode().isActive()
                            + ", scheduled=" + factory.isScheduled() + ", progress=" + factory.laneProgress(0)
                            + ", input=" + factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0)));
            helper.assertTrue(factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(1, 0)).is(Items.BLUE_CONCRETE),
                    "second virtual lane produced no blue concrete");
            helper.assertTrue(factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0)).isEmpty()
                    && factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(1, 0)).isEmpty(),
                    "two lanes did not consume two independent inputs");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void heterogeneousFactoryRunsDifferentCoreMachineKinds(GameTestHelper helper) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(MACHINE, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(MACHINE);
        factory.inventory().setStackInSlot(0, machineComponent(helper, ModContent.CONCRETE_CURING_CHAMBER.get()));
        factory.inventory().setStackInSlot(1, machineComponent(helper, ModContent.SOIL_PROCESSOR.get()));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0), new ItemStack(Items.WHITE_CONCRETE_POWDER));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(1, 0), new ItemStack(Items.MUD));
        helper.succeedWhen(() -> {
            helper.assertTrue(factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(0, 0)).is(Items.WHITE_CONCRETE),
                    "concrete lane did not retain its own operation");
            helper.assertTrue(factory.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.outputSlot(1, 0)).is(Items.CLAY),
                    "soil lane did not retain its own operation");
        });
    }

    @GameTest(template = "empty")
    public static void heterogeneousFactoryPersistsDistinctLaneState(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get());
        var factory = helper.<dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity>getBlockEntity(MACHINE);
        factory.inventory().setStackInSlot(0, machineComponent(helper, ModContent.CONCRETE_CURING_CHAMBER.get()));
        factory.inventory().setStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0), new ItemStack(Items.RED_CONCRETE_POWDER));
        var tag = new net.minecraft.nbt.CompoundTag();
        factory.saveAdditional(tag, helper.getLevel().registryAccess());
        var restored = new dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity(new BlockPos(8, 1, 1), ModContent.HETEROGENEOUS_SPATIAL_FACTORY.get().defaultBlockState());
        restored.loadTag(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.inventory().getStackInSlot(0).is(ModContent.MACHINE_SPACE_COMPONENT.get())
                && restored.inventory().getStackInSlot(dev.yuzhe.aeprimitives.content.HeterogeneousFactoryBlockEntity.inputSlot(0, 0)).is(Items.RED_CONCRETE_POWDER),
                "factory lane identity or input did not survive persistence");
        helper.assertTrue(restored.isScheduled(), "persisted lane work lost its wake-up state");
        helper.succeed();
    }

    private static ItemStack machineComponent(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        var envelope = dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope.capture(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block),
                block.defaultBlockState(), new net.minecraft.nbt.CompoundTag());
        return dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem.create(
                ModContent.MACHINE_SPACE_COMPONENT.get(), envelope);
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
