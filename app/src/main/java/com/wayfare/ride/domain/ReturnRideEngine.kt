package com.wayfare.ride.domain

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ReturnMatchOriginType(val label: String) {
    NEAR_DROPOFF("Near your drop-off"),
    ALONG_RETURN_CORRIDOR("Along return corridor")
}

data class ReturnRideMatch(
    val trip: RideTrip,
    val originType: ReturnMatchOriginType,
    val distanceFromDropOffKm: Double,
    val corridorDeviationKm: Double,
    val returnProgressKm: Double,
    val etaToPickupMin: Int,
    val suggestedBackhaulFareZar: Double,
    val earningsPerKmZar: Double?,
    val priorityScore: Double
) {
    val proximityKm: Double
        get() = if (originType == ReturnMatchOriginType.NEAR_DROPOFF) {
            distanceFromDropOffKm
        } else {
            corridorDeviationKm
        }

    val proximitySummary: String
        get() = when (originType) {
            ReturnMatchOriginType.NEAR_DROPOFF ->
                "${"%.1f".format(Locale.US, distanceFromDropOffKm)} km from your drop-off"
            ReturnMatchOriginType.ALONG_RETURN_CORRIDOR ->
                "${"%.1f".format(Locale.US, corridorDeviationKm)} km off your return route"
        }
}

data class OutboundDriverApproachingHint(
    val outboundTripId: String,
    val distanceToRiderPickupKm: Double,
    val etaMinutes: Int
)

data class ReturnFareComparison(
    val standardFareZar: Double,
    val discountedFareZar: Double,
    val savingsZar: Double,
    val savingsPercent: Int
)

object ReturnRideEngine {
    const val RETURN_MATCH_RADIUS_KM = 8.0
    const val RETURN_DISCOUNT_MULTIPLIER = 0.82
    const val MIN_VIABLE_RETURN_RATE_PER_KM_ZAR = 12.0
    const val MIN_RETURN_PROGRESS_KM = 0.5

    fun evaluateReturnRideMatch(
        candidate: RideTrip,
        outboundTrip: RideTrip,
        driverLocation: RideCoordinates? = null,
        nowMs: Long = System.currentTimeMillis()
    ): ReturnRideMatch? {
        if (outboundTrip.status != RideStatus.IN_PROGRESS) return null
        if (candidate.id == outboundTrip.id) return null
        if (!candidate.status.isOpenForDrivers || candidate.driverId != null) return null
        if (LaunchPolicy.isTripExpired(candidate, nowMs)) return null

        val outboundOrigin = outboundTrip.pickupCoordinates ?: outboundTrip.route?.originCoordinates ?: return null
        val outboundDropoff = outboundTrip.destinationCoordinates ?: outboundTrip.route?.destinationCoordinates ?: return null

        val candPickup = candidate.pickupCoordinates ?: candidate.route?.originCoordinates ?: return null
        val candDest = candidate.destinationCoordinates ?: candidate.route?.destinationCoordinates ?: return null

        val distanceFromDropOffKm = LaunchPolicy.haversineKm(candPickup, outboundDropoff) ?: return null
        val corridorDeviationKm = DriverMapEngine.pointToSegmentDistanceKm(candPickup, outboundDropoff, outboundOrigin)

        if (distanceFromDropOffKm > RETURN_MATCH_RADIUS_KM && corridorDeviationKm > RETURN_MATCH_RADIUS_KM) {
            return null
        }

        val originType = if (distanceFromDropOffKm <= RETURN_MATCH_RADIUS_KM) {
            ReturnMatchOriginType.NEAR_DROPOFF
        } else {
            ReturnMatchOriginType.ALONG_RETURN_CORRIDOR
        }

        val pickupToOriginKm = LaunchPolicy.haversineKm(candPickup, outboundOrigin) ?: return null
        val destToOriginKm = LaunchPolicy.haversineKm(candDest, outboundOrigin) ?: return null
        val returnProgressKm = pickupToOriginKm - destToOriginKm

        // Candidate destination must make genuine net progress back toward the driver's outbound origin,
        // unless pickup is already right inside the town origin radius and drops off inside it.
        if (pickupToOriginKm <= 1.2) {
            if (destToOriginKm > RETURN_MATCH_RADIUS_KM) return null
        } else {
            if (returnProgressKm < MIN_RETURN_PROGRESS_KM) return null
            val alignmentCos = directionalAlignmentCosine(
                corridorStart = outboundDropoff,
                corridorEnd = outboundOrigin,
                candStart = candPickup,
                candEnd = candDest
            )
            val destDeviationKm = DriverMapEngine.pointToSegmentDistanceKm(candDest, outboundDropoff, outboundOrigin)
            val endsNearOriginOrCorridor =
                destToOriginKm <= RETURN_MATCH_RADIUS_KM ||
                    destDeviationKm <= RETURN_MATCH_RADIUS_KM * 1.35
            if (!endsNearOriginOrCorridor || alignmentCos < 0.15) return null
        }

        val tripKm = (candidate.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
            ?: LaunchPolicy.haversineKm(candPickup, candDest)
            ?: 1.0).coerceAtLeast(0.5)
        val distanceMeters = candidate.route?.distanceMeters?.takeIf { it > 0 }
            ?: (tripKm * 1000.0).toInt()
        val earningsPerKm = if (tripKm > 0.1) candidate.fare / tripKm else null

        val suggestedBackhaulFareZar = if (candidate.isReturnRequest) {
            candidate.fare
        } else {
            SmartRideGuards.calculateBackhaulReturnFare(
                standardFareZar = candidate.fare,
                distanceMeters = distanceMeters,
                category = candidate.category
            )
        }

        val etaAnchor = driverLocation ?: outboundDropoff
        val anchorToPickupKm = LaunchPolicy.haversineKm(etaAnchor, candPickup) ?: distanceFromDropOffKm
        val etaMin = ceil((anchorToPickupKm * 1000.0) / 550.0).toInt().coerceAtLeast(1)

        val proximityBonus = (RETURN_MATCH_RADIUS_KM - min(distanceFromDropOffKm, corridorDeviationKm)).coerceAtLeast(0.0) * 4.0
        val taggedBonus = if (candidate.isReturnRequest) 40.0 else 0.0
        val priorityScore = taggedBonus + proximityBonus + returnProgressKm * 1.2 + (earningsPerKm ?: 12.0)

        return ReturnRideMatch(
            trip = candidate,
            originType = originType,
            distanceFromDropOffKm = distanceFromDropOffKm,
            corridorDeviationKm = corridorDeviationKm,
            returnProgressKm = returnProgressKm,
            etaToPickupMin = etaMin,
            suggestedBackhaulFareZar = suggestedBackhaulFareZar,
            earningsPerKmZar = earningsPerKm,
            priorityScore = priorityScore
        )
    }

