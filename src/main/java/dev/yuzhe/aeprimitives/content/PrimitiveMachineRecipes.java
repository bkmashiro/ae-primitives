package dev.yuzhe.aeprimitives.content;

import java.util.List;
import dev.yuzhe.aeprimitives.crafting.LazyPatternRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.neoforged.neoforge.items.ItemStackHandler;

final class PrimitiveMachineRecipes {

    record Plan(int[] consumed, int[] damaged, List<ItemStack> outputs) {
        void apply(ItemStackHandler inventory) {
            for (int slot = 0; slot < 3; slot++) {
                if (consumed[slot] > 0) inventory.extractItem(slot, consumed[slot], false);
                if (damaged[slot] <= 0) continue;
                var tool = inventory.getStackInSlot(slot).copy();
                int nextDamage = tool.getDamageValue() + damaged[slot];
                if (nextDamage >= tool.getMaxDamage()) tool.shrink(1);
                else tool.setDamageValue(nextDamage);
                inventory.setStackInSlot(slot, tool);
            }
        }
    }

    static Plan find(MachineKind kind, ItemStackHandler inventory) {
        return switch (kind) {
            case CONCRETE -> concrete(inventory);
            case SOIL -> soil(inventory);
            case DRIPSTONE -> dripstone(inventory);
            case OXIDATION -> oxidation(inventory);
            case CROP -> crop(inventory);
            case TREE -> tree(inventory);
            case GROWTH_RACK -> growthRack(inventory);
            case BEE -> bee(inventory);
            case BATCH -> batch(inventory);
            case COOLING -> cooling(inventory);
            default -> null;
        };
    }

    private static Plan concrete(ItemStackHandler inventory) {
        var input = inventory.getStackInSlot(0);
        if (!(input.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof ConcretePowderBlock)) return null;
        ResourceLocation powderId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        String path = powderId.getPath();
        if (!path.endsWith("_concrete_powder")) return null;
        var solid = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                powderId.getNamespace(), path.substring(0, path.length() - "_powder".length())));
        if (solid == Blocks.AIR) return null;
        return plan(new int[]{1, 0, 0}, new ItemStack(solid));
    }

    private static Plan soil(ItemStackHandler inventory) {
        var material = inventory.getStackInSlot(0);
        if (material.is(Items.MUD)) return plan(new int[]{1, 0, 0}, new ItemStack(Items.CLAY));
        if (material.is(Items.DIRT) && inventory.getStackInSlot(1).is(Items.WATER_BUCKET)) {
            return plan(new int[]{1, 1, 0}, new ItemStack(Items.MUD), new ItemStack(Items.BUCKET));
        }
        return null;
    }

    private static Plan dripstone(ItemStackHandler inventory) {
        var source = inventory.getStackInSlot(0);
        if (!inventory.getStackInSlot(1).is(Items.BUCKET)) return null;
        if (source.is(Items.LAVA_BUCKET)) return plan(new int[]{0, 1, 0}, new ItemStack(Items.LAVA_BUCKET));
        if (source.is(Items.WATER_BUCKET)) return plan(new int[]{0, 1, 0}, new ItemStack(Items.WATER_BUCKET));
        return null;
    }

    private static Plan oxidation(ItemStackHandler inventory) {
        var input = inventory.getStackInSlot(0);
        if (!(input.getItem() instanceof BlockItem blockItem)) return null;
        return WeatheringCopper.getNext(blockItem.getBlock())
                .map(next -> plan(new int[]{1, 0, 0}, new ItemStack(next)))
                .orElse(null);
    }

    private static Plan crop(ItemStackHandler inventory) {
        var starter = inventory.getStackInSlot(0);
        Item result = LazyPatternRegistry.CROPS.get(starter.getItem());
        if (result == null || inventory.getStackInSlot(1).getCount() < 3
                || !inventory.getStackInSlot(1).is(Items.BONE_MEAL)) return null;
        return plan(new int[]{0, 3, 0}, new ItemStack(result));
    }

    private static Plan tree(ItemStackHandler inventory) {
        Item log = LazyPatternRegistry.TREES.get(inventory.getStackInSlot(0).getItem());
        if (log == null || inventory.getStackInSlot(1).getCount() < 8
                || !inventory.getStackInSlot(1).is(Items.BONE_MEAL)) return null;
        return plan(new int[]{0, 8, 0}, new ItemStack(log, 4));
    }

    private static Plan growthRack(ItemStackHandler inventory) {
        var plant = inventory.getStackInSlot(0);
        if (!LazyPatternRegistry.GROWTH_PLANTS.contains(plant.getItem())) {
            return null;
        }
        return plan(new int[]{0, 0, 0}, plant.copyWithCount(1));
    }

    private static Plan bee(ItemStackHandler inventory) {
        var flower = inventory.getStackInSlot(0);
        if (!LazyPatternRegistry.FLOWERS.contains(flower.getItem())) return null;
        var tool = inventory.getStackInSlot(1);
        if (tool.is(Items.GLASS_BOTTLE)) {
            return plan(new int[]{0, 1, 0}, new ItemStack(Items.HONEY_BOTTLE));
        }
        if (tool.is(Items.SHEARS)) {
            return new Plan(new int[3], new int[]{0, 1, 0}, List.of(new ItemStack(Items.HONEYCOMB, 3)));
        }
        return null;
    }

    private static Plan batch(ItemStackHandler inventory) {
        var input = inventory.getStackInSlot(0);
        if (input.isEmpty() || input.getMaxStackSize() < 8 || input.getCount() < 8) return null;
        return plan(new int[]{8, 0, 0}, input.copyWithCount(8));
    }

    private static Plan cooling(ItemStackHandler inventory) {
        var hot = inventory.getStackInSlot(0);
        var coolant = inventory.getStackInSlot(1);
        if (!hot.is(Items.LAVA_BUCKET)) return null;
        if (coolant.is(Items.ICE)) {
            return plan(new int[]{1, 1, 0}, new ItemStack(Items.OBSIDIAN), new ItemStack(Items.BUCKET));
        }
        if (coolant.is(Items.BLUE_ICE) && inventory.getStackInSlot(2).is(Items.SOUL_SOIL)) {
            return plan(new int[]{1, 0, 0}, new ItemStack(Items.BASALT), new ItemStack(Items.BUCKET));
        }
        return null;
    }

    private static Plan plan(int[] consumed, ItemStack... outputs) {
        return new Plan(consumed, new int[3], List.of(outputs));
    }

    private PrimitiveMachineRecipes() {}
}
