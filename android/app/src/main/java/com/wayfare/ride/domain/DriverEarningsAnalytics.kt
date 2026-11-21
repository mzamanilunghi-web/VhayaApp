package com.wayfare.ride.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

enum class EarningsPeriod(
    val label: String,
    val subtitle: String,
    val comparisonLabel: String
) {
    DAILY("Daily", "Last 7 days", "vs yesterday"),
    WEEKLY("Weekly", "Last 6 weeks", "vs last week"),
    MONTHLY("Monthly", "Last 6 months", "vs last month")
}

data class EarningsBarPoint(
    val key: String,
    val label: String,
    val detailLabel: String,
    val netMinor: Long,
    val grossMinor: Long,
    val commissionMinor: Long,
    val tripCount: Int,
    val isCurrentBucket: Boolean
)

data class EarningsPeriodSummary(
    val period: EarningsPeriod,
    val currentNetMinor: Long,
    val previousNetMinor: Long,
    val totalWindowNetMinor: Long,
    val tripCount: Int,
    val avgPerTripMinor: Long,
    val peakNetMinor: Long,
    val peakLabel: String,
    val deltaPercent: Int?,
    val axisMaxMinor: Long,
    val bars: List<EarningsBarPoint>
)

data class DriverEarningsDashboardData(
    val todayNetMinor: Long,
    val todayTrips: Int,
    val weekNetMinor: Long,
    val weekTrips: Int,
    val monthNetMinor: Long,
    val monthTrips: Int,
    val allTimeNetMinor: Long,
    val allTimeTrips: Int,
    val daily: EarningsPeriodSummary,
    val weekly: EarningsPeriodSummary,
    val monthly: EarningsPeriodSummary
) {
    fun summaryFor(period: EarningsPeriod): EarningsPeriodSummary = when (period) {
        EarningsPeriod.DAILY -> daily
        EarningsPeriod.WEEKLY -> weekly
        EarningsPeriod.MONTHLY -> monthly
    }
}

private data class NormalizedEarningEntry(
    val rideId: String,
    val timestampMs: Long,
    val grossMinor: Long,
    val commissionMinor: Long,
    val netMinor: Long
)

object DriverEarningsAnalytics {
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val shortRangeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun computeTodayNetMinor(
        earnings: DriverEarningsSummary,
        completedTrips: List<RideTrip>,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val entries = mergeEntries(earnings, completedTrips)
        if (entries.isEmpty() && earnings.totalNetMinor > 0L) {
            return earnings.totalNetMinor
        }
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return entries
            .filter { Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() == today }
            .sumOf { it.netMinor }
    }

