package dev.yuzhe.aeprimitives.kinetics.content;

import com.simibubi.create.AllRecipeTypes;

public enum KineticMachineKind {
    PRESS("me_press", AllRecipeTypes.PRESSING, 8.0f),
    CRUSHER("me_crusher", AllRecipeTypes.CRUSHING, 16.0f),
    FAN("me_catalyst_chamber", null, 8.0f);

    private final String id;
    private final AllRecipeTypes recipeType;
    private final float stressImpact;

    KineticMachineKind(String id, AllRecipeTypes recipeType, float stressImpact) {
        this.id = id;
        this.recipeType = recipeType;
        this.stressImpact = stressImpact;
    }

    public String id() { return id; }
    public AllRecipeTypes recipeType() { return recipeType; }
    public float stressImpact() { return stressImpact; }
}
