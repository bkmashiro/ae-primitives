package dev.yuzhe.aeprimitives.pneumatic;

public final class AirBankMath {
    private AirBankMath() { }

    public static float pressure(long stored, long capacity, float ratingBar) {
        if (stored <= 0 || capacity <= 0 || ratingBar <= 0) return 0.0f;
        return ratingBar * Math.min(1.0f, (float) stored / (float) capacity);
    }

    public static int equalizingTransfer(float sourcePressure, float targetPressure, int targetVolume,
                                         int throughput, long available) {
        if (sourcePressure <= targetPressure || targetVolume <= 0 || throughput <= 0 || available <= 0) return 0;
        long pressureLimited = Math.max(1L, (long) Math.floor((sourcePressure - targetPressure) * targetVolume));
        return (int) Math.min(Math.min(pressureLimited, throughput), Math.min(available, Integer.MAX_VALUE));
    }
}
