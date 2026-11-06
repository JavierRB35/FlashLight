package com.javierrb35.flashlight

import com.javierrb35.flashlight.receive.MorseLightDebouncer
import com.javierrb35.flashlight.receive.MorseLightDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseReceptionPipelineTest {
    @Test
    fun detectsFlashByContrastEvenBelowFormerAbsoluteThreshold() {
        val detector = MorseLightDetector()
        detector.isBright(30)

        assertTrue(detector.isBright(170))
        assertFalse(detector.isBright(30))
    }

    @Test
    fun debouncesStateChangesWithoutShiftingTheirTimestamps() {
        val debouncer = MorseLightDebouncer()

        assertEquals(MorseLightDebouncer.Sample(false, 0), debouncer.sample(false, 0))
        assertNull(debouncer.sample(true, 100))
        assertEquals(MorseLightDebouncer.Sample(true, 100), debouncer.sample(true, 133))
        assertNull(debouncer.sample(false, 300))
        assertEquals(MorseLightDebouncer.Sample(false, 300), debouncer.sample(false, 333))
    }

    @Test
    fun decodesEncodedMessageAfterCameraFrameSampling() {
        val expected = "HI 5"
        val encoding = MorseCodec.encode(expected)
        val detector = MorseLightDetector()
        val debouncer = MorseLightDebouncer()
        val decoder = MorseDecoder()
        var segmentIndex = 0
        var segmentEndsAt = 400L + encoding.segments.first().durationMillis
        val signalEnd = 400L + encoding.segments.sumOf { it.durationMillis }

        for (now in 0L..(signalEnd + 600L) step FRAME_INTERVAL_MILLIS) {
            while (segmentIndex < encoding.segments.size && now >= segmentEndsAt) {
                segmentIndex++
                if (segmentIndex < encoding.segments.size) {
                    segmentEndsAt += encoding.segments[segmentIndex].durationMillis
                }
            }
            val lightOn = now >= 400L && segmentIndex < encoding.segments.size &&
                encoding.segments[segmentIndex].lightOn
            val brightness = if (lightOn) FLASH_BRIGHTNESS else AMBIENT_BRIGHTNESS
            val detectedLight = detector.isBright(brightness)
            debouncer.sample(detectedLight, now)?.let { sample ->
                decoder.sample(sample.lightOn, sample.timestampMillis)
            }
        }

        assertEquals(expected, decoder.text())
    }

    private companion object {
        const val FRAME_INTERVAL_MILLIS = 33L
        const val AMBIENT_BRIGHTNESS = 30
        const val FLASH_BRIGHTNESS = 170
    }
}
