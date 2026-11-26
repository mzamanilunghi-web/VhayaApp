package com.wayfare.ride.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

@Serializable
enum class HazardType(
    val key: String,
    val label: String,
    val shortBadge: String,
    val detailHint: String,
    val defaultTtlMs: Long
) {
    @SerialName("gravel_mud")
    GRAVEL_MUD(
        key = "gravel_mud",
        label = "Gravel / Mud",
        shortBadge = "Mud",
        detailHint = "Slippery mud or rough unpaved stretch",
        defaultTtlMs = 12L * 60 * 60_000L
    ),

    @SerialName("flooded_drift")
    FLOODED_DRIFT(
        key = "flooded_drift",
        label = "Flooded Bridge",
        shortBadge = "Flood",
        detailHint = "Low-water bridge / drift under water",
        defaultTtlMs = 6L * 60 * 60_000L
    ),

    @SerialName("potholes")
    POTHOLES(
        key = "potholes",
        label = "Severe Potholes",
        shortBadge = "Pothole",
        detailHint = "Deep dongas or broken tar ahead",
        defaultTtlMs = 24L * 60 * 60_000L
    ),

    @SerialName("roadblock")
    ROADBLOCK(
        key = "roadblock",
        label = "Roadblock / Stop",
        shortBadge = "Stop",
        detailHint = "Police checkpoint, roadworks, or closure",
        defaultTtlMs = 3L * 60 * 60_000L
    );

    companion object {
        fun fromKey(raw: String?): HazardType =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: GRAVEL_MUD
    }
}

@Serializable
data class RoadHazardPin(
    val id: String,
    val type: HazardType,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val note: String? = null,
    val reportedBy: String = "Driver",
    val reportedAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long = System.currentTimeMillis() + type.defaultTtlMs,
    val confirmations: Int = 1
) {
    val coordinates: RideCoordinates
        get() = RideCoordinates(latitude, longitude)

    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean = expiresAtMs > nowMs
}

@Serializable
data class DriverCorridorTarget(
    val label: String,
    val destination: RideCoordinates,
    val maxDetourKm: Double = 6.0,
    val strictFilter: Boolean = true,
    val updatedAtMs: Long = 0L
)

data class GtwTripMatch(
    val tripId: String,
    val addedDetourKm: Double,
    val directionalAlignment: Double,
    val progressTowardTargetKm: Double,
    val pickupToCorridorKm: Double,
    val endsNearTargetKm: Double,
    val matchScore: Double
)

data class MapRequestMarker(
    val tripId: String,
    val pickup: RideCoordinates,
    val destination: RideCoordinates?,
    val fareZar: Double,
    val fareLabel: String,
    val ratePerKmZar: Double?,
    val isHighYield: Boolean,
    val category: RideCategory,
    val corridorMatched: Boolean,
    val isReturnRideMatch: Boolean = false,
    val gtwDetourKm: Double? = null
)

data class DeadheadBreakdown(
    val deadheadKm: Double,
    val paidTripKm: Double,
    val totalKm: Double,
    val paidShareFraction: Float
)

data class OfflineCorridorPack(
    val id: String,
    val title: String,
    val subtitle: String,
    val origin: RideCoordinates,
    val destination: RideCoordinates
)

@Serializable
data class OfflineCorridorPackStatus(
    val packId: String,
    val downloadedTiles: Int,
    val totalTiles: Int,
    val cachedAtMs: Long
) {
    val isComplete: Boolean
        get() = totalTiles > 0 && downloadedTiles >= totalTiles
}

data class TileCoordinate(
    val zoom: Int,
    val x: Int,
    val y: Int
)

object DriverMapEngine {

