package dev.yuzhe.aeprimitives.content;

public record MachineDefinition(int processingTicks, int maxSpeedCards, MachineTier tier) {
    public MachineDefinition {
        if (processingTicks <= 0) throw new IllegalArgumentException("processingTicks must be positive");
        if (maxSpeedCards < 0 || maxSpeedCards > 4) throw new IllegalArgumentException("maxSpeedCards must be between 0 and 4");
    }
}
