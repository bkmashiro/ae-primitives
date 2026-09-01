package dev.yuzhe.aeprimitives.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class PhysicalMetricSelectionTest {
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("test", "a");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("test", "b");
    private static final ResourceLocation C = ResourceLocation.fromNamespaceAndPath("test", "c");

    @Test
    void choosesFirstMetricWhenSelectionIsMissing() {
        assertThat(PhysicalMetricSelection.next(List.of(C, A, B), null)).isEqualTo(A);
    }

    @Test
    void cyclesInStableIdOrder() {
        assertThat(PhysicalMetricSelection.next(List.of(C, A, B), A)).isEqualTo(B);
        assertThat(PhysicalMetricSelection.next(List.of(C, A, B), C)).isEqualTo(A);
    }

    @Test
    void preservesUnavailableSelectionUntilPlayerChangesIt() {
        var missing = ResourceLocation.fromNamespaceAndPath("gone", "metric");
        assertThat(PhysicalMetricSelection.resolve(List.of(A, B), missing)).isEqualTo(missing);
        assertThat(PhysicalMetricSelection.next(List.of(A, B), missing)).isEqualTo(A);
    }
}
