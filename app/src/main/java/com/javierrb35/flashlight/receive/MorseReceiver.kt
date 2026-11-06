package com.javierrb35.flashlight

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.javierrb35.flashlight.receive.MorseLightDebouncer
import com.javierrb35.flashlight.receive.MorseLightDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * Camera-based Morse receiver. Faithful port of the original pipeline: it
 * samples the central third of the luma plane, builds a brightness histogram,
 * takes the 99.5th percentile, detects contrast relative to ambient light,
 * debounces the binary light state for two frames and feeds [MorseDecoder],
 * which turns the light timings into text. No photos or video are recorded or
 * stored.
 */
class MorseReceiver(
    private val context: Context,
    private val cameraManager: CameraManager,
) {
    enum class Status {
        IDLE, STARTING, LISTENING, STOPPED, ERROR
    }

    data class UiState(
        val status: Status = Status.IDLE,
        val text: String = "",
        /** Last human-facing hint, matching the original status lines. */
        val hint: String = "",
    ) {
        /** True while the receiver is starting or actively listening. */
        val listening: Boolean
            get() = status == Status.STARTING || status == Status.LISTENING
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val decoder = MorseDecoder()
    private val brightnessHistogram = IntArray(256)

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null

    private var receiving = false
    private var cameraOpening = false
    private val lightDetector = MorseLightDetector()
    private val lightDebouncer = MorseLightDebouncer()
    private var lastPublishedText = ""

    val isActive: Boolean get() = receiving || cameraOpening

    fun start(previewTexture: TextureView?) {
        if (receiving) return
        synchronized(decoder) { decoder.reset() }
        lightDetector.reset()
        lightDebouncer.reset()
        lastPublishedText = ""
        receiving = true
        _state.value = UiState(
            status = Status.STARTING,
            text = "",
            hint = "Centra la luz del emisor. Inicia primero esta recepción.",
        )
        startCameraThread()
        if (previewTexture?.isAvailable() == true) openReceiverCamera(previewTexture)
    }

    fun stop(keepTranscript: Boolean) {
        val wasReceiving = receiving
        receiving = false
        var finalText = ""
        if (wasReceiving) {
            synchronized(decoder) {
                finalText = decoder.finish(SystemClock.elapsedRealtime())
            }
        }
        closeCamera()
        val shown = if (keepTranscript && finalText.isNotEmpty()) finalText else ""
        _state.value = UiState(
            status = if (wasReceiving) Status.STOPPED else Status.IDLE,
            text = shown,
            hint = if (wasReceiving) "Recepción detenida"
            else "Centra la luz del teléfono emisor en la vista.",
        )
        lastPublishedText = shown
    }

    /** Called when the TextureView surface becomes ready (or again on rotate). */
    fun onSurfaceAvailable(texture: TextureView) {
        if (receiving && camera == null && !cameraOpening) openReceiverCamera(texture)
    }

    @SuppressLint("MissingPermission")
    private fun openReceiverCamera(textureView: TextureView) {
        if (!receiving || camera != null || cameraOpening || cameraHandler == null) return
        try {
            val id = findReceivingCamera()
            if (id == null) {
                stop(false)
                _state.value = _state.value.copy(
                    status = Status.ERROR,
                    hint = "No se encontró una cámara compatible con vista previa.",
                )
                return
            }
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.let { choosePreviewSize(it.getOutputSizes(ImageFormat.YUV_420_888)) }
            if (size == null) {
                stop(false)
                _state.value = _state.value.copy(
                    status = Status.ERROR,
                    hint = "La cámara no ofrece una resolución compatible para recibir Morse.",
                )
                return
            }
            val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3)
            reader.setOnImageAvailableListener({ analyzeCameraFrame(it) }, cameraHandler)
            imageReader = reader
            val texture = textureView.surfaceTexture ?: run {
                stop(false)
                return
            }
            texture.setDefaultBufferSize(size.width, size.height)
            cameraOpening = true
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(opened: CameraDevice) {
                    cameraOpening = false
                    if (!receiving) {
                        opened.close()
                        return
                    }
                    camera = opened
                    createReceiverSession(opened, texture)
                }

                override fun onDisconnected(disconnected: CameraDevice) {
                    cameraOpening = false
                    disconnected.close()
                    camera = null
                    if (receiving) stop(true)
                }

                override fun onError(device: CameraDevice, error: Int) {
                    cameraOpening = false
                    device.close()
                    camera = null
                    if (receiving) {
                        stop(false)
                        _state.value = _state.value.copy(
                            status = Status.ERROR,
                            hint = "No se pudo abrir la cámara receptora.",
                        )
                    }
                }
            }, cameraHandler)
        } catch (error: Exception) {
            when (error) {
                is android.hardware.camera2.CameraAccessException,
                is IllegalArgumentException,
                is SecurityException -> {
                    cameraOpening = false
                    stop(false)
                    _state.value = _state.value.copy(
                        status = Status.ERROR,
                        hint = "No se pudo iniciar la cámara receptora.",
                    )
                }

                else -> throw error
            }
        }
    }

    private fun createReceiverSession(device: CameraDevice, texture: SurfaceTexture) {
        if (!receiving || imageReader == null) return
        try {
            val previewSurface = Surface(texture)
            this.previewSurface = previewSurface
            val surfaces = listOf(previewSurface, imageReader!!.surface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (!receiving || camera == null) {
                        configured.close()
                        return
                    }
                    session = configured
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        request.addTarget(previewSurface)
                        request.addTarget(imageReader!!.surface)
                        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        configured.setRepeatingRequest(request.build(), null, cameraHandler)
                        _state.value = _state.value.copy(
                            status = Status.LISTENING,
                            hint = "Escuchando luz · centra el emisor",
                        )
                    } catch (error: Exception) {
                        when (error) {
                            is android.hardware.camera2.CameraAccessException,
                            is IllegalStateException -> {
                                stop(false)
                                _state.value = _state.value.copy(
                                    status = Status.ERROR,
                                    hint = "No se pudo iniciar el análisis de la cámara.",
                                )
                            }

                            else -> throw error
                        }
                    }
                }

                override fun onConfigureFailed(failed: CameraCaptureSession) {
                    failed.close()
                    stop(false)
                    _state.value = _state.value.copy(
                        status = Status.ERROR,
                        hint = "La cámara no pudo preparar la recepción.",
                    )
                }
            }, cameraHandler)
        } catch (error: Exception) {
            when (error) {
                is android.hardware.camera2.CameraAccessException,
                is IllegalStateException -> {
                    stop(false)
                    _state.value = _state.value.copy(
                        status = Status.ERROR,
                        hint = "No se pudo configurar la cámara receptora.",
                    )
                }

                else -> throw error
            }
        }
    }

    private fun analyzeCameraFrame(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            if (planes.isEmpty()) return
            val luma: ByteBuffer = planes[0].buffer
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride
            val width = image.width
            val height = image.height
            Arrays.fill(brightnessHistogram, 0)
            var sampledPixels = 0
            var y = height / 3
            while (y < height * 2 / 3) {
                var x = width / 3
                while (x < width * 2 / 3) {
                    val index = y * rowStride + x * pixelStride
                    if (index >= 0 && index < luma.limit()) {
                        brightnessHistogram[luma.get(index).toInt() and 0xFF]++
                        sampledPixels++
                    }
                    x += 2
                }
                y += 2
            }
            val percentile = Math.max(0, Math.ceil(sampledPixels * 0.995).toInt() - 1)
            var value = 0
            var cumulative = brightnessHistogram[0]
            while (value < 255) {
                if (cumulative > percentile) break
                cumulative += brightnessHistogram[value + 1]
                value++
            }
            val bright = lightDetector.isBright(value)
            processBrightness(bright, SystemClock.elapsedRealtime())
        } finally {
            image.close()
        }
    }

    private fun processBrightness(bright: Boolean, now: Long) {
        if (!receiving) return
        val stableSample = lightDebouncer.sample(bright, now) ?: return
        val text = synchronized(decoder) {
            decoder.sample(stableSample.lightOn, stableSample.timestampMillis)
        }
        publish(text)
    }

    private fun publish(text: String) {
        if (text == lastPublishedText) return
        lastPublishedText = text
        _state.value = _state.value.copy(
            text = if (text.isEmpty()) "" else text,
        )
    }

    private fun findReceivingCamera(): String? {
        var fallback: String? = null
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: continue
            if (choosePreviewSize(map.getOutputSizes(ImageFormat.YUV_420_888)) == null) continue
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id
            if (fallback == null) fallback = id
        }
        return fallback
    }

    private fun choosePreviewSize(sizes: Array<Size>?): Size? {
        if (sizes == null || sizes.isEmpty()) return null
        var best: Size? = null
        var bestDistance = Long.MAX_VALUE
        for (size in sizes) {
            if (size.width < 320 || size.height < 240) continue
            val distance = Math.abs(
                size.width.toLong() * size.height - PREVIEW_WIDTH.toLong() * PREVIEW_HEIGHT,
            )
            if (distance < bestDistance) {
                best = size
                bestDistance = distance
            }
        }
        return best
    }

    private fun startCameraThread() {
        if (cameraThread != null) return
        cameraThread = HandlerThread("FlashLightCamera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun closeCamera() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        try {
            camera?.close()
        } catch (_: Exception) {
        }
        camera = null
        cameraOpening = false
        previewSurface?.release()
        previewSurface = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    companion object {
        private const val PREVIEW_WIDTH = 640
        private const val PREVIEW_HEIGHT = 480
    }
}
