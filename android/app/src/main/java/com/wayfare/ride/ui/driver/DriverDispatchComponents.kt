package com.wayfare.ride.ui.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.DriverDispatchMetrics
import com.wayfare.ride.domain.DriverMapEngine
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val OFFER_CYCLE_SECONDS = 15

@Composable
fun DriverShiftSummaryStrip(
    todayEarningsZar: Double,
    completedTripsCount: Int,
    weeklyPlanActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Earnings",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = LaunchPolicy.formatZar(todayEarningsZar),
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$completedTripsCount ${if (completedTripsCount == 1) "ride" else "rides"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }

            StatusChip(
                label = if (weeklyPlanActive) "0% Fee Pass" else "Standard",
                containerColor = if (weeklyPlanActive) {
                    wayfareColors.positive.copy(alpha = 0.14f)
                } else {
                    colorScheme.surface
                },
                contentColor = if (weeklyPlanActive) {
                    wayfareColors.positive
                } else {
                    colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinRateFilterStrip(
    selectedRate: Double,
    filteredOutCount: Int,
    onSelectRate: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var expanded by remember { mutableStateOf(false) }
    val activeLabel = if (selectedRate <= 0.0) "Filter: All rides" else "Rate: R ${selectedRate.toInt()}+/km"

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().testTag("driver_min_rate_filter_strip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedRate > 0.0 || expanded,
                onClick = { expanded = !expanded },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = activeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Hide rate filter options" else "Show rate filter options",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = wayfareColors.brandHighlight,
                    selectedLabelColor = colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.height(48.dp)
            )
            if (filteredOutCount > 0) {
                TextButton(
                    onClick = { onSelectRate(0.0) },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("driver_reset_rate_filter_btn")
                ) {
                    Text(
                        text = "Show all ($filteredOutCount)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SmartRideGuards.DRIVER_MIN_RATE_FILTER_OPTIONS_ZAR.forEach { rate ->
                    val isSelected = selectedRate == rate
                    val label = if (rate <= 0.0) "All rides" else "R ${rate.toInt()}+/km"
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectRate(rate)
                            expanded = false
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(48.dp).testTag("min_rate_chip_${rate.toInt()}")
                    )
                }
            }
        }
    }
}

@Composable
fun DriverOfflineDashboardSheet(
    vehicleLabel: String,
    registration: String,
    todayEarningsZar: Double,
    completedTripsCount: Int,
    weeklyPlanActive: Boolean,
    locationGranted: Boolean = true,
    hasGpsFix: Boolean = false,
    currentAddress: String? = null,
    addressLoading: Boolean = false,
    onEnableLocation: (() -> Unit)? = null,
    onGoOnline: () -> Unit,
    minRatePerKmZar: Double = 0.0,
    filteredOutCount: Int = 0,
    onSelectMinRatePerKm: (Double) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ready to drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = vehicleLabel.ifBlank { "Vehicle details saved" },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (registration.isNotBlank()) {
                    MonoBadge(text = registration)
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (!locationGranted) {
                    wayfareColors.warning.copy(alpha = 0.14f)
                } else {
                    colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    1.dp,
                    if (!locationGranted) wayfareColors.warning.copy(alpha = 0.45f) else colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!locationGranted && onEnableLocation != null) {
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onEnableLocation)
                        } else {
                            Modifier
                        }
                    )
                    .testTag("driver_current_address_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = if (locationGranted) Icons.Filled.MyLocation else Icons.Filled.LocationOff,
                        contentDescription = if (locationGranted) "Current driver GPS address" else "Location permission off",
                        tint = when {
                            !locationGranted -> wayfareColors.warning
                            hasGpsFix -> wayfareColors.emeraldAccent
                            else -> colorScheme.primary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    val addressText = when {
                        !locationGranted -> "Location permission off · Tap to allow GPS"
                        !hasGpsFix -> "Acquiring your live GPS location..."
                        addressLoading && currentAddress.isNullOrBlank() -> "Resolving your street address..."
                        !currentAddress.isNullOrBlank() -> currentAddress
                        else -> "Live GPS fixed"
                    }
                    Text(
                        text = addressText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            DriverShiftSummaryStrip(
                todayEarningsZar = todayEarningsZar,
                completedTripsCount = completedTripsCount,
                weeklyPlanActive = weeklyPlanActive
            )

            MinRateFilterStrip(
                selectedRate = minRatePerKmZar,
                filteredOutCount = filteredOutCount,
                onSelectRate = onSelectMinRatePerKm
            )

            Button(
                onClick = onGoOnline,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .tactilePressScale()
                    .testTag("driver_go_online_cta")
            ) {
                Text("Go online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DriverScanningWaitingSheet(
    todayEarningsZar: Double,
    completedTripsCount: Int,
    weeklyPlanActive: Boolean,
    hottestZone: LaunchPolicy.DemandHeatZone?,
    hottestZoneDistanceKm: Double?,
    onFocusHottestZone: () -> Unit,
    onGoOffline: () -> Unit,
    locationGranted: Boolean = true,
    hasGpsFix: Boolean = false,
    currentAddress: String? = null,
    minRatePerKmZar: Double = 0.0,
    filteredOutCount: Int = 0,
    onSelectMinRatePerKm: (Double) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val radarTransition = rememberInfiniteTransition(label = "driverRadarPulse")
    val pulseScale by radarTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarScale"
    )

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(wayfareColors.positive.copy(alpha = 0.18f))
                        )
                        Icon(
                            imageVector = Icons.Filled.Radar,
                            contentDescription = "Scanning",
                            tint = wayfareColors.positive,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Scanning nearby requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val subtitle = when {
                            !locationGranted -> "Enable GPS to receive accurate nearby pickups."
                            hasGpsFix && !currentAddress.isNullOrBlank() -> "Near $currentAddress"
                            else -> "You're online · acquiring live GPS location..."
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = onGoOffline,
                    modifier = Modifier.height(48.dp).testTag("driver_go_offline_cta")
                ) {
                    Text("Go offline")
                }
            }

            if (hottestZone != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = wayfareColors.heatLow.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, wayfareColors.heatMid.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onFocusHottestZone)
                        .testTag("driver_focus_hotspot_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = wayfareColors.heatHigh,
                                modifier = Modifier.size(18.dp)
                            )
                            val dist = hottestZoneDistanceKm?.let { " · ${"%.1f".format(Locale.US, it)} km" }.orEmpty()
                            Text(
                                text = "Busy area: ${hottestZone.label}$dist",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = wayfareColors.heatMid
                        )
                    }
                }
            }

            DriverShiftSummaryStrip(
                todayEarningsZar = todayEarningsZar,
                completedTripsCount = completedTripsCount,
                weeklyPlanActive = weeklyPlanActive
            )

            MinRateFilterStrip(
                selectedRate = minRatePerKmZar,
                filteredOutCount = filteredOutCount,
                onSelectRate = onSelectMinRatePerKm
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriverIncomingOfferCard(
    trip: RideTrip,
    requesterName: String,
    driverUserId: String,
    requestIndex: Int,
    totalCount: Int,
    nowEpochMs: Long,
    driverCoords: RideCoordinates,
    busyAccepting: Boolean,
    onAccept: () -> Unit,
    onCounter: (Double) -> Unit,
    onSkipNext: () -> Unit,
    onDecline: () -> Unit,
    corridorTarget: DriverCorridorTarget? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val allowsCounter = trip.pricingMode == PricingMode.OFFER
    val metrics = remember(trip, driverCoords, driverUserId) {
        DriverDispatchMetrics.compute(trip, driverCoords, driverUserId)
    }
    val deadhead = remember(trip, driverCoords) {
        DriverMapEngine.computeDeadheadVsPaid(trip, driverCoords)
    }
    val matchesCorridor = remember(trip, driverCoords, corridorTarget) {
        corridorTarget != null && DriverMapEngine.matchesCorridorFilter(trip, driverCoords, corridorTarget)
    }

    var showCounterRow by remember(trip.id) { mutableStateOf(false) }
    var counterAmountText by remember(trip.id, trip.fare) {
        mutableStateOf((trip.fare.roundToInt() + 10).toString())
    }
    var secondsLeft by remember(trip.id) { mutableIntStateOf(OFFER_CYCLE_SECONDS) }
    val barProgress = remember(trip.id) { Animatable(1f) }

    LaunchedEffect(trip.id, totalCount) {
        while (true) {
            secondsLeft = OFFER_CYCLE_SECONDS
            barProgress.snapTo(1f)
            val animJob = launch {
                barProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = OFFER_CYCLE_SECONDS * 1000, easing = LinearEasing)
                )
            }
            for (s in OFFER_CYCLE_SECONDS downTo 1) {
                secondsLeft = s
                delay(1_000L)
            }
            animJob.cancel()
            if (totalCount > 1) {
                onSkipNext()
                break
            }
        }
    }

    val ttlLabel = remember(trip.expiresAt, trip.createdAt, nowEpochMs) {
        LaunchPolicy.formatCountdownMs(LaunchPolicy.remainingOpenMs(trip, nowEpochMs))
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Animated top offer countdown strip (Uber/Bolt style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress.value.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(if (secondsLeft <= 5) colorScheme.error else colorScheme.primary)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (totalCount > 1) {
                                "Ride request · $requestIndex of $totalCount · TTL $ttlLabel"
                            } else {
                                "Ride request · Expires in $ttlLabel"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$requesterName · ${trip.category.label}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (secondsLeft <= 5) colorScheme.errorContainer else wayfareColors.brandHighlight
                    ) {
                        Text(
                            text = "${secondsLeft}s",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = WayfareMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (secondsLeft <= 5) colorScheme.error else colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("driver_request_countdown")
                        )
                    }
                }

                // Fare & Economics Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = LaunchPolicy.formatZar(trip.fare),
                            fontFamily = WayfareMonoFamily,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        metrics.ratePerKmZar?.let { rpk ->
                            MonoBadge(
                                text = "R ${"%.1f".format(Locale.US, rpk)}/km",
                                containerColor = wayfareColors.positive.copy(alpha = 0.14f),
                                contentColor = wayfareColors.positive,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                    PaymentMethodPill(method = trip.paymentMethod)
                }

                // Deadhead (unpaid approach) vs Paid-trip split visual bar
                deadhead?.let { split ->
                    val paidPct = (split.paidShareFraction * 100f).roundToInt().coerceIn(5, 100)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.50f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().testTag("driver_deadhead_breakdown")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Deadhead ${"%.1f".format(Locale.US, split.deadheadKm)} km · Paid ${"%.1f".format(Locale.US, split.paidTripKm)} km",
                                    fontFamily = WayfareMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "$paidPct% paid",
                                    fontFamily = WayfareMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (paidPct >= 65) wayfareColors.positive else wayfareColors.warning
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            ) {
                                val deadFraction = (1f - split.paidShareFraction).coerceIn(0.0f, 0.95f)
                                if (deadFraction > 0.02f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(deadFraction)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(wayfareColors.warning)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(split.paidShareFraction.coerceAtLeast(0.05f))
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(wayfareColors.positive)
                                )
                            }
                        }
                    }
                }

                // Prioritized offer badges: cap upfront badges to top 3 high-signal tags + inline details toggle
                var showExtraBadges by remember(trip.id) { mutableStateOf(false) }
                val offerBadges = buildList {
                    if (matchesCorridor && corridorTarget != null) {
                        add(
                            Triple(
                                "On way to ${corridorTarget.label.substringBefore(" ·")}",
                                wayfareColors.emeraldContainer to wayfareColors.positive,
                                "driver_offer_corridor_badge"
                            )
                        )
                    }
                    if (trip.isReturnRequest) {
                        add(
                            Triple(
                                "Return Request",
                                wayfareColors.emeraldContainer to wayfareColors.positive,
                                "driver_offer_return_request_badge"
                            )
                        )
                    }
                    if (trip.paymentMethod == PaymentMethod.CASH && trip.cashChangeForZar != null) {
                        add(
                            Triple(
                                "Bring change for R${trip.cashChangeForZar}",
                                wayfareColors.warning.copy(alpha = 0.16f) to wayfareColors.warning,
                                "driver_request_cash_change"
                            )
                        )
                    }
                    if (metrics.pickupKm != null && metrics.pickupEtaMin != null) {
                        add(
                            Triple(
                                "Pickup ~${metrics.pickupEtaMin} min (${"%.1f".format(Locale.US, metrics.pickupKm)} km)",
                                wayfareColors.brandHighlight to colorScheme.onPrimaryContainer,
                                null
                            )
                        )
                    }
                    if (metrics.tripKm != null && metrics.tripEtaMin != null) {
                        add(
                            Triple(
                                "Trip ~${metrics.tripEtaMin} min (${"%.1f".format(Locale.US, metrics.tripKm)} km)",
                                colorScheme.surfaceVariant to colorScheme.onSurfaceVariant,
                                null
                            )
                        )
                    }
                    if (trip.category == RideCategory.BAKKIE || trip.category == RideCategory.PARCEL) {
                        add(
                            Triple(
                                "${trip.category.label} · ${trip.category.caption}",
                                wayfareColors.brandHighlight to colorScheme.onPrimaryContainer,
                                null
                            )
                        )
                    }
                    if (trip.isSharedRide) {
                        add(
                            Triple(
                                "Shared · ${trip.seatCount} seat(s)",
                                wayfareColors.emeraldContainer to wayfareColors.positive,
                                null
                            )
                        )
                    }
                    if (trip.isReturnTrip) {
                        add(
                            Triple(
                                "Wait & return",
                                wayfareColors.brandHighlight to colorScheme.onPrimaryContainer,
                                null
                            )
                        )
                    }
                    if (trip.includesGravelRoad) {
                        add(
                            Triple(
                                "Gravel road",
                                wayfareColors.warning.copy(alpha = 0.16f) to wayfareColors.warning,
                                null
                            )
                        )
                    }
                    trip.beneficiaryName?.takeIf { it.isNotBlank() }?.let { person ->
                        add(
                            Triple(
                                "For: $person",
                                wayfareColors.brandHighlight to colorScheme.onPrimaryContainer,
                                null
                            )
                        )
                    }
                    if (trip.riderVerified == true || (trip.riderCompletedRides ?: 0) > 0) {
                        val rideCountSuffix = trip.riderCompletedRides?.takeIf { it > 0 }?.let { " · $it rides" }.orEmpty()
                        add(
                            Triple(
                                "Verified rider$rideCountSuffix",
                                wayfareColors.emeraldContainer to wayfareColors.positive,
                                "driver_verified_rider_badge"
                            )
                        )
                    }
                }
                val visibleBadges = if (showExtraBadges) offerBadges else offerBadges.take(3)
                if (offerBadges.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        visibleBadges.forEach { (label, colors, tag) ->
                            StatusChip(
                                label = label,
                                containerColor = colors.first,
                                contentColor = colors.second,
                                modifier = if (tag != null) Modifier.testTag(tag) else Modifier
                            )
                        }
                        if (offerBadges.size > 3) {
                            FilterChip(
                                selected = showExtraBadges,
                                onClick = { showExtraBadges = !showExtraBadges },
                                label = {
                                    Text(
                                        text = if (showExtraBadges) "Fewer details" else "+${offerBadges.size - 3} more",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                modifier = Modifier.testTag("driver_offer_details_toggle")
                            )
                        }
                    }
                }

                trip.pickupNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = wayfareColors.brandHighlight.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth().testTag("driver_request_pickup_note")
                    ) {
                        Text(
                            text = "Pickup note: $note",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                // Visual A -> B Route Timeline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(wayfareColors.positive)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(colorScheme.outlineVariant)
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(wayfareColors.crimsonAccent)
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = trip.pickup.substringBefore(" ·"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = trip.destination.substringBefore(" ·"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                metrics.myPendingOffer?.let { myOffer ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = wayfareColors.warning.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, wayfareColors.warning.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().testTag("driver_existing_offer_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = wayfareColors.warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Your offer ${LaunchPolicy.formatZar(myOffer.amount)} sent · awaiting rider",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = allowsCounter && showCounterRow) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (metrics.quickBidAmountsZar.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                metrics.quickBidAmountsZar.forEachIndexed { idx, bidAmount ->
                                    FilterChip(
                                        selected = false,
                                        enabled = !busyAccepting,
                                        onClick = {
                                            onCounter(bidAmount)
                                            showCounterRow = false
                                        },
                                        label = {
                                            Text(
                                                text = LaunchPolicy.formatZar(bidAmount),
                                                fontFamily = WayfareMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = wayfareColors.brandHighlight.copy(alpha = 0.55f),
                                            labelColor = colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("driver_quick_bid_$idx")
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = counterAmountText,
                                onValueChange = { text -> counterAmountText = text.filter { it.isDigit() }.take(5) },
                                prefix = { Text("R ") },
                                label = { Text("Custom offer") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("driver_counter_input")
                            )
                            Button(
                                onClick = {
                                    val amt = counterAmountText.toDoubleOrNull() ?: trip.fare
                                    onCounter(amt)
                                    showCounterRow = false
                                },
                                enabled = !busyAccepting,
                                modifier = Modifier.height(52.dp).testTag("driver_send_counter_btn")
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        enabled = !busyAccepting,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier.height(52.dp).testTag("driver_decline_request")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Decline request", modifier = Modifier.size(18.dp))
                    }

                    if (totalCount > 1) {
                        OutlinedButton(
                            onClick = onSkipNext,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(52.dp).testTag("driver_skip_request")
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next request", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Next", maxLines = 1)
                        }
                    }

                    if (allowsCounter) {
                        OutlinedButton(
                            onClick = { showCounterRow = !showCounterRow },
                            enabled = !busyAccepting,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(52.dp).testTag("driver_toggle_counter")
                        ) {
                            Text("Offer", maxLines = 1)
                        }
                    }

                    Button(
                        onClick = onAccept,
                        enabled = !busyAccepting,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .tactilePressScale()
                            .testTag("driver_accept_request")
                    ) {
                        Text(
                            text = "Accept ${LaunchPolicy.formatZar(trip.fare)}",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DriverPaymentVerificationChip(
    trip: RideTrip,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val (label, bg, fg, showCheck) = when (trip.paymentMethod) {
        PaymentMethod.CARD -> when (trip.paymentStatus) {
            PaymentStatus.AUTHORIZED, PaymentStatus.PAID -> Quad(
                "Card · Verified",
                wayfareColors.positive.copy(alpha = 0.14f),
                wayfareColors.positive,
                true
            )
            PaymentStatus.FAILED, PaymentStatus.EXPIRED -> Quad(
                "Card · Failed",
                colorScheme.errorContainer,
                colorScheme.error,
                false
            )
            else -> Quad(
                "Card · Awaiting rider",
                wayfareColors.warning.copy(alpha = 0.16f),
                wayfareColors.warning,
                false
            )
        }
        PaymentMethod.WALLET -> Quad(
            "Wallet · Secured",
            wayfareColors.positive.copy(alpha = 0.14f),
            wayfareColors.positive,
            true
        )
        PaymentMethod.CASH -> Quad(
            "Cash · Collect ${LaunchPolicy.formatZar(trip.fare)}",
            colorScheme.secondaryContainer,
            colorScheme.onSecondaryContainer,
            false
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier.testTag("driver_payment_verification_chip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            if (showCheck) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun DriverTripStageTimeline(
    status: RideStatus,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val currentStep = when (status) {
        RideStatus.ACCEPTED -> 0
        RideStatus.ARRIVED -> 1
        RideStatus.IN_PROGRESS, RideStatus.COMPLETED -> 2
        else -> 0
    }
    val steps = listOf("To pickup", "At pickup", "On trip")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        steps.forEachIndexed { idx, label ->
            val reached = idx <= currentStep
            val isCurrent = idx == currentStep
            val barColor = when {
                idx < currentStep -> wayfareColors.positive
                isCurrent -> colorScheme.primary
                else -> colorScheme.surfaceVariant
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (reached) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DriverCancelTripDialog(
    requesterName: String,
    onConfirmCancel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val reasons = listOf(
        "Rider did not arrive at pickup",
        "Pickup location inaccessible",
        "Vehicle or safety issue",
        "Wait time exceeded"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel trip with $requesterName?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Choose a reason so the rider is notified and re-matched promptly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCancel(selectedReason) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                    contentColor = colorScheme.onError
                ),
                modifier = Modifier.testTag("driver_cancel_confirm_btn")
            ) {
                Text("Cancel trip", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep trip")
            }
        }
    )
}
