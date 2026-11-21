package com.wayfare.ride.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.audio.NotificationSoundStyle
import com.wayfare.ride.audio.SoundAlertPlayer
import com.wayfare.ride.domain.AppLanguage
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.driver.DriverProfileTab
import com.wayfare.ride.ui.driver.OfflineMapPacksSheet
import com.wayfare.ride.ui.theme.AppThemeMode
import com.wayfare.ride.ui.theme.LocalWayfareColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAndPlacesSheet(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val session = uiState.currentSession
    var showDriverApplicationSheet by rememberSaveable { mutableStateOf(false) }
    var showSavedPlaces by rememberSaveable { mutableStateOf(false) }
    var showTrustedDrivers by rememberSaveable { mutableStateOf(false) }
    var showOfflineMapsSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }
    var deletionReason by rememberSaveable { mutableStateOf("") }

    BackHandler {
        if (showDriverApplicationSheet) showDriverApplicationSheet = false else onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (showDriverApplicationSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 660.dp)
                    .imePadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Drive with Vhaya",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showDriverApplicationSheet = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Back to account")
                    }
                }
                DriverProfileTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    topContentPaddingDp = 8.dp
                )
            }
            return@ModalBottomSheet
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .testTag("account_sheet_list")
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session?.displayName ?: "Vhaya Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(session?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                    StatusChip(
                        label = session?.role?.label ?: "Guest",
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                    IconButton(onClick = { viewModel.refreshNow() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh account data")
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close account")
                    }
                }
            }

            if (session?.role == RideRole.RIDER) {
                item {
                    OutlinedButton(
                        onClick = { showDriverApplicationSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("apply_to_drive_btn")
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        val statusLabel = uiState.currentDriverApp?.applicationStatus?.let {
                            if (it == "draft") "Apply to drive with Vhaya" else "Driver application (${it.replace("_", " ")})"
                        } ?: "Apply to drive with Vhaya"
                        Text(statusLabel, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Block 2: My Places & Community Drivers Circle (Collapsible accordions)
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { showSavedPlaces = !showSavedPlaces }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("account_saved_places_toggle")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Saved places (${uiState.savedDestinations.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (showSavedPlaces) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (showSavedPlaces) "Collapse saved places" else "Expand saved places",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(visible = showSavedPlaces) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                                ) {
                                    if (uiState.savedDestinations.isEmpty()) {
                                        Text(
                                            text = "Tap the bookmark icon when searching to save favourite places.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        uiState.savedDestinations.forEach { place ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = colorScheme.surface,
                                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                                ) {
                                                    Text(place.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = { viewModel.deleteSavedPlace(place.id) },
                                                        modifier = Modifier.size(48.dp)
                                                    ) {
                                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete saved place", tint = colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { showTrustedDrivers = !showTrustedDrivers }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("account_trusted_drivers_toggle")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = wayfareColors.positive, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Community Drivers Circle (${uiState.trustedDrivers.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (showTrustedDrivers) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (showTrustedDrivers) "Collapse trusted drivers" else "Expand trusted drivers",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(visible = showTrustedDrivers) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                                ) {
                                    if (uiState.trustedDrivers.isEmpty()) {
                                        Text(
                                            text = "Star trusted drivers after a 4–5★ ride or from Trips so their bids appear first.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        uiState.trustedDrivers.forEach { driver ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = wayfareColors.emeraldContainer.copy(alpha = 0.45f),
                                                border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth().testTag("trusted_driver_row_${driver.driverId}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(driver.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                        val vehicleLine = listOfNotNull(
                                                            listOf(driver.vehicleColor, driver.vehicle).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() },
                                                            driver.registration.takeIf { it.isNotBlank() }
                                                        ).joinToString(" · ")
                                                        if (vehicleLine.isNotBlank()) {
                                                            Text(
                                                                text = vehicleLine,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.removeTrustedDriver(driver.driverId) },
                                                        modifier = Modifier.size(48.dp).testTag("remove_trusted_driver_${driver.driverId}")
                                                    ) {
                                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove trusted driver", tint = colorScheme.error)
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

            // Block 3: Grouped App Preferences Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "App Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Appearance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppThemeMode.entries.forEach { mode ->
                                    val selected = uiState.themeMode == mode
                                    val icon = when (mode) {
                                        AppThemeMode.DARK -> Icons.Filled.DarkMode
                                        AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                        AppThemeMode.LIGHT -> Icons.Filled.LightMode
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        label = { Text(mode.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "${mode.label} theme",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = wayfareColors.brandHighlight,
                                            selectedLabelColor = colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("theme_mode_${mode.key}")
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Notification sound", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = uiState.notificationSoundStyle.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                NotificationSoundStyle.entries.forEach { style ->
                                    val selected = uiState.notificationSoundStyle == style
                                    val icon = when (style) {
                                        NotificationSoundStyle.CAR_HORN -> Icons.Filled.DirectionsCar
                                        NotificationSoundStyle.CAR_CHIME -> Icons.Filled.ElectricCar
                                        NotificationSoundStyle.CLASSIC -> Icons.Filled.NotificationsActive
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            viewModel.setNotificationSoundStyle(style)
                                            SoundAlertPlayer.playPreview(context, style)
                                        },
                                        label = {
                                            Text(
                                                text = style.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "${style.label} notification sound",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = wayfareColors.emeraldContainer,
                                            selectedLabelColor = wayfareColors.positive,
                                            selectedLeadingIconColor = wayfareColors.positive
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("notif_sound_${style.key}")
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Language · Limpopo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppLanguage.entries.chunked(2).forEach { pair ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        pair.forEach { lang ->
                                            val selected = uiState.appLanguage == lang
                                            FilterChip(
                                                selected = selected,
                                                onClick = { viewModel.setAppLanguage(lang) },
                                                label = {
                                                    Text(
                                                        text = lang.label,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = wayfareColors.brandHighlight,
                                                    selectedLabelColor = colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("app_lang_${lang.code}")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

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
                                Icon(
                                    imageVector = Icons.Filled.DataSaverOn,
                                    contentDescription = "Low-Data Map Mode",
                                    tint = if (uiState.lowDataMode) wayfareColors.positive else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text("Low-Data Schematic Map", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Pauses map image tiles on weak rural 2G/3G signal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.lowDataMode,
                                onCheckedChange = { viewModel.setLowDataMode(it) },
                                modifier = Modifier.testTag("low_data_mode_switch")
                            )
                        }

                        if (session?.role == RideRole.DRIVER) {
                            HorizontalDivider()
                            val readyPacks = uiState.offlineTilePacks.count { it.isComplete }
                            OutlinedButton(
                                onClick = { showOfflineMapsSheet = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("account_offline_maps_btn")
                            ) {
                                Icon(
                                    imageVector = if (readyPacks > 0) Icons.Filled.CloudDone else Icons.Filled.DownloadForOffline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (readyPacks > 0) "Offline corridor maps ($readyPacks ready)" else "Offline corridor maps",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Block 4: Privacy & Sign Out
            item {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        viewModel.setPrivacySheetVisible(true)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("account_privacy_btn")
                ) {
                    Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy & POPIA controls", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Button(
                    onClick = { viewModel.signOut() },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("account_sign_out_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("account_delete_account_btn")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        DeleteAccountConfirmationDialog(
            deletionReason = deletionReason,
            onReasonChange = { deletionReason = it },
            busy = uiState.isBusy("popia_delete"),
            onConfirmDelete = {
                viewModel.deleteAccountPermanently(deletionReason) {
                    showDeleteAccountDialog = false
                    onDismiss()
                }
            },
            onWipeDeviceOnly = {
                showDeleteAccountDialog = false
                onDismiss()
                viewModel.clearAllLocalUserDataAndSignOut()
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }

    if (showOfflineMapsSheet) {
        val cacheStats = remember(uiState.offlineTilePacks, uiState.offlinePackDownloadingId) {
            viewModel.offlineTileCacheStats()
        }
        OfflineMapPacksSheet(
            cachedPacks = uiState.offlineTilePacks,
            downloadingPackId = uiState.offlinePackDownloadingId,
            progress = uiState.offlinePackProgress,
            cacheStats = cacheStats,
            onDownloadPack = { pack -> viewModel.downloadOfflineCorridorPack(pack) },
            onClearCache = { viewModel.clearOfflineTileCache() },
            onDismiss = { showOfflineMapsSheet = false }
        )
    }
}
