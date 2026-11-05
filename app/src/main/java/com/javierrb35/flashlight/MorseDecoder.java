package com.javierrb35.flashlight;

final class MorseDecoder {
    private static final long LETTER_GAP = 2 * MorseCodec.UNIT_MILLIS;
    private static final long WORD_GAP = 5 * MorseCodec.UNIT_MILLIS;

    private final StringBuilder symbols = new StringBuilder();
    private final StringBuilder text = new StringBuilder();
    private Boolean lightOn;
    private long stateStartedAt;
    private boolean wordPending;

    void reset() {
        symbols.setLength(0);
        text.setLength(0);
        lightOn = null;
        stateStartedAt = 0;
        wordPending = false;
    }

    String sample(boolean nextLightOn, long timeMillis) {
        if (lightOn == null) {
            lightOn = nextLightOn;
            stateStartedAt = timeMillis;
            return text.toString();
        }
        if (nextLightOn != lightOn) {
            long duration = Math.max(0, timeMillis - stateStartedAt);
            if (lightOn) {
                symbols.append(duration < 2 * MorseCodec.UNIT_MILLIS ? '.' : '-');
            } else {
                classifyGap(duration);
            }
            lightOn = nextLightOn;
            stateStartedAt = timeMillis;
        } else if (!nextLightOn) {
            flushSilence(timeMillis);
        }
        return text.toString();
    }

    String finish(long timeMillis) {
        if (Boolean.FALSE.equals(lightOn)) flushSilence(timeMillis);
        finishLetter();
        while (text.length() > 0 && text.charAt(text.length() - 1) == ' ') {
            text.setLength(text.length() - 1);
        }
        lightOn = null;
        return text.toString();
    }

    String text() {
        return text.toString();
    }

    private void classifyGap(long duration) {
        if (duration >= WORD_GAP) {
            finishLetter();
            wordPending = text.length() > 0;
        } else if (duration >= LETTER_GAP) {
            finishLetter();
        }
    }

    private void flushSilence(long timeMillis) {
        long silence = Math.max(0, timeMillis - stateStartedAt);
        if (silence >= LETTER_GAP) finishLetter();
        if (silence >= 6 * MorseCodec.UNIT_MILLIS && text.length() > 0) {
            wordPending = true;
        }
    }

    private void finishLetter() {
        if (symbols.length() == 0) return;
        char character = MorseCodec.decode(symbols.toString());
        if (character != '\0') {
            if (wordPending && text.length() > 0
                    && text.charAt(text.length() - 1) != ' ') {
                text.append(' ');
            }
            text.append(character);
        }
        symbols.setLength(0);
        wordPending = false;
    }
}
