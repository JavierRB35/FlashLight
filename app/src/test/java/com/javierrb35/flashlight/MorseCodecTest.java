package com.javierrb35.flashlight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MorseCodecTest {
    @Test
    public void normalizesSpanishAccentsAndEncodesWordsAndPunctuation() {
        MorseCodec.Encoding encoding = MorseCodec.encode("¡Árbol, niño!");

        assertEquals("ARBOL,NINO!", encoding.text);
        assertEquals(1, encoding.skippedCharacters);
        assertEquals(37, encoding.flashCount);
        assertFalse(encoding.segments.isEmpty());
        assertFalse(encoding.segments.get(encoding.segments.size() - 1).lightOn);
    }

    @Test
    public void emitsStandardDotDashAndLetterGapDurations() {
        MorseCodec.Encoding encoding = MorseCodec.encode("ET");

        assertEquals(2, encoding.flashCount);
        assertEquals(4, encoding.segments.size());
        assertTrue(encoding.segments.get(0).lightOn);
        assertEquals(200L, encoding.segments.get(0).durationMillis);
        assertFalse(encoding.segments.get(1).lightOn);
        assertEquals(600L, encoding.segments.get(1).durationMillis);
        assertTrue(encoding.segments.get(2).lightOn);
        assertEquals(600L, encoding.segments.get(2).durationMillis);
        assertFalse(encoding.segments.get(3).lightOn);
        assertEquals(1400L, encoding.segments.get(3).durationMillis);
    }
}
