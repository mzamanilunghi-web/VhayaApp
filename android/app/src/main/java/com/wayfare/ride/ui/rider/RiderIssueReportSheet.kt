package com.wayfare.ride.ui.rider

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wayfare.ride.domain.DriverIssueCategory
import com.wayfare.ride.domain.IssueCategory
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IssueReportingSheet(
    trip: RideTrip,
    initialCategory: IssueCategory = IssueCategory.SAFETY,
    isDriver: Boolean = false,
    initialDriverCategory: DriverIssueCategory = DriverIssueCategory.SAFETY,
    onSubmit: (category: String, message: String, requestRefund: Boolean, attachmentUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    var selectedCategory by remember(trip.id, initialCategory) { mutableStateOf(initialCategory) }
    var selectedDriverCategory by remember(trip.id, initialDriverCategory) { mutableStateOf(initialDriverCategory) }
    var description by remember(trip.id) { mutableStateOf("") }
    var requestRefund by remember(trip.id, initialCategory, initialDriverCategory, isDriver) {
        mutableStateOf(
            if (isDriver) initialDriverCategory == DriverIssueCategory.FARE_REVIEW
            else initialCategory == IssueCategory.PAYMENT
        )
    }
    var attachmentUri by remember(trip.id) { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachmentUri = uri
        }
    }

    BackHandler { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = colorScheme.error)
                    Text(
                        text = if (isDriver) "Report driver trip issue" else "Report trip issue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close issue form")
                }
            }

            Text(
                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")} · ${LaunchPolicy.formatZar(trip.fare)}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDriver) {
                    DriverIssueCategory.entries.forEach { category ->
                        val active = selectedDriverCategory == category
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedDriverCategory = category
                                if (category == DriverIssueCategory.FARE_REVIEW) {
                                    requestRefund = true
                                }
                            },
                            label = { Text(category.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = wayfareColors.brandHighlight,
                                selectedLabelColor = colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("issue_category_${category.key}")
                        )
                    }
                } else {
                    IssueCategory.entries.forEach { category ->
                        val active = selectedCategory == category
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedCategory = category
                                if (category == IssueCategory.PAYMENT) {
                                    requestRefund = true
                                }
                            },
                            label = { Text(category.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = wayfareColors.brandHighlight,
                                selectedLabelColor = colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("issue_category_${category.key}")
                        )
                    }
                }
            }

            Text(
                text = if (isDriver) selectedDriverCategory.description else selectedCategory.description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isDriver) "Request fare / payout review" else "Request refund / fare review",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${LaunchPolicy.formatZar(trip.fare)} via ${trip.paymentMethod.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = requestRefund,
                        onCheckedChange = { requestRefund = it },
                        modifier = Modifier.testTag("issue_refund_switch")
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What happened?") },
                placeholder = {
                    Text(
                        if (isDriver) {
                            "Share rider details, no-show wait time, pickup issue, or payout concern..."
                        } else {
                            "Share trip details, driver conduct, lost item, or billing concern..."
                        }
                    )
                },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("issue_description_input")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("issue_attach_photo_btn")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (attachmentUri == null) "Attach photo (optional)" else "Change photo")
                }
                if (attachmentUri != null) {
                    OutlinedButton(
                        onClick = { attachmentUri = null },
                        modifier = Modifier.height(48.dp).testTag("issue_remove_photo_btn")
                    ) {
                        Text("Remove")
                    }
                }
            }

            attachmentUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Attached evidence photo preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("issue_photo_preview")
                )
            }

            val telemetryPreview = remember(trip.id, trip.status, trip.fare, trip.paymentStatus, trip.messages.size) {
                SmartRideGuards.buildSupportTelemetrySummary(trip)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("issue_telemetry_preview")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(
                        text = "Auto-attached Trip Telemetry",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    Text(
                        text = telemetryPreview,
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        val chosenKey = if (isDriver) selectedDriverCategory.key else selectedCategory.key
                        onSubmit(chosenKey, description.trim(), requestRefund, attachmentUri)
                    }
                },
                enabled = description.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("issue_submit_btn")
            ) {
                Text("Submit to Vhaya Support", fontWeight = FontWeight.Bold)
            }
        }
    }
}
