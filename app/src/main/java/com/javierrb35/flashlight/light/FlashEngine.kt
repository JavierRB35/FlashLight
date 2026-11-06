package com.javierrb35.flashlight

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Flash selection mode on the "Luz" page. */
enum class LightMode { TORCH, STROBE, SOS }

/**
 * The one place that talks to the flash LED. Behaviour mirrors the original
 * product exactly: torch, strobe of 1–6 flashes per second adjustable while
 * running, looping SOS pattern and full Morse transmissions with per-flash
 * progress. All timings come from the tested Java helpers (StrobeTiming,
 * MorsePattern, MorseCodec).
 */
class FlashEngine(
    private val cameraManager: CameraManager,
    private val scope: CoroutineScope,
) {
    /** Abstraction for tests. */
    interface Torch {
        /** Camera id with a flash, or null when the device has none. */
        fun flashCameraId(): String?
        fun setTorch(on: Boolean)
    }

    private val cameraTorch = object : Torch {
        private var cameraId: String? = null
        override fun flashCameraId(): String? {
            if (cameraId != null) return cameraId
            cameraId = try {
                cameraManager.cameraIdList.firstOrNull { id ->
                    java.lang.Boolean.TRUE == cameraManager
                        .getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                }
            } catch (_: Exception) {
                null
            }
            return cameraId
        }

        override fun setTorch(on: Boolean) {
            val id = cameraId ?: flashCameraId() ?: throw TorchUnavailableException()
            try {
                cameraManager.setTorchMode(id, on)
            } catch (error: Exception) {
                when (error) {
                    is android.hardware.camera2.CameraAccessException,
                    is IllegalArgumentException,
                    is SecurityException -> throw TorchUnavailableException(error)
                    else -> throw error
                }
            }
        }
    }

    /** Test seam. */
    internal var torch: Torch = cameraTorch

    data class State(
        val mode: LightMode = LightMode.TORCH,
        val flashesPerSecond: Int = StrobeTiming.MIN_FLASHES_PER_SECOND,
        val running: Boolean = false,
        val ledOn: Boolean = false,
        /** Transmission progress, by completed flashes. */
        val sentFlashes: Int = 0,
        val totalFlashes: Int = 0,
        val transmittingCharacters: Int = 0,
        val transmitting: Boolean = false,
        val preparationSecondsRemaining: Int = 0,
        val transmissionPhase: TransmissionPhase = TransmissionPhase.IDLE,
    )

    enum class TransmissionPhase { IDLE, PREPARING, RUNNING, COMPLETED, STOPPED }

    sealed interface Event {
        data object TransmissionCompleted : Event
        data class Failure(val message: FailureKind) : Event
    }

    /** Failure catalogue with the exact original user-facing semantics. */
    enum class FailureKind {
        NO_FLASH, FLASH_LOST_STROBE, FLASH_LOST_SOS, FLASH_LOST_SEND, FLASH_UNREACHABLE
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableStateFlow<Event?>(null)
    val events: StateFlow<Event?> = _events.asStateFlow()

    private var job: Job? = null

    /** Clears the current event so the same failure can be emitted again. */
    fun consumeEvent() {
        _events.value = null
    }

    fun setMode(mode: LightMode) {
        if (_state.value.running) stopLight()
        _state.update { it.copy(mode = mode) }
    }

    fun setFlashesPerSecond(fps: Int) {
        val bounded = fps.coerceIn(StrobeTiming.MIN_FLASHES_PER_SECOND, StrobeTiming.MAX_FLASHES_PER_SECOND)
        _state.update { it.copy(flashesPerSecond = bounded) }
    }

    /** Main action: stops if running, otherwise starts the selected mode. */
    fun onMainAction() {
        if (_state.value.running) {
            stopLight()
            return
        }
        when (_state.value.mode) {
            LightMode.TORCH -> startLight()
            LightMode.STROBE, LightMode.SOS -> startLight() // caller confirms first
        }
    }

    fun startLight() {
        val cameraId = try {
            torch.flashCameraId()
        } catch (_: Exception) {
            null
        }
        if (cameraId == null) {
            _events.value = Event.Failure(FailureKind.NO_FLASH)
            return
        }
        val mode = _state.value.mode
        job = scope.launch {
            runCatching { torch.setTorch(true) }
                .onFailure {
                    _events.value = Event.Failure(FailureKind.FLASH_UNREACHABLE)
                    return@launch
                }
            _state.update { it.copy(running = true, ledOn = true) }
            when (mode) {
                LightMode.STROBE -> runStrobe()
                LightMode.SOS -> runSos()
                LightMode.TORCH -> Unit
            }
        }
    }

    fun stopLight() {
        job?.cancel()
        job = null
        val wasActive = _state.value.running || _state.value.ledOn
        if (wasActive) tryTurnOff()
        _state.update { it.copy(running = false, ledOn = false) }
    }

    internal fun startTransmission(message: String) {
        if (_state.value.transmitting) {
            stopTransmission()
            return
        }
        val encoding = MorseCodec.encode(message)
        if (encoding.text.isNotEmpty()) startEncoded(encoding)
    }

    /** Starts a transmission from an encoding already validated and confirmed. */
    internal fun startEncoded(encoding: MorseCodec.Encoding) {
        if (_state.value.transmitting) return
        val cameraId = try {
            torch.flashCameraId()
        } catch (_: Exception) {
            null
        }
        if (cameraId == null) {
            _events.value = Event.Failure(FailureKind.NO_FLASH)
            return
        }
        job = scope.launch {
            _state.update {
                it.copy(
                    transmitting = true,
                    transmissionPhase = TransmissionPhase.PREPARING,
                    sentFlashes = 0,
                    totalFlashes = encoding.flashCount,
                    transmittingCharacters = encoding.text.length,
                    preparationSecondsRemaining = MorseSendCountdown.SECONDS,
                )
            }
            if (!runCatching { torch.setTorch(false) }.isSuccess) {
                _events.value = Event.Failure(FailureKind.FLASH_LOST_SEND)
                finishTransmission(TransmissionPhase.STOPPED)
                return@launch
            }
            MorseSendCountdown.wait { secondsRemaining ->
                _state.update { it.copy(preparationSecondsRemaining = secondsRemaining) }
            }
            _state.update {
                it.copy(
                    transmissionPhase = TransmissionPhase.RUNNING,
                    preparationSecondsRemaining = 0,
                )
            }
            for (segment in encoding.segments) {
                if (!runCatching { torch.setTorch(segment.lightOn) }.isSuccess) {
                    _events.value = Event.Failure(FailureKind.FLASH_LOST_SEND)
                    finishTransmission(TransmissionPhase.STOPPED)
                    return@launch
                }
                _state.update { s ->
                    if (segment.lightOn) {
                        s.copy(ledOn = true, sentFlashes = s.sentFlashes + 1)
                    } else {
                        s.copy(ledOn = false)
                    }
                }
                delay(segment.durationMillis)
            }
            tryTurnOff()
            _events.value = Event.TransmissionCompleted
            finishTransmission(TransmissionPhase.COMPLETED)
        }
    }

    fun stopTransmission() {
        job?.cancel()
        job = null
        val wasTransmitting = _state.value.transmitting
        if (wasTransmitting) {
            tryTurnOff()
            finishTransmission(TransmissionPhase.STOPPED)
        }
    }

    /** True while anything is on: the UpdateUi busy predicate. */
    fun anyLightActivity(): Boolean {
        val s = _state.value
        return s.running || s.ledOn || s.transmitting
    }

    private fun finishTransmission(phase: TransmissionPhase) {
        _state.update {
            it.copy(
                transmitting = false,
                ledOn = false,
                preparationSecondsRemaining = 0,
                transmissionPhase = phase,
            )
        }
    }

    private fun tryTurnOff() {
        try {
            torch.setTorch(false)
        } catch (_: Exception) {
            // The original logs and moves on; there is nothing more to do.
        }
    }

    private suspend fun runStrobe() {
        while (true) {
            delay(StrobeTiming.halfPeriodMillis(_state.value.flashesPerSecond))
            val next = !_state.value.ledOn
            try {
                torch.setTorch(next)
            } catch (_: Exception) {
                _events.value = Event.Failure(FailureKind.FLASH_LOST_STROBE)
                stopLight()
                return
            }
            _state.update { it.copy(ledOn = next) }
        }
    }

    private suspend fun runSos() {
        val segments = MorsePattern.sos()
        var index = 0
        while (true) {
            delay(segments[index].durationMillis)
            index = (index + 1) % segments.size
            val segment = segments[index]
            try {
                torch.setTorch(segment.lightOn)
            } catch (_: Exception) {
                _events.value = Event.Failure(FailureKind.FLASH_LOST_SOS)
                stopLight()
                return
            }
            _state.update { it.copy(ledOn = segment.lightOn) }
        }
    }
}

class TorchUnavailableException(cause: Throwable? = null) : Exception(cause)

internal object MorseSendCountdown {
    const val SECONDS = 3
    private const val SECOND_MILLIS = 1000L

    suspend fun wait(onSecond: (Int) -> Unit) {
        for (secondsRemaining in SECONDS downTo 1) {
            onSecond(secondsRemaining)
            delay(SECOND_MILLIS)
        }
    }
}
