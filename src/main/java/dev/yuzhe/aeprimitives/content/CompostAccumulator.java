package dev.yuzhe.aeprimitives.content;

final class CompostAccumulator {
    private static final float COMPLETION = 7.0f;

    record Result(float progress, boolean completed) {}

    static Result add(float progress, float compostChance) {
        float next = progress + Math.max(0, Math.min(1, compostChance));
        if (next + 0.000001f < COMPLETION) return new Result(next, false);
        return new Result(next - COMPLETION, true);
    }

    private CompostAccumulator() {}
}
