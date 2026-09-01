package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;
import dev.yuzhe.aeprimitives.content.MachineTier;
import net.minecraft.resources.ResourceLocation;

public enum KineticMachineKind {
    PRESS("me_press", AllRecipeTypes.PRESSING, 8.0f, MachineTier.ADVANCED, 8),
    CRUSHER("me_crusher", AllRecipeTypes.CRUSHING, 16.0f, MachineTier.ADVANCED, 8),
    FAN("me_catalyst_chamber", null, 8.0f, MachineTier.ADVANCED, 8),
    BASIN("me_basin_processor", null, 16.0f, MachineTier.ADVANCED, 8),
    FILLING("me_filling_station", null, 8.0f, MachineTier.ADVANCED, 8),
    DEPLOYER("me_deployer", null, 8.0f, MachineTier.ADVANCED, 8);

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

    public boolean acceptsOperation(ResourceLocation operation) {
        return switch (this) {
            case PRESS -> operation.equals(AllRecipeTypes.PRESSING.getId());
            case CRUSHER -> operation.equals(AllRecipeTypes.CRUSHING.getId());
            case BASIN -> operation.equals(AllRecipeTypes.MIXING.getId())
                    || operation.equals(AllRecipeTypes.COMPACTING.getId());
            case FILLING -> operation.equals(AllRecipeTypes.FILLING.getId())
                    || operation.equals(AllRecipeTypes.EMPTYING.getId());
            case DEPLOYER -> operation.equals(AllRecipeTypes.DEPLOYING.getId());
            case FAN -> false;
        };
    }
}
