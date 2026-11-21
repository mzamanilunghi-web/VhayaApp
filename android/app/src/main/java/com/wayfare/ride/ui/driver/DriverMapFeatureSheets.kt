package com.wayfare.ride.ui.driver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.DriverMapEngine
import com.wayfare.ride.domain.HazardType
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NearbyPlaceCategory
import com.wayfare.ride.domain.OfflineCorridorPack
import com.wayfare.ride.domain.OfflineCorridorPackStatus
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RoadHazardPin
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun DriverMapFeaturePillRow(
    corridorTarget: DriverCorridorTarget?,
    corridorFilteredOutCount: Int,
    hazardCount: Int,
    onOpenCorridorSheet: () -> Unit,
    onOpenHazardSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (corridorTarget != null) wayfareColors.emeraldContainer else colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(
                1.dp,
                if (corridorTarget != null) wayfareColors.positive.copy(alpha = 0.65f) else colorScheme.outlineVariant
            ),
            shadowElevation = 3.dp,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .tactilePressScale()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenCorridorSheet)
                .testTag("driver_corridor_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = "Heading Home corridor filter",
                    tint = if (corridorTarget != null) wayfareColors.positive else colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                val label = if (corridorTarget != null) {
                    val hiddenNote = if (corridorFilteredOutCount > 0) " · $corridorFilteredOutCount off-route" else ""
                    "To ${corridorTarget.label.substringBefore(" ·")}$hiddenNote"
                } else {
                    "Heading Home"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (corridorTarget != null) wayfareColors.positive else colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (hazardCount > 0) wayfareColors.warning.copy(alpha = 0.14f) else colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(
                1.dp,
                if (hazardCount > 0) wayfareColors.warning.copy(alpha = 0.55f) else colorScheme.outlineVariant
            ),
            shadowElevation = 3.dp,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .tactilePressScale()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenHazardSheet)
                .testTag("driver_hazard_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Report rural road hazard",
                    tint = if (hazardCount > 0) wayfareColors.warning else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (hazardCount > 0) "Hazards ($hazardCount)" else "Report hazard",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (hazardCount > 0) wayfareColors.warning else colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DriverRepositionNavBanner(
    zone: LaunchPolicy.DemandHeatZone,
    driverCoords: RideCoordinates,
    route: RideRoute?,
    isLoading: Boolean,
    onOpenExternal: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val distKm = (route?.distanceMeters?.div(1000.0))
        ?: LaunchPolicy.haversineKm(driverCoords, zone.centroid)
        ?: 0.0
    val etaMins = route?.let { ceil(it.durationSeconds / 60.0).toInt().coerceAtLeast(1) }
        ?: (distKm * 2.0).roundToInt().coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.primary,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, wayfareColors.routeHighlight.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("driver_reposition_banner")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isLoading) {
                LinearProgressIndicator(
                    color = wayfareColors.routeHighlight,
                    trackColor = colorScheme.onPrimary.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.onPrimary.copy(alpha = 0.16f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = "Driving to demand zone",
                            tint = wayfareColors.routeHighlight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Repositioning to ${zone.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$etaMins min (${"%.1f".format(Locale.US, distKm)} km) · Scanning offers en route",
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onPrimary.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onOpenExternal,
                    modifier = Modifier.size(48.dp).testTag("driver_reposition_external_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open zone directions in external maps",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp).testTag("driver_reposition_stop_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Stop routing to demand zone",
                        tint = wayfareColors.routeHighlight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HazardInspectorPill(
    hazard: RoadHazardPin,
    driverCoords: RideCoordinates,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val isCritical = hazard.type == HazardType.FLOODED_DRIFT || hazard.type == HazardType.ROADBLOCK
    val accent = if (isCritical) wayfareColors.crimsonAccent else wayfareColors.warning
    val distKm = LaunchPolicy.haversineKm(driverCoords, hazard.coordinates)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.65f)),
        shadowElevation = 7.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("driver_hazard_inspector")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val distNote = distKm?.let { " · ${"%.1f".format(Locale.US, it)} km" }.orEmpty()
                    Text(
                        text = "${hazard.type.label}$distNote",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1
                    )
                    Text(
                        text = "${hazard.label} · ${hazard.confirmations} confirm${if (hazard.confirmations == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).testTag("hazard_inspector_close")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss hazard warning", modifier = Modifier.size(18.dp))
                }
            }
            hazard.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = "\"$note\" — ${hazard.reportedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp)
            ) {
                OutlinedButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("hazard_confirm_btn")
                ) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Still there", maxLines = 1)
                }
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wayfareColors.positive,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("hazard_clear_btn")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Road clear", maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CorridorFilterSheet(
    currentTarget: DriverCorridorTarget?,
    onSelectTarget: (DriverCorridorTarget?) -> Unit,
    onPickOnMap: (detourKm: Double) -> Unit,
    onDismiss: () -> Unit,
    driverCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    nearbyPlaces: List<PlaceSearchSuggestion> = emptyList(),
    nearbyPlacesLoading: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var selectedDetourKm by remember(currentTarget) {
        mutableDoubleStateOf(currentTarget?.maxDetourKm ?: 6.0)
    }
    val corridorChoices = remember(nearbyPlaces, driverCoords) {
        if (nearbyPlaces.isNotEmpty()) {
            nearbyPlaces
        } else {
            LaunchPolicy.nearbyCuratedOfflineFallback(
                origin = driverCoords,
                category = NearbyPlaceCategory.ALL,
                maxRadiusKm = 45.0,
                limit = 15
            )
        }
    }

    BackHandler { onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Heading Home / Corridor Filter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Show only ride offers taking passengers along your way home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                if (currentTarget != null) {
                    TextButton(
                        onClick = {
                            onSelectTarget(null)
                            onDismiss()
                        },
                        modifier = Modifier.height(48.dp).testTag("corridor_clear_btn")
                    ) {
                        Text("Clear", color = colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Max detour off your route",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4.0 to "Tight (4 km)", 6.0 to "Standard (6 km)", 10.0 to "Wide (10 km)").forEach { (km, label) ->
                        val selected = selectedDetourKm == km
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDetourKm = km
                                if (currentTarget != null) {
                                    onSelectTarget(currentTarget.copy(maxDetourKm = km))
                                }
                            },
                            label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = wayfareColors.emeraldContainer,
                                selectedLabelColor = wayfareColors.positive
                            ),
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { onPickOnMap(selectedDetourKm) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("corridor_pick_on_map_btn")
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Drop custom pin on map", fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nearby destinations & hubs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant
                )
                if (nearbyPlacesLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (corridorChoices.isEmpty()) {
                Text(
                    text = if (nearbyPlacesLoading) "Looking for places near your location..." else "Tap Drop custom pin on map above to set your corridor destination.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(corridorChoices, key = { "${it.id}-${it.label}" }) { place ->
                        val shortName = place.label.substringBefore(" ·")
                        val areaHint = place.label.substringAfter(" ·", "").takeIf { it.isNotBlank() }
                        val distKm = place.distanceMeters?.div(1000.0)
                            ?: LaunchPolicy.haversineKm(driverCoords, place.coordinates)
                        val subtitle = listOfNotNull(
                            areaHint,
                            distKm?.let { "${"%.1f".format(Locale.US, it)} km away" }
                        ).joinToString(" · ").ifBlank { "Nearby destination" }
                        val isActive = currentTarget?.label == shortName || currentTarget?.label == place.label
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) wayfareColors.emeraldContainer else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isActive) wayfareColors.positive else colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectTarget(
                                        DriverCorridorTarget(
                                            label = shortName,
                                            destination = place.coordinates,
                                            maxDetourKm = selectedDetourKm
                                        )
                                    )
                                    onDismiss()
                                }
                                .testTag("corridor_hub_${place.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shortName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected corridor",
                                        tint = wayfareColors.positive
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardReportSheet(
    driverCoords: RideCoordinates,
    driverAddress: String?,
    onReportAtGps: (HazardType, String?) -> Unit,
    onPickSpotOnMap: (HazardType, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var selectedType by remember { mutableStateOf(HazardType.GRAVEL_MUD) }
    var noteInput by remember { mutableStateOf("") }

    BackHandler { onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Warn drivers of a road hazard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Near: ${driverAddress?.takeIf { it.isNotBlank() } ?: LaunchPolicy.nearestLandmarkLabel(driverCoords)}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            HazardType.entries.forEach { type ->
                val isSelected = selectedType == type
                val isCritical = type == HazardType.FLOODED_DRIFT || type == HazardType.ROADBLOCK
                val accent = if (isCritical) wayfareColors.crimsonAccent else wayfareColors.warning
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) accent.copy(alpha = 0.14f) else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) accent else colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedType = type }
                        .testTag("hazard_type_${type.key}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(type.detailHint, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = accent)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it.take(120) },
                label = { Text("Optional details (e.g. only 4x4 / deep water)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("hazard_note_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onPickSpotOnMap(selectedType, noteInput.takeIf { it.isNotBlank() }) },
                    modifier = Modifier.weight(1f).height(52.dp).testTag("hazard_pick_map_btn")
                ) {
                    Text("Pick on map")
                }
                Button(
                    onClick = { onReportAtGps(selectedType, noteInput.takeIf { it.isNotBlank() }) },
                    modifier = Modifier.weight(1f).height(52.dp).testTag("hazard_drop_gps_btn")
                ) {
                    Text("Drop at GPS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapPacksSheet(
    cachedPacks: List<OfflineCorridorPackStatus>,
    downloadingPackId: String?,
    progress: Pair<Int, Int>?,
    cacheStats: Pair<Int, Long>,
    onDownloadPack: (OfflineCorridorPack) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val (tileCount, totalBytes) = cacheStats
    val sizeMb = totalBytes / (1024.0 * 1024.0)

    BackHandler { onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline Corridor Map Packs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$tileCount map tiles stored on device (${"%.1f".format(Locale.US, sizeMb)} MB)",
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                if (tileCount > 0) {
                    IconButton(
                        onClick = onClearCache,
                        modifier = Modifier.size(48.dp).testTag("offline_maps_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Clear offline tile cache",
                            tint = colorScheme.error
                        )
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(DriverMapEngine.PRESET_CORRIDOR_PACKS, key = { it.id }) { pack ->
                    val status = cachedPacks.firstOrNull { it.packId == pack.id }
                    val isDownloading = downloadingPackId == pack.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (status?.isComplete == true) {
                            wayfareColors.emeraldContainer.copy(alpha = 0.7f)
                        } else {
                            colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (status?.isComplete == true) wayfareColors.positive.copy(alpha = 0.5f) else colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("offline_pack_${pack.id}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = pack.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = pack.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (isDownloading) {
                                    val (done, total) = progress ?: (0 to 1)
                                    MonoBadge(text = "$done/$total")
                                } else {
                                    OutlinedButton(
                                        onClick = { onDownloadPack(pack) },
                                        enabled = downloadingPackId == null,
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(48.dp).testTag("download_pack_${pack.id}")
                                    ) {
                                        Text(
                                            text = if (status?.isComplete == true) "Ready" else "Save",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (isDownloading) {
                                val (done, total) = progress ?: (0 to 1)
                                LinearProgressIndicator(
                                    progress = { (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            } else if (status != null) {
                                Text(
                                    text = "${status.downloadedTiles}/${status.totalTiles} tiles + corridor road routes cached offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = wayfareColors.positive
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