    fun buildDashboard(
        earnings: DriverEarningsSummary,
        completedTrips: List<RideTrip>,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DriverEarningsDashboardData {
        val entries = mergeEntries(earnings, completedTrips)
        val nowZoned = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val today = nowZoned.toLocalDate()
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentMonth = YearMonth.from(today)

        val entriesByDate = entries.groupBy {
            Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate()
        }
        val entriesByWeekStart = entries.groupBy { entry ->
            Instant.ofEpochMilli(entry.timestampMs)
                .atZone(zoneId)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val entriesByMonth = entries.groupBy { entry ->
            YearMonth.from(Instant.ofEpochMilli(entry.timestampMs).atZone(zoneId).toLocalDate())
        }

        // Daily summary: rolling 7 days ending today
        val dailyBars = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val bucket = entriesByDate[date].orEmpty()
            val label = if (offset == 0) {
                "Today"
            } else {
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3)
            }
            val detail = if (offset == 0) {
                "Today · ${date.format(shortRangeFormatter)}"
            } else {
                date.format(dayMonthFormatter)
            }
            EarningsBarPoint(
                key = date.toString(),
                label = label,
                detailLabel = detail,
                netMinor = bucket.sumOf { it.netMinor },
                grossMinor = bucket.sumOf { it.grossMinor },
                commissionMinor = bucket.sumOf { it.commissionMinor },
                tripCount = bucket.size,
                isCurrentBucket = offset == 0
            )
        }
        val todayBucket = entriesByDate[today].orEmpty()
        val yesterdayNet = entriesByDate[today.minusDays(1)].orEmpty().sumOf { it.netMinor }
        val dailySummary = buildPeriodSummary(
            period = EarningsPeriod.DAILY,
            currentNetMinor = todayBucket.sumOf { it.netMinor },
            previousNetMinor = yesterdayNet,
            currentTrips = todayBucket.size,
            bars = dailyBars
        )

        // Weekly summary: last 6 ISO weeks (Mon–Sun) ending current week
        val weekFields = WeekFields.ISO
        val weeklyBars = (5 downTo 0).map { offset ->
            val weekStart = currentWeekStart.minusWeeks(offset.toLong())
            val weekEnd = weekStart.plusDays(6)
            val bucket = entriesByWeekStart[weekStart].orEmpty()
            val weekNum = weekStart.get(weekFields.weekOfWeekBasedYear())
            val label = if (offset == 0) "This wk" else "W$weekNum"
            val detail = "${weekStart.format(shortRangeFormatter)} – ${weekEnd.format(shortRangeFormatter)}"
            EarningsBarPoint(
                key = weekStart.toString(),
                label = label,
                detailLabel = detail,
                netMinor = bucket.sumOf { it.netMinor },
                grossMinor = bucket.sumOf { it.grossMinor },
                commissionMinor = bucket.sumOf { it.commissionMinor },
                tripCount = bucket.size,
                isCurrentBucket = offset == 0
            )
        }
        val thisWeekBucket = entriesByWeekStart[currentWeekStart].orEmpty()
        val lastWeekNet = entriesByWeekStart[currentWeekStart.minusWeeks(1)].orEmpty().sumOf { it.netMinor }
        val weeklySummary = buildPeriodSummary(
            period = EarningsPeriod.WEEKLY,
            currentNetMinor = thisWeekBucket.sumOf { it.netMinor },
            previousNetMinor = lastWeekNet,
            currentTrips = thisWeekBucket.size,
            bars = weeklyBars
        )

        // Monthly summary: last 6 calendar months ending current month
        val monthlyBars = (5 downTo 0).map { offset ->
            val ym = currentMonth.minusMonths(offset.toLong())
            val bucket = entriesByMonth[ym].orEmpty()
            val label = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3)
            val detail = ym.format(monthYearFormatter)
            EarningsBarPoint(
                key = ym.toString(),
                label = label,
                detailLabel = detail,
                netMinor = bucket.sumOf { it.netMinor },
                grossMinor = bucket.sumOf { it.grossMinor },
                commissionMinor = bucket.sumOf { it.commissionMinor },
                tripCount = bucket.size,
                isCurrentBucket = offset == 0
            )
        }
        val thisMonthBucket = entriesByMonth[currentMonth].orEmpty()
        val lastMonthNet = entriesByMonth[currentMonth.minusMonths(1)].orEmpty().sumOf { it.netMinor }
        val monthlySummary = buildPeriodSummary(
            period = EarningsPeriod.MONTHLY,
            currentNetMinor = thisMonthBucket.sumOf { it.netMinor },
            previousNetMinor = lastMonthNet,
            currentTrips = thisMonthBucket.size,
            bars = monthlyBars
        )

        val entriesNet = entries.sumOf { it.netMinor }
        val allTimeNetMinor = if (earnings.totalNetMinor > 0L) {
            maxOf(earnings.totalNetMinor, entriesNet)
        } else {
            entriesNet
        }
        val allTimeTrips = if (earnings.rideCount > 0) {
            maxOf(earnings.rideCount, entries.size)
        } else {
            entries.size
        }

