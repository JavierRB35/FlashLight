package com.javierrb35.flashlight.receive

import java.nio.ByteBuffer
import java.util.Arrays

internal class MorseFlashLocator {
    data class Point(val x: Float, val y: Float)

    data class Detection(val lightOn: Boolean, val point: Point?)

    data class Brightness(val peak: IntArray, val fourthBrightest: IntArray)

    private val peakDetectors = Array(CELL_COUNT) { MorseLightDetector() }
    private val areaDetectors = Array(CELL_COUNT) { MorseLightDetector() }
    private val topPixels = IntArray(CELL_COUNT * TOP_PIXELS)
    private val peak = IntArray(CELL_COUNT)
    private val fourthBrightest = IntArray(CELL_COUNT)
    private val measuredBrightness = Brightness(peak, fourthBrightest)
    private var lastDetectedCell: Int? = null

    fun reset() {
        peakDetectors.forEach(MorseLightDetector::reset)
        areaDetectors.forEach(MorseLightDetector::reset)
        lastDetectedCell = null
    }

    fun measureBrightness(
        luma: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): Brightness {
        require(width > 0 && height > 0)
        Arrays.fill(topPixels, 0)
        val bufferLimit = luma.limit()

        // Scan every pixel: a distant LED can occupy one pixel at an odd
        // coordinate and disappear entirely with step-2 sampling. Keep both
        // the maximum and the fourth brightest pixel so a steady hotspot
        // cannot hide a larger flashing source in the same camera region.
        for (y in 0 until height) {
            val rowOffset = y * rowStride
            val cellRow = y * ROWS / height * COLUMNS
            for (x in 0 until width) {
                val offset = rowOffset + x * pixelStride
                if (offset >= 0 && offset < bufferLimit) {
                    val cell = cellRow + x * COLUMNS / width
                    val value = luma.get(offset).toInt() and 0xFF
                    var rank = cell * TOP_PIXELS
                    if (value > topPixels[rank]) {
                        topPixels[rank] = value
                        val lastRank = rank + TOP_PIXELS - 1
                        while (rank < lastRank && topPixels[rank] > topPixels[rank + 1]) {
                            val lower = topPixels[rank + 1]
                            topPixels[rank + 1] = topPixels[rank]
                            topPixels[rank] = lower
                            rank++
                        }
                    }
                }
            }
        }
        for (cell in 0 until CELL_COUNT) {
            val start = cell * TOP_PIXELS
            fourthBrightest[cell] = topPixels[start]
            peak[cell] = topPixels[start + TOP_PIXELS - 1]
        }
        return measuredBrightness
    }

    /** For callers that already have a representative brightness per region. */
    fun analyze(cellBrightness: IntArray): Detection =
        analyze(Brightness(cellBrightness, cellBrightness))

    fun analyze(brightness: Brightness): Detection {
        require(brightness.peak.size == CELL_COUNT && brightness.fourthBrightest.size == CELL_COUNT) {
            "Expected $CELL_COUNT brightness values per region"
        }

        var strongestCell: Int? = null
        var strongestContrast = 0
        for (index in 0 until CELL_COUNT) {
            val peakContrast = peakDetectors[index].signalContrast(brightness.peak[index]) ?: 0
            val areaContrast = areaDetectors[index].signalContrast(brightness.fourthBrightest[index]) ?: 0
            val contrast = maxOf(peakContrast, areaContrast)
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
        private const val TOP_PIXELS = 4
    }
}
