package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AirKeyType extends AEKeyType {
    public static final AirKeyType INSTANCE = new AirKeyType();

    private AirKeyType() {
        super(ResourceLocation.fromNamespaceAndPath(AePrimitivesPneumatic.MOD_ID, "compressed_air"),
                AirKey.class, Component.translatable("key_type.aeprimitives_pneumatic.compressed_air"));
    }

    @Override public MapCodec<AirKey> codec() { return AirKey.MAP_CODEC; }
    @Override public AirKey readFromPacket(RegistryFriendlyByteBuf buffer) { return AirKey.fromPacket(buffer); }
    @Override public String getUnitSymbol() { return " mL"; }
    @Override public int getAmountPerUnit() { return 1; }
    @Override public int getAmountPerOperation() { return 1; }
    @Override public int getAmountPerByte() { return 8; }
}
