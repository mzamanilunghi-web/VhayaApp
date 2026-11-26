package com.wayfare.ride.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverPocketRoute
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PocketItem
import com.wayfare.ride.domain.PocketItemSize
import com.wayfare.ride.domain.PocketItemStatus
import com.wayfare.ride.domain.PocketItemType
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderPocketSheet(
    pocketRoutes: List<DriverPocketRoute>,
    pocketItems: List<PocketItem>,
    onClose: () -> Unit,
    onAddParcelToPocket: (routeId: String, desc: String, size: PocketItemSize, recipientName: String, recipientPhone: String) -> Unit,
    onPostPocketRequest: (origin: String, dest: String, desc: String, size: PocketItemSize, fee: Double, recipientName: String, recipientPhone: String) -> Unit,
    onSelectRoute: (DriverPocketRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var bookingRoute by remember { mutableStateOf<DriverPocketRoute?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = colorScheme.surface,
        modifier = modifier.testTag("rider_pocket_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(wayfareColors.brandHighlight.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = "Pocket cargo",
                            tint = wayfareColors.brandHighlight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Pocket · Long Distance & Parcels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Send goods or book seats on drivers passing by",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_pocket_btn")) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Drivers Passing By (${pocketRoutes.size})", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Post Request", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("My Parcels (${pocketItems.size})", style = MaterialTheme.typography.labelMedium) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTabIndex) {
                0 -> {
                    // Browse Passing & Scheduled Drivers
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search corridor") },
                        placeholder = { Text("Filter town (e.g. Malamulele, Tembisa, Joburg)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("pocket_corridor_search_input")
                    )

                    val filteredRoutes = remember(searchQuery, pocketRoutes) {
                        if (searchQuery.isBlank()) pocketRoutes
                        else pocketRoutes.filter {
                            it.originLabel.contains(searchQuery, ignoreCase = true) ||
                                it.destinationLabel.contains(searchQuery, ignoreCase = true) ||
                                it.driverName.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filteredRoutes.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalShipping,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No drivers found for this corridor yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "You can register your parcel or route on the Pocket board tab!",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 440.dp)
                        ) {
                            items(filteredRoutes, key = { it.id }) { route ->
                                DriverPocketRouteCard(
                                    route = route,
                                    onAddParcelClick = { bookingRoute = route },
                                    onSelectRoute = { onSelectRoute(route) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Post Request to Pocket Board
                    RiderPostPocketRequestForm(
                        onSubmit = { origin, dest, desc, size, fee, rName, rPhone ->
                            onPostPocketRequest(origin, dest, desc, size, fee, rName, rPhone)
                            selectedTabIndex = 2
                        }
                    )
                }
                2 -> {
                    // My Sent Parcels
                    if (pocketItems.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Inventory2,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No pocket parcels sent yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Select a driver passing by to add a parcel to their pocket.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 440.dp)
                        ) {
                            items(pocketItems, key = { it.id }) { item ->
                                PocketItemCard(item = item, isDriverView = false)
                            }
                        }
                    }
                }
            }
        }
    }

    bookingRoute?.let { route ->
        BookParcelIntoPocketDialog(
            route = route,
            onDismiss = { bookingRoute = null },
            onConfirm = { desc, size, rName, rPhone ->
                onAddParcelToPocket(route.id, desc, size, rName, rPhone)
                bookingRoute = null
                selectedTabIndex = 2
            }
        )
    }
}

@Composable
fun DriverPocketRouteCard(
    route: DriverPocketRoute,
    onAddParcelClick: () -> Unit,
    onSelectRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pocket_route_${route.id}")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = route.originLabel.substringBefore(" ·").substringBefore(","),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "to",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = route.destinationLabel.substringBefore(" ·").substringBefore(","),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Alarm,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = route.departureTimeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            // Driver info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${route.driverName} · ${route.vehicleModel} (${route.vehicleColor})",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Capacity chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = wayfareColors.brandHighlight.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Pocket: ${route.availableParcelSlots} slots left (from ${LaunchPolicy.formatZar(route.baseParcelPriceZar)})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${route.availableSeats} seats (${LaunchPolicy.formatZar(route.seatPriceZar)})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (route.notes.isNotBlank()) {
                Text(
                    text = "“${route.notes}”",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAddParcelClick,
                    shape = RoundedCornerShape(12.dp),
                    enabled = route.availableParcelSlots > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("add_parcel_btn_${route.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Pocket", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSelectRoute,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EventSeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reserve Seat")
                }
            }
        }
    }
}

