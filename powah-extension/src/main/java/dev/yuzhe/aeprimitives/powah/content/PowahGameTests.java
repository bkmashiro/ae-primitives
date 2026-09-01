package dev.yuzhe.aeprimitives.powah.content;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.powah.AePrimitivesPowah;
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
@GameTestHolder(AePrimitivesPowah.MOD_ID) @PrefixGameTestTemplate(false)
public final class PowahGameTests {
 @GameTest(template="empty") public static void energizingUsesExactEnergyAcrossIndependentSpatialLanes(GameTestHelper h){
  var p=new BlockPos(3,1,3);h.setBlock(p,PowahContent.ENERGIZING_CHAMBER.get());
  h.setBlock(p.east(),ModContent.ADVANCED_SPATIAL_PARALLEL.get().defaultBlockState().setValue(SpatialParallelBlock.FACING,Direction.WEST));
  var be=(MeEnergizingChamberBlockEntity)h.getBlockEntity(p);
  be.inventory().setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("iron_ingot")),3));
  be.inventory().setStackInSlot(1,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("gold_ingot")),3));
  be.inventory().setStackInSlot(17,new ItemStack(PowahContent.NIOTIC_EMITTER.get(),3));
  h.assertTrue(be.energy().receiveEnergy(Integer.MAX_VALUE,true)==30000,"three niotic emitters must expose three times the real rod transfer limit");
  h.assertTrue(be.laneCountForTest()==3,"advanced sidecar should expose three lanes");be.startPlansForTest((ServerLevel)h.getLevel());
  h.assertTrue(be.activePlansForTest()==3,"three complete ingredient sets should become independent plans");
  h.assertTrue(Math.abs(be.totalRequiredFeForTest()-30000.0)<0.01,"three lanes must require three times the recipe energy");
  h.assertTrue(be.energy().receiveEnergy(29999,false)==29999,"external FE buffer should accept energy within aggregate emitter throughput");
  be.runExternalEnergyTickForTest();
  h.assertTrue(count(be,"powah:steel_energized")==4,"only two independently funded lanes should complete");
  h.assertTrue(be.activePlansForTest()==1,"the underfunded lane must remain queued");
  h.assertTrue(be.energy().receiveEnergy(1,false)==1,"the final FE must come from the external connection");
  be.runExternalEnergyTickForTest();
  h.assertTrue(count(be,"powah:steel_energized")==6,"three exact recipe executions should emit six energized steel");
  h.assertTrue(be.activePlansForTest()==0&&be.energy().getEnergyStored()==0,"all external FE must be accounted for exactly");h.succeed();
 }
 @GameTest(template="empty") public static void invalidInputsDoNotReserveEnergy(GameTestHelper h){var p=new BlockPos(2,1,2);h.setBlock(p,PowahContent.ENERGIZING_CHAMBER.get());var be=(MeEnergizingChamberBlockEntity)h.getBlockEntity(p);be.inventory().setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("dirt"))));be.inventory().setStackInSlot(17,new ItemStack(PowahContent.BASIC_EMITTER.get()));be.startPlansForTest((ServerLevel)h.getLevel());h.assertTrue(be.activePlansForTest()==0,"non-recipe input must not create an energy plan");h.succeed();}
 private static int count(MeEnergizingChamberBlockEntity be,String id){var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));int n=0;for(int i=6;i<17;i++){var s=be.inventory().getStackInSlot(i);if(s.is(item))n+=s.getCount();}return n;}
}
