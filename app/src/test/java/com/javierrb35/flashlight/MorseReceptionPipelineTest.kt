package com.javierrb35.flashlight

import com.javierrb35.flashlight.receive.MorseLightDebouncer
import com.javierrb35.flashlight.receive.MorseLightDetector
import com.javierrb35.flashlight.receive.MorseFlashLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class MorseReceptionPipelineTest {
    @Test
    fun detectsFlashByContrastEvenBelowFormerAbsoluteThreshold() {
        val detector = MorseLightDetector()
        detector.isBright(30)

        assertTrue(detector.isBright(170))
        assertFalse(detector.isBright(30))
    }

    @Test
    fun locatesFlashingSourceOutsideTheCenterAndKeepsItsMarkerDuringDarkPulses() {
        val locator = MorseFlashLocator()
        val ambient = IntArray(MorseFlashLocator.CELL_COUNT) { AMBIENT_BRIGHTNESS }
        val flashCell = 2 * MorseFlashLocator.COLUMNS + 5

        assertFalse(locator.analyze(ambient).lightOn)
        val brightFrame = ambient.copyOf().also { it[flashCell] = FLASH_BRIGHTNESS }
        val detected = locator.analyze(brightFrame)

        assertTrue(detected.lightOn)
        assertEquals(
            MorseFlashLocator.Point(
                x = 5.5f / MorseFlashLocator.COLUMNS,
                y = 2.5f / MorseFlashLocator.ROWS,
            ),
            detected.point,
        )
        val darkFrame = locator.analyze(ambient)
        assertFalse(darkFrame.lightOn)
        assertEquals(detected.point, darkFrame.point)
    }

    @Test
    fun detectsSmallOffCenterFlashInSampledCameraImage() {
        val locator = MorseFlashLocator()
        val width = 600
        val height = 400
        val pixels = ByteArray(width * height) { AMBIENT_BRIGHTNESS.toByte() }

        assertFalse(
            locator.analyze(
                locator.measureBrightness(ByteBuffer.wrap(pixels), width, height, width, 1),
            ).lightOn,
        )
        for (y in 250 until 254) {
            for (x in 550 until 556) {
                pixels[y * width + x] = FLASH_BRIGHTNESS.toByte()
            }
        }

        val detection = locator.analyze(
            locator.measureBrightness(ByteBuffer.wrap(pixels), width, height, width, 1),
        )
        assertTrue(detection.lightOn)
        assertEquals(
            MorseFlashLocator.Point(
                x = 5.5f / MorseFlashLocator.COLUMNS,
                y = 2.5f / MorseFlashLocator.ROWS,
            ),
            detection.point,
        )
    }

    @Test
    fun ignoresSteadyBrightArea() {
        val locator = MorseFlashLocator()
        val steadyLight = IntArray(MorseFlashLocator.CELL_COUNT) { FLASH_BRIGHTNESS }

        repeat(10) {
            assertFalse(locator.analyze(steadyLight).lightOn)
        }
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
