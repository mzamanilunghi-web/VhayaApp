package com.wayfare.ride.ui.common

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PopiaDataExporter
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val privacy = uiState.privacyPreferences

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showDeletionDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var deletionReason by remember { mutableStateOf("") }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }
        }
        pendingExportJson = null
    }

    BackHandler { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = colorScheme.primary)
                        Column {
                            Text("Privacy & POPIA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Protection of Personal Information Act (SA)", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close privacy settings")
                    }
                }
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Filled.GpsFixed,
                    title = "Live location sharing",
                    subtitle = "Allow foreground GPS updates for pickups and active trips",
                    checked = privacy.locationSharingConsent,
                    onCheckedChange = { viewModel.setLocationSharingConsent(it) },
                    testTag = "privacy_location_consent_switch"
                )
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Trip-only privacy mode",
                    subtitle = "Share background driver GPS only while a ride is actively in progress",
                    checked = privacy.tripOnlyPrivacyMode,
                    onCheckedChange = { viewModel.setTripOnlyPrivacyMode(it) },
                    testTag = "privacy_trip_only_switch"
                )
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Support case notifications",
                    subtitle = "Notify when Vhaya Support replies or updates your ticket status",
                    checked = privacy.supportNotificationsEnabled,
                    onCheckedChange = { viewModel.setSupportNotificationsEnabled(it) },
                    testTag = "privacy_notifications_switch"
                )
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Data retention schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            items(PopiaDataExporter.RETENTION_SCHEDULE) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(item.category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(item.retentionPeriod, style = MaterialTheme.typography.labelMedium, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text(item.legalBasis, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Your POPIA rights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.exportPopiaDataJson { json ->
                                pendingExportJson = json
                                saveFileLauncher.launch(PopiaDataExporter.suggestedFileName())
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("privacy_export_data_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save JSON")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportPopiaDataJson { json ->
                                PopiaDataExporter.shareExportFile(context, json)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("privacy_share_export_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share export")
                    }
                }
            }

            item {
                val privacyUrl = remember(uiState.apiBaseUrl) {
                    LaunchPolicy.resolvePrivacyPolicyUrl(uiState.apiBaseUrl, isDebugBuild = BuildConfig.DEBUG)
                }
                val deletionUrl = remember(uiState.apiBaseUrl) {
                    LaunchPolicy.resolveAccountDeletionUrl(uiState.apiBaseUrl, isDebugBuild = BuildConfig.DEBUG)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            if (privacyUrl != null) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                                }.onFailure { showPolicyDialog = true }
                            } else {
                                showPolicyDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("privacy_policy_link_btn")
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Privacy Policy")
                    }
                    OutlinedButton(
                        onClick = {
                            if (deletionUrl != null) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deletionUrl)))
                                }.onFailure { showDeletionDialog = true }
                            } else {
                                showPolicyDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("privacy_web_delete_btn")
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Web Erasure Info")
                    }
                }
            }

            item {
                Button(
                    onClick = { showDeletionDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.error
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("privacy_delete_account_btn")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete account or wipe data", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            title = { Text("Vhaya Privacy & Data Safety") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Account Data: Name and email are used for authentication, rider/driver matching, and receipts.")
                    Text("• Location: Precise foreground GPS is collected only during ride booking, driver dispatch, and active trips.")
                    Text("• Payments: Card top-ups and payouts are processed over HTTPS via Paystack; Vhaya does not store card CVVs.")
                    Text("• Deletion: You can permanently delete your account inside this Privacy sheet or email ${LaunchPolicy.SUPPORT_EMAIL}.")
                }
            },
            confirmButton = {
                Button(onClick = { showPolicyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeletionDialog) {
        DeleteAccountConfirmationDialog(
            deletionReason = deletionReason,
            onReasonChange = { deletionReason = it },
            busy = uiState.isBusy("popia_delete"),
            onConfirmDelete = {
                viewModel.deleteAccountPermanently(deletionReason) {
                    showDeletionDialog = false
                    onDismiss()
                }
            },
            onWipeDeviceOnly = {
                showDeletionDialog = false
                onDismiss()
                viewModel.clearAllLocalUserDataAndSignOut()
            },
            onDismiss = { showDeletionDialog = false }
        )
    }
}

@Composable
fun DeleteAccountConfirmationDialog(
    deletionReason: String,
    onReasonChange: (String) -> Unit,
    busy: Boolean,
    onConfirmDelete: () -> Unit,
    onWipeDeviceOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account & personal data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Permanently delete your Vhaya login and clear all saved places, contacts, and local records from this device. Financial/tax logs required by SA law are retained per POPIA schedule."
                )
                OutlinedTextField(
                    value = deletionReason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                    contentColor = colorScheme.onError
                ),
                modifier = Modifier.testTag("privacy_confirm_permanent_delete")
            ) {
                Text("Delete account now")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onWipeDeviceOnly,
                    modifier = Modifier.testTag("privacy_wipe_local_btn")
                ) {
                    Text("Wipe device only")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun PrivacyToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
