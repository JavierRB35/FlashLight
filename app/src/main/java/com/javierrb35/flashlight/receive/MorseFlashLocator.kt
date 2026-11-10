package com.javierrb35.flashlight.receive

import java.nio.ByteBuffer
import java.util.Arrays

internal class MorseFlashLocator {
    data class Point(val x: Float, val y: Float)

    data class Detection(val lightOn: Boolean, val point: Point?)

    private val detectors = Array(CELL_COUNT) { MorseLightDetector() }
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
        Arrays.fill(cellBrightness, 0)

        // A distant flash may cover only one camera pixel. Sampling every other
        // pixel and taking a 99.9th percentile can miss it entirely. Use the
        // brightest pixel in each region; the per-region ambient model and the
        // two-frame debouncer reject stationary lights and isolated glitches.
        val bufferLimit = luma.limit()
        for (y in 0 until height) {
            val rowOffset = y * rowStride
            val cellRow = y * ROWS / height * COLUMNS
            for (x in 0 until width) {
                val offset = rowOffset + x * pixelStride
                if (offset >= 0 && offset < bufferLimit) {
                    val cell = cellRow + x * COLUMNS / width
                    val brightness = luma.get(offset).toInt() and 0xFF
                    if (brightness > cellBrightness[cell]) cellBrightness[cell] = brightness
                }
            }
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
    }
}