    val PRESET_CORRIDOR_PACKS: List<OfflineCorridorPack> = listOf(
        OfflineCorridorPack(
            id = "pack_polokwane_mankweng",
            title = "Polokwane ↔ Mankweng (R71)",
            subtitle = "CBD, Nobody, Turfloop & Mankweng Hospital",
            origin = RideCoordinates(-23.9045, 29.4689),
            destination = RideCoordinates(-23.8880, 29.7240)
        ),
        OfflineCorridorPack(
            id = "pack_polokwane_seshego",
            title = "Polokwane ↔ Seshego",
            subtitle = "Circle Centre, Blood River & Luthuli Park",
            origin = RideCoordinates(-23.9045, 29.4689),
            destination = RideCoordinates(-23.8290, 29.3570)
        ),
        OfflineCorridorPack(
            id = "pack_polokwane_sebayeng",
            title = "Polokwane ↔ Sebayeng",
            subtitle = "Solomondale, Sebayeng & R81 corridor",
            origin = RideCoordinates(-23.9045, 29.4689),
            destination = RideCoordinates(-23.7580, 29.6650)
        ),
        OfflineCorridorPack(
            id = "pack_mankweng_villages",
            title = "Mankweng ↔ Ga-Mothapo & Ga-Dikgale",
            subtitle = "Village feeder gravel & clinic routes",
            origin = RideCoordinates(-23.8880, 29.7240),
            destination = RideCoordinates(-23.8165, 29.7540)
        ),
        OfflineCorridorPack(
            id = "pack_polokwane_lebowakgomo",
            title = "Polokwane ↔ Lebowakgomo",
            subtitle = "R37, Chuenespoort Gate & Ga-Maja",
            origin = RideCoordinates(-23.9045, 29.4689),
            destination = RideCoordinates(-24.3030, 29.5050)
        )
    )

    fun evaluateGtwMatch(
        trip: RideTrip,
        driverLocation: RideCoordinates?,
        corridor: DriverCorridorTarget?
    ): GtwTripMatch? {
        if (corridor == null) return null
        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return null
        val dest = trip.destinationCoordinates ?: trip.route?.destinationCoordinates ?: return null
        val origin = driverLocation ?: pickup

        val originToTargetKm = LaunchPolicy.haversineKm(origin, corridor.destination) ?: 0.0
        val originToPickupKm = LaunchPolicy.haversineKm(origin, pickup) ?: 0.0
        val pickupToDestKm = (trip.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
            ?: LaunchPolicy.haversineKm(pickup, dest)
            ?: 0.0).coerceAtLeast(0.0)
        val destToHomeKm = LaunchPolicy.haversineKm(dest, corridor.destination) ?: Double.MAX_VALUE
        val pickupToHomeKm = LaunchPolicy.haversineKm(pickup, corridor.destination) ?: Double.MAX_VALUE
        val addedDetourKm = (originToPickupKm + pickupToDestKm + destToHomeKm - originToTargetKm).coerceAtLeast(0.0)

        if (originToTargetKm < 0.8) {
            if (destToHomeKm > corridor.maxDetourKm) return null
            return GtwTripMatch(
                tripId = trip.id,
                addedDetourKm = addedDetourKm,
                directionalAlignment = 1.0,
                progressTowardTargetKm = pickupToHomeKm - destToHomeKm,
                pickupToCorridorKm = originToPickupKm,
                endsNearTargetKm = destToHomeKm,
                matchScore = (100.0 - addedDetourKm * 4.0).coerceAtLeast(1.0)
            )
        }

        val pickupLatDistKm = pointToSegmentDistanceKm(pickup, origin, corridor.destination)
        if (pickupLatDistKm > corridor.maxDetourKm && originToPickupKm > corridor.maxDetourKm) {
            return null
        }

        val destLatDistKm = pointToSegmentDistanceKm(dest, origin, corridor.destination)
        val progressKm = pickupToHomeKm - destToHomeKm
        val alignment = directionalAlignmentCosine(origin, corridor.destination, pickup, dest)

        val progressesTowardTarget = destToHomeKm <= pickupToHomeKm + 1.0
        val endsAlongCorridorOrNearHome = destLatDistKm <= corridor.maxDetourKm * 1.25 ||
            destToHomeKm <= corridor.maxDetourKm
        val alignedForward = alignment >= 0.10 || destToHomeKm <= corridor.maxDetourKm
        val detourReasonable = addedDetourKm <= corridor.maxDetourKm * 2.2 || destToHomeKm <= corridor.maxDetourKm

        if (!progressesTowardTarget || !endsAlongCorridorOrNearHome || !alignedForward || !detourReasonable) {
            return null
        }

        val corridorFitBonus = (corridor.maxDetourKm - pickupLatDistKm).coerceAtLeast(0.0) * 3.0
        val matchScore = 50.0 +
            alignment * 25.0 +
            corridorFitBonus +
            progressKm.coerceAtLeast(0.0) * 1.2 -
            addedDetourKm * 2.5

        return GtwTripMatch(
            tripId = trip.id,
            addedDetourKm = addedDetourKm,
            directionalAlignment = alignment,
            progressTowardTargetKm = progressKm,
            pickupToCorridorKm = min(pickupLatDistKm, originToPickupKm),
            endsNearTargetKm = destToHomeKm,
            matchScore = matchScore
        )
    }

