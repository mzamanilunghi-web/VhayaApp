package com.wayfare.ride.service

import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.SmartRideGuards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.round

enum class FareEstimateSource(val label: String) {
    DISTANCE_AND_TIME("Distance & time meter"),
    LIVE_ROUTE_QUOTE("Route distance & time quote"),
    OFFLINE_ESTIMATE("Offline distance & time")
}

data class FareModifierLine(
    val label: String,
    val amountZar: Double
)

data class FareEstimateRequest(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val category: RideCategory = RideCategory.STANDARD,
    val serverQuoteFareZar: Double? = null,
    val isOfflineEstimated: Boolean = false,
    val isSharedRide: Boolean = false,
    val seatCount: Int = 1,
    val isReturnTrip: Boolean = false,
    val isReturnRequest: Boolean = false,
    val includesGravelRoad: Boolean = false,
    val isRuralArea: Boolean = false
)

data class FareEstimateBreakdown(
    val distanceKm: Double,
    val durationMinutes: Int,
    val baseFareZar: Double,
    val distanceFareZar: Double,
    val timeFareZar: Double,
    val standardSubtotalZar: Double,
    val modifierAdjustmentZar: Double,
    val estimatedFareZar: Double,
    val minRangeZar: Double,
    val maxRangeZar: Double,
    val ratePerKmZar: Double,
    val ratePerMinZar: Double,
    val category: RideCategory,
    val source: FareEstimateSource,
    val modifiers: List<FareModifierLine> = emptyList()
)

class FareEstimationService {

    private val _activeEstimate = MutableStateFlow<FareEstimateBreakdown?>(null)
    val activeEstimate: StateFlow<FareEstimateBreakdown?> = _activeEstimate.asStateFlow()

    fun estimateFare(request: FareEstimateRequest): FareEstimateBreakdown {
        val distanceKm = (request.distanceMeters / 1000.0).coerceAtLeast(0.1)
        val durationMinutesExact = (request.durationSeconds / 60.0).coerceAtLeast(1.0)
        val durationMinutes = round(durationMinutesExact).toInt().coerceAtLeast(1)

        val categoryMult = categoryMultiplier(request.category)
        val minFare = if (request.category == RideCategory.BAKKIE) 30.0 else 20.0

        val effectiveRatePerKm = RATE_PER_KM_ZAR * categoryMult
        val effectiveRatePerMin = RATE_PER_MINUTE_ZAR * categoryMult

        val rawMeteredBase = BASE_FARE_ZAR
        val rawMeteredDistance = distanceKm * RATE_PER_KM_ZAR
        val rawMeteredTime = durationMinutesExact * RATE_PER_MINUTE_ZAR
        val rawMeteredSum = (rawMeteredBase + rawMeteredDistance + rawMeteredTime).coerceAtLeast(1.0)

        val hasServerQuote = request.serverQuoteFareZar != null && request.serverQuoteFareZar > 0.0
        val unadjustedStandardBase = if (hasServerQuote) {
            max(20.0, round(request.serverQuoteFareZar!!))
        } else {
            max(20.0, round(rawMeteredSum))
        }

        val categorySubtotal = max(minFare, round(unadjustedStandardBase * categoryMult))

        val baseShare = round(categorySubtotal * (rawMeteredBase / rawMeteredSum)).coerceAtLeast(0.0)
        val timeShare = round(categorySubtotal * (rawMeteredTime / rawMeteredSum)).coerceAtLeast(0.0)
        val distShare = (categorySubtotal - baseShare - timeShare).coerceAtLeast(0.0)

        val finalEstimate = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = unadjustedStandardBase,
            category = request.category,
            isSharedRide = request.isSharedRide,
            seatCount = request.seatCount,
            isReturnTrip = request.isReturnTrip,
            isReturnRequest = request.isReturnRequest,
            includesGravelRoad = request.includesGravelRoad,
            isRuralArea = request.isRuralArea,
            distanceMeters = request.distanceMeters
        )

        val modifiers = buildModifierLines(
            categorySubtotalZar = categorySubtotal,
            finalEstimatedZar = finalEstimate,
            request = request
        )

        val adjustmentZar = finalEstimate - categorySubtotal
        val rangeLow = max(minFare, round(finalEstimate * 0.95))
        val rangeHigh = max(finalEstimate, round(finalEstimate * 1.08))

        val source = when {
            request.isOfflineEstimated -> FareEstimateSource.OFFLINE_ESTIMATE
            hasServerQuote -> FareEstimateSource.LIVE_ROUTE_QUOTE
            else -> FareEstimateSource.DISTANCE_AND_TIME
        }

