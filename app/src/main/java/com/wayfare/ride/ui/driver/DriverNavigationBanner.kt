package com.wayfare.ride.ui.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NavigationStep
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private fun iconForManeuver(step: NavigationStep?): ImageVector {
    if (step == null) return Icons.Filled.Navigation
    val type = step.maneuverType.lowercase(Locale.ROOT)
    val mod = step.maneuverModifier?.lowercase(Locale.ROOT)
    return when (type) {
        "arrive" -> Icons.Filled.Flag
        "roundabout", "rotary", "exit roundabout", "exit rotary" -> Icons.Filled.RoundaboutRight
        "fork" -> when (mod) {
            "left", "slight left", "sharp left" -> Icons.Filled.ForkLeft
            else -> Icons.Filled.ForkRight
        }
        "merge" -> Icons.Filled.Merge
        else -> when (mod) {
            "left" -> Icons.Filled.TurnLeft
            "slight left" -> Icons.Filled.TurnSlightLeft
            "sharp left" -> Icons.Filled.TurnSharpLeft
            "right" -> Icons.Filled.TurnRight
            "slight right" -> Icons.Filled.TurnSlightRight
            "sharp right" -> Icons.Filled.TurnSharpRight
            "uturn" -> Icons.Filled.UTurnLeft
            "straight" -> Icons.Filled.Straight
            else -> Icons.Filled.Navigation
        }
    }
}

