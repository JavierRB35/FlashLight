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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 41;
    private static final int MODE_TORCH = 0;
    private static final int MODE_STROBE = 1;
    private static final int NAVY = Color.rgb(16, 26, 44);
    private static final int INK = Color.rgb(28, 39, 58);
    private static final int GOLD = Color.rgb(245, 185, 66);
    private static final int PAPER = Color.rgb(245, 246, 248);
    private static final int MUTED = Color.rgb(101, 112, 130);
    private static final int WARNING_BG = Color.rgb(255, 246, 225);
    private static final int WARNING_INK = Color.rgb(117, 75, 12);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable strobeTick = this::toggleStrobeOutput;
    private CameraManager cameraManager;
    private String cameraId;
    private TextView mainAction;
    private TextView status;
    private TextView speedLabel;
    private TextView torchModeButton;
    private TextView strobeModeButton;
    private SeekBar speedSlider;
    private LinearLayout warningCard;
    private int mode = MODE_TORCH;
    private int flashesPerSecond = 1;
    private boolean running;
    private boolean ledOn;
    private boolean permissionPending;

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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(18), dp(22), dp(26));
        scroll.addView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_flashlight);
        header.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = vertical();
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("FlashLight", 26, NAVY, true);
        heading.addView(title);
        heading.addView(text("LUZ A TU MANERA", 11, MUTED, true));
        header.addView(heading);
        page.addView(header);

        TextView intro = text("Una linterna sencilla, siempre bajo tu control.", 15, MUTED, false);
        LinearLayout.LayoutParams introParams = params(-1, -2);
        introParams.topMargin = dp(18);
        page.addView(intro, introParams);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(5), dp(5), dp(5), dp(5));
        modes.setBackground(round(Color.rgb(231, 234, 239), 16));
        torchModeButton = modeButton("Linterna", MODE_TORCH);
        strobeModeButton = modeButton("Estrobo", MODE_STROBE);
        modes.addView(torchModeButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        modes.addView(strobeModeButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams modesParams = params(-1, -2);
        modesParams.topMargin = dp(25);
        page.addView(modes, modesParams);

        LinearLayout lightPanel = vertical();
        lightPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        lightPanel.setPadding(dp(18), dp(24), dp(18), dp(22));
        lightPanel.setBackground(round(Color.WHITE, 24));
        LinearLayout.LayoutParams panelParams = params(-1, -2);
        panelParams.topMargin = dp(15);
        page.addView(lightPanel, panelParams);

        mainAction = text("ENCENDER", 16, NAVY, true);
        mainAction.setGravity(Gravity.CENTER);
        mainAction.setMinHeight(dp(164));
        mainAction.setBackground(round(GOLD, 100));
        mainAction.setElevation(dp(3));
        mainAction.setFocusable(true);
        mainAction.setContentDescription("Encender linterna");
        mainAction.setOnClickListener(v -> onMainAction());
        lightPanel.addView(mainAction, params(-1, dp(164)));

        status = text("Apagada", 16, MUTED, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(16);
        lightPanel.addView(status, statusParams);

        LinearLayout speedCard = vertical();
        speedCard.setPadding(dp(18), dp(16), dp(18), dp(14));
        speedCard.setBackground(round(Color.WHITE, 20));
        LinearLayout.LayoutParams speedParams = params(-1, -2);
        speedParams.topMargin = dp(15);
        page.addView(speedCard, speedParams);
        TextView speedTitle = text("Velocidad del estrobo", 17, INK, true);
        speedCard.addView(speedTitle);
        speedLabel = text("1 destello por segundo", 14, MUTED, false);
        LinearLayout.LayoutParams speedLabelParams = params(-1, -2);
        speedLabelParams.topMargin = dp(4);
        speedCard.addView(speedLabel, speedLabelParams);
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
                    handler.postDelayed(strobeTick, StrobeTiming.halfPeriodMillis(flashesPerSecond));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        speedCard.addView(speedSlider, params(-1, dp(48)));
        TextView range = text("1 flash/seg", 12, MUTED, false);
        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        rangeRow.addView(range, new LinearLayout.LayoutParams(0, -2, 1));
        TextView rangeMax = text("4 flashes/seg", 12, MUTED, false);
        rangeMax.setGravity(Gravity.RIGHT);
        rangeRow.addView(rangeMax);
        speedCard.addView(rangeRow);

        warningCard = vertical();
        warningCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        warningCard.setBackground(round(WARNING_BG, 16));
        LinearLayout.LayoutParams warningParams = params(-1, -2);
        warningParams.topMargin = dp(15);
        page.addView(warningCard, warningParams);
        warningCard.addView(text("Precaución con los destellos", 15, WARNING_INK, true));
        TextView warning = text(
                "La luz intermitente puede provocar convulsiones en personas sensibles. "
                        + "No la uses cerca de personas con epilepsia, no mires directamente "
                        + "al LED y detén el efecto si alguien siente malestar.",
                14, WARNING_INK, false);
        LinearLayout.LayoutParams warningTextParams = params(-1, -2);
        warningTextParams.topMargin = dp(6);
        warningCard.addView(warning, warningTextParams);

        TextView footer = text("El flash se apaga al salir de la aplicación.", 13, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = params(-1, -2);
        footerParams.topMargin = dp(20);
        page.addView(footer, footerParams);
        return scroll;
    }

    private TextView modeButton(String label, int selectedMode) {
        TextView button = text(label, 15, INK, true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Modo " + label);
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
        if (mode == MODE_STROBE) {
            confirmStrobe();
        } else {
            startLight(false);
        }
    }

    private void confirmStrobe() {
        new AlertDialog.Builder(this)
                .setTitle("Confirma el modo estrobo")
                .setMessage("La luz intermitente puede desencadenar convulsiones "
                        + "fotosensibles. No la uses cerca de personas sensibles ni mires "
                        + "directamente al flash. ¿Quieres continuar?")
                .setNegativeButton("Cancelar", (dialog, which) -> refreshControls())
                .setPositiveButton("Activar estrobo", (dialog, which) -> startLight(true))
                .show();
    }

    private void startLight(boolean strobe) {
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
            if (strobe) {
                handler.postDelayed(strobeTick, StrobeTiming.halfPeriodMillis(flashesPerSecond));
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

    private void stopLight() {
        handler.removeCallbacks(strobeTick);
        boolean wasOn = ledOn;
        running = false;
        ledOn = false;
        if (wasOn && cameraId != null && cameraManager != null) {
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
        torchModeButton.setTextColor(strobeMode ? INK : NAVY);
        torchModeButton.setBackground(round(strobeMode ? Color.TRANSPARENT : Color.WHITE, 12));
        strobeModeButton.setTextColor(strobeMode ? NAVY : INK);
        strobeModeButton.setBackground(round(strobeMode ? Color.WHITE : Color.TRANSPARENT, 12));
        speedSlider.setEnabled(strobeMode && !running);
        warningCard.setVisibility(strobeMode ? View.VISIBLE : View.GONE);
        mainAction.setText(running ? "APAGAR" : "ENCENDER");
        mainAction.setBackground(round(running ? Color.rgb(255, 225, 222) : GOLD, 100));
        mainAction.setContentDescription(running ? "Apagar linterna" : "Encender linterna");
        status.setText(running
                ? (strobeMode ? "Estrobo activo · " + flashesPerSecond + " Hz" : "Linterna encendida")
                : "Apagada");
        status.setTextColor(running ? NAVY : MUTED);
    }

    private void updateSpeedLabel() {
        if (speedLabel == null) return;
        speedLabel.setText(flashesPerSecond == 1
                ? "1 destello por segundo"
                : flashesPerSecond + " destellos por segundo");
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
        if (mode == MODE_STROBE) confirmStrobe();
        else startLight(false);
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