@Composable
fun BookParcelIntoPocketDialog(
    route: DriverPocketRoute,
    onDismiss: () -> Unit,
    onConfirm: (desc: String, size: PocketItemSize, recipientName: String, recipientPhone: String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var desc by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(PocketItemSize.MEDIUM_BAG) }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("book_parcel_into_pocket_dialog")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Add Item to Driver's Pocket",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${route.driverName} · ${route.originLabel.substringBefore(",")} → ${route.destinationLabel.substringBefore(",")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            Text(
                text = "Item details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                placeholder = { Text("e.g. Box of clothes, Matric documents, Food parcel") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pocket_item_desc_input")
            )

            Text(
                text = "Package size",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PocketItemSize.entries.forEach { size ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedSize == size) colorScheme.primary.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (selectedSize == size) colorScheme.primary else colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSize = size }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = size.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Up to ${size.maxKg} kg",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = LaunchPolicy.formatZar(size.baseFeeZar),
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            Text(
                text = "Recipient in destination town",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )

            OutlinedTextField(
                value = recipientName,
                onValueChange = { recipientName = it },
                placeholder = { Text("Recipient name (e.g. Musa Baloyi)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pocket_recipient_name_input")
            )

            OutlinedTextField(
                value = recipientPhone,
                onValueChange = { recipientPhone = it },
                placeholder = { Text("Recipient phone (e.g. 082 123 4567)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pocket_recipient_phone_input")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (desc.isNotBlank()) {
                        onConfirm(desc, selectedSize, recipientName, recipientPhone)
                    }
                },
                enabled = desc.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("confirm_pocket_booking_btn")
            ) {
                Text(
                    text = "Pay & Place in Pocket (${LaunchPolicy.formatZar(selectedSize.baseFeeZar)})",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RiderPostPocketRequestForm(
    onSubmit: (origin: String, dest: String, desc: String, size: PocketItemSize, fee: Double, recipientName: String, recipientPhone: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf("Malamulele Main Plaza & Rank") }
    var dest by remember { mutableStateOf("Tembisa Plaza & Mall") }
    var desc by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(PocketItemSize.MEDIUM_BAG) }
    var offeredFeeText by remember { mutableStateOf("140") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Post on Pocket Board for Passing Drivers",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )
        Text(
            text = "Drivers travelling along this corridor (via GTW or scheduled) will see your goods and can claim them into their pocket.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = origin,
            onValueChange = { origin = it },
            label = { Text("Pickup Town / Rank") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = dest,
            onValueChange = { dest = it },
            label = { Text("Drop-off Town / Suburb") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Item description (e.g. 5kg clothing, Spare parts)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("pocket_post_desc_input")
        )

        Text(
            text = "Package size",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PocketItemSize.entries.forEach { size ->
                FilterChip(
                    selected = selectedSize == size,
                    onClick = {
                        selectedSize = size
                        offeredFeeText = size.baseFeeZar.toInt().toString()
                    },
                    label = { Text(size.name.lowercase().replace('_', ' ')) }
                )
            }
        }

        OutlinedTextField(
            value = offeredFeeText,
            onValueChange = { offeredFeeText = it },
            label = { Text("Offered Fare (ZAR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = recipientName,
            onValueChange = { recipientName = it },
            label = { Text("Recipient Name & Dropoff spot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = recipientPhone,
            onValueChange = { recipientPhone = it },
            label = { Text("Recipient Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val fee = offeredFeeText.toDoubleOrNull() ?: selectedSize.baseFeeZar
                if (desc.isNotBlank() && origin.isNotBlank() && dest.isNotBlank()) {
                    onSubmit(origin, dest, desc, selectedSize, fee, recipientName, recipientPhone)
                }
            },
            enabled = desc.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("submit_pocket_request_btn")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Publish to Corridor Board", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PocketItemCard(
    item: PocketItem,
    isDriverView: Boolean,
    onStatusChange: ((PocketItemStatus) -> Unit)? = null,
    onClaimClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pocket_item_card_${item.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.itemDescription,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (item.status) {
                        PocketItemStatus.LISTED -> colorScheme.surfaceVariant
                        PocketItemStatus.RESERVED -> wayfareColors.brandHighlight.copy(alpha = 0.2f)
                        PocketItemStatus.COLLECTED,
                        PocketItemStatus.IN_TRANSIT -> colorScheme.primary.copy(alpha = 0.15f)
                        PocketItemStatus.DELIVERED -> wayfareColors.positive.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = item.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            PocketItemStatus.LISTED -> colorScheme.onSurfaceVariant
                            PocketItemStatus.RESERVED -> colorScheme.onSurface
                            PocketItemStatus.COLLECTED,
                            PocketItemStatus.IN_TRANSIT -> colorScheme.primary
                            PocketItemStatus.DELIVERED -> wayfareColors.positive
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Route
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${item.originLabel.substringBefore(",")} → ${item.destinationLabel.substringBefore(",")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Fare and size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.size.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(text = "·", color = colorScheme.outline)
                    Text(
                        text = LaunchPolicy.formatZar(item.fareZar),
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }

                if (item.recipientPin.isNotBlank()) {
                    Text(
                        text = "PIN: ${item.recipientPin}",
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = wayfareColors.positive
                    )
                }
            }

            if (item.recipientName.isNotBlank() || item.recipientPhone.isNotBlank()) {
                Text(
                    text = "Recipient: ${item.recipientName} (${item.recipientPhone})",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            if (isDriverView) {
                if (item.status == PocketItemStatus.LISTED && onClaimClick != null) {
                    Button(
                        onClick = onClaimClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Claim into My Pocket")
                    }
                } else if (onStatusChange != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        if (item.status == PocketItemStatus.RESERVED) {
                            Button(
                                onClick = { onStatusChange(PocketItemStatus.COLLECTED) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Mark Collected")
                            }
                        } else if (item.status == PocketItemStatus.COLLECTED) {
                            Button(
                                onClick = { onStatusChange(PocketItemStatus.IN_TRANSIT) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Depart / In Transit")
                            }
                        } else if (item.status == PocketItemStatus.IN_TRANSIT) {
                            Button(
                                onClick = { onStatusChange(PocketItemStatus.DELIVERED) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = wayfareColors.positive),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify PIN & Deliver")
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
fun DriverPocketSheet(
    pocketRoutes: List<DriverPocketRoute>,
    pocketItems: List<PocketItem>,
    onClose: () -> Unit,
    onCreateRoute: (origin: String, dest: String, time: String, seats: Int, seatPrice: Double, parcelSlots: Int, baseFee: Double, notes: String) -> Unit,
    onClaimItem: (itemId: String) -> Unit,
    onUpdateItemStatus: (itemId: String, PocketItemStatus) -> Unit,
    onNotifySendersAndDepart: (routeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isCreatingRoute by remember { mutableStateOf(false) }

    val myActiveRoute = pocketRoutes.firstOrNull { it.isActive }
    val myPocketItems = pocketItems.filter { it.assignedDriverRouteId == myActiveRoute?.id || it.status != PocketItemStatus.LISTED }
    val openBoardItems = pocketItems.filter { it.status == PocketItemStatus.LISTED }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = colorScheme.surface,
        modifier = modifier.testTag("driver_pocket_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(wayfareColors.brandHighlight.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = "Driver Pocket",
                            tint = wayfareColors.brandHighlight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Driver Pocket & Cargo Boot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (myActiveRoute != null) "${myActiveRoute.originLabel.substringBefore(",")} → ${myActiveRoute.destinationLabel.substringBefore(",")}" else "No active route registered",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_driver_pocket_btn")) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pocket Manifest (${myPocketItems.size})", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Corridor Board (${openBoardItems.size})", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Schedule Route", style = MaterialTheme.typography.labelMedium) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // Pre-departure notification banner
                    if (myPocketItems.isNotEmpty()) {
                        val totalEarnings = myPocketItems.sumOf { it.fareZar }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = wayfareColors.emeraldContainer,
                            border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = wayfareColors.positive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Departure Alert · ${myPocketItems.size} items in Pocket",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = wayfareColors.positive
                                    )
                                }
                                Text(
                                    text = "Total cargo earnings: ${LaunchPolicy.formatZar(totalEarnings)}. Tap to notify senders that you are preparing to depart.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface
                                )
                                Button(
                                    onClick = { myActiveRoute?.id?.let { onNotifySendersAndDepart(it) } },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = wayfareColors.positive),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Notify Senders & Start Run", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (myPocketItems.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Inventory2,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your pocket is currently empty",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Claim parcels from the Corridor Board or schedule a new long-distance corridor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(myPocketItems, key = { it.id }) { item ->
                                PocketItemCard(
                                    item = item,
                                    isDriverView = true,
                                    onStatusChange = { newStatus -> onUpdateItemStatus(item.id, newStatus) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Open board items
                    if (openBoardItems.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp)
                        ) {
                            Text(
                                text = "No open parcel requests right now",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "New requests posted along your corridor will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(openBoardItems, key = { it.id }) { item ->
                                PocketItemCard(
                                    item = item,
                                    isDriverView = true,
                                    onClaimClick = { onClaimItem(item.id) }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Schedule new route form
                    DriverCreatePocketRouteForm(
                        onSubmit = { origin, dest, time, seats, sPrice, slots, bPrice, notes ->
                            onCreateRoute(origin, dest, time, seats, sPrice, slots, bPrice, notes)
                            selectedTab = 0
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DriverCreatePocketRouteForm(
    onSubmit: (origin: String, dest: String, time: String, seats: Int, seatPrice: Double, parcelSlots: Int, baseFee: Double, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf("Malamulele Main Plaza & Rank") }
    var dest by remember { mutableStateOf("Tembisa Plaza & Mall") }
    var time by remember { mutableStateOf("Today 15:00") }
    var seatsText by remember { mutableStateOf("3") }
    var seatPriceText by remember { mutableStateOf("250") }
    var parcelSlotsText by remember { mutableStateOf("4") }
    var baseFeeText by remember { mutableStateOf("120") }
    var notes by remember { mutableStateOf("Passing Polokwane and Pretoria to Tembisa. Large canopy boot.") }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Publish Long-Distance Route with Pocket",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )

        OutlinedTextField(
            value = origin,
            onValueChange = { origin = it },
            label = { Text("Departure Origin (e.g. Malamulele Box)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = dest,
            onValueChange = { dest = it },
            label = { Text("Destination (e.g. Tembisa Plaza / Joburg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            label = { Text("Departure Time (e.g. Today 15:00, Tomorrow 06:00)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("driver_pocket_departure_time_input")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = seatsText,
                onValueChange = { seatsText = it },
                label = { Text("Available Seats") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = seatPriceText,
                onValueChange = { seatPriceText = it },
                label = { Text("Price/Seat (R)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = parcelSlotsText,
                onValueChange = { parcelSlotsText = it },
                label = { Text("Pocket Slots") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = baseFeeText,
                onValueChange = { baseFeeText = it },
                label = { Text("Base Parcel (R)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Corridor details / vehicle notes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val seats = seatsText.toIntOrNull() ?: 3
                val sPrice = seatPriceText.toDoubleOrNull() ?: 250.0
                val slots = parcelSlotsText.toIntOrNull() ?: 4
                val bPrice = baseFeeText.toDoubleOrNull() ?: 120.0
                if (origin.isNotBlank() && dest.isNotBlank()) {
                    onSubmit(origin, dest, time, seats, sPrice, slots, bPrice, notes)
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("publish_driver_pocket_route_btn")
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Publish Route to Pocket Board", fontWeight = FontWeight.Bold)
        }
    }
}
