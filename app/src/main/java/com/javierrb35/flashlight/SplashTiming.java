package com.javierrb35.flashlight;

/** Total foreground-visible time for the automatic four-second splash. */
final class SplashTiming {
    static final long VISIBLE_MILLIS = 4_000L;

    private SplashTiming() { }

    static long remainingMillis(long remaining, long visibleElapsedMillis) {
        return Math.max(0L, remaining - Math.max(0L, visibleElapsedMillis));
    }
}
