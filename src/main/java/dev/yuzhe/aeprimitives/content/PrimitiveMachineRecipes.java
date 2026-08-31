package dev.yuzhe.aeprimitives.content;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final Map<Item, Item> CROPS = Map.of(
            Items.WHEAT_SEEDS, Items.WHEAT,
            Items.CARROT, Items.CARROT,
            Items.POTATO, Items.POTATO,
            Items.BEETROOT_SEEDS, Items.BEETROOT);
    private static final Map<Item, Item> TREES = Map.ofEntries(
            Map.entry(Items.OAK_SAPLING, Items.OAK_LOG),
            Map.entry(Items.BIRCH_SAPLING, Items.BIRCH_LOG),
            Map.entry(Items.SPRUCE_SAPLING, Items.SPRUCE_LOG),
            Map.entry(Items.JUNGLE_SAPLING, Items.JUNGLE_LOG),
            Map.entry(Items.ACACIA_SAPLING, Items.ACACIA_LOG),
            Map.entry(Items.DARK_OAK_SAPLING, Items.DARK_OAK_LOG),
            Map.entry(Items.CHERRY_SAPLING, Items.CHERRY_LOG),
            Map.entry(Items.MANGROVE_PROPAGULE, Items.MANGROVE_LOG),
            Map.entry(Items.AZALEA, Items.OAK_LOG),
            Map.entry(Items.FLOWERING_AZALEA, Items.OAK_LOG));
    private static final Set<Item> FLOWERS = Set.of(
            Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
            Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP,
            Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.SUNFLOWER,
            Items.LILAC, Items.ROSE_BUSH, Items.PEONY, Items.TORCHFLOWER);

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
        Item result = CROPS.get(starter.getItem());
        if (result == null || inventory.getStackInSlot(1).getCount() < 3
                || !inventory.getStackInSlot(1).is(Items.BONE_MEAL)) return null;
        return plan(new int[]{0, 3, 0}, new ItemStack(result));
    }

    private static Plan tree(ItemStackHandler inventory) {
        Item log = TREES.get(inventory.getStackInSlot(0).getItem());
        if (log == null || inventory.getStackInSlot(1).getCount() < 8
                || !inventory.getStackInSlot(1).is(Items.BONE_MEAL)) return null;
        return plan(new int[]{0, 8, 0}, new ItemStack(log, 4));
    }

    private static Plan growthRack(ItemStackHandler inventory) {
        var plant = inventory.getStackInSlot(0);
        if (!plant.is(Items.SUGAR_CANE) && !plant.is(Items.CACTUS) && !plant.is(Items.BAMBOO)
                && !plant.is(Items.KELP) && !plant.is(Items.WEEPING_VINES) && !plant.is(Items.TWISTING_VINES)) {
            return null;
        }
        return plan(new int[]{0, 0, 0}, plant.copyWithCount(1));
    }

    private static Plan bee(ItemStackHandler inventory) {
        var flower = inventory.getStackInSlot(0);
        if (!FLOWERS.contains(flower.getItem())) return null;
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
