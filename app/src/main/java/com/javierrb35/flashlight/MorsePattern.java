package com.javierrb35.flashlight;

import java.util.ArrayList;
import java.util.List;

final class MorsePattern {
    static final long UNIT_MILLIS = 200L;

    static final class Segment {
        final boolean lightOn;
        final long durationMillis;

        Segment(boolean lightOn, int units) {
            this.lightOn = lightOn;
            this.durationMillis = UNIT_MILLIS * units;
        }
    }

    private MorsePattern() {}

    static Segment[] sos() {
        List<Segment> segments = new ArrayList<>();
        appendLetter(segments, "...", 3);
        appendLetter(segments, "---", 3);
        appendLetter(segments, "...", 7);
        return segments.toArray(new Segment[0]);
    }

    private static void appendLetter(List<Segment> segments, String symbols, int trailingGap) {
        for (int i = 0; i < symbols.length(); i++) {
            segments.add(new Segment(true, symbols.charAt(i) == '.' ? 1 : 3));
            if (i < symbols.length() - 1) segments.add(new Segment(false, 1));
        }
        segments.add(new Segment(false, trailingGap));
    }
}
