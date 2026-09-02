package dev.yuzhe.aeprimitives;

import dev.yuzhe.aeprimitives.client.ClientRegistration;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.crafting.LazyPatternRegistry;
import dev.yuzhe.aeprimitives.diagnostics.ProcessAnalyzerPreviewCommand;
import dev.yuzhe.aeprimitives.network.ProcessAnalyzerPayload;
import dev.yuzhe.aeprimitives.network.NetworkLensPayload;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AePrimitives.MOD_ID)
public final class AePrimitives {
    public static final String MOD_ID = "aeprimitives";

    public AePrimitives(IEventBus modBus) {
        ModContent.register(modBus);
        modBus.addListener(ProcessAnalyzerPayload::register);
        modBus.addListener(NetworkLensPayload::register);
        NeoForge.EVENT_BUS.addListener(AePrimitives::addReloadListener);
        if (Boolean.getBoolean("mcHarness.enabled")) {
            NeoForge.EVENT_BUS.addListener(AePrimitives::registerHarnessCommands);
        }
        if (FMLEnvironment.dist == Dist.CLIENT) ClientRegistration.register(modBus);
    }

    private static void registerHarnessCommands(RegisterCommandsEvent event) {
        ProcessAnalyzerPreviewCommand.register(event.getDispatcher());
    }

    private static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener((barrier, resources, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                barrier.wait(null).thenRunAsync(LazyPatternRegistry::invalidate, gameExecutor));
    }
}
