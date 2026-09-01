package dev.yuzhe.aeprimitives.botania.content;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import dev.yuzhe.aeprimitives.content.MachineTier;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelHost;
import dev.yuzhe.aeprimitives.spatial.SpatialParallelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.recipe.PureDaisyRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class PureDaisyChamberBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost, SpatialParallelHost {
 private static final IGridNodeListener<PureDaisyChamberBlockEntity> NODE_LISTENER=new IGridNodeListener<>(){public void onSaveChanges(PureDaisyChamberBlockEntity owner,IGridNode node){owner.setChanged();}};
 private final IManagedGridNode mainNode=GridHelper.createManagedNode(this,NODE_LISTENER).setFlags(GridFlags.REQUIRE_CHANNEL).setExposedOnSides(EnumSet.allOf(Direction.class)).setIdlePowerUsage(2.0);
 private final ItemStackHandler inventory=new ItemStackHandler(10){public boolean isItemValid(int slot,ItemStack stack){return slot==0;}protected void onContentsChanged(int slot){setChanged();}};
 private final List<Plan> plans=new ArrayList<>();
 private boolean topologyDirty=true;
 private int cachedLanes=1;
 public PureDaisyChamberBlockEntity(BlockPos p,BlockState s){super(BotaniaContent.PURE_DAISY_CHAMBER_ENTITY.get(),p,s);}
 public ItemStackHandler inventory(){return inventory;}
 public static void serverTick(Level level,BlockPos pos,BlockState state,PureDaisyChamberBlockEntity be){if(level instanceof ServerLevel server)be.tick(server);}
 private void tick(ServerLevel level){
  if(topologyDirty)refreshTopology();
  flushCompleted();
  if(!mainNode.isActive())return;
  startPlans(level);
  boolean changed=false;
  for(var plan:plans)if(plan.progress<plan.time){plan.progress++;changed=true;}
  if(changed)setChanged();
  flushCompleted();
 }
 private void startPlans(ServerLevel level){
  int available=Math.max(0,cachedLanes-plans.size());
  while(available-->0&&!inventory.getStackInSlot(0).isEmpty()){
   var input=inventory.getStackInSlot(0);
   var recipe=findRecipe(level,input);
   if(recipe==null)return;
   var outputState=recipe.getOutput().pick(level.random);
   var output=new ItemStack(outputState.getBlock().asItem());
   if(output.isEmpty()||!canQueue(output))return;
   inventory.extractItem(0,1,false);
   plans.add(new Plan(output,Math.max(1,recipe.getTime()),0));
   setChanged();
  }
 }
 @Nullable private PureDaisyRecipe findRecipe(ServerLevel level,ItemStack stack){
  if(!(stack.getItem() instanceof BlockItem blockItem))return null;
  var state=blockItem.getBlock().defaultBlockState();
  for(var holder:BotaniaRecipeTypes.getRecipes(level,BotaniaRecipeTypes.PURE_DAISY_TYPE)){
   var recipe=holder.value();
   if(recipe.matches(level,worldPosition,state))return recipe;
  }
  return null;
 }
 private void flushCompleted(){
  for(int i=0;i<plans.size();){var p=plans.get(i);if(p.progress<p.time||!queue(p.output)){i++;continue;}plans.remove(i);setChanged();}
 }
 private boolean canQueue(ItemStack stack){return findOutputSlot(stack)>=0;}
 private boolean queue(ItemStack stack){int slot=findOutputSlot(stack);if(slot<0)return false;var current=inventory.getStackInSlot(slot);if(current.isEmpty())inventory.setStackInSlot(slot,stack.copy());else current.grow(stack.getCount());return true;}
 private int findOutputSlot(ItemStack stack){for(int i=1;i<10;i++){var current=inventory.getStackInSlot(i);if(current.isEmpty())return i;if(ItemStack.isSameItemSameComponents(current,stack)&&current.getCount()+stack.getCount()<=current.getMaxStackSize())return i;}return -1;}
 public void runTicksForTest(ServerLevel level,int ticks){if(topologyDirty)refreshTopology();startPlans(level);for(int i=0;i<ticks;i++){for(var p:plans)if(p.progress<p.time)p.progress++;flushCompleted();}}
 public int activePlansForTest(){return plans.size();}
 public int recipeTimeForTest(){return plans.isEmpty()?0:plans.getFirst().time;}
 public int laneCountForTest(){if(topologyDirty)refreshTopology();return cachedLanes;}
 private void refreshTopology(){
  if(level==null||level.isClientSide)return;
  int lanes=1;
  for(var direction:Direction.values()){
   var state=level.getBlockState(worldPosition.relative(direction));
   if(state.getBlock() instanceof SpatialParallelBlock sidecar
    && state.getValue(SpatialParallelBlock.FACING)==direction.getOpposite()
    && sidecar.tier()==spatialParallelTier())lanes+=sidecar.addedLanes();
  }
  cachedLanes=Math.min(maxSpatialParallelLanes(),lanes);topologyDirty=false;
 }
 public MachineTier spatialParallelTier(){return MachineTier.ADVANCED;}
 public int maxSpatialParallelLanes(){return 8;}
 public void invalidateSpatialParallelism(){topologyDirty=true;setChanged();}
 public IManagedGridNode getMainNode(){return mainNode;}
 public void onLoad(){super.onLoad();if(!level.isClientSide)mainNode.create(level,worldPosition);topologyDirty=true;}
 public void setRemoved(){super.setRemoved();mainNode.destroy();}
 protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);mainNode.saveToNBT(tag);tag.put("inventory",inventory.serializeNBT(registries));var list=new ListTag();for(var p:plans)list.add(p.save(registries));tag.put("plans",list);}
 protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);mainNode.loadFromNBT(tag);if(tag.contains("inventory"))inventory.deserializeNBT(registries,tag.getCompound("inventory"));plans.clear();for(var entry:tag.getList("plans",10))plans.add(Plan.load((CompoundTag)entry,registries));topologyDirty=true;}
 public IGridNode getGridNode(Direction dir){return mainNode.isReady()?mainNode.getNode():null;}
 public IGridNode getActionableNode(){return mainNode.getNode();}
 public AECableType getCableConnectionType(Direction dir){return AECableType.SMART;}

 private static final class Plan{
  private final ItemStack output;private final int time;private int progress;
  private Plan(ItemStack o,int t,int p){output=o;time=t;progress=p;}
  private CompoundTag save(HolderLookup.Provider r){var t=new CompoundTag();t.put("output",output.save(r));t.putInt("time",time);t.putInt("progress",progress);return t;}
  private static Plan load(CompoundTag t,HolderLookup.Provider r){return new Plan(ItemStack.parseOptional(r,t.getCompound("output")),Math.max(1,t.getInt("time")),Math.max(0,t.getInt("progress")));}
 }
}