    fun findReturnRideMatches(
        trips: List<RideTrip>,
        outboundTrip: RideTrip?,
        declinedIds: Set<String> = emptySet(),
        expiredIds: Set<String> = emptySet(),
        driverLocation: RideCoordinates? = null,
        nowMs: Long = System.currentTimeMillis()
    ): List<ReturnRideMatch> {
        if (outboundTrip == null || outboundTrip.status != RideStatus.IN_PROGRESS) return emptyList()
        return trips
            .asSequence()
            .filter { it.id !in declinedIds && it.id !in expiredIds }
            .mapNotNull { candidate ->
                evaluateReturnRideMatch(
                    candidate = candidate,
                    outboundTrip = outboundTrip,
                    driverLocation = driverLocation,
                    nowMs = nowMs
                )
            }
            .sortedByDescending { it.priorityScore }
            .toList()
    }

    fun findApproachingReturnDriverHint(
        trips: List<RideTrip>,
        riderPickup: RideCoordinates,
        riderDest: RideCoordinates?,
        activeUserId: String,
        radiusKm: Double = RETURN_MATCH_RADIUS_KM
    ): OutboundDriverApproachingHint? {
        return trips
            .asSequence()
            .filter { outbound ->
                outbound.status == RideStatus.IN_PROGRESS &&
                    outbound.riderId != activeUserId &&
                    outbound.driverId != activeUserId
            }
            .mapNotNull { outbound ->
                val outOrigin = outbound.pickupCoordinates ?: outbound.route?.originCoordinates
                val outDropoff = outbound.destinationCoordinates ?: outbound.route?.destinationCoordinates ?: return@mapNotNull null
                val driverPos = outbound.liveDriverLocation ?: outOrigin

                val dropoffDistKm = LaunchPolicy.haversineKm(riderPickup, outDropoff) ?: Double.MAX_VALUE
                val corridorDistKm = if (outOrigin != null) {
                    DriverMapEngine.pointToSegmentDistanceKm(riderPickup, outDropoff, outOrigin)
                } else {
                    dropoffDistKm
                }
                if (dropoffDistKm > radiusKm && corridorDistKm > radiusKm) return@mapNotNull null

                if (riderDest != null && outOrigin != null) {
                    val pickupToOrigin = LaunchPolicy.haversineKm(riderPickup, outOrigin) ?: 0.0
                    val destToOrigin = LaunchPolicy.haversineKm(riderDest, outOrigin) ?: 0.0
                    if (pickupToOrigin > 1.2 && destToOrigin >= pickupToOrigin) {
                        return@mapNotNull null
                    }
                }

                val relevantDistKm = driverPos?.let { LaunchPolicy.haversineKm(it, riderPickup) } ?: dropoffDistKm
                val etaMin = ceil((relevantDistKm * 1000.0) / 550.0).toInt().coerceAtLeast(1)
                OutboundDriverApproachingHint(
                    outboundTripId = outbound.id,
                    distanceToRiderPickupKm = min(dropoffDistKm, corridorDistKm),
                    etaMinutes = etaMin
                )
            }
            .minByOrNull { it.distanceToRiderPickupKm }
    }

    private fun directionalAlignmentCosine(
        corridorStart: RideCoordinates,
        corridorEnd: RideCoordinates,
        candStart: RideCoordinates,
        candEnd: RideCoordinates
    ): Double {
        val avgLat = Math.toRadians((corridorStart.latitude + corridorEnd.latitude) * 0.5)
        val cosLat = cos(avgLat).coerceAtLeast(0.2)
        val cx = (corridorEnd.longitude - corridorStart.longitude) * cosLat
        val cy = corridorEnd.latitude - corridorStart.latitude
        val rx = (candEnd.longitude - candStart.longitude) * cosLat
        val ry = candEnd.latitude - candStart.latitude
        val cNorm = sqrt(cx * cx + cy * cy)
        val rNorm = sqrt(rx * rx + ry * ry)
        if (cNorm <= 1e-9 || rNorm <= 1e-9) return 0.0
        return (cx * rx + cy * ry) / (cNorm * rNorm)
    }
}
