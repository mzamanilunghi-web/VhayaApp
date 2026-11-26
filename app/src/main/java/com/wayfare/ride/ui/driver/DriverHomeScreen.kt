package com.wayfare.ride.ui.driver

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wayfare.ride.audio.TurnVoiceNavigator
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.DriverIssueCategory
import com.wayfare.ride.ui.common.DriverPocketSheet
import com.wayfare.ride.domain.DriverMapEngine
import com.wayfare.ride.domain.HazardType
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.LocationPermissionState
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RoadHazardPin
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.service.RideTrackingService
import com.wayfare.ride.ui.DriverTab
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.LocationDegradedBanner
import com.wayfare.ride.ui.components.LocationRationaleDialog
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.OsmRouteMap
import com.wayfare.ride.ui.components.evaluateLocationPermissionState
import com.wayfare.ride.ui.components.findActivity
import com.wayfare.ride.ui.components.isDeviceLocationServicesEnabled
import com.wayfare.ride.ui.components.observeLiveDeviceLocationFlow
import com.wayfare.ride.ui.components.openAppLocationSettings
import com.wayfare.ride.ui.components.openDeviceLocationSourceSettings
import com.wayfare.ride.ui.components.openExternalNavigation
import com.wayfare.ride.ui.components.resolveBestLastKnownLocation
import com.wayfare.ride.ui.components.resolveFreshDeviceLocation
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import kotlinx.coroutines.launch

private fun resolveRequesterName(accounts: List<UserAccountSummary>, riderId: String): String {
    return accounts.firstOrNull { it.userId == riderId }?.displayName
        ?: "Vhaya Requester"
}