    fun matchesCorridorFilter(
        trip: RideTrip,
        driverLocation: RideCoordinates?,
        corridor: DriverCorridorTarget?
    ): Boolean {
        if (corridor == null) return true
        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return true
        val dest = trip.destinationCoordinates ?: trip.route?.destinationCoordinates ?: return true
        if (pickup == dest && (LaunchPolicy.haversineKm(driverLocation ?: pickup, corridor.destination) ?: 0.0) < 0.8) {
            return (LaunchPolicy.haversineKm(dest, corridor.destination) ?: Double.MAX_VALUE) <= corridor.maxDetourKm
        }
        return evaluateGtwMatch(trip, driverLocation, corridor) != null
    }

    fun rankTripsForGtw(
        trips: List<RideTrip>,
        driverLocation: RideCoordinates?,
        corridor: DriverCorridorTarget?
    ): List<RideTrip> {
        if (corridor == null) return trips
        val scored = trips.map { trip ->
            trip to evaluateGtwMatch(trip, driverLocation, corridor)
        }
        return if (corridor.strictFilter) {
            scored
                .filter { (_, match) -> match != null }
                .sortedByDescending { (_, match) -> match!!.matchScore }
                .map { (trip, _) -> trip }
        } else {
            scored
                .sortedWith(
                    compareByDescending<Pair<RideTrip, GtwTripMatch?>> { it.second != null }
                        .thenByDescending { it.second?.matchScore ?: Double.NEGATIVE_INFINITY }
                )
                .map { it.first }
        }
    }

