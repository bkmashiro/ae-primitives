package dev.yuzhe.aeprimitives.pneumatic;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class PneumaticContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AePrimitivesPneumatic.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AePrimitivesPneumatic.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AePrimitivesPneumatic.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AePrimitivesPneumatic.MOD_ID);

    public static final Supplier<AirStorageCellItem> BASIC_AIR_CELL = ITEMS.register("basic_compressed_air_cell",
            () -> new AirStorageCellItem(new Item.Properties(), AirPressureTier.BASIC, 1, 0.5));
    public static final Supplier<AirStorageCellItem> REINFORCED_AIR_CELL = ITEMS.register("reinforced_compressed_air_cell",
            () -> new AirStorageCellItem(new Item.Properties(), AirPressureTier.REINFORCED, 8, 2.0));
    public static final Supplier<PneumaticAssemblyHeadItem> BASIC_ASSEMBLY_HEAD = ITEMS.register("basic_pneumatic_assembly_head",
            () -> new PneumaticAssemblyHeadItem(new Item.Properties(), AirPressureTier.BASIC));
    public static final Supplier<PneumaticAssemblyHeadItem> REINFORCED_ASSEMBLY_HEAD = ITEMS.register("reinforced_pneumatic_assembly_head",
            () -> new PneumaticAssemblyHeadItem(new Item.Properties(), AirPressureTier.REINFORCED));

    public static final Supplier<PressurePortBlock> BASIC_IMPORT = block("basic_pressure_import_port", AirPressureTier.BASIC, PressurePortMode.IMPORT);
    public static final Supplier<PressurePortBlock> REINFORCED_IMPORT = block("reinforced_pressure_import_port", AirPressureTier.REINFORCED, PressurePortMode.IMPORT);
    public static final Supplier<PressurePortBlock> BASIC_EXPORT = block("basic_pressure_export_port", AirPressureTier.BASIC, PressurePortMode.EXPORT);
    public static final Supplier<PressurePortBlock> REINFORCED_EXPORT = block("reinforced_pressure_export_port", AirPressureTier.REINFORCED, PressurePortMode.EXPORT);
    public static final Supplier<MePneumaticAssemblyChamberBlock> PNEUMATIC_ASSEMBLY_CHAMBER =
            BLOCKS.register("me_pneumatic_assembly_chamber", () -> new MePneumaticAssemblyChamberBlock(
                    BlockBehaviour.Properties.of().strength(4.0f).requiresCorrectToolForDrops()));
    public static final Supplier<Item> PNEUMATIC_ASSEMBLY_CHAMBER_ITEM = ITEMS.register("me_pneumatic_assembly_chamber",
            () -> new BlockItem(PNEUMATIC_ASSEMBLY_CHAMBER.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<PressurePortBlockEntity>> PRESSURE_PORT_ENTITY =
            BLOCK_ENTITIES.register("pressure_port", () -> {
                var type = BlockEntityType.Builder.of(PressurePortBlockEntity::new,
                        BASIC_IMPORT.get(), REINFORCED_IMPORT.get(), BASIC_EXPORT.get(), REINFORCED_EXPORT.get()).build(null);
                BASIC_IMPORT.get().bind(type);
                REINFORCED_IMPORT.get().bind(type);
                BASIC_EXPORT.get().bind(type);
                REINFORCED_EXPORT.get().bind(type);
                return type;
            });
    public static final Supplier<BlockEntityType<MePneumaticAssemblyChamberBlockEntity>> PNEUMATIC_ASSEMBLY_CHAMBER_ENTITY =
            BLOCK_ENTITIES.register("me_pneumatic_assembly_chamber", () -> {
                var type = BlockEntityType.Builder.of(MePneumaticAssemblyChamberBlockEntity::new,
                        PNEUMATIC_ASSEMBLY_CHAMBER.get()).build(null);
                PNEUMATIC_ASSEMBLY_CHAMBER.get().bind(type);
                return type;
            });

    public static final Supplier<CreativeModeTab> CREATIVE_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aeprimitives_pneumaticcraft"))
            .icon(() -> BASIC_AIR_CELL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(BASIC_AIR_CELL.get());
                output.accept(REINFORCED_AIR_CELL.get());
                output.accept(BASIC_ASSEMBLY_HEAD.get());
                output.accept(REINFORCED_ASSEMBLY_HEAD.get());
                output.accept(BASIC_IMPORT.get());
                output.accept(REINFORCED_IMPORT.get());
                output.accept(BASIC_EXPORT.get());
                output.accept(REINFORCED_EXPORT.get());
                output.accept(PNEUMATIC_ASSEMBLY_CHAMBER.get());
            }).build());

    private static Supplier<PressurePortBlock> block(String id, AirPressureTier tier, PressurePortMode mode) {
        var block = BLOCKS.register(id, () -> new PressurePortBlock(tier, mode,
                BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener(PneumaticContent::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(me.desht.pneumaticcraft.api.PNCCapabilities.AIR_HANDLER_MACHINE,
                PRESSURE_PORT_ENTITY.get(), (blockEntity, side) -> blockEntity.airHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PNEUMATIC_ASSEMBLY_CHAMBER_ENTITY.get(),
                (blockEntity, side) -> blockEntity.inventory());
    }
}
