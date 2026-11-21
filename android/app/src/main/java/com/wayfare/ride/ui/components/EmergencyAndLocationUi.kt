package com.wayfare.ride.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.LocationPermissionState
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.ui.theme.LocalWayfareColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun isDeviceLocationServicesEnabled(context: Context): Boolean {
    return runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        LocationManagerCompat.isLocationEnabled(manager)
    }.getOrDefault(true)
}

fun openDeviceLocationSourceSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(intent) }.isFailure) {
        openAppLocationSettings(context)
    }
}

fun evaluateLocationPermissionState(
    context: Context,
    shouldShowRationale: Boolean,
    hasRequestedBefore: Boolean
): LocationPermissionState {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fine) return LocationPermissionState.GRANTED_FINE

    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (coarse) return LocationPermissionState.GRANTED_COARSE

    if (shouldShowRationale) return LocationPermissionState.DENIED_CAN_RETRY
    if (hasRequestedBefore) return LocationPermissionState.DENIED_PERMANENTLY
    return LocationPermissionState.DENIED_CAN_RETRY
}

fun openAppLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@SuppressLint("MissingPermission")
fun resolveDeviceOrFallbackLocation(context: Context): RideCoordinates {
    return resolveBestLastKnownLocation(context) ?: LaunchPolicy.DEFAULT_MAP_CENTER
}

private fun availableLocationProviders(manager: LocationManager, fineGranted: Boolean): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (runCatching { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.FUSED_PROVIDER)
        }
    }
    if (fineGranted && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
        add(LocationManager.GPS_PROVIDER)
    }
    if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
        add(LocationManager.NETWORK_PROVIDER)
    }
}

@SuppressLint("MissingPermission")
fun resolveBestLastKnownLocation(context: Context): RideCoordinates? {
    return try {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (fine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val freshest: Location? = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { loc -> LaunchPolicy.isValidDeviceCoordinate(RideCoordinates(loc.latitude, loc.longitude)) }
            .maxByOrNull { it.time }

        freshest?.let { loc ->
            val coords = RideCoordinates(loc.latitude, loc.longitude)
            if (LaunchPolicy.isValidDeviceCoordinate(coords)) coords else null
        }
    } catch (_: Throwable) {
        null
    }
}

@SuppressLint("MissingPermission")
suspend fun resolveFreshDeviceLocation(
    context: Context,
    timeoutMs: Long = 3_500L
): RideCoordinates? {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return resolveBestLastKnownLocation(context)
    val providers = availableLocationProviders(manager, fine)
    if (providers.isEmpty()) return resolveBestLastKnownLocation(context)

    for (provider in providers) {
        val activeFix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<RideCoordinates?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        val coords = location?.let { RideCoordinates(it.latitude, it.longitude) }
                            ?.takeIf { LaunchPolicy.isValidDeviceCoordinate(it) }
                        if (cont.isActive) cont.resume(coords)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (activeFix != null) return activeFix
    }

    return resolveBestLastKnownLocation(context)
}

@SuppressLint("MissingPermission")
fun observeLiveDeviceLocationFlow(
    context: Context,
    minIntervalMs: Long = 5_000L,
    minDistanceM: Float = 10f
): Flow<RideCoordinates> = callbackFlow {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        close()
        return@callbackFlow
    }

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (manager == null) {
        close()
        return@callbackFlow
    }

    resolveBestLastKnownLocation(context)?.let { trySend(it) }

    val targetProviders = availableLocationProviders(manager, fine)
    val cancellationSignals = mutableListOf<CancellationSignal>()
    targetProviders.forEach { provider ->
        val signal = CancellationSignal()
        cancellationSignals += signal
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                ContextCompat.getMainExecutor(context)
            ) { location ->
                val coords = location?.let { RideCoordinates(it.latitude, it.longitude) }
                if (coords != null && LaunchPolicy.isValidDeviceCoordinate(coords)) {
                    trySend(coords)
                }
            }
        }
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val coords = RideCoordinates(location.latitude, location.longitude)
            if (LaunchPolicy.isValidDeviceCoordinate(coords)) {
                trySend(coords)
            }
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    targetProviders.forEach { provider ->
        runCatching {
            manager.requestLocationUpdates(
                provider,
                minIntervalMs,
                minDistanceM,
                listener,
                Looper.getMainLooper()
            )
        }
    }

    awaitClose {
        cancellationSignals.forEach { it.cancel() }
        runCatching { manager.removeUpdates(listener) }
    }
}

fun alertTrustedContacts(
    context: Context,
    trip: RideTrip,
    contacts: List<TrustedContact>,
    currentCoords: RideCoordinates? = trip.liveDriverLocation ?: trip.pickupCoordinates
): Boolean {
    if (contacts.isEmpty()) return false
    val driverInfo = trip.driverProfile?.let { "${it.name} · ${it.vehicleColor} ${it.vehicle} (${it.registration})" }
        ?: "Driver assignment in progress"
    val coordPart = currentCoords?.let {
        "Live map: https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=16/${it.latitude}/${it.longitude}"
    }.orEmpty()

    val message = buildString {
        appendLine("VHAYA SAFETY ALERT")
        appendLine("Trip: ${trip.pickup.substringBefore(" ·")} -> ${trip.destination.substringBefore(" ·")}")
        appendLine("Status: ${trip.status.label}")
        appendLine("Driver: $driverInfo")
        trip.tripPin?.let { appendLine("Pickup PIN: $it") }
        if (coordPart.isNotBlank()) appendLine(coordPart)
    }.trim()

    val recipients = contacts.joinToString(",") { it.phone.trim() }
    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$recipients")).apply {
        putExtra("sms_body", message)
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return if (smsIntent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(smsIntent) }.isSuccess
    } else {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(shareIntent, "Alert trusted contacts").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(chooser)
        }.isSuccess
    }
}

