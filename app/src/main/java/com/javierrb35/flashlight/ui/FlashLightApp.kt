package com.javierrb35.flashlight

import android.view.TextureView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.javierrb35.flashlight.R

/**
 * The three product pages — Luz, Enviar, Recibir — with the header, the
 * per-session flashing confirmations and the safety warning card. All user
 * facing wording mirrors the original application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashLightApp(
    page: Page,
    engineState: FlashEngine.State,
    receiverState: MorseReceiver.UiState,
    message: String,
    onMessageChange: (String) -> Unit,
    receivedText: String,
    onReceivedTextChange: (String) -> Unit,
    onSelectPage: (Page) -> Unit,
    onSelectMode: (LightMode) -> Unit,
    onMainAction: () -> Unit,
    onFlashesPerSecond: (Int) -> Unit,
    onSendClicked: () -> Unit,
    onStopTransmission: () -> Unit,
    onReceiveClicked: () -> Unit,
    onTextureViewReady: (TextureView) -> Unit,
    onSurfaceAvailable: (TextureView) -> Unit,
    onCheckUpdates: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbar.showSnackbar(it)
            onDismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Header(
                onCheckUpdates = onCheckUpdates,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                dynamicColor = dynamicColor,
                onDynamicColorChange = onDynamicColorChange,
            )
            PageSelector(
                selected = page,
                onSelect = onSelectPage,
            )
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "pages",
            ) { current ->
                when (current) {
                    Page.LIGHT -> LightPage(
                        state = engineState,
                        onSelectMode = onSelectMode,
                        onMainAction = onMainAction,
                        onFlashesPerSecond = onFlashesPerSecond,
                    )

                    Page.SEND -> SendPage(
                        state = engineState,
                        message = message,
                        onMessageChange = onMessageChange,
                        onSendClicked = onSendClicked,
                        onStopTransmission = onStopTransmission,
                    )

                    Page.RECEIVE -> ReceivePage(
                        receiverState = receiverState,
                        receivedText = receivedText,
                        onReceivedTextChange = onReceivedTextChange,
                        onReceiveClicked = onReceiveClicked,
                        onTextureViewReady = onTextureViewReady,
                        onSurfaceAvailable = onSurfaceAvailable,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

enum class Page { LIGHT, SEND, RECEIVE }

@Composable
private fun Header(
    onCheckUpdates: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.flashlight_mascot),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = "Flashlight",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "LUZ A TU MANERA · SIMPLE Y SEGURA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { showSettings = true }) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Ajustes de pantalla",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onCheckUpdates,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "↻",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Ajustes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tema",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ThemeOptionRow(
                        label = "Oscuro (recomendado)",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                    )
                    ThemeOptionRow(
                        label = "Claro",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                    )
                    ThemeOptionRow(
                        label = "Según el sistema",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeChange(ThemeMode.SYSTEM) },
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        SettingsSwitchRow(
                            title = "Color dinámico",
                            subtitle = "Adaptar los colores al fondo de pantalla",
                            value = dynamicColor,
                            onChange = onDynamicColorChange,
                        )
                    }
                    SettingsSwitchRow(
                        title = "Mantener pantalla encendida",
                        subtitle = "Evita que se bloquee mientras la luz está activa",
                        value = keepScreenOn,
                        onChange = onKeepScreenOnChange,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Aceptar") }
            },
        )
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) },
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSelector(selected: Page, onSelect: (Page) -> Unit) {
    val options = listOf(
        Triple(Page.LIGHT, "Luz", "Abrir modo Luz"),
        Triple(Page.SEND, "Enviar", "Abrir modo Enviar"),
        Triple(Page.RECEIVE, "Recibir", "Abrir modo Recibir"),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        options.forEachIndexed { index, (page, label, _) ->
            SegmentedButton(
                selected = selected == page,
                onClick = { onSelect(page) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label) },
                modifier = Modifier.height(44.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightPage(
    state: FlashEngine.State,
    onSelectMode: (LightMode) -> Unit,
    onMainAction: () -> Unit,
    onFlashesPerSecond: (Int) -> Unit,
) {
    var pendingConfirmation by remember { mutableStateOf<LightMode?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            val modes = listOf(
                Triple(LightMode.TORCH, "Linterna", Icons.Rounded.FlashlightOn),
                Triple(LightMode.STROBE, "Estrobo", Icons.Rounded.Bolt),
                Triple(LightMode.SOS, "SOS", Icons.Rounded.Sos),
            )
            modes.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = {
                        if (state.mode != mode) {
                            if (mode == LightMode.STROBE || mode == LightMode.SOS) {
                                pendingConfirmation = mode
                            } else {
                                onSelectMode(mode)
                            }
                        }
                    },
                    enabled = !state.running,
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    icon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.height(16.dp))
                    },
                    label = { Text(label) },
                    modifier = Modifier.height(44.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        PowerDial(
            lit = state.ledOn,
            flashing = state.running && state.mode != LightMode.TORCH,
            onToggle = onMainAction,
            contentDescription = when {
                state.running -> "Detener luz"
                state.mode == LightMode.SOS -> "Iniciar señal SOS en código Morse"
                else -> "Encender linterna"
            },
        )

        Spacer(Modifier.height(16.dp))

        val status = when {
            state.running && state.mode == LightMode.STROBE ->
                "Estrobo activo · ${state.flashesPerSecond} Hz"

            state.running && state.mode == LightMode.SOS -> "SOS en Morse activo"
            state.running -> "Linterna encendida"
            else -> "Apagada"
        }
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            color = if (state.running) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.mode == LightMode.STROBE) {
            SpeedCard(
                title = "Velocidad del estrobo",
                value = state.flashesPerSecond,
                onValue = onFlashesPerSecond,
            )
        } else if (state.mode == LightMode.SOS) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(
                        text = "Señal SOS en código Morse",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "···  −−−  ···\n3 cortos · 3 largos · 3 cortos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (state.mode != LightMode.TORCH) {
            WarningCard(
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "El flash se apaga al salir.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    pendingConfirmation?.let { mode ->
        val sos = mode == LightMode.SOS
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text(if (sos) "Confirma la señal SOS" else "Confirma el modo estrobo") },
            text = {
                Text(
                    "La luz intermitente puede desencadenar convulsiones fotosensibles. " +
                            "No la uses cerca de personas sensibles ni mires directamente al flash. " +
                            "¿Quieres continuar?",
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) { Text("Cancelar") }
            },
            confirmButton = {
                Button(onClick = {
                    pendingConfirmation = null
                    onSelectMode(mode)
                    onMainAction()
                }) {
                    Text(if (sos) "Activar SOS" else "Activar estrobo")
                }
            },
        )
    }
}

@Composable
private fun SpeedCard(
    title: String,
    value: Int,
    onValue: (Int) -> Unit,
) {
    val max = StrobeTiming.MAX_FLASHES_PER_SECOND
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (value == 1) "1 destello/seg" else "$value destellos/seg",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValue(it.toInt()) },
                valueRange = StrobeTiming.MIN_FLASHES_PER_SECOND.toFloat()..max.toFloat(),
                steps = max - StrobeTiming.MIN_FLASHES_PER_SECOND - 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Velocidad, flashes por segundo" },
            )
            Row {
                Text(
                    text = "1 destello/seg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$max destellos/seg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WarningCard(modifier: Modifier = Modifier) {
    val warningBg = Color(0xFFFFF6E1)
    val warningInk = Color(0xFF754B0C)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = warningBg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = warningInk,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Precaución: los destellos pueden afectar a personas fotosensibles",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = warningInk,
                )
                Text(
                    text = "No uses señales intermitentes cerca de personas sensibles.",
                    style = MaterialTheme.typography.labelMedium,
                    color = warningInk,
                )
            }
        }
    }
}

@Composable
private fun SendPage(
    state: FlashEngine.State,
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClicked: () -> Unit,
    onStopTransmission: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.padding(top = 10.dp)) {
        Text(
            text = "Mensaje para transmitir",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            enabled = !state.transmitting,
            placeholder = { Text("Escribe texto sin límite práctico") },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Destellos enviados",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.sentFlashes} de ${state.totalFlashes}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = {
                if (state.totalFlashes == 0) 0f
                else state.sentFlashes.toFloat() / state.totalFlashes
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics {
                    contentDescription =
                        "Destellos enviados: ${state.sentFlashes} de ${state.totalFlashes}"
                },
        )

        val statusLine = when (state.transmissionPhase) {
            FlashEngine.TransmissionPhase.PREPARING ->
                "El envío comienza en ${state.preparationSecondsRemaining}…"

            FlashEngine.TransmissionPhase.RUNNING ->
                "Transmitiendo · ${state.transmittingCharacters} caracteres"

            FlashEngine.TransmissionPhase.COMPLETED -> "Transmisión completada"
            FlashEngine.TransmissionPhase.STOPPED ->
                if (state.totalFlashes > 0 && state.sentFlashes < state.totalFlashes) {
                    "Envío detenido · ${state.sentFlashes} de ${state.totalFlashes} destellos"
                } else {
                    defaultSendStatus
                }

            FlashEngine.TransmissionPhase.IDLE -> defaultSendStatus
        }
        Text(
            text = statusLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = {
                focusManager.clearFocus()
                if (state.transmitting) onStopTransmission else onSendClicked()
            },
            colors = if (state.transmitting) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(48.dp),
        ) {
            Text(
                if (state.transmitting) "DETENER TRANSMISIÓN" else "ENVIAR CON FLASH",
                fontWeight = FontWeight.SemiBold,
            )
        }

        WarningCard(modifier = Modifier.padding(top = 12.dp))
    }
}

private const val defaultSendStatus = "Morse a 200 ms por punto · alfabeto internacional"

@Composable
private fun ReceivePage(
    receiverState: MorseReceiver.UiState,
    receivedText: String,
    onReceivedTextChange: (String) -> Unit,
    onReceiveClicked: () -> Unit,
    onTextureViewReady: (TextureView) -> Unit,
    onSurfaceAvailable: (TextureView) -> Unit,
) {
    val listening = receiverState.listening

    Column(Modifier.padding(top = 10.dp)) {
        Text(
            text = receiverState.hint.ifEmpty {
                "Centra la luz del teléfono emisor en la vista."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF120F08),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(220.dp),
        ) {
            AndroidView(
                factory = { context ->
                    TextureView(context).also { created ->
                        created.contentDescription =
                            "Vista de cámara para recibir Morse luminoso"
                        onTextureViewReady(created)
                        created.surfaceTextureListener =
                            object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surface: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    onSurfaceAvailable(created)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surface: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                }

                                override fun onSurfaceTextureDestroyed(
                                    surface: android.graphics.SurfaceTexture,
                                ): Boolean = true

                                override fun onSurfaceTextureUpdated(
                                    surface: android.graphics.SurfaceTexture,
                                ) {
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = receivedText,
                onValueChange = onReceivedTextChange,
                placeholder = { Text("El texto recibido aparecerá aquí.") },
                minLines = 2,
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
            )
        }
        Button(
            onClick = onReceiveClicked,
            colors = if (listening) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(46.dp),
        ) {
            if (receiverState.status == MorseReceiver.Status.STARTING) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (listening) "DETENER RECEPCIÓN" else "INICIAR RECEPCIÓN",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
