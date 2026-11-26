package com.wayfare.ride

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.ReturnMatchOriginType
import com.wayfare.ride.domain.ReturnRideEngine
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.notifications.RideNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReturnRideEngineTest {

    // Corridor anchors around Limpopo (Polokwane town <-> Mankweng / Ga-Maja rural corridor)
    private val polokwaneTown = RideCoordinates(-23.9045, 29.4688)
    private val turfloopMidCorridor = RideCoordinates(-23.8960, 29.6100)
    private val mankwengVillage = RideCoordinates(-23.8891, 29.7340)
    private val nearbyVillage2Km = RideCoordinates(-23.8750, 29.7420)
    private val distantTzaneen45Km = RideCoordinates(-23.8332, 30.1635)

    private fun sampleOutboundTrip(
        status: RideStatus = RideStatus.IN_PROGRESS
    ): RideTrip = RideTrip(
        id = "outbound-1",
        riderId = "rider-out",
        driverId = "driver-1",
        pickup = "Polokwane Mall of the North",
        destination = "Mankweng Hospital",
        category = RideCategory.STANDARD,
        fare = 240.0,
        pricingMode = PricingMode.OFFER,
        status = status,
        paymentMethod = PaymentMethod.CASH,
        createdAt = System.currentTimeMillis() - 300_000L,
        pickupCoordinates = polokwaneTown,
        destinationCoordinates = mankwengVillage,
        route = RideRoute(
            distanceMeters = 27_000,
            durationSeconds = 1900,
            originCoordinates = polokwaneTown,
            destinationCoordinates = mankwengVillage
        ),
        liveDriverLocation = RideCoordinates(-23.8930, 29.6800)
    )

    private fun sampleCandidate(
        id: String,
        pickup: RideCoordinates,
        destination: RideCoordinates,
        fare: Double = 220.0,
        distanceMeters: Int = 18_000,
        isReturnRequest: Boolean = false,
        status: RideStatus = RideStatus.REQUESTED
    ): RideTrip = RideTrip(
        id = id,
        riderId = "rider-$id",
        driverId = null,
        pickup = "Pickup $id",
        destination = "Destination $id",
        category = RideCategory.STANDARD,
        fare = fare,
        pricingMode = PricingMode.OFFER,
        status = status,
        paymentMethod = PaymentMethod.CASH,
        createdAt = System.currentTimeMillis() - 30_000L,
        pickupCoordinates = pickup,
        destinationCoordinates = destination,
        route = RideRoute(
            distanceMeters = distanceMeters,
            durationSeconds = 1200,
            originCoordinates = pickup,
            destinationCoordinates = destination
        ),
        isReturnRequest = isReturnRequest
    )

    @Test
    fun `matches candidate within 8 km of dropoff heading back toward origin`() {
        val outbound = sampleOutboundTrip(RideStatus.IN_PROGRESS)
        val nearVillageRider = sampleCandidate(
            id = "ret-near",
            pickup = nearbyVillage2Km,
            destination = polokwaneTown,
            fare = 240.0,
            distanceMeters = 27_000,
            isReturnRequest = true
        )

        val match = ReturnRideEngine.evaluateReturnRideMatch(
            candidate = nearVillageRider,
            outboundTrip = outbound,
            driverLocation = outbound.liveDriverLocation
        )

        assertNotNull(match)
        assertEquals(ReturnMatchOriginType.NEAR_DROPOFF, match!!.originType)
        assertTrue(match.distanceFromDropOffKm <= ReturnRideEngine.RETURN_MATCH_RADIUS_KM)
        assertTrue(match.returnProgressKm > 15.0)
        assertEquals(240.0, match.suggestedBackhaulFareZar, 0.01)
    }

    @Test
    fun `matches candidate along return corridor within 8 km perpendicular offset`() {
        val outbound = sampleOutboundTrip(RideStatus.IN_PROGRESS)
        // ~12.6 km west of Mankweng (> 8 km from drop-off), but ~0.1 km off return corridor back to town
        val corridorPickup = RideCoordinates(-23.8975, 29.6100)
        val corridorRider = sampleCandidate(
            id = "ret-corridor",
            pickup = corridorPickup,
            destination = polokwaneTown,
            fare = 160.0,
            distanceMeters = 14_000,
            isReturnRequest = false
        )

        val match = ReturnRideEngine.evaluateReturnRideMatch(
            candidate = corridorRider,
            outboundTrip = outbound
        )

        assertNotNull(match)
        assertEquals(ReturnMatchOriginType.ALONG_RETURN_CORRIDOR, match!!.originType)
        assertTrue(match.distanceFromDropOffKm > ReturnRideEngine.RETURN_MATCH_RADIUS_KM)
        assertTrue(match.corridorDeviationKm <= ReturnRideEngine.RETURN_MATCH_RADIUS_KM)
    }

    @Test
    fun `ignores candidate beyond 8 km or heading away from outbound origin`() {
        val outbound = sampleOutboundTrip(RideStatus.IN_PROGRESS)

        val tooFarFromCorridor = sampleCandidate(
            id = "far-out",
            pickup = distantTzaneen45Km,
            destination = polokwaneTown
        )
        assertNull(ReturnRideEngine.evaluateReturnRideMatch(tooFarFromCorridor, outbound))

        // Starts near Mankweng drop-off, but goes east toward Tzaneen (opposite direction of town)
        val wrongDirection = sampleCandidate(
            id = "wrong-dir",
            pickup = mankwengVillage,
            destination = distantTzaneen45Km
        )
        assertNull(ReturnRideEngine.evaluateReturnRideMatch(wrongDirection, outbound))

        // Outbound trip not IN_PROGRESS yet (only ACCEPTED)
        val notStartedOutbound = sampleOutboundTrip(RideStatus.ACCEPTED)
        val validReturn = sampleCandidate("valid", nearbyVillage2Km, polokwaneTown)
        assertNull(ReturnRideEngine.evaluateReturnRideMatch(validReturn, notStartedOutbound))
    }

    @Test
    fun `prioritizes explicit return requests over regular open rides`() {
        val outbound = sampleOutboundTrip(RideStatus.IN_PROGRESS)
        val regularCandidate = sampleCandidate(
            id = "regular",
            pickup = nearbyVillage2Km,
            destination = polokwaneTown,
            isReturnRequest = false
        )
        val returnRequestCandidate = sampleCandidate(
            id = "explicit-return",
            pickup = turfloopMidCorridor,
            destination = polokwaneTown,
            isReturnRequest = true
        )

        val ranked = ReturnRideEngine.findReturnRideMatches(
            trips = listOf(regularCandidate, returnRequestCandidate),
            outboundTrip = outbound
        )

        assertEquals(2, ranked.size)
        assertEquals("explicit-return", ranked.first().trip.id)
    }

    @Test
    fun `calculates discounted backhaul fare with R12 per km and minimum floor`() {
        // 10 km, standard R200 -> 0.82 * 200 = R164 (>= R120 floor)
        val discounted = SmartRideGuards.calculateBackhaulReturnFare(
            standardFareZar = 200.0,
            distanceMeters = 10_000,
            category = RideCategory.STANDARD
        )
        assertEquals(164.0, discounted, 0.01)

        // 15 km at low standard R190 -> 0.82 * 190 = R156 vs 15 * 12 = R180 floor -> R180
        val protectedByKmFloor = SmartRideGuards.calculateBackhaulReturnFare(
            standardFareZar = 190.0,
            distanceMeters = 15_000,
            category = RideCategory.STANDARD
        )
        assertEquals(180.0, protectedByKmFloor, 0.01)

        // Bakkie minimum R30 floor
        val bakkieFloor = SmartRideGuards.calculateBackhaulReturnFare(
            standardFareZar = 32.0,
            distanceMeters = 1_500,
            category = RideCategory.BAKKIE
        )
        assertEquals(30.0, bakkieFloor, 0.01)
    }

    @Test
    fun `return fare comparison calculates savings and wait and return remains un-discounted`() {
        val comparison = SmartRideGuards.calculateReturnFareComparison(
            baseEstimateZar = 200.0,
            distanceMeters = 12_000,
            category = RideCategory.STANDARD,
            isRuralArea = true
        )
        assertEquals(216.0, comparison.standardFareZar, 0.01)
        assertEquals(177.0, comparison.discountedFareZar, 0.01)
        assertEquals(39.0, comparison.savingsZar, 0.01)
        assertTrue(comparison.savingsPercent in 17..19)

        // Wait & return takes precedence if both booleans were ever true
        val waitAndReturn = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = 200.0,
            isReturnTrip = true,
            isReturnRequest = true,
            distanceMeters = 12_000
        )
        assertEquals(370.0, waitAndReturn, 0.01)
    }

    @Test
    fun `finds approaching return driver hint for rider waiting along outbound route`() {
        val outbound = sampleOutboundTrip(RideStatus.IN_PROGRESS)
        val hint = ReturnRideEngine.findApproachingReturnDriverHint(
            trips = listOf(outbound),
            riderPickup = nearbyVillage2Km,
            riderDest = polokwaneTown,
            activeUserId = "rider-waiting"
        )

        assertNotNull(hint)
        assertEquals(outbound.id, hint!!.outboundTripId)
        assertTrue(hint.distanceToRiderPickupKm <= ReturnRideEngine.RETURN_MATCH_RADIUS_KM)
        assertTrue(hint.etaMinutes >= 1)

        // Does not hint if the active user owns the outbound trip
        val selfHint = ReturnRideEngine.findApproachingReturnDriverHint(
            trips = listOf(outbound),
            riderPickup = nearbyVillage2Km,
            riderDest = polokwaneTown,
            activeUserId = "rider-out"
        )
        assertNull(selfHint)
    }

    @Test
    fun `builds return ride notification and offer labels for return requests`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outbound = sampleOutboundTrip(RideStatus.IN_PROGRESS)
        val returnCandidate = sampleCandidate(
            id = "ret-notif",
            pickup = nearbyVillage2Km,
            destination = polokwaneTown,
            fare = 185.0,
            distanceMeters = 14_000,
            isReturnRequest = true
        )
        val match = ReturnRideEngine.evaluateReturnRideMatch(returnCandidate, outbound)!!

        val returnNotif = RideNotificationHelper.buildReturnRideMatchNotification(context, match)
        assertNotNull(returnNotif)
        assertEquals(RideNotificationHelper.CHANNEL_RETURN_RIDES, returnNotif.channelId)

        val offerSpec = RideNotificationHelper.buildDriverOfferSpec(returnCandidate)
        assertTrue(offerSpec.title.startsWith("Return Ride"))
        assertTrue(offerSpec.metaLabel.contains("Return Request"))

        val riderSpec = RideNotificationHelper.buildRiderStatusSpec(
            trip = returnCandidate.copy(driverId = null, status = RideStatus.REQUESTED),
            isFirstNotification = true
        )
        assertTrue(riderSpec.title.contains("Return Request"))
        assertTrue(riderSpec.bigText.contains("Return Request"))
    }
}
