package dev.yuzhe.aeprimitives.compat.ponder;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.content.ModContent;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import appeng.core.definitions.AEBlocks;

/** Optional Ponder tutorial; this class is loaded only when Ponder is present. */
public final class AePrimitivesPonderPlugin implements PonderPlugin {
    public static void register() {
        PonderIndex.addPlugin(new AePrimitivesPonderPlugin());
    }

    @Override
    public String getModId() {
        return AePrimitives.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModContent.PROCESS_ANALYZER.getId())
                .addStoryBoard("process_analyzer", AePrimitivesPonderPlugin::processAnalyzer);
    }

    private static void processAnalyzer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("process_analyzer", "Trace an abstract process through the ME network");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        var providerA = util.grid().at(1, 1, 2);
        var providerB = util.grid().at(3, 1, 2);
        scene.world().setBlock(providerA, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), false);
        scene.world().setBlock(providerB, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), false);
        scene.world().showSection(util.select().position(providerA), Direction.DOWN);
        scene.world().showSection(util.select().position(providerB), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("Operation Patterns describe capabilities such as pressing or mixing. Sequence Patterns connect those capabilities into one process.")
                .pointAt(util.vector().centerOf(providerA))
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showLine(PonderPalette.OUTPUT,
                util.vector().centerOf(providerA), util.vector().centerOf(providerB), 70);
        scene.overlay().showText(70)
                .text("Use the Process Analyzer on any Pattern Provider. The graph is scoped to that ME network and shows ready, busy, and missing steps.")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(providerB))
                .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }
}
