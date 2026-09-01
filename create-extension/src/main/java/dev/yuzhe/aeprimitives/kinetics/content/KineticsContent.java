package dev.yuzhe.aeprimitives.kinetics.content;

import appeng.api.AECapabilities;
import dev.yuzhe.aeprimitives.kinetics.AePrimitivesKinetics;
import java.util.function.Supplier;
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

public final class KineticsContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AePrimitivesKinetics.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AePrimitivesKinetics.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AePrimitivesKinetics.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AePrimitivesKinetics.MOD_ID);

    public static final DeferredBlock<KineticMachineBlock> ME_PRESS = block(KineticMachineKind.PRESS);
    public static final DeferredBlock<KineticMachineBlock> ME_CRUSHER = block(KineticMachineKind.CRUSHER);
    public static final DeferredBlock<KineticMachineBlock> ME_CATALYST_CHAMBER = block(KineticMachineKind.FAN);
    public static final DeferredBlock<KineticMachineBlock> ME_BASIN_PROCESSOR = block(KineticMachineKind.BASIN);
    public static final DeferredBlock<KineticMachineBlock> ME_FILLING_STATION = block(KineticMachineKind.FILLING);
    public static final DeferredBlock<KineticMachineBlock> ME_DEPLOYER = block(KineticMachineKind.DEPLOYER);
    public static final DeferredItem<BlockItem> ME_PRESS_ITEM = item(KineticMachineKind.PRESS, ME_PRESS);
    public static final DeferredItem<BlockItem> ME_CRUSHER_ITEM = item(KineticMachineKind.CRUSHER, ME_CRUSHER);
    public static final DeferredItem<BlockItem> ME_CATALYST_CHAMBER_ITEM = item(KineticMachineKind.FAN, ME_CATALYST_CHAMBER);
    public static final DeferredItem<BlockItem> ME_BASIN_PROCESSOR_ITEM = item(KineticMachineKind.BASIN, ME_BASIN_PROCESSOR);
    public static final DeferredItem<BlockItem> ME_FILLING_STATION_ITEM = item(KineticMachineKind.FILLING, ME_FILLING_STATION);
    public static final DeferredItem<BlockItem> ME_DEPLOYER_ITEM = item(KineticMachineKind.DEPLOYER, ME_DEPLOYER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticMachineBlockEntity>> MACHINE_ENTITY =
            BLOCK_ENTITIES.register("kinetic_machine", () -> BlockEntityType.Builder.of(
                    KineticMachineBlockEntity::new, ME_PRESS.get(), ME_CRUSHER.get(), ME_CATALYST_CHAMBER.get(),
                    ME_BASIN_PROCESSOR.get(), ME_FILLING_STATION.get(), ME_DEPLOYER.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeprimitives_kinetics"))
                    .icon(() -> ME_PRESS_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ME_PRESS_ITEM.get());
                        output.accept(ME_CRUSHER_ITEM.get());
                        output.accept(ME_CATALYST_CHAMBER_ITEM.get());
                        output.accept(ME_BASIN_PROCESSOR_ITEM.get());
                        output.accept(ME_FILLING_STATION_ITEM.get());
                        output.accept(ME_DEPLOYER_ITEM.get());
                    }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(KineticsContent::registerCapabilities);
    }

    private static DeferredBlock<KineticMachineBlock> block(KineticMachineKind kind) {
        return BLOCKS.register(kind.id(), () -> new KineticMachineBlock(kind,
                BlockBehaviour.Properties.of().strength(4.0f).requiresCorrectToolForDrops().noOcclusion()));
    }

    private static DeferredItem<BlockItem> item(KineticMachineKind kind, Supplier<? extends KineticMachineBlock> block) {
        return ITEMS.register(kind.id(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE_ENTITY.get(),
                (machine, side) -> machine.inventory());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MACHINE_ENTITY.get(),
                (machine, side) -> machine.supportsFluids() ? machine.fluids() : null);
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, MACHINE_ENTITY.get(),
                (machine, side) -> machine);
    }

    private KineticsContent() {}
}
