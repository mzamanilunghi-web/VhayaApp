package com.wayfare.ride

import com.wayfare.ride.domain.AppLanguage
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PolylineCodec
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideMessage
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.RuralStrings
import com.wayfare.ride.domain.SmartRideGuards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRideGuardsTest {

    private val pickup = RideCoordinates(latitude = -23.9045, longitude = 29.4688)
    private val midpoint = RideCoordinates(latitude = -23.8990, longitude = 29.4600)
    private val destination = RideCoordinates(latitude = -23.8920, longitude = 29.4500)

    private fun sampleTrip(
        status: RideStatus = RideStatus.ACCEPTED,
        fare: Double = 80.0,
        distanceMeters: Int = 4000,
        messages: List<RideMessage> = emptyList(),
        pickupNote: String? = null,
        cashChangeForZar: Int? = null
    ): RideTrip {
        return RideTrip(
            id = "trip_smart_1",
            riderId = "rider_1",
            driverId = "driver_1",
            pickup = "Mall of the North",
            destination = "Polokwane CBD",
            category = RideCategory.STANDARD,
            fare = fare,
            pricingMode = PricingMode.OFFER,
            status = status,
            paymentStatus = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.CASH,
            createdAt = 1_000_000L,
            pickupCoordinates = pickup,
            destinationCoordinates = destination,
            route = RideRoute(
                originCoordinates = pickup,
                destinationCoordinates = destination,
                distanceMeters = distanceMeters,
                durationSeconds = 480,
                encodedPolyline = PolylineCodec.encode(listOf(pickup, midpoint, destination))
            ),
            tripPin = "4821",
            arrivalConfirmationMethod = "gps_auto",
            messages = messages,
            pickupNote = pickupNote,
            cashChangeForZar = cashChangeForZar
        )
    }

    @Test
    fun stallTracker_flagsIdleDriverAfterNinetySecondsWithoutProgress() {
        val t0 = 1_000_000L
        val tracker0 = SmartRideGuards.advanceStallTracker(
            existing = null,
            tripId = "trip_smart_1",
            distanceToPickupMeters = 900.0,
            nowMs = t0
        )
        // Small jitter (<60m progress) 95s later should keep closestObservedAtMs anchored at t0.
        val tracker1 = SmartRideGuards.advanceStallTracker(
            existing = tracker0,
            tripId = "trip_smart_1",
            distanceToPickupMeters = 875.0,
            nowMs = t0 + 95_000L
        )
        val trip = sampleTrip(status = RideStatus.ACCEPTED)
        // Coordinates ~875m away from pickup
        val stalledDriverCoords = RideCoordinates(latitude = -23.9124, longitude = 29.4688)
        val assessment = SmartRideGuards.evaluateDriverApproachHealth(
            trip = trip,
            driverCoords = stalledDriverCoords,
            tracker = tracker1,
            nowMs = t0 + 95_000L
        )
        assertNotNull(assessment)
        assertTrue(assessment!!.isStalled)
        assertTrue(assessment.shouldOfferSwitch)
        assertFalse(assessment.isDrivingAway)
    }

    @Test
    fun stallTracker_flagsDriverMovingWrongWayBeyondFourHundredMeters() {
        val t0 = 2_000_000L
        val tracker0 = SmartRideGuards.advanceStallTracker(
            existing = null,
            tripId = "trip_smart_1",
            distanceToPickupMeters = 300.0,
            nowMs = t0
        )
        val trip = sampleTrip(status = RideStatus.ACCEPTED)
        // Place driver ~900m south of pickup -> moved ~600m away from 300m closest mark
        val retreatingDriver = RideCoordinates(latitude = -23.9126, longitude = 29.4688)
        val assessment = SmartRideGuards.evaluateDriverApproachHealth(
            trip = trip,
            driverCoords = retreatingDriver,
            tracker = tracker0,
            nowMs = t0 + 25_000L
        )
        assertNotNull(assessment)
        assertTrue(assessment!!.isDrivingAway)
        assertTrue(assessment.shouldOfferSwitch)
    }

    @Test
    fun stallTracker_suppressesWarningInsideArrivalRadius() {
        val t0 = 3_000_000L
        val tracker0 = SmartRideGuards.advanceStallTracker(
            existing = null,
            tripId = "trip_smart_1",
            distanceToPickupMeters = 100.0,
            nowMs = t0
        )
        val trip = sampleTrip(status = RideStatus.ACCEPTED)
        val nearPickup = RideCoordinates(latitude = -23.9049, longitude = 29.4688)
        val assessment = SmartRideGuards.evaluateDriverApproachHealth(
            trip = trip,
            driverCoords = nearPickup,
            tracker = tracker0,
            nowMs = t0 + 180_000L
        )
        assertNotNull(assessment)
        assertFalse(assessment!!.shouldOfferSwitch)
    }

    @Test
    fun offRouteGuard_detectsOnRouteVersusDeviationOverFiveHundredMeters() {
        val trip = sampleTrip(status = RideStatus.IN_PROGRESS)
        val onRoutePoint = midpoint
        val onRoute = SmartRideGuards.evaluateOffRoute(onRoutePoint, trip)
        assertFalse(onRoute.isOffRoute)
        assertTrue(onRoute.distanceFromRouteMeters < 20.0)

        // Offset ~0.01 deg longitude (~1000m) off route
        val farOffRoutePoint = RideCoordinates(latitude = midpoint.latitude, longitude = midpoint.longitude + 0.01)
        val offRoute = SmartRideGuards.evaluateOffRoute(farOffRoutePoint, trip)
        assertTrue(offRoute.isOffRoute)
        assertTrue(offRoute.distanceFromRouteMeters > 500.0)
    }

    @Test
    fun dropOffProximity_warnsWhenDriverMoreThanThreeHundredMetersFromDestination() {
        val nearDest = SmartRideGuards.evaluateDropOffProximity(
            driverCoords = RideCoordinates(destination.latitude + 0.0005, destination.longitude),
            destinationCoords = destination
        )
        assertFalse(nearDest.isFarFromDestination)

        val farFromDest = SmartRideGuards.evaluateDropOffProximity(
            driverCoords = pickup,
            destinationCoords = destination
        )
        assertTrue(farFromDest.isFarFromDestination)
        assertTrue(farFromDest.distanceMeters > 300.0)
    }

    @Test
    fun antiExtortionScanner_flagsDriverSurchargesAndIgnoresNormalOrOwnMessages() {
        val normalChat = listOf(
            RideMessage("m1", "trip_smart_1", "driver", "driver", "I am at the main gate by Woolworths", 100L),
            RideMessage("m2", "trip_smart_1", "rider", "driver", "Please don't ask for extra cash", 101L)
        )
        val safeResult = SmartRideGuards.scanChatForFareExtortion(normalChat, viewerRole = "rider")
        assertFalse(safeResult.isFlagged)

        val extortionChat = normalChat + RideMessage(
            id = "m3",
            tripId = "trip_smart_1",
            sender = "driver",
            channel = "driver",
            message = "Traffic is heavy, add R30 petrol money or cancel and pay outside app",
            createdAt = 102L
        )
        val flagged = SmartRideGuards.scanChatForFareExtortion(extortionChat, viewerRole = "rider")
        assertTrue(flagged.isFlagged)
        assertEquals("m3", flagged.flaggedMessage?.id)
        assertNotNull(flagged.matchedCue)
    }

    @Test
    fun offerAcceptanceCoach_classifiesHighModerateLowAndPeakDemand() {
        // Off-peak 11:00 -> benchmark = R20/km -> 4km benchmark = R80
        val high = SmartRideGuards.evaluateOfferAcceptance(
            fareZar = 78.0,
            distanceMeters = 4000,
            hourOfDay = 11,
            minuteOfHour = 0
        )
        assertEquals(SmartRideGuards.AcceptanceTier.HIGH, high.tier)
        assertFalse(high.isPeakHour)

        val moderate = SmartRideGuards.evaluateOfferAcceptance(
            fareZar = 64.0,
            distanceMeters = 4000,
            hourOfDay = 11,
            minuteOfHour = 0
        )
        assertEquals(SmartRideGuards.AcceptanceTier.MODERATE, moderate.tier)

        val low = SmartRideGuards.evaluateOfferAcceptance(
            fareZar = 45.0,
            distanceMeters = 4000,
            hourOfDay = 11,
            minuteOfHour = 0
        )
        assertEquals(SmartRideGuards.AcceptanceTier.LOW, low.tier)

        // Peak hour 07:30 increases benchmark by 10% (R88 for 4km)
        val peak = SmartRideGuards.evaluateOfferAcceptance(
            fareZar = 78.0,
            distanceMeters = 4000,
            hourOfDay = 7,
            minuteOfHour = 30
        )
        assertTrue(peak.isPeakHour)
        assertTrue(peak.recommendedFairZar > high.recommendedFairZar)
    }

    @Test
    fun waitTimer_countsDownThreeMinutesThenTracksOvertime() {
        assertNull(SmartRideGuards.evaluateWaitTimer(arrivedAtMs = null, nowMs = 500_000L))

        val arrivedAt = 1_000_000L
        val atTwoMinutes = SmartRideGuards.evaluateWaitTimer(arrivedAtMs = arrivedAt, nowMs = arrivedAt + 120_000L)
        assertNotNull(atTwoMinutes)
        assertFalse(atTwoMinutes!!.isFreeWaitExpired)
        assertEquals(60_000L, atTwoMinutes.remainingFreeMs)
        assertEquals(0L, atTwoMinutes.overtimeMs)
        assertTrue(atTwoMinutes.badgeLabel.startsWith("Free wait"))

        val atFourMinutes = SmartRideGuards.evaluateWaitTimer(arrivedAtMs = arrivedAt, nowMs = arrivedAt + 240_000L)
        assertNotNull(atFourMinutes)
        assertTrue(atFourMinutes!!.isFreeWaitExpired)
        assertEquals(0L, atFourMinutes.remainingFreeMs)
        assertEquals(60_000L, atFourMinutes.overtimeMs)
        assertTrue(atFourMinutes.badgeLabel.startsWith("Wait exceeded +"))
    }

    @Test
    fun driverMinRateFilter_filtersBelowThresholdPerKm() {
        // 4km trip at R60 = R15/km
        val trip15PerKm = sampleTrip(fare = 60.0, distanceMeters = 4000)
        assertTrue(SmartRideGuards.matchesDriverMinRateFilter(trip15PerKm, 0.0))
        assertTrue(SmartRideGuards.matchesDriverMinRateFilter(trip15PerKm, 15.0))
        assertFalse(SmartRideGuards.matchesDriverMinRateFilter(trip15PerKm, 20.0))
    }

    @Test
    fun supportEvidence_includesTelemetryAndJsonFields() {
        val extortionMsg = RideMessage("m9", "trip_smart_1", "driver", "driver", "Pay extra cash please", 10L)
        val trip = sampleTrip(
            status = RideStatus.COMPLETED,
            fare = 85.0,
            messages = listOf(extortionMsg),
            pickupNote = "Security boom",
            cashChangeForZar = 200
        )
        val summary = SmartRideGuards.buildSupportTelemetrySummary(trip, pickup)
        assertTrue(summary.contains("[Trip Telemetry]"))
        assertTrue(summary.contains("id=trip_smart_1"))
        assertTrue(summary.contains("changeFor=R200"))
        assertTrue(summary.contains("chatAlert="))

        val evidence = SmartRideGuards.buildSupportEvidenceJson(trip, pickup)
        assertEquals("\"trip_smart_1\"", evidence["tripId"].toString())
        assertEquals("true", evidence["chatExtortionFlagged"].toString())
        assertEquals("200", evidence["cashChangeForZar"].toString())
    }

    @Test
    fun ruralCategories_scaleBakkieAndParcelWithFloors() {
        // 5km = R100 standard base
        assertEquals(100.0, LaunchPolicy.calculateEstimate(5000, RideCategory.STANDARD), 0.01)
        assertEquals(125.0, LaunchPolicy.calculateEstimate(5000, RideCategory.BAKKIE), 0.01)
        assertEquals(85.0, LaunchPolicy.calculateEstimate(5000, RideCategory.PARCEL), 0.01)

        // Short 1km trip floors Parcel at R20 minimum
        assertEquals(20.0, LaunchPolicy.calculateEstimate(1000, RideCategory.PARCEL), 0.01)
    }

    @Test
    fun offlineVillageGazetteer_searchesRuralLimpopoLandmarksAndDetectsPeriUrban() {
        val mankweng = LaunchPolicy.searchOfflineLandmarks("Mankweng")
        assertTrue(mankweng.isNotEmpty())
        assertTrue(mankweng.any { it.label.contains("Mankweng Hospital") })
        assertTrue(mankweng.all { it.provider == "vhaya_offline" })

        val seshego = LaunchPolicy.searchOfflineLandmarks("Seshego")
        assertTrue(seshego.any { it.label.contains("Seshego") })

        val blankResults = LaunchPolicy.searchOfflineLandmarks("")
        assertTrue(blankResults.isNotEmpty())

        // Urban CBD -> CBD is not rural
        assertFalse(LaunchPolicy.isRuralOrPeriUrban(pickup, destination))
        // Polokwane CBD -> Ga-Mothapo Village (~30km away) is rural/peri-urban
        val gaMothapo = LaunchPolicy.RURAL_VILLAGE_LANDMARKS.first { it.id == "rural_ga_mothapo" }.coordinates
        assertTrue(LaunchPolicy.isRuralOrPeriUrban(pickup, gaMothapo))
    }

    @Test
    fun ruralAdjustedFare_handlesSharedReturnGravelAndBakkieFloor() {
        // Standard 100 base with 1 shared seat reduces fare to R70
        val singleSeatShared = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = 100.0,
            category = RideCategory.STANDARD,
            isSharedRide = true,
            seatCount = 1
        )
        assertEquals(70.0, singleSeatShared, 0.01)

        // Wait & return + gravel road scales appropriately
        val waitReturnGravel = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = 100.0,
            category = RideCategory.STANDARD,
            isReturnTrip = true,
            includesGravelRoad = true
        )
        assertEquals(207.0, waitReturnGravel, 0.01)

        // Short bakkie trip enforces R30 minimum
        val shortBakkie = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = 20.0,
            category = RideCategory.BAKKIE
        )
        assertEquals(30.0, shortBakkie, 0.01)
    }

    @Test
    fun ruralOfferCoachAndSmsFallback_workWithoutNetworkTiles() {
        val forecast = SmartRideGuards.evaluateOfferAcceptance(
            fareZar = 210.0,
            distanceMeters = 5000,
            hourOfDay = 11,
            minuteOfHour = 0,
            isReturnTrip = true,
            includesGravelRoad = true
        )
        assertEquals(SmartRideGuards.AcceptanceTier.HIGH, forecast.tier)
        assertTrue(forecast.detail.contains("Wait & return"))
        assertTrue(forecast.detail.contains("Gravel allowance"))

        val baseTrip = sampleTrip(fare = 120.0).copy(
            beneficiaryName = "Gogo Mavis",
            beneficiaryPhone = "0821234567"
        )
        val sms = SmartRideGuards.buildSmsTripSummary(baseTrip, pickup)
        assertTrue(sms.contains("VHAYA RURAL TRIP UPDATE"))
        assertTrue(sms.contains("Pickup PIN: 4821"))
        assertTrue(sms.contains("Passenger: Gogo Mavis"))
        assertTrue(sms.contains("GPS: -23.90450,29.46880"))
    }

    @Test
    fun motherTongueStrings_supportAllFourLimpopoLanguages() {
        assertEquals("Where to?", RuralStrings.forLanguage(AppLanguage.ENGLISH).whereToLabel)
        assertEquals("O ya kae?", RuralStrings.forLanguage(AppLanguage.SEPEDI).whereToLabel)
        assertEquals("Ni ya ngafhi?", RuralStrings.forLanguage(AppLanguage.TSHIVENDA).whereToLabel)
        assertEquals("U ya kwihi?", RuralStrings.forLanguage(AppLanguage.XITSONGA).whereToLabel)
    }
}
