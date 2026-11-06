package com.javierrb35.flashlight;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class SplashTimingTest {
    @Test
    public void splashRunsForFourSecondsOfForegroundTime() {
        long remaining = SplashTiming.VISIBLE_MILLIS;
        assertEquals(4_000L, remaining);
        remaining = SplashTiming.remainingMillis(remaining, 1_000L);
        assertEquals(3_000L, remaining);
        // Time while in background is not counted.
        remaining = SplashTiming.remainingMillis(remaining, 0L);
        assertEquals(3_000L, remaining);
        assertEquals(0L, SplashTiming.remainingMillis(remaining, 3_000L));
    }

    @Test
    public void elapsedTimeIsClampedAndCannotBecomeNegative() {
        assertEquals(0L, SplashTiming.remainingMillis(1_000L, 5_000L));
        assertEquals(1_000L, SplashTiming.remainingMillis(1_000L, -20L));
    }

    @Test
    public void repeatedFrameUpdatesMeasureFromTheSameVisibleIntervalStart() {
        long remainingAtResume = SplashTiming.VISIBLE_MILLIS;

        assertEquals(3_984L, SplashTiming.remainingMillis(remainingAtResume, 16L));
        assertEquals(3_968L, SplashTiming.remainingMillis(remainingAtResume, 32L));
        assertEquals(3_000L, SplashTiming.remainingMillis(remainingAtResume, 1_000L));
        assertEquals(0L, SplashTiming.remainingMillis(remainingAtResume, 4_000L));
    }
}
