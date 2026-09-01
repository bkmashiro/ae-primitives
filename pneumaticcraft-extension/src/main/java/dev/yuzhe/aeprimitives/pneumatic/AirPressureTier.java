package dev.yuzhe.aeprimitives.pneumatic;

import net.minecraft.util.StringRepresentable;

public enum AirPressureTier implements StringRepresentable {
    BASIC("basic", 5.0f),
    REINFORCED("reinforced", 20.0f);

    private final String serializedName;
    private final float ratingBar;

    AirPressureTier(String serializedName, float ratingBar) {
        this.serializedName = serializedName;
        this.ratingBar = ratingBar;
    }

    @Override public String getSerializedName() { return serializedName; }
    public float ratingBar() { return ratingBar; }

    public static AirPressureTier byName(String name) {
        for (var tier : values()) if (tier.serializedName.equals(name)) return tier;
        return BASIC;
    }
}
