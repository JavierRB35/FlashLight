package com.javierrb35.flashlight;

/** Monotonic, rotation-safe splash duration. No artificial delay after returning from background. */
final class SplashTiming {
    static final long VISIBLE_MILLIS = 4_000L;

    private SplashTiming() { }

    static long remainingMillis(long startedAtMillis, long nowMillis) {
        if (nowMillis < startedAtMillis) return VISIBLE_MILLIS;
        return Math.max(0L, VISIBLE_MILLIS - (nowMillis - startedAtMillis));
    }
}