        return DriverEarningsDashboardData(
            todayNetMinor = dailySummary.currentNetMinor,
            todayTrips = todayBucket.size,
            weekNetMinor = weeklySummary.currentNetMinor,
            weekTrips = thisWeekBucket.size,
            monthNetMinor = monthlySummary.currentNetMinor,
            monthTrips = thisMonthBucket.size,
            allTimeNetMinor = allTimeNetMinor,
            allTimeTrips = allTimeTrips,
            daily = dailySummary,
            weekly = weeklySummary,
            monthly = monthlySummary
        )
    }

    private fun mergeEntries(
        earnings: DriverEarningsSummary,
        completedTrips: List<RideTrip>
    ): List<NormalizedEarningEntry> {
        val byId = LinkedHashMap<String, NormalizedEarningEntry>()
        for (rec in earnings.records) {
            val resolvedNet = if (rec.netMinor > 0L || rec.fareMinor <= 0L) {
                rec.netMinor
            } else {
                (rec.fareMinor - rec.commissionMinor).coerceAtLeast(0L)
            }
            byId[rec.rideId] = NormalizedEarningEntry(
                rideId = rec.rideId,
                timestampMs = rec.completedAt,
                grossMinor = rec.fareMinor.coerceAtLeast(resolvedNet),
                commissionMinor = rec.commissionMinor.coerceAtLeast(0L),
                netMinor = resolvedNet.coerceAtLeast(0L)
            )
        }
        for (trip in completedTrips) {
            if (!byId.containsKey(trip.id)) {
                val fareMinor = (trip.fare * 100.0).roundToLong().coerceAtLeast(0L)
                byId[trip.id] = NormalizedEarningEntry(
                    rideId = trip.id,
                    timestampMs = trip.createdAt,
                    grossMinor = fareMinor,
                    commissionMinor = 0L,
                    netMinor = fareMinor
                )
            }
        }
        return byId.values.sortedBy { it.timestampMs }
    }

    private fun buildPeriodSummary(
        period: EarningsPeriod,
        currentNetMinor: Long,
        previousNetMinor: Long,
        currentTrips: Int,
        bars: List<EarningsBarPoint>
    ): EarningsPeriodSummary {
        val windowTotal = bars.sumOf { it.netMinor }
        val windowTrips = bars.sumOf { it.tripCount }
        val totalTrips = if (currentTrips > 0) currentTrips else windowTrips
        val avgMinor = if (currentTrips > 0) {
            currentNetMinor / currentTrips
        } else if (windowTrips > 0) {
            windowTotal / windowTrips
        } else {
            0L
        }
        val peakBar = bars.maxByOrNull { it.netMinor }
        val peakNet = peakBar?.netMinor ?: 0L
        val peakLabel = if (peakNet > 0L) peakBar?.label.orEmpty() else "—"
        val deltaPct = if (previousNetMinor > 0L) {
            (((currentNetMinor - previousNetMinor) * 100.0) / previousNetMinor.toDouble()).roundToLong().toInt()
        } else {
            null
        }
        val maxVal = bars.maxOfOrNull { it.netMinor } ?: 0L
        return EarningsPeriodSummary(
            period = period,
            currentNetMinor = currentNetMinor,
            previousNetMinor = previousNetMinor,
            totalWindowNetMinor = windowTotal,
            tripCount = totalTrips,
            avgPerTripMinor = avgMinor,
            peakNetMinor = peakNet,
            peakLabel = peakLabel,
            deltaPercent = deltaPct,
            axisMaxMinor = computeNiceAxisMaxMinor(maxVal),
            bars = bars
        )
    }

    internal fun computeNiceAxisMaxMinor(maxMinor: Long): Long {
        if (maxMinor <= 0L) return 10_000L // R 100 baseline ceiling
        val zar = maxMinor / 100.0
        val step = when {
            zar <= 100.0 -> 50.0
            zar <= 500.0 -> 100.0
            zar <= 2_000.0 -> 250.0
            zar <= 5_000.0 -> 500.0
            else -> 1_000.0
        }
        val niceZar = (ceil(zar / step) * step).coerceAtLeast(step)
        return (niceZar * 100.0).roundToLong()
    }

    internal fun formatCompactAxisZar(minor: Long): String {
        val zar = minor / 100.0
        return when {
            zar <= 0.0 -> "R 0"
            zar >= 1_000.0 -> {
                val k = zar / 1_000.0
                if (k % 1.0 == 0.0) "R ${k.toInt()}k" else "R ${"%.1f".format(Locale.US, k)}k"
            }
            else -> "R ${zar.roundToLong()}"
        }
    }
}
