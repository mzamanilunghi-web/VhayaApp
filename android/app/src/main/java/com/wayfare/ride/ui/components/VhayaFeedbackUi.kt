package com.wayfare.ride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.VhayaFeedbackMessage
import com.wayfare.ride.domain.VhayaFeedbackTone
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun VhayaFloatingToastHost(
    currentMessage: VhayaFeedbackMessage?,
    onDismiss: () -> Unit,
    onAction: (VhayaFeedbackMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayedMessage by remember { mutableStateOf(currentMessage) }
    if (currentMessage != null) {
        displayedMessage = currentMessage
    }

    val visible = currentMessage != null

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it - 32 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(200)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it - 32 },
                animationSpec = tween(220)
            ) + fadeOut(animationSpec = tween(180))
        ) {
            displayedMessage?.let { msg ->
                VhayaFeedbackBannerCard(
                    feedback = msg,
                    isActive = visible,
                    onDismiss = onDismiss,
                    onAction = {
                        onAction(msg)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun VhayaFeedbackBannerCard(
    feedback: VhayaFeedbackMessage,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val scope = rememberCoroutineScope()

    val accentColor = when (feedback.tone) {
        VhayaFeedbackTone.SUCCESS -> wayfareColors.positive
        VhayaFeedbackTone.ERROR -> colorScheme.error
        VhayaFeedbackTone.WARNING -> wayfareColors.warning
        VhayaFeedbackTone.SUPPORT -> colorScheme.primary
        VhayaFeedbackTone.INFO -> colorScheme.primary
    }
    val toneIcon = when (feedback.tone) {
        VhayaFeedbackTone.SUCCESS -> Icons.Filled.CheckCircle
        VhayaFeedbackTone.ERROR -> Icons.Filled.ErrorOutline
        VhayaFeedbackTone.WARNING -> Icons.Filled.Schedule
        VhayaFeedbackTone.SUPPORT -> Icons.Filled.SupportAgent
        VhayaFeedbackTone.INFO -> Icons.Filled.Info
    }

    val progress = remember(feedback.id) { Animatable(1f) }
    val dragOffsetX = remember(feedback.id) { Animatable(0f) }
    val dragOffsetY = remember(feedback.id) { Animatable(0f) }

    LaunchedEffect(feedback.id, isActive) {
        if (!isActive) return@LaunchedEffect
        progress.snapTo(1f)
        launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = feedback.durationMs.toInt(),
                    easing = LinearEasing
                )
            )
        }
        delay(feedback.durationMs)
        onDismiss()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.36f)),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .offset {
                IntOffset(
                    x = dragOffsetX.value.roundToInt(),
                    y = dragOffsetY.value.coerceAtMost(24f).roundToInt()
                )
            }
            .pointerInput(feedback.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragOffsetY.value < -56f || kotlin.math.abs(dragOffsetX.value) > 140f) {
                                onDismiss()
                            } else {
                                launch { dragOffsetX.animateTo(0f, spring()) }
                                launch { dragOffsetY.animateTo(0f, spring()) }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { dragOffsetX.animateTo(0f, spring()) }
                            launch { dragOffsetY.animateTo(0f, spring()) }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x)
                            dragOffsetY.snapTo((dragOffsetY.value + dragAmount.y).coerceAtMost(24f))
                        }
                    }
                )
            }
            .testTag("vhaya_feedback_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.11f),
                            colorScheme.surface.copy(alpha = 0.0f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = toneIcon,
                                contentDescription = feedback.title,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = feedback.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("vhaya_feedback_title")
                        )
                        Text(
                            text = feedback.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("vhaya_feedback_message")
                        )
                    }

                    if (!feedback.actionLabel.isNullOrBlank()) {
                        TextButton(
                            onClick = onAction,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = accentColor.copy(alpha = 0.14f),
                                contentColor = accentColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("vhaya_feedback_action")
                        ) {
                            Text(
                                text = feedback.actionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vhaya_feedback_dismiss")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss message",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(accentColor.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
}

private enum class ConfettiShape {
    RIBBON,
    CIRCLE,
    SPARKLE
}

private data class ConfettiParticleSpec(
    val angleRad: Float,
    val speedFactor: Float,
    val gravityFactor: Float,
    val driftPhase: Float,
    val driftAmp: Float,
    val sizeDp: Float,
    val initialRotationDeg: Float,
    val spinDegPerSec: Float,
    val colorIndex: Int,
    val shape: ConfettiShape
)

@Composable
fun CelebrationConfettiOverlay(
    triggerKey: Any?,
    modifier: Modifier = Modifier,
    focalYFraction: Float = 0.28f,
    durationMs: Int = 2100
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val palette = listOf(
        wayfareColors.positive,
        colorScheme.primary,
        wayfareColors.warning,
        wayfareColors.routeHighlight,
        wayfareColors.walletAccent,
        wayfareColors.crimsonAccent
    )

    val particles = remember(triggerKey) {
        val rng = Random(triggerKey.hashCode())
        List(48) { idx ->
            val fanAngle = (-Math.PI * 0.92) + (rng.nextFloat() * Math.PI * 0.84)
            ConfettiParticleSpec(
                angleRad = fanAngle.toFloat(),
                speedFactor = 0.32f + rng.nextFloat() * 0.58f,
                gravityFactor = 0.42f + rng.nextFloat() * 0.35f,
                driftPhase = rng.nextFloat() * (2f * PI.toFloat()),
                driftAmp = 8f + rng.nextFloat() * 18f,
                sizeDp = 5f + rng.nextFloat() * 5.5f,
                initialRotationDeg = rng.nextFloat() * 360f,
                spinDegPerSec = (rng.nextFloat() - 0.5f) * 520f,
                colorIndex = idx % 6,
                shape = when (idx % 5) {
                    0 -> ConfettiShape.SPARKLE
                    1 -> ConfettiShape.CIRCLE
                    else -> ConfettiShape.RIBBON
                }
            )
        }
    }

    val progress = remember(triggerKey) { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
        )
    }

    val t = progress.value
    if (t >= 0.999f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val originX = size.width * 0.5f
        val originY = size.height * focalYFraction
        val minDim = minOf(size.width, size.height)
        val alpha = when {
            t < 0.1f -> (t / 0.1f).coerceIn(0f, 1f)
            t > 0.68f -> (1f - (t - 0.68f) / 0.32f).coerceIn(0f, 1f)
            else -> 1f
        }

        particles.forEach { p ->
            val dragTime = 1f - (1f - t) * (1f - t) * (1f - t)
            val velocityScale = minDim * 0.62f * p.speedFactor
            val vx = cos(p.angleRad) * velocityScale
            val vy = sin(p.angleRad) * velocityScale
            val gravity = minDim * 0.82f * p.gravityFactor * t * t
            val flutter = sin(t * 10f + p.driftPhase) * p.driftAmp.dp.toPx() * t

            val cx = originX + vx * dragTime + flutter
            val cy = originY + vy * dragTime + gravity
            val pxSize = p.sizeDp.dp.toPx()
            val color = palette[p.colorIndex].copy(alpha = alpha * 0.92f)
            val rotation = p.initialRotationDeg + p.spinDegPerSec * (t * (durationMs / 1000f))

            rotate(degrees = rotation, pivot = Offset(cx, cy)) {
                when (p.shape) {
                    ConfettiShape.CIRCLE -> {
                        drawCircle(
                            color = color,
                            radius = pxSize * 0.42f,
                            center = Offset(cx, cy)
                        )
                    }
                    ConfettiShape.RIBBON -> {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(cx - pxSize * 0.65f, cy - pxSize * 0.28f),
                            size = Size(pxSize * 1.3f, pxSize * 0.56f),
                            cornerRadius = CornerRadius(pxSize * 0.2f, pxSize * 0.2f)
                        )
                    }
                    ConfettiShape.SPARKLE -> {
                        val r = pxSize * 0.72f
                        val inner = r * 0.28f
                        val path = Path().apply {
                            moveTo(cx, cy - r)
                            lineTo(cx + inner, cy - inner)
                            lineTo(cx + r, cy)
                            lineTo(cx + inner, cy + inner)
                            lineTo(cx, cy + r)
                            lineTo(cx - inner, cy + inner)
                            lineTo(cx - r, cy)
                            lineTo(cx - inner, cy - inner)
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                }
            }
        }
    }
}

@Composable
fun CelebrationHeroEmblem(
    accentColor: Color,
    triggerKey: Any?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.CheckCircle,
    contentDescription: String? = null,
    badgeSize: Dp = 56.dp,
    iconSize: Dp = 30.dp
) {
    val badgeScale = remember(triggerKey) { Animatable(0.45f) }
    val ring1Progress = remember(triggerKey) { Animatable(0f) }
    val ring2Progress = remember(triggerKey) { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        badgeScale.snapTo(0.45f)
        ring1Progress.snapTo(0f)
        ring2Progress.snapTo(0f)

        launch {
            badgeScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            ring1Progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(160)
            ring2Progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(badgeSize * 1.45f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = badgeSize.toPx() * 0.48f
            val r1 = ring1Progress.value
            if (r1 in 0.01f..0.99f) {
                drawCircle(
                    color = accentColor.copy(alpha = (1f - r1) * 0.42f),
                    radius = baseRadius * (1f + r1 * 0.72f),
                    style = Stroke(width = 2.5.dp.toPx() * (1f - r1 * 0.5f))
                )
            }
            val r2 = ring2Progress.value
            if (r2 in 0.01f..0.99f) {
                drawCircle(
                    color = accentColor.copy(alpha = (1f - r2) * 0.30f),
                    radius = baseRadius * (1f + r2 * 0.92f),
                    style = Stroke(width = 2.dp.toPx() * (1f - r2 * 0.5f))
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.16f),
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .size(badgeSize)
                .scale(badgeScale.value)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = accentColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
fun AnimatedZarAmountText(
    targetAmount: Double,
    style: TextStyle,
    color: Color,
    triggerKey: Any?,
    modifier: Modifier = Modifier,
    prefix: String = ""
) {
    val animatedValue = remember(triggerKey, targetAmount) { Animatable(0f) }
    val settleScale = remember(triggerKey, targetAmount) { Animatable(1f) }

    LaunchedEffect(triggerKey, targetAmount) {
        if (targetAmount <= 0.0) {
            animatedValue.snapTo(0f)
            return@LaunchedEffect
        }
        animatedValue.snapTo(0f)
        animatedValue.animateTo(
            targetValue = targetAmount.toFloat(),
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
        )
        settleScale.animateTo(
            targetValue = 1.07f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
        settleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
        )
    }

    val currentAmount = if (animatedValue.value >= targetAmount.toFloat() * 0.995f) {
        targetAmount
    } else {
        animatedValue.value.toDouble()
    }

    Text(
        text = prefix + LaunchPolicy.formatZar(currentAmount),
        fontFamily = WayfareMonoFamily,
        style = style,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.scale(settleScale.value)
    )
}
