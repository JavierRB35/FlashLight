package com.javierrb35.flashlight;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Typeface;
import android.content.res.ColorStateList;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Four-second branded opening screen. It never requests permissions, operates the
 * camera or flash, or requires a user click. MainActivity owns all normal features.
 */
public final class SplashActivity extends Activity {
    private static final String STATE_STARTED_AT = "splashStartedAt";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable launch = this::launchApplication;
    private long startedAt;
    private boolean launched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startedAt = savedInstanceState == null
                ? SystemClock.elapsedRealtime()
                : savedInstanceState.getLong(STATE_STARTED_AT, SystemClock.elapsedRealtime());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(BrandPalette.NAVY);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(createSplash());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!launched) {
            handler.removeCallbacks(launch);
            handler.postDelayed(launch,
                    SplashTiming.remainingMillis(startedAt, SystemClock.elapsedRealtime()));
        }
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(launch);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(STATE_STARTED_AT, startedAt);
        super.onSaveInstanceState(outState);
    }

    private void launchApplication() {
        if (launched || isFinishing() || isDestroyed()) return;
        launched = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private View createSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BrandPalette.NAVY);
        root.addView(new Waves(this), new FrameLayout.LayoutParams(-1, -1));

        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int logoDp = (int) Math.min(landscape ? 190f : 292f,
                Math.min(widthDp * (landscape ? .34f : .74f),
                        heightDp * (landscape ? .38f : .38f)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(16));

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.flashlight_mascot);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascot.setContentDescription("Flashlight: mascota de linterna sonriente");
        content.addView(mascot, new LinearLayout.LayoutParams(dp(logoDp), dp(logoDp)));

        TextView title = label("Flashlight", landscape ? 38 : 43, true, Color.WHITE);
        title.setLetterSpacing(.012f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(landscape ? 0 : 4);
        content.addView(title, titleParams);

        TextView author = label("Carlos Javier Ruiz Bermúdez",
                landscape ? 17 : 18, true, Color.WHITE);
        author.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(-1, -2);
        authorParams.topMargin = dp(landscape ? 6 : 18);
        content.addView(author, authorParams);

        TextView email = label("cjruizbermudez@gmail.com", 14, false, Color.WHITE);
        email.setGravity(Gravity.CENTER);
        email.setMaxLines(1);
        email.setTextScaleX(.94f);
        content.addView(email, new LinearLayout.LayoutParams(-1, -2));

        String month = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"))
                .format(new Date());
        TextView date = label(month, 14, false, Color.WHITE);
        date.setGravity(Gravity.CENTER);
        content.addView(date, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.HORIZONTAL);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-2, -2);
        loadingParams.topMargin = dp(landscape ? 11 : 28);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(BrandPalette.YELLOW));
        spinner.setContentDescription("Iniciando aplicación");
        loading.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView loadingText = label("Iniciando…", 15, false, Color.rgb(229, 234, 245));
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(-2, -2);
        loadingTextParams.leftMargin = dp(10);
        loading.addView(loadingText, loadingTextParams);
        content.addView(loading, loadingParams);

        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        root.addView(content, contentParams);
        return root;
    }

    private TextView label(String content, int sizeSp, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setText(content);
        text.setTextColor(color);
        text.setTextSize(sizeSp);
        text.setTypeface(Typeface.create("sans-serif-rounded",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setIncludeFontPadding(true);
        return text;
    }

    private int dp(float size) {
        return (int) (getResources().getDisplayMetrics().density * size + .5f);
    }

    /** Decorative background, scaled to portrait and landscape without stretching the mascot. */
    private static final class Waves extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Waves(Activity context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            paint.setShader(new LinearGradient(0, 0, w, h,
                    Color.rgb(12, 28, 62), Color.rgb(27, 40, 79),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            // Top-left sunrise, matching the yellow corner of the approved design.
            paint.setColor(BrandPalette.YELLOW);
            path.reset();
            path.moveTo(0, 0);
            path.lineTo(.32f * w, 0);
            path.cubicTo(.24f * w, .12f * h, .10f * w, .13f * h, 0, .20f * h);
            path.close();
            canvas.drawPath(path, paint);

            // Cool teal on the left, kept clear of the central text.
            paint.setColor(BrandPalette.TURQUOISE);
            path.reset();
            path.moveTo(0, .22f * h);
            path.cubicTo(.27f * w, .14f * h, .22f * w, .32f * h, 0, .46f * h);
            path.close();
            canvas.drawPath(path, paint);

            // Subtle navy overlay at the lower-left.
            paint.setColor(Color.rgb(20, 49, 101));
            path.reset();
            path.moveTo(0, h);
            path.lineTo(0, .81f * h);
            path.cubicTo(.19f * w, .70f * h, .33f * w, .77f * h, .49f * w, h);
            path.close();
            canvas.drawPath(path, paint);

            // Coral and aqua waves from the right edge.
            paint.setColor(BrandPalette.CORAL);
            path.reset();
            path.moveTo(w, .39f * h);
            path.cubicTo(.82f * w, .47f * h, .88f * w, .79f * h, .62f * w, h);
            path.lineTo(w, h);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(BrandPalette.TURQUOISE);
            path.reset();
            path.moveTo(w, .78f * h);
            path.cubicTo(.84f * w, .80f * h, .73f * w, .92f * h, .58f * w, h);
            path.lineTo(w, h);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
