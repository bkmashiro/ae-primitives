package dev.yuzhe.aeprimitives.content;

public enum MachineKind {
    FORTUNE("fortune_chamber", 20),
    TRANSFORMATION("transformation_chamber", 20),
    GENERATOR("resource_generator", 40),
    GROWTH("growth_chamber", 80),
    COMPOST("compost_chamber", 20),
    FOUNDRY("resonance_controller", 20),
    CONCRETE("concrete_curing_chamber", 40),
    SOIL("soil_processor", 100),
    DRIPSTONE("dripstone_reservoir", 1200),
    OXIDATION("oxidation_chamber", 1200),
    CROP("crop_cultivator", 80),
    TREE("tree_nursery", 200),
    GROWTH_RACK("growth_rack", 200),
    BEE("apiary_chamber", 600),
    BATCH("batch_gate", 20),
    COOLING("cooling_plate", 40);

    private final String id;
    private final int processingTicks;
    MachineKind(String id, int processingTicks) { this.id = id; this.processingTicks = processingTicks; }
    public String id() { return id; }
    public int processingTicks() { return processingTicks; }
}