        return FareEstimateBreakdown(
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            baseFareZar = baseShare,
            distanceFareZar = distShare,
            timeFareZar = timeShare,
            standardSubtotalZar = categorySubtotal,
            modifierAdjustmentZar = adjustmentZar,
            estimatedFareZar = finalEstimate,
            minRangeZar = rangeLow,
            maxRangeZar = rangeHigh,
            ratePerKmZar = effectiveRatePerKm,
            ratePerMinZar = effectiveRatePerMin,
            category = request.category,
            source = source,
            modifiers = modifiers
        )
    }

    fun estimateForRoute(
        route: RideRoute,
        category: RideCategory = RideCategory.STANDARD,
        isSharedRide: Boolean = false,
        seatCount: Int = 1,
        isReturnTrip: Boolean = false,
        isReturnRequest: Boolean = false,
        includesGravelRoad: Boolean = false,
        isRuralArea: Boolean = false
    ): FareEstimateBreakdown {
        val isOfflineRoute = route.provider == "room-cache" || route.provider == "offline-estimate"
        val request = FareEstimateRequest(
            distanceMeters = route.distanceMeters,
            durationSeconds = route.durationSeconds,
            category = category,
            serverQuoteFareZar = route.fareEstimate,
            isOfflineEstimated = isOfflineRoute,
            isSharedRide = isSharedRide,
            seatCount = seatCount,
            isReturnTrip = isReturnTrip,
            isReturnRequest = isReturnRequest,
            includesGravelRoad = includesGravelRoad,
            isRuralArea = isRuralArea
        )
        return estimateFare(request)
    }

    fun updateActiveEstimate(
        route: RideRoute?,
        category: RideCategory = RideCategory.STANDARD,
        isSharedRide: Boolean = false,
        seatCount: Int = 1,
        isReturnTrip: Boolean = false,
        isReturnRequest: Boolean = false,
        includesGravelRoad: Boolean = false,
        isRuralArea: Boolean = false
    ): FareEstimateBreakdown? {
        val next = route?.let {
            estimateForRoute(
                route = it,
                category = category,
                isSharedRide = isSharedRide,
                seatCount = seatCount,
                isReturnTrip = isReturnTrip,
                isReturnRequest = isReturnRequest,
                includesGravelRoad = includesGravelRoad,
                isRuralArea = isRuralArea
            )
        }
        _activeEstimate.value = next
        return next
    }

    fun clearActiveEstimate() {
        _activeEstimate.value = null
    }

    private fun buildModifierLines(
        categorySubtotalZar: Double,
        finalEstimatedZar: Double,
        request: FareEstimateRequest
    ): List<FareModifierLine> {
        val lines = mutableListOf<FareModifierLine>()
        var running = categorySubtotalZar

        if (request.includesGravelRoad) {
            val next = round(running * 1.12)
            lines += FareModifierLine("Gravel road (+12%)", next - running)
            running = next
        } else if (request.isRuralArea) {
            val next = round(running * 1.08)
            lines += FareModifierLine("Rural corridor (+8%)", next - running)
            running = next
        }

        if (request.isReturnTrip) {
            val next = round(running * 1.85)
            lines += FareModifierLine("Wait & return", next - running)
            running = next
        }

        if (request.isSharedRide) {
            val seats = request.seatCount.coerceIn(1, 4)
            val mult = (0.45 + 0.25 * seats).coerceAtMost(1.20)
            val next = round(running * mult)
            lines += FareModifierLine("Shared ride ($seats seat${if (seats == 1) "" else "s"})", next - running)
            running = next
        }

        if (request.isReturnRequest && !request.isReturnTrip) {
            val diff = finalEstimatedZar - running
            if (diff != 0.0) {
                lines += FareModifierLine("Return Request backhaul", diff)
                running += diff
            }
        }

        val residual = finalEstimatedZar - running
        if (residual != 0.0 && lines.isNotEmpty()) {
            val last = lines.removeAt(lines.lastIndex)
            lines += last.copy(amountZar = last.amountZar + residual)
        } else if (residual != 0.0 && lines.isEmpty()) {
            lines += FareModifierLine("Minimum fare protection", residual)
        }

        return lines.filter { kotlin.math.abs(it.amountZar) >= 0.5 }
    }

    companion object {
        const val BASE_FARE_ZAR = 12.0
        const val RATE_PER_KM_ZAR = 14.5
        const val RATE_PER_MINUTE_ZAR = 2.2

        val defaultInstance: FareEstimationService by lazy { FareEstimationService() }

        fun categoryMultiplier(category: RideCategory): Double = when (category) {
            RideCategory.BAKKIE -> 1.25
            RideCategory.PARCEL -> 0.85
            else -> 1.0
        }

        fun calculateDistanceTimeFare(
            distanceMeters: Int,
            durationSeconds: Int,
            category: RideCategory = RideCategory.STANDARD
        ): Double {
            val distanceKm = (distanceMeters / 1000.0).coerceAtLeast(0.5)
            val durationMinutes = (durationSeconds / 60.0).coerceAtLeast(1.0)
            val minFare = if (category == RideCategory.BAKKIE) 30.0 else 20.0
            val raw = (BASE_FARE_ZAR + distanceKm * RATE_PER_KM_ZAR + durationMinutes * RATE_PER_MINUTE_ZAR) *
                categoryMultiplier(category)
            return max(minFare, round(raw))
        }
    }
}
