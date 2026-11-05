package com.javierrb35.flashlight;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MorseCodec {
    static final long UNIT_MILLIS = 200L;

    static final class Segment {
        final boolean lightOn;
        final long durationMillis;

        Segment(boolean lightOn, long durationMillis) {
            this.lightOn = lightOn;
            this.durationMillis = durationMillis;
        }
    }

    static final class Encoding {
        final String text;
        final List<Segment> segments;
        final int skippedCharacters;
        final int flashCount;

        Encoding(String text, List<Segment> segments, int skippedCharacters, int flashCount) {
            this.text = text;
            this.segments = segments;
            this.skippedCharacters = skippedCharacters;
            this.flashCount = flashCount;
        }
    }

    private static final Map<Character, String> CODES = new HashMap<>();
    private static final Map<String, Character> LETTERS = new HashMap<>();

    static {
        add('A', ".-"); add('B', "-..."); add('C', "-.-."); add('D', "-..");
        add('E', "."); add('F', "..-."); add('G', "--."); add('H', "....");
        add('I', ".."); add('J', ".---"); add('K', "-.-"); add('L', ".-..");
        add('M', "--"); add('N', "-."); add('O', "---"); add('P', ".--.");
        add('Q', "--.-"); add('R', ".-."); add('S', "..."); add('T', "-");
        add('U', "..-"); add('V', "...-"); add('W', ".--"); add('X', "-..-");
        add('Y', "-.--"); add('Z', "--..");
        add('0', "-----"); add('1', ".----"); add('2', "..---"); add('3', "...--");
        add('4', "....-"); add('5', "....."); add('6', "-...."); add('7', "--...");
        add('8', "---.."); add('9', "----.");
        add('.', ".-.-.-"); add(',', "--..--"); add('?', "..--.."); add('!', "-.-.--");
    }

    private MorseCodec() {}

    private static void add(char character, String code) {
        CODES.put(character, code);
        LETTERS.put(code, character);
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return Normalizer.normalize(decomposed.replaceAll("\\p{M}+", ""), Normalizer.Form.NFC)
                .toUpperCase(Locale.ROOT);
    }

    static Encoding encode(String source) {
        String normalized = normalize(source);
        List<Segment> segments = new ArrayList<>();
        StringBuilder transmitted = new StringBuilder();
        int skipped = 0;
        boolean pendingWordGap = false;
        boolean hasPreviousCharacter = false;
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (Character.isWhitespace(character)) {
                if (hasPreviousCharacter) pendingWordGap = true;
                continue;
            }
            String code = CODES.get(character);
            if (code == null) {
                skipped++;
                continue;
            }
            if (hasPreviousCharacter) {
                append(segments, false, (pendingWordGap ? 7 : 3) * UNIT_MILLIS);
            }
            pendingWordGap = false;
            hasPreviousCharacter = true;
            transmitted.append(character);
            for (int j = 0; j < code.length(); j++) {
                append(segments, true, (code.charAt(j) == '.' ? 1 : 3) * UNIT_MILLIS);
                if (j < code.length() - 1) append(segments, false, UNIT_MILLIS);
            }
        }
        if (hasPreviousCharacter) append(segments, false, 7 * UNIT_MILLIS);
        int flashCount = 0;
        for (Segment segment : segments) {
            if (segment.lightOn) flashCount++;
        }
        return new Encoding(transmitted.toString(), segments, skipped, flashCount);
    }

    private static void append(List<Segment> segments, boolean lightOn, long duration) {
        if (!segments.isEmpty()) {
            Segment previous = segments.get(segments.size() - 1);
            if (previous.lightOn == lightOn) {
                segments.set(segments.size() - 1,
                        new Segment(lightOn, previous.durationMillis + duration));
                return;
            }
        }
        segments.add(new Segment(lightOn, duration));
    }

    static char decode(String symbols) {
        Character result = LETTERS.get(symbols);
        return result == null ? '\0' : result;
    }
}
