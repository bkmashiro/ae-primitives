package dev.yuzhe.aeprimitives.farmersdelight;

import dev.yuzhe.aeprimitives.farmersdelight.content.FarmersDelightContent;
import dev.yuzhe.aeprimitives.farmersdelight.content.CookingPotVirtualLaneExecutor;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutors;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AePrimitivesFarmersDelight.MOD_ID)
public final class AePrimitivesFarmersDelight {
    public static final String MOD_ID = "aeprimitives_farmersdelight";
    public AePrimitivesFarmersDelight(IEventBus modBus) {
        FarmersDelightContent.register(modBus);
        VirtualMachineLaneExecutors.register(CookingPotVirtualLaneExecutor.INSTANCE);
    }
}
