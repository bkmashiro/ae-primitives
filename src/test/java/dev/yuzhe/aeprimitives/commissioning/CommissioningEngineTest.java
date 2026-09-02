package dev.yuzhe.aeprimitives.commissioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.content.MachineKind;
import dev.yuzhe.aeprimitives.crafting.PrimitivePatternSpec;
import java.lang.reflect.RecordComponent;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class CommissioningEngineTest {
    private static final ResourceLocation MACHINE = id("growth_chamber");
    private static final ResourceLocation RECIPE = id("commissioning_test");

    @Test
    void producesRepeatableDescriptionFromSyntheticInputsOnly() {
        var spec = new PrimitivePatternSpec(RECIPE, MachineKind.GROWTH, List.of(
                PrimitivePatternSpec.Input.consumed(Items.BONE_MEAL, 3),
                PrimitivePatternSpec.Input.catalyst(Items.WHEAT_SEEDS)),
                List.of(new GenericStack(AEItemKey.of(Items.WHEAT), 1)));

        var first = CommissioningEngine.run(MACHINE, spec, List.of());
        var second = CommissioningEngine.run(MACHINE, spec, List.of());

        assertEquals(first, second);
        assertEquals(CommissioningStatus.READY, first.status());
        assertEquals(2, first.consumption().size());
        assertTrue(first.consumption().get(1).retained());
        assertEquals("minecraft:wheat", first.outputs().getFirst().id().toString());
    }

    @Test
    void reportContractCannotCarryCollectibleStacksOrProductionHandles() {
        for (RecordComponent component : CommissioningReport.class.getRecordComponents()) {
            String type = component.getGenericType().getTypeName();
            assertTrue(!type.contains("ItemStack") && !type.contains("ItemStackHandler")
                            && !type.contains("Level") && !type.contains("Storage") && !type.contains("Player"),
                    () -> "unsafe commissioning report field: " + type);
        }
    }

    @Test
    void rejectionCannotExposeSyntheticOutputs() {
        assertThrows(IllegalArgumentException.class, () -> new CommissioningReport(
                MACHINE, null, CommissioningStatus.UNSUPPORTED_PROBABILISTIC, List.of(),
                List.of(new CommissioningResource("item", Items.DIAMOND.builtInRegistryHolder().key().location(),
                        1, false)), List.of(), "rejected"));
    }

    @Test
    void virtualInventoryIsBoundedAndDetached() {
        var inventory = new VirtualCommissioningInventory();
        for (int index = 0; index < 32; index++) inventory.seed(id("input_" + index), 1);
        assertThrows(IllegalArgumentException.class, () -> inventory.seed(id("overflow"), 1));
        var snapshot = inventory.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(id("mutate"), 1L));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aeprimitives", path);
    }
}
