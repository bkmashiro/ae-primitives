package dev.yuzhe.aeprimitives.botania.content;
import dev.yuzhe.aeprimitives.botania.AePrimitivesBotania;
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
public final class BotaniaContent {
 private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,AePrimitivesBotania.MOD_ID);
 private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,AePrimitivesBotania.MOD_ID);
 private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,AePrimitivesBotania.MOD_ID);
 public static final Supplier<Block> PURE_DAISY_CHAMBER=BLOCKS.register("pure_daisy_chamber",PureDaisyChamberBlock::new);
 public static final Supplier<Item> PURE_DAISY_CHAMBER_ITEM=ITEMS.register("pure_daisy_chamber",()->new BlockItem(PURE_DAISY_CHAMBER.get(),new Item.Properties()));
 public static final Supplier<BlockEntityType<PureDaisyChamberBlockEntity>> PURE_DAISY_CHAMBER_ENTITY=ENTITIES.register("pure_daisy_chamber",()->BlockEntityType.Builder.of(PureDaisyChamberBlockEntity::new,PURE_DAISY_CHAMBER.get()).build(null));
 public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);bus.addListener(BotaniaContent::capabilities);}
 private static void capabilities(RegisterCapabilitiesEvent e){e.registerBlockEntity(Capabilities.ItemHandler.BLOCK,PURE_DAISY_CHAMBER_ENTITY.get(),(be,side)->be.inventory());}
 private BotaniaContent(){}
}
