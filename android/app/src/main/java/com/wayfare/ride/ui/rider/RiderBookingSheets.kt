package com.wayfare.ride.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.AppLanguage
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.OutboundDriverApproachingHint
import com.wayfare.ride.domain.NearbyPlaceCategory
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RuralRunPreset
import com.wayfare.ride.domain.RuralStrings
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.service.FareEstimateBreakdown
import com.wayfare.ride.service.FareEstimateSource
import com.wayfare.ride.service.FareEstimationService
import com.wayfare.ride.ui.BookingDraft
import com.wayfare.ride.ui.WayfareUiState
import java.util.Locale
import kotlin.math.roundToInt
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareEmptyState
import com.wayfare.ride.ui.components.shareTextMessage
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.delay

data class QuickPlaceShortcut(
    val label: String,
    val coordinates: RideCoordinates
)

@Composable
fun CompactWhereToSheet(
    riderName: String,
    walletBalanceMinor: Long,
    savedDestinations: List<SavedDestination>,
    recentDestinations: List<QuickPlaceShortcut>,
    nearbyPlaces: List<PlaceSearchSuggestion> = emptyList(),
    nearbyPlacesLoading: Boolean = false,
    onWhereToClick: () -> Unit,
    onToggleScheduleLater: () -> Unit,
    onOpenWallet: () -> Unit,
    onSelectQuickPlace: (String, RideCoordinates) -> Unit,
    onPinOnMap: () -> Unit,
    onOpenSavedPlaces: () -> Unit,
    appLanguage: AppLanguage = AppLanguage.ENGLISH,
    onSelectRuralPreset: (RuralRunPreset) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val strings = remember(appLanguage) { RuralStrings.forLanguage(appLanguage) }
    val firstName = remember(riderName) {
        riderName.trim().substringBefore(" ").takeIf { it.isNotBlank() }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colorScheme.outline)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (firstName != null) "${strings.readyToRollPrefix}, $firstName" else strings.readyGeneric,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.chooseDestinationHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onPinOnMap)
                            .testTag("home_pin_shortcut")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Map,
                                contentDescription = "Pin on map",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pin",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenWallet)
                            .testTag("home_wallet_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = "Open wallet",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = LaunchPolicy.formatZarMinor(walletBalanceMinor),
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .tactilePressScale()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onWhereToClick)
                        .testTag("where_to_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search destination",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = strings.whereToLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(56.dp)
                        .tactilePressScale()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleScheduleLater)
                        .testTag("schedule_later_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Schedule ride",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.scheduleLaterLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            val quickNearbyPlaces = remember(savedDestinations, recentDestinations, nearbyPlaces) {
                val usedLabels = (savedDestinations.map { it.label } + recentDestinations.map { it.label })
                    .mapTo(mutableSetOf()) { it.substringBefore(" ·").substringBefore(",").trim().lowercase() }
                nearbyPlaces.filter { place ->
                    val shortName = place.label.substringBefore(" ·").substringBefore(",").trim().lowercase()
                    shortName.isNotBlank() && usedLabels.none { it == shortName }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quick destinations & rural runs",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (savedDestinations.isNotEmpty()) {
                        Text(
                            text = "${savedDestinations.size} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenSavedPlaces)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("home_saved_shortcut")
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("rural_presets_row")
                ) {
                    items(savedDestinations, key = { "quick-saved-${it.id}" }) { saved ->
                        QuickDestinationPill(
                            icon = Icons.Filled.Bookmark,
                            label = saved.label.substringBefore(","),
                            subtitle = "Saved",
                            onClick = {
                                onSelectQuickPlace(saved.label, RideCoordinates(saved.latitude, saved.longitude))
                            }
                        )
                    }
                    items(recentDestinations, key = { "quick-recent-${it.label}" }) { recent ->
                        QuickDestinationPill(
                            icon = Icons.Filled.History,
                            label = recent.label.substringBefore(","),
                            subtitle = "Recent",
                            onClick = { onSelectQuickPlace(recent.label, recent.coordinates) }
                        )
                    }
                    items(RuralRunPreset.entries, key = { "rural-${it.key}" }) { preset ->
                        val presetIcon = when (preset) {
                            RuralRunPreset.CLINIC_RUN -> Icons.Filled.LocalHospital
                            RuralRunPreset.PENSION_DAY -> Icons.Filled.Groups
                            RuralRunPreset.TOWN_GROCERY -> Icons.Filled.LocalShipping
                            RuralRunPreset.FAMILY_PARCEL -> Icons.Filled.Inventory2
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.primaryContainer.copy(alpha = 0.42f),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .height(50.dp)
                                .tactilePressScale()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectRuralPreset(preset) }
                                .testTag("rural_preset_${preset.key}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = presetIcon,
                                    contentDescription = preset.title,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    items(quickNearbyPlaces.take(5), key = { "quick-nearby-${it.id}-${it.label}" }) { place ->
                        val shortName = place.label.substringBefore(" ·").substringBefore(",")
                        val distLabel = formatPlaceDistance(place.distanceMeters) ?: nearbyPlaceTypeLabel(place.category)
                        QuickDestinationPill(
                            icon = nearbyPlaceIcon(place.category),
                            label = shortName,
                            subtitle = distLabel,
                            onClick = { onSelectQuickPlace(place.label, place.coordinates) }
                        )
                    }
                    if (quickNearbyPlaces.isEmpty() && nearbyPlacesLoading) {
                        item(key = "quick-nearby-loading") {
                            QuickDestinationPill(
                                icon = Icons.Filled.GpsFixed,
                                label = "Nearby places",
                                subtitle = "Searching...",
                                onClick = onWhereToClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDestinationPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SearchDestinationSheet(
    draft: BookingDraft,
    uiState: WayfareUiState,
    recentDestinations: List<QuickPlaceShortcut>,
    onBack: () -> Unit,
    onUseGps: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPickOnMap: (Boolean) -> Unit,
    onSelectPlace: (String, RideCoordinates, Boolean) -> Unit,
    onSavePlace: (String, RideCoordinates) -> Unit,
    onClearPreset: () -> Unit = {},
    onSelectNearbyCategory: (NearbyPlaceCategory) -> Unit = {},
    onRetryNearby: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var editingPickup by remember(draft.pickingForPickup) { mutableStateOf(draft.pickingForPickup) }
    var query by remember { mutableStateOf("") }
    var isDebouncingSearch by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        onQueryChange("")
        if (query.trim().length >= 3) {
            isDebouncingSearch = true
            delay(280L)
            onQueryChange(query)
            isDebouncingSearch = false
        } else {
            isDebouncingSearch = false
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            var showSavedList by remember { mutableStateOf(true) }
            var showRecentList by remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("search_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (editingPickup) "Set pickup point" else "Plan your route",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Connected A -> B Route Spine (Pickup -> Destination)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorScheme.primary))
                    Box(Modifier.width(2.dp).height(34.dp).background(colorScheme.outline))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(wayfareColors.destinationMarker))
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (editingPickup) colorScheme.primaryContainer.copy(alpha = 0.5f) else colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (editingPickup) colorScheme.primary else colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { editingPickup = true; query = "" }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = draft.pickupLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onUseGps,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.GpsFixed, contentDescription = "GPS", modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("GPS", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(if (editingPickup) "Search pickup address..." else "Where to? Search any address...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onPickOnMap(editingPickup) },
                                modifier = Modifier.size(48.dp).testTag("pick_on_map_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Map,
                                    contentDescription = "Pin on map",
                                    tint = colorScheme.primary
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("destination_search_input")
                    )
                }
            }

            if (uiState.searchingPlaces || isDebouncingSearch) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (
                uiState.placeSearchError != null &&
                !uiState.searchingPlaces &&
                query.trim().length >= 3
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Address lookup is offline",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Pick a nearby place below, retry, or tap Pin on map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { onQueryChange(query) }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            if (draft.activePreset != null && !editingPickup) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = wayfareColors.brandHighlight,
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("active_preset_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${draft.activePreset.title} preset active",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = draft.activePreset.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = onClearPreset,
                            modifier = Modifier.size(48.dp).testTag("clear_preset_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear preset",
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (query.trim().length < 3) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("nearby_category_chips")
                ) {
                    items(NearbyPlaceCategory.entries, key = { it.key }) { cat ->
                        val selected = uiState.selectedNearbyCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectNearbyCategory(cat) },
                            leadingIcon = {
                                Icon(
                                    imageVector = nearbyCategoryChipIcon(cat),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = cat.chipLabel,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primaryContainer,
                                selectedLabelColor = colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = colorScheme.primary
                            ),
                            modifier = Modifier.height(48.dp).testTag("nearby_category_${cat.key}")
                        )
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
                if (query.trim().length < 3 && !editingPickup && uiState.savedDestinations.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showSavedList = !showSavedList }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Saved places (${uiState.savedDestinations.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (showSavedList) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showSavedList) "Collapse saved places" else "Expand saved places",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (showSavedList) {
                        items(uiState.savedDestinations, key = { "saved-${it.id}" }) { saved ->
                            PlaceRow(
                                title = saved.label,
                                subtitle = "Saved place",
                                isSaved = true,
                                leadingIcon = Icons.Filled.Bookmark,
                                onClick = { onSelectPlace(saved.label, RideCoordinates(saved.latitude, saved.longitude), editingPickup) },
                                onToggleSave = null
                            )
                        }
                    }
                }
                if (query.trim().length < 3 && !editingPickup && recentDestinations.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showRecentList = !showRecentList }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Recent rides (${recentDestinations.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (showRecentList) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showRecentList) "Collapse recent rides" else "Expand recent rides",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (showRecentList) {
                        items(recentDestinations, key = { "recent-${it.label}" }) { recent ->
                            PlaceRow(
                                title = recent.label,
                                subtitle = "Recent destination",
                                isSaved = false,
                                leadingIcon = Icons.Filled.History,
                                onClick = { onSelectPlace(recent.label, recent.coordinates, editingPickup) },
                                onToggleSave = { onSavePlace(recent.label, recent.coordinates) }
                            )
                        }
                    }
                }
                if (query.trim().length >= 3) {
                    if (
                        uiState.placeSearchError == null &&
                        uiState.placeSuggestions.isNotEmpty()
                    ) {
                        items(uiState.placeSuggestions, key = { "search-${it.id}-${it.label}" }) { place ->
                            val alreadySaved = uiState.savedDestinations.any { it.label == place.label }
                            val shortTitle = place.label.substringBefore(" ·")
                            val areaHint = place.label.substringAfter(" ·", "").takeIf { it.isNotBlank() }
                            val typeLabel = nearbyPlaceTypeLabel(place.category)
                            val distLabel = formatPlaceDistance(place.distanceMeters)
                            val subtitle = listOfNotNull(areaHint, typeLabel, distLabel)
                                .joinToString(" · ")
                                .ifBlank { "Search result" }
                            PlaceRow(
                                title = shortTitle,
                                subtitle = subtitle,
                                isSaved = alreadySaved,
                                leadingIcon = if (alreadySaved) Icons.Filled.Bookmark else nearbyPlaceIcon(place.category),
                                onClick = { onSelectPlace(place.label, place.coordinates, editingPickup) },
                                onToggleSave = { onSavePlace(place.label, place.coordinates) }
                            )
                        }
                    } else if (
                        !uiState.searchingPlaces &&
                        !isDebouncingSearch &&
                        uiState.placeSearchError == null
                    ) {
                        item {
                            WayfareEmptyState(
                                icon = Icons.Filled.Search,
                                title = "No matching places",
                                message = "Try a clinic, shop, mall, or street name, or tap Pin on map.",
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = uiState.selectedNearbyCategory.sectionTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            if (uiState.nearbyPlacesLoading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (uiState.nearbyPlaces.isNotEmpty()) {
                        items(uiState.nearbyPlaces, key = { "nearby-${it.id}-${it.label}" }) { place ->
                            val alreadySaved = uiState.savedDestinations.any { it.label == place.label }
                            val shortTitle = place.label.substringBefore(" ·")
                            val areaHint = place.label.substringAfter(" ·", "").takeIf { it.isNotBlank() }
                            val typeLabel = nearbyPlaceTypeLabel(place.category)
                            val distLabel = formatPlaceDistance(place.distanceMeters)
                            val subtitle = listOfNotNull(areaHint, typeLabel, distLabel)
                                .joinToString(" · ")
                                .ifBlank { "Nearby place" }
                            PlaceRow(
                                title = shortTitle,
                                subtitle = subtitle,
                                isSaved = alreadySaved,
                                leadingIcon = if (alreadySaved) Icons.Filled.Bookmark else nearbyPlaceIcon(place.category),
                                onClick = { onSelectPlace(place.label, place.coordinates, editingPickup) },
                                onToggleSave = { onSavePlace(place.label, place.coordinates) }
                            )
                        }
                    } else if (uiState.nearbyPlacesLoading) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Looking for ${uiState.selectedNearbyCategory.chipLabel.lowercase()} near you...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Text(
                                        text = uiState.nearbyPlacesError
                                            ?: "No ${uiState.selectedNearbyCategory.chipLabel.lowercase()} found within 15 km.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = onRetryNearby,
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Retry")
                                        }
                                        OutlinedButton(
                                            onClick = { onPickOnMap(editingPickup) },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Pin on map")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nearbyCategoryChipIcon(category: NearbyPlaceCategory): ImageVector = when (category) {
    NearbyPlaceCategory.ALL -> Icons.Filled.Place
    NearbyPlaceCategory.CLINICS -> Icons.Filled.LocalHospital
    NearbyPlaceCategory.SHOPPING -> Icons.Filled.Storefront
    NearbyPlaceCategory.PAYPOINTS -> Icons.Filled.AccountBalance
    NearbyPlaceCategory.TRANSIT -> Icons.Filled.DirectionsBus
}

private fun nearbyPlaceIcon(categoryKey: String?): ImageVector = when (categoryKey?.lowercase()) {
    NearbyPlaceCategory.CLINICS.key -> Icons.Filled.LocalHospital
    NearbyPlaceCategory.SHOPPING.key -> Icons.Filled.Storefront
    NearbyPlaceCategory.PAYPOINTS.key -> Icons.Filled.AccountBalance
    NearbyPlaceCategory.TRANSIT.key -> Icons.Filled.DirectionsBus
    else -> Icons.Filled.Place
}

private fun nearbyPlaceTypeLabel(categoryKey: String?): String = when (categoryKey?.lowercase()) {
    NearbyPlaceCategory.CLINICS.key -> "Clinic / Health"
    NearbyPlaceCategory.SHOPPING.key -> "Mall / Shops"
    NearbyPlaceCategory.PAYPOINTS.key -> "Paypoint / Civic"
    NearbyPlaceCategory.TRANSIT.key -> "Rank / Station"
    else -> "Nearby place"
}

private fun formatPlaceDistance(distanceMeters: Double?): String? {
    if (distanceMeters == null || !distanceMeters.isFinite()) return null
    return if (distanceMeters < 1000.0) {
        "${distanceMeters.roundToInt().coerceAtLeast(50)} m away"
    } else {
        "${"%.1f".format(Locale.US, distanceMeters / 1000.0)} km away"
    }
}

@Composable
private fun PlaceRow(
    title: String,
    subtitle: String,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: (() -> Unit)?,
    leadingIcon: ImageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.LocationOn
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("place_row_${title.take(12)}")
    ) {
        Surface(shape = CircleShape, color = colorScheme.surfaceVariant, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onToggleSave != null) {
            IconButton(onClick = onToggleSave, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.Bookmark,
                    contentDescription = "Save place",
                    tint = if (isSaved) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MapPinPickerSheet(
    pickingForPickup: Boolean,
    centerCoords: RideCoordinates,
    pinPreviewAddress: String?,
    pinPreviewLoading: Boolean,
    onCancel: () -> Unit,
    onConfirmPin: (RideCoordinates) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val fallbackLabel = remember(centerCoords) { LaunchPolicy.nearestLandmarkLabel(centerCoords) }
    val displayLabel = pinPreviewAddress?.takeIf { it.isNotBlank() } ?: fallbackLabel

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (pickingForPickup) "Drag map to set pickup" else "Drag map to set destination",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                if (pinPreviewLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Finding street...",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("pin_picker_address_label")
            )
            Text(
                text = "${"%.5f".format(centerCoords.latitude)}, ${"%.5f".format(centerCoords.longitude)}",
                fontFamily = WayfareMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("cancel_map_pin")
                ) { Text("Back") }
                Button(
                    onClick = { onConfirmPin(centerCoords) },
                    modifier = Modifier.weight(2f).height(50.dp).tactilePressScale().testTag("confirm_map_pin")
                ) {
                    Text(if (pickingForPickup) "Use pickup" else "Use destination", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChooseRideAndFareSheet(
    draft: BookingDraft,
    walletBalanceMinor: Long,
    isDestinationSaved: Boolean,
    onToggleSaveDestination: () -> Unit,
    onBack: () -> Unit,
    onSelectCategory: (RideCategory) -> Unit,
    onToggleOffer: () -> Unit,
    onOfferTextChange: (String) -> Unit,
    onAdjustOffer: (Int) -> Unit,
    onToggleSchedule: () -> Unit,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onRetryRoute: () -> Unit,
    onRequestRide: () -> Unit,
    onTogglePickupTag: (String) -> Unit = {},
    onPickupNoteChange: (String) -> Unit = {},
    onSelectCashChangeForZar: (Int?) -> Unit = {},
    onToggleBeneficiary: () -> Unit = {},
    onBeneficiaryNameChange: (String) -> Unit = {},
    onBeneficiaryPhoneChange: (String) -> Unit = {},
    onToggleSharedRide: () -> Unit = {},
    onSeatCountChange: (Int) -> Unit = {},
    onToggleReturnTrip: () -> Unit = {},
    onToggleReturnRequest: () -> Unit = {},
    onToggleGravelRoad: () -> Unit = {},
    approachingDriverHint: OutboundDriverApproachingHint? = null,
    fareEstimateBreakdown: FareEstimateBreakdown? = draft.fareEstimateBreakdown
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val route = draft.previewRoute
    val kmText = when {
        draft.routeLoading -> "Calculating..."
        route != null -> "${"%.1f".format(route.distanceMeters / 1000.0)} km"
        else -> "Route unavailable"
    }
    val minText = when {
        draft.routeLoading -> "ETA pending"
        route != null -> "${(route.durationSeconds / 60).coerceAtLeast(1)} min"
        else -> "-- min"
    }
    val lowWallet = draft.paymentMethod == PaymentMethod.WALLET &&
        walletBalanceMinor < (draft.effectiveFare * 100).toLong()

    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
            .background(colorScheme.surface, sheetShape)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f), sheetShape)
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("ride_sheet_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Change destination")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.destinationLabel.substringBefore(" ·"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "From ${draft.pickupLabel.substringBefore(" ·")} · $kmText · $minText",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onToggleSaveDestination,
                    modifier = Modifier.size(48.dp).testTag("save_destination_bookmark")
                ) {
                    Icon(
                        imageVector = if (isDestinationSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isDestinationSaved) "Destination saved" else "Save destination",
                        tint = if (isDestinationSaved) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = draft.scheduledOffsetMinutes != null,
                    onClick = onToggleSchedule,
                    leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = {
                        Text(
                            when (draft.scheduledOffsetMinutes) {
                                null -> "Now"
                                30 -> "+30m"
                                else -> "+1h"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = wayfareColors.brandHighlight,
                        selectedLabelColor = colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("ride_timing_chip")
                )
            }

            if (draft.routeLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("route_preview_loading")
                )
            }

            draft.routeError?.let { errorMsg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRetryRoute,
                            modifier = Modifier.testTag("retry_route_btn")
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(RideCategory.entries, key = { it.name }) { category ->
                    val selected = draft.category == category
                    val ruralCorridor = LaunchPolicy.isRuralOrPeriUrban(draft.pickupCoords, draft.destinationCoords)
                    val fare = route?.let {
                        FareEstimationService.defaultInstance.estimateForRoute(
                            route = it,
                            category = category,
                            isSharedRide = draft.isSharedRide,
                            seatCount = draft.seatCount,
                            isReturnTrip = draft.isReturnTrip,
                            isReturnRequest = draft.isReturnRequest,
                            includesGravelRoad = draft.includesGravelRoad,
                            isRuralArea = ruralCorridor
                        ).estimatedFareZar
                    }
                    val bg by animateColorAsState(
                        if (selected) wayfareColors.brandHighlight else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        label = "catBg"
                    )
                    val cardScale by animateFloatAsState(
                        targetValue = if (selected) 1.02f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "catScale"
                    )
                    val categoryIcon = when (category) {
                        RideCategory.BAKKIE -> Icons.Filled.LocalShipping
                        RideCategory.PARCEL -> Icons.Filled.Inventory2
                        else -> Icons.Filled.DirectionsCar
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colorScheme.primary else colorScheme.outlineVariant),
                        modifier = Modifier
                            .width(114.dp)
                            .scale(cardScale)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectCategory(category) }
                            .testTag("category_card_${category.name.lowercase()}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = category.label,
                                    tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = category.caption,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(category.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                text = fare?.let { LaunchPolicy.formatZar(it) } ?: "--",
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            val resolvedEstimate = fareEstimateBreakdown ?: route?.let {
                FareEstimationService.defaultInstance.estimateForRoute(
                    route = it,
                    category = draft.category,
                    isSharedRide = draft.isSharedRide,
                    seatCount = draft.seatCount,
                    isReturnTrip = draft.isReturnTrip,
                    isReturnRequest = draft.isReturnRequest,
                    includesGravelRoad = draft.includesGravelRoad,
                    isRuralArea = LaunchPolicy.isRuralOrPeriUrban(draft.pickupCoords, draft.destinationCoords)
                )
            }
            val customOfferZar = draft.customOfferText.toDoubleOrNull()?.takeIf { it >= 20.0 }
            if (draft.routeLoading || resolvedEstimate != null) {
                FareEstimateSummaryCard(
                    breakdown = resolvedEstimate,
                    isLoading = draft.routeLoading,
                    customOfferZar = customOfferZar
                )
            }

            // Unified Collapsible Customize Ride Accordion (Return Request -18%, Rural Options, Beneficiary, Pickup Notes)
            val returnComparison = draft.returnFareComparison
            val returnSelected = draft.isReturnRequest
            val hasCustomizeExtras = draft.isReturnRequest ||
                draft.isSharedRide ||
                draft.isReturnTrip ||
                draft.includesGravelRoad ||
                draft.beneficiaryEnabled ||
                draft.pickupNote.isNotBlank() ||
                approachingDriverHint != null
            var showCustomizeRide by remember(draft.activePreset, approachingDriverHint != null) {
                mutableStateOf(hasCustomizeExtras)
            }

            val customizeSummaryChips = remember(
                draft.isReturnRequest,
                returnComparison.savingsPercent,
                draft.isSharedRide,
                draft.seatCount,
                draft.isReturnTrip,
                draft.includesGravelRoad,
                draft.beneficiaryEnabled,
                draft.pickupNote,
                approachingDriverHint
            ) {
                buildList {
                    if (draft.isReturnRequest) {
                        add(if (returnComparison.savingsPercent > 0) "Return -${returnComparison.savingsPercent}%" else "Return Request")
                    }
                    if (approachingDriverHint != null && !draft.isReturnRequest) {
                        add("Driver ~${approachingDriverHint.etaMinutes}m away")
                    }
                    if (draft.isSharedRide) add("Shared (${draft.seatCount})")
                    if (draft.isReturnTrip) add("Wait & return")
                    if (draft.includesGravelRoad) add("Gravel")
                    if (draft.beneficiaryEnabled) add("For family")
                    if (draft.pickupNote.isNotBlank()) add("Note")
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(
                    1.dp,
                    if (hasCustomizeExtras) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth().testTag("rural_trip_options_section")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Surface(
                        onClick = { showCustomizeRide = !showCustomizeRide },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("toggle_trip_extras_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Customize ride",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (customizeSummaryChips.isNotEmpty()) {
                                        customizeSummaryChips.joinToString(" · ")
                                    } else {
                                        "Optional · Return -18%, share seat, gravel, gate note"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (customizeSummaryChips.isNotEmpty()) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (showCustomizeRide) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showCustomizeRide) "Hide ride options" else "Customize ride",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCustomizeRide) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)
                        ) {
                            HorizontalDivider()
                            // Return Request (-18% backhaul fare + approaching driver hint)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (returnSelected) {
                                    wayfareColors.emeraldContainer.copy(alpha = 0.72f)
                                } else {
                                    colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (returnSelected) 1.5.dp else 1.dp,
                                    color = if (returnSelected) wayfareColors.positive else colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onToggleReturnRequest)
                                    .testTag("rural_chip_return_request")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (returnSelected) wayfareColors.positive else wayfareColors.emeraldContainer,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.SwapHoriz,
                                                contentDescription = "Return Request",
                                                tint = if (returnSelected) colorScheme.onPrimary else wayfareColors.positive,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Return Request",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (returnSelected) wayfareColors.positive else colorScheme.onSurface
                                            )
                                            if (returnComparison.savingsPercent > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = wayfareColors.emeraldContainer
                                                ) {
                                                    Text(
                                                        text = "-${returnComparison.savingsPercent}%",
                                                        fontFamily = WayfareMonoFamily,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = wayfareColors.positive,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (approachingDriverHint != null) {
                                            Text(
                                                text = "Driver heading ~${"%.1f".format(Locale.US, approachingDriverHint.distanceToRiderPickupKm)} km away (~${approachingDriverHint.etaMinutes} min)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = wayfareColors.positive,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("approaching_driver_return_hint")
                                            )
                                        } else {
                                            Text(
                                                text = if (returnSelected) {
                                                    "Match drivers returning toward your drop-off"
                                                } else {
                                                    "Catch drivers returning from drop-off for less"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (route != null) {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.testTag("return_request_savings_banner")
                                        ) {
                                            if (returnComparison.savingsZar >= 1.0) {
                                                Text(
                                                    text = LaunchPolicy.formatZar(returnComparison.standardFareZar),
                                                    fontFamily = WayfareMonoFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant,
                                                    textDecoration = TextDecoration.LineThrough
                                                )
                                            }
                                            Text(
                                                text = LaunchPolicy.formatZar(returnComparison.discountedFareZar),
                                                fontFamily = WayfareMonoFamily,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (returnSelected) wayfareColors.positive else colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                    FilterChip(
                        selected = draft.isSharedRide,
                        onClick = onToggleSharedRide,
                        label = {
                            Text(
                                if (draft.isSharedRide) "Share · ${draft.seatCount} seat(s)" else "Share seat",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.emeraldContainer,
                            selectedLabelColor = wayfareColors.positive
                        ),
                        modifier = Modifier.testTag("rural_chip_shared_ride")
                    )
                    FilterChip(
                        selected = draft.isReturnTrip,
                        onClick = onToggleReturnTrip,
                        label = { Text("Wait & return", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("rural_chip_return_trip")
                    )
                    FilterChip(
                        selected = draft.includesGravelRoad,
                        onClick = onToggleGravelRoad,
                        leadingIcon = { Icon(Icons.Filled.Terrain, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("Gravel road", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("rural_chip_gravel_road")
                    )
                    FilterChip(
                        selected = draft.beneficiaryEnabled,
                        onClick = onToggleBeneficiary,
                        leadingIcon = { Icon(Icons.Filled.PersonAddAlt1, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("Book for family", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("rural_chip_beneficiary")
                    )
                }

                AnimatedVisibility(visible = draft.isSharedRide) {
                    var splitRiderCount by remember(draft.seatCount) {
                        mutableIntStateOf(draft.seatCount.coerceAtLeast(2).coerceIn(2, 4))
                    }
                    val splitSummary = remember(draft.effectiveFare, splitRiderCount) {
                        SmartRideGuards.calculateFareSplit(draft.effectiveFare, splitRiderCount)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Seats needed:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            (1..4).forEach { seats ->
                                FilterChip(
                                    selected = draft.seatCount == seats,
                                    onClick = { onSeatCountChange(seats) },
                                    label = { Text("$seats") },
                                    modifier = Modifier.height(48.dp).testTag("shared_seat_count_$seats")
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = wayfareColors.emeraldContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().testTag("fare_split_calculator_card")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Split ÷ $splitRiderCount riders: ${LaunchPolicy.formatZar(splitSummary.exactPerPersonZar)} each",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = wayfareColors.positive
                                        )
                                        Text(
                                            text = "Suggested cash: R ${splitSummary.roundedCashPerPersonZar} per person",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            shareTextMessage(
                                                context = context,
                                                chooserTitle = "Share fare split",
                                                text = SmartRideGuards.buildFareSplitShareText(
                                                    pickup = draft.pickupLabel,
                                                    destination = draft.destinationLabel,
                                                    totalFareZar = draft.effectiveFare,
                                                    riderCount = splitRiderCount,
                                                    driverLabel = null
                                                )
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier.height(48.dp).testTag("share_fare_split_btn")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share fare split", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Share", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Group size:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    (2..4).forEach { count ->
                                        FilterChip(
                                            selected = splitRiderCount == count,
                                            onClick = { splitRiderCount = count },
                                            label = { Text("÷$count") },
                                            modifier = Modifier.height(48.dp).testTag("fare_split_count_$count")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = draft.beneficiaryEnabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = draft.beneficiaryName,
                            onValueChange = onBeneficiaryNameChange,
                            label = { Text("Passenger name (e.g. Gogo Mavis)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("beneficiary_name_input")
                        )
                        OutlinedTextField(
                            value = draft.beneficiaryPhone,
                            onValueChange = onBeneficiaryPhoneChange,
                            label = { Text("Passenger mobile number") },
                            placeholder = { Text("082 123 4567") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("beneficiary_phone_input")
                        )
                    }
                }

                            HorizontalDivider()
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().testTag("pickup_notes_section")
                            ) {
                                Text(
                                    text = "Pickup landmarks / note for driver",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    (SmartRideGuards.PICKUP_MICRO_TAGS + SmartRideGuards.RURAL_PICKUP_MICRO_TAGS).forEach { tag ->
                                        val active = draft.pickupNote.contains(tag, ignoreCase = true)
                                        FilterChip(
                                            selected = active,
                                            onClick = { onTogglePickupTag(tag) },
                                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = wayfareColors.brandHighlight,
                                                selectedLabelColor = colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("pickup_tag_${tag.lowercase().replace(" ", "_")}")
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = draft.pickupNote,
                                    onValueChange = onPickupNoteChange,
                                    placeholder = { Text("e.g. Gate 2 entrance, outside Checkers, white hoodie...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("pickup_note_input")
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (draft.isOfferExpanded) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleOffer)
                            .padding(vertical = 4.dp)
                            .testTag("toggle_offer_fare")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(
                                    text = "Your offer price",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap to adjust · drivers can match or counter",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MonoBadge(text = LaunchPolicy.formatZar(draft.effectiveFare))
                            Icon(
                                imageVector = if (draft.isOfferExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (draft.isOfferExpanded) "Hide fare editor" else "Adjust offer price",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = draft.isOfferExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                            Text(
                                text = "Propose your fare — nearby drivers can accept or send a counter-offer.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onAdjustOffer(-10) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(48.dp).testTag("offer_minus_10")
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease R10", modifier = Modifier.size(16.dp))
                                    Text("R10")
                                }
                                OutlinedTextField(
                                    value = draft.customOfferText,
                                    onValueChange = onOfferTextChange,
                                    singleLine = true,
                                    prefix = { Text("R ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("offer_amount_input")
                                )
                                OutlinedButton(
                                    onClick = { onAdjustOffer(10) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(48.dp).testTag("offer_plus_10")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase R10", modifier = Modifier.size(16.dp))
                                    Text("R10")
                                }
                            }
                        }
                    }
                }
            }

            // Smart Offer Coach — only shown when the rider adjusts the fare or enters a low offer
            route?.takeIf { !draft.routeLoading }?.let { validRoute ->
                val ruralCorridor = LaunchPolicy.isRuralOrPeriUrban(draft.pickupCoords, draft.destinationCoords)
                val forecast = remember(
                    draft.effectiveFare,
                    validRoute.distanceMeters,
                    draft.category,
                    draft.isSharedRide,
                    draft.seatCount,
                    draft.isReturnTrip,
                    draft.isReturnRequest,
                    draft.includesGravelRoad,
                    ruralCorridor
                ) {
                    SmartRideGuards.evaluateOfferAcceptance(
                        fareZar = draft.effectiveFare,
                        distanceMeters = validRoute.distanceMeters,
                        category = draft.category,
                        isSharedRide = draft.isSharedRide,
                        seatCount = draft.seatCount,
                        isReturnTrip = draft.isReturnTrip,
                        includesGravelRoad = draft.includesGravelRoad,
                        isRuralArea = ruralCorridor,
                        isReturnRequest = draft.isReturnRequest
                    )
                }
                val showCoach = draft.isOfferExpanded ||
                    draft.customOfferText.isNotBlank() ||
                    forecast.tier == SmartRideGuards.AcceptanceTier.LOW
                AnimatedVisibility(visible = showCoach) {
                    val coachBg = when (forecast.tier) {
                        SmartRideGuards.AcceptanceTier.HIGH -> wayfareColors.positive.copy(alpha = 0.12f)
                        SmartRideGuards.AcceptanceTier.MODERATE -> colorScheme.primaryContainer.copy(alpha = 0.55f)
                        SmartRideGuards.AcceptanceTier.LOW -> wayfareColors.warning.copy(alpha = 0.15f)
                    }
                    val coachFg = when (forecast.tier) {
                        SmartRideGuards.AcceptanceTier.HIGH -> wayfareColors.positive
                        SmartRideGuards.AcceptanceTier.MODERATE -> colorScheme.primary
                        SmartRideGuards.AcceptanceTier.LOW -> wayfareColors.warning
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = coachBg,
                        border = BorderStroke(1.dp, coachFg.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().testTag("smart_offer_coach_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = forecast.headline,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = coachFg
                                )
                                Text(
                                    text = forecast.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            if (forecast.tier == SmartRideGuards.AcceptanceTier.LOW) {
                                OutlinedButton(
                                    onClick = {
                                        onOfferTextChange(forecast.recommendedFairZar.toInt().toString())
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(48.dp).testTag("match_suggested_fare_btn")
                                ) {
                                    Text(
                                        text = "Match ${LaunchPolicy.formatZar(forecast.recommendedFairZar)}",
                                        fontFamily = WayfareMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clean Cash / Wallet / Card selector + concise hint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Payment",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = when (draft.paymentMethod) {
                        PaymentMethod.CARD -> "Opens Paystack when confirmed"
                        PaymentMethod.WALLET -> "Reserved from Wallet"
                        PaymentMethod.CASH -> "Pay driver at drop-off"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("payment_method_timing_note")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PaymentMethod.entries.forEach { method ->
                    val selected = draft.paymentMethod == method
                    val icon = when (method) {
                        PaymentMethod.CASH -> Icons.Filled.Payments
                        PaymentMethod.WALLET -> Icons.Filled.AccountBalanceWallet
                        PaymentMethod.CARD -> Icons.Filled.CreditCard
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPaymentMethod(method) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            val text = if (method == PaymentMethod.WALLET && selected) {
                                "Wallet (${LaunchPolicy.formatZarMinor(walletBalanceMinor)})"
                            } else method.label
                            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (method == PaymentMethod.CASH) {
                                wayfareColors.emeraldContainer
                            } else {
                                wayfareColors.brandHighlight
                            },
                            selectedLabelColor = if (method == PaymentMethod.CASH) {
                                wayfareColors.positive
                            } else {
                                colorScheme.onPrimaryContainer
                            },
                            selectedLeadingIconColor = if (method == PaymentMethod.CASH) {
                                wayfareColors.positive
                            } else {
                                colorScheme.primary
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("payment_chip_${method.name.lowercase()}")
                    )
                }
            }

            // Compact expandable Cash change preference
            if (draft.paymentMethod == PaymentMethod.CASH) {
                var showChangeOptions by remember(draft.cashChangeForZar) {
                    mutableStateOf(draft.cashChangeForZar != null)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showChangeOptions = !showChangeOptions }
                            .padding(horizontal = 4.dp)
                            .testTag("toggle_cash_change_row")
                    ) {
                        Text(
                            text = draft.cashChangeForZar?.let { "Cash note: Change for R$it" }
                                ?: "Need change? Tap to tell driver",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (draft.cashChangeForZar != null) wayfareColors.positive else colorScheme.onSurfaceVariant,
                            fontWeight = if (draft.cashChangeForZar != null) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Icon(
                            imageVector = if (showChangeOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Cash change options",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AnimatedVisibility(visible = showChangeOptions) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SmartRideGuards.CASH_CHANGE_OPTIONS_ZAR.forEach { noteAmount ->
                                val selected = draft.cashChangeForZar == noteAmount
                                val label = if (noteAmount == null) "Exact cash" else "R$noteAmount note"
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectCashChangeForZar(noteAmount) },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = wayfareColors.emeraldContainer,
                                        selectedLabelColor = wayfareColors.positive
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cash_change_chip_${noteAmount ?: "exact"}")
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = lowWallet) {
                Text(
                    text = "Wallet balance is below this fare. Top up in Wallet or switch to Cash / Card.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.error
                )
            }

            AnimatedVisibility(visible = draft.isOfflineEstimatedRoute) {
                Text(
                    text = "Saved offline route · Fare may adjust slightly when signal returns.",
                    style = MaterialTheme.typography.labelSmall,
                    color = wayfareColors.warning,
                    modifier = Modifier.testTag("offline_route_booking_notice")
                )
            }

            Button(
                onClick = onRequestRide,
                enabled = draft.isRouteValidForRequest,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(54.dp).tactilePressScale().testTag("request_ride_cta")
            ) {
                val ctaVerb = if (draft.customOfferText.isNotBlank()) "Offer" else "Request ${draft.category.label}"
                val ctaLabel = if (draft.customOfferText.isNotBlank()) {
                    "Offer ${LaunchPolicy.formatZar(draft.effectiveFare)} · ${draft.category.label}"
                } else {
                    "$ctaVerb · ${LaunchPolicy.formatZar(draft.effectiveFare)} (${draft.paymentMethod.label})"
                }
                Text(
                    text = if (draft.routeLoading) "Calculating route..." else ctaLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }
    }
}

@Composable
private fun FareEstimateSummaryCard(
    breakdown: FareEstimateBreakdown?,
    isLoading: Boolean,
    customOfferZar: Double?
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var showFullBreakdown by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fare_estimate_breakdown_card")
    ) {
        if (isLoading && breakdown == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(
                    text = "Calculating estimated fare from distance & ride time...",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else if (breakdown != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showFullBreakdown = !showFullBreakdown }
                            .padding(end = 6.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = "Estimated fare based on distance and time",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Estimated fare",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (showFullBreakdown) "Tap to hide details" else "Tap for pickup fee & range",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (showFullBreakdown) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showFullBreakdown) "Hide breakdown" else "Show breakdown",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MonoBadge(
                        text = LaunchPolicy.formatZar(breakdown.estimatedFareZar),
                        modifier = Modifier.testTag("fare_estimate_total_badge")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FareEstimateLineRow(
                        label = "Distance (${"%.1f".format(Locale.US, breakdown.distanceKm)} km)",
                        amountZar = breakdown.distanceFareZar,
                        modifier = Modifier.testTag("fare_estimate_distance_row")
                    )
                    FareEstimateLineRow(
                        label = "Ride time (${breakdown.durationMinutes} min)",
                        amountZar = breakdown.timeFareZar,
                        modifier = Modifier.testTag("fare_estimate_time_row")
                    )
                    AnimatedVisibility(visible = showFullBreakdown) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))
                            FareEstimateLineRow(
                                label = "Base pickup",
                                amountZar = breakdown.baseFareZar
                            )
                            breakdown.modifiers.forEach { modLine ->
                                FareEstimateLineRow(
                                    label = modLine.label,
                                    amountZar = modLine.amountZar,
                                    signed = true,
                                    highlightColor = if (modLine.amountZar < 0) wayfareColors.positive else colorScheme.onSurface
                                )
                            }
                            val footnote = if (customOfferZar != null && customOfferZar != breakdown.estimatedFareZar) {
                                "Your offer ${LaunchPolicy.formatZar(customOfferZar)} overrides the ${LaunchPolicy.formatZar(breakdown.estimatedFareZar)} estimate."
                            } else {
                                "Est. range ${LaunchPolicy.formatZar(breakdown.minRangeZar)} – ${LaunchPolicy.formatZar(breakdown.maxRangeZar)} before you confirm."
                            }
                            Text(
                                text = footnote,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customOfferZar != null) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FareEstimateLineRow(
    label: String,
    amountZar: Double,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    highlightColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        val formatted = LaunchPolicy.formatZar(kotlin.math.abs(amountZar))
        val text = when {
            signed && amountZar < 0 -> "-$formatted"
            signed && amountZar > 0 -> "+$formatted"
            else -> formatted
        }
        Text(
            text = text,
            fontFamily = WayfareMonoFamily,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = highlightColor
        )
    }
}

