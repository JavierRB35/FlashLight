package com.javierrb35.flashlight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MorseDecoderTest {
    @Test
    public void decodesLettersAndWordGapsFromLightTiming() {
        MorseDecoder decoder = new MorseDecoder();
        long t = 0;
        decoder.sample(false, t);
        decoder.sample(true, t += 50);
        decoder.sample(false, t += 200);
        decoder.sample(false, t += 400);
        assertEquals("E", decoder.text());

        decoder.sample(true, t += 1000);
        decoder.sample(false, t += 600);
        decoder.sample(false, t += 500);
        assertEquals("E T", decoder.finish(t));
    }

    @Test
    public void decodesDashAndFlushesFinalLetter() {
        MorseDecoder decoder = new MorseDecoder();
        decoder.sample(true, 0);
        decoder.sample(false, 600);
        assertEquals("T", decoder.finish(1200));
    }

    @Test
    public void decodesAnEncodedMessageWithAWordGap() {
        MorseCodec.Encoding encoded = MorseCodec.encode("HI 5");
        MorseDecoder decoder = new MorseDecoder();
        long time = 0;
        decoder.sample(false, time);
        for (MorseCodec.Segment segment : encoded.segments) {
            decoder.sample(segment.lightOn, time);
            time += segment.durationMillis;
        }

        assertEquals("HI 5", decoder.finish(time + 1200));
    }
}
