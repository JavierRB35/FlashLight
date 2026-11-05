package com.javierrb35.flashlight

import android.app.Application
import android.hardware.camera2.CameraManager
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connects the Compose UI with the flash engine and the Morse receiver, both
 * living at ViewModel scope so a rotation never interrupts a transmission or
 * the camera pipeline.
 */
class FlashLightViewModel(app: Application) : AndroidViewModel(app) {

    private val cameraManager = app.getSystemService(Application.CAMERA_SERVICE) as CameraManager
    private val prefs = AppPrefs(app)

    val engine = FlashEngine(cameraManager, viewModelScope)
    val receiver = MorseReceiver(app, cameraManager)

    private val _page = MutableStateFlow(Page.LIGHT)
    val page: StateFlow<Page> = _page.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    /** Transcript of the receiver; editable, like the original editor. */
    private val _receivedText = MutableStateFlow("")
    val receivedText: StateFlow<String> = _receivedText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Pending send confirmation: the encoding shown in the dialog. */
    private val _pendingSend = MutableStateFlow<MorseCodec.Encoding?>(null)
    internal val pendingSend: StateFlow<MorseCodec.Encoding?> = _pendingSend.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /** Waiting-for-camera-permission action. */
    internal enum class PendingCameraAction { RECEIVE }
    internal var pendingCameraAction: PendingCameraAction? = null

    private var textureView: TextureView? = null

    init {
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    null -> Unit
                    is FlashEngine.Event.Failure -> _errorMessage.value = when (event.message) {
                        FlashEngine.FailureKind.NO_FLASH ->
                            "Este dispositivo no tiene un flash disponible."

                        FlashEngine.FailureKind.FLASH_LOST_STROBE ->
                            "El flash dejó de estar disponible. El efecto se ha detenido."

                        FlashEngine.FailureKind.FLASH_LOST_SOS ->
                            "El flash dejó de estar disponible. La señal SOS se ha detenido."

                        FlashEngine.FailureKind.FLASH_LOST_SEND ->
                            "El flash dejó de estar disponible. Se detuvo la transmisión."

                        FlashEngine.FailureKind.FLASH_UNREACHABLE ->
                            "No se pudo encender el flash. Cierra otras aplicaciones que usen " +
                                "la cámara e inténtalo de nuevo."
                    }

                    FlashEngine.Event.TransmissionCompleted ->
                        _errorMessage.value = "Mensaje Morse enviado"
                }
                engine.consumeEvent()
            }
        }
        viewModelScope.launch {
            receiver.state.collect { state ->
                if (state.status == MorseReceiver.Status.ERROR) {
                    _errorMessage.value = state.hint
                }
                _receivedText.value = state.text
            }
        }
        viewModelScope.launch {
            prefs.prefs.collect { p ->
                _keepScreenOn.value = p.keepScreenOn
                _themeMode.value = p.themeMode
                _dynamicColor.value = p.dynamicColor
                if (!engine.state.value.running) {
                    engine.setFlashesPerSecond(p.strobeHz)
                }
            }
        }
    }

    fun busy(): Boolean = engine.anyLightActivity() || receiver.isActive

    /** What onPause must guarantee: light off, transmission stopped, camera closed. */
    fun stopEverythingForPause() {
        engine.stopLight()
        engine.stopTransmission()
        receiver.stop(keepTranscript = true)
    }

    fun selectPage(page: Page) {
        if (_page.value == page) return
        engine.stopLight()
        engine.stopTransmission()
        receiver.stop(keepTranscript = false)
        _page.value = page
    }

    fun setTextureView(view: TextureView?) {
        textureView = view
    }

    /** The preview surface became usable; open the camera if receiving. */
    fun onSurfaceAvailable(view: TextureView) {
        textureView = view
        receiver.onSurfaceAvailable(view)
    }

    fun selectMode(mode: LightMode) = engine.setMode(mode)

    fun onMainAction() = engine.onMainAction()

    fun setFlashesPerSecond(fps: Int) {
        engine.setFlashesPerSecond(fps)
        viewModelScope.launch { prefs.setStrobeHz(engine.state.value.flashesPerSecond) }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun setMessage(value: String) {
        _message.value = value
    }

    fun setReceivedText(value: String) {
        _receivedText.value = value
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { prefs.setKeepScreenOn(value) }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(value) }
    }

    /** Prepares the send confirmation dialog; null message shows the guidance error. */
    fun onSendClicked() {
        val encoding = MorseCodec.encode(_message.value)
        if (encoding.text.isEmpty()) {
            _errorMessage.value =
                "Escribe al menos una letra o un número que pueda transmitirse en Morse."
            return
        }
        _pendingSend.value = encoding
    }

    fun dismissSendConfirmation() {
        _pendingSend.value = null
    }

    fun confirmSend() {
        val encoding = _pendingSend.value ?: return
        _pendingSend.value = null
        engine.startEncoded(encoding)
    }

    fun onStopTransmission() = engine.stopTransmission()

    fun onReceiveClicked(hasCameraPermission: Boolean, requestPermission: () -> Unit) {
        if (receiver.isActive) {
            receiver.stop(keepTranscript = true)
            return
        }
        if (!hasCameraPermission) {
            pendingCameraAction = PendingCameraAction.RECEIVE
            requestPermission()
            return
        }
        startReceiver()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        val action = pendingCameraAction
        pendingCameraAction = null
        if (action == PendingCameraAction.RECEIVE) {
            if (granted) {
                startReceiver()
            } else {
                _errorMessage.value =
                    "Se necesita permiso de cámara para recibir señales. No se toman fotos " +
                        "ni se graba vídeo."
            }
        }
    }

    private fun startReceiver() {
        val view = textureView
        if (view == null) {
            _errorMessage.value =
                "La vista de cámara aún no está lista; inténtalo de nuevo en un instante."
            return
        }
        receiver.start(view)
    }
}