    fun directionalAlignmentCosine(
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

    fun pointToSegmentDistanceKm(
        point: RideCoordinates,
        segStart: RideCoordinates,
        segEnd: RideCoordinates
    ): Double {
        val avgLatRad = Math.toRadians((segStart.latitude + segEnd.latitude) * 0.5)
        val cosLat = cos(avgLatRad).coerceAtLeast(0.2)
        val ax = segStart.longitude * cosLat
        val ay = segStart.latitude
        val bx = segEnd.longitude * cosLat
        val by = segEnd.latitude
        val px = point.longitude * cosLat
        val py = point.latitude
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-12) {
            return LaunchPolicy.haversineKm(point, segStart) ?: 0.0
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val proj = RideCoordinates(
            latitude = segStart.latitude + (segEnd.latitude - segStart.latitude) * t,
            longitude = segStart.longitude + (segEnd.longitude - segStart.longitude) * t
        )
        return LaunchPolicy.haversineKm(point, proj) ?: 0.0
    }

    fun buildRequestMarkers(
        trips: List<RideTrip>,
        driverLocation: RideCoordinates?,
        minRatePerKmZar: Double,
        corridorTarget: DriverCorridorTarget?,
        returnMatchTripIds: Set<String> = emptySet()
    ): List<MapRequestMarker> {
        val yieldThreshold = max(18.0, minRatePerKmZar)
        return trips.mapNotNull { trip ->
            val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return@mapNotNull null
            val dest = trip.destinationCoordinates ?: trip.route?.destinationCoordinates
            val metrics = DriverDispatchMetrics.compute(trip, driverLocation, "")
            val gtwMatch = if (corridorTarget != null) evaluateGtwMatch(trip, driverLocation, corridorTarget) else null
            val isReturnMatch = trip.isReturnRequest || trip.id in returnMatchTripIds
            val prefix = when {
                isReturnMatch -> "Return "
                trip.category == RideCategory.BAKKIE -> "Bakkie "
                trip.category == RideCategory.PARCEL -> "Parcel "
                trip.category == RideCategory.XL -> "XL "
                trip.isSharedRide -> "Shared "
                else -> ""
            }
            val fareShort = "R${trip.fare.roundToInt()}"
            val rate = metrics.ratePerKmZar
            MapRequestMarker(
                tripId = trip.id,
                pickup = pickup,
                destination = dest,
                fareZar = trip.fare,
                fareLabel = "$prefix$fareShort",
                ratePerKmZar = rate,
                isHighYield = rate != null && rate >= yieldThreshold,
                category = trip.category,
                corridorMatched = gtwMatch != null,
                isReturnRideMatch = isReturnMatch,
                gtwDetourKm = gtwMatch?.addedDetourKm
            )
        }
    }

    fun computeDeadheadVsPaid(
        trip: RideTrip,
        driverCoords: RideCoordinates?
    ): DeadheadBreakdown? {
        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return null
        val dest = trip.destinationCoordinates ?: trip.route?.destinationCoordinates
        val deadheadKm = LaunchPolicy.haversineKm(driverCoords, pickup)?.coerceAtLeast(0.0) ?: return null
        val paidKm = (trip.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
            ?: LaunchPolicy.haversineKm(pickup, dest)
            ?: return null).coerceAtLeast(0.1)
        val total = (deadheadKm + paidKm).coerceAtLeast(0.1)
        return DeadheadBreakdown(
            deadheadKm = deadheadKm,
            paidTripKm = paidKm,
            totalKm = total,
            paidShareFraction = (paidKm / total).toFloat().coerceIn(0.05f, 1f)
        )
    }

    fun resolveSpokenTurnPrompt(
        legKey: String,
        progress: LaunchPolicy.ActiveNavigationProgress?,
        lastSpokenKey: String?
    ): Pair<String, String>? {
        if (progress == null) return null
        val step = progress.upcomingStep
        val dist = progress.distanceToManeuverMeters
        val isArrive = step.maneuverType.equals("arrive", ignoreCase = true)

        val tier = when {
            isArrive && dist <= 55.0 -> "arrive_now"
            dist <= 75.0 -> "now"
            dist in 180.0..380.0 -> "approach"
            else -> return null
        }

        val stepKey = "$legKey:${progress.upcomingStepIndex}:$tier"
        if (stepKey == lastSpokenKey) return null

        val cleanInstruction = step.instruction.trim().ifBlank { "Continue ahead" }
        val text = when (tier) {
            "arrive_now" -> "You have arrived at your destination."
            "now" -> cleanInstruction
            else -> {
                val roundedMeters = ((dist / 50.0).roundToInt() * 50).coerceIn(100, 400)
                val lowered = cleanInstruction.replaceFirstChar { it.lowercase(Locale.ROOT) }
                "In $roundedMeters meters, $lowered"
            }
        }
        return text to stepKey
    }

    fun latLonToTile(lat: Double, lon: Double, zoom: Int): TileCoordinate {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val n = 1 shl zoom
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(clampedLat)
        val yDouble = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val y = floor(yDouble).toInt().coerceIn(0, n - 1)
        return TileCoordinate(zoom, x, y)
    }

    fun computeTilesForCorridor(
        origin: RideCoordinates,
        destination: RideCoordinates,
        zooms: IntRange = 12..14,
        maxTiles: Int = 96
    ): List<TileCoordinate> {
        val tiles = LinkedHashSet<TileCoordinate>()
        val distKm = LaunchPolicy.haversineKm(origin, destination) ?: 5.0
        val sampleSteps = max(8, (distKm * 2.0).roundToInt()).coerceAtMost(48)

        for (zoom in zooms) {
            val n = 1 shl zoom
            for (i in 0..sampleSteps) {
                val t = i.toDouble() / sampleSteps.toDouble()
                val lat = origin.latitude + (destination.latitude - origin.latitude) * t
                val lon = origin.longitude + (destination.longitude - origin.longitude) * t
                val centerTile = latLonToTile(lat, lon, zoom)
                val pad = if (zoom <= 13) 1 else 0
                for (dx in -pad..pad) {
                    for (dy in -pad..pad) {
                        val tx = (centerTile.x + dx).coerceIn(0, n - 1)
                        val ty = (centerTile.y + dy).coerceIn(0, n - 1)
                        tiles += TileCoordinate(zoom, tx, ty)
                        if (tiles.size >= maxTiles) return tiles.toList()
                    }
                }
            }
        }
        return tiles.toList()
    }

    fun formatOfflineHubShortLabel(hub: LaunchPolicy.PopularHub): String {
        return hub.name
            .replace("Shopping Centre", "Centre")
            .replace("Circle Centre", "Circle")
            .replace("University of Limpopo", "UL Turfloop")
            .replace("Tribal Office", "Tribal")
            .replace("Main Junction", "Jn")
            .trim()
    }
}
