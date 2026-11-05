package com.javierrb35.flashlight;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.TextureView;
import android.util.Size;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.ByteBuffer;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 41;
    private static final int PAGE_LIGHT = 0;
    private static final int PAGE_SEND = 1;
    private static final int PAGE_RECEIVE = 2;
    private static final int PENDING_LIGHT = 0;
    private static final int PENDING_SEND = 1;
    private static final int PENDING_RECEIVE = 2;
    private static final int MODE_TORCH = 0;
    private static final int MODE_STROBE = 1;
    private static final int MODE_SOS = 2;
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int BRIGHT_PIXEL_THRESHOLD = 220;
    private static final long MORSE_UNIT_MILLIS = MorseCodec.UNIT_MILLIS;
    private static final int NAVY = BrandPalette.NAVY;
    private static final int INK = BrandPalette.INK;
    private static final int GOLD = BrandPalette.YELLOW;
    private static final int PAPER = BrandPalette.CANVAS;
    private static final int MUTED = BrandPalette.MUTED;
    private static final int WARNING_BG = Color.rgb(255, 246, 225);
    private static final int WARNING_INK = Color.rgb(117, 75, 12);

    private UpdateUi updates;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable strobeTick = this::toggleStrobeOutput;
    private final Runnable sosTick = this::advanceSos;
    private final Runnable sendTick = this::advanceTransmission;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice receivingCamera;
    private CameraCaptureSession receiveSession;
    private Surface receivePreviewSurface;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private TextView mainAction;
    private TextView status;
    private TextView speedLabel;
    private TextView torchModeButton;
    private TextView strobeModeButton;
    private TextView sosModeButton;
    private TextView lightPageButton;
    private TextView sendPageButton;
    private TextView receivePageButton;
    private SeekBar speedSlider;
    private LinearLayout speedCard;
    private LinearLayout lightModeRow;
    private LinearLayout lightPanel;
    private LinearLayout communicationPanel;
    private LinearLayout senderPanel;
    private LinearLayout receiverPanel;
    private TextView senderStatus;
    private EditText messageEditor;
    private ProgressBar transmissionProgress;
    private TextView transmissionProgressLabel;
    private TextView sendButton;
    private TextView receiverStatus;
    private TextureView receivePreview;
    private EditText receivedText;
    private TextView receiveButton;
    private TextView speedTitle;
    private LinearLayout rangeRow;
    private TextView sosPatternHint;
    private LinearLayout warningCard;
    private int mode = MODE_TORCH;
    private int flashesPerSecond = 1;
    private boolean running;
    private boolean ledOn;
    private boolean permissionPending;
    private int pageMode = PAGE_LIGHT;
    private int pendingAction = PENDING_LIGHT;
    private MorsePattern.Segment[] sosSegments;
    private int sosSegmentIndex;
    private MorseCodec.Encoding pendingEncoding;
    private MorseCodec.Encoding transmission;
    private int transmissionSegmentIndex;
    private int completedFlashes;
    private int totalFlashes;
    private boolean receiving;
    private boolean receiverCameraOpening;
    private boolean receiverStateInitialized;
    private boolean stableCameraLight;
    private boolean candidateCameraLight;
    private int candidateFrameCount;
    private long candidateStartedAt;
    private double ambientBrightness;
    private boolean ambientBrightnessInitialized;
    private final int[] brightnessHistogram = new int[256];
    private final MorseDecoder decoder = new MorseDecoder();
    private String lastDisplayedReceivedText = "";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        updates = new UpdateUi(this, () -> running || ledOn || receiving ||
                receiverCameraOpening || transmission != null);
        setContentView(createScreen());
        refreshControls();
        updates.onOpen();
    }

    private View createScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(12));
        page.setBackgroundColor(PAPER);
        page.setFocusableInTouchMode(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.flashlight_mascot);
        header.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = vertical();
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("Flashlight", 26, NAVY, true);
        heading.addView(title);
        heading.addView(text("LUZ A TU MANERA · SIMPLE Y SEGURA", 10, MUTED, true));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView updateButton = text("↻", 28, BrandPalette.TURQUOISE, true);
        updateButton.setGravity(Gravity.CENTER);
        updateButton.setContentDescription("Comprobar actualizaciones de FlashLight");
        updateButton.setTooltipText("Buscar actualizaciones");
        updateButton.setOnClickListener(v -> updates.openMenu());
        header.addView(updateButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(header);

        LinearLayout pageModes = new LinearLayout(this);
        pageModes.setOrientation(LinearLayout.HORIZONTAL);
        pageModes.setPadding(dp(4), dp(4), dp(4), dp(4));
        pageModes.setBackground(round(BrandPalette.SELECTOR, 16));
        lightPageButton = pageModeButton("Luz", PAGE_LIGHT);
        sendPageButton = pageModeButton("Enviar", PAGE_SEND);
        receivePageButton = pageModeButton("Recibir", PAGE_RECEIVE);
        pageModes.addView(lightPageButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        pageModes.addView(sendPageButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        pageModes.addView(receivePageButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams pageModesParams = params(-1, -2);
        pageModesParams.topMargin = dp(8);
        page.addView(pageModes, pageModesParams);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(5), dp(5), dp(5), dp(5));
        modes.setBackground(round(BrandPalette.SELECTOR, 16));
        lightModeRow = modes;
        torchModeButton = modeButton("Linterna", MODE_TORCH);
        strobeModeButton = modeButton("Estrobo", MODE_STROBE);
        sosModeButton = modeButton("SOS", MODE_SOS);
        modes.addView(torchModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(strobeModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(sosModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams modesParams = params(-1, -2);
        modesParams.topMargin = dp(10);
        page.addView(modes, modesParams);

        lightPanel = vertical();
        lightPanel.setGravity(Gravity.CENTER);
        lightPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        lightPanel.setBackground(round(Color.WHITE, 24));
        lightPanel.setMinimumHeight(dp(142));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, 0, 1);
        panelParams.topMargin = dp(10);
        panelParams.bottomMargin = dp(10);
        page.addView(lightPanel, panelParams);

        mainAction = text("ENCENDER", 16, NAVY, true);
        mainAction.setGravity(Gravity.CENTER);
        mainAction.setMinHeight(dp(96));
        mainAction.setBackground(round(GOLD, 28));
        Drawable mascot = getDrawable(R.drawable.flashlight_mascot);
        if (mascot != null) {
            mascot.setBounds(0, 0, dp(30), dp(30));
            mainAction.setCompoundDrawables(mascot, null, null, null);
            mainAction.setCompoundDrawablePadding(dp(10));
            mainAction.setPadding(dp(18), 0, dp(18), 0);
        }
        mainAction.setElevation(dp(3));
        mainAction.setFocusable(true);
        mainAction.setContentDescription("Encender linterna");
        mainAction.setOnClickListener(v -> onMainAction());
        lightPanel.addView(mainAction, params(-1, dp(104)));

        status = text("Apagada", 16, MUTED, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(8);
        lightPanel.addView(status, statusParams);

        speedCard = vertical();
        speedCard.setPadding(dp(14), dp(10), dp(14), dp(8));
        speedCard.setBackground(round(Color.WHITE, 20));
        LinearLayout.LayoutParams speedParams = params(-1, -2);
        speedParams.topMargin = dp(0);
        page.addView(speedCard, speedParams);
        LinearLayout speedHeading = new LinearLayout(this);
        speedHeading.setGravity(Gravity.CENTER_VERTICAL);
        speedTitle = text("Velocidad", 15, INK, true);
        speedHeading.addView(speedTitle, new LinearLayout.LayoutParams(0, -2, 1));
        speedLabel = text("1 destello/seg", 14, NAVY, true);
        speedLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        speedHeading.addView(speedLabel);
        speedCard.addView(speedHeading);
        speedSlider = new SeekBar(this);
        speedSlider.setProgressTintList(ColorStateList.valueOf(BrandPalette.TURQUOISE));
        speedSlider.setThumbTintList(ColorStateList.valueOf(BrandPalette.CORAL));
        speedSlider.setMax(StrobeTiming.MAX_FLASHES_PER_SECOND
                - StrobeTiming.MIN_FLASHES_PER_SECOND);
        speedSlider.setProgress(0);
        speedSlider.setContentDescription("Velocidad, flashes por segundo");
        speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                flashesPerSecond = StrobeTiming.MIN_FLASHES_PER_SECOND + progress;
                updateSpeedLabel();
                if (running && mode == MODE_STROBE) {
                    handler.removeCallbacks(strobeTick);
                    handler.post(strobeTick);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        speedCard.addView(speedSlider, params(-1, dp(44)));
        TextView range = text("1 destello/seg", 12, MUTED, false);
        rangeRow = new LinearLayout(this);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        rangeRow.addView(range, new LinearLayout.LayoutParams(0, -2, 1));
        TextView rangeMax = text("6 destellos/seg", 12, MUTED, false);
        rangeMax.setGravity(Gravity.RIGHT);
        rangeRow.addView(rangeMax);
        speedCard.addView(rangeRow);
        sosPatternHint = text("···  −−−  ···\n3 cortos · 3 largos · 3 cortos",
                12, NAVY, true);
        sosPatternHint.setGravity(Gravity.CENTER);
        speedCard.addView(sosPatternHint);

        communicationPanel = vertical();
        communicationPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        communicationPanel.setBackground(round(Color.WHITE, 20));
        LinearLayout.LayoutParams communicationParams = new LinearLayout.LayoutParams(-1, 0, 1);
        communicationParams.topMargin = dp(10);
        communicationParams.bottomMargin = dp(4);
        page.addView(communicationPanel, communicationParams);

        senderPanel = vertical();
        senderPanel.setVisibility(View.GONE);
        communicationPanel.addView(senderPanel,
                new LinearLayout.LayoutParams(-1, 0, 1));
        senderPanel.addView(text("Mensaje para transmitir", 15, INK, true));
        messageEditor = new EditText(this);
        messageEditor.setTextColor(INK);
        messageEditor.setHintTextColor(MUTED);
        messageEditor.setTextSize(16);
        messageEditor.setHint("Escribe texto sin límite práctico");
        messageEditor.setGravity(Gravity.TOP | Gravity.START);
        messageEditor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditor.setImeOptions(EditorInfo.IME_ACTION_NONE);
        messageEditor.setMinLines(2);
        messageEditor.setMaxLines(4);
        messageEditor.setPadding(dp(12), dp(10), dp(12), dp(10));
        messageEditor.setBackground(round(PAPER, 14));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1);
        editorParams.topMargin = dp(8);
        senderPanel.addView(messageEditor, editorParams);
        LinearLayout progressHeading = new LinearLayout(this);
        progressHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView progressTitle = text("Destellos enviados", 12, MUTED, false);
        progressHeading.addView(progressTitle, new LinearLayout.LayoutParams(0, -2, 1));
        transmissionProgressLabel = text("0 de 0", 12, INK, true);
        transmissionProgressLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        progressHeading.addView(transmissionProgressLabel);
        LinearLayout.LayoutParams progressHeadingParams = params(-1, -2);
        progressHeadingParams.topMargin = dp(6);
        senderPanel.addView(progressHeading, progressHeadingParams);
        transmissionProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        transmissionProgress.setMax(1);
        transmissionProgress.setProgress(0);
        transmissionProgress.setContentDescription("Progreso de destellos enviados");
        LinearLayout.LayoutParams progressParams = params(-1, dp(12));
        progressParams.topMargin = dp(2);
        senderPanel.addView(transmissionProgress, progressParams);
        messageEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable editable) {
                if (transmission == null) {
                    completedFlashes = 0;
                    totalFlashes = 0;
                    updateTransmissionProgress();
                }
            }
        });
        senderStatus = text("Morse a 200 ms por punto · alfabeto internacional",
                12, MUTED, false);
        LinearLayout.LayoutParams senderStatusParams = params(-1, -2);
        senderStatusParams.topMargin = dp(6);
        senderPanel.addView(senderStatus, senderStatusParams);
        sendButton = actionButton("ENVIAR CON FLASH", this::onSendClicked);
        LinearLayout.LayoutParams sendButtonParams = params(-1, dp(46));
        sendButtonParams.topMargin = dp(8);
        senderPanel.addView(sendButton, sendButtonParams);

        receiverPanel = vertical();
        receiverPanel.setVisibility(View.GONE);
        communicationPanel.addView(receiverPanel,
                new LinearLayout.LayoutParams(-1, 0, 1));
        receiverStatus = text("Centra la luz del teléfono emisor en la vista.",
                13, MUTED, false);
        receiverPanel.addView(receiverStatus);
        FrameLayout previewContainer = new FrameLayout(this);
        previewContainer.setBackground(round(NAVY, 14));
        previewContainer.setClipToOutline(true);
        receivePreview = new TextureView(this);
        previewContainer.addView(receivePreview, new FrameLayout.LayoutParams(-1, -1));
        receivePreview.setContentDescription("Vista de cámara para recibir Morse luminoso");
        receivePreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (receiving) openReceiverCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1);
        previewParams.topMargin = dp(6);
        previewParams.bottomMargin = dp(6);
        receiverPanel.addView(previewContainer, previewParams);
        receivedText = new EditText(this);
        receivedText.setText("El texto recibido aparecerá aquí.");
        receivedText.setTextSize(18);
        receivedText.setTextColor(INK);
        receivedText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        receivedText.setHintTextColor(MUTED);
        receivedText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        receivedText.setGravity(Gravity.TOP | Gravity.START);
        receivedText.setMinLines(2);
        receivedText.setMaxLines(2);
        receivedText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        receivedText.setPadding(dp(12), dp(8), dp(12), dp(8));
        receivedText.setBackground(round(Color.WHITE, 12));
        receiverPanel.addView(receivedText, params(-1, -2));
        receiveButton = actionButton("INICIAR RECEPCIÓN", this::onReceiveClicked);
        LinearLayout.LayoutParams receiveButtonParams = params(-1, dp(44));
        receiveButtonParams.topMargin = dp(6);
        receiverPanel.addView(receiveButton, receiveButtonParams);

        warningCard = vertical();
        warningCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        warningCard.setBackground(round(WARNING_BG, 16));
        LinearLayout.LayoutParams warningParams = params(-1, -2);
        warningParams.topMargin = dp(8);
        page.addView(warningCard, warningParams);
        warningCard.addView(text("Precaución: los destellos pueden afectar a personas fotosensibles",
                12, WARNING_INK, true));
        TextView warning = text(
                "No uses señales intermitentes cerca de personas sensibles.",
                11, WARNING_INK, false);
        warningCard.addView(warning);

        TextView footer = text("El flash se apaga al salir.", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = params(-1, -2);
        footerParams.topMargin = dp(6);
        page.addView(footer, footerParams);
        return page;
    }

    private TextView modeButton(String label, int selectedMode) {
        TextView button = text(label, 15, INK, true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Modo " + label);
        button.setTextSize(13);
        button.setOnClickListener(v -> selectMode(selectedMode));
        return button;
    }

    private TextView pageModeButton(String label, int selectedPage) {
        TextView button = text(label, 14, INK, true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Abrir modo " + label);
        button.setOnClickListener(v -> selectPageMode(selectedPage));
        return button;
    }

    private TextView actionButton(String label, Runnable action) {
        TextView button = text(label, 14, NAVY, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(GOLD, 14));
        button.setMinHeight(dp(44));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void selectPageMode(int newPageMode) {
        if (pageMode == newPageMode) return;
        stopLight();
        stopTransmission();
        stopReceiver(false);
        pageMode = newPageMode;
        refreshControls();
    }

    private void selectMode(int newMode) {
        if (mode == newMode) return;
        stopLight();
        mode = newMode;
        refreshControls();
    }

    private void onMainAction() {
        if (running) {
            stopLight();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission(PENDING_LIGHT);
            return;
        }
        if (mode == MODE_STROBE || mode == MODE_SOS) {
            confirmFlashingMode();
        } else {
            startLight();
        }
    }

    private void requestCameraPermission(int action) {
        permissionPending = true;
        pendingAction = action;
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    }

    private void onSendClicked() {
        if (transmission != null) {
            stopTransmission();
            return;
        }
        MorseCodec.Encoding encoded = MorseCodec.encode(messageEditor.getText().toString());
        if (encoded.text.isEmpty()) {
            showFailure("Escribe al menos una letra o un número que pueda transmitirse en Morse.");
            return;
        }
        completedFlashes = 0;
        totalFlashes = encoded.flashCount;
        updateTransmissionProgress();
        pendingEncoding = encoded;
        String warning = encoded.skippedCharacters > 0
                ? "\n\nSe omitirán " + encoded.skippedCharacters
                        + " carácter(es) no compatibles."
                : "";
        String normalizedPreview = encoded.text.length() <= 48
                ? encoded.text : encoded.text.substring(0, 48) + "…";
        InputMethodManager keyboard = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(messageEditor.getWindowToken(), 0);
        new AlertDialog.Builder(this)
                .setTitle("Enviar por luz")
                .setMessage("Texto: " + normalizedPreview + "\n"
                        + encoded.text.length() + " caracteres · punto de 200 ms."
                        + warning + "\n\nInicia primero la recepción y centra ambos teléfonos.")
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    pendingEncoding = null;
                    completedFlashes = 0;
                    totalFlashes = 0;
                    updateTransmissionProgress();
                })
                .setPositiveButton("Transmitir", (dialog, which) -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestCameraPermission(PENDING_SEND);
                    } else {
                        startTransmission(pendingEncoding);
                    }
                })
                .show();
    }

    private void onReceiveClicked() {
        if (receiving) {
            stopReceiver(true);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission(PENDING_RECEIVE);
            return;
        }
        startReceiver();
    }

    private void startTransmission(MorseCodec.Encoding encoded) {
        if (encoded == null) return;
        pendingEncoding = null;
        try {
            cameraId = findFlashCamera();
            if (cameraId == null) {
                showFailure("Este dispositivo no tiene un flash disponible para transmitir.");
                return;
            }
            transmission = encoded;
            transmissionSegmentIndex = 0;
            completedFlashes = 0;
            totalFlashes = encoded.flashCount;
            updateTransmissionProgress();
            running = true;
            ledOn = false;
            messageEditor.setEnabled(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            senderStatus.setText("Preparando transmisión · " + encoded.text.length() + " caracteres");
            sendButton.setText("DETENER TRANSMISIÓN");
            advanceTransmission();
        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
            stopTransmission();
            showFailure("No se pudo acceder al flash para transmitir.");
        }
    }

    private void advanceTransmission() {
        if (transmission == null || pageMode != PAGE_SEND) return;
        if (transmissionSegmentIndex > 0
                && transmission.segments.get(transmissionSegmentIndex - 1).lightOn) {
            completedFlashes++;
            updateTransmissionProgress();
        }
        if (transmissionSegmentIndex >= transmission.segments.size()) {
            stopTransmission();
            senderStatus.setText("Transmisión completada");
            Toast.makeText(this, "Mensaje Morse enviado", Toast.LENGTH_SHORT).show();
            return;
        }
        MorseCodec.Segment segment = transmission.segments.get(transmissionSegmentIndex++);
        try {
            ledOn = segment.lightOn;
            cameraManager.setTorchMode(cameraId, ledOn);
            senderStatus.setText("Transmitiendo · " + transmission.text.length() + " caracteres");
            handler.postDelayed(sendTick, segment.durationMillis);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
            stopTransmission();
            showFailure("El flash dejó de estar disponible. Se detuvo la transmisión.");
        }
    }

    private void stopTransmission() {
        handler.removeCallbacks(sendTick);
        boolean wasActive = transmission != null || running || ledOn;
        transmission = null;
        pendingEncoding = null;
        running = false;
        ledOn = false;
        if (messageEditor != null) messageEditor.setEnabled(true);
        if (wasActive && cameraId != null && cameraManager != null) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
                android.util.Log.w("FlashLight", "No se pudo apagar el flash al detener el envío", error);
            }
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (sendButton != null) sendButton.setText("ENVIAR CON FLASH");
        if (senderStatus != null && pageMode == PAGE_SEND) {
            senderStatus.setText(wasActive && completedFlashes < totalFlashes
                    ? "Envío detenido · " + completedFlashes + " de " + totalFlashes
                            + " destellos"
                    : "Morse a 200 ms por punto · alfabeto internacional");
        }
        updateTransmissionProgress();
    }

    private void startReceiver() {
        decoder.reset();
        receivedText.setText("");
        lastDisplayedReceivedText = "";
        receiverStateInitialized = false;
        candidateFrameCount = 0;
        ambientBrightnessInitialized = false;
        receiving = true;
        receiverStatus.setText("Centra la luz del emisor. Inicia primero esta recepción.");
        receiveButton.setText("DETENER RECEPCIÓN");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startCameraThread();
        if (receivePreview.isAvailable()) openReceiverCamera();
    }

    private void openReceiverCamera() {
        if (!receiving || receivingCamera != null || receiverCameraOpening || cameraHandler == null
                || !receivePreview.isAvailable()) return;
        try {
            String id = findReceivingCamera();
            if (id == null) {
                stopReceiver(false);
                showFailure("No se encontró una cámara compatible con vista previa.");
                return;
            }
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                stopReceiver(false);
                showFailure("La cámara no permite analizar fotogramas.");
                return;
            }
            Size size = choosePreviewSize(map.getOutputSizes(ImageFormat.YUV_420_888));
            if (size == null) {
                stopReceiver(false);
                showFailure("La cámara no ofrece una resolución compatible para recibir Morse.");
                return;
            }
            imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                    ImageFormat.YUV_420_888, 3);
            imageReader.setOnImageAvailableListener(this::analyzeCameraFrame, cameraHandler);
            SurfaceTexture texture = receivePreview.getSurfaceTexture();
            texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            receiverCameraOpening = true;
            cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    receiverCameraOpening = false;
                    if (!receiving) {
                        camera.close();
                        return;
                    }
                    receivingCamera = camera;
                    createReceiverSession(texture);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    receiverCameraOpening = false;
                    camera.close();
                    receivingCamera = null;
                    if (receiving) runOnUiThread(() -> stopReceiver(true));
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    receiverCameraOpening = false;
                    camera.close();
                    receivingCamera = null;
                    if (receiving) runOnUiThread(() -> {
                        stopReceiver(false);
                        showFailure("No se pudo abrir la cámara receptora.");
                    });
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
            receiverCameraOpening = false;
            stopReceiver(false);
            showFailure("No se pudo iniciar la cámara receptora.");
        }
    }

    private void createReceiverSession(SurfaceTexture texture) {
        if (!receiving || receivingCamera == null || imageReader == null) return;
        try {
            receivePreviewSurface = new Surface(texture);
            Surface analysisSurface = imageReader.getSurface();
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(receivePreviewSurface);
            surfaces.add(analysisSurface);
            receivingCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (!receiving || receivingCamera == null) {
                        session.close();
                        return;
                    }
                    receiveSession = session;
                    try {
                        CaptureRequest.Builder request = receivingCamera.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(receivePreviewSurface);
                        request.addTarget(analysisSurface);
                        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(request.build(), null, cameraHandler);
                        runOnUiThread(() ->
                                receiverStatus.setText("Escuchando luz · centra el emisor"));
                    } catch (CameraAccessException | IllegalStateException error) {
                        runOnUiThread(() -> {
                            stopReceiver(false);
                            showFailure("No se pudo iniciar el análisis de la cámara.");
                        });
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    runOnUiThread(() -> {
                        stopReceiver(false);
                        showFailure("La cámara no pudo preparar la recepción.");
                    });
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException error) {
            runOnUiThread(() -> {
                stopReceiver(false);
                showFailure("No se pudo configurar la cámara receptora.");
            });
        }
    }

    private void analyzeCameraFrame(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) return;
            ByteBuffer luma = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            int width = image.getWidth();
            int height = image.getHeight();
            Arrays.fill(brightnessHistogram, 0);
            int sampledPixels = 0;
            for (int y = height / 3; y < (height * 2) / 3; y += 2) {
                for (int x = width / 3; x < (width * 2) / 3; x += 2) {
                    int index = y * rowStride + x * pixelStride;
                    if (index >= 0 && index < luma.limit()) {
                        brightnessHistogram[luma.get(index) & 0xFF]++;
                        sampledPixels++;
                    }
                }
            }
            int percentile = Math.max(0, (int) Math.ceil(sampledPixels * 0.995) - 1);
            int value = 0;
            for (int cumulative = brightnessHistogram[0]; value < 255; value++) {
                if (cumulative > percentile) break;
                cumulative += brightnessHistogram[value + 1];
            }
            if (!ambientBrightnessInitialized) {
                ambientBrightness = value;
                ambientBrightnessInitialized = true;
            }
            boolean bright = value >= BRIGHT_PIXEL_THRESHOLD
                    && value - ambientBrightness >= 18;
            if (!bright) ambientBrightness = ambientBrightness * 0.95 + value * 0.05;
            processBrightness(bright, SystemClock.elapsedRealtime());
        } finally {
            image.close();
        }
    }

    private void processBrightness(boolean bright, long now) {
        if (!receiving) return;
        synchronized (decoder) {
            if (!receiverStateInitialized) {
                candidateCameraLight = bright;
                stableCameraLight = bright;
                candidateFrameCount = 0;
                receiverStateInitialized = true;
                publishReceivedText(decoder.sample(bright, now));
                return;
            }
            if (bright != candidateCameraLight) {
                candidateCameraLight = bright;
                candidateFrameCount = 1;
                candidateStartedAt = now;
            } else {
                candidateFrameCount++;
            }
            if (candidateFrameCount >= 2 && candidateCameraLight != stableCameraLight) {
                stableCameraLight = candidateCameraLight;
                publishReceivedText(decoder.sample(stableCameraLight, candidateStartedAt));
            } else if (candidateCameraLight == stableCameraLight) {
                publishReceivedText(decoder.sample(stableCameraLight, now));
            }
        }
    }

    private void publishReceivedText(String text) {
        if (text.equals(lastDisplayedReceivedText)) return;
        lastDisplayedReceivedText = text;
        runOnUiThread(() -> receivedText.setText(text.isEmpty()
                ? "Esperando mensaje…" : text));
    }

    private String findReceivingCamera() throws CameraAccessException {
        String fallback = null;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null || choosePreviewSize(
                    map.getOutputSizes(ImageFormat.YUV_420_888)) == null) continue;
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            if (fallback == null) fallback = id;
        }
        return fallback;
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        Size best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Size size : sizes) {
            if (size.getWidth() < 320 || size.getHeight() < 240) continue;
            long distance = Math.abs((long) size.getWidth() * size.getHeight()
                    - (long) PREVIEW_WIDTH * PREVIEW_HEIGHT);
            if (distance < bestDistance) {
                best = size;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void startCameraThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("FlashLightCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopReceiver(boolean keepTranscript) {
        boolean wasReceiving = receiving;
        receiving = false;
        if (wasReceiving) {
            String finalText;
            synchronized (decoder) {
                finalText = decoder.finish(SystemClock.elapsedRealtime());
            }
            if (keepTranscript && !finalText.isEmpty()) {
                lastDisplayedReceivedText = finalText;
                receivedText.setText(finalText);
            }
        }
        receiving = false;
        if (receiveSession != null) {
            receiveSession.close();
            receiveSession = null;
        }
        if (receivingCamera != null) {
            receivingCamera.close();
            receivingCamera = null;
        }
        receiverCameraOpening = false;
        if (receivePreviewSurface != null) {
            receivePreviewSurface.release();
            receivePreviewSurface = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (receiveButton != null) receiveButton.setText("INICIAR RECEPCIÓN");
        if (receiverStatus != null) receiverStatus.setText(keepTranscript
                ? "Recepción detenida" : "Centra la luz del teléfono emisor en la vista.");
    }

    private void confirmFlashingMode() {
        boolean sos = mode == MODE_SOS;
        new AlertDialog.Builder(this)
                .setTitle(sos ? "Confirma la señal SOS" : "Confirma el modo estrobo")
                .setMessage("La luz intermitente puede desencadenar convulsiones "
                        + "fotosensibles. No la uses cerca de personas sensibles ni mires "
                        + "directamente al flash. ¿Quieres continuar?")
                .setNegativeButton("Cancelar", (dialog, which) -> refreshControls())
                .setPositiveButton(sos ? "Activar SOS" : "Activar estrobo",
                        (dialog, which) -> startLight())
                .show();
    }

    private void startLight() {
        try {
            cameraId = findFlashCamera();
            if (cameraId == null) {
                showFailure("Este dispositivo no tiene un flash disponible.");
                return;
            }
            running = true;
            ledOn = true;
            cameraManager.setTorchMode(cameraId, true);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            refreshControls();
            if (mode == MODE_STROBE) {
                handler.postDelayed(strobeTick, StrobeTiming.halfPeriodMillis(flashesPerSecond));
            } else if (mode == MODE_SOS) {
                sosSegments = MorsePattern.sos();
                sosSegmentIndex = 0;
                handler.postDelayed(sosTick, sosSegments[0].durationMillis);
            }
        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
            stopLight();
            showFailure("No se pudo encender el flash. Cierra otras aplicaciones que usen la cámara e inténtalo de nuevo.");
        }
    }

    private String findFlashCamera() throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            android.hardware.camera2.CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(id);
            Boolean available = characteristics.get(
                    android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (Boolean.TRUE.equals(available)) return id;
        }
        return null;
    }

    private void toggleStrobeOutput() {
        if (!running || mode != MODE_STROBE || cameraId == null) return;
        try {
            ledOn = !ledOn;
            cameraManager.setTorchMode(cameraId, ledOn);
            refreshControls();
            handler.postDelayed(strobeTick, StrobeTiming.halfPeriodMillis(flashesPerSecond));
        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
            stopLight();
            showFailure("El flash dejó de estar disponible. El efecto se ha detenido.");
        }
    }

    private void advanceSos() {
        if (!running || mode != MODE_SOS || cameraId == null || sosSegments == null) return;
        sosSegmentIndex = (sosSegmentIndex + 1) % sosSegments.length;
        MorsePattern.Segment segment = sosSegments[sosSegmentIndex];
        try {
            ledOn = segment.lightOn;
            cameraManager.setTorchMode(cameraId, ledOn);
            refreshControls();
            handler.postDelayed(sosTick, segment.durationMillis);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
            stopLight();
            showFailure("El flash dejó de estar disponible. La señal SOS se ha detenido.");
        }
    }

    private void stopLight() {
        handler.removeCallbacks(strobeTick);
        handler.removeCallbacks(sosTick);
        boolean wasActive = running || ledOn;
        running = false;
        ledOn = false;
        sosSegments = null;
        if (wasActive && cameraId != null && cameraManager != null) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
                android.util.Log.w("FlashLight", "No se pudo apagar el flash al detenerlo", error);
            }
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshControls();
    }

    private void refreshControls() {
        if (mainAction == null) return;
        boolean lightPage = pageMode == PAGE_LIGHT;
        boolean sendPage = pageMode == PAGE_SEND;
        boolean receivePage = pageMode == PAGE_RECEIVE;
        boolean strobeMode = mode == MODE_STROBE;
        boolean sosMode = mode == MODE_SOS;
        updateModeButton(lightPageButton, lightPage);
        updateModeButton(sendPageButton, sendPage);
        updateModeButton(receivePageButton, receivePage);
        updateModeButton(torchModeButton, mode == MODE_TORCH);
        updateModeButton(strobeModeButton, strobeMode);
        updateModeButton(sosModeButton, sosMode);
        lightModeRow.setVisibility(lightPage ? View.VISIBLE : View.GONE);
        lightPanel.setVisibility(lightPage ? View.VISIBLE : View.GONE);
        communicationPanel.setVisibility(lightPage ? View.GONE : View.VISIBLE);
        senderPanel.setVisibility(sendPage ? View.VISIBLE : View.GONE);
        receiverPanel.setVisibility(receivePage ? View.VISIBLE : View.GONE);
        speedSlider.setEnabled(strobeMode);
        warningCard.setVisibility((lightPage && (strobeMode || sosMode)) || sendPage
                ? View.VISIBLE : View.GONE);
        mainAction.setText(running ? "DETENER" : (sosMode ? "INICIAR SOS" : "ENCENDER"));
        mainAction.setBackground(round(running ? Color.rgb(255, 225, 222) : GOLD, 28));
        mainAction.setContentDescription(running ? "Detener luz"
                : (sosMode ? "Iniciar señal SOS en código Morse" : "Encender linterna"));
        speedCard.setVisibility(lightPage && (strobeMode || sosMode) ? View.VISIBLE : View.GONE);
        speedTitle.setText(sosMode ? "Señal SOS en código Morse" : "Velocidad del estrobo");
        speedLabel.setVisibility(sosMode ? View.GONE : View.VISIBLE);
        speedSlider.setVisibility(sosMode ? View.GONE : View.VISIBLE);
        rangeRow.setVisibility(sosMode ? View.GONE : View.VISIBLE);
        sosPatternHint.setVisibility(sosMode ? View.VISIBLE : View.GONE);
        status.setText(running
                ? (strobeMode ? "Estrobo activo · " + flashesPerSecond + " Hz"
                        : (sosMode ? "SOS en Morse activo" : "Linterna encendida"))
                : "Apagada");
        status.setTextColor(running ? NAVY : MUTED);
        if (sendButton != null) {
            sendButton.setText(transmission == null ? "ENVIAR CON FLASH" : "DETENER TRANSMISIÓN");
        }
        if (receiveButton != null) {
            receiveButton.setText(receiving ? "DETENER RECEPCIÓN" : "INICIAR RECEPCIÓN");
        }
        if (updates != null) updates.onControlsRefreshed();
    }

    private void updateModeButton(TextView button, boolean selected) {
        button.setTextColor(selected ? NAVY : INK);
        button.setBackground(round(selected ? GOLD : Color.TRANSPARENT, 12));
        button.setSelected(selected);
    }

    private void updateSpeedLabel() {
        if (speedLabel == null) return;
        speedLabel.setText(flashesPerSecond + (flashesPerSecond == 1
                ? " destello/seg" : " destellos/seg"));
    }

    private void updateTransmissionProgress() {
        if (transmissionProgress == null || transmissionProgressLabel == null) return;
        transmissionProgress.setMax(Math.max(1, totalFlashes));
        transmissionProgress.setProgress(Math.min(completedFlashes, totalFlashes));
        transmissionProgressLabel.setText(completedFlashes + " de " + totalFlashes);
        transmissionProgress.setContentDescription("Destellos enviados: " + completedFlashes
                + " de " + totalFlashes);
    }

    private void showFailure(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_CAMERA || !permissionPending) return;
        permissionPending = false;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            showFailure("Se necesita permiso de cámara para controlar el flash o recibir señales. No se toman fotos ni se graba vídeo.");
            return;
        }
        switch (pendingAction) {
            case PENDING_SEND:
                startTransmission(pendingEncoding);
                break;
            case PENDING_RECEIVE:
                startReceiver();
                break;
            default:
                if (mode == MODE_STROBE || mode == MODE_SOS) confirmFlashingMode();
                else startLight();
                break;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && updates != null) updates.onControlsRefreshed();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (updates != null) updates.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updates != null) updates.onResume();
    }

    @Override
    protected void onPause() {
        stopLight();
        stopTransmission();
        stopReceiver(true);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setLineSpacing(dp(2), 1.08f);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }
}
