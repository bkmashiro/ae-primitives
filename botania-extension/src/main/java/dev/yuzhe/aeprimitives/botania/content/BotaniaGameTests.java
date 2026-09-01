package dev.yuzhe.aeprimitives.botania.content;
import dev.yuzhe.aeprimitives.botania.AePrimitivesBotania;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@GameTestHolder(AePrimitivesBotania.MOD_ID) @PrefixGameTestTemplate(false)
public final class BotaniaGameTests {
 @GameTest(template="empty") public static void pureDaisyPreservesRecipeTimeAcrossSpatialLanes(GameTestHelper h){
  var p=new BlockPos(3,1,3);h.setBlock(p,BotaniaContent.PURE_DAISY_CHAMBER.get());
  var side=p.east();h.setBlock(side,ModContent.ADVANCED_SPATIAL_PARALLEL.get().defaultBlockState().setValue(SpatialParallelBlock.FACING,Direction.WEST));
  var be=(PureDaisyChamberBlockEntity)h.getBlockEntity(p);
  be.inventory().setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("stone")),3));
  h.assertTrue(be.laneCountForTest()==3,"advanced sidecar should expose three lanes");
  be.runTicksForTest((ServerLevel)h.getLevel(),1);
  int time=be.recipeTimeForTest();h.assertTrue(time>1,"Botania recipe time must be preserved");
  be.runTicksForTest((ServerLevel)h.getLevel(),time-2);
  h.assertTrue(count(be,"botania:livingrock")==0,"outputs appeared before recipe time elapsed");
  be.runTicksForTest((ServerLevel)h.getLevel(),1);
  h.assertTrue(count(be,"botania:livingrock")==3,"each spatial lane should complete one real Pure Daisy recipe");
  h.succeed();
 }
 @GameTest(template="empty") public static void chamberRejectsNonBlockInputs(GameTestHelper h){var p=new BlockPos(2,1,2);h.setBlock(p,BotaniaContent.PURE_DAISY_CHAMBER.get());var be=(PureDaisyChamberBlockEntity)h.getBlockEntity(p);be.inventory().setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("stick"))));be.runTicksForTest((ServerLevel)h.getLevel(),400);h.assertTrue(be.activePlansForTest()==0,"non-block input must not create a Pure Daisy plan");h.succeed();}
 private static int count(PureDaisyChamberBlockEntity be,String id){var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));int n=0;for(int i=1;i<10;i++){var s=be.inventory().getStackInSlot(i);if(s.is(item))n+=s.getCount();}return n;}
}
