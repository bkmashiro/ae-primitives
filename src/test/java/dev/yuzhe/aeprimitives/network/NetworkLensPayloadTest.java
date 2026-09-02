package dev.yuzhe.aeprimitives.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.yuzhe.aeprimitives.diagnostics.NetworkLensTarget;
import dev.yuzhe.aeprimitives.diagnostics.NetworkLensTargetKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NetworkLensPayloadTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aeprimitives", "test");

    @Test
    void preservesWorldAndVirtualTargetsWithoutFakeCoordinates() {
        var payload = new NetworkLensPayload(ResourceLocation.withDefaultNamespace("overworld"), List.of(
                NetworkLensTarget.world(new BlockPos(1, 2, 3), NetworkLensTargetKind.MACHINE, ID, -1, "machine"),
                NetworkLensTarget.textual(NetworkLensTargetKind.VIRTUAL_LANE, ID, 2, "missing resource")), 100);
        assertEquals(new BlockPos(1, 2, 3), payload.targets().getFirst().pos());
        assertEquals(null, payload.targets().get(1).pos());
        assertEquals(2, payload.targets().get(1).lane());
    }

    @Test
    void rejectsOversizedPayloadInsteadOfSilentlyTruncating() {
        var targets = new ArrayList<NetworkLensTarget>();
        for (int index = 0; index <= NetworkLensPayload.MAX_TARGETS; index++) {
            targets.add(NetworkLensTarget.textual(NetworkLensTargetKind.VIRTUAL_LANE, ID, index, "lane"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkLensPayload(ResourceLocation.withDefaultNamespace("overworld"), targets, 100));
        assertTrue(targets.size() > NetworkLensPayload.MAX_TARGETS);
    }
}