@Composable
fun DriverTurnByTurnCard(
    route: RideRoute?,
    isLoadingNav: Boolean,
    driverCoords: RideCoordinates,
    targetCoords: RideCoordinates?,
    targetLabel: String,
    isPickupLeg: Boolean,
    followCamera: Boolean,
    onToggleFollowCamera: () -> Unit,
    onReroute: () -> Unit,
    onOpenExternalMaps: () -> Unit,
    onMinimizeDriveMode: (() -> Unit)? = null,
    voiceNavEnabled: Boolean = true,
    onToggleVoiceNav: (() -> Unit)? = null,
    headingUpEnabled: Boolean = false,
    onToggleHeadingUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var showStepList by remember(route?.encodedPolyline, isPickupLeg) { mutableStateOf(false) }

    val progress = remember(driverCoords, route, targetCoords) {
        route?.let { LaunchPolicy.computeActiveNavigationProgress(driverCoords, it, targetCoords) }
    }

    val legCaption = if (isPickupLeg) "to pickup" else "to drop-off"
    val targetShort = remember(targetLabel) {
        targetLabel.substringBefore(" ·").substringBefore(",").ifBlank { if (isPickupLeg) "pickup" else "drop-off" }
    }

    val remainingMeters = progress?.remainingDistanceMeters
        ?: targetCoords?.let { LaunchPolicy.distanceMeters(driverCoords, it).roundToInt() }
        ?: route?.distanceMeters
        ?: 0
    val remainingMinutes = progress?.let { ceil(it.remainingDurationSeconds / 60.0).toInt().coerceAtLeast(1) }
        ?: ((remainingMeters / 1000.0) * 2.1).roundToInt().coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.primary,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, wayfareColors.routeHighlight.copy(alpha = 0.28f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("driver_turn_by_turn_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isLoadingNav) {
                LinearProgressIndicator(
                    color = wayfareColors.routeHighlight,
                    trackColor = colorScheme.onPrimary.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("driver_nav_loading")
                )
            }

            // Primary Next-Turn Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = (route?.steps?.size ?: 0) > 1) { showStepList = !showStepList }
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.onPrimary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, wayfareColors.routeHighlight.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForManeuver(progress?.upcomingStep),
                            contentDescription = progress?.upcomingStep?.instruction ?: "Next turn",
                            tint = wayfareColors.routeHighlight,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (progress != null) {
                            Text(
                                text = LaunchPolicy.formatApproachDistance(progress.distanceToManeuverMeters),
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = wayfareColors.routeHighlight,
                                modifier = Modifier.testTag("driver_nav_next_distance")
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.onPrimary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "$remainingMinutes min · ${LaunchPolicy.formatApproachDistance(remainingMeters.toDouble())} $legCaption",
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onPrimary.copy(alpha = 0.94f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("driver_live_eta_text")
                            )
                        }
                    }

                    Text(
                        text = progress?.upcomingStep?.instruction ?: "Head toward $targetShort",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("driver_nav_primary_instruction")
                    )

                    val secondaryLine = progress?.nextAfterStep?.instruction
                        ?.let { "Then: $it" }
                        ?: if (route == null && !isLoadingNav) {
                            "Direct route to $targetShort · tap Reroute for street turns"
                        } else {
                            "Destination · $targetShort"
                        }
                    Text(
                        text = secondaryLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onPrimary.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("driver_nav_then_instruction")
                    )
                }

                if (onMinimizeDriveMode != null) {
                    TextButton(
                        onClick = onMinimizeDriveMode,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("driver_minimize_drive_mode")
                    ) {
                        Text(
                            text = "Minimize",
                            color = wayfareColors.routeHighlight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Secondary Action Bar (Turns Drawer Toggle, Follow GPS, Reroute, External Maps)
            Surface(
                color = colorScheme.onPrimary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    val stepsCount = route?.steps?.size ?: 0
                    TextButton(
                        onClick = { showStepList = !showStepList },
                        enabled = stepsCount > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("driver_nav_steps_toggle")
                    ) {
                        Icon(
                            imageVector = if (showStepList) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showStepList) "Hide steps" else "Show turn-by-turn steps",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (stepsCount > 0) "Turns ($stepsCount)" else "Direct",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (followCamera) wayfareColors.routeHighlight.copy(alpha = 0.24f) else colorScheme.onPrimary.copy(alpha = 0.06f),
                            border = if (followCamera) BorderStroke(1.dp, wayfareColors.routeHighlight.copy(alpha = 0.65f)) else null,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onToggleFollowCamera)
                                .testTag("driver_nav_follow_toggle")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = if (followCamera) "Camera following GPS" else "Follow GPS",
                                    tint = if (followCamera) wayfareColors.routeHighlight else colorScheme.onPrimary.copy(alpha = 0.78f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (followCamera) "Following" else "Follow",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (followCamera) wayfareColors.routeHighlight else colorScheme.onPrimary.copy(alpha = 0.88f)
                                )
                            }
                        }

                        if (onToggleVoiceNav != null) {
                            IconButton(
                                onClick = onToggleVoiceNav,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("driver_nav_voice_toggle")
                            ) {
                                Icon(
                                    imageVector = if (voiceNavEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = if (voiceNavEnabled) "Mute voice turn prompts" else "Enable voice turn prompts",
                                    tint = if (voiceNavEnabled) wayfareColors.routeHighlight else colorScheme.onPrimary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (onToggleHeadingUp != null) {
                            IconButton(
                                onClick = onToggleHeadingUp,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("driver_nav_heading_up_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Explore,
                                    contentDescription = if (headingUpEnabled) "Switch to north-up map" else "Switch to heading-up map",
                                    tint = if (headingUpEnabled) wayfareColors.routeHighlight else colorScheme.onPrimary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onReroute,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("driver_nav_reroute_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Recalculate street route",
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenExternalMaps,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("driver_nav_external_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in external maps app",
                                tint = colorScheme.onPrimary.copy(alpha = 0.9f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Turn-by-Turn Maneuver Step Drawer
            AnimatedVisibility(
                visible = showStepList && !route?.steps.isNullOrEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val allSteps = route?.steps.orEmpty()
                val startIdx = (progress?.upcomingStepIndex ?: 0).coerceIn(0, (allSteps.size - 1).coerceAtLeast(0))
                val upcomingSteps = remember(allSteps, startIdx) {
                    allSteps.subList(startIdx, allSteps.size)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surface)
                ) {
                    HorizontalDivider(color = colorScheme.outlineVariant)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .testTag("driver_nav_steps_list")
                    ) {
                        itemsIndexed(upcomingSteps, key = { _, step -> "${step.index}_${step.maneuverType}" }) { idx, step ->
                            val isNext = idx == 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isNext) colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        else colorScheme.surface
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isNext) colorScheme.primary else colorScheme.surfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = iconForManeuver(step),
                                            contentDescription = null,
                                            tint = if (isNext) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.instruction,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (step.distanceMeters > 0) {
                                        Text(
                                            text = "Continue ${LaunchPolicy.formatApproachDistance(step.distanceMeters.toDouble())}",
                                            fontFamily = WayfareMonoFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (idx < upcomingSteps.lastIndex) {
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            }
        }
    }
}
