package com.wayfare.ride

import com.wayfare.ride.domain.DriverDispatchMetrics
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverDispatchMetricsTest {

    private fun sampleOpenTrip(
        pricingMode: PricingMode = PricingMode.OFFER,
        fare: Double = 100.0,
        distanceMeters: Int = 5000,
        durationSeconds: Int = 600,
        offers: List<RideOffer> = emptyList()
    ) = RideTrip(
        id = "trip-d1",
        riderId = "rider-1",
        pickup = "Mall of the North · Bendor",
        destination = "Savannah Mall · Fauna Park",
        category = RideCategory.STANDARD,
        fare = fare,
        pricingMode = pricingMode,
        status = RideStatus.REQUESTED,
        paymentMethod = PaymentMethod.CARD,
        createdAt = System.currentTimeMillis(),
        pickupCoordinates = RideCoordinates(-23.8768, 29.4979),
        destinationCoordinates = RideCoordinates(-23.9038, 29.4857),
        route = RideRoute(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            encodedPolyline = ""
        ),
        offers = offers
    )

    @Test
    fun `computes rate per km etas and quick bid chips for offer trips`() {
        val trip = sampleOpenTrip(pricingMode = PricingMode.OFFER, fare = 100.0, distanceMeters = 5000, durationSeconds = 600)
        val driverCoords = RideCoordinates(-23.8868, 29.4979)

        val metrics = DriverDispatchMetrics.compute(trip, driverCoords, driverUserId = "driver-1")

        assertNotNull(metrics.pickupKm)
        assertTrue((metrics.pickupKm ?: 0.0) > 0.5)
        assertNotNull(metrics.pickupEtaMin)
        assertEquals(5.0, metrics.tripKm ?: 0.0, 0.01)
        assertEquals(10, metrics.tripEtaMin)
        assertEquals(20.0, metrics.ratePerKmZar ?: 0.0, 0.01)
        assertEquals(listOf(110.0, 120.0, 130.0), metrics.quickBidAmountsZar)
        assertNull(metrics.myPendingOffer)
    }

    @Test
    fun `detects drivers existing pending offer and omits quick bids for fixed estimate`() {
        val offer = RideOffer(
            id = "offer-1",
            tripId = "trip-d1",
            driverId = "driver-1",
            amount = 120.0,
            status = "pending",
            createdAt = System.currentTimeMillis(),
            driverProfile = DriverProfileSnapshot(
                id = "driver-1",
                name = "Thabo Mokoena",
                vehicle = "Toyota Corolla Quest",
                vehicleColor = "White",
                registration = "ABC 123 GP"
            )
        )
        val trip = sampleOpenTrip(pricingMode = PricingMode.ESTIMATE, offers = listOf(offer))
        val metrics = DriverDispatchMetrics.compute(trip, RideCoordinates(-23.8768, 29.4979), driverUserId = "driver-1")

        assertTrue(metrics.quickBidAmountsZar.isEmpty())
        assertEquals("offer-1", metrics.myPendingOffer?.id)
        assertEquals(120.0, metrics.myPendingOffer?.amount ?: 0.0, 0.01)
    }
}
