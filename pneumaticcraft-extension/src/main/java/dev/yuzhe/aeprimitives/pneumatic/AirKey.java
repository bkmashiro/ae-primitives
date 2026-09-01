package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AirKey extends AEKey {
    private static final Codec<AirPressureTier> TIER_CODEC = Codec.STRING.xmap(AirPressureTier::byName,
            AirPressureTier::getSerializedName);
    public static final MapCodec<AirKey> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TIER_CODEC.fieldOf("tier").forGetter(AirKey::tier)
    ).apply(instance, AirKey::of));
    private static final AirKey BASIC = new AirKey(AirPressureTier.BASIC);
    private static final AirKey REINFORCED = new AirKey(AirPressureTier.REINFORCED);

    private final AirPressureTier tier;

    private AirKey(AirPressureTier tier) { this.tier = tier; }

    public static AirKey of(AirPressureTier tier) {
        return tier == AirPressureTier.REINFORCED ? REINFORCED : BASIC;
    }

    public AirPressureTier tier() { return tier; }
    @Override public AEKeyType getType() { return AirKeyType.INSTANCE; }
    @Override public AirKey dropSecondary() { return this; }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("tier", tier.getSerializedName());
        return tag;
    }
    @Override public Object getPrimaryKey() { return tier; }
    @Override public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(AePrimitivesPneumatic.MOD_ID,
                tier.getSerializedName() + "_compressed_air");
    }
    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) { buffer.writeByte(tier.ordinal()); }
    public static AirKey fromPacket(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        return of(ordinal == AirPressureTier.REINFORCED.ordinal() ? AirPressureTier.REINFORCED : AirPressureTier.BASIC);
    }
    @Override public ItemStack wrapForDisplayOrFilter() {
        return new ItemStack(tier == AirPressureTier.REINFORCED
                ? PneumaticContent.REINFORCED_AIR_CELL.get() : PneumaticContent.BASIC_AIR_CELL.get());
    }
    @Override protected Component computeDisplayName() {
        return Component.translatable("key.aeprimitives_pneumatic." + tier.getSerializedName() + "_compressed_air");
    }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    @Override public boolean hasComponents() { return false; }
    @Override public boolean equals(Object other) { return this == other || other instanceof AirKey key && tier == key.tier; }
    @Override public int hashCode() { return Objects.hash(tier); }
    @Override public String toString() { return getId().toString(); }
}
