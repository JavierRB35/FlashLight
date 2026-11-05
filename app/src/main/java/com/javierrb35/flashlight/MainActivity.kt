package com.javierrb35.flashlight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Owns all normal features. The Compose UI drives the [FlashLightViewModel];
 * the update system ([UpdateUi]) keeps working exactly as before, including
 * its deferral rules while any light signalling is active.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: FlashLightViewModel by viewModels()

    private lateinit var updates: UpdateUi

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onCameraPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        updates = UpdateUi(this, viewModel::busy)
        setContent {
            FlashLightScreenTheme()
        }
        updates.onOpen()
    }

    @androidx.compose.runtime.Composable
    private fun FlashLightScreenTheme() {
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
        FlashLightTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
            FlashLightScreen()
        }
    }

    @androidx.compose.runtime.Composable
    private fun FlashLightScreen() {
        val engineState by viewModel.engine.state.collectAsStateWithLifecycle()
        val receiverState by viewModel.receiver.state.collectAsStateWithLifecycle()
        val page by viewModel.page.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        val receivedText by viewModel.receivedText.collectAsStateWithLifecycle()
        val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
        val pendingSend by viewModel.pendingSend.collectAsStateWithLifecycle()
        val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

        // The screen stays on while anything is emitting or listening — as long
        // as the user has not disabled that in the screen settings.
        val active = engineState.running || engineState.transmitting || receiverState.listening
        if (active && keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        FlashLightApp(
            page = page,
            engineState = engineState,
            receiverState = receiverState,
            message = message,
            onMessageChange = viewModel::setMessage,
            receivedText = receivedText,
            onReceivedTextChange = viewModel::setReceivedText,
            onSelectPage = viewModel::selectPage,
            onSelectMode = viewModel::selectMode,
            onMainAction = viewModel::onMainAction,
            onFlashesPerSecond = viewModel::setFlashesPerSecond,
            onSendClicked = viewModel::onSendClicked,
            onStopTransmission = viewModel::onStopTransmission,
            onReceiveClicked = {
                viewModel.onReceiveClicked(
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED,
                    requestPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
            },
            onTextureViewReady = viewModel::setTextureView,
            onSurfaceAvailable = viewModel::onSurfaceAvailable,
            onCheckUpdates = { updates.openMenu() },
            keepScreenOn = keepScreenOn,
            onKeepScreenOnChange = viewModel::setKeepScreenOn,
            themeMode = themeMode,
            onThemeChange = viewModel::setThemeMode,
            dynamicColor = dynamicColor,
            onDynamicColorChange = viewModel::setDynamicColor,
            errorMessage = errorMessage,
            onDismissError = viewModel::dismissError,
        )

        pendingSend?.let { encoding ->
            val preview = if (encoding.text.length <= 48) {
                encoding.text
            } else {
                encoding.text.substring(0, 48) + "…"
            }
            val skippedWarning = if (encoding.skippedCharacters > 0) {
                "\n\nSe omitirán ${encoding.skippedCharacters} carácter(es) no compatibles."
            } else {
                ""
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.dismissSendConfirmation() },
                title = { androidx.compose.material3.Text("Enviar por luz") },
                text = {
                    androidx.compose.material3.Text(
                        "Texto: $preview\n" +
                                "${encoding.text.length} caracteres · punto de 200 ms." +
                                "$skippedWarning\n\nInicia primero la recepción y centra ambos " +
                                "teléfonos.",
                    )
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.dismissSendConfirmation() },
                    ) { androidx.compose.material3.Text("Cancelar") }
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = { viewModel.confirmSend() }) {
                        androidx.compose.material3.Text("Transmitir")
                    }
                },
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::updates.isInitialized) updates.onControlsRefreshed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::updates.isInitialized) updates.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::updates.isInitialized) updates.onResume()
    }

    override fun onPause() {
        viewModel.stopEverythingForPause()
        super.onPause()
    }
}
