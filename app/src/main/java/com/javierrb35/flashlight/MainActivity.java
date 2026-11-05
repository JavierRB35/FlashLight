package com.javierrb35.flashlight;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 41;
    private static final int MODE_TORCH = 0;
    private static final int MODE_STROBE = 1;
    private static final int MODE_SOS = 2;
    private static final int NAVY = Color.rgb(16, 26, 44);
    private static final int INK = Color.rgb(28, 39, 58);
    private static final int GOLD = Color.rgb(245, 185, 66);
    private static final int PAPER = Color.rgb(245, 246, 248);
    private static final int MUTED = Color.rgb(101, 112, 130);
    private static final int WARNING_BG = Color.rgb(255, 246, 225);
    private static final int WARNING_INK = Color.rgb(117, 75, 12);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable strobeTick = this::toggleStrobeOutput;
    private final Runnable sosTick = this::advanceSos;
    private CameraManager cameraManager;
    private String cameraId;
    private TextView mainAction;
    private TextView status;
    private TextView speedLabel;
    private TextView torchModeButton;
    private TextView strobeModeButton;
    private TextView sosModeButton;
    private SeekBar speedSlider;
    private LinearLayout speedCard;
    private TextView speedTitle;
    private LinearLayout rangeRow;
    private TextView sosPatternHint;
    private LinearLayout warningCard;
    private int mode = MODE_TORCH;
    private int flashesPerSecond = 1;
    private boolean running;
    private boolean ledOn;
    private boolean permissionPending;
    private MorsePattern.Segment[] sosSegments;
    private int sosSegmentIndex;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        setContentView(createScreen());
        refreshControls();
    }

    private View createScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(12));
        page.setBackgroundColor(PAPER);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_flashlight);
        header.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = vertical();
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("FlashLight", 26, NAVY, true);
        heading.addView(title);
        heading.addView(text("LUZ A TU MANERA · SIMPLE Y SEGURA", 10, MUTED, true));
        header.addView(heading);
        page.addView(header);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(5), dp(5), dp(5), dp(5));
        modes.setBackground(round(Color.rgb(231, 234, 239), 16));
        torchModeButton = modeButton("Linterna", MODE_TORCH);
        strobeModeButton = modeButton("Estrobo", MODE_STROBE);
        sosModeButton = modeButton("SOS", MODE_SOS);
        modes.addView(torchModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(strobeModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(sosModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams modesParams = params(-1, -2);
        modesParams.topMargin = dp(10);
        page.addView(modes, modesParams);

        LinearLayout lightPanel = vertical();
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

        warningCard = vertical();
        warningCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        warningCard.setBackground(round(WARNING_BG, 16));
        LinearLayout.LayoutParams warningParams = params(-1, -2);
        warningParams.topMargin = dp(8);
        page.addView(warningCard, warningParams);
        warningCard.addView(text("⚠ Precaución con los destellos", 13, WARNING_INK, true));
        TextView warning = text(
                "Puede provocar convulsiones fotosensibles. No usar cerca de personas sensibles "
                        + "ni mirar directamente al LED.",
                12, WARNING_INK, false);
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
            permissionPending = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        if (mode == MODE_STROBE || mode == MODE_SOS) {
            confirmFlashingMode();
        } else {
            startLight();
        }
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
        boolean strobeMode = mode == MODE_STROBE;
        boolean sosMode = mode == MODE_SOS;
        updateModeButton(torchModeButton, mode == MODE_TORCH);
        updateModeButton(strobeModeButton, strobeMode);
        updateModeButton(sosModeButton, sosMode);
        speedSlider.setEnabled(strobeMode);
        warningCard.setVisibility(strobeMode || sosMode ? View.VISIBLE : View.GONE);
        mainAction.setText(running ? "DETENER" : (sosMode ? "INICIAR SOS" : "ENCENDER"));
        mainAction.setBackground(round(running ? Color.rgb(255, 225, 222) : GOLD, 28));
        mainAction.setContentDescription(running ? "Detener luz"
                : (sosMode ? "Iniciar señal SOS en código Morse" : "Encender linterna"));
        speedCard.setVisibility(strobeMode || sosMode ? View.VISIBLE : View.GONE);
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
    }

    private void updateModeButton(TextView button, boolean selected) {
        button.setTextColor(selected ? NAVY : INK);
        button.setBackground(round(selected ? Color.WHITE : Color.TRANSPARENT, 12));
        button.setSelected(selected);
    }

    private void updateSpeedLabel() {
        if (speedLabel == null) return;
        speedLabel.setText(flashesPerSecond + (flashesPerSecond == 1
                ? " destello/seg" : " destellos/seg"));
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
            showFailure("Se necesita permiso de cámara para controlar el flash. No se toman fotos ni se graba vídeo.");
            return;
        }
        if (mode == MODE_STROBE || mode == MODE_SOS) confirmFlashingMode();
        else startLight();
    }

    @Override
    protected void onPause() {
        stopLight();
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
