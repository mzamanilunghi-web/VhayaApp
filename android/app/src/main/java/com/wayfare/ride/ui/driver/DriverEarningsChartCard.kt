package com.wayfare.ride.ui.driver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverEarningsAnalytics
import com.wayfare.ride.domain.DriverEarningsDashboardData
import com.wayfare.ride.domain.EarningsBarPoint
import com.wayfare.ride.domain.EarningsPeriod
import com.wayfare.ride.domain.EarningsPeriodSummary
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@Composable
internal fun DriverEarningsDashboardCard(
    dashboard: DriverEarningsDashboardData,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    var selectedPeriod by rememberSaveable { mutableStateOf(EarningsPeriod.DAILY) }
    val activeSummary = dashboard.summaryFor(selectedPeriod)
    var selectedBarIndex by remember(selectedPeriod, activeSummary.bars.size) {
        mutableIntStateOf((activeSummary.bars.size - 1).coerceAtLeast(0))
    }
    val safeIndex = selectedBarIndex.coerceIn(0, (activeSummary.bars.size - 1).coerceAtLeast(0))
    val inspectedBar = activeSummary.bars.getOrNull(safeIndex)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("driver_earnings_dashboard_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Header + Period Trend Badge
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = wayfareColors.brandHighlight,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Income performance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedPeriod.label} view · ${selectedPeriod.subtitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                PeriodDeltaBadge(summary = activeSummary)
            }

            // 3-Horizon Summary Strip (Daily · Weekly · Monthly)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PeriodSummaryTile(
                    title = "Daily",
                    amountMinor = dashboard.todayNetMinor,
                    tripCount = dashboard.todayTrips,
                    selected = selectedPeriod == EarningsPeriod.DAILY,
                    onClick = { selectedPeriod = EarningsPeriod.DAILY },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("earnings_summary_daily")
                )
                PeriodSummaryTile(
                    title = "Weekly",
                    amountMinor = dashboard.weekNetMinor,
                    tripCount = dashboard.weekTrips,
                    selected = selectedPeriod == EarningsPeriod.WEEKLY,
                    onClick = { selectedPeriod = EarningsPeriod.WEEKLY },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("earnings_summary_weekly")
                )
                PeriodSummaryTile(
                    title = "Monthly",
                    amountMinor = dashboard.monthNetMinor,
                    tripCount = dashboard.monthTrips,
                    selected = selectedPeriod == EarningsPeriod.MONTHLY,
                    onClick = { selectedPeriod = EarningsPeriod.MONTHLY },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("earnings_summary_monthly")
                )
            }

            // Segmented Period Selector
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    EarningsPeriod.entries.forEach { period ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedPeriod = period }
                                .testTag("earnings_period_${period.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = period.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Selected Bar Inspection Banner
            if (inspectedBar != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.36f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("earnings_bar_callout")
                ) {
                    AnimatedContent(
                        targetState = inspectedBar,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                        label = "inspectedBarCallout"
                    ) { bar ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = bar.detailLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${bar.tripCount} ${if (bar.tripCount == 1) "trip" else "trips"}" +
                                        if (bar.commissionMinor > 0L) {
                                            " · Fee ${LaunchPolicy.formatZarMinor(bar.commissionMinor)}"
                                        } else {
                                            " · Tap any bar to inspect"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = LaunchPolicy.formatZarMinor(bar.netMinor),
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (bar.netMinor > 0L) wayfareColors.positive else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Interactive Bar Chart
            EarningsBarChart(
                bars = activeSummary.bars,
                axisMaxMinor = activeSummary.axisMaxMinor,
                selectedIndex = safeIndex,
                onSelectBar = { selectedBarIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_earnings_bar_chart")
            )

            HorizontalDivider(color = colorScheme.outlineVariant)

            // Period KPI Metrics Footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DashboardMetricCell(
                    label = "${selectedPeriod.subtitle} total",
                    value = LaunchPolicy.formatZarMinor(activeSummary.totalWindowNetMinor),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCell(
                    label = "Avg / trip",
                    value = LaunchPolicy.formatZarMinor(activeSummary.avgPerTripMinor),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCell(
                    label = "Best (${activeSummary.peakLabel})",
                    value = LaunchPolicy.formatZarMinor(activeSummary.peakNetMinor),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PeriodDeltaBadge(summary: EarningsPeriodSummary) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val delta = summary.deltaPercent
    when {
        delta == null && summary.currentNetMinor > 0L -> {
            StatusChip(
                label = "Active ${summary.period.label.lowercase()}",
                containerColor = wayfareColors.emeraldContainer,
                contentColor = wayfareColors.positive
            )
        }
        delta == null -> {
            StatusChip(
                label = "No trips yet",
                containerColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant
            )
        }
        delta >= 0 -> {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = wayfareColors.emeraldContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = wayfareColors.positive,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "+$delta% ${summary.period.comparisonLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = wayfareColors.positive
                    )
                }
            }
        }
        else -> {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colorScheme.errorContainer.copy(alpha = 0.72f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$delta% ${summary.period.comparisonLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSummaryTile(
    title: String,
    amountMinor: Long,
    tripCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) wayfareColors.brandHighlight.copy(alpha = 0.65f) else colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colorScheme.primary else colorScheme.outlineVariant
        ),
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
            )
            Text(
                text = LaunchPolicy.formatZarMinor(amountMinor),
                fontFamily = WayfareMonoFamily,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$tripCount ${if (tripCount == 1) "trip" else "trips"}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EarningsBarChart(
    bars: List<EarningsBarPoint>,
    axisMaxMinor: Long,
    selectedIndex: Int,
    onSelectBar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val safeAxisMax = axisMaxMinor.coerceAtLeast(1L)
    val midAxis = safeAxisMax / 2L
    val gridColor = colorScheme.outlineVariant
    val primaryColor = colorScheme.primary
    val positiveColor = wayfareColors.positive
    val mutedBarColor = colorScheme.primary.copy(alpha = 0.28f)
    val zeroBarColor = colorScheme.outlineVariant

    val animatedFractions = bars.mapIndexed { idx, bar ->
        val rawFraction = if (bar.netMinor <= 0L) 0f else (bar.netMinor.toFloat() / safeAxisMax.toFloat()).coerceIn(0.06f, 1f)
        val fraction by animateFloatAsState(
            targetValue = rawFraction,
            animationSpec = tween(durationMillis = 360, delayMillis = idx * 24, easing = FastOutSlowInEasing),
            label = "barFraction_$idx"
        )
        fraction
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
        ) {
            // Y-axis reference labels
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .padding(end = 6.dp)
            ) {
                Text(
                    text = DriverEarningsAnalytics.formatCompactAxisZar(safeAxisMax),
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = DriverEarningsAnalytics.formatCompactAxisZar(midAxis),
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = "R 0",
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Plot canvas + interactive tap columns
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    val topY = 6.dp.toPx()
                    val bottomY = size.height - 2.dp.toPx()
                    val plotHeight = (bottomY - topY).coerceAtLeast(1f)
                    val midY = topY + plotHeight * 0.5f

                    drawLine(
                        color = gridColor,
                        start = Offset(0f, topY),
                        end = Offset(size.width, topY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, bottomY),
                        end = Offset(size.width, bottomY),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    if (bars.isNotEmpty()) {
                        val slotWidth = size.width / bars.size.toFloat()
                        val barWidth = (slotWidth * 0.54f).coerceIn(14.dp.toPx(), 34.dp.toPx())
                        val cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())

                        bars.forEachIndexed { idx, bar ->
                            val centerX = slotWidth * idx + slotWidth * 0.5f
                            val left = centerX - barWidth * 0.5f
                            val isSelected = idx == selectedIndex
                            val fraction = animatedFractions.getOrElse(idx) { 0f }

                            if (bar.netMinor <= 0L) {
                                val minH = 4.dp.toPx()
                                drawRoundRect(
                                    color = if (isSelected) primaryColor.copy(alpha = 0.55f) else zeroBarColor,
                                    topLeft = Offset(left, bottomY - minH),
                                    size = Size(barWidth, minH),
                                    cornerRadius = cornerRadius
                                )
                            } else {
                                val barHeight = (plotHeight * fraction).coerceAtLeast(8.dp.toPx())
                                val top = bottomY - barHeight
                                val brush = if (isSelected) {
                                    Brush.verticalGradient(
                                        colors = listOf(positiveColor, primaryColor),
                                        startY = top,
                                        endY = bottomY
                                    )
                                } else {
                                    val barTone = if (bar.isCurrentBucket) primaryColor.copy(alpha = 0.62f) else mutedBarColor
                                    Brush.verticalGradient(
                                        colors = listOf(barTone, barTone.copy(alpha = 0.72f)),
                                        startY = top,
                                        endY = bottomY
                                    )
                                }
                                drawRoundRect(
                                    brush = brush,
                                    topLeft = Offset(left, top),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }
                    }
                }

                // Transparent full-height hit columns for accessible 48dp+ bar selection
                Row(modifier = Modifier.fillMaxSize()) {
                    bars.forEachIndexed { idx, bar ->
                        val desc = "${bar.detailLabel}: ${LaunchPolicy.formatZarMinor(bar.netMinor)} from ${bar.tripCount} trips"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { contentDescription = desc }
                                .clickable { onSelectBar(idx) }
                                .testTag("earnings_bar_${idx}")
                        )
                    }
                }
            }
        }

        // X-axis bucket labels aligned with columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp)
        ) {
            bars.forEachIndexed { idx, bar ->
                val isSelected = idx == selectedIndex
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected || bar.isCurrentBucket) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isSelected -> colorScheme.primary
                        bar.isCurrentBucket -> colorScheme.onSurface
                        else -> colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectBar(idx) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            fontFamily = WayfareMonoFamily,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
