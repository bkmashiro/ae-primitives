package dev.yuzhe.aeprimitives.botania;

import dev.yuzhe.aeprimitives.botania.content.BotaniaContent;
import dev.yuzhe.aeprimitives.botania.content.ManaPoolVirtualLaneExecutor;
import dev.yuzhe.aeprimitives.botania.content.RunicAltarVirtualLaneExecutor;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutors;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AePrimitivesBotania.MOD_ID)
public final class AePrimitivesBotania {
    public static final String MOD_ID = "aeprimitives_botania";

    public AePrimitivesBotania(IEventBus bus) {
        BotaniaContent.register(bus);
        VirtualMachineLaneExecutors.register(ManaPoolVirtualLaneExecutor.INSTANCE);
        VirtualMachineLaneExecutors.register(RunicAltarVirtualLaneExecutor.INSTANCE);
    }
}
