package dev.yuzhe.aeprimitives.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.content.MachineKind;
import dev.yuzhe.aeprimitives.content.ModContent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.WeatheringCopper;

public final class LazyPatternRegistry {
    public static final Map<Item, Item> CROPS = Map.of(
            Items.WHEAT_SEEDS, Items.WHEAT,
            Items.CARROT, Items.CARROT,
            Items.POTATO, Items.POTATO,
            Items.BEETROOT_SEEDS, Items.BEETROOT);
    public static final Map<Item, Item> TREES = Map.ofEntries(
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
    public static final Set<Item> FLOWERS = Set.of(
            Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
            Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP,
            Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.SUNFLOWER,
            Items.LILAC, Items.ROSE_BUSH, Items.PEONY, Items.TORCHFLOWER);
    public static final Set<Item> GROWTH_PLANTS = Set.of(
            Items.SUGAR_CANE, Items.CACTUS, Items.BAMBOO, Items.KELP,
            Items.WEEPING_VINES, Items.TWISTING_VINES);

    private static final EnumMap<MachineKind, List<LazyPrimitivePattern>> CACHE = new EnumMap<>(MachineKind.class);
    private static int revision;

    public static synchronized List<LazyPrimitivePattern> patternsFor(MachineKind kind) {
        return CACHE.computeIfAbsent(kind, LazyPatternRegistry::build);
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized void invalidate() {
        CACHE.clear();
        revision++;
        DynamicPatternProvider.refreshAll();
    }

    public static boolean supports(MachineKind kind) {
        return switch (kind) {
            case GROWTH, CONCRETE, SOIL, DRIPSTONE, OXIDATION, CROP, TREE, GROWTH_RACK, BEE, COOLING -> true;
            default -> false;
        };
    }

    private static List<LazyPrimitivePattern> build(MachineKind kind) {
        var specs = new ArrayList<PrimitivePatternSpec>();
        switch (kind) {
            case GROWTH -> growth(specs);
            case CONCRETE -> concrete(specs);
            case SOIL -> soil(specs);
            case DRIPSTONE -> dripstone(specs);
            case OXIDATION -> oxidation(specs);
            case CROP -> crops(specs);
            case TREE -> trees(specs);
            case GROWTH_RACK -> growthRack(specs);
            case BEE -> apiary(specs);
            case COOLING -> cooling(specs);
            default -> { return List.of(); }
        }
        var deduplicated = new LinkedHashMap<ResourceLocation, LazyPrimitivePattern>();
        for (var spec : specs) {
            deduplicated.put(spec.id(), new LazyPrimitivePattern(spec, ModContent.PATTERN_PROVIDER_CARD.get()));
        }
        return List.copyOf(deduplicated.values());
    }

    private static void growth(List<PrimitivePatternSpec> out) {
        add(out, MachineKind.GROWTH, "certus", List.of(consumed(appeng.core.definitions.AEItems.CERTUS_QUARTZ_DUST, 1), consumed(Items.SAND, 1)), stack(appeng.core.definitions.AEItems.CERTUS_QUARTZ_CRYSTAL, 2));
        add(out, MachineKind.GROWTH, "fluix", List.of(consumed(appeng.core.definitions.AEItems.FLUIX_DUST, 1), consumed(Items.SAND, 1)), stack(appeng.core.definitions.AEItems.FLUIX_CRYSTAL, 2));
    }

    private static void concrete(List<PrimitivePatternSpec> out) {
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof ConcretePowderBlock) || block.asItem() == Items.AIR) continue;
            var id = BuiltInRegistries.BLOCK.getKey(block);
            var path = id.getPath();
            if (!path.endsWith("_concrete_powder")) continue;
            var solid = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(0, path.length() - "_powder".length())));
            if (solid == Blocks.AIR || solid.asItem() == Items.AIR) continue;
            add(out, MachineKind.CONCRETE, id.toString(), List.of(consumed(block, 1)), stack(solid, 1));
        }
    }

    private static void soil(List<PrimitivePatternSpec> out) {
        add(out, MachineKind.SOIL, "mud_to_clay", List.of(consumed(Items.MUD, 1)), stack(Items.CLAY, 1));
        add(out, MachineKind.SOIL, "dirt_to_mud", List.of(consumed(Items.DIRT, 1), consumed(Items.WATER_BUCKET, 1)), stack(Items.MUD, 1), stack(Items.BUCKET, 1));
    }

    private static void dripstone(List<PrimitivePatternSpec> out) {
        add(out, MachineKind.DRIPSTONE, "water", List.of(catalyst(Items.WATER_BUCKET), consumed(Items.BUCKET, 1)), stack(Items.WATER_BUCKET, 1));
        add(out, MachineKind.DRIPSTONE, "lava", List.of(catalyst(Items.LAVA_BUCKET), consumed(Items.BUCKET, 1)), stack(Items.LAVA_BUCKET, 1));
    }

    private static void oxidation(List<PrimitivePatternSpec> out) {
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block.asItem() instanceof BlockItem) || block.asItem() == Items.AIR) continue;
            WeatheringCopper.getNext(block).ifPresent(next -> {
                if (next.asItem() != Items.AIR) {
                    var id = BuiltInRegistries.BLOCK.getKey(block);
                    add(out, MachineKind.OXIDATION, id.toString(), List.of(consumed(block, 1)), stack(next, 1));
                }
            });
        }
    }

    private static void crops(List<PrimitivePatternSpec> out) {
        CROPS.forEach((starter, result) -> add(out, MachineKind.CROP, itemId(starter),
                List.of(catalyst(starter), consumed(Items.BONE_MEAL, 3)), stack(result, 1)));
    }

    private static void trees(List<PrimitivePatternSpec> out) {
        TREES.forEach((starter, result) -> add(out, MachineKind.TREE, itemId(starter),
                List.of(catalyst(starter), consumed(Items.BONE_MEAL, 8)), stack(result, 4)));
    }

    private static void growthRack(List<PrimitivePatternSpec> out) {
        for (var plant : GROWTH_PLANTS) {
            add(out, MachineKind.GROWTH_RACK, itemId(plant), List.of(catalyst(plant)), stack(plant, 1));
        }
    }

    private static void apiary(List<PrimitivePatternSpec> out) {
        for (var flower : FLOWERS) {
            add(out, MachineKind.BEE, itemId(flower) + "/honey", List.of(catalyst(flower), consumed(Items.GLASS_BOTTLE, 1)), stack(Items.HONEY_BOTTLE, 1));
        }
    }

    private static void cooling(List<PrimitivePatternSpec> out) {
        add(out, MachineKind.COOLING, "obsidian", List.of(consumed(Items.LAVA_BUCKET, 1), consumed(Items.ICE, 1)), stack(Items.OBSIDIAN, 1), stack(Items.BUCKET, 1));
        add(out, MachineKind.COOLING, "basalt", List.of(consumed(Items.LAVA_BUCKET, 1), catalyst(Items.BLUE_ICE), catalyst(Items.SOUL_SOIL)), stack(Items.BASALT, 1), stack(Items.BUCKET, 1));
    }

    private static void add(List<PrimitivePatternSpec> out, MachineKind kind, String variant,
                            List<PrimitivePatternSpec.Input> inputs, GenericStack... outputs) {
        String safe = variant.replace(':', '/');
        out.add(new PrimitivePatternSpec(ResourceLocation.fromNamespaceAndPath(AePrimitives.MOD_ID, "dynamic/" + kind.id() + "/" + safe), kind, inputs, List.of(outputs)));
    }

    private static PrimitivePatternSpec.Input consumed(ItemLike item, long amount) {
        return PrimitivePatternSpec.Input.consumed(item, amount);
    }

    private static PrimitivePatternSpec.Input catalyst(ItemLike item) {
        return PrimitivePatternSpec.Input.catalyst(item);
    }

    private static GenericStack stack(ItemLike item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private LazyPatternRegistry() {}
}
