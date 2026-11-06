package com.javierrb35.flashlight

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MorseSendCountdownTest {
    @Test
    fun waitsThreeSecondsAndReportsEachRemainingSecond() = runTest {
        val seconds = mutableListOf<Int>()
        var completed = false

        val countdown = launch {
            MorseSendCountdown.wait(seconds::add)
            completed = true
        }

        runCurrent()
        assertEquals(listOf(3), seconds)
        assertEquals(false, completed)

        advanceTimeBy(1000)
        runCurrent()
        assertEquals(listOf(3, 2), seconds)
        assertEquals(false, completed)

        advanceTimeBy(1000)
        runCurrent()
        assertEquals(listOf(3, 2, 1), seconds)
        assertEquals(false, completed)

        advanceTimeBy(1000)
        runCurrent()
        assertEquals(true, completed)
        countdown.join()
    }

    @Test
    fun completesOnlyAfterThreeFullSeconds() = runTest {
        var completed = false
        val countdown = launch {
            MorseSendCountdown.wait {}
            completed = true
        }

        advanceTimeBy(2999)
        runCurrent()
        assertEquals(false, completed)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(true, completed)
        countdown.join()
    }
}
