package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(AePrimitivesPneumatic.MOD_ID)
public final class AePrimitivesPneumatic {
    public static final String MOD_ID = "aeprimitives_pneumaticcraft";

    public AePrimitivesPneumatic(IEventBus bus) {
        bus.addListener(AePrimitivesPneumatic::registerKeyType);
        PneumaticContent.register(bus);
    }

    private static void registerKeyType(RegisterEvent event) {
        if (event.getRegistryKey() == AEKeyType.REGISTRY_KEY) {
            AEKeyTypes.register(AirKeyType.INSTANCE);
        }
    }
}
