package dev.yuzhe.aeprimitives.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.content.MachineKind;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record PrimitivePatternSpec(
        ResourceLocation id,
        MachineKind machine,
        List<Input> inputs,
        List<GenericStack> outputs) {

    public PrimitivePatternSpec {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (outputs.isEmpty()) throw new IllegalArgumentException("A primitive pattern needs an output");
    }

    public record Input(AEItemKey key, long amount, AEItemKey remainingKey) {
        public Input {
            if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid pattern input");
        }

        public static Input consumed(net.minecraft.world.level.ItemLike item, long amount) {
            return new Input(AEItemKey.of(item), amount, null);
        }

        public static Input catalyst(net.minecraft.world.level.ItemLike item) {
            var key = AEItemKey.of(item);
            return new Input(key, 1, key);
        }
    }
}