fun sendSmsMessage(
    context: Context,
    phoneNumber: String?,
    message: String
): Boolean {
    val cleanPhone = phoneNumber.orEmpty().trim()
    val smsUri = if (cleanPhone.isNotBlank()) Uri.parse("smsto:${Uri.encode(cleanPhone)}") else Uri.parse("smsto:")
    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
        putExtra("sms_body", message)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return if (smsIntent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(smsIntent) }.isSuccess
    } else {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(shareIntent, "Share trip details").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(chooser) }.isSuccess
    }
}

fun dialPhoneNumber(
    context: Context,
    phoneNumber: String?
): Boolean {
    val cleanPhone = phoneNumber.orEmpty().trim()
    if (cleanPhone.isBlank()) return false
    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return runCatching { context.startActivity(dialIntent) }.isSuccess
}

@Composable
fun EmergencyActionPanel(
    trip: RideTrip,
    trustedContactsCount: Int,
    onCall112: () -> Unit,
    onAlertContacts: () -> Unit,
    onReportSafety: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("emergency_action_panel")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Emergency escalation",
                    tint = colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Live trip safety · ${trip.status.label}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.error
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onCall112,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("emergency_panel_call_112")
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("112", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onAlertContacts,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("emergency_panel_alert_contacts")
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (trustedContactsCount > 0) "Alert ($trustedContactsCount)" else "Contacts",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onReportSafety,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                        .testTag("emergency_panel_report_safety")
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Issue", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun LocationRationaleDialog(
    isDriver: Boolean,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Enable location access") },
        text = {
            Text(
                if (isDriver) {
                    "Vhaya uses your live location while you are online or on an active trip to show nearby ride requests, verify 150 m pickup arrival, and keep riders updated."
                } else {
                    "Vhaya uses your device location to set accurate GPS pickup pins and show nearby drivers. You can still search or drag the map pin if you keep GPS off."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier.testTag("location_rationale_allow")
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("location_rationale_dismiss")
            ) {
                Text("Not now")
            }
        }
    )
}

@Composable
fun LocationDegradedBanner(
    permissionState: LocationPermissionState,
    onRetryPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualFallback: (() -> Unit)? = null,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    actionLabelOverride: String? = null,
    onActionOverride: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isPermanent = permissionState == LocationPermissionState.DENIED_PERMANENTLY
    val titleText = titleOverride ?: if (isPermanent) "Location blocked in Settings" else "Location access is off"
    val subtitleText = subtitleOverride ?: if (isPermanent) {
        "Open app settings to restore GPS, or search pickup manually."
    } else {
        "Allow GPS or set your pickup manually on the map."
    }
    val actionLabel = actionLabelOverride ?: if (isPermanent) "Settings" else "Allow"
    val actionClick = onActionOverride ?: if (isPermanent) onOpenSettings else onRetryPermission

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("location_degraded_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            if (onManualFallback != null) {
                TextButton(
                    onClick = onManualFallback,
                    modifier = Modifier.height(48.dp).testTag("location_manual_fallback_btn")
                ) {
                    Text("Set")
                }
            }
            TextButton(
                onClick = actionClick,
                modifier = Modifier.height(48.dp).testTag("location_action_btn")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LiveTrackingStatusPill(
    isActive: Boolean,
    consentEnabled: Boolean,
    tripOnlyMode: Boolean,
    onStopSharing: () -> Unit,
    onEnableSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) wayfareColors.brandHighlight else colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_tracking_status_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when {
                        isActive -> "Background GPS sharing active"
                        !consentEnabled -> "Location sharing paused by you"
                        tripOnlyMode -> "Trip-only privacy · resumes when trip starts"
                        else -> "Foreground location standby"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = if (isActive || consentEnabled) onStopSharing else onEnableSharing,
                modifier = Modifier.height(48.dp).testTag("live_tracking_toggle_btn")
            ) {
                Text(if (isActive || consentEnabled) "Pause" else "Resume")
            }
        }
    }
}

@Composable
fun OfflineReconnectingBanner(
    pendingOfflineCount: Int,
    syncError: String?,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, wayfareColors.warning.copy(alpha = 0.65f)),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_reconnecting_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = "Offline status",
                tint = wayfareColors.warning,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                val title = if (pendingOfflineCount > 0) {
                    "Connecting to Vhaya · $pendingOfflineCount queued"
                } else {
                    "Connecting to Vhaya..."
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = syncError ?: "Active trips remain visible and queued actions auto-send.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onSyncNow,
                modifier = Modifier.height(48.dp).testTag("offline_sync_now_btn")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sync")
            }
        }
    }
}
