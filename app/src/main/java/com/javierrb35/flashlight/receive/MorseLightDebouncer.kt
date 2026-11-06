package com.javierrb35.flashlight.receive

internal class MorseLightDebouncer {
    data class Sample(val lightOn: Boolean, val timestampMillis: Long)

    private var initialized = false
    private var stableLightOn = false
    private var candidateLightOn = false
    private var candidateFrameCount = 0
    private var candidateStartedAt = 0L

    fun reset() {
        initialized = false
        stableLightOn = false
        candidateLightOn = false
        candidateFrameCount = 0
        candidateStartedAt = 0L
    }

    fun sample(lightOn: Boolean, timestampMillis: Long): Sample? {
        if (!initialized) {
            initialized = true
            stableLightOn = lightOn
            candidateLightOn = lightOn
            return Sample(lightOn, timestampMillis)
        }

        if (lightOn != candidateLightOn) {
            candidateLightOn = lightOn
            candidateFrameCount = 1
            candidateStartedAt = timestampMillis
        } else {
            candidateFrameCount++
        }

        if (candidateFrameCount >= REQUIRED_STABLE_FRAMES &&
            candidateLightOn != stableLightOn
        ) {
            stableLightOn = candidateLightOn
            return Sample(stableLightOn, candidateStartedAt)
        }

        return if (candidateLightOn == stableLightOn) {
            Sample(stableLightOn, timestampMillis)
        } else {
            null
        }
    }

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 2
    }
}
