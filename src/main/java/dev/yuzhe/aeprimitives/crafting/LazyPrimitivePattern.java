package dev.yuzhe.aeprimitives.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class LazyPrimitivePattern implements IPatternDetails {
    private final PrimitivePatternSpec spec;
    private final AEItemKey definition;
    private volatile IInput[] resolvedInputs;

    LazyPrimitivePattern(PrimitivePatternSpec spec, net.minecraft.world.level.ItemLike definitionItem) {
        this.spec = spec;
        var stack = new ItemStack(definitionItem);
        var data = new CompoundTag();
        data.putString("primitivePattern", spec.id().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        this.definition = AEItemKey.of(stack);
    }

    public PrimitivePatternSpec spec() {
        return spec;
    }

    public boolean inputsResolved() {
        return resolvedInputs != null;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        var current = resolvedInputs;
        if (current == null) {
            synchronized (this) {
                current = resolvedInputs;
                if (current == null) {
                    current = spec.inputs().stream().map(ExactInput::new).toArray(IInput[]::new);
                    resolvedInputs = current;
                }
            }
        }
        return current.clone();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return spec.outputs();
    }

    @Override
    public int hashCode() {
        return spec.id().hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LazyPrimitivePattern pattern && spec.id().equals(pattern.spec.id());
    }

    private record ExactInput(PrimitivePatternSpec.Input spec) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(spec.key(), spec.amount())};
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return spec.key().equals(key);
        }

        @Override
        public AEKey getRemainingKey(AEKey key) {
            return spec.remainingKey();
        }
    }
}
