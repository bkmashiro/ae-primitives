package dev.yuzhe.aeprimitives.content;

public enum MachineKind {
    FORTUNE("fortune_chamber", 20, 4, MachineTier.ADVANCED),
    TRANSFORMATION("transformation_chamber", 20, 4, MachineTier.BASIC),
    GENERATOR("resource_generator", 40, 2, MachineTier.ADVANCED),
    GROWTH("growth_chamber", 80, 4, MachineTier.BASIC),
    COMPOST("compost_chamber", 20, 4, MachineTier.BASIC),
    FOUNDRY("resonance_controller", 20, 2, MachineTier.ULTIMATE),
    CONCRETE("concrete_curing_chamber", 40, 4, MachineTier.BASIC),
    SOIL("soil_processor", 100, 4, MachineTier.BASIC),
    DRIPSTONE("dripstone_reservoir", 1200, 2, MachineTier.ADVANCED),
    OXIDATION("oxidation_chamber", 1200, 4, MachineTier.ADVANCED),
    CROP("crop_cultivator", 80, 4, MachineTier.BASIC),
    TREE("tree_nursery", 200, 4, MachineTier.ADVANCED),
    GROWTH_RACK("growth_rack", 200, 2, MachineTier.ADVANCED),
    BEE("apiary_chamber", 600, 2, MachineTier.ADVANCED),
    BATCH("batch_gate", 20, 0, MachineTier.BASIC),
    COOLING("cooling_plate", 40, 4, MachineTier.ADVANCED);

    private final String id;
    private final MachineDefinition definition;

    MachineKind(String id, int processingTicks, int maxSpeedCards, MachineTier tier) {
        this.id = id;
        this.definition = new MachineDefinition(processingTicks, maxSpeedCards, tier);
    }

    public String id() { return id; }
    public MachineDefinition definition() { return definition; }
    public int processingTicks() { return definition.processingTicks(); }
    public int maxSpeedCards() { return definition.maxSpeedCards(); }
    public MachineTier tier() { return definition.tier(); }
    public boolean supportsPatternProvider() {
        return dev.yuzhe.aeprimitives.crafting.LazyPatternRegistry.supports(this);
    }
}
