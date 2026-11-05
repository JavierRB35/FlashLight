package com.javierrb35.flashlight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StrobeTimingTest {
    @Test
    public void calculatesHalfPeriodForSupportedRates() {
        assertEquals(500L, StrobeTiming.halfPeriodMillis(1));
        assertEquals(250L, StrobeTiming.halfPeriodMillis(2));
        assertEquals(166L, StrobeTiming.halfPeriodMillis(3));
        assertEquals(125L, StrobeTiming.halfPeriodMillis(4));
    }

    @Test
    public void clampsRatesToSafeSupportedRange() {
        assertEquals(500L, StrobeTiming.halfPeriodMillis(0));
        assertEquals(125L, StrobeTiming.halfPeriodMillis(5));
    }
}
