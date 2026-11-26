package com.wayfare.ride.ui.payments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wayfare.ride.audio.SoundAlertPlayer
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentVerificationResult
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.ui.components.AnimatedZarAmountText
import com.wayfare.ride.ui.components.CelebrationConfettiOverlay
import com.wayfare.ride.ui.components.CelebrationHeroEmblem
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors

@Composable
fun PaymentReturnScreen(
    result: PaymentVerificationResult,
    onDone: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    BackHandler { onDone() }

    val isCelebration = result.state == PaymentVerificationState.AUTHORIZED ||
        result.state == PaymentVerificationState.PAID
    val celebrationKey = "${result.reference}:${result.state}"

    LaunchedEffect(celebrationKey, isCelebration) {
        if (isCelebration) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            SoundAlertPlayer.playSuccessCelebrationChime(context)
        }
    }

    val title = when (result.state) {
        PaymentVerificationState.CHECKING -> "Verifying Paystack payment"
        PaymentVerificationState.AUTHORIZED -> "Fare reserved"
        PaymentVerificationState.PAID -> "Payment confirmed"
        PaymentVerificationState.PENDING -> "Confirmation pending"
        PaymentVerificationState.FAILED -> "Authorization not confirmed"
    }

    val badgeLabel = if (result.amountMinor > 0L) {
        "${result.reference} · ${LaunchPolicy.formatZarMinor(result.amountMinor)}"
    } else {
        result.reference
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background.copy(alpha = 0.96f))
            .padding(20.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = BorderStroke(
                1.dp,
                if (isCelebration) wayfareColors.positive.copy(alpha = 0.45f) else colorScheme.outline
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isCelebration) 10.dp else 4.dp),
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(22.dp)
            ) {
                when (result.state) {
                    PaymentVerificationState.CHECKING -> {
                        CircularProgressIndicator(modifier = Modifier.size(38.dp), color = colorScheme.primary)
                    }
                    PaymentVerificationState.AUTHORIZED, PaymentVerificationState.PAID -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CelebrationHeroEmblem(
                                icon = Icons.Filled.CheckCircle,
                                accentColor = wayfareColors.positive,
                                triggerKey = celebrationKey,
                                contentDescription = title,
                                badgeSize = 52.dp,
                                iconSize = 28.dp
                            )
                            StatusChip(
                                label = if (result.state == PaymentVerificationState.PAID) "Settled" else "Authorized",
                                containerColor = wayfareColors.positive.copy(alpha = 0.14f),
                                contentColor = wayfareColors.positive
                            )
                        }
                    }
                    PaymentVerificationState.PENDING -> {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = wayfareColors.warning, modifier = Modifier.size(42.dp))
                    }
                    PaymentVerificationState.FAILED -> {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(42.dp))
                    }
                }

                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(result.message, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)

                if (isCelebration && result.amountMinor > 0L) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = wayfareColors.positive.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = if (result.state == PaymentVerificationState.PAID) "Amount settled" else "Fare reserved",
                                style = MaterialTheme.typography.labelMedium,
                                color = wayfareColors.positive,
                                fontWeight = FontWeight.SemiBold
                            )
                            AnimatedZarAmountText(
                                targetAmount = result.amountMinor / 100.0,
                                style = MaterialTheme.typography.headlineMedium,
                                color = wayfareColors.positive,
                                triggerKey = celebrationKey
                            )
                        }
                    }
                }

                MonoBadge(text = badgeLabel)

                if ((result.state == PaymentVerificationState.PENDING || result.state == PaymentVerificationState.FAILED) &&
                    onRetry != null
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("payment_return_retry_btn")
                    ) {
                        Text("Retry verification", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onDone,
                    enabled = result.state != PaymentVerificationState.CHECKING,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .tactilePressScale()
                        .testTag("payment_return_done_btn")
                ) {
                    Text("Return to Vhaya", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isCelebration) {
            CelebrationConfettiOverlay(
                triggerKey = celebrationKey,
                focalYFraction = 0.30f
            )
        }
    }
}

