package dev.yuzhe.aeprimitives.powah.content;
import dev.yuzhe.aeprimitives.powah.AePrimitivesPowah;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
public final class PowahContent {
 private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,AePrimitivesPowah.MOD_ID);
 private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,AePrimitivesPowah.MOD_ID);
 private static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,AePrimitivesPowah.MOD_ID);
 public static final Supplier<Block> ENERGIZING_CHAMBER=BLOCKS.register("me_energizing_chamber",MeEnergizingChamberBlock::new);
 public static final Supplier<Item> ENERGIZING_CHAMBER_ITEM=ITEMS.register("me_energizing_chamber",()->new BlockItem(ENERGIZING_CHAMBER.get(),new Item.Properties()));
 public static final Supplier<Item> BASIC_EMITTER=ITEMS.register("basic_emitter_module",()->new Item(new Item.Properties()));
 public static final Supplier<Item> NIOTIC_EMITTER=ITEMS.register("niotic_emitter_module",()->new Item(new Item.Properties()));
 public static final Supplier<Item> NITRO_EMITTER=ITEMS.register("nitro_emitter_module",()->new Item(new Item.Properties()));
 public static final Supplier<BlockEntityType<MeEnergizingChamberBlockEntity>> ENERGIZING_CHAMBER_ENTITY=BES.register("me_energizing_chamber",()->BlockEntityType.Builder.of(MeEnergizingChamberBlockEntity::new,ENERGIZING_CHAMBER.get()).build(null));
 public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);BES.register(bus);bus.addListener(PowahContent::capabilities);}
 private static void capabilities(RegisterCapabilitiesEvent e){
  e.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ENERGIZING_CHAMBER_ENTITY.get(),(be,side)->be.inventory());
  e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,ENERGIZING_CHAMBER_ENTITY.get(),(be,side)->be.energy());
 }
}
