package com.wayfare.ride.ui.rider

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.theme.LocalWayfareColors
import kotlin.math.roundToInt

@Composable
fun rememberObservedDriverBaselineMeters(trip: RideTrip): Double? {
    var peakDistanceMeters by remember(trip.id) { mutableStateOf<Double?>(null) }
    val pickup = trip.pickupCoordinates
    val driverLoc = trip.liveDriverLocation ?: trip.driverProfile?.lastLocation
    val currentDistance = remember(driverLoc, pickup) {
        if (driverLoc != null && pickup != null) {
            LaunchPolicy.distanceMeters(driverLoc, pickup)
        } else {
            null
        }
    }
    SideEffect {
        if (currentDistance != null) {
            val prev = peakDistanceMeters
            if (prev == null || currentDistance > prev) {
                peakDistanceMeters = currentDistance
            }
        }
    }
    return peakDistanceMeters ?: currentDistance
}

@Composable
fun DriverApproachProgressBar(
    trip: RideTrip,
    modifier: Modifier = Modifier
) {
    if (trip.status != RideStatus.ACCEPTED && trip.status != RideStatus.ARRIVED) return

    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val currentDriverLocation = trip.liveDriverLocation ?: trip.driverProfile?.lastLocation
    val baselineMeters = rememberObservedDriverBaselineMeters(trip)
    val snapshot = remember(
        trip.status,
        currentDriverLocation,
        trip.pickupCoordinates,
        baselineMeters
    ) {
        LaunchPolicy.calculateDriverApproachProgress(
            status = trip.status,
            currentDriverLocation = currentDriverLocation,
            pickupLocation = trip.pickupCoordinates,
            baselineDistanceMeters = baselineMeters
        )
    }

    val animatedProgress by animateFloatAsState(
        targetValue = snapshot.progress,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "driverApproachProgress"
    )

    val isArrived = trip.status == RideStatus.ARRIVED
    val isNearlyHere = isArrived || snapshot.isArrivedOrWithinRadius

    val infiniteTransition = rememberInfiniteTransition(label = "driverApproachMotion")
    val shimmerFraction by infiniteTransition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "approachShimmer"
    )
    val beaconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pickupBeaconScale"
    )

    val accentColor by animateColorAsState(
        targetValue = if (isNearlyHere) wayfareColors.positive else colorScheme.primary,
        animationSpec = spring(),
        label = "approachAccent"
    )
    val secondaryAccent by animateColorAsState(
        targetValue = if (isNearlyHere) wayfareColors.positive else wayfareColors.routeHighlight,
        animationSpec = spring(),
        label = "approachSecondaryAccent"
    )
    val cardBackground by animateColorAsState(
        targetValue = if (isNearlyHere) {
            wayfareColors.positive.copy(alpha = 0.10f)
        } else {
            colorScheme.primaryContainer.copy(alpha = 0.35f)
        },
        label = "approachBg"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (isNearlyHere) {
            wayfareColors.positive.copy(alpha = 0.42f)
        } else {
            colorScheme.primary.copy(alpha = 0.30f)
        },
        label = "approachBorder"
    )

    val driverFirstName = trip.driverProfile?.name?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "Driver"
    val headline = when {
        isArrived -> "$driverFirstName has arrived"
        snapshot.isArrivedOrWithinRadius -> "$driverFirstName is right around the corner"
        snapshot.remainingMeters != null -> "$driverFirstName is on the way"
        else -> "$driverFirstName assigned · awaiting live GPS"
    }

    val badgeText = when {
        isArrived -> "At pickup · 100%"
        snapshot.remainingMeters != null && snapshot.etaMinutes != null ->
            "~${snapshot.etaMinutes} min · ${LaunchPolicy.formatApproachDistance(snapshot.remainingMeters)}"
        else -> "En route"
    }

    val accessibilityLabel = "$headline, $badgeText"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBackground,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = animatedProgress,
                    range = 0f..1f
                )
            }
            .testTag("driver_approach_progress_bar")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isArrived) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = wayfareColors.positive,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .scale(beaconScale)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                MonoBadge(
                    text = badgeText,
                    containerColor = if (isNearlyHere) {
                        wayfareColors.positive.copy(alpha = 0.16f)
                    } else {
                        colorScheme.surface
                    },
                    contentColor = if (isNearlyHere) wayfareColors.positive else colorScheme.primary,
                    modifier = Modifier.testTag("driver_approach_eta_badge")
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                val trackInsetDp = 15.dp
                val carBadgeSizeDp = 28.dp
                val pinBadgeSizeDp = 24.dp
                val trackColor = colorScheme.surfaceVariant
                val shimmerHighlight = Color.White.copy(alpha = 0.35f)
                val endMarkerColor = if (isNearlyHere) wayfareColors.positive else colorScheme.outlineVariant

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val horizontalInset = trackInsetDp.toPx()
                    val usableWidth = (size.width - horizontalInset * 2f).coerceAtLeast(1f)
                    val trackHeight = 7.dp.toPx()
                    val centerY = size.height / 2f
                    val topY = centerY - trackHeight / 2f
                    val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                    // Base track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(horizontalInset, topY),
                        size = Size(usableWidth, trackHeight),
                        cornerRadius = radius
                    )

                    // Active gradient fill
                    val filledWidth = (usableWidth * animatedProgress).coerceIn(trackHeight, usableWidth)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(accentColor, secondaryAccent),
                            startX = horizontalInset,
                            endX = horizontalInset + filledWidth
                        ),
                        topLeft = Offset(horizontalInset, topY),
                        size = Size(filledWidth, trackHeight),
                        cornerRadius = radius
                    )

                    // Subtle traveling light pulse along the filled segment while en route
                    if (!isArrived && filledWidth > trackHeight * 2f) {
                        val pulseX = horizontalInset + filledWidth * shimmerFraction
                        val pulseWidth = (filledWidth * 0.28f).coerceAtLeast(16.dp.toPx())
                        val clampedLeft = (pulseX - pulseWidth / 2f).coerceIn(
                            horizontalInset,
                            horizontalInset + filledWidth - 4.dp.toPx()
                        )
                        val clampedRight = (pulseX + pulseWidth / 2f).coerceIn(
                            clampedLeft,
                            horizontalInset + filledWidth
                        )
                        if (clampedRight > clampedLeft) {
                            drawRoundRect(
                                color = shimmerHighlight,
                                topLeft = Offset(clampedLeft, topY),
                                size = Size(clampedRight - clampedLeft, trackHeight),
                                cornerRadius = radius
                            )
                        }
                    }

                    // Milestone ticks at start and midpoint
                    listOf(0f, 0.5f).forEach { tick ->
                        val tickX = horizontalInset + usableWidth * tick
                        val reached = animatedProgress >= tick
                        drawCircle(
                            color = if (reached) accentColor else endMarkerColor,
                            radius = 3.dp.toPx(),
                            center = Offset(tickX, centerY)
                        )
                    }
                }

                // Destination pickup pin anchored on the right end
                Surface(
                    shape = CircleShape,
                    color = if (isNearlyHere) wayfareColors.positive else colorScheme.surface,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isNearlyHere) wayfareColors.positive else accentColor
                    ),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(pinBadgeSizeDp)
                        .scale(if (snapshot.isArrivedOrWithinRadius && !isArrived) beaconScale else 1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Pickup point",
                            tint = if (isNearlyHere) colorScheme.onPrimary else accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Moving driver vehicle marker anchored to animatedProgress
                val maxTravelPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (maxWidth - carBadgeSizeDp).toPx().coerceAtLeast(0f)
                }
                val markerOffsetX = (maxTravelPx * animatedProgress).roundToInt()

                Surface(
                    shape = CircleShape,
                    color = accentColor,
                    border = BorderStroke(2.dp, colorScheme.surface),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(x = markerOffsetX, y = 0) }
                        .size(carBadgeSizeDp)
                        .testTag("driver_approach_vehicle_marker")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = "Driver moving to pickup",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Dispatched",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(animatedProgress * 100).roundToInt()}% closer",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Text(
                    text = "Pickup",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isNearlyHere) FontWeight.Bold else FontWeight.Medium,
                    color = if (isNearlyHere) wayfareColors.positive else colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
