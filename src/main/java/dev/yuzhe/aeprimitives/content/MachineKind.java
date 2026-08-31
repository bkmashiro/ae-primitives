package dev.yuzhe.aeprimitives.content;

public enum MachineKind {
    FORTUNE("fortune_chamber", 20),
    TRANSFORMATION("transformation_chamber", 20),
    GENERATOR("resource_generator", 40),
    GROWTH("growth_chamber", 80),
    COMPOST("compost_chamber", 20),
    FOUNDRY("resonance_controller", 20);

    private final String id;
    private final int processingTicks;
    MachineKind(String id, int processingTicks) { this.id = id; this.processingTicks = processingTicks; }
    public String id() { return id; }
    public int processingTicks() { return processingTicks; }
}
