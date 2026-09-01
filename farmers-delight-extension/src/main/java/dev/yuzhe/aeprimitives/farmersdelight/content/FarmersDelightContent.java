package dev.yuzhe.aeprimitives.farmersdelight.content;

import dev.yuzhe.aeprimitives.farmersdelight.AePrimitivesFarmersDelight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FarmersDelightContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AePrimitivesFarmersDelight.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AePrimitivesFarmersDelight.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AePrimitivesFarmersDelight.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AePrimitivesFarmersDelight.MOD_ID);
    public static final DeferredBlock<MeCuttingBoardBlock> ME_CUTTING_BOARD = BLOCKS.register("me_cutting_board",
            () -> new MeCuttingBoardBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> ME_CUTTING_BOARD_ITEM = ITEMS.register("me_cutting_board",
            () -> new BlockItem(ME_CUTTING_BOARD.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeCuttingBoardBlockEntity>> CUTTING_BOARD_ENTITY =
            BLOCK_ENTITIES.register("me_cutting_board", () -> BlockEntityType.Builder.of(MeCuttingBoardBlockEntity::new, ME_CUTTING_BOARD.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aeprimitives_farmersdelight"))
            .icon(() -> ME_CUTTING_BOARD_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(ME_CUTTING_BOARD_ITEM.get())).build());
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus);
        bus.addListener(FarmersDelightContent::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CUTTING_BOARD_ENTITY.get(), (machine, side) -> machine.inventory());
    }
    private FarmersDelightContent() {}
}
