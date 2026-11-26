package com.wayfare.ride.domain

import com.wayfare.ride.service.FareEstimationService
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object LaunchPolicy {
    const val CURRENCY = "ZAR"
    const val COUNTRY = "South Africa"
    const val RATE_PER_KM = 20.0
    const val ARRIVAL_RADIUS_METERS = 150.0
    const val MIN_RIDE_DURATION_SECONDS = 5 * 60
    const val CANCELLATION_FEE_PERCENT = 0.0
    const val RIDER_EN_ROUTE_CANCELLATION_FEE_ZAR = 10.0
    const val DRIVER_DOCS_EMAIL = "hlulanigo@gmail.com"
    const val OPEN_REQUEST_TTL_MS = 5 * 60_000L

    val DEFAULT_MAP_CENTER = RideCoordinates(latitude = -23.9045, longitude = 29.4689)

    fun isWithinSouthAfrica(coords: RideCoordinates): Boolean {
        return coords.latitude in -35.5..-22.0 && coords.longitude in 16.0..33.5
    }

    fun isValidDeviceCoordinate(coords: RideCoordinates): Boolean {
        if (!coords.latitude.isFinite() || !coords.longitude.isFinite()) return false
        if (coords.latitude !in -90.0..90.0 || coords.longitude !in -180.0..180.0) return false
        // Reject uninitialized Null Island (0.0, 0.0) readings
        if (kotlin.math.abs(coords.latitude) < 0.005 && kotlin.math.abs(coords.longitude) < 0.005) return false
        return true
    }

    fun formatZar(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
            format.maximumFractionDigits = 2
            format.format(amount)
        } catch (_: Throwable) {
            "R %.2f".format(Locale.US, amount)
        }
    }

    fun formatZarMinor(amountMinor: Long): String = formatZar(amountMinor / 100.0)

    fun calculateEstimate(distanceMeters: Int, category: RideCategory): Double {
        val km = max(1.0, distanceMeters / 1000.0)
        val raw = km * RATE_PER_KM
        val multiplier = when (category) {
            RideCategory.STANDARD -> 1.0
            RideCategory.COMFORT -> 1.0 // Draft launch policy: R20/km across categories
            RideCategory.XL -> 1.0
            RideCategory.BAKKIE -> 1.25
            RideCategory.PARCEL -> 0.85
        }
        return kotlin.math.max(20.0, round(raw * multiplier))
    }

    fun canRiderCancel(status: RideStatus, scheduled: Boolean = false): Boolean {
        return status == RideStatus.REQUESTED ||
            status == RideStatus.COUNTERED ||
            status == RideStatus.ACCEPTED ||
            status == RideStatus.ARRIVED ||
            (scheduled && status == RideStatus.ACCEPTED)
    }

    fun riderCancellationFee(trip: RideTrip): Double {
        return when (trip.status) {
            RideStatus.ACCEPTED,
            RideStatus.ARRIVED -> RIDER_EN_ROUTE_CANCELLATION_FEE_ZAR
            else -> 0.0
        }
    }

    fun tripExpiresAtMs(trip: RideTrip): Long {
        trip.expiresAt?.takeIf { it > 0L }?.let { return it }
        val anchorMs = max(trip.createdAt, trip.pickupAt ?: trip.createdAt)
        return anchorMs + OPEN_REQUEST_TTL_MS
    }

    fun tripExpirationWindowMs(trip: RideTrip): Long {
        val anchorMs = max(trip.createdAt, trip.pickupAt ?: trip.createdAt)
        return (tripExpiresAtMs(trip) - anchorMs).coerceAtLeast(OPEN_REQUEST_TTL_MS)
    }

    fun remainingOpenMs(trip: RideTrip, nowMs: Long = System.currentTimeMillis()): Long {
        if (!trip.status.isOpenForDrivers || trip.driverId != null) return 0L
        return (tripExpiresAtMs(trip) - nowMs).coerceAtLeast(0L)
    }

    fun isTripExpired(trip: RideTrip, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (trip.syncStatus != "SYNCED") return false
        return trip.status.isOpenForDrivers && trip.driverId == null && remainingOpenMs(trip, nowMs) <= 0L
    }

    fun formatCountdownMs(ms: Long): String {
        val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return "%d:%02d".format(Locale.US, mins, secs)
    }

    fun haversineKm(first: RideCoordinates?, second: RideCoordinates?): Double? {
        if (first == null || second == null) return null
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371.0 * c
    }

    fun distanceMeters(first: RideCoordinates, second: RideCoordinates): Double {
        return (haversineKm(first, second) ?: 0.0) * 1000.0
    }

    data class DriverApproachSnapshot(
        val progress: Float,
        val remainingMeters: Double?,
        val etaMinutes: Int?,
        val isArrivedOrWithinRadius: Boolean
    )

    fun calculateDriverApproachProgress(
        status: RideStatus,
        currentDriverLocation: RideCoordinates?,
        pickupLocation: RideCoordinates?,
        baselineDistanceMeters: Double?
    ): DriverApproachSnapshot {
        if (status == RideStatus.ARRIVED) {
            return DriverApproachSnapshot(
                progress = 1f,
                remainingMeters = 0.0,
                etaMinutes = 0,
                isArrivedOrWithinRadius = true
            )
        }
        if (currentDriverLocation == null || pickupLocation == null) {
            return DriverApproachSnapshot(
                progress = 0.08f,
                remainingMeters = null,
                etaMinutes = null,
                isArrivedOrWithinRadius = false
            )
        }
        val remaining = distanceMeters(currentDriverLocation, pickupLocation).coerceAtLeast(0.0)
        val etaMinutes = kotlin.math.ceil(remaining / 500.0).toInt().coerceAtLeast(1)
        val withinRadius = remaining <= ARRIVAL_RADIUS_METERS
        if (withinRadius) {
            return DriverApproachSnapshot(
                progress = 0.96f,
                remainingMeters = remaining,
                etaMinutes = 1,
                isArrivedOrWithinRadius = true
            )
        }
        val defaultBaseline = max(remaining, 2_000.0)
        val effectiveBaseline = max(
            baselineDistanceMeters ?: defaultBaseline,
            max(remaining, ARRIVAL_RADIUS_METERS + 150.0)
        )
        val span = (effectiveBaseline - ARRIVAL_RADIUS_METERS).coerceAtLeast(150.0)
        val traveled = (effectiveBaseline - remaining).coerceAtLeast(0.0)
        val fraction = (traveled / span).toFloat().coerceIn(0f, 1f)
        val displayProgress = (0.08f + fraction * 0.88f).coerceIn(0.08f, 0.96f)
        return DriverApproachSnapshot(
            progress = displayProgress,
            remainingMeters = remaining,
            etaMinutes = etaMinutes,
            isArrivedOrWithinRadius = false
        )
    }

    fun formatApproachDistance(meters: Double): String {
        val safeMeters = meters.coerceAtLeast(0.0)
        return if (safeMeters < 1000.0) {
            "${safeMeters.toInt()} m"
        } else {
            "%.1f km".format(Locale.US, safeMeters / 1000.0)
        }
    }

    fun matchScoreForDriver(trip: RideTrip, driverLocation: RideCoordinates?): Double {
        val km = trip.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
            ?: haversineKm(trip.pickupCoordinates, trip.destinationCoordinates)
        val farePerKm = if (km != null && km > 0) trip.fare / km else trip.fare
        val ageMinutes = max(0.0, (System.currentTimeMillis() - trip.createdAt) / 60_000.0)
        val recencyBonus = max(0.0, 1.0 - ageMinutes / 30.0) * 5.0
        val offerBonus = if (trip.pricingMode == PricingMode.OFFER) 2.0 else 0.0
        val returnReqBonus = if (trip.isReturnRequest) 8.0 else 0.0
        var score = farePerKm + recencyBonus + offerBonus + returnReqBonus
        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates
        if (driverLocation != null && pickup != null) {
            val distance = haversineKm(driverLocation, pickup)
            if (distance != null) {
                score += max(0.0, 25.0 - distance) * 0.4
            }
        }
        return score
    }

    fun offerScore(offer: RideOffer, trustedDriverIds: Set<String> = emptySet()): Double {
        val rating = offer.driverProfile.rating
        val completed = offer.driverProfile.completedRides
        val acceptance = offer.driverProfile.acceptanceRate
        val isTrusted = offer.driverId in trustedDriverIds || offer.driverProfile.id in trustedDriverIds
        val trustBoost = if (isTrusted) 50.0 else 0.0
        return rating * 12.0 + completed * 0.5 + acceptance * 8.0 + trustBoost - offer.amount / 20.0
    }

    fun normalizeSouthAfricanRegistration(value: String): String {
        return value.uppercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(30)
    }

    fun formatSouthAfricanRegistration(value: String): String {
        val normalized = normalizeSouthAfricanRegistration(value).trim()
        val compact = normalized.replace(Regex("[\\s-]"), "")
        val gautengMatch = Regex("^([A-Z]{3})(\\d{3})([A-Z]{2})$").matchEntire(compact)
        if (gautengMatch != null) {
            return "${gautengMatch.groupValues[1]} ${gautengMatch.groupValues[2]} ${gautengMatch.groupValues[3]}"
        }
        return normalized
    }

    data class PopularHub(
        val id: String,
        val name: String,
        val area: String,
        val coordinates: RideCoordinates
    )

    val POLOKWANE_POPULAR_HUBS: List<PopularHub> = listOf(
        PopularHub(
            id = "hub_mall_of_the_north",
            name = "Mall of the North",
            area = "Bendor",
            coordinates = RideCoordinates(-23.8768, 29.4979)
        ),
        PopularHub(
            id = "hub_savannah_mall",
            name = "Savannah Mall",
            area = "Fauna Park",
            coordinates = RideCoordinates(-23.9038, 29.4857)
        ),
        PopularHub(
            id = "hub_peter_mokaba",
            name = "Peter Mokaba Stadium",
            area = "Polokwane Central",
            coordinates = RideCoordinates(-23.9242, 29.4685)
        ),
        PopularHub(
            id = "hub_polokwane_cbd",
            name = "Polokwane CBD",
            area = "Library Gardens",
            coordinates = RideCoordinates(-23.9045, 29.4689)
        ),
        PopularHub(
            id = "hub_cycad",
            name = "Cycad Shopping Centre",
            area = "Bendor",
            coordinates = RideCoordinates(-23.9082, 29.4765)
        ),
        PopularHub(
            id = "hub_provincial_hospital",
            name = "Polokwane Hospital",
            area = "Hospital Park",
            coordinates = RideCoordinates(-23.9117, 29.4573)
        ),
        PopularHub(
            id = "hub_seshego_plaza",
            name = "Seshego Circle Centre",
            area = "Seshego",
            coordinates = RideCoordinates(-23.8548, 29.3924)
        ),
        PopularHub(
            id = "hub_ul_turfloop",
            name = "University of Limpopo",
            area = "Turfloop",
            coordinates = RideCoordinates(-23.8888, 29.7386)
        ),
        PopularHub(
            id = "hub_polokwane_airport",
            name = "Polokwane Airport",
            area = "Gateway",
            coordinates = RideCoordinates(-23.8453, 29.4586)
        )
    )

    val RURAL_VILLAGE_LANDMARKS: List<PopularHub> = listOf(
        PopularHub("rural_mankweng_hospital", "Mankweng Hospital", "Mankweng", RideCoordinates(-23.8842, 29.7267)),
        PopularHub("rural_paledi_mall", "Paledi Mall", "Mankweng · Turfloop", RideCoordinates(-23.8860, 29.7294)),
        PopularHub("rural_mankweng_rank", "Mankweng Taxi Rank", "Mankweng", RideCoordinates(-23.8880, 29.7240)),
        PopularHub("rural_ga_mothapo", "Ga-Mothapo Tribal Office", "Ga-Mothapo Village", RideCoordinates(-23.9020, 29.7720)),
        PopularHub("rural_ga_dikgale", "Ga-Dikgale Clinic & Hub", "Ga-Dikgale", RideCoordinates(-23.8165, 29.7540)),
        PopularHub("rural_sebayeng", "Sebayeng Shopping Centre", "Sebayeng · Solomondale", RideCoordinates(-23.7580, 29.6650)),
        PopularHub("rural_solomondale", "Solomondale Main Junction", "Solomondale", RideCoordinates(-23.7760, 29.6440)),
        PopularHub("rural_ga_maja", "Ga-Maja Clinic", "Ga-Maja · Chuene", RideCoordinates(-24.0410, 29.6220)),
        PopularHub("rural_ga_molepo", "Ga-Molepo Tribal Rank", "Ga-Molepo", RideCoordinates(-24.0150, 29.7610)),
        PopularHub("rural_chuene", "Chuenespoort Main Gate", "Chuene", RideCoordinates(-24.1320, 29.5550)),
        PopularHub("rural_moletjie", "Moletjie Mabokelele Hub", "Moletjie", RideCoordinates(-23.7420, 29.3380)),
        PopularHub("rural_koloti", "Koloti Crossroad Spaza", "Koloti · Moletjie", RideCoordinates(-23.7780, 29.2980)),
        PopularHub("rural_ga_matlala", "Ga-Matlala Hospital & Hub", "Ga-Matlala", RideCoordinates(-23.7190, 29.0750)),
        PopularHub("rural_maraba", "Marabastad / Ga-Maraba", "Maraba", RideCoordinates(-23.9610, 29.2420)),
        PopularHub("rural_nobody", "Nobody (Motinti) Turn-Off", "Nobody · R71", RideCoordinates(-23.8890, 29.6620)),
        PopularHub("rural_perskebult", "Perskebult / Luthuli Park", "Seshego West", RideCoordinates(-23.8410, 29.3420)),
        PopularHub("rural_blood_river", "Blood River Main Rank", "Blood River", RideCoordinates(-23.8290, 29.3570)),
        PopularHub("rural_lebowakgomo", "Lebowakgomo Mall & Civic", "Lebowakgomo", RideCoordinates(-24.3030, 29.5050)),
        PopularHub("rural_zebediela", "Zebediela Plaza & Clinic", "Zebediela", RideCoordinates(-24.3110, 29.2660)),
        PopularHub("rural_mogwadi", "Mogwadi (Dendron) Rank", "Mogwadi", RideCoordinates(-23.3760, 29.3270)),
        PopularHub("rural_senwabarwana", "Senwabarwana Mall & Rank", "Senwabarwana (Bochum)", RideCoordinates(-23.2850, 29.1260)),
        PopularHub("rural_mokopane", "Mokopane Crossing Mall", "Mokopane", RideCoordinates(-24.1940, 29.0090)),
        PopularHub("rural_tzaneen", "Tzaneen Crossing & Rank", "Tzaneen", RideCoordinates(-23.8330, 30.1630)),
        PopularHub("rural_malamulele_plaza", "Malamulele Main Plaza & Rank", "Malamulele", RideCoordinates(-22.9730, 30.6840)),
        PopularHub("rural_thohoyandou_pendaris", "Thohoyandou Pendaris Shopping Centre", "Thohoyandou", RideCoordinates(-22.9460, 30.4850)),
        PopularHub("rural_thohoyandou_phangami", "Phangami Mall", "Thohoyandou", RideCoordinates(-22.9550, 30.4720)),
        PopularHub("rural_giyani_plaza", "Giyani Main Taxi Rank & Plaza", "Giyani", RideCoordinates(-23.3020, 30.7180)),
        PopularHub("rural_joburg_park_station", "Johannesburg Park Station & Noord St", "Johannesburg CBD", RideCoordinates(-26.1950, 28.0430)),
        PopularHub("rural_pretoria_bosman", "Pretoria Station & Bosman Rank", "Pretoria Central", RideCoordinates(-25.7540, 28.1880)),
        PopularHub("rural_tembisa_plaza", "Tembisa Plaza & Mall", "Tembisa", RideCoordinates(-25.9960, 28.2250)),
        PopularHub("rural_tembisa_oakmoor", "Oakmoor Station & Taxi Rank", "Tembisa South", RideCoordinates(-26.0230, 28.2280)),
        PopularHub("rural_kempton_park", "Kempton Park Taxi Rank & Station", "Kempton Park", RideCoordinates(-26.1030, 28.2320))
    )

    val ALL_OFFLINE_HUBS: List<PopularHub>
        get() = POLOKWANE_POPULAR_HUBS + RURAL_VILLAGE_LANDMARKS

    fun searchOfflineLandmarks(
        query: String,
        proximity: RideCoordinates? = null,
        limit: Int = 12
    ): List<PlaceSearchSuggestion> {
        val clean = query.trim().lowercase(Locale.ROOT)
        val matches = if (clean.isBlank()) {
            RURAL_VILLAGE_LANDMARKS
        } else {
            ALL_OFFLINE_HUBS.filter {
                it.name.lowercase(Locale.ROOT).contains(clean) ||
                    it.area.lowercase(Locale.ROOT).contains(clean)
            }
        }
        return matches
            .map { hub ->
                val distM = proximity?.let { distanceMeters(it, hub.coordinates) }
                PlaceSearchSuggestion(
                    id = hub.id,
                    label = "${hub.name}, ${hub.area}",
                    latitude = hub.coordinates.latitude,
                    longitude = hub.coordinates.longitude,
                    distanceMeters = distM,
                    provider = "vhaya_offline",
                    category = inferPlaceCategory("${hub.name} ${hub.area}")
                )
            }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            .take(limit)
    }

    fun inferPlaceCategory(label: String, explicitCategory: String? = null): String? {
        explicitCategory?.takeIf { it.isNotBlank() }?.let { return it }
        val lower = label.lowercase(Locale.ROOT)
        return when {
            lower.contains("clinic") || lower.contains("hospital") || lower.contains("medical") ||
                lower.contains("pharmacy") || lower.contains("health") || lower.contains("doctor") -> "clinic"
            lower.contains("mall") || lower.contains("plaza") || lower.contains("shopping") ||
                lower.contains("centre") || lower.contains("center") || lower.contains("market") ||
                lower.contains("pick n pay") || lower.contains("shoprite") || lower.contains("checkers") ||
                lower.contains("spar") || lower.contains("crossing") || lower.contains("boxer") -> "shopping"
            lower.contains("post office") || lower.contains("sassa") || lower.contains("bank") ||
                lower.contains("civic") || lower.contains("tribal") || lower.contains("municipal") ||
                lower.contains("pension") || lower.contains("paypoint") || lower.contains("capitec") ||
                lower.contains("fnb") || lower.contains("absa") || lower.contains("nedbank") -> "pension"
            lower.contains("rank") || lower.contains("station") || lower.contains("taxi") ||
                lower.contains("bus") || lower.contains("junction") || lower.contains("airport") -> "transit"
            else -> null
        }
    }

    fun matchesNearbyCategory(place: PlaceSearchSuggestion, category: NearbyPlaceCategory): Boolean {
        if (category == NearbyPlaceCategory.ALL) return true
        val inferred = inferPlaceCategory(place.label, place.category)
        if (inferred == category.key) return true
        val lower = place.label.lowercase(Locale.ROOT)
        return when (category) {
            NearbyPlaceCategory.ALL -> true
            NearbyPlaceCategory.CLINICS ->
                lower.contains("clinic") || lower.contains("hospital") || lower.contains("pharmacy") || lower.contains("health")
            NearbyPlaceCategory.SHOPPING ->
                lower.contains("mall") || lower.contains("plaza") || lower.contains("shop") || lower.contains("store") || lower.contains("crossing")
            NearbyPlaceCategory.PAYPOINTS ->
                lower.contains("post") || lower.contains("bank") || lower.contains("civic") || lower.contains("sassa") || lower.contains("mall")
            NearbyPlaceCategory.TRANSIT ->
                lower.contains("rank") || lower.contains("station") || lower.contains("taxi") || lower.contains("bus") || lower.contains("airport")
        }
    }

    /**
     * Offline fallback hubs strictly scoped within [maxRadiusKm] of [origin] so users outside Limpopo
     * never see unrelated hardcoded Polokwane locations.
     */
    fun nearbyCuratedOfflineFallback(
        origin: RideCoordinates,
        category: NearbyPlaceCategory = NearbyPlaceCategory.ALL,
        maxRadiusKm: Double = 35.0,
        limit: Int = 12
    ): List<PlaceSearchSuggestion> {
        val maxMeters = maxRadiusKm * 1000.0
        return ALL_OFFLINE_HUBS
            .map { hub ->
                val distM = distanceMeters(origin, hub.coordinates)
                PlaceSearchSuggestion(
                    id = hub.id,
                    label = "${hub.name}, ${hub.area}",
                    latitude = hub.coordinates.latitude,
                    longitude = hub.coordinates.longitude,
                    distanceMeters = distM,
                    provider = "vhaya_offline",
                    category = inferPlaceCategory("${hub.name} ${hub.area}")
                )
            }
            .filter { (it.distanceMeters ?: Double.MAX_VALUE) <= maxMeters }
            .filter { matchesNearbyCategory(it, category) }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            .take(limit)
    }

    fun isRuralOrPeriUrban(pickup: RideCoordinates?, destination: RideCoordinates?): Boolean {
        val pickupDistKm = haversineKm(pickup, DEFAULT_MAP_CENTER) ?: 0.0
        val destDistKm = haversineKm(destination, DEFAULT_MAP_CENTER) ?: 0.0
        val tripKm = haversineKm(pickup, destination) ?: 0.0
        return pickupDistKm > 9.0 || destDistKm > 9.0 || tripKm > 12.0
    }

    fun findNearestHubName(point: RideCoordinates, maxRadiusMeters: Double = 350.0): String? {
        val closestUrban = POLOKWANE_POPULAR_HUBS
            .map { hub -> hub to distanceMeters(point, hub.coordinates) }
            .minByOrNull { it.second }
        if (closestUrban != null && closestUrban.second <= maxRadiusMeters) {
            return "${closestUrban.first.name} (${closestUrban.first.area})"
        }
        val closestRural = RURAL_VILLAGE_LANDMARKS
            .map { hub -> hub to distanceMeters(point, hub.coordinates) }
            .minByOrNull { it.second }
        val ruralRadius = max(maxRadiusMeters, 2_500.0)
        return if (closestRural != null && closestRural.second <= ruralRadius) {
            "Near ${closestRural.first.name} (${closestRural.first.area})"
        } else null
    }

    fun nearestLandmarkLabel(point: RideCoordinates): String {
        return findNearestHubName(point)
            ?: "Pin (${"%.4f".format(Locale.US, point.latitude)}, ${"%.4f".format(Locale.US, point.longitude)})"
    }

    val SOUTH_AFRICAN_VEHICLE_CATALOG: List<String> = listOf(
        "Toyota Corolla",
        "Toyota Corolla Cross",
        "Toyota Corolla Quest",
        "Toyota Fortuner",
        "Toyota Hilux",
        "Toyota Starlet",
        "Toyota Urban Cruiser",
        "Toyota Yaris",
        "Volkswagen Polo",
        "Volkswagen Polo Vivo",
        "Volkswagen T-Cross",
        "Volkswagen Taigo",
        "Volkswagen Tiguan",
        "Suzuki Swift",
        "Suzuki Baleno",
        "Suzuki Fronx",
        "Suzuki Ertiga",
        "Hyundai Grand i10",
        "Hyundai i20",
        "Hyundai Venue",
        "Hyundai Creta",
        "Kia Picanto",
        "Kia Sonet",
        "Ford Ranger",
        "Ford Territory",
        "Nissan Magnite",
        "Renault Kwid",
        "Renault Triber",
        "Haval Jolion",
        "Chery Tiggo 4 Pro"
    )

    fun searchVehicles(query: String, limit: Int = 8): List<String> {
        val q = query.trim().lowercase(Locale.ROOT)
        return if (q.isEmpty()) {
            listOf(
                "Toyota Corolla Quest",
                "Toyota Corolla",
                "Volkswagen Polo Vivo",
                "Volkswagen Polo",
                "Suzuki Swift",
                "Toyota Starlet"
            )
        } else {
            SOUTH_AFRICAN_VEHICLE_CATALOG.filter { it.lowercase(Locale.ROOT).contains(q) }.take(limit)
        }
    }

    enum class DemandTier(val label: String) {
        LOW("Warm"),
        MEDIUM("Busy"),
        HIGH("Hot")
    }

    data class DemandHeatZone(
        val id: String,
        val label: String,
        val centroid: RideCoordinates,
        val intensity: Float,
        val tier: DemandTier,
        val requestCount: Int,
        val liveOpenCount: Int,
        val avgFareZar: Double,
        val radiusMeters: Double
    )

    fun buildDemandHeatZones(
        trips: List<RideTrip>,
        nowMs: Long = System.currentTimeMillis()
    ): List<DemandHeatZone> {
        data class WeightedPoint(
            val trip: RideTrip,
            val coords: RideCoordinates,
            val weight: Double,
            val isLiveOpen: Boolean
        )

        val candidates = trips.mapNotNull { trip ->
            val coords = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return@mapNotNull null
            val ageMinutes = max(0.0, (nowMs - trip.createdAt) / 60_000.0)
            val expired = isTripExpired(trip, nowMs)
            val baseWeight = when (trip.status) {
                RideStatus.REQUESTED, RideStatus.COUNTERED -> if (expired) 0.80 else 1.0
                RideStatus.UNFULFILLED -> 0.80
                RideStatus.ACCEPTED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS -> 0.65
                RideStatus.COMPLETED -> 0.45
                RideStatus.CANCELLED -> if (ageMinutes <= 120.0) 0.15 else 0.0
            }
            if (baseWeight <= 0.0) return@mapNotNull null
            val recencyFactor = 1.0 / (1.0 + ageMinutes / 90.0)
            val weight = baseWeight * recencyFactor
            if (weight < 0.04) return@mapNotNull null
            WeightedPoint(
                trip = trip,
                coords = coords,
                weight = weight,
                isLiveOpen = trip.status.isOpenForDrivers && !expired
            )
        }.sortedByDescending { it.weight }

        if (candidates.isEmpty()) return emptyList()

        val clusterRadiusMeters = 900.0
        val assigned = BooleanArray(candidates.size)
        data class RawCluster(
            val centroid: RideCoordinates,
            val score: Double,
            val points: List<WeightedPoint>
        )

        val rawClusters = mutableListOf<RawCluster>()
        for (i in candidates.indices) {
            if (assigned[i]) continue
            assigned[i] = true
            val seed = candidates[i]
            val members = mutableListOf(seed)
            var sumLat = seed.coords.latitude * seed.weight
            var sumLon = seed.coords.longitude * seed.weight
            var sumWeight = seed.weight

            for (j in candidates.indices) {
                if (assigned[j]) continue
                val other = candidates[j]
                val currentCentroid = RideCoordinates(sumLat / sumWeight, sumLon / sumWeight)
                if (distanceMeters(currentCentroid, other.coords) <= clusterRadiusMeters) {
                    assigned[j] = true
                    members += other
                    sumLat += other.coords.latitude * other.weight
                    sumLon += other.coords.longitude * other.weight
                    sumWeight += other.weight
                }
            }

            rawClusters += RawCluster(
                centroid = RideCoordinates(sumLat / sumWeight, sumLon / sumWeight),
                score = sumWeight,
                points = members
            )
        }

        val maxScore = max(rawClusters.maxOf { it.score }, 2.4)
        return rawClusters
            .sortedWith(compareByDescending<RawCluster> { it.points.count { p -> p.isLiveOpen } }.thenByDescending { it.score })
            .take(12)
            .mapIndexed { idx, cluster ->
                val intensity = (cluster.score / maxScore).toFloat().coerceIn(0.22f, 1.0f)
                val liveCount = cluster.points.count { it.isLiveOpen }
                val tier = when {
                    intensity >= 0.68f || liveCount >= 2 -> DemandTier.HIGH
                    intensity >= 0.38f || liveCount >= 1 -> DemandTier.MEDIUM
                    else -> DemandTier.LOW
                }
                val fallbackLabel = cluster.points
                    .maxByOrNull { it.trip.createdAt }
                    ?.trip
                    ?.pickup
                    ?.substringBefore(" ·")
                    ?.substringBefore(",")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Active Zone"
                val label = findNearestHubName(cluster.centroid, maxRadiusMeters = 850.0)
                    ?.substringBefore(" (")
                    ?: fallbackLabel
                val avgFare = cluster.points.map { it.trip.fare }.average().takeIf { !it.isNaN() } ?: 0.0
                val radiusMeters = 520.0 + intensity * 420.0
                DemandHeatZone(
                    id = "zone_${idx}_${(cluster.centroid.latitude * 1000).toInt()}_${(cluster.centroid.longitude * 1000).toInt()}",
                    label = label,
                    centroid = cluster.centroid,
                    intensity = intensity,
                    tier = tier,
                    requestCount = cluster.points.size,
                    liveOpenCount = liveCount,
                    avgFareZar = avgFare,
                    radiusMeters = radiusMeters
                )
            }
    }

    data class ActiveNavigationProgress(
        val upcomingStepIndex: Int,
        val upcomingStep: NavigationStep,
        val nextAfterStep: NavigationStep?,
        val distanceToManeuverMeters: Double,
        val remainingDistanceMeters: Int,
        val remainingDurationSeconds: Int
    )

    fun formatOsrmInstruction(
        type: String,
        modifier: String?,
        exit: Int?,
        streetName: String
    ): String {
        val cleanType = type.trim().lowercase(Locale.ROOT)
        val cleanMod = modifier?.trim()?.lowercase(Locale.ROOT)
        val cleanStreet = streetName.trim()
        val ontoClause = if (cleanStreet.isNotBlank()) " onto $cleanStreet" else ""

        fun turnVerb(mod: String?): String = when (mod) {
            "left" -> "Turn left"
            "right" -> "Turn right"
            "slight left" -> "Turn slight left"
            "slight right" -> "Turn slight right"
            "sharp left" -> "Turn sharp left"
            "sharp right" -> "Turn sharp right"
            "uturn" -> "Make a U-turn"
            "straight" -> "Continue straight"
            else -> "Continue"
        }

        return when (cleanType) {
            "depart" -> if (cleanStreet.isNotBlank()) "Head on $cleanStreet" else "Head toward the route"
            "arrive" -> "Arrive at destination"
            "roundabout", "rotary" -> {
                val exitPart = exit?.let { "take exit $it" } ?: "exit roundabout"
                "At roundabout, $exitPart$ontoClause"
            }
            "exit roundabout", "exit rotary" -> "Exit roundabout$ontoClause"
            "fork" -> when (cleanMod) {
                "left", "slight left", "sharp left" -> "Keep left at fork$ontoClause"
                "right", "slight right", "sharp right" -> "Keep right at fork$ontoClause"
                else -> "Continue at fork$ontoClause"
            }
            "merge" -> when (cleanMod) {
                "left", "slight left" -> "Merge left$ontoClause"
                "right", "slight right" -> "Merge right$ontoClause"
                else -> "Merge$ontoClause"
            }
            "on ramp" -> "Take ramp$ontoClause"
            "off ramp" -> "Take exit$ontoClause"
            "end of road" -> "At end of road, ${turnVerb(cleanMod).replaceFirstChar { it.lowercase(Locale.ROOT) }}$ontoClause"
            "new name" -> if (cleanStreet.isNotBlank()) "Continue onto $cleanStreet" else "Continue ahead"
            "turn", "continue", "notification", "use lane" -> "${turnVerb(cleanMod)}$ontoClause"
            else -> if (cleanStreet.isNotBlank()) "Continue onto $cleanStreet" else "Follow the route"
        }
    }

    private fun pointToSegmentDistanceAndT(
        point: RideCoordinates,
        a: RideCoordinates,
        b: RideCoordinates
    ): Pair<Double, Double> {
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
            return distanceMeters(point, a) to 0.0
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val proj = RideCoordinates(
            latitude = a.latitude + (b.latitude - a.latitude) * t,
            longitude = a.longitude + (b.longitude - a.longitude) * t
        )
        return distanceMeters(point, proj) to t
    }

    fun computeActiveNavigationProgress(
        driverCoords: RideCoordinates?,
        route: RideRoute,
        targetCoords: RideCoordinates?
    ): ActiveNavigationProgress? {
        val steps = route.steps
        if (steps.isEmpty()) return null

        if (steps.size == 1) {
            val step = steps.first()
            val directMeters = if (driverCoords != null && targetCoords != null) {
                distanceMeters(driverCoords, targetCoords)
            } else {
                route.distanceMeters.toDouble().coerceAtLeast(0.0)
            }
            return ActiveNavigationProgress(
                upcomingStepIndex = 0,
                upcomingStep = step,
                nextAfterStep = null,
                distanceToManeuverMeters = directMeters,
                remainingDistanceMeters = directMeters.toInt(),
                remainingDurationSeconds = route.durationSeconds.coerceAtLeast(1)
            )
        }

        if (driverCoords == null) {
            val upcomingIdx = 1.coerceAtMost(steps.lastIndex)
            val distToManeuver = steps.first().distanceMeters.toDouble().coerceAtLeast(0.0)
            return ActiveNavigationProgress(
                upcomingStepIndex = upcomingIdx,
                upcomingStep = steps[upcomingIdx],
                nextAfterStep = steps.getOrNull(upcomingIdx + 1),
                distanceToManeuverMeters = distToManeuver,
                remainingDistanceMeters = route.distanceMeters.coerceAtLeast(distToManeuver.toInt()),
                remainingDurationSeconds = route.durationSeconds.coerceAtLeast(30)
            )
        }

        var bestSegIdx = 0
        var bestT = 0.0
        var bestSegDist = Double.POSITIVE_INFINITY
        for (i in 0 until steps.lastIndex) {
            val locA = steps[i].location
            val locB = steps[i + 1].location
            if (locA != null && locB != null) {
                val (segDist, t) = pointToSegmentDistanceAndT(driverCoords, locA, locB)
                if (segDist < bestSegDist) {
                    bestSegDist = segDist
                    bestSegIdx = i
                    bestT = t
                }
            }
        }

        var upcomingIdx = (bestSegIdx + 1).coerceIn(1, steps.lastIndex)
        val currentStep = steps[bestSegIdx]
        var upcomingStep = steps[upcomingIdx]
        var distToManeuver = upcomingStep.location?.let { distanceMeters(driverCoords, it) }
            ?: (currentStep.distanceMeters * (1.0 - bestT)).coerceAtLeast(0.0)

        // If driver already reached within 25m of an intermediate turn and there is a following step, advance cleanly
        if (distToManeuver <= 25.0 && upcomingIdx < steps.lastIndex) {
            upcomingIdx += 1
            upcomingStep = steps[upcomingIdx]
            distToManeuver = upcomingStep.location?.let { distanceMeters(driverCoords, it) }
                ?: steps[upcomingIdx - 1].distanceMeters.toDouble().coerceAtLeast(0.0)
        }

        val tailDistance = steps.subList(upcomingIdx, steps.size).sumOf { it.distanceMeters.coerceAtLeast(0) }
        val totalRemainingMeters = (distToManeuver.toInt() + tailDistance).coerceAtLeast(0)

        val currentSegFractionLeft = (1.0 - bestT).coerceIn(0.0, 1.0)
        val currentSegDurLeft = (steps[bestSegIdx].durationSeconds * currentSegFractionLeft).toInt()
        val tailDuration = steps.subList(upcomingIdx, steps.size).sumOf { it.durationSeconds.coerceAtLeast(0) }
        val remainingDurationSec = (currentSegDurLeft + tailDuration)
            .coerceAtLeast(if (totalRemainingMeters > 40) 30 else 0)

        return ActiveNavigationProgress(
            upcomingStepIndex = upcomingIdx,
            upcomingStep = upcomingStep,
            nextAfterStep = steps.getOrNull(upcomingIdx + 1),
            distanceToManeuverMeters = distToManeuver,
            remainingDistanceMeters = totalRemainingMeters,
            remainingDurationSeconds = remainingDurationSec
        )
    }

    data class OfferPriceComparison(
        val deltaAmount: Double,
        val deltaPercent: Int,
        val isMatchesRiderOffer: Boolean,
        val isCounterHigher: Boolean,
        val isLower: Boolean,
        val badgeText: String,
        val driverEtaMinutes: Int?,
        val driverDistanceKm: Double?
    )

    fun summarizeOfferComparison(offer: RideOffer, trip: RideTrip): OfferPriceComparison {
        val riderFare = trip.fare.coerceAtLeast(1.0)
        val delta = offer.amount - riderFare
        val absDelta = kotlin.math.abs(delta)
        val pct = kotlin.math.round((absDelta / riderFare) * 100.0).toInt()

        val matches = absDelta < 0.50
        val higher = !matches && delta > 0.0
        val lower = !matches && delta < 0.0

        val badgeText = when {
            matches -> "Matches your price"
            higher -> if (pct > 0) "+${formatZar(absDelta)} (+$pct%)" else "+${formatZar(absDelta)}"
            else -> if (pct > 0) "-${formatZar(absDelta)} (-$pct%)" else "-${formatZar(absDelta)}"
        }

        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates
        val distKm = haversineKm(offer.driverProfile.lastLocation, pickup)
        val etaMin = distKm?.let { kotlin.math.ceil((it * 1000.0) / 500.0).toInt().coerceAtLeast(1) }

        return OfferPriceComparison(
            deltaAmount = delta,
            deltaPercent = pct,
            isMatchesRiderOffer = matches,
            isCounterHigher = higher,
            isLower = lower,
            badgeText = badgeText,
            driverEtaMinutes = etaMin,
            driverDistanceKm = distKm
        )
    }

    enum class PasswordStrength(val label: String) {
        EMPTY("Enter password"),
        WEAK("Weak"),
        FAIR("Fair"),
        GOOD("Good"),
        STRONG("Strong")
    }

    data class PasswordPolicyEvaluation(
        val hasMinLength: Boolean,
        val hasUppercase: Boolean,
        val hasLowercase: Boolean,
        val hasDigit: Boolean,
        val hasSpecialChar: Boolean,
        val hasNoSurroundingSpaces: Boolean,
        val satisfiedCount: Int,
        val totalRules: Int = 5,
        val strength: PasswordStrength,
        val isValid: Boolean
    ) {
        val missingRequirements: List<String>
            get() = buildList {
                if (!hasMinLength) add("8+ characters")
                if (!hasUppercase) add("an uppercase letter (A-Z)")
                if (!hasLowercase) add("a lowercase letter (a-z)")
                if (!hasDigit) add("a number (0-9)")
                if (!hasSpecialChar) add("a special character (!@#\$...)")
            }

        fun validationErrorOrNull(): String? {
            if (isValid) return null
            if (!hasNoSurroundingSpaces) {
                return "Password cannot start or end with spaces."
            }
            return "Password must include ${missingRequirements.joinToString(", ")}."
        }
    }

    fun evaluatePasswordPolicy(password: String): PasswordPolicyEvaluation {
        if (password.isEmpty()) {
            return PasswordPolicyEvaluation(
                hasMinLength = false,
                hasUppercase = false,
                hasLowercase = false,
                hasDigit = false,
                hasSpecialChar = false,
                hasNoSurroundingSpaces = true,
                satisfiedCount = 0,
                strength = PasswordStrength.EMPTY,
                isValid = false
            )
        }
        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        val hasNoSurroundingSpaces = password == password.trim()
        val satisfiedCount = listOf(
            hasMinLength,
            hasUppercase,
            hasLowercase,
            hasDigit,
            hasSpecialChar
        ).count { it }
        val valid = satisfiedCount == 5 && hasNoSurroundingSpaces
        val strength = when {
            valid -> PasswordStrength.STRONG
            !hasMinLength -> PasswordStrength.WEAK
            satisfiedCount >= 4 -> PasswordStrength.GOOD
            satisfiedCount >= 3 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
        return PasswordPolicyEvaluation(
            hasMinLength = hasMinLength,
            hasUppercase = hasUppercase,
            hasLowercase = hasLowercase,
            hasDigit = hasDigit,
            hasSpecialChar = hasSpecialChar,
            hasNoSurroundingSpaces = hasNoSurroundingSpaces,
            satisfiedCount = satisfiedCount,
            strength = strength,
            isValid = valid
        )
    }

    const val SUPPORT_EMAIL = DRIVER_DOCS_EMAIL

    fun cleanApiOrigin(apiBaseUrl: String): String {
        val trimmed = apiBaseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/api", ignoreCase = true)) {
            trimmed.dropLast(4).trimEnd('/')
        } else {
            trimmed
        }
    }

    fun isProductionSafeApiUrl(apiBaseUrl: String, isDebugBuild: Boolean): Boolean {
        val origin = cleanApiOrigin(apiBaseUrl)
        if (origin.isBlank()) return false
        val lower = origin.lowercase(Locale.ROOT)
        if (lower.startsWith("https://")) {
            return lower.length > "https://".length && !lower.contains(" ")
        }
        if (!isDebugBuild) return false
        return lower.startsWith("http://localhost") ||
            lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("http://10.0.2.2")
    }

    fun resolvePrivacyPolicyUrl(apiBaseUrl: String, isDebugBuild: Boolean = false): String? {
        if (!isProductionSafeApiUrl(apiBaseUrl, isDebugBuild)) return null
        return "${cleanApiOrigin(apiBaseUrl)}/privacy"
    }

    fun resolveAccountDeletionUrl(apiBaseUrl: String, isDebugBuild: Boolean = false): String? {
        if (!isProductionSafeApiUrl(apiBaseUrl, isDebugBuild)) return null
        return "${cleanApiOrigin(apiBaseUrl)}/delete-account"
    }

    const val ROUTE_CACHE_TTL_MS: Long = 14L * 24L * 60L * 60L * 1000L
    const val PLACE_CACHE_TTL_MS: Long = 30L * 24L * 60L * 60L * 1000L
    const val QUEUED_FARE_DRIFT_TOLERANCE_RATIO: Double = 1.15

    data class QueuedFareDecision(
        val finalFare: Double,
        val finalPricingMode: PricingMode,
        val fareDriftCapped: Boolean
    )

    fun resolveQueuedRideFare(
        offlineFare: Double,
        customOfferFare: Double?,
        isSharedRide: Boolean,
        isReturnTrip: Boolean,
        includesGravelRoad: Boolean,
        liveRuralEstimateZar: Double,
        isReturnRequest: Boolean = false
    ): QueuedFareDecision {
        val safeOffline = offlineFare.coerceAtLeast(20.0)
        val safeLive = liveRuralEstimateZar.coerceAtLeast(20.0)
        if (customOfferFare != null) {
            return QueuedFareDecision(
                finalFare = customOfferFare.coerceAtLeast(20.0),
                finalPricingMode = PricingMode.OFFER,
                fareDriftCapped = false
            )
        }
        val driftedUp = safeLive > safeOffline * QUEUED_FARE_DRIFT_TOLERANCE_RATIO
        if (driftedUp) {
            return QueuedFareDecision(
                finalFare = safeOffline,
                finalPricingMode = PricingMode.OFFER,
                fareDriftCapped = true
            )
        }
        val isOfferMode = isSharedRide || isReturnTrip || isReturnRequest || includesGravelRoad
        return QueuedFareDecision(
            finalFare = if (isOfferMode) safeOffline else safeLive,
            finalPricingMode = if (isOfferMode) PricingMode.OFFER else PricingMode.ESTIMATE,
            fareDriftCapped = false
        )
    }

    fun buildOfflineEstimatedRoute(
        pickup: RideCoordinates,
        destination: RideCoordinates
    ): RideRoute {
        val straightKm = haversineKm(pickup, destination) ?: 1.0
        val roadKm = (straightKm * 1.25).coerceAtLeast(0.35)
        val distanceMeters = (roadKm * 1000.0).roundToInt().coerceAtLeast(350)
        // Conservative rural corridor speed (~42 km/h = 11.67 m/s)
        val durationSeconds = (distanceMeters / 11.67).roundToInt().coerceAtLeast(180)
        return RideRoute(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            encodedPolyline = PolylineCodec.encode(listOf(pickup, destination)),
            provider = "offline-estimate",
            originCoordinates = pickup,
            destinationCoordinates = destination,
            fareEstimate = FareEstimationService.calculateDistanceTimeFare(
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                category = RideCategory.STANDARD
            ),
            quoteToken = null
        )
    }

    fun computeOfflineSyncBackoffMs(retryCount: Int): Long {
        val exponent = retryCount.coerceIn(0, 6)
        return (5_000L * (1L shl exponent)).coerceAtMost(300_000L)
    }
}
