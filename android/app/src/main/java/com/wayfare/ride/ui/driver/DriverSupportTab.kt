package com.wayfare.ride.ui.driver

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverIssueCategory
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.resolveSupportCategoryLabel
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.Emergency112Chip
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareEmptyState
import com.wayfare.ride.ui.components.dialEmergency112
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.rider.IssueReportingSheet
import com.wayfare.ride.ui.rider.SupportThreadView
import com.wayfare.ride.ui.theme.LocalWayfareColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriverSupportTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    var selectedCaseId by remember { mutableStateOf<Long?>(null) }
    var statusFilter by remember { mutableStateOf("all") }
    var reportTargetTrip by remember { mutableStateOf<RideTrip?>(null) }

    val selectedCase = uiState.supportCases.firstOrNull { it.id == selectedCaseId }

    var hasNotifPermission by remember {
        mutableStateOf(SupportNotificationHelper.hasNotificationPermission(context))
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val driverTrips = remember(uiState.trips, uiState.activeUserId) {
        uiState.trips
            .filter { it.driverId == uiState.activeUserId }
            .sortedByDescending { it.createdAt }
    }

    if (selectedCase != null) {
        val currentName = uiState.currentSession?.displayName?.takeIf { it.isNotBlank() } ?: "Driver"
        BackHandler { selectedCaseId = null }
        SupportThreadView(
            supportCase = selectedCase,
            senderLabel = currentName,
            onBack = { selectedCaseId = null },
            onSendReply = { msg -> viewModel.sendRiderSupportFollowUp(selectedCase, msg) }
        )
        return
    }

    val filteredCases = remember(uiState.supportCases, statusFilter) {
        if (statusFilter == "all") uiState.supportCases
        else uiState.supportCases.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Driver support", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Emergency112Chip(onClick = { dialEmergency112(context) })
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = wayfareColors.brandHighlight,
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = colorScheme.primary)
                        Text(
                            text = "Enable notifications for instant Vhaya Support replies.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.height(48.dp).testTag("driver_support_enable_notif_btn")
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Report a trip, rider, or payout issue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val latestTrip = driverTrips.firstOrNull()
                    if (latestTrip == null) {
                        Text(
                            text = "Complete or accept a trip first to link an issue report directly to a ride.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Latest trip: ${latestTrip.pickup.substringBefore(" ·")} → ${latestTrip.destination.substringBefore(" ·")} · ${LaunchPolicy.formatZar(latestTrip.fare)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { reportTargetTrip = latestTrip },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("driver_support_new_ticket_btn")
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open issue on latest trip", fontWeight = FontWeight.Bold)
                        }
                        if (driverTrips.size > 1) {
                            HorizontalDivider()
                            Text(
                                text = "Or tap a recent ride below:",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            driverTrips.drop(1).take(3).forEach { trip ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { reportTargetTrip = trip }
                                        .testTag("driver_support_pick_trip_${trip.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatShortTime(trip.createdAt)} · ${trip.status.label}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Report",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("all" to "All", "open" to "Open", "in_progress" to "In progress", "resolved" to "Resolved").forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) },
                        modifier = Modifier.testTag("driver_support_filter_$key")
                    )
                }
            }
        }

        if (filteredCases.isEmpty()) {
            item {
                WayfareEmptyState(
                    icon = Icons.Filled.SupportAgent,
                    title = if (uiState.supportCases.isEmpty()) "No driver tickets yet" else "No $statusFilter tickets",
                    message = "Your safety, rider, and fare review conversations with Vhaya Support appear here."
                )
            }
        } else {
            items(filteredCases, key = { it.id }) { ticket ->
                val statusNormalized = ticket.status.lowercase()
                val chipBg = when (statusNormalized) {
                    "resolved" -> wayfareColors.brandHighlight
                    "in_progress" -> colorScheme.primaryContainer
                    else -> colorScheme.secondaryContainer
                }
                val chipFg = when (statusNormalized) {
                    "resolved" -> colorScheme.onPrimaryContainer
                    "in_progress" -> colorScheme.onPrimaryContainer
                    else -> colorScheme.onSecondaryContainer
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedCaseId = ticket.id }
                        .testTag("driver_support_case_${ticket.id}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "#${ticket.id} · ${resolveSupportCategoryLabel(ticket.category)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (ticket.priority.equals("urgent", ignoreCase = true) || ticket.priority.equals("high", ignoreCase = true)) {
                                    StatusChip(
                                        label = ticket.priority.replaceFirstChar { it.uppercase() },
                                        containerColor = colorScheme.errorContainer,
                                        contentColor = colorScheme.error
                                    )
                                }
                                StatusChip(
                                    label = ticket.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    containerColor = chipBg,
                                    contentColor = chipFg
                                )
                            }
                        }
                        Text(
                            text = ticket.message,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!ticket.staffNote.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Staff note: ${ticket.staffNote}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${ticket.pickup.substringBefore(" ·")} → ${ticket.destination.substringBefore(" ·")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${ticket.replies.size} repl${if (ticket.replies.size == 1) "y" else "ies"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    reportTargetTrip?.let { target ->
        IssueReportingSheet(
            trip = target,
            isDriver = true,
            initialDriverCategory = DriverIssueCategory.RIDER,
            onSubmit = { cat, msg, requestReview, attachmentUri ->
                viewModel.createSupportCase(
                    trip = target,
                    category = cat,
                    priority = if (cat == DriverIssueCategory.SAFETY.key || requestReview) "high" else "normal",
                    message = msg,
                    requestRefund = requestReview,
                    attachmentUri = attachmentUri
                )
                reportTargetTrip = null
            },
            onDismiss = { reportTargetTrip = null }
        )
    }
}