@Composable
fun DriverHomeScreen(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onOpenAccount: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val driverApp = uiState.currentDriverApp
    val isApproved = driverApp?.verificationStatus == "approved"
    val isOnline = driverApp?.isOnline == true
    val activeTrip = uiState.driverActiveTrip
    val openRequests = uiState.driverOpenRequests
    val currentRequest = if (openRequests.isNotEmpty()) {
        openRequests[uiState.driverRequestIndex % openRequests.size]
    } else null
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()

    fun currentLocationState(): LocationPermissionState {
        val rationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: false
        return evaluateLocationPermissionState(
            context = context,
            shouldShowRationale = rationale,
            hasRequestedBefore = viewModel.hasRequestedLocationPermission()
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var driverLocationState by remember { mutableStateOf(currentLocationState()) }
    var locationServicesEnabled by remember { mutableStateOf(isDeviceLocationServicesEnabled(context)) }
    var inSessionPermanentDenial by rememberSaveable(uiState.activeUserId) { mutableStateOf(false) }
    var showDriverRationale by remember { mutableStateOf(false) }
    var driverLocationAskedForSession by rememberSaveable(uiState.activeUserId) { mutableStateOf(false) }
    var driverNotificationAskedForSession by rememberSaveable(uiState.activeUserId) { mutableStateOf(false) }

    val driverNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun maybeRequestDriverNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !driverNotificationAskedForSession &&
            !SupportNotificationHelper.hasNotificationPermission(context)
        ) {
            driverNotificationAskedForSession = true
            driverNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val driverPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.markLocationPermissionRequested()
        val updatedState = currentLocationState()
        driverLocationState = updatedState
        locationServicesEnabled = isDeviceLocationServicesEnabled(context)
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            inSessionPermanentDenial = false
            resolveBestLastKnownLocation(context)?.let { coords ->
                viewModel.updateDriverCoords(coords, activeTrip?.id, fromGps = true)
            }
            coroutineScope.launch {
                resolveFreshDeviceLocation(context)?.let { fresh ->
                    viewModel.updateDriverCoords(fresh, activeTrip?.id, fromGps = true)
                }
            }
        } else {
            val rationaleAfter = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
            } ?: false
            inSessionPermanentDenial = !rationaleAfter
        }
        maybeRequestDriverNotifications()
    }

    fun launchDriverLocationRequest() {
        driverPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = currentLocationState()
                val wasGranted = driverLocationState.isGranted
                driverLocationState = updated
                locationServicesEnabled = isDeviceLocationServicesEnabled(context)
                if (updated.isGranted) {
                    inSessionPermanentDenial = false
                    if (!wasGranted || !uiState.driverHasGpsFix) {
                        resolveBestLastKnownLocation(context)?.let { coords ->
                            viewModel.updateDriverCoords(coords, activeTrip?.id, fromGps = true)
                        }
                        coroutineScope.launch {
                            resolveFreshDeviceLocation(context)?.let { fresh ->
                                viewModel.updateDriverCoords(fresh, activeTrip?.id, fromGps = true)
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.activeUserId) {
        val state = currentLocationState()
        driverLocationState = state
        locationServicesEnabled = isDeviceLocationServicesEnabled(context)
        if (state.isGranted) {
            inSessionPermanentDenial = false
            resolveBestLastKnownLocation(context)?.let { coords ->
                viewModel.updateDriverCoords(coords, activeTrip?.id, fromGps = true)
            }
            maybeRequestDriverNotifications()
        } else if (uiState.activeUserId.isNotBlank() && !driverLocationAskedForSession) {
            driverLocationAskedForSession = true
            val shouldShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            if (shouldShowRationale) {
                showDriverRationale = true
            } else {
                launchDriverLocationRequest()
            }
        }
    }

    LaunchedEffect(
        activeTrip?.id,
        activeTrip?.status,
        uiState.privacyPreferences.locationSharingConsent,
        uiState.privacyPreferences.tripOnlyPrivacyMode,
        driverLocationState
    ) {
        val trip = activeTrip
        val shouldShare = trip != null &&
            driverLocationState.isGranted &&
            uiState.privacyPreferences.locationSharingConsent &&
            (!uiState.privacyPreferences.tripOnlyPrivacyMode || trip.status == RideStatus.IN_PROGRESS)
        if (shouldShare && trip != null) {
            val summary = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}"
            RideTrackingService.startForTrip(context, trip.id, summary)
        } else {
            RideTrackingService.stopForTripEnd(context)
        }
    }

    LaunchedEffect(driverLocationState.isGranted, locationServicesEnabled, isOnline, activeTrip?.id) {
        if (!driverLocationState.isGranted || !locationServicesEnabled) return@LaunchedEffect
        var lastRemotePushMs = 0L
        var lastRemoteCoords: RideCoordinates? = null
        val intervalMs = if (isOnline || activeTrip != null) 5_000L else 15_000L
        val distanceM = if (isOnline || activeTrip != null) 10f else 25f
        observeLiveDeviceLocationFlow(
            context = context,
            minIntervalMs = intervalMs,
            minDistanceM = distanceM
        ).collect { liveCoords ->
            val tripId = activeTrip?.id
            val nowMs = System.currentTimeMillis()
            val movedMeters = lastRemoteCoords?.let {
                LaunchPolicy.distanceMeters(it, liveCoords)
            } ?: Double.MAX_VALUE
            val shouldPushRemote = tripId != null &&
                (nowMs - lastRemotePushMs >= 8_000L || movedMeters >= 20.0)
            if (shouldPushRemote) {
                lastRemotePushMs = nowMs
                lastRemoteCoords = liveCoords
                viewModel.updateDriverCoords(liveCoords, tripId, fromGps = true)
            } else {
                viewModel.updateDriverCoords(liveCoords, null, fromGps = true)
            }
        }
    }

    LaunchedEffect(isApproved, isOnline, activeTrip == null, uiState.driverSoundEnabled, currentRequest?.id, uiState.notificationSoundStyle) {
        if (isApproved && isOnline && activeTrip == null && uiState.driverSoundEnabled && currentRequest != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            com.wayfare.ride.audio.SoundAlertPlayer.playRideOfferChime(context, uiState.notificationSoundStyle)
        }
    }

    val todayEarningsZar = remember(uiState.driverEarnings, uiState.driverCompletedTrips) {
        if (uiState.driverEarnings.totalNetMinor > 0L || uiState.driverEarnings.rideCount > 0) {
            uiState.driverEarnings.totalNetMinor / 100.0
        } else {
            uiState.driverCompletedTrips.sumOf { it.fare }
        }
    }

    val isDriveMode = activeTrip?.status == RideStatus.IN_PROGRESS && !uiState.driverDriveModeMinimized
    val demandZones = remember(uiState.trips) { LaunchPolicy.buildDemandHeatZones(uiState.trips) }
    var selectedDemandZone by remember { mutableStateOf<LaunchPolicy.DemandHeatZone?>(null) }
    var focusTargetCoords by remember { mutableStateOf<RideCoordinates?>(null) }
    var focusTrigger by remember { mutableIntStateOf(0) }

    var showCorridorSheet by remember { mutableStateOf(false) }
    var showHazardSheet by remember { mutableStateOf(false) }
    var selectedHazardPin by remember { mutableStateOf<RoadHazardPin?>(null) }
    var mapPickMode by remember { mutableStateOf<String?>(null) }
    var pendingCorridorDetourKm by remember { mutableDoubleStateOf(6.0) }
    var pendingCorridorStrictFilter by remember { mutableStateOf(true) }
    var pendingHazardType by remember { mutableStateOf(HazardType.GRAVEL_MUD) }
    var pendingHazardNote by remember { mutableStateOf<String?>(null) }
    var pickerCenterCoords by remember { mutableStateOf(uiState.driverCurrentCoords) }

    LaunchedEffect(demandZones, uiState.driverHeatmapEnabled, isDriveMode) {
        if (!uiState.driverHeatmapEnabled || isDriveMode) {
            selectedDemandZone = null
        } else {
            selectedDemandZone = selectedDemandZone?.let { current ->
                demandZones.firstOrNull { it.id == current.id }
            }
        }
    }

    LaunchedEffect(uiState.roadHazards) {
        selectedHazardPin = selectedHazardPin?.let { sel ->
            uiState.roadHazards.firstOrNull { it.id == sel.id }
        }
    }

    LaunchedEffect(activeTrip?.id) {
        if (activeTrip != null) {
            mapPickMode = null
            selectedDemandZone = null
        }
    }

    val driverLiveCoords = activeTrip?.liveDriverLocation ?: uiState.driverCurrentCoords
    val repositionZone = if (activeTrip == null) uiState.driverRepositionZone else null
    val activeGtwTarget = if (activeTrip == null) uiState.driverCorridorTarget else null

    LaunchedEffect(activeGtwTarget?.destination, activeTrip == null) {
        if (activeTrip == null && activeGtwTarget != null) {
            viewModel.refreshDriverGtwRoute(activeGtwTarget, force = false)
        }
    }

    val activeNavTargetCoords = when {
        activeTrip == null -> null
        activeTrip.status == RideStatus.IN_PROGRESS ->
            activeTrip.destinationCoordinates ?: activeTrip.route?.destinationCoordinates
        else ->
            activeTrip.pickupCoordinates ?: activeTrip.route?.originCoordinates
    }
    val mapPickup = when {
        activeTrip != null -> driverLiveCoords
        isOnline && currentRequest != null -> currentRequest.pickupCoordinates
        isOnline && repositionZone != null -> driverLiveCoords
        activeGtwTarget != null -> driverLiveCoords
        else -> null
    }
    val mapDest = when {
        activeTrip != null -> activeNavTargetCoords ?: activeTrip.destinationCoordinates
        isOnline && currentRequest != null -> currentRequest.destinationCoordinates
        isOnline && repositionZone != null -> repositionZone.centroid
        activeGtwTarget != null -> activeGtwTarget.destination
        else -> null
    }
    val mapRoute = when {
        activeTrip != null && activeTrip.status == RideStatus.IN_PROGRESS ->
            uiState.driverNavRoute ?: activeTrip.route
        activeTrip != null ->
            uiState.driverNavRoute
        isOnline && currentRequest != null ->
            currentRequest.route
        isOnline && repositionZone != null ->
            uiState.driverRepositionRoute
        activeGtwTarget != null ->
            uiState.driverGtwRoute
        else -> null
    }
    val secondaryMapRoute = if (activeTrip != null && activeTrip.status != RideStatus.IN_PROGRESS) {
        activeTrip.route
    } else {
        null
    }
    val activeNavProgress = remember(driverLiveCoords, mapRoute, activeNavTargetCoords, activeTrip?.id) {
        if (activeTrip != null && mapRoute != null) {
            LaunchPolicy.computeActiveNavigationProgress(driverLiveCoords, mapRoute, activeNavTargetCoords)
        } else null
    }

    val voiceNavigator = remember(context) { TurnVoiceNavigator(context) }
    DisposableEffect(voiceNavigator) {
        onDispose { voiceNavigator.release() }
    }
    var lastSpokenNavKey by remember(activeTrip?.id, activeTrip?.status) { mutableStateOf<String?>(null) }
    LaunchedEffect(
        activeTrip?.id,
        activeTrip?.status,
        uiState.driverVoiceNavEnabled,
        activeNavProgress?.upcomingStepIndex,
        activeNavProgress?.distanceToManeuverMeters
    ) {
        if (activeTrip != null && uiState.driverVoiceNavEnabled) {
            val legKey = "${activeTrip.id}:${activeTrip.status.name}"
            val prompt = DriverMapEngine.resolveSpokenTurnPrompt(legKey, activeNavProgress, lastSpokenNavKey)
            if (prompt != null) {
                lastSpokenNavKey = prompt.second
                voiceNavigator.speak(prompt.first)
            }
        } else {
            voiceNavigator.stop()
        }
    }

    val openRequestMarkers = remember(
        isApproved,
        isOnline,
        activeTrip?.id,
        activeTrip?.status,
        openRequests,
        uiState.driverReturnRideMatches,
        driverLiveCoords,
        uiState.driverMinRatePerKmZar,
        uiState.driverCorridorTarget
    ) {
        when {
            isApproved && isOnline && activeTrip == null && openRequests.isNotEmpty() -> {
                DriverMapEngine.buildRequestMarkers(
                    trips = openRequests,
                    driverLocation = driverLiveCoords,
                    minRatePerKmZar = uiState.driverMinRatePerKmZar,
                    corridorTarget = uiState.driverCorridorTarget
                )
            }
            isApproved && isOnline && activeTrip?.status == RideStatus.IN_PROGRESS && uiState.driverReturnRideMatches.isNotEmpty() -> {
                val returnTrips = uiState.driverReturnRideMatches.map { it.trip }
                DriverMapEngine.buildRequestMarkers(
                    trips = returnTrips,
                    driverLocation = driverLiveCoords,
                    minRatePerKmZar = uiState.driverMinRatePerKmZar,
                    corridorTarget = null,
                    returnMatchTripIds = returnTrips.map { it.id }.toSet()
                )
            }
            else -> emptyList()
        }
    }

    val corridorFilteredOutCount = remember(
        uiState.driverAllOpenRequests,
        uiState.driverMinRatePerKmZar,
        uiState.driverCurrentCoords,
        uiState.driverCorridorTarget
    ) {
        val target = uiState.driverCorridorTarget ?: return@remember 0
        uiState.driverAllOpenRequests.count { trip ->
            SmartRideGuards.matchesDriverMinRateFilter(trip, uiState.driverMinRatePerKmZar) &&
                !DriverMapEngine.matchesCorridorFilter(trip, uiState.driverCurrentCoords, target)
        }
    }

    val deadheadTargetCoords = if (activeTrip == null && isOnline && currentRequest != null) {
        currentRequest.pickupCoordinates ?: currentRequest.route?.originCoordinates
    } else null

    Box(modifier = modifier.fillMaxSize()) {
        OsmRouteMap(
            modifier = Modifier.fillMaxSize(),
            pickup = mapPickup,
            destination = mapDest,
            driverLocation = driverLiveCoords,
            route = mapRoute,
            secondaryRoute = secondaryMapRoute,
            nextManeuverPoint = activeNavProgress?.upcomingStep?.location,
            followDriverLocation = activeTrip != null && uiState.driverNavFollowCamera && mapPickMode == null,
            onUserPanGesture = {
                if (activeTrip != null && uiState.driverNavFollowCamera) {
                    viewModel.setDriverNavFollowCamera(false)
                }
            },
            pickupLabel = when {
                activeTrip != null -> "You"
                isOnline && currentRequest != null -> currentRequest.pickup
                isOnline && repositionZone != null -> "You"
                activeGtwTarget != null -> "You"
                else -> null
            },
            destinationLabel = when {
                activeTrip != null -> if (activeTrip.status == RideStatus.IN_PROGRESS) activeTrip.destination else activeTrip.pickup
                isOnline && currentRequest != null -> currentRequest.destination
                isOnline && repositionZone != null -> repositionZone.label
                activeGtwTarget != null -> "GTW: ${activeGtwTarget.label}"
                else -> null
            },
            isPinPickerMode = mapPickMode != null,
            onCameraCenterChanged = { center ->
                if (mapPickMode != null) {
                    pickerCenterCoords = center
                }
            },
            isMatching = isOnline && activeTrip == null && currentRequest != null,
            controlsTopOffsetDp = if (activeTrip != null) 256 else 160,
            bottomContentPaddingDp = when {
                mapPickMode != null -> 140
                isDriveMode -> 230
                activeTrip != null -> 310
                currentRequest != null && isOnline -> 315
                else -> 195
            },
            demandZones = demandZones,
            showDemandHeatmap = uiState.driverHeatmapEnabled && !isDriveMode,
            onToggleDemandHeatmap = { viewModel.toggleDriverHeatmap() },
            onDemandZoneSelected = { zone ->
                selectedDemandZone = zone
                selectedHazardPin = null
            },
            focusTarget = focusTargetCoords,
            focusTrigger = focusTrigger,
            lowDataMode = uiState.lowDataMode,
            deadheadOrigin = if (deadheadTargetCoords != null) driverLiveCoords else null,
            deadheadTarget = deadheadTargetCoords,
            showReachRings = activeTrip == null && mapPickMode == null,
            openRequestMarkers = openRequestMarkers,
            selectedRequestId = currentRequest?.id,
            onRequestMarkerSelected = { tripId ->
                viewModel.selectDriverRequestById(tripId)
                selectedDemandZone = null
                selectedHazardPin = null
            },
            corridorTarget = uiState.driverCorridorTarget,
            roadHazards = uiState.roadHazards,
            onHazardSelected = { hazard ->
                selectedHazardPin = hazard
                selectedDemandZone = null
            },
            headingUpMode = uiState.driverHeadingUpEnabled && mapPickMode == null,
            onToggleHeadingUp = { viewModel.toggleDriverHeadingUp() },
            showOfflineLandmarks = !uiState.isNetworkConnected || uiState.lowDataMode
        )

        if (!isDriveMode && mapPickMode == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(48.dp)
                            .tactilePressScale()
                            .clip(CircleShape)
                            .clickable(onClick = onOpenAccount)
                            .testTag("driver_account_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Account and settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DriverTopOnlineBar(
                        isOnline = isOnline,
                        isApproved = isApproved,
                        soundEnabled = uiState.driverSoundEnabled,
                        todayEarningsZar = todayEarningsZar,
                        onToggleOnline = { goOnline ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (goOnline) {
                                if (driverLocationState.isGranted) {
                                    resolveBestLastKnownLocation(context)?.let { coords ->
                                        viewModel.updateDriverCoords(coords, activeTrip?.id, fromGps = true)
                                    }
                                    maybeRequestDriverNotifications()
                                } else {
                                    val shouldShowRationale = activity != null &&
                                        ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    if (shouldShowRationale) {
                                        showDriverRationale = true
                                    } else {
                                        launchDriverLocationRequest()
                                    }
                                }
                            }
                            viewModel.setDriverOnline(goOnline)
                        },
                        onToggleSound = {
                            val enabling = !uiState.driverSoundEnabled
                            viewModel.toggleDriverSound()
                            if (enabling) {
                                com.wayfare.ride.audio.SoundAlertPlayer.playRideOfferChime(
                                    context,
                                    uiState.notificationSoundStyle
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(
                    visible = !driverLocationState.isGranted || !locationServicesEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (!driverLocationState.isGranted) {
                        val effectiveBannerState = if (inSessionPermanentDenial) {
                            LocationPermissionState.DENIED_PERMANENTLY
                        } else {
                            LocationPermissionState.DENIED_CAN_RETRY
                        }
                        LocationDegradedBanner(
                            permissionState = effectiveBannerState,
                            onRetryPermission = {
                                if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                    showDriverRationale = true
                                } else {
                                    launchDriverLocationRequest()
                                }
                            },
                            onOpenSettings = { openAppLocationSettings(context) },
                            subtitleOverride = if (inSessionPermanentDenial) {
                                "Open Settings and enable Location so Vhaya shows your true driver address."
                            } else {
                                "Allow GPS so riders see your real position and pickup ETA."
                            }
                        )
                    } else {
                        LocationDegradedBanner(
                            permissionState = driverLocationState,
                            onRetryPermission = {},
                            onOpenSettings = { openDeviceLocationSourceSettings(context) },
                            titleOverride = "Device GPS is turned off",
                            subtitleOverride = "Turn on Location services in Settings to resolve your street address.",
                            actionLabelOverride = "Settings",
                            onActionOverride = { openDeviceLocationSourceSettings(context) }
                        )
                    }
                }

                if (isApproved && activeTrip == null) {
                    DriverMapFeaturePillRow(
                        corridorTarget = uiState.driverCorridorTarget,
                        corridorFilteredOutCount = corridorFilteredOutCount,
                        hazardCount = uiState.roadHazards.size,
                        onOpenCorridorSheet = { showCorridorSheet = true },
                        onOpenHazardSheet = { showHazardSheet = true },
                        activePocketCount = uiState.pocketItems.count { it.status != com.wayfare.ride.domain.PocketItemStatus.LISTED },
                        onOpenPocketSheet = { viewModel.openDriverPocketSheet() }
                    )
                }

                // Single contextual priority status slot (prevents stacked floating pills)
                if (activeTrip != null) {
                    activeTrip.let { trip ->
                        val isPickupLeg = trip.status != RideStatus.IN_PROGRESS
                        val targetLabel = if (isPickupLeg) trip.pickup else trip.destination
                        DriverTurnByTurnCard(
                            route = mapRoute,
                            isLoadingNav = uiState.driverNavLoading,
                            driverCoords = driverLiveCoords,
                            targetCoords = activeNavTargetCoords,
                            targetLabel = targetLabel,
                            isPickupLeg = isPickupLeg,
                            followCamera = uiState.driverNavFollowCamera,
                            onToggleFollowCamera = {
                                viewModel.setDriverNavFollowCamera(!uiState.driverNavFollowCamera)
                            },
                            onReroute = { viewModel.refreshDriverNavigationRoute(force = true) },
                            onOpenExternalMaps = {
                                activeNavTargetCoords?.let { openExternalNavigation(context, it, targetLabel) }
                            },
                            voiceNavEnabled = uiState.driverVoiceNavEnabled,
                            onToggleVoiceNav = { viewModel.toggleDriverVoiceNav() },
                            headingUpEnabled = uiState.driverHeadingUpEnabled,
                            onToggleHeadingUp = { viewModel.toggleDriverHeadingUp() }
                        )
                    }
                } else {
                    when {
                        isApproved && isOnline && currentRequest != null -> {
                            val pickupKm = LaunchPolicy.haversineKm(uiState.driverCurrentCoords, currentRequest.pickupCoordinates)
                            IncomingRequestAlertPill(
                                pickupKm = pickupKm,
                                soundEnabled = uiState.driverSoundEnabled
                            )
                        }
                        selectedHazardPin != null -> {
                            selectedHazardPin?.let { hazard ->
                                HazardInspectorPill(
                                    hazard = hazard,
                                    driverCoords = driverLiveCoords,
                                    onConfirm = { viewModel.confirmRoadHazard(hazard.id) },
                                    onClear = {
                                        viewModel.clearRoadHazard(hazard.id)
                                        selectedHazardPin = null
                                    },
                                    onDismiss = { selectedHazardPin = null }
                                )
                            }
                        }
                        uiState.driverHeatmapEnabled && selectedDemandZone != null -> {
                            selectedDemandZone?.let { zone ->
                                val distKm = LaunchPolicy.haversineKm(uiState.driverCurrentCoords, zone.centroid)
                                DemandZoneInspectorPill(
                                    zone = zone,
                                    distanceKm = distKm,
                                    onDriveHere = {
                                        viewModel.navigateToDemandZone(zone)
                                        focusTargetCoords = zone.centroid
                                        focusTrigger++
                                        selectedDemandZone = null
                                    },
                                    onDismiss = { selectedDemandZone = null }
                                )
                            }
                        }
                        repositionZone != null -> {
                            repositionZone.let { zone ->
                                DriverRepositionNavBanner(
                                    zone = zone,
                                    driverCoords = driverLiveCoords,
                                    route = uiState.driverRepositionRoute,
                                    isLoading = uiState.driverRepositionLoading,
                                    onOpenExternal = { openExternalNavigation(context, zone.centroid, zone.label) },
                                    onStop = { viewModel.clearRepositionZone() }
                                )
                            }
                        }
                        isApproved && activeGtwTarget != null -> {
                            DriverGtwNavBanner(
                                target = activeGtwTarget,
                                driverCoords = driverLiveCoords,
                                route = uiState.driverGtwRoute,
                                isLoading = uiState.driverGtwRouteLoading,
                                matchedCount = uiState.driverGtwMatchedCount,
                                onToggleMode = { viewModel.toggleDriverGtwStrictMode() },
                                onOpenSettings = { showCorridorSheet = true },
                                onOpenExternal = {
                                    openExternalNavigation(context, activeGtwTarget.destination, activeGtwTarget.label)
                                },
                                onClear = { viewModel.setDriverCorridorTarget(null) }
                            )
                        }
                        isApproved && uiState.driverHeatmapEnabled -> {
                            LiveDemandSummaryPill(
                                demandZones = demandZones,
                                onFocusHottest = {
                                    demandZones.firstOrNull()?.let { topZone ->
                                        selectedDemandZone = topZone
                                        focusTargetCoords = topZone.centroid
                                        focusTrigger++
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (mapPickMode != null) {
            BackHandler { mapPickMode = null }
            val isCorridorPick = mapPickMode == "corridor"
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 14.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 520.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isCorridorPick) {
                            "Pan map to choose your GTW destination"
                        } else {
                            "Pan map to pin ${pendingHazardType.label}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    OutlinedButton(
                        onClick = { mapPickMode = null },
                        modifier = Modifier.weight(1f).height(52.dp).testTag("driver_map_picker_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val chosen = pickerCenterCoords
                            val landmark = LaunchPolicy.nearestLandmarkLabel(chosen)
                            if (isCorridorPick) {
                                viewModel.setDriverCorridorTarget(
                                    DriverCorridorTarget(
                                        label = landmark,
                                        destination = chosen,
                                        maxDetourKm = pendingCorridorDetourKm,
                                        strictFilter = pendingCorridorStrictFilter
                                    )
                                )
                            } else {
                                viewModel.reportRoadHazard(
                                    type = pendingHazardType,
                                    coords = chosen,
                                    label = landmark,
                                    note = pendingHazardNote
                                )
                            }
                            mapPickMode = null
                        },
                        modifier = Modifier.weight(1.6f).height(52.dp).testTag("driver_map_picker_confirm_btn")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isCorridorPick) "Set GTW Target" else "Pin Hazard Here",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (activeTrip != null) {
            val requesterName = resolveRequesterName(uiState.userAccounts, activeTrip.riderId)
            if (isDriveMode) {
                BackHandler { viewModel.setDriverDriveModeMinimized(true) }
                val unreadChatCount = uiState.unreadChatCountByTrip[activeTrip.id] ?: 0
                DriverDriveModeOverlay(
                    trip = activeTrip,
                    requesterName = requesterName,
                    driverCoords = uiState.driverCurrentCoords,
                    navRoute = uiState.driverNavRoute ?: activeTrip.route,
                    isLoadingNav = uiState.driverNavLoading,
                    followCamera = uiState.driverNavFollowCamera,
                    privacyPreferences = uiState.privacyPreferences,
                    trustedContacts = uiState.trustedContacts,
                    viewModel = viewModel,
                    onMinimize = { viewModel.setDriverDriveModeMinimized(true) },
                    voiceNavEnabled = uiState.driverVoiceNavEnabled,
                    headingUpEnabled = uiState.driverHeadingUpEnabled,
                    onToggleVoiceNav = { viewModel.toggleDriverVoiceNav() },
                    onToggleHeadingUp = { viewModel.toggleDriverHeadingUp() },
                    onQuickReportHazard = { showHazardSheet = true },
                    unreadChatCount = unreadChatCount,
                    pendingOpenChatTripId = uiState.pendingOpenChatTripId,
                    returnMatches = uiState.driverReturnRideMatches,
                    queuedReturnTrip = uiState.driverQueuedReturnTrip,
                    onQueueReturnRide = viewModel::driverQueueReturnRide,
                    onCounterReturnRide = viewModel::driverCounterReturnRide,
                    onDismissReturnRide = viewModel::driverDismissReturnRide,
                    onCancelQueuedReturn = viewModel::driverCancelQueuedReturnRide
                )
            } else {
                val unreadChatCount = uiState.unreadChatCountByTrip[activeTrip.id] ?: 0
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                ) {
                    DriverActiveTripSheet(
                        trip = activeTrip,
                        requesterName = requesterName,
                        driverCoords = uiState.driverCurrentCoords,
                        privacyPreferences = uiState.privacyPreferences,
                        trustedContacts = uiState.trustedContacts,
                        viewModel = viewModel,
                        onExpandDriveMode = { viewModel.setDriverDriveModeMinimized(false) },
                        nowEpochMs = uiState.nowEpochMs,
                        waitArrivedAtMs = activeTrip.driverArrivedAt
                            ?: activeTrip.arrivalConfirmedAt
                            ?: uiState.fallbackArrivalTimestamps[activeTrip.id],
                        unreadChatCount = unreadChatCount,
                        pendingOpenChatTripId = uiState.pendingOpenChatTripId,
                        returnMatches = uiState.driverReturnRideMatches,
                        queuedReturnTrip = uiState.driverQueuedReturnTrip,
                        onQueueReturnRide = viewModel::driverQueueReturnRide,
                        onCounterReturnRide = viewModel::driverCounterReturnRide,
                        onDismissReturnRide = viewModel::driverDismissReturnRide,
                        onCancelQueuedReturn = viewModel::driverCancelQueuedReturnRide
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
            ) {
                val sheetKey = when {
                    !isApproved -> "gate"
                    !isOnline -> "offline"
                    currentRequest != null -> "req_${currentRequest.id}"
                    else -> "waiting"
                }
                AnimatedContent(
                    targetState = sheetKey,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(durationMillis = 200)
                        ) { it / 3 } + fadeIn()) togetherWith
                            (slideOutVertically { it / 4 } + fadeOut())
                    },
                    label = "driverBottomSheet"
                ) { key ->
                    when {
                        key == "gate" -> DriverPendingGateSheet(
                            statusLabel = driverApp?.applicationStatus ?: "draft",
                            onOpenProfile = onOpenOnboarding
                        )
                        key == "offline" -> DriverOfflineDashboardSheet(
                            vehicleLabel = "${driverApp?.vehicleColor.orEmpty()} ${driverApp?.vehicle.orEmpty()}".trim(),
                            registration = driverApp?.registration.orEmpty(),
                            todayEarningsZar = todayEarningsZar,
                            completedTripsCount = uiState.driverCompletedTrips.size,
                            weeklyPlanActive = uiState.driverWeeklyPlanActive,
                            locationGranted = driverLocationState.isGranted && locationServicesEnabled,
                            hasGpsFix = uiState.driverHasGpsFix,
                            currentAddress = uiState.driverCurrentAddress,
                            addressLoading = uiState.driverAddressLoading,
                            onEnableLocation = {
                                if (!driverLocationState.isGranted) {
                                    if (inSessionPermanentDenial) {
                                        openAppLocationSettings(context)
                                    } else if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                        showDriverRationale = true
                                    } else {
                                        launchDriverLocationRequest()
                                    }
                                } else if (!locationServicesEnabled) {
                                    openDeviceLocationSourceSettings(context)
                                }
                            },
                            onGoOnline = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (driverLocationState.isGranted) {
                                    resolveBestLastKnownLocation(context)?.let { coords ->
                                        viewModel.updateDriverCoords(coords, activeTrip?.id, fromGps = true)
                                    }
                                    maybeRequestDriverNotifications()
                                } else {
                                    val shouldShowRationale = activity != null &&
                                        ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    if (shouldShowRationale) {
                                        showDriverRationale = true
                                    } else {
                                        launchDriverLocationRequest()
                                    }
                                }
                                viewModel.setDriverOnline(true)
                            },
                            minRatePerKmZar = uiState.driverMinRatePerKmZar,
                            filteredOutCount = uiState.driverFilteredOutCount,
                            onSelectMinRatePerKm = { viewModel.setDriverMinRatePerKm(it) }
                        )
                        key.startsWith("req_") && currentRequest != null -> DriverIncomingOfferCard(
                            trip = currentRequest,
                            requesterName = resolveRequesterName(uiState.userAccounts, currentRequest.riderId),
                            driverUserId = uiState.activeUserId,
                            requestIndex = (uiState.driverRequestIndex % openRequests.size) + 1,
                            totalCount = openRequests.size,
                            nowEpochMs = uiState.nowEpochMs,
                            driverCoords = uiState.driverCurrentCoords,
                            busyAccepting = uiState.isBusy("driver_offer"),
                            corridorTarget = uiState.driverCorridorTarget,
                            onAccept = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.driverSubmitOffer(currentRequest, currentRequest.fare)
                            },
                            onCounter = { amount ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.driverSubmitOffer(currentRequest, amount)
                            },
                            onSkipNext = { viewModel.skipDriverRequest(openRequests.size) },
                            onDecline = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.declineDriverRequest(currentRequest.id)
                            }
                        )
                        else -> {
                            val hottest = demandZones.firstOrNull()
                            val hottestDist = hottest?.let {
                                LaunchPolicy.haversineKm(uiState.driverCurrentCoords, it.centroid)
                            }
                            DriverScanningWaitingSheet(
                                todayEarningsZar = todayEarningsZar,
                                completedTripsCount = uiState.driverCompletedTrips.size,
                                weeklyPlanActive = uiState.driverWeeklyPlanActive,
                                hottestZone = hottest,
                                hottestZoneDistanceKm = hottestDist,
                                onFocusHottestZone = {
                                    hottest?.let { topZone ->
                                        selectedDemandZone = topZone
                                        focusTargetCoords = topZone.centroid
                                        focusTrigger++
                                    }
                                },
                                onGoOffline = { viewModel.setDriverOnline(false) },
                                locationGranted = driverLocationState.isGranted && locationServicesEnabled,
                                hasGpsFix = uiState.driverHasGpsFix,
                                currentAddress = uiState.driverCurrentAddress,
                                minRatePerKmZar = uiState.driverMinRatePerKmZar,
                                filteredOutCount = uiState.driverFilteredOutCount,
                                onSelectMinRatePerKm = { viewModel.setDriverMinRatePerKm(it) }
                            )
                        }
                    }
                }
            }
        }

        if (showDriverRationale) {
            LocationRationaleDialog(
                isDriver = true,
                onAllow = {
                    showDriverRationale = false
                    launchDriverLocationRequest()
                },
                onDismiss = {
                    showDriverRationale = false
                    maybeRequestDriverNotifications()
                }
            )
        }

        uiState.pendingDriverSummaryTrip?.let { summaryTrip ->
            val summaryRequesterName = resolveRequesterName(uiState.userAccounts, summaryTrip.riderId)
            DriverPostTripSummarySheet(
                trip = summaryTrip,
                requesterName = summaryRequesterName,
                weeklyPlanActive = uiState.driverWeeklyPlanActive,
                existingRating = uiState.driverRiderRatings[summaryTrip.id],
                onRateRider = { stars, tags ->
                    viewModel.driverRateRider(
                        tripId = summaryTrip.id,
                        stars = stars,
                        tags = tags,
                        requesterName = summaryRequesterName
                    )
                },
                onReportIssue = { categoryKey, message, requestRefund, attachmentUri ->
                    val priority = DriverIssueCategory.fromKey(categoryKey).defaultPriority
                    viewModel.createSupportCase(
                        trip = summaryTrip,
                        category = categoryKey,
                        priority = priority,
                        message = message,
                        requestRefund = requestRefund,
                        attachmentUri = attachmentUri
                    )
                },
                onViewEarnings = {
                    viewModel.dismissDriverTripSummary()
                    viewModel.selectDriverTab(DriverTab.EARNINGS)
                },
                onDismiss = { viewModel.dismissDriverTripSummary() },
                isSendingCashChange = uiState.isBusy("cash_change_wallet"),
                cashChangeCredited = summaryTrip.id in uiState.cashChangeCreditedTripIds,
                onSendCashChangeToWallet = { amt -> viewModel.sendCashChangeToWallet(summaryTrip, amt) }
            )
        }

        if (showCorridorSheet) {
            LaunchedEffect(showCorridorSheet, driverLiveCoords) {
                viewModel.refreshNearbyPlacesIfNeeded(driverLiveCoords)
            }
            CorridorFilterSheet(
                currentTarget = uiState.driverCorridorTarget,
                onSelectTarget = { target -> viewModel.setDriverCorridorTarget(target) },
                onPickOnMap = { detourKm, strict ->
                    pendingCorridorDetourKm = detourKm
                    pendingCorridorStrictFilter = strict
                    showCorridorSheet = false
                    pickerCenterCoords = driverLiveCoords
                    mapPickMode = "corridor"
                },
                onDismiss = { showCorridorSheet = false },
                driverCoords = driverLiveCoords,
                nearbyPlaces = uiState.nearbyPlaces,
                nearbyPlacesLoading = uiState.nearbyPlacesLoading,
                placeSuggestions = uiState.placeSuggestions,
                searchingPlaces = uiState.searchingPlaces,
                onSearchPlaces = { query -> viewModel.searchPlaces(query) },
                savedDestinations = uiState.savedDestinations,
                gtwMatchedCount = uiState.driverGtwMatchedCount
            )
        }

        if (showHazardSheet) {
            HazardReportSheet(
                driverCoords = driverLiveCoords,
                driverAddress = uiState.driverCurrentAddress,
                onReportAtGps = { type, note ->
                    val label = uiState.driverCurrentAddress?.takeIf { it.isNotBlank() }
                        ?: LaunchPolicy.nearestLandmarkLabel(driverLiveCoords)
                    viewModel.reportRoadHazard(type = type, coords = driverLiveCoords, label = label, note = note)
                    showHazardSheet = false
                },
                onPickSpotOnMap = { type, note ->
                    pendingHazardType = type
                    pendingHazardNote = note
                    showHazardSheet = false
                    pickerCenterCoords = driverLiveCoords
                    mapPickMode = "hazard"
                },
                onDismiss = { showHazardSheet = false }
            )
        }

        if (uiState.isDriverPocketSheetVisible) {
            DriverPocketSheet(
                pocketRoutes = uiState.pocketRoutes,
                pocketItems = uiState.pocketItems,
                onClose = { viewModel.closeDriverPocketSheet() },
                onCreateRoute = { origin, dest, time, seats, sPrice, slots, bPrice, notes ->
                    viewModel.createDriverPocketRoute(
                        originLabel = origin,
                        originCoordinates = driverLiveCoords,
                        destinationLabel = dest,
                        destinationCoordinates = uiState.driverCorridorTarget?.destination ?: LaunchPolicy.DEFAULT_MAP_CENTER,
                        departureTimeLabel = time,
                        departureTimestampMs = System.currentTimeMillis() + 3600_000L * 4,
                        totalSeats = seats,
                        seatPriceZar = sPrice,
                        totalParcelSlots = slots,
                        baseParcelPriceZar = bPrice,
                        notes = notes
                    )
                },
                onClaimItem = { itemId -> viewModel.claimPocketItem(itemId) },
                onUpdateItemStatus = { itemId, status -> viewModel.updatePocketItemStatus(itemId, status) },
                onNotifySendersAndDepart = { routeId -> viewModel.notifySendersAndDepart(routeId) }
            )
        }

    }
}

@Composable
private fun DriverTopOnlineBar(
    isOnline: Boolean,
    isApproved: Boolean,
    soundEnabled: Boolean,
    todayEarningsZar: Double,
    onToggleOnline: (Boolean) -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val pulseTransition = rememberInfiniteTransition(label = "onlineDotPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isOnline) colorScheme.primary else colorScheme.outline,
        label = "onlineBorder"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(if (isOnline) dotScale else 1f)
                        .clip(CircleShape)
                        .background(if (isOnline) wayfareColors.positive else colorScheme.outline)
                )
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                MonoBadge(
                    text = LaunchPolicy.formatZar(todayEarningsZar),
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleSound,
                    modifier = Modifier.size(48.dp).testTag("driver_sound_toggle")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Toggle alert sound",
                        tint = if (soundEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Switch(
                    checked = isOnline,
                    onCheckedChange = onToggleOnline,
                    enabled = isApproved,
                    colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.primary),
                    modifier = Modifier.testTag("driver_online_switch")
                )
            }
        }
    }
}

@Composable
private fun IncomingRequestAlertPill(
    pickupKm: Double?,
    soundEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = wayfareColors.routeHighlight,
                modifier = Modifier.size(16.dp)
            )
            val distPart = pickupKm?.let { " · ${"%.1f".format(it)} km away" }.orEmpty()
            Text(
                text = "New ride$distPart · ${if (soundEnabled) "Alert chime ON" else "Muted"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun LiveDemandSummaryPill(
    demandZones: List<LaunchPolicy.DemandHeatZone>,
    onFocusHottest: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val openCount = demandZones.sumOf { it.liveOpenCount }
    val hottest = demandZones.firstOrNull()
    val summaryText = if (hottest == null) {
        "Demand Heatmap ON · No ride requests yet"
    } else if (openCount > 0) {
        "${demandZones.size} hot zones · $openCount live · Tap ${hottest.label}"
    } else {
        "${demandZones.size} active zones · Top: ${hottest.label}"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, wayfareColors.heatMid.copy(alpha = 0.45f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = hottest != null, onClick = onFocusHottest)
            .testTag("driver_demand_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = if (openCount > 0) wayfareColors.heatHigh else wayfareColors.heatLow,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DemandZoneInspectorPill(
    zone: LaunchPolicy.DemandHeatZone,
    distanceKm: Double?,
    onDriveHere: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val tierColor = when (zone.tier) {
        LaunchPolicy.DemandTier.HIGH -> wayfareColors.heatHigh
        LaunchPolicy.DemandTier.MEDIUM -> wayfareColors.heatMid
        LaunchPolicy.DemandTier.LOW -> wayfareColors.heatLow
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.65f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("driver_demand_inspector")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = tierColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = tierColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val distLabel = distanceKm?.let { " · ${"%.1f".format(it)} km" }.orEmpty()
                    Text(
                        text = "${zone.label} (${zone.tier.label})$distLabel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val farePart = if (zone.avgFareZar > 0.0) " · Avg ${LaunchPolicy.formatZar(zone.avgFareZar)}" else ""
                    Text(
                        text = "${zone.liveOpenCount} live · ${zone.requestCount} recent requests$farePart",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).testTag("driver_demand_inspector_close")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close hotspot details",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Button(
                onClick = onDriveHere,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(end = 6.dp)
                    .testTag("driver_demand_drive_here_btn")
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Drive here (scan en route)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriverPendingGateSheet(
    statusLabel: String,
    onOpenProfile: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
            Text("Finish verification to go online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Application status: ${statusLabel.replace("_", " ")} · Submit your application and email your documents to ${LaunchPolicy.DRIVER_DOCS_EMAIL} for manual review.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Open driver profile")
            }
        }
    }
}
