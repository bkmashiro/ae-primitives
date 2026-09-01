package dev.yuzhe.aeprimitives.powah;
import dev.yuzhe.aeprimitives.powah.content.PowahContent;
import dev.yuzhe.aeprimitives.powah.content.EnergizingVirtualLaneExecutor;
import dev.yuzhe.aeprimitives.space.VirtualMachineLaneExecutors;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
@Mod(AePrimitivesPowah.MOD_ID) public final class AePrimitivesPowah {public static final String MOD_ID="aeprimitives_powah";public AePrimitivesPowah(IEventBus bus){PowahContent.register(bus);VirtualMachineLaneExecutors.register(EnergizingVirtualLaneExecutor.INSTANCE);}}
