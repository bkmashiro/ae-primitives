package dev.yuzhe.aeprimitives.content;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrimitiveMachineRecipesTest {
    @Test
    void curesConcretePowderWithoutWorldPlacement() {
        var inventory = inventory(new ItemStack(Items.RED_CONCRETE_POWDER));
        var plan = plan(MachineKind.CONCRETE, inventory);
        assertThat(plan.outputs()).singleElement().extracting(ItemStack::getItem).isEqualTo(Items.RED_CONCRETE);
        plan.apply(inventory);
        assertThat(inventory.getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void convertsDirtToMudThenMudToClay() {
        var wet = inventory(new ItemStack(Items.DIRT), new ItemStack(Items.WATER_BUCKET));
        assertThat(plan(MachineKind.SOIL, wet).outputs()).extracting(ItemStack::getItem)
                .containsExactly(Items.MUD, Items.BUCKET);
        var drying = inventory(new ItemStack(Items.MUD));
        assertThat(plan(MachineKind.SOIL, drying).outputs()).singleElement()
                .extracting(ItemStack::getItem).isEqualTo(Items.CLAY);
    }

    @Test
    void dripstoneKeepsItsSourceAndFillsAnEmptyBucket() {
        var inventory = inventory(new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.BUCKET));
        var plan = plan(MachineKind.DRIPSTONE, inventory);
        plan.apply(inventory);
        assertThat(inventory.getStackInSlot(0).is(Items.LAVA_BUCKET)).isTrue();
        assertThat(inventory.getStackInSlot(1).isEmpty()).isTrue();
        assertThat(plan.outputs()).singleElement().extracting(ItemStack::getItem).isEqualTo(Items.LAVA_BUCKET);
    }

    @Test
    void advancesCopperByExactlyOneWeatheringStage() {
        var plan = plan(MachineKind.OXIDATION, inventory(new ItemStack(Items.COPPER_BLOCK)));
        assertThat(plan.outputs()).singleElement().extracting(ItemStack::getItem).isEqualTo(Items.EXPOSED_COPPER);
    }

    @Test
    void cultivationAndTreeNurseryConsumeBonemealButKeepTheParentPlant() {
        var crop = inventory(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BONE_MEAL, 3));
        var cropPlan = plan(MachineKind.CROP, crop);
        cropPlan.apply(crop);
        assertThat(crop.getStackInSlot(0).is(Items.WHEAT_SEEDS)).isTrue();
        assertThat(crop.getStackInSlot(1).isEmpty()).isTrue();
        assertThat(cropPlan.outputs()).singleElement().extracting(ItemStack::getItem).isEqualTo(Items.WHEAT);

        var tree = inventory(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.BONE_MEAL, 8));
        var treePlan = plan(MachineKind.TREE, tree);
        treePlan.apply(tree);
        assertThat(tree.getStackInSlot(0).is(Items.OAK_SAPLING)).isTrue();
        assertThat(treePlan.outputs().getFirst()).satisfies(stack -> {
            assertThat(stack.is(Items.OAK_LOG)).isTrue();
            assertThat(stack.getCount()).isEqualTo(4);
        });
    }

    @Test
    void passiveGrowthAndApiaryRequireTheirPhysicalCatalysts() {
        var rackPlan = plan(MachineKind.GROWTH_RACK, inventory(new ItemStack(Items.SUGAR_CANE)));
        assertThat(rackPlan.outputs()).singleElement().extracting(ItemStack::getItem).isEqualTo(Items.SUGAR_CANE);

        var apiary = inventory(new ItemStack(Items.POPPY), new ItemStack(Items.GLASS_BOTTLE));
        var apiaryPlan = plan(MachineKind.BEE, apiary);
        apiaryPlan.apply(apiary);
        assertThat(apiary.getStackInSlot(0).is(Items.POPPY)).isTrue();
        assertThat(apiary.getStackInSlot(1).isEmpty()).isTrue();
        assertThat(apiaryPlan.outputs()).singleElement().extracting(ItemStack::getItem).isEqualTo(Items.HONEY_BOTTLE);
    }

    @Test
    void batchGateMovesOnlyCompleteEightItemBatches() {
        assertThat(PrimitiveMachineRecipes.find(MachineKind.BATCH,
                inventory(new ItemStack(Items.IRON_INGOT, 7)))).isNull();
        var plan = plan(MachineKind.BATCH, inventory(new ItemStack(Items.IRON_INGOT, 9)));
        assertThat(plan.outputs().getFirst().getCount()).isEqualTo(8);
    }

    @Test
    void coolingPlateUsesRealConsumablesAndKeepsBasaltCatalysts() {
        var obsidian = plan(MachineKind.COOLING,
                inventory(new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.ICE)));
        assertThat(obsidian.outputs()).extracting(ItemStack::getItem).containsExactly(Items.OBSIDIAN, Items.BUCKET);

        var basaltInventory = inventory(new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.BLUE_ICE),
                new ItemStack(Items.SOUL_SOIL));
        var basalt = plan(MachineKind.COOLING, basaltInventory);
        basalt.apply(basaltInventory);
        assertThat(basaltInventory.getStackInSlot(1).is(Items.BLUE_ICE)).isTrue();
        assertThat(basaltInventory.getStackInSlot(2).is(Items.SOUL_SOIL)).isTrue();
        assertThat(basalt.outputs()).extracting(ItemStack::getItem).containsExactly(Items.BASALT, Items.BUCKET);
    }

    private static PrimitiveMachineRecipes.Plan plan(MachineKind kind, ItemStackHandler inventory) {
        var plan = PrimitiveMachineRecipes.find(kind, inventory);
        assertThat(plan).isNotNull();
        return plan;
    }

    private static ItemStackHandler inventory(ItemStack... inputs) {
        var inventory = new ItemStackHandler(12);
        for (int i = 0; i < inputs.length; i++) inventory.setStackInSlot(i, inputs[i]);
        return inventory;
    }
}
