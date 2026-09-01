package dev.yuzhe.aeprimitives.pneumatic;

import java.util.Optional;

final class PneumaticAssemblyMath {
    // PneumaticCraft's pressure chamber interface charges 1000 mL for each item crossing either door.
    static final long AIR_PER_TRANSFERRED_ITEM = 1_000L;

    private PneumaticAssemblyMath() {
    }

    static Optional<AirPressureTier> tierFor(float requiredPressure) {
        if (!(requiredPressure > 0.0f)) return Optional.empty();
        if (requiredPressure <= AirPressureTier.BASIC.ratingBar()) return Optional.of(AirPressureTier.BASIC);
        if (requiredPressure <= AirPressureTier.REINFORCED.ratingBar()) return Optional.of(AirPressureTier.REINFORCED);
        return Optional.empty();
    }

    static long airCost(long inputItems, long outputItems) {
        if (inputItems < 0 || outputItems < 0) return -1;
        try {
            return Math.multiplyExact(Math.addExact(inputItems, outputItems), AIR_PER_TRANSFERRED_ITEM);
        } catch (ArithmeticException ignored) {
            return -1;
        }
    }

    static boolean canPayAtPressure(long stored, long capacity, long cost, float requiredPressure,
                                    AirPressureTier tier) {
        if (cost < 0 || stored < cost) return false;
        return AirBankMath.pressure(stored - cost, capacity, tier.ratingBar()) + 1.0e-4f >= requiredPressure;
    }
}
