package com.javierrb35.flashlight;

final class StrobeTiming {
    static final int MIN_FLASHES_PER_SECOND = 1;
    static final int MAX_FLASHES_PER_SECOND = 4;

    private StrobeTiming() {}

    static long halfPeriodMillis(int flashesPerSecond) {
        int bounded = Math.max(MIN_FLASHES_PER_SECOND,
                Math.min(MAX_FLASHES_PER_SECOND, flashesPerSecond));
        return 500L / bounded;
    }
}
