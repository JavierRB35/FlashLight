package com.javierrb35.flashlight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MorsePatternTest {
    @Test
    public void sosHasThreeShortThreeLongThreeShortSignals() {
        MorsePattern.Segment[] segments = MorsePattern.sos();
        int[] expectedOnUnits = {1, 1, 1, 3, 3, 3, 1, 1, 1};
        int signal = 0;
        long totalDuration = 0;

        for (MorsePattern.Segment segment : segments) {
            totalDuration += segment.durationMillis;
            if (segment.lightOn) {
                assertEquals(expectedOnUnits[signal++] * MorsePattern.UNIT_MILLIS,
                        segment.durationMillis);
            }
        }

        assertEquals(expectedOnUnits.length, signal);
        assertEquals(18, segments.length);
        assertEquals(34 * MorsePattern.UNIT_MILLIS, totalDuration);
        assertTrue(segments[0].lightOn);
        assertFalse(segments[1].lightOn);
        assertEquals(3 * MorsePattern.UNIT_MILLIS, segments[5].durationMillis);
        assertEquals(3 * MorsePattern.UNIT_MILLIS, segments[11].durationMillis);
        assertEquals(7 * MorsePattern.UNIT_MILLIS, segments[17].durationMillis);
    }
}
