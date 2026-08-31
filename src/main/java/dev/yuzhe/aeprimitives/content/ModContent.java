package dev.yuzhe.aeprimitives.content;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.menu.PrimitiveMachineMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AePrimitives.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AePrimitives.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AePrimitives.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AePrimitives.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AePrimitives.MOD_ID);

    public static final DeferredBlock<PrimitiveMachineBlock> FORTUNE_CHAMBER = block(MachineKind.FORTUNE);
    public static final DeferredBlock<PrimitiveMachineBlock> TRANSFORMATION_CHAMBER = block(MachineKind.TRANSFORMATION);
    public static final DeferredBlock<PrimitiveMachineBlock> RESOURCE_GENERATOR = block(MachineKind.GENERATOR);
    public static final DeferredBlock<PrimitiveMachineBlock> GROWTH_CHAMBER = block(MachineKind.GROWTH);
    public static final DeferredBlock<PrimitiveMachineBlock> COMPOST_CHAMBER = block(MachineKind.COMPOST);
    public static final DeferredBlock<PrimitiveMachineBlock> RESONANCE_CONTROLLER = block(MachineKind.FOUNDRY);
    public static final DeferredBlock<PrimitiveMachineBlock> CONCRETE_CURING_CHAMBER = block(MachineKind.CONCRETE);
    public static final DeferredBlock<PrimitiveMachineBlock> SOIL_PROCESSOR = block(MachineKind.SOIL);
    public static final DeferredBlock<PrimitiveMachineBlock> DRIPSTONE_RESERVOIR = block(MachineKind.DRIPSTONE);
    public static final DeferredBlock<PrimitiveMachineBlock> OXIDATION_CHAMBER = block(MachineKind.OXIDATION);
    public static final DeferredBlock<PrimitiveMachineBlock> CROP_CULTIVATOR = block(MachineKind.CROP);
    public static final DeferredBlock<PrimitiveMachineBlock> TREE_NURSERY = block(MachineKind.TREE);
    public static final DeferredBlock<PrimitiveMachineBlock> GROWTH_RACK = block(MachineKind.GROWTH_RACK);
    public static final DeferredBlock<PrimitiveMachineBlock> APIARY_CHAMBER = block(MachineKind.BEE);
    public static final DeferredBlock<PrimitiveMachineBlock> BATCH_GATE = block(MachineKind.BATCH);
    public static final DeferredBlock<PrimitiveMachineBlock> COOLING_PLATE = block(MachineKind.COOLING);
    public static final DeferredBlock<ResonancePartBlock> RESONANCE_CASING = part("resonance_casing");
    public static final DeferredBlock<ResonancePartBlock> RESONANCE_COIL = part("resonance_coil");
    public static final DeferredBlock<ResonancePartBlock> RESONANCE_CORE = part("resonance_core");

    public static final DeferredItem<BlockItem> FORTUNE_CHAMBER_ITEM = item(MachineKind.FORTUNE, FORTUNE_CHAMBER);
    public static final DeferredItem<BlockItem> TRANSFORMATION_CHAMBER_ITEM = item(MachineKind.TRANSFORMATION, TRANSFORMATION_CHAMBER);
    public static final DeferredItem<BlockItem> RESOURCE_GENERATOR_ITEM = item(MachineKind.GENERATOR, RESOURCE_GENERATOR);
    public static final DeferredItem<BlockItem> GROWTH_CHAMBER_ITEM = item(MachineKind.GROWTH, GROWTH_CHAMBER);
    public static final DeferredItem<BlockItem> COMPOST_CHAMBER_ITEM = item(MachineKind.COMPOST, COMPOST_CHAMBER);
    public static final DeferredItem<BlockItem> RESONANCE_CONTROLLER_ITEM = item(MachineKind.FOUNDRY, RESONANCE_CONTROLLER);
    public static final DeferredItem<BlockItem> CONCRETE_CURING_CHAMBER_ITEM = item(MachineKind.CONCRETE, CONCRETE_CURING_CHAMBER);
    public static final DeferredItem<BlockItem> SOIL_PROCESSOR_ITEM = item(MachineKind.SOIL, SOIL_PROCESSOR);
    public static final DeferredItem<BlockItem> DRIPSTONE_RESERVOIR_ITEM = item(MachineKind.DRIPSTONE, DRIPSTONE_RESERVOIR);
    public static final DeferredItem<BlockItem> OXIDATION_CHAMBER_ITEM = item(MachineKind.OXIDATION, OXIDATION_CHAMBER);
    public static final DeferredItem<BlockItem> CROP_CULTIVATOR_ITEM = item(MachineKind.CROP, CROP_CULTIVATOR);
    public static final DeferredItem<BlockItem> TREE_NURSERY_ITEM = item(MachineKind.TREE, TREE_NURSERY);
    public static final DeferredItem<BlockItem> GROWTH_RACK_ITEM = item(MachineKind.GROWTH_RACK, GROWTH_RACK);
    public static final DeferredItem<BlockItem> APIARY_CHAMBER_ITEM = item(MachineKind.BEE, APIARY_CHAMBER);
    public static final DeferredItem<BlockItem> BATCH_GATE_ITEM = item(MachineKind.BATCH, BATCH_GATE);
    public static final DeferredItem<BlockItem> COOLING_PLATE_ITEM = item(MachineKind.COOLING, COOLING_PLATE);
    public static final DeferredItem<BlockItem> RESONANCE_CASING_ITEM = simpleItem("resonance_casing", RESONANCE_CASING);
    public static final DeferredItem<BlockItem> RESONANCE_COIL_ITEM = simpleItem("resonance_coil", RESONANCE_COIL);
    public static final DeferredItem<BlockItem> RESONANCE_CORE_ITEM = simpleItem("resonance_core", RESONANCE_CORE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrimitiveMachineBlockEntity>> MACHINE_ENTITY =
            BLOCK_ENTITIES.register("primitive_machine", () -> {
                var type = BlockEntityType.Builder.of(PrimitiveMachineBlockEntity::new,
                        FORTUNE_CHAMBER.get(), TRANSFORMATION_CHAMBER.get(), RESOURCE_GENERATOR.get(),
                        GROWTH_CHAMBER.get(), COMPOST_CHAMBER.get(), RESONANCE_CONTROLLER.get(),
                        CONCRETE_CURING_CHAMBER.get(), SOIL_PROCESSOR.get(), DRIPSTONE_RESERVOIR.get(),
                        OXIDATION_CHAMBER.get(), CROP_CULTIVATOR.get(), TREE_NURSERY.get(), GROWTH_RACK.get(),
                        APIARY_CHAMBER.get(), BATCH_GATE.get(), COOLING_PLATE.get()).build(null);
                FORTUNE_CHAMBER.get().bind(type);
                TRANSFORMATION_CHAMBER.get().bind(type);
                RESOURCE_GENERATOR.get().bind(type);
                GROWTH_CHAMBER.get().bind(type);
                COMPOST_CHAMBER.get().bind(type);
                RESONANCE_CONTROLLER.get().bind(type);
                CONCRETE_CURING_CHAMBER.get().bind(type);
                SOIL_PROCESSOR.get().bind(type);
                DRIPSTONE_RESERVOIR.get().bind(type);
                OXIDATION_CHAMBER.get().bind(type);
                CROP_CULTIVATOR.get().bind(type);
                TREE_NURSERY.get().bind(type);
                GROWTH_RACK.get().bind(type);
                APIARY_CHAMBER.get().bind(type);
                BATCH_GATE.get().bind(type);
                COOLING_PLATE.get().bind(type);
                AEBaseBlockEntity.registerBlockEntityItem(type, FORTUNE_CHAMBER_ITEM.get());
                return type;
            });

    public static final DeferredHolder<MenuType<?>, MenuType<PrimitiveMachineMenu>> MACHINE_MENU =
            MENUS.register("primitive_machine", () -> IMenuTypeExtension.create(PrimitiveMachineMenu::new));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeprimitives"))
                    .icon(() -> RESOURCE_GENERATOR_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(FORTUNE_CHAMBER_ITEM.get());
                        output.accept(TRANSFORMATION_CHAMBER_ITEM.get());
                        output.accept(RESOURCE_GENERATOR_ITEM.get());
                        output.accept(GROWTH_CHAMBER_ITEM.get());
                        output.accept(COMPOST_CHAMBER_ITEM.get());
                        output.accept(CONCRETE_CURING_CHAMBER_ITEM.get());
                        output.accept(SOIL_PROCESSOR_ITEM.get());
                        output.accept(DRIPSTONE_RESERVOIR_ITEM.get());
                        output.accept(OXIDATION_CHAMBER_ITEM.get());
                        output.accept(CROP_CULTIVATOR_ITEM.get());
                        output.accept(TREE_NURSERY_ITEM.get());
                        output.accept(GROWTH_RACK_ITEM.get());
                        output.accept(APIARY_CHAMBER_ITEM.get());
                        output.accept(BATCH_GATE_ITEM.get());
                        output.accept(COOLING_PLATE_ITEM.get());
                        output.accept(RESONANCE_CONTROLLER_ITEM.get());
                        output.accept(RESONANCE_CASING_ITEM.get());
                        output.accept(RESONANCE_COIL_ITEM.get());
                        output.accept(RESONANCE_CORE_ITEM.get());
                    }).build());

    private static DeferredBlock<PrimitiveMachineBlock> block(MachineKind kind) {
        return BLOCKS.register(kind.id(), () -> new PrimitiveMachineBlock(kind,
                BlockBehaviour.Properties.of().strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));
    }
    private static DeferredItem<BlockItem> item(MachineKind kind, Supplier<? extends Block> block) {
        return ITEMS.register(kind.id(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
    private static DeferredBlock<ResonancePartBlock> part(String id) {
        return BLOCKS.register(id, () -> new ResonancePartBlock(
                BlockBehaviour.Properties.of().strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));
    }
    private static DeferredItem<BlockItem> simpleItem(String id, Supplier<? extends Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); TABS.register(bus);
        bus.addListener(ModContent::registerCapabilities);
        bus.addListener(ModContent::commonSetup);
    }
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerSpeed(4, FORTUNE_CHAMBER.get(), TRANSFORMATION_CHAMBER.get(), GROWTH_CHAMBER.get(),
                    COMPOST_CHAMBER.get(), CONCRETE_CURING_CHAMBER.get(), SOIL_PROCESSOR.get(),
                    OXIDATION_CHAMBER.get(), CROP_CULTIVATOR.get(), TREE_NURSERY.get(), COOLING_PLATE.get());
            registerSpeed(2, RESOURCE_GENERATOR.get(), RESONANCE_CONTROLLER.get(), DRIPSTONE_RESERVOIR.get(),
                    GROWTH_RACK.get(), APIARY_CHAMBER.get());
        });
    }
    private static void registerSpeed(int maximum, Block... machines) {
        for (var machine : machines) Upgrades.add(AEItems.SPEED_CARD, machine, maximum);
    }
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE_ENTITY.get(),
                (be, side) -> be.inventory());
    }
    private ModContent() {}
}
