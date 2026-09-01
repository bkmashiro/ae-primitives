package dev.yuzhe.aeprimitives.botania.content;

import dev.yuzhe.aeprimitives.botania.AePrimitivesBotania;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BotaniaContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AePrimitivesBotania.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AePrimitivesBotania.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AePrimitivesBotania.MOD_ID);
    public static final Supplier<Block> PURE_DAISY_INTERFACE = BLOCKS.register("pure_daisy_interface", PureDaisyInterfaceBlock::new);
    public static final Supplier<Item> PURE_DAISY_INTERFACE_ITEM = ITEMS.register("pure_daisy_interface", () -> new BlockItem(PURE_DAISY_INTERFACE.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<PureDaisyInterfaceBlockEntity>> PURE_DAISY_INTERFACE_ENTITY = ENTITIES.register("pure_daisy_interface", () -> BlockEntityType.Builder.of(PureDaisyInterfaceBlockEntity::new, PURE_DAISY_INTERFACE.get()).build(null));
    public static final Supplier<Block> PETAL_APOTHECARY_INTERFACE = BLOCKS.register("petal_apothecary_interface", PetalApothecaryInterfaceBlock::new);
    public static final Supplier<Item> PETAL_APOTHECARY_INTERFACE_ITEM = ITEMS.register("petal_apothecary_interface", () -> new BlockItem(PETAL_APOTHECARY_INTERFACE.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<PetalApothecaryInterfaceBlockEntity>> PETAL_APOTHECARY_INTERFACE_ENTITY = ENTITIES.register("petal_apothecary_interface", () -> BlockEntityType.Builder.of(PetalApothecaryInterfaceBlockEntity::new, PETAL_APOTHECARY_INTERFACE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(BotaniaContent::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PURE_DAISY_INTERFACE_ENTITY.get(), (be, side) -> be.inventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PETAL_APOTHECARY_INTERFACE_ENTITY.get(), (be, side) -> be.inventory());
    }
    private BotaniaContent() {}
}
