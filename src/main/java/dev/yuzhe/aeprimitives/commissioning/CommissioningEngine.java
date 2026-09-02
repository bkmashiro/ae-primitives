package dev.yuzhe.aeprimitives.commissioning;

import appeng.api.stacks.AEItemKey;
import dev.yuzhe.aeprimitives.crafting.PrimitivePatternSpec;
import dev.yuzhe.aeprimitives.diagnostics.MachineInsightRequirement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Pure deterministic planner over copied declarations and a bounded synthetic input ledger. */
public final class CommissioningEngine {
    public static CommissioningReport run(
            ResourceLocation machine,
            PrimitivePatternSpec pattern,
            List<MachineInsightRequirement> requirements) {
        var inventory = new VirtualCommissioningInventory();
        var inputs = new ArrayList<CommissioningResource>();
        for (var input : pattern.inputs()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(input.key().getItem());
            boolean retained = input.remainingKey() != null && input.remainingKey().equals(input.key());
            var resource = new CommissioningResource("item", id, input.amount(), retained);
            inputs.add(resource);
            inventory.seed(id, input.amount());
        }
        for (var input : inputs) {
            if (!inventory.require(input.id(), input.amount(), input.retained())) {
                return rejected(machine, CommissioningStatus.INVALID_CONFIGURATION, "synthetic_input_mismatch");
            }
        }

        var outputs = new ArrayList<CommissioningResource>();
        for (var output : pattern.outputs()) {
            if (!(output.what() instanceof AEItemKey itemKey)) {
                return rejected(machine, CommissioningStatus.INVALID_CONFIGURATION, "non_item_output");
            }
            outputs.add(new CommissioningResource("item", BuiltInRegistries.ITEM.getKey(itemKey.getItem()),
                    output.amount(), false));
        }
        return new CommissioningReport(machine, pattern.id(), CommissioningStatus.READY,
                inputs, outputs, requirements, "deterministic_virtual_plan");
    }

    public static CommissioningReport rejected(
            ResourceLocation machine, CommissioningStatus status, String reason) {
        return new CommissioningReport(machine, null, status, List.of(), List.of(), List.of(), reason);
    }

    private CommissioningEngine() {
    }
}
