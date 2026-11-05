package com.javierrb35.flashlight

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The master light switch. Off = quiet surface; lit = brand yellow with a soft
 * halo; flashing modes breathe while active. Pressing scales it with a haptic.
 */
@Composable
fun PowerDial(
    lit: Boolean,
    flashing: Boolean,
    onToggle: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    val litAmount by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(180),
        label = "litAmount",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAmount by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAmount",
    )

    val dialColor by animateColorAsState(
        targetValue = lerp(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.primaryContainer,
            litAmount,
        ),
        animationSpec = tween(180),
        label = "dialColor",
    )
    val ringColor by animateColorAsState(
        targetValue = lerp(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.primary,
            litAmount,
        ),
        animationSpec = tween(180),
        label = "ringColor",
    )
    val iconColor by animateColorAsState(
        targetValue = lerp(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.onPrimaryContainer,
            litAmount,
        ),
        animationSpec = tween(180),
        label = "iconColor",
    )

    val glowAlpha = when {
        flashing -> androidx.compose.ui.util.lerp(0.14f, 0.40f, pulseAmount)
        lit -> 0.30f
        else -> 0f
    }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(216.dp)
            .scale(pressScale),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(216.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius * 1.28f,
                ),
                radius = radius * 1.28f,
            )
        }
        Canvas(modifier = Modifier.size(216.dp)) {
            drawCircle(
                color = ringColor.copy(alpha = if (lit) 0.9f else 0.5f),
                radius = size.minDimension / 2f - 2.dp.toPx(),
                style = Stroke(width = 2.5f.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = iconColor, radius = 80.dp),
                    role = Role.Switch,
                    onClickLabel = contentDescription,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(160.dp)) { drawCircle(color = dialColor) }
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
