package dev.yuzhe.aeprimitives.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.content.MachineKind;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class LazyPrimitivePatternTest {
    @Test
    void inputsResolveOnlyWhenThePlannerAsksForThem() {
        var spec = new PrimitivePatternSpec(
                ResourceLocation.fromNamespaceAndPath("aeprimitives", "test/tree/oak"),
                MachineKind.TREE,
                List.of(
                        PrimitivePatternSpec.Input.catalyst(Items.OAK_SAPLING),
                        PrimitivePatternSpec.Input.consumed(Items.BONE_MEAL, 8)),
                List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 4)));
        var pattern = new LazyPrimitivePattern(spec, Items.PAPER);

        assertThat(pattern.inputsResolved()).isFalse();
        assertThat(pattern.getOutputs()).containsExactly(new GenericStack(AEItemKey.of(Items.OAK_LOG), 4));
        assertThat(pattern.inputsResolved()).isFalse();

        var inputs = pattern.getInputs();
        assertThat(pattern.inputsResolved()).isTrue();
        assertThat(inputs).hasSize(2);
        assertThat(inputs[0].getPossibleInputs()).containsExactly(new GenericStack(AEItemKey.of(Items.OAK_SAPLING), 1));
        assertThat(inputs[0].getRemainingKey(AEItemKey.of(Items.OAK_SAPLING))).isEqualTo(AEItemKey.of(Items.OAK_SAPLING));
        assertThat(inputs[1].getPossibleInputs()).containsExactly(new GenericStack(AEItemKey.of(Items.BONE_MEAL), 8));
        assertThat(inputs[1].getRemainingKey(AEItemKey.of(Items.BONE_MEAL))).isNull();
    }

    @Test
    void returnedInputArrayCannotMutateTheCachedPattern() {
        var spec = new PrimitivePatternSpec(
                ResourceLocation.fromNamespaceAndPath("aeprimitives", "test/concrete"),
                MachineKind.CONCRETE,
                List.of(PrimitivePatternSpec.Input.consumed(Items.WHITE_CONCRETE_POWDER, 1)),
                List.of(new GenericStack(AEItemKey.of(Items.WHITE_CONCRETE), 1)));
        var pattern = new LazyPrimitivePattern(spec, Items.PAPER);

        var first = pattern.getInputs();
        first[0] = null;

        assertThat(pattern.getInputs()[0]).isNotNull();
    }
}
