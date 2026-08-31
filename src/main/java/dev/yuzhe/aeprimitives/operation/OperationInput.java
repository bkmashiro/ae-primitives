package dev.yuzhe.aeprimitives.operation;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.world.level.ItemLike;

/** One AE input whose alternatives may originate from a recipe ingredient/tag. */
public record OperationInput(List<GenericStack> alternatives, AEKey remainingKey) {
    public OperationInput {
        alternatives = List.copyOf(alternatives);
        if (alternatives.isEmpty()) throw new IllegalArgumentException("an operation input needs an alternative");
        long amount = alternatives.getFirst().amount();
        if (amount <= 0 || alternatives.stream().anyMatch(candidate -> candidate.amount() != amount)) {
            throw new IllegalArgumentException("all alternatives must have the same positive amount");
        }
    }

    public long amount() { return alternatives.getFirst().amount(); }

    public static OperationInput exact(ItemLike item, long amount) {
        return new OperationInput(List.of(new GenericStack(AEItemKey.of(item), amount)), null);
    }

    public static OperationInput catalyst(ItemLike item) {
        var key = AEItemKey.of(item);
        return new OperationInput(List.of(new GenericStack(key, 1)), key);
    }
}
