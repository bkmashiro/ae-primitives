package dev.yuzhe.aeprimitives.pneumatic;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricContext;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricPresentation;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricProvider;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricProviders;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricSample;
import dev.yuzhe.aeprimitives.metrics.PhysicalMetricState;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class PneumaticMetricProviders {
    private PneumaticMetricProviders() {
    }

    static void register() {
        for (var tier : AirPressureTier.values()) {
            register(tier, MetricKind.PRESSURE);
            register(tier, MetricKind.STORED_AIR);
            register(tier, MetricKind.VOLUME);
        }
        PhysicalMetricProviders.register(new MaximumTierProvider());
    }

    private static void register(AirPressureTier tier, MetricKind kind) {
        PhysicalMetricProviders.register(new TierMetricProvider(tier, kind));
    }

    private record TierMetricProvider(AirPressureTier tier, MetricKind kind) implements PhysicalMetricProvider {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(AePrimitivesPneumatic.MOD_ID,
                    tier.getSerializedName() + "_" + kind.path);
        }

        @Override
        public Set<AEKey> watchedStorageKeys() {
            return Set.of(AirKey.of(tier));
        }

        @Override
        public PhysicalMetricSample sample(PhysicalMetricContext context) {
            var bank = snapshot(context, tier);
            double value = switch (kind) {
                case PRESSURE -> bank.pressureBar();
                case STORED_AIR -> bank.stored();
                case VOLUME -> bank.capacity();
            };
            double maximum = switch (kind) {
                case PRESSURE -> tier.ratingBar();
                case STORED_AIR, VOLUME -> Math.max(1, bank.capacity());
            };
            return new PhysicalMetricSample(id(), labelKey(id()), kind.unit, value, 0.0, maximum,
                    kind.presentation, PhysicalMetricState.NORMAL);
        }
    }

    private static final class MaximumTierProvider implements PhysicalMetricProvider {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
                AePrimitivesPneumatic.MOD_ID, "maximum_pressure_tier");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public Set<AEKey> watchedStorageKeys() {
            return Set.of(AirKey.of(AirPressureTier.BASIC), AirKey.of(AirPressureTier.REINFORCED));
        }

        @Override
        public PhysicalMetricSample sample(PhysicalMetricContext context) {
            double rating = snapshot(context, AirPressureTier.REINFORCED).capacity() > 0
                    ? AirPressureTier.REINFORCED.ratingBar()
                    : snapshot(context, AirPressureTier.BASIC).capacity() > 0
                    ? AirPressureTier.BASIC.ratingBar() : 0.0;
            return new PhysicalMetricSample(ID, labelKey(ID), "bar", rating, 0.0,
                    AirPressureTier.REINFORCED.ratingBar(), PhysicalMetricPresentation.DISCRETE,
                    rating > 0 ? PhysicalMetricState.NORMAL : PhysicalMetricState.UNAVAILABLE);
        }
    }

    private static PressurePortBlockEntity.BankSnapshot snapshot(PhysicalMetricContext context,
                                                                 AirPressureTier tier) {
        var storage = context.grid().getStorageService().getInventory();
        var key = AirKey.of(tier);
        long probe = Long.MAX_VALUE / 4;
        long stored = storage.extract(key, probe, Actionable.SIMULATE, context.actionSource());
        long free = storage.insert(key, probe, Actionable.SIMULATE, context.actionSource());
        long capacity = Long.MAX_VALUE - stored < free ? Long.MAX_VALUE : stored + free;
        return new PressurePortBlockEntity.BankSnapshot(stored, capacity,
                AirBankMath.pressure(stored, capacity, tier.ratingBar()));
    }

    private static String labelKey(ResourceLocation id) {
        return "metric." + id.getNamespace() + "." + id.getPath();
    }

    private enum MetricKind {
        PRESSURE("pressure", "bar", PhysicalMetricPresentation.NEEDLE),
        STORED_AIR("stored_air", "mL", PhysicalMetricPresentation.BAR),
        VOLUME("volume", "mL", PhysicalMetricPresentation.NUMERIC);

        private final String path;
        private final String unit;
        private final PhysicalMetricPresentation presentation;

        MetricKind(String path, String unit, PhysicalMetricPresentation presentation) {
            this.path = path;
            this.unit = unit;
            this.presentation = presentation;
        }
    }
}
