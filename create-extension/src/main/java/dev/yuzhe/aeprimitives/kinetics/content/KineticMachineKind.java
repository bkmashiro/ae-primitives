package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import dev.yuzhe.aeprimitives.content.MachineTier;

public enum KineticMachineKind {
    PRESS("me_press", AllRecipeTypes.PRESSING, 8.0f, MachineTier.ADVANCED, 8),
    CRUSHER("me_crusher", AllRecipeTypes.CRUSHING, 16.0f, MachineTier.ADVANCED, 8),
    FAN("me_catalyst_chamber", null, 8.0f, MachineTier.ADVANCED, 8),
    BASIN("me_basin_processor", null, 16.0f, MachineTier.ADVANCED, 8),
    FILLING("me_filling_station", null, 8.0f, MachineTier.ADVANCED, 8);

    private final String id;
    private final AllRecipeTypes recipeType;
    private final float stressImpact;
    private final MachineTier tier;
    private final int maxParallelLanes;

    KineticMachineKind(String id, AllRecipeTypes recipeType, float stressImpact, MachineTier tier, int maxParallelLanes) {
        this.id = id;
        this.recipeType = recipeType;
        this.stressImpact = stressImpact;
        this.tier = tier;
        this.maxParallelLanes = maxParallelLanes;
    }

    public String id() { return id; }
    public AllRecipeTypes recipeType() { return recipeType; }
    public float stressImpact() { return stressImpact; }
    public MachineTier tier() { return tier; }
    public int maxParallelLanes() { return maxParallelLanes; }
}
