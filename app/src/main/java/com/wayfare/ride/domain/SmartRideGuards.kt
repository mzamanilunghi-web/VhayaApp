package com.wayfare.ride.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object SmartRideGuards {
    const val FREE_PICKUP_WAIT_WINDOW_MS = 3 * 60_000L
    const val STALL_IDLE_THRESHOLD_MS = 90_000L
    const val STALL_MIN_PROGRESS_METERS = 60.0
    const val WRONG_WAY_RETREAT_METERS = 400.0
    const val OFF_ROUTE_THRESHOLD_METERS = 500.0
    const val DROPOFF_PROXIMITY_THRESHOLD_METERS = 300.0

    val PICKUP_MICRO_TAGS: List<String> = listOf(
        "Main gate",
        "Food court",
        "Security boom",
        "Call on arrival"
    )

    val RURAL_PICKUP_MICRO_TAGS: List<String> = listOf(
        "At main road",
        "By spaza shop",
        "Near clinic",
        "Stand / gate #"
    )

    val CASH_CHANGE_OPTIONS_ZAR: List<Int?> = listOf(null, 100, 200)

    val DRIVER_MIN_RATE_FILTER_OPTIONS_ZAR: List<Double> = listOf(0.0, 15.0, 20.0, 25.0)

    data class DriverStallTracker(
        val tripId: String,
        val firstObservedAtMs: Long,
        val closestDistanceMeters: Double,
        val closestObservedAtMs: Long,
        val lastDistanceMeters: Double,
        val lastObservedAtMs: Long
    )

    data class DriverStallAssessment(
        val isStalled: Boolean,
        val isDrivingAway: Boolean,
        val idleSeconds: Long,
        val movedAwayMeters: Double,
        val currentDistanceMeters: Double,
        val headline: String,
        val detail: String
    ) {
        val shouldOfferSwitch: Boolean
            get() = isStalled || isDrivingAway
    }

    fun advanceStallTracker(
        existing: DriverStallTracker?,
        tripId: String,
        distanceToPickupMeters: Double,
        nowMs: Long
    ): DriverStallTracker {
        val cleanDistance = distanceToPickupMeters.coerceAtLeast(0.0)
        if (existing == null || existing.tripId != tripId) {
            return DriverStallTracker(
                tripId = tripId,
                firstObservedAtMs = nowMs,
                closestDistanceMeters = cleanDistance,
                closestObservedAtMs = nowMs,
                lastDistanceMeters = cleanDistance,
                lastObservedAtMs = nowMs
            )
        }
        val improvedSignificantly = cleanDistance <= existing.closestDistanceMeters - STALL_MIN_PROGRESS_METERS
        val nextClosestDistance = if (cleanDistance < existing.closestDistanceMeters) {
            cleanDistance
        } else {
            existing.closestDistanceMeters
        }
        val nextClosestAt = if (improvedSignificantly) nowMs else existing.closestObservedAtMs
        return existing.copy(
            closestDistanceMeters = nextClosestDistance,
            closestObservedAtMs = nextClosestAt,
            lastDistanceMeters = cleanDistance,
            lastObservedAtMs = nowMs
        )
    }

    fun evaluateDriverApproachHealth(
        trip: RideTrip,
        driverCoords: RideCoordinates?,
        tracker: DriverStallTracker?,
        nowMs: Long = System.currentTimeMillis()
    ): DriverStallAssessment? {
        if (trip.status != RideStatus.ACCEPTED || driverCoords == null || tracker == null) return null
        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return null
        val currentDist = LaunchPolicy.distanceMeters(driverCoords, pickup)
        if (currentDist <= LaunchPolicy.ARRIVAL_RADIUS_METERS) {
            return DriverStallAssessment(
                isStalled = false,
                isDrivingAway = false,
                idleSeconds = 0L,
                movedAwayMeters = 0.0,
                currentDistanceMeters = currentDist,
                headline = "",
                detail = ""
            )
        }
        val movedAway = (currentDist - tracker.closestDistanceMeters).coerceAtLeast(0.0)
        val idleMs = (nowMs - tracker.closestObservedAtMs).coerceAtLeast(0L)
        val isDrivingAway = movedAway >= WRONG_WAY_RETREAT_METERS
        val isStalled = idleMs >= STALL_IDLE_THRESHOLD_MS

        val headline = when {
            isDrivingAway -> "Driver appears to be moving away"
            isStalled -> "Driver hasn't moved toward pickup"
            else -> ""
        }
        val detail = when {
            isDrivingAway ->
                "Moved ${LaunchPolicy.formatApproachDistance(movedAway)} further from your pickup. You can switch drivers for R 0.00 fee."
            isStalled ->
                "No progress for ${idleMs / 1000L}s (${LaunchPolicy.formatApproachDistance(currentDist)} away). Switch to another driver for free."
            else -> ""
        }
        return DriverStallAssessment(
            isStalled = isStalled,
            isDrivingAway = isDrivingAway,
            idleSeconds = idleMs / 1000L,
            movedAwayMeters = movedAway,
            currentDistanceMeters = currentDist,
            headline = headline,
            detail = detail
        )
    }

    data class OffRouteAssessment(
        val isOffRoute: Boolean,
        val distanceFromRouteMeters: Double,
        val summary: String
    )

    fun distanceToRouteMeters(
        point: RideCoordinates,
        route: RideRoute?,
        pickupFallback: RideCoordinates? = null,
        destFallback: RideCoordinates? = null
    ): Double? {
        val decoded = route?.encodedPolyline
            ?.takeIf { it.isNotBlank() }
            ?.let { PolylineCodec.decode(it) }
            .orEmpty()
        val points = when {
            decoded.size >= 2 -> decoded
            route?.originCoordinates != null && route.destinationCoordinates != null ->
                listOf(route.originCoordinates, route.destinationCoordinates)
            pickupFallback != null && destFallback != null ->
                listOf(pickupFallback, destFallback)
            else -> return null
        }

        var bestMeters = Double.POSITIVE_INFINITY
        for (i in 0 until points.lastIndex) {
            val d = pointToSegmentMeters(point, points[i], points[i + 1])
            if (d < bestMeters) bestMeters = d
        }
        return if (bestMeters.isFinite()) bestMeters else null
    }

    fun evaluateOffRoute(
        driverCoords: RideCoordinates?,
        trip: RideTrip,
        thresholdMeters: Double = OFF_ROUTE_THRESHOLD_METERS
    ): OffRouteAssessment {
        if (trip.status != RideStatus.IN_PROGRESS || driverCoords == null) {
            return OffRouteAssessment(isOffRoute = false, distanceFromRouteMeters = 0.0, summary = "")
        }
        val deviation = distanceToRouteMeters(
            point = driverCoords,
            route = trip.route,
            pickupFallback = trip.pickupCoordinates,
            destFallback = trip.destinationCoordinates
        ) ?: return OffRouteAssessment(isOffRoute = false, distanceFromRouteMeters = 0.0, summary = "")
        val isOff = deviation > thresholdMeters
        val summary = if (isOff) {
            "Vehicle is ${LaunchPolicy.formatApproachDistance(deviation)} off the expected route."
        } else {
            ""
        }
        return OffRouteAssessment(
            isOffRoute = isOff,
            distanceFromRouteMeters = deviation,
            summary = summary
        )
    }

    data class DropOffProximityAssessment(
        val isFarFromDestination: Boolean,
        val distanceMeters: Double
    )

    fun evaluateDropOffProximity(
        driverCoords: RideCoordinates?,
        destinationCoords: RideCoordinates?,
        thresholdMeters: Double = DROPOFF_PROXIMITY_THRESHOLD_METERS
    ): DropOffProximityAssessment {
        if (driverCoords == null || destinationCoords == null) {
            return DropOffProximityAssessment(isFarFromDestination = false, distanceMeters = 0.0)
        }
        val dist = LaunchPolicy.distanceMeters(driverCoords, destinationCoords)
        return DropOffProximityAssessment(
            isFarFromDestination = dist > thresholdMeters,
            distanceMeters = dist
        )
    }

    private fun pointToSegmentMeters(
        point: RideCoordinates,
        a: RideCoordinates,
        b: RideCoordinates
    ): Double {
        val avgLatRad = Math.toRadians((a.latitude + b.latitude) * 0.5)
        val cosLat = cos(avgLatRad).coerceAtLeast(0.2)
        val ax = a.longitude * cosLat
        val ay = a.latitude
        val bx = b.longitude * cosLat
        val by = b.latitude
        val px = point.longitude * cosLat
        val py = point.latitude
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-12) {
            return LaunchPolicy.distanceMeters(point, a)
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val proj = RideCoordinates(
            latitude = a.latitude + (b.latitude - a.latitude) * t,
            longitude = a.longitude + (b.longitude - a.longitude) * t
        )
        return LaunchPolicy.distanceMeters(point, proj)
    }

    data class ChatExtortionAlert(
        val isFlagged: Boolean,
        val flaggedMessage: RideMessage? = null,
        val matchedCue: String? = null,
        val summary: String = ""
    )

    private val EXTORTION_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("\\bextra\\s+(money|cash|fare|fee|r\\d*)\\b", RegexOption.IGNORE_CASE) to "extra cash request",
        Regex("\\b(more|double)\\s+(money|cash|fare)\\b", RegexOption.IGNORE_CASE) to "fare increase demand",
        Regex("\\badd\\s*(r\\s*\\d+|\\d{2,}|more)\\b", RegexOption.IGNORE_CASE) to "top-up fee request",
        Regex("\\bcancel\\s+(and|the\\s+trip|ride|app)\\b", RegexOption.IGNORE_CASE) to "off-app cancellation request",
        Regex("\\b(outside|off)\\s+(the\\s+)?app\\b", RegexOption.IGNORE_CASE) to "off-app payment demand",
        Regex("\\bcash\\s+only\\b", RegexOption.IGNORE_CASE) to "cash-only demand",
        Regex("\\b(petrol|fuel|toll)\\s+(money|extra|fee)\\b", RegexOption.IGNORE_CASE) to "unapproved surcharge",
        Regex("\\bpay\\s+(me\\s+)?(more|extra|cash)\\b", RegexOption.IGNORE_CASE) to "extra payment demand"
    )

    fun scanChatForFareExtortion(
        messages: List<RideMessage>,
        viewerRole: String = "rider"
    ): ChatExtortionAlert {
        val candidates = messages
            .filter { it.channel == "driver" && !it.sender.equals(viewerRole, ignoreCase = true) }
            .asReversed()
        for (msg in candidates) {
            val text = msg.message.trim()
            for ((regex, cue) in EXTORTION_PATTERNS) {
                if (regex.containsMatchIn(text)) {
                    return ChatExtortionAlert(
                        isFlagged = true,
                        flaggedMessage = msg,
                        matchedCue = cue,
                        summary = "Detected $cue in chat. Your agreed fare is locked in Vhaya — never pay extra outside the app."
                    )
                }
            }
        }
        return ChatExtortionAlert(isFlagged = false)
    }

    enum class AcceptanceTier(val label: String) {
        HIGH("High chance"),
        MODERATE("Fair chance"),
        LOW("Low chance")
    }

    data class OfferAcceptanceForecast(
        val tier: AcceptanceTier,
        val ratePerKmZar: Double,
        val recommendedFairZar: Double,
        val isPeakHour: Boolean,
        val headline: String,
        val detail: String
    )

    fun calculateBackhaulReturnFare(
        standardFareZar: Double,
        distanceMeters: Int? = null,
        category: RideCategory = RideCategory.STANDARD
    ): Double {
        val minFare = if (category == RideCategory.BAKKIE) 30.0 else 20.0
        val safeStandard = max(minFare, standardFareZar)
        val discounted = round(safeStandard * ReturnRideEngine.RETURN_DISCOUNT_MULTIPLIER)
        val kmFloor = distanceMeters?.takeIf { it > 0 }?.let {
            round((it / 1000.0) * ReturnRideEngine.MIN_VIABLE_RETURN_RATE_PER_KM_ZAR)
        } ?: 0.0
        return max(minFare, max(discounted, kmFloor)).coerceAtMost(safeStandard)
    }

    fun calculateRuralAdjustedFare(
        baseEstimateZar: Double,
        category: RideCategory = RideCategory.STANDARD,
        isSharedRide: Boolean = false,
        seatCount: Int = 1,
        isReturnTrip: Boolean = false,
        includesGravelRoad: Boolean = false,
        isRuralArea: Boolean = false,
        isReturnRequest: Boolean = false,
        distanceMeters: Int? = null
    ): Double {
        var factor = when (category) {
            RideCategory.BAKKIE -> 1.25
            RideCategory.PARCEL -> 0.85
            else -> 1.0
        }
        if (includesGravelRoad) factor *= 1.12
        else if (isRuralArea) factor *= 1.08
        if (isReturnTrip) factor *= 1.85
        if (isSharedRide) {
            val clampedSeats = seatCount.coerceIn(1, 4)
            // Single shared seat reduces rider cost by ~30%; extra seats scale proportionally
            factor *= (0.45 + 0.25 * clampedSeats).coerceAtMost(1.20)
        }
        val minFare = if (category == RideCategory.BAKKIE) 30.0 else 20.0
        val standardResult = max(minFare, round(baseEstimateZar * factor))
        return if (isReturnRequest && !isReturnTrip) {
            calculateBackhaulReturnFare(
                standardFareZar = standardResult,
                distanceMeters = distanceMeters,
                category = category
            )
        } else {
            standardResult
        }
    }

    fun calculateReturnFareComparison(
        baseEstimateZar: Double,
        distanceMeters: Int? = null,
        category: RideCategory = RideCategory.STANDARD,
        isSharedRide: Boolean = false,
        seatCount: Int = 1,
        includesGravelRoad: Boolean = false,
        isRuralArea: Boolean = false
    ): ReturnFareComparison {
        val standardFare = calculateRuralAdjustedFare(
            baseEstimateZar = baseEstimateZar,
            category = category,
            isSharedRide = isSharedRide,
            seatCount = seatCount,
            isReturnTrip = false,
            includesGravelRoad = includesGravelRoad,
            isRuralArea = isRuralArea,
            isReturnRequest = false,
            distanceMeters = distanceMeters
        )
        val discountedFare = calculateRuralAdjustedFare(
            baseEstimateZar = baseEstimateZar,
            category = category,
            isSharedRide = isSharedRide,
            seatCount = seatCount,
            isReturnTrip = false,
            includesGravelRoad = includesGravelRoad,
            isRuralArea = isRuralArea,
            isReturnRequest = true,
            distanceMeters = distanceMeters
        )
        val savings = (standardFare - discountedFare).coerceAtLeast(0.0)
        val pct = if (standardFare > 0.0) round((savings / standardFare) * 100.0).toInt() else 0
        return ReturnFareComparison(
            standardFareZar = standardFare,
            discountedFareZar = discountedFare,
            savingsZar = savings,
            savingsPercent = pct
        )
    }

    data class FareSplitSummary(
        val totalFareZar: Double,
        val riderCount: Int,
        val exactPerPersonZar: Double,
        val roundedCashPerPersonZar: Int
    )

    fun calculateFareSplit(
        totalFareZar: Double,
        riderCount: Int
    ): FareSplitSummary {
        val safeTotal = totalFareZar.coerceAtLeast(20.0)
        val safeCount = riderCount.coerceIn(2, 4)
        val exact = safeTotal / safeCount
        val roundedCash = kotlin.math.ceil(exact).toInt()
        return FareSplitSummary(
            totalFareZar = safeTotal,
            riderCount = safeCount,
            exactPerPersonZar = exact,
            roundedCashPerPersonZar = roundedCash
        )
    }

    fun buildFareSplitShareText(
        pickup: String,
        destination: String,
        totalFareZar: Double,
        riderCount: Int,
        driverLabel: String? = null
    ): String {
        val split = calculateFareSplit(totalFareZar, riderCount)
        return buildString {
            appendLine("VHAYA LIFT SHARE SPLIT")
            appendLine("${pickup.substringBefore(" ·")} → ${destination.substringBefore(" ·")}")
            appendLine("Total fare: ${LaunchPolicy.formatZar(split.totalFareZar)} ÷ ${split.riderCount} riders")
            appendLine("Each rider pays: R ${split.roundedCashPerPersonZar} (${LaunchPolicy.formatZar(split.exactPerPersonZar)} exact)")
            driverLabel?.takeIf { it.isNotBlank() }?.let { appendLine("Driver: $it") }
            append("Pay directly in Vhaya Cash / Wallet.")
        }.trim()
    }

    fun evaluateOfferAcceptance(
        fareZar: Double,
        distanceMeters: Int,
        hourOfDay: Int? = null,
        minuteOfHour: Int? = null,
        category: RideCategory = RideCategory.STANDARD,
        isSharedRide: Boolean = false,
        seatCount: Int = 1,
        isReturnTrip: Boolean = false,
        includesGravelRoad: Boolean = false,
        isRuralArea: Boolean = false,
        isReturnRequest: Boolean = false
    ): OfferAcceptanceForecast {
        val resolvedHour: Int
        val resolvedMinute: Int
        if (hourOfDay != null) {
            resolvedHour = hourOfDay
            resolvedMinute = minuteOfHour ?: 0
        } else {
            val cal = Calendar.getInstance()
            resolvedHour = cal.get(Calendar.HOUR_OF_DAY)
            resolvedMinute = cal.get(Calendar.MINUTE)
        }
        val minutesOfDay = resolvedHour * 60 + resolvedMinute
        val isPeak = (minutesOfDay in (6 * 60 + 30)..(9 * 60)) ||
            (minutesOfDay in (16 * 60)..(18 * 60 + 30))

        val km = max(1.0, distanceMeters / 1000.0)
        val ratePerKm = fareZar / km
        val benchmarkRate = LaunchPolicy.RATE_PER_KM * (if (isPeak) 1.10 else 1.0)
        val baseBenchmark = max(20.0, round(km * benchmarkRate))
        val recommended = calculateRuralAdjustedFare(
            baseEstimateZar = baseBenchmark,
            category = category,
            isSharedRide = isSharedRide,
            seatCount = seatCount,
            isReturnTrip = isReturnTrip,
            includesGravelRoad = includesGravelRoad,
            isRuralArea = isRuralArea,
            isReturnRequest = isReturnRequest,
            distanceMeters = distanceMeters
        )
        val ratio = fareZar / recommended

        val tier = when {
            ratio >= 0.92 -> AcceptanceTier.HIGH
            ratio >= 0.72 -> AcceptanceTier.MODERATE
            else -> AcceptanceTier.LOW
        }
        val contextCues = buildList {
            if (isPeak) add("Peak hour")
            if (isReturnTrip) add("Wait & return")
            if (isReturnRequest && !isReturnTrip) add("Return Request (-18%)")
            if (includesGravelRoad) add("Gravel allowance")
            else if (isRuralArea && !isReturnRequest) add("Rural return leg")
            if (isSharedRide) add("Shared ($seatCount seat${if (seatCount == 1) "" else "s"})")
        }.joinToString(" · ")
        val cuePrefix = if (contextCues.isNotEmpty()) "$contextCues · " else ""
        val headline = "${tier.label} · R ${"%.1f".format(Locale.US, ratePerKm)}/km"
        val detail = when (tier) {
            AcceptanceTier.HIGH -> "${cuePrefix}Drivers typically accept quickly at this rate."
            AcceptanceTier.MODERATE -> "${cuePrefix}May receive counter-offers around ${LaunchPolicy.formatZar(recommended)}."
            AcceptanceTier.LOW -> "${cuePrefix}Below rural fuel & time benchmark (${LaunchPolicy.formatZar(recommended)} suggested)."
        }
        return OfferAcceptanceForecast(
            tier = tier,
            ratePerKmZar = ratePerKm,
            recommendedFairZar = recommended,
            isPeakHour = isPeak,
            headline = headline,
            detail = detail
        )
    }

    fun buildSmsTripSummary(
        trip: RideTrip,
        driverCoords: RideCoordinates? = trip.liveDriverLocation
    ): String = buildString {
        appendLine("VHAYA RURAL TRIP UPDATE")
        appendLine("From: ${trip.pickup.substringBefore(" ·")}")
        appendLine("To: ${trip.destination.substringBefore(" ·")}")
        appendLine("Status: ${trip.status.label} | Fare: ${LaunchPolicy.formatZar(trip.fare)} (${trip.paymentMethod.label})")
        trip.driverProfile?.let { d ->
            appendLine("Driver: ${d.name} · ${d.vehicleColor} ${d.vehicle} (${d.registration})")
        }
        trip.tripPin?.takeIf { it.isNotBlank() }?.let { appendLine("Pickup PIN: $it") }
        trip.beneficiaryName?.takeIf { it.isNotBlank() }?.let { name ->
            appendLine("Passenger: $name ${trip.beneficiaryPhone.orEmpty()}".trim())
        }
        (driverCoords ?: trip.pickupCoordinates)?.let { c ->
            append("GPS: ${"%.5f".format(Locale.US, c.latitude)},${"%.5f".format(Locale.US, c.longitude)}")
        }
    }.trim()

    data class WaitTimerStatus(
        val elapsedMs: Long,
        val remainingFreeMs: Long,
        val overtimeMs: Long,
        val isFreeWaitExpired: Boolean,
        val badgeLabel: String
    )

    fun evaluateWaitTimer(
        arrivedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        freeWindowMs: Long = FREE_PICKUP_WAIT_WINDOW_MS
    ): WaitTimerStatus? {
        val anchor = arrivedAtMs?.takeIf { it > 0L } ?: return null
        val elapsed = (nowMs - anchor).coerceAtLeast(0L)
        val remaining = (freeWindowMs - elapsed).coerceAtLeast(0L)
        val overtime = (elapsed - freeWindowMs).coerceAtLeast(0L)
        val expired = elapsed >= freeWindowMs
        val badgeLabel = if (!expired) {
            "Free wait ${LaunchPolicy.formatCountdownMs(remaining)}"
        } else {
            "Wait exceeded +${LaunchPolicy.formatCountdownMs(overtime)}"
        }
        return WaitTimerStatus(
            elapsedMs = elapsed,
            remainingFreeMs = remaining,
            overtimeMs = overtime,
            isFreeWaitExpired = expired,
            badgeLabel = badgeLabel
        )
    }

    fun matchesDriverMinRateFilter(trip: RideTrip, minRatePerKmZar: Double): Boolean {
        if (minRatePerKmZar <= 0.0) return true
        val km = trip.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
            ?: LaunchPolicy.haversineKm(trip.pickupCoordinates, trip.destinationCoordinates)
            ?: return true
        if (km <= 0.0) return true
        return (trip.fare / km) >= minRatePerKmZar
    }

    fun buildSupportTelemetrySummary(
        trip: RideTrip,
        currentLocation: RideCoordinates? = null
    ): String {
        val routeKm = trip.route?.distanceMeters?.takeIf { it > 0 }?.let { "%.1f km".format(Locale.US, it / 1000.0) }
            ?: LaunchPolicy.haversineKm(trip.pickupCoordinates, trip.destinationCoordinates)?.let { "%.1f km est.".format(Locale.US, it) }
            ?: "unknown"
        val arrivalMethod = trip.arrivalConfirmationMethod ?: "pending"
        val pinStatus = if (!trip.tripPin.isNullOrBlank()) "generated" else "none"
        val extortion = scanChatForFareExtortion(trip.messages)
        val driverDistMeters = currentLocation?.let { loc ->
            val target = if (trip.status == RideStatus.IN_PROGRESS) trip.destinationCoordinates else trip.pickupCoordinates
            target?.let { LaunchPolicy.distanceMeters(loc, it).toInt() }
        }
        return buildString {
            append("[Trip Telemetry] ")
            append("id=").append(trip.id)
            append(" | status=").append(trip.status.name.lowercase(Locale.ROOT))
            append(" | fare=").append(LaunchPolicy.formatZar(trip.fare))
            append(" | method=").append(trip.paymentMethod.name.lowercase(Locale.ROOT))
            append(" | payStatus=").append(trip.paymentStatus.name.lowercase(Locale.ROOT))
            append(" | route=").append(routeKm)
            append(" | arrival=").append(arrivalMethod)
            append(" | pin=").append(pinStatus)
            if (trip.cashChangeForZar != null) {
                append(" | changeFor=R").append(trip.cashChangeForZar)
            }
            if (driverDistMeters != null) {
                append(" | gpsOffsetMeters=").append(driverDistMeters)
            }
            if (extortion.isFlagged) {
                append(" | chatAlert=").append(extortion.matchedCue ?: "extortion")
            }
            if (trip.includesGravelRoad) append(" | gravelRoad=true")
            if (trip.isReturnTrip) append(" | returnTrip=true")
            if (trip.isReturnRequest) append(" | returnRequest=true")
            if (trip.isSharedRide) append(" | sharedSeats=").append(trip.seatCount)
            if (!trip.beneficiaryName.isNullOrBlank()) append(" | forOther=").append(trip.beneficiaryName)
        }
    }

    fun buildSupportEvidenceJson(
        trip: RideTrip,
        currentLocation: RideCoordinates? = null
    ): JsonObject {
        val routeKm = trip.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
        val extortion = scanChatForFareExtortion(trip.messages)
        val offRoute = evaluateOffRoute(trip.liveDriverLocation ?: currentLocation, trip)
        return buildJsonObject {
            put("tripId", trip.id)
            put("rideStatus", trip.status.name.lowercase(Locale.ROOT))
            put("category", trip.category.name.lowercase(Locale.ROOT))
            put("paymentMethod", trip.paymentMethod.name.lowercase(Locale.ROOT))
            put("paymentStatus", trip.paymentStatus.name.lowercase(Locale.ROOT))
            put("lockedFareZar", trip.fare)
            if (routeKm != null) put("routeKm", routeKm)
            trip.arrivalConfirmationMethod?.let { put("arrivalMethod", it) }
            put("pinIssued", !trip.tripPin.isNullOrBlank())
            trip.pickupNote?.takeIf { it.isNotBlank() }?.let { put("pickupNote", it) }
            trip.cashChangeForZar?.let { put("cashChangeForZar", it) }
            put("includesGravelRoad", trip.includesGravelRoad)
            put("isReturnTrip", trip.isReturnTrip)
            put("isReturnRequest", trip.isReturnRequest)
            put("isSharedRide", trip.isSharedRide)
            if (trip.isSharedRide) put("seatCount", trip.seatCount)
            trip.beneficiaryName?.takeIf { it.isNotBlank() }?.let { put("beneficiaryName", it) }
            put("offRouteFlagged", offRoute.isOffRoute)
            if (offRoute.isOffRoute) put("offRouteMeters", offRoute.distanceFromRouteMeters)
            put("chatExtortionFlagged", extortion.isFlagged)
            extortion.matchedCue?.let { put("chatExtortionCue", it) }
        }
    }
}
