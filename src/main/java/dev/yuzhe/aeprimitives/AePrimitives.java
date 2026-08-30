package dev.yuzhe.aeprimitives;

import dev.yuzhe.aeprimitives.client.ClientRegistration;
import dev.yuzhe.aeprimitives.content.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AePrimitives.MOD_ID)
public final class AePrimitives {
    public static final String MOD_ID = "aeprimitives";

    public AePrimitives(IEventBus modBus) {
        ModContent.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientRegistration.register(modBus);
    }
}
