package com.javierrb35.flashlight

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.javierrb35.flashlight.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Branded opening screen with the developer information of the original
 * design: mascot, name, author, e-mail and the current month in Spanish.
 * It shows four seconds of foreground-visible time — the counter pauses while
 * the activity is not resumed and survives configuration changes — and then
 * opens [MainActivity] automatically. It never requests permissions, touches
 * the camera or the flash, and requires no interaction.
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DarkColors) {
                SplashContent(onLaunch = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                })
            }
        }
    }
}

private const val STATE_REMAINING = "splashRemainingMillis"

@Composable
private fun SplashContent(onLaunch: () -> Unit) {
    var remainingMillis by rememberSaveable { mutableLongStateOf(SplashTiming.VISIBLE_MILLIS) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastResumeElapsed by remember(lifecycleOwner) { mutableLongStateOf(0L) }
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                }

                Lifecycle.Event.ON_PAUSE -> {
                    if (lastResumeElapsed != 0L) {
                        remainingMillis = SplashTiming.remainingMillis(
                            remainingMillis,
                            SystemClock.elapsedRealtime() - lastResumeElapsed,
                        )
                        lastResumeElapsed = 0L
                    }
                    resumed = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Let one frame render before starting the timer, then measure every tick
    // from the start of this visible interval.
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val intervalStartedAt = SystemClock.elapsedRealtime()
        val intervalRemaining = remainingMillis
        lastResumeElapsed = intervalStartedAt
        while (remainingMillis > 0) {
            withFrameNanos { }
            val visibleElapsed = SystemClock.elapsedRealtime() - intervalStartedAt
            remainingMillis = SplashTiming.remainingMillis(intervalRemaining, visibleElapsed)
        }
        if (resumed) onLaunch()
    }

    SplashBody(remainingMillis)
}

@Composable
private fun SplashBody(remainingMillis: Long) {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.toFloat()
    val heightDp = configuration.screenHeightDp
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val logoDp = minOf(
        if (landscape) 190f else 292f,
        minOf(
            widthDp * (if (landscape) 0.34f else 0.74f),
            heightDp * 0.38f,
        ),
    ).toInt()

    val monthYear = remember {
        SimpleDateFormat("MMMM yyyy", Locale("es", "ES")).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
        SplashWaves(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.flashlight_mascot),
                contentDescription = "Flashlight: mascota de linterna sonriente",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(logoDp.dp),
            )
            Text(
                text = "Flashlight",
                color = Color.White,
                fontSize = if (landscape) 38.sp else 43.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (landscape) 0.dp else 4.dp),
            )
            Text(
                text = "Carlos Javier Ruiz Bermúdez",
                color = Color.White,
                fontSize = if (landscape) 17.sp else 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (landscape) 6.dp else 18.dp),
            )
            Text(
                text = "cjruizbermudez@gmail.com",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = monthYear,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = if (landscape) 11.dp else 28.dp),
            ) {
                CircularProgressIndicator(
                    color = Yellow,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "Iniciando aplicación" },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Iniciando…",
                    color = Color(0xFFE5EAF5),
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** Decorative background waves, ported 1:1 from the original splash design. */
@Composable
private fun SplashWaves(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .semantics { contentDescription = "" },
    ) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0C1C3E), Color(0xFF1B284F)),
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )
        fun wave(color: Color, block: Path.() -> Unit) {
            val path = Path().apply(block)
            drawPath(path, color, style = Fill)
        }

        // Top-left sunrise, matching the yellow corner of the approved design.
        wave(Yellow) {
            moveTo(0f, 0f)
            lineTo(0.32f * w, 0f)
            cubicTo(0.24f * w, 0.12f * h, 0.10f * w, 0.13f * h, 0f, 0.20f * h)
            close()
        }
        // Cool teal on the left, kept clear of the central text.
        wave(Turquoise) {
            moveTo(0f, 0.22f * h)
            cubicTo(0.27f * w, 0.14f * h, 0.22f * w, 0.32f * h, 0f, 0.46f * h)
            close()
        }
        // Subtle navy overlay at the lower-left.
        wave(Color(0xFF143165)) {
            moveTo(0f, h)
            lineTo(0f, 0.81f * h)
            cubicTo(0.19f * w, 0.70f * h, 0.33f * w, 0.77f * h, 0.49f * w, h)
            close()
        }
        // Coral and aqua waves from the right edge.
        wave(Coral) {
            moveTo(w, 0.39f * h)
            cubicTo(0.82f * w, 0.47f * h, 0.88f * w, 0.79f * h, 0.62f * w, h)
            lineTo(w, h)
            close()
        }
        wave(Turquoise) {
            moveTo(w, 0.78f * h)
            cubicTo(0.84f * w, 0.80f * h, 0.73f * w, 0.92f * h, 0.58f * w, h)
            lineTo(w, h)
            close()
        }
    }
}
