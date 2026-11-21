package com.wayfare.ride.ui.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.R
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.LaunchPolicy.PasswordStrength
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.AppThemeMode
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@Composable
internal fun AuthThemeCyclePill(
    themeMode: AppThemeMode,
    onCycleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = LocalWayfareColors.current.isDark
    val icon = when (themeMode) {
        AppThemeMode.LIGHT -> Icons.Filled.LightMode
        AppThemeMode.DARK -> Icons.Filled.DarkMode
        AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    }
    val containerColor = if (isDark) {
        Color.Black.copy(alpha = 0.36f)
    } else {
        colorScheme.surface.copy(alpha = 0.88f)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.28f)
    } else {
        colorScheme.outlineVariant
    }
    val contentColor = if (isDark) Color.White else colorScheme.onSurface

    Surface(
        onClick = onCycleTheme,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("auth_theme_toggle_btn")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Switch appearance theme (${themeMode.label})",
                tint = if (isDark) Color.White else colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = themeMode.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PasswordPolicyChecklistCard(
    evaluation: LaunchPolicy.PasswordPolicyEvaluation,
    passwordEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val targetFraction = if (passwordEmpty) 0.04f else (evaluation.satisfiedCount / 5f).coerceIn(0.18f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 240),
        label = "pwStrengthFraction"
    )
    val targetMeterColor = when (evaluation.strength) {
        PasswordStrength.EMPTY -> colorScheme.outlineVariant
        PasswordStrength.WEAK -> colorScheme.error
        PasswordStrength.FAIR -> wayfareColors.warning
        PasswordStrength.GOOD -> colorScheme.primary
        PasswordStrength.STRONG -> wayfareColors.positive
    }
    val animatedMeterColor by animateColorAsState(
        targetValue = targetMeterColor,
        animationSpec = tween(durationMillis = 240),
        label = "pwStrengthColor"
    )

    val rules = listOf(
        "8+ chars" to evaluation.hasMinLength,
        "A-Z upper" to evaluation.hasUppercase,
        "a-z lower" to evaluation.hasLowercase,
        "0-9 number" to evaluation.hasDigit,
        "!@# symbol" to evaluation.hasSpecialChar
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(
            1.dp,
            if (evaluation.isValid) wayfareColors.positive.copy(alpha = 0.45f) else colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("auth_password_policy_checklist")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (evaluation.isValid) wayfareColors.positive else colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Password strength",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                }
                Text(
                    text = if (passwordEmpty) "Required" else "${evaluation.strength.label} · ${evaluation.satisfiedCount}/5",
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (passwordEmpty) colorScheme.onSurfaceVariant else animatedMeterColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colorScheme.outlineVariant)
                    .testTag("auth_password_strength_bar")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(animatedMeterColor)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rules.forEach { (label, met) ->
                    PasswordRuleChip(label = label, met = met)
                }
            }

            if (!evaluation.hasNoSurroundingSpaces && !passwordEmpty) {
                Text(
                    text = "Remove leading or trailing spaces.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PasswordRuleChip(
    label: String,
    met: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val chipScale by animateFloatAsState(
        targetValue = if (met) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ruleScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (met) wayfareColors.emeraldContainer else colorScheme.surface,
        animationSpec = tween(200),
        label = "ruleBg"
    )
    val fgColor by animateColorAsState(
        targetValue = if (met) wayfareColors.positive else colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "ruleFg"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(
            1.dp,
            if (met) wayfareColors.positive.copy(alpha = 0.40f) else colorScheme.outlineVariant
        ),
        modifier = Modifier.scale(chipScale)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (met) "$label requirement met" else "$label requirement pending",
                tint = fgColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (met) FontWeight.Bold else FontWeight.Medium,
                color = fgColor
            )
        }
    }
}

@Composable
internal fun EmailVerificationScreen(
    session: UserSession,
    busy: Boolean,
    resendCooldownSeconds: Int,
    notice: String?,
    error: String?,
    themeMode: AppThemeMode,
    onCycleTheme: () -> Unit,
    onCheckVerified: () -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
            ) {
                // Branded Verification Hero Strip
                val isDarkHero = wayfareColors.isDark
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surface,
                    border = if (isDarkHero) null else BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Crossfade(
                            targetState = isDarkHero,
                            animationSpec = tween(durationMillis = 260),
                            label = "verificationHeroArtwork"
                        ) { dark ->
                            Image(
                                painter = painterResource(
                                    id = if (dark) R.drawable.img_vhaya_auth_hero else R.drawable.img_vhaya_auth_hero_light
                                ),
                                contentDescription = "Vhaya mobility skyline",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isDarkHero) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                wayfareColors.walletCardEnd.copy(alpha = 0.68f),
                                                wayfareColors.walletCardStart.copy(alpha = 0.88f)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colorStops = arrayOf(
                                                0.0f to colorScheme.surface.copy(alpha = 0.84f),
                                                0.62f to colorScheme.surface.copy(alpha = 0.56f),
                                                1.0f to colorScheme.surface.copy(alpha = 0.24f)
                                            )
                                        )
                                    }
                                )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isDarkHero) {
                                        wayfareColors.positive.copy(alpha = 0.24f)
                                    } else {
                                        wayfareColors.emeraldContainer.copy(alpha = 0.94f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDarkHero) {
                                            wayfareColors.positive.copy(alpha = 0.65f)
                                        } else {
                                            wayfareColors.positive.copy(alpha = 0.42f)
                                        }
                                    ),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.MarkEmailRead,
                                            contentDescription = "Email verification pending",
                                            tint = if (isDarkHero) Color.White else wayfareColors.positive,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusChip(
                                        label = "Almost ready",
                                        containerColor = if (isDarkHero) {
                                            wayfareColors.positive.copy(alpha = 0.26f)
                                        } else {
                                            wayfareColors.emeraldContainer.copy(alpha = 0.96f)
                                        },
                                        contentColor = if (isDarkHero) Color.White else wayfareColors.positive
                                    )
                                    Text(
                                        text = "Verify your inbox",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkHero) Color.White else colorScheme.onSurface
                                    )
                                }
                            }
                            AuthThemeCyclePill(themeMode = themeMode, onCycleTheme = onCycleTheme)
                        }
                    }
                }

                // Main Verification Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "We sent a confirmation link to:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )

                        MonoBadge(
                            text = session.email,
                            containerColor = wayfareColors.brandHighlight,
                            contentColor = colorScheme.onPrimaryContainer
                        )

                        // Visual 3-Step Checklist
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                VerificationStepRow(
                                    number = "1",
                                    title = "Open the Vhaya confirmation email",
                                    subtitle = "Check Promotions or Spam if you don't see it within 60 seconds."
                                )
                                VerificationStepRow(
                                    number = "2",
                                    title = "Tap Verify Email in the message",
                                    subtitle = "Your Vhaya account activates right away."
                                )
                                VerificationStepRow(
                                    number = "3",
                                    title = "Return here and continue",
                                    subtitle = "We check automatically every few seconds once you verify, or tap below."
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.primary
                            )
                            Text(
                                text = "Checking verification status automatically…",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        notice?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = wayfareColors.emeraldContainer,
                                border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = wayfareColors.positive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        error?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.errorContainer,
                                border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onCheckVerified,
                            enabled = !busy,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .tactilePressScale()
                                .testTag("verify_email_check_btn")
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = colorScheme.onPrimary
                                )
                            } else {
                                Text("I've verified my email", fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onResend,
                                enabled = !busy && resendCooldownSeconds == 0,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("verify_email_resend_btn")
                            ) {
                                Icon(
                                    imageVector = if (resendCooldownSeconds > 0) Icons.Filled.Refresh else Icons.Filled.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (resendCooldownSeconds > 0) {
                                        "Resend (${resendCooldownSeconds}s)"
                                    } else {
                                        "Resend email"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("verify_email_sign_out_btn")
                            ) {
                                Text("Use another email", color = colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationStepRow(
    number: String,
    title: String,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = wayfareColors.brandHighlight,
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
