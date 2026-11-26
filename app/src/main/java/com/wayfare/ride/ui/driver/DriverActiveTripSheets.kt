package com.wayfare.ride.ui.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.ride.audio.SoundAlertPlayer
import com.wayfare.ride.domain.DriverIssueCategory
import com.wayfare.ride.domain.DriverRiderRating
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.ReturnMatchOriginType
import com.wayfare.ride.domain.ReturnRideMatch
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.notifications.RideNotificationHelper
import com.wayfare.ride.service.RideTrackingService
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.AnimatedZarAmountText
import com.wayfare.ride.ui.components.CelebrationConfettiOverlay
import com.wayfare.ride.ui.components.CelebrationHeroEmblem
import com.wayfare.ride.ui.components.Emergency112Chip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareConfirmDialog
import com.wayfare.ride.ui.components.alertTrustedContacts
import com.wayfare.ride.ui.components.dialEmergency112
import com.wayfare.ride.ui.components.dialPhoneNumber
import com.wayfare.ride.ui.components.openExternalNavigation
import com.wayfare.ride.ui.components.resolveBestLastKnownLocation
import com.wayfare.ride.ui.components.resolveFreshDeviceLocation
import com.wayfare.ride.ui.components.sendSmsMessage
import com.wayfare.ride.ui.components.shareTripSnapshot
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.rider.DriverChatSheetContent
import com.wayfare.ride.ui.rider.IssueReportingSheet
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun PaymentMethodPill(
    method: PaymentMethod,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val icon = when (method) {
        PaymentMethod.CASH -> Icons.Filled.Payments
        PaymentMethod.WALLET -> Icons.Filled.AccountBalanceWallet
        PaymentMethod.CARD -> Icons.Filled.CreditCard
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.secondaryContainer,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = method.label,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = method.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DriverActiveTripSheet(
    trip: RideTrip,
    requesterName: String,
    driverCoords: RideCoordinates,
    privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    trustedContacts: List<TrustedContact> = emptyList(),
    viewModel: WayfareViewModel,
    onExpandDriveMode: () -> Unit,
    nowEpochMs: Long = System.currentTimeMillis(),
    waitArrivedAtMs: Long? = trip.driverArrivedAt ?: trip.arrivalConfirmedAt,
    unreadChatCount: Int = 0,
    pendingOpenChatTripId: String? = null,
    returnMatches: List<ReturnRideMatch> = emptyList(),
    queuedReturnTrip: RideTrip? = null,
    onQueueReturnRide: (ReturnRideMatch) -> Unit = {},
    onCounterReturnRide: (RideTrip, Double) -> Unit = { _, _ -> },
    onDismissReturnRide: (String) -> Unit = {},
    onCancelQueuedReturn: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val trackingActive by RideTrackingService.isTrackingActive.collectAsStateWithLifecycle()

    var showStageModal by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    var showIssueSheet by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var pinInput by remember(trip.id) { mutableStateOf("") }
    var gpsStatusNote by remember(trip.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingOpenChatTripId, trip.id) {
        if (pendingOpenChatTripId == trip.id) {
            showChatSheet = true
            viewModel.consumePendingOpenChat(trip.id)
        }
    }

    LaunchedEffect(showChatSheet, trip.id, trip.messages.size) {
        viewModel.setChatSheetOpen(trip.id, showChatSheet)
        if (showChatSheet) {
            RideNotificationHelper.dismissTripChatNotification(context, trip.id)
        }
    }

    DisposableEffect(trip.id) {
        onDispose {
            viewModel.setChatSheetOpen(trip.id, false)
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            gpsStatusNote = "Resolving live GPS..."
            coroutineScope.launch {
                val resolved = resolveFreshDeviceLocation(context) ?: resolveDriverDeviceLocation(context)
                if (resolved != null) {
                    viewModel.updateDriverCoords(resolved, trip.id, fromGps = true)
                    gpsStatusNote = "Live GPS updated."
                } else {
                    gpsStatusNote = "Device GPS fix unavailable yet. Move outdoors and retry."
                }
            }
        } else {
            gpsStatusNote = "Location permission is required to confirm GPS arrival."
        }
    }

    fun syncRealDriverGps() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            val cached = resolveDriverDeviceLocation(context)
            if (cached != null) {
                viewModel.updateDriverCoords(cached, trip.id, fromGps = true)
                gpsStatusNote = "Live GPS updated."
            } else {
                gpsStatusNote = "Acquiring live GPS fix..."
            }
            coroutineScope.launch {
                resolveFreshDeviceLocation(context)?.let { fresh ->
                    viewModel.updateDriverCoords(fresh, trip.id, fromGps = true)
                    gpsStatusNote = "Live GPS updated."
                } ?: run {
                    if (cached == null) {
                        gpsStatusNote = "Waiting for device GPS fix..."
                    }
                }
            }
        } else {
            gpsPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(trip.id, trip.status) {
        if (trip.status == RideStatus.ACCEPTED || trip.status == RideStatus.IN_PROGRESS) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                val resolved = resolveFreshDeviceLocation(context) ?: resolveDriverDeviceLocation(context)
                resolved?.let { viewModel.updateDriverCoords(it, trip.id, fromGps = true) }
            }
        }
    }

    val stageLabel = when (trip.status) {
        RideStatus.ACCEPTED -> "1. To pickup"
        RideStatus.ARRIVED -> "2. At pickup · Enter PIN"
        RideStatus.IN_PROGRESS -> "3. On trip"
        else -> trip.status.label
    }

    val targetCoords = if (trip.status == RideStatus.IN_PROGRESS) {
        trip.destinationCoordinates
    } else {
        trip.pickupCoordinates
    }
    val remainingKm = remember(trip.liveDriverLocation, driverCoords, targetCoords, trip.route) {
        val current = trip.liveDriverLocation ?: driverCoords
        LaunchPolicy.haversineKm(current, targetCoords)
            ?: ((trip.route?.distanceMeters ?: 4500) / 1000.0)
    }
    val remainingMin = remember(remainingKm) { (remainingKm * 2.2).roundToInt().coerceAtLeast(1) }

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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StatusChip(
                        label = stageLabel,
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                    DriverPaymentVerificationChip(trip = trip)
                }
                Spacer(Modifier.width(6.dp))
                MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
            }

            DriverTripStageTimeline(status = trip.status)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$requesterName · ~${remainingMin} min (${"%.1f".format(remainingKm)} km)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(48.dp).testTag("driver_more_actions_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More trip actions",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh GPS") },
                            leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                syncRealDriverGps()
                            },
                            modifier = Modifier.testTag("driver_sync_gps_btn")
                        )
                        DropdownMenuItem(
                            text = { Text("Share trip") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                shareTripSnapshot(context, trip)
                            },
                            modifier = Modifier.testTag("driver_share_trip_btn")
                        )
                        if (trustedContacts.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Alert trusted contacts") },
                                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    alertTrustedContacts(context, trip, trustedContacts, driverCoords)
                                },
                                modifier = Modifier.testTag("driver_alert_contacts_btn")
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Report issue") },
                            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showIssueSheet = true
                            },
                            modifier = Modifier.testTag("driver_report_issue_btn")
                        )
                        if (trip.status == RideStatus.ACCEPTED || trip.status == RideStatus.ARRIVED) {
                            DropdownMenuItem(
                                text = { Text("Cancel trip", color = colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    showCancelDialog = true
                                },
                                modifier = Modifier.testTag("driver_cancel_trip_btn")
                            )
                        }
                    }
                }
            }

            Text(
                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val showDriverMetaChips = trip.riderVerified == true ||
                (trip.riderCompletedRides ?: 0) > 0 ||
                (trip.paymentMethod == PaymentMethod.CASH && trip.cashChangeForZar != null) ||
                trip.category == RideCategory.BAKKIE ||
                trip.category == RideCategory.PARCEL ||
                trip.isSharedRide ||
                trip.isReturnTrip ||
                trip.isReturnRequest ||
                trip.includesGravelRoad
            if (showDriverMetaChips) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (trip.category == RideCategory.BAKKIE || trip.category == RideCategory.PARCEL) {
                        StatusChip(
                            label = "${trip.category.label} · ${trip.category.caption}",
                            containerColor = wayfareColors.brandHighlight,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    }
                    if (trip.isSharedRide) {
                        StatusChip(
                            label = "Shared · ${trip.seatCount} seat(s)",
                            containerColor = wayfareColors.emeraldContainer,
                            contentColor = wayfareColors.positive
                        )
                    }
                    if (trip.isReturnTrip) {
                        StatusChip(
                            label = "Wait & return",
                            containerColor = wayfareColors.brandHighlight,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    }
                    if (trip.isReturnRequest) {
                        StatusChip(
                            label = "Return Request",
                            containerColor = wayfareColors.emeraldContainer,
                            contentColor = wayfareColors.positive,
                            modifier = Modifier.testTag("driver_active_return_request_chip")
                        )
                    }
                    if (trip.includesGravelRoad) {
                        StatusChip(
                            label = "Gravel road",
                            containerColor = wayfareColors.warning.copy(alpha = 0.16f),
                            contentColor = wayfareColors.warning
                        )
                    }
                    if (trip.riderVerified == true || (trip.riderCompletedRides ?: 0) > 0) {
                        val suffix = trip.riderCompletedRides?.takeIf { it > 0 }?.let { " · $it rides" }.orEmpty()
                        StatusChip(
                            label = "Verified rider$suffix",
                            containerColor = wayfareColors.emeraldContainer,
                            contentColor = wayfareColors.positive
                        )
                    }
                    if (trip.paymentMethod == PaymentMethod.CASH && trip.cashChangeForZar != null) {
                        StatusChip(
                            label = "Change for R${trip.cashChangeForZar}",
                            containerColor = wayfareColors.warning.copy(alpha = 0.16f),
                            contentColor = wayfareColors.warning,
                            modifier = Modifier.testTag("driver_active_cash_change_chip")
                        )
                    }
                }
            }

            if (!trip.beneficiaryName.isNullOrBlank() || !trip.beneficiaryPhone.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("driver_beneficiary_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Passenger: ${trip.beneficiaryName ?: "Booked for family"}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            trip.beneficiaryPhone?.takeIf { it.isNotBlank() }?.let { bPhone ->
                                Text(
                                    text = bPhone,
                                    fontFamily = WayfareMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        trip.beneficiaryPhone?.takeIf { it.isNotBlank() }?.let { bPhone ->
                            OutlinedButton(
                                onClick = { dialPhoneNumber(context, bPhone) },
                                modifier = Modifier.height(48.dp).testTag("driver_call_beneficiary_btn")
                            ) {
                                Icon(Icons.Filled.Phone, contentDescription = "Call passenger", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Call")
                            }
                            OutlinedButton(
                                onClick = {
                                    sendSmsMessage(
                                        context,
                                        bPhone,
                                        "Vhaya driver is on the way to ${trip.pickup.substringBefore(" ·")}. Destination: ${trip.destination.substringBefore(" ·")}."
                                    )
                                },
                                modifier = Modifier.height(48.dp).testTag("driver_sms_beneficiary_btn")
                            ) {
                                Icon(Icons.Filled.Sms, contentDescription = "SMS passenger", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SMS")
                            }
                        }
                    }
                }
            }

            trip.pickupNote?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = wayfareColors.brandHighlight.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().testTag("driver_active_pickup_note")
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

            if (trip.status == RideStatus.ARRIVED) {
                SmartRideGuards.evaluateWaitTimer(waitArrivedAtMs, nowEpochMs)?.let { waitStatus ->
                    val waitBg = if (waitStatus.isFreeWaitExpired) {
                        wayfareColors.warning.copy(alpha = 0.15f)
                    } else {
                        wayfareColors.emeraldContainer
                    }
                    val waitFg = if (waitStatus.isFreeWaitExpired) {
                        wayfareColors.warning
                    } else {
                        wayfareColors.positive
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = waitBg,
                        border = BorderStroke(1.dp, waitFg.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("driver_wait_timer_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = waitFg, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (waitStatus.isFreeWaitExpired) "Free wait exceeded" else "Waiting at pickup",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            MonoBadge(
                                text = waitStatus.badgeLabel,
                                containerColor = colorScheme.surface,
                                contentColor = waitFg
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showChatSheet = true },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("driver_chat_btn")
                ) {
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (trip.messages.isNotEmpty()) "Chat (${trip.messages.size})" else "Chat",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (unreadChatCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.error,
                            modifier = Modifier.testTag("chat_unread_badge")
                        ) {
                            Text(
                                text = unreadChatCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (targetCoords != null) {
                            val navLabel = if (trip.status == RideStatus.IN_PROGRESS) trip.destination else trip.pickup
                            openExternalNavigation(context, targetCoords, navLabel)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
                }
                Emergency112Chip(onClick = {
                    if (trustedContacts.isNotEmpty()) {
                        alertTrustedContacts(context, trip, trustedContacts, driverCoords)
                    } else {
                        dialEmergency112(context)
                    }
                })
            }

            if (trip.status == RideStatus.IN_PROGRESS) {
                DriverReturnRadarSection(
                    queuedReturnTrip = queuedReturnTrip,
                    returnMatches = returnMatches,
                    onQueueReturnRide = onQueueReturnRide,
                    onCounterReturnRide = onCounterReturnRide,
                    onDismissReturnRide = onDismissReturnRide,
                    onCancelQueuedReturn = onCancelQueuedReturn
                )
            }

            when (trip.status) {
                RideStatus.ACCEPTED -> {
                    Button(
                        onClick = { showStageModal = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp).tactilePressScale().testTag("driver_open_stage_modal")
                    ) {
                        Text("Arrived at pickup", fontWeight = FontWeight.Bold)
                    }
                }
                RideStatus.ARRIVED -> {
                    Button(
                        onClick = { showStageModal = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp).tactilePressScale().testTag("driver_enter_pin_cta")
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enter 6-digit PIN to start", fontWeight = FontWeight.Bold)
                    }
                }
                RideStatus.IN_PROGRESS -> {
                    Button(
                        onClick = onExpandDriveMode,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("driver_expand_drive_mode")
                    ) {
                        Text("Open Drive Mode", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {}
            }
        }
    }

    if (showStageModal) {
        BackHandler { showStageModal = false }
        ModalBottomSheet(onDismissRequest = { showStageModal = false }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Trip stage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    StagePill("1. To pickup", active = trip.status == RideStatus.ACCEPTED, done = trip.status != RideStatus.ACCEPTED, modifier = Modifier.weight(1f))
                    StagePill("2. At pickup", active = trip.status == RideStatus.ARRIVED, done = trip.status == RideStatus.IN_PROGRESS, modifier = Modifier.weight(1f))
                    StagePill("3. On trip", active = trip.status == RideStatus.IN_PROGRESS, done = false, modifier = Modifier.weight(1f))
                }

                if (trip.status == RideStatus.ACCEPTED) {
                    val dist = trip.pickupCoordinates?.let {
                        LaunchPolicy.distanceMeters(trip.liveDriverLocation ?: driverCoords, it).toInt()
                    } ?: 0
                    Text(
                        text = "Distance to pickup: $dist m. Tap Confirm arrival once you're at the pickup point.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    gpsStatusNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = { syncRealDriverGps() },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stage_sync_gps_btn")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh GPS coordinates")
                    }
                    Button(
                        onClick = {
                            syncRealDriverGps()
                            viewModel.driverMarkArrived(trip.id)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("stage_confirm_gps_arrived")
                    ) {
                        Text("Confirm GPS arrival", fontWeight = FontWeight.Bold)
                    }
                } else if (trip.status == RideStatus.ARRIVED) {
                    Text(
                        text = "Ask $requesterName for their 6-digit pickup PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { s -> pinInput = s.filter { it.isDigit() }.take(6) },
                        label = { Text("6-digit Pickup PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("driver_pin_input")
                    )
                    Button(
                        onClick = {
                            viewModel.driverVerifyPinAndStart(trip.id, pinInput)
                            showStageModal = false
                        },
                        enabled = pinInput.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("driver_verify_pin_btn")
                    ) {
                        Text("Verify PIN & start trip", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showChatSheet) {
        BackHandler { showChatSheet = false }
        ModalBottomSheet(onDismissRequest = { showChatSheet = false }) {
            DriverChatSheetContent(
                trip = trip,
                senderRole = "driver",
                onSend = { viewModel.sendTripChat(trip.id, it) },
                onClose = { showChatSheet = false }
            )
        }
    }

    if (showCancelDialog) {
        DriverCancelTripDialog(
            requesterName = requesterName,
            onConfirmCancel = { reason ->
                showCancelDialog = false
                viewModel.driverCancelTrip(trip.id, reason)
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    if (showIssueSheet) {
        IssueReportingSheet(
            trip = trip,
            isDriver = true,
            onSubmit = { categoryKey, message, requestRefund, attachmentUri ->
                showIssueSheet = false
                val priority = DriverIssueCategory.fromKey(categoryKey).defaultPriority
                viewModel.createSupportCase(
                    trip = trip,
                    category = categoryKey,
                    priority = priority,
                    message = message,
                    requestRefund = requestRefund,
                    attachmentUri = attachmentUri
                )
            },
            onDismiss = { showIssueSheet = false }
        )
    }
}

@Composable
private fun StagePill(label: String, active: Boolean, done: Boolean, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            done -> wayfareColors.brandHighlight
            active -> colorScheme.primary
            else -> colorScheme.surfaceVariant
        },
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                done -> colorScheme.onPrimaryContainer
                active -> colorScheme.onPrimary
                else -> colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDriveModeOverlay(
    trip: RideTrip,
    requesterName: String,
    driverCoords: RideCoordinates,
    navRoute: com.wayfare.ride.domain.RideRoute? = trip.route,
    isLoadingNav: Boolean = false,
    followCamera: Boolean = true,
    privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    trustedContacts: List<TrustedContact> = emptyList(),
    viewModel: WayfareViewModel,
    onMinimize: () -> Unit,
    voiceNavEnabled: Boolean = true,
    headingUpEnabled: Boolean = false,
    onToggleVoiceNav: () -> Unit = {},
    onToggleHeadingUp: () -> Unit = {},
    onQuickReportHazard: () -> Unit = {},
    unreadChatCount: Int = 0,
    pendingOpenChatTripId: String? = null,
    returnMatches: List<ReturnRideMatch> = emptyList(),
    queuedReturnTrip: RideTrip? = null,
    onQueueReturnRide: (ReturnRideMatch) -> Unit = {},
    onCounterReturnRide: (RideTrip, Double) -> Unit = { _, _ -> },
    onDismissReturnRide: (String) -> Unit = {},
    onCancelQueuedReturn: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val trackingActive by RideTrackingService.isTrackingActive.collectAsStateWithLifecycle()
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showIssueSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pendingOpenChatTripId, trip.id) {
        if (pendingOpenChatTripId == trip.id) {
            showChat = true
            viewModel.consumePendingOpenChat(trip.id)
        }
    }

    LaunchedEffect(showChat, trip.id, trip.messages.size) {
        viewModel.setChatSheetOpen(trip.id, showChat)
        if (showChat) {
            RideNotificationHelper.dismissTripChatNotification(context, trip.id)
        }
    }

    DisposableEffect(trip.id) {
        onDispose {
            viewModel.setChatSheetOpen(trip.id, false)
        }
    }

    val liveDriverCoords = trip.liveDriverLocation ?: driverCoords
    val targetCoords = trip.destinationCoordinates ?: navRoute?.destinationCoordinates

    Box(modifier = Modifier.fillMaxSize()) {
        DriverTurnByTurnCard(
            route = navRoute,
            isLoadingNav = isLoadingNav,
            driverCoords = liveDriverCoords,
            targetCoords = targetCoords,
            targetLabel = trip.destination,
            isPickupLeg = false,
            followCamera = followCamera,
            onToggleFollowCamera = { viewModel.setDriverNavFollowCamera(!followCamera) },
            onReroute = { viewModel.refreshDriverNavigationRoute(force = true) },
            onOpenExternalMaps = {
                targetCoords?.let { openExternalNavigation(context, it, trip.destination) }
            },
            onMinimizeDriveMode = onMinimize,
            voiceNavEnabled = voiceNavEnabled,
            onToggleVoiceNav = onToggleVoiceNav,
            headingUpEnabled = headingUpEnabled,
            onToggleHeadingUp = onToggleHeadingUp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 14.dp, end = 14.dp)
                .widthIn(max = 580.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
        ) {
            if (trip.status == RideStatus.IN_PROGRESS) {
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    DriverReturnRadarSection(
                        queuedReturnTrip = queuedReturnTrip,
                        returnMatches = returnMatches,
                        onQueueReturnRide = onQueueReturnRide,
                        onCounterReturnRide = onCounterReturnRide,
                        onDismissReturnRide = onDismissReturnRide,
                        onCancelQueuedReturn = onCancelQueuedReturn
                    )
                }
            }

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = colorScheme.primary)
                        Column {
                            Text("On trip · $requesterName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Drop-off ${trip.destination.substringBefore(" ·")}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DriverPaymentVerificationChip(trip = trip)
                        MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
                    }
                }

                    var showDriveMoreMenu by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                trip.destinationCoordinates?.let { openExternalNavigation(context, it, trip.destination) }
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Navigate", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showChat = true },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("driver_drive_chat_btn")
                        ) {
                            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (trip.messages.isNotEmpty()) "Chat (${trip.messages.size})" else "Chat",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (unreadChatCount > 0) {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.error,
                                    modifier = Modifier.testTag("chat_unread_badge")
                                ) {
                                    Text(
                                        text = unreadChatCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onError,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Emergency112Chip(onClick = {
                            if (trustedContacts.isNotEmpty()) {
                                alertTrustedContacts(context, trip, trustedContacts, driverCoords)
                            } else {
                                dialEmergency112(context)
                            }
                        })
                        Box {
                            OutlinedButton(
                                onClick = { showDriveMoreMenu = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(48.dp).testTag("driver_drive_more_actions_btn")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More trip actions", modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showDriveMoreMenu,
                                onDismissRequest = { showDriveMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Report road hazard") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = wayfareColors.warning)
                                    },
                                    onClick = {
                                        showDriveMoreMenu = false
                                        onQuickReportHazard()
                                    },
                                    modifier = Modifier.testTag("driver_drive_report_hazard_btn")
                                )
                                DropdownMenuItem(
                                    text = { Text("Share trip") },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        showDriveMoreMenu = false
                                        shareTripSnapshot(context, trip)
                                    }
                                )
                                if (trustedContacts.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Alert trusted contacts") },
                                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                        onClick = {
                                            showDriveMoreMenu = false
                                            alertTrustedContacts(context, trip, trustedContacts, driverCoords)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Report issue") },
                                    leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                                    onClick = {
                                        showDriveMoreMenu = false
                                        showIssueSheet = true
                                    },
                                    modifier = Modifier.testTag("driver_drive_report_issue_btn")
                                )
                            }
                        }
                    }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCompleteConfirm = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp).tactilePressScale().testTag("driver_complete_trip_btn")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Complete trip · ${LaunchPolicy.formatZar(trip.fare)}", fontWeight = FontWeight.Bold)
                }
            }
            }
        }
    }

    if (showCompleteConfirm) {
        val dropOffCheck = SmartRideGuards.evaluateDropOffProximity(liveDriverCoords, targetCoords)
        val confirmTitle = if (dropOffCheck.isFarFromDestination) {
            "Far from drop-off — complete anyway?"
        } else {
            "Complete this trip?"
        }
        val confirmMessage = buildString {
            if (dropOffCheck.isFarFromDestination) {
                append(
                    "GPS notice: You are ${LaunchPolicy.formatApproachDistance(dropOffCheck.distanceMeters)} away from booked drop-off (${trip.destination.substringBefore(" ·")}). Complete only if $requesterName has already exited.\n\n"
                )
            }
            append(
                "Confirm drop-off at ${trip.destination.substringBefore(" ·")} and capture ${LaunchPolicy.formatZar(trip.fare)} (${trip.paymentMethod.label})."
            )
        }
        WayfareConfirmDialog(
            title = confirmTitle,
            message = confirmMessage,
            confirmLabel = if (dropOffCheck.isFarFromDestination) "Yes, complete trip" else "Complete trip",
            isDestructive = dropOffCheck.isFarFromDestination,
            onConfirm = {
                showCompleteConfirm = false
                viewModel.driverCompleteTrip(trip.id)
            },
            onDismiss = { showCompleteConfirm = false }
        )
    }

    if (showChat) {
        BackHandler { showChat = false }
        ModalBottomSheet(onDismissRequest = { showChat = false }) {
            DriverChatSheetContent(
                trip = trip,
                senderRole = "driver",
                onSend = { viewModel.sendTripChat(trip.id, it) },
                onClose = { showChat = false }
            )
        }
    }

    if (showIssueSheet) {
        IssueReportingSheet(
            trip = trip,
            isDriver = true,
            onSubmit = { categoryKey, message, requestRefund, attachmentUri ->
                showIssueSheet = false
                val priority = DriverIssueCategory.fromKey(categoryKey).defaultPriority
                viewModel.createSupportCase(
                    trip = trip,
                    category = categoryKey,
                    priority = priority,
                    message = message,
                    requestRefund = requestRefund,
                    attachmentUri = attachmentUri
                )
            },
            onDismiss = { showIssueSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DriverPostTripSummarySheet(
    trip: RideTrip,
    requesterName: String,
    weeklyPlanActive: Boolean,
    existingRating: DriverRiderRating? = null,
    onRateRider: (stars: Int, tags: List<String>) -> Unit = { _, _ -> },
    onReportIssue: (category: String, message: String, requestRefund: Boolean, attachmentUri: Uri?) -> Unit = { _, _, _, _ -> },
    onViewEarnings: () -> Unit,
    onDismiss: () -> Unit,
    isSendingCashChange: Boolean = false,
    cashChangeCredited: Boolean = false,
    onSendCashChangeToWallet: (Int) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var selectedStars by remember(trip.id, existingRating?.stars) {
        mutableIntStateOf(existingRating?.stars ?: 5)
    }
    var selectedTags by remember(trip.id, existingRating?.tags) {
        mutableStateOf(existingRating?.tags?.toSet() ?: emptySet())
    }
    var showIssueSheet by remember(trip.id) { mutableStateOf(false) }

    val feedbackTags = remember(selectedStars) {
        if (selectedStars >= 4) {
            listOf("On time", "Polite", "Accurate pin", "Respectful")
        } else {
            listOf("Late pickup", "Pin mismatch", "Long wait", "Route change")
        }
    }

    BackHandler { onDismiss() }

    LaunchedEffect(trip.id) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        SoundAlertPlayer.playSuccessCelebrationChime(context)
    }

    val settlementNote = when (trip.paymentMethod) {
        PaymentMethod.CASH -> "Collect ${LaunchPolicy.formatZar(trip.fare)} cash from $requesterName."
        PaymentMethod.WALLET -> "Settled from requester Vhaya wallet."
        PaymentMethod.CARD -> "Captured via card hold."
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CelebrationHeroEmblem(
                        icon = Icons.Filled.CheckCircle,
                        accentColor = wayfareColors.positive,
                        triggerKey = trip.id,
                        contentDescription = "Trip completed",
                        badgeSize = 46.dp,
                        iconSize = 24.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Trip completed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            StatusChip(
                                label = "Earned",
                                containerColor = wayfareColors.positive.copy(alpha = 0.14f),
                                contentColor = wayfareColors.positive
                            )
                        }
                        Text(
                            text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = wayfareColors.positive.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Captured fare", style = MaterialTheme.typography.labelLarge, color = colorScheme.onSurfaceVariant)
                            PaymentMethodPill(method = trip.paymentMethod)
                        }
                        AnimatedZarAmountText(
                            targetAmount = trip.fare,
                            style = MaterialTheme.typography.headlineMedium,
                            color = wayfareColors.positive,
                            triggerKey = trip.id,
                            prefix = "+",
                            modifier = Modifier.testTag("driver_summary_fare")
                        )
                        HorizontalDivider()
                        Text(
                            text = settlementNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (weeklyPlanActive) {
                                "Vhaya Weekly Driver Pass active · 0% per-ride launch fee."
                            } else {
                                "Standard payout cycle settles weekly via Paystack / EFT."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (trip.paymentMethod == PaymentMethod.CASH) {
                    var changeInput by remember(trip.id, trip.cashChangeForZar, trip.fare) {
                        val suggested = trip.cashChangeForZar
                            ?.let { (it - trip.fare.roundToInt()).coerceAtLeast(10) }
                            ?: 20
                        mutableStateOf(suggested.toString())
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (cashChangeCredited) {
                            wayfareColors.emeraldContainer
                        } else {
                            colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (cashChangeCredited) wayfareColors.positive.copy(alpha = 0.45f) else colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("driver_cash_change_wallet_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = "Short on cash change?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (cashChangeCredited) {
                                Text(
                                    text = "Change credited to $requesterName's Vhaya Wallet ✓",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = wayfareColors.positive
                                )
                            } else {
                                Text(
                                    text = "Send the rider's leftover cash change directly to their Vhaya Wallet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(10, 20, 50).forEach { amt ->
                                        FilterChip(
                                            selected = changeInput == amt.toString(),
                                            onClick = { changeInput = amt.toString() },
                                            label = {
                                                Text(
                                                    text = "R$amt",
                                                    fontFamily = WayfareMonoFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = changeInput,
                                        onValueChange = { s -> changeInput = s.filter { it.isDigit() }.take(3) },
                                        prefix = { Text("R ") },
                                        label = { Text("Change") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).testTag("driver_cash_change_input")
                                    )
                                    val parsedAmt = changeInput.toIntOrNull() ?: 0
                                    Button(
                                        onClick = { onSendCashChangeToWallet(parsedAmt) },
                                        enabled = parsedAmt in 1..500 && !isSendingCashChange,
                                        modifier = Modifier.height(52.dp).testTag("driver_send_cash_change_btn")
                                    ) {
                                        Text(
                                            text = if (isSendingCashChange) "Sending..." else "Credit Wallet",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_post_trip_rating_card")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (existingRating != null) "Rider rated" else "Rate $requesterName",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (existingRating != null) {
                                        "${existingRating.stars} ★ saved for your trip records."
                                    } else {
                                        "Share quick trip feedback before your next ride."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { showIssueSheet = true },
                                modifier = Modifier.height(48.dp).testTag("driver_summary_report_issue")
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Report issue")
                            }
                        }

                        if (existingRating != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..5).forEach { star ->
                                    Icon(
                                        imageVector = if (star <= existingRating.stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = null,
                                        tint = if (star <= existingRating.stars) wayfareColors.routeHighlight else colorScheme.outline,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                if (existingRating.tags.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = existingRating.tags.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..5).forEach { star ->
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedStars = star
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("driver_rate_rider_star_$star")
                                    ) {
                                        Icon(
                                            imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = "Rate $star stars",
                                            tint = if (star <= selectedStars) wayfareColors.routeHighlight else colorScheme.outline,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                feedbackTags.forEach { tag ->
                                    val selected = tag in selectedTags
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedTags = if (selected) selectedTags - tag else selectedTags + tag
                                        },
                                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.primaryContainer,
                                            selectedLabelColor = colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRateRider(selectedStars, selectedTags.toList())
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("driver_submit_rider_rating_btn")
                            ) {
                                Text("Save ${selectedStars}★ rider rating", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onViewEarnings,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("driver_summary_view_earnings")
                    ) {
                        Text("View earnings")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp).tactilePressScale().testTag("driver_summary_done")
                    ) {
                        Text("Ready next ride", fontWeight = FontWeight.Bold)
                    }
                }
            }

            CelebrationConfettiOverlay(
                triggerKey = trip.id,
                focalYFraction = 0.22f,
                modifier = Modifier.matchParentSize()
            )
        }
    }

    if (showIssueSheet) {
        IssueReportingSheet(
            trip = trip,
            isDriver = true,
            onSubmit = { categoryKey, message, requestRefund, attachmentUri ->
                showIssueSheet = false
                onReportIssue(categoryKey, message, requestRefund, attachmentUri)
            },
            onDismiss = { showIssueSheet = false }
        )
    }
}

@Composable
private fun DriverReturnRadarSection(
    queuedReturnTrip: RideTrip?,
    returnMatches: List<ReturnRideMatch>,
    onQueueReturnRide: (ReturnRideMatch) -> Unit,
    onCounterReturnRide: (RideTrip, Double) -> Unit,
    onDismissReturnRide: (String) -> Unit,
    onCancelQueuedReturn: () -> Unit
) {
    when {
        queuedReturnTrip != null -> {
            QueuedReturnUpNextCard(
                queuedTrip = queuedReturnTrip,
                onCancelQueuedReturn = onCancelQueuedReturn
            )
        }
        returnMatches.isNotEmpty() -> {
            ReturnRideMatchBanner(
                matches = returnMatches,
                onQueueReturnRide = onQueueReturnRide,
                onCounterReturnRide = onCounterReturnRide,
                onDismissReturnRide = onDismissReturnRide
            )
        }
        else -> {
            ReturnRideRadarPill()
        }
    }
}

@Composable
private fun ReturnRideRadarPill() {
    val wayfareColors = LocalWayfareColors.current
    val pulseTransition = rememberInfiniteTransition(label = "returnRadarPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "returnRadarIconScale"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = wayfareColors.emeraldContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.30f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("return_ride_radar_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Radar,
                contentDescription = "Return Ride Radar active",
                tint = wayfareColors.positive,
                modifier = Modifier
                    .size(16.dp)
                    .scale(pulseScale)
            )
            Text(
                text = "Return Ride Radar · Listening within 8 km",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = wayfareColors.positive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueuedReturnUpNextCard(
    queuedTrip: RideTrip,
    onCancelQueuedReturn: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.5.dp, wayfareColors.positive),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queued_return_up_next_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = wayfareColors.emeraldContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Queued return ride",
                        tint = wayfareColors.positive,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusChip(
                        label = "Up Next · Return Ride",
                        containerColor = wayfareColors.emeraldContainer,
                        contentColor = wayfareColors.positive
                    )
                    MonoBadge(text = LaunchPolicy.formatZar(queuedTrip.fare))
                }
                Text(
                    text = "${queuedTrip.pickup.substringBefore(" ·")} → ${queuedTrip.destination.substringBefore(" ·")}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Starts automatically once you complete this drop-off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onCancelQueuedReturn,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("cancel_queued_return_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Release queued return ride",
                    tint = colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Release", color = colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReturnRideMatchBanner(
    matches: List<ReturnRideMatch>,
    onQueueReturnRide: (ReturnRideMatch) -> Unit,
    onCounterReturnRide: (RideTrip, Double) -> Unit,
    onDismissReturnRide: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val haptics = LocalHapticFeedback.current
    var matchIndex by remember(matches.size) { mutableIntStateOf(0) }
    val activeMatch = matches.getOrNull(matchIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0))) ?: return
    val trip = activeMatch.trip

    var showCounterRow by remember(trip.id) { mutableStateOf(false) }
    var counterInput by remember(trip.id, activeMatch.suggestedBackhaulFareZar) {
        mutableStateOf(activeMatch.suggestedBackhaulFareZar.roundToInt().toString())
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.5.dp, wayfareColors.positive.copy(alpha = 0.7f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("return_ride_match_banner")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = wayfareColors.positive,
                        modifier = Modifier.size(18.dp)
                    )
                    StatusChip(
                        label = if (trip.isReturnRequest) "Return Request" else "Return Ride",
                        containerColor = wayfareColors.emeraldContainer,
                        contentColor = wayfareColors.positive
                    )
                    val originTag = if (activeMatch.originType == ReturnMatchOriginType.NEAR_DROPOFF) {
                        "~${activeMatch.etaToPickupMin}m away"
                    } else {
                        "On return corridor"
                    }
                    Text(
                        text = originTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (matches.size > 1) {
                        TextButton(
                            onClick = { matchIndex = (matchIndex + 1) % matches.size },
                            modifier = Modifier.height(48.dp).testTag("return_ride_cycle_btn")
                        ) {
                            Text(
                                text = "${(matchIndex % matches.size) + 1}/${matches.size}",
                                fontFamily = WayfareMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next return match",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    MonoBadge(text = LaunchPolicy.formatZar(activeMatch.suggestedBackhaulFareZar))
                }
            }

            Text(
                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${activeMatch.proximitySummary} · ${trip.category.label} · ${trip.paymentMethod.label}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(visible = showCounterRow, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = counterInput,
                        onValueChange = { s -> counterInput = s.filter { it.isDigit() }.take(4) },
                        prefix = { Text("R") },
                        label = { Text("Offer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("return_ride_counter_input")
                    )
                    val parsed = counterInput.toDoubleOrNull() ?: activeMatch.suggestedBackhaulFareZar
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCounterRow = false
                            onCounterReturnRide(trip, parsed)
                        },
                        enabled = parsed >= 20.0,
                        modifier = Modifier.height(50.dp).testTag("return_ride_counter_send_btn")
                    ) {
                        Text("Send", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onDismissReturnRide(trip.id) },
                    modifier = Modifier.height(48.dp).testTag("return_ride_dismiss_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Skip return match",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Skip")
                }
                OutlinedButton(
                    onClick = { showCounterRow = !showCounterRow },
                    modifier = Modifier.height(48.dp).testTag("return_ride_counter_btn")
                ) {
                    Text(if (showCounterRow) "Hide" else "Counter")
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQueueReturnRide(activeMatch)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .tactilePressScale()
                        .testTag("return_ride_queue_btn")
                ) {
                    Text("Queue Next", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

private fun resolveDriverDeviceLocation(context: Context): RideCoordinates? =
    resolveBestLastKnownLocation(context)
