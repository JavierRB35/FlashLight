package com.javierrb35.flashlight.receive

internal class MorseLightDetector {
    private var ambientBrightness = 0.0
    private var initialized = false

    fun reset() {
        ambientBrightness = 0.0
        initialized = false
    }

    fun isBright(value: Int): Boolean {
        if (!initialized) {
            ambientBrightness = value.toDouble()
            initialized = true
        }
        val contrast = value - ambientBrightness
        val bright = value >= MIN_SIGNAL_BRIGHTNESS && contrast >= MIN_SIGNAL_CONTRAST
        if (!bright) updateAmbient(value)
        return bright
    }

    fun signalContrast(value: Int): Int? {
        if (!initialized) {
            ambientBrightness = value.toDouble()
            initialized = true
            return null
        }
        val contrast = value - ambientBrightness
        if (value >= MIN_SIGNAL_BRIGHTNESS && contrast >= MIN_SIGNAL_CONTRAST) {
            return contrast.toInt()
        }
        updateAmbient(value)
        return null
    }

    private fun updateAmbient(value: Int) {
        ambientBrightness = ambientBrightness * AMBIENT_WEIGHT +
            value * (1.0 - AMBIENT_WEIGHT)
    }

    private companion object {
        const val MIN_SIGNAL_BRIGHTNESS = 80
        const val MIN_SIGNAL_CONTRAST = 18
        const val AMBIENT_WEIGHT = 0.95
    }
}
