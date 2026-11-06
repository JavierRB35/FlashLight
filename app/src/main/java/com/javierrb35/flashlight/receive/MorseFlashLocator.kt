package com.javierrb35.flashlight.receive

import java.nio.ByteBuffer
import java.util.Arrays

internal class MorseFlashLocator {
    data class Point(val x: Float, val y: Float)

    data class Detection(val lightOn: Boolean, val point: Point?)

    private val detectors = Array(CELL_COUNT) { MorseLightDetector() }
    private val brightnessHistograms = Array(CELL_COUNT) { IntArray(256) }
    private val sampledPixels = IntArray(CELL_COUNT)
    private val cellBrightness = IntArray(CELL_COUNT)
    private var lastDetectedCell: Int? = null

    fun reset() {
        detectors.forEach(MorseLightDetector::reset)
        lastDetectedCell = null
    }

    fun measureBrightness(
        luma: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): IntArray {
        require(width > 0 && height > 0)
        brightnessHistograms.forEach { Arrays.fill(it, 0) }
        Arrays.fill(sampledPixels, 0)

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val offset = y * rowStride + x * pixelStride
                if (offset >= 0 && offset < luma.limit()) {
                    val cell = y * ROWS / height * COLUMNS + x * COLUMNS / width
                    brightnessHistograms[cell][luma.get(offset).toInt() and 0xFF]++
                    sampledPixels[cell]++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        for (cell in cellBrightness.indices) {
            val count = sampledPixels[cell]
            if (count == 0) {
                cellBrightness[cell] = 0
                continue
            }
            val percentile = Math.max(0, Math.ceil(count * CELL_PERCENTILE).toInt() - 1)
            val histogram = brightnessHistograms[cell]
            var value = 0
            var cumulative = histogram[0]
            while (value < 255 && cumulative <= percentile) {
                value++
                cumulative += histogram[value]
            }
            cellBrightness[cell] = value
        }
        return cellBrightness
    }

    fun analyze(cellBrightness: IntArray): Detection {
        require(cellBrightness.size == CELL_COUNT) {
            "Expected $CELL_COUNT brightness values, got ${cellBrightness.size}"
        }

        var strongestCell: Int? = null
        var strongestContrast = 0
        for (index in cellBrightness.indices) {
            val contrast = detectors[index].signalContrast(cellBrightness[index]) ?: continue
            if (contrast > strongestContrast) {
                strongestCell = index
                strongestContrast = contrast
            }
        }

        if (strongestCell != null) lastDetectedCell = strongestCell
        val point = lastDetectedCell?.let(::pointForCell)
        return Detection(strongestCell != null, point)
    }

    private fun pointForCell(index: Int) = Point(
        x = (index % COLUMNS + 0.5f) / COLUMNS,
        y = (index / COLUMNS + 0.5f) / ROWS,
    )

    companion object {
        const val COLUMNS = 6
        const val ROWS = 4
        const val CELL_COUNT = COLUMNS * ROWS
        const val CELL_PERCENTILE = 0.999
        private const val SAMPLE_STEP = 2
    }
}
