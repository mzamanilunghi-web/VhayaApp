package com.wayfare.ride

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.service.FareEstimateRequest
import com.wayfare.ride.service.FareEstimateSource
import com.wayfare.ride.service.FareEstimationService
import com.wayfare.ride.ui.BookingDraft
import com.wayfare.ride.ui.rider.ChooseRideAndFareSheet
import com.wayfare.ride.ui.theme.WayfareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FareEstimationServiceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `distance and time both contribute proportionally to estimated fare`() {
        val service = FareEstimationService()

        // Same 6 km distance, 10 min vs 30 min ride duration
        val quickTrip = service.estimateFare(
            FareEstimateRequest(distanceMeters = 6_000, durationSeconds = 600)
        )
        val slowTrafficTrip = service.estimateFare(
            FareEstimateRequest(distanceMeters = 6_000, durationSeconds = 1_800)
        )

        assertTrue(slowTrafficTrip.timeFareZar > quickTrip.timeFareZar)
        assertTrue(slowTrafficTrip.estimatedFareZar > quickTrip.estimatedFareZar)
        assertEquals(6.0, quickTrip.distanceKm, 0.01)
        assertEquals(10, quickTrip.durationMinutes)
        assertEquals(30, slowTrafficTrip.durationMinutes)

        // Same 20 min duration, 5 km vs 15 km distance
        val shortDist = service.estimateFare(
            FareEstimateRequest(distanceMeters = 5_000, durationSeconds = 1_200)
        )
        val longDist = service.estimateFare(
            FareEstimateRequest(distanceMeters = 15_000, durationSeconds = 1_200)
        )
        assertTrue(longDist.distanceFareZar > shortDist.distanceFareZar)
        assertTrue(longDist.estimatedFareZar > shortDist.estimatedFareZar)
        assertEquals(FareEstimateSource.DISTANCE_AND_TIME, longDist.source)
    }

    @Test
    fun `category multipliers and minimum fare floors apply cleanly`() {
        val service = FareEstimationService()

        val standard = service.estimateFare(
            FareEstimateRequest(distanceMeters = 10_000, durationSeconds = 900, category = RideCategory.STANDARD)
        )
        val bakkie = service.estimateFare(
            FareEstimateRequest(distanceMeters = 10_000, durationSeconds = 900, category = RideCategory.BAKKIE)
        )
        val parcel = service.estimateFare(
            FareEstimateRequest(distanceMeters = 10_000, durationSeconds = 900, category = RideCategory.PARCEL)
        )

        assertTrue(bakkie.estimatedFareZar > standard.estimatedFareZar)
        assertTrue(parcel.estimatedFareZar < standard.estimatedFareZar)

        // Very short route still respects minimum fare floors (R20 standard, R30 bakkie)
        val tinyStandard = service.estimateFare(
            FareEstimateRequest(distanceMeters = 200, durationSeconds = 60, category = RideCategory.STANDARD)
        )
        val tinyBakkie = service.estimateFare(
            FareEstimateRequest(distanceMeters = 200, durationSeconds = 60, category = RideCategory.BAKKIE)
        )
        assertEquals(20.0, tinyStandard.estimatedFareZar, 0.01)
        assertEquals(30.0, tinyBakkie.estimatedFareZar, 0.01)
    }

    @Test
    fun `server quote anchors final total while breaking down distance and time shares`() {
        val service = FareEstimationService()
        val route = RideRoute(
            distanceMeters = 8_000,
            durationSeconds = 900,
            fareEstimate = 160.0,
            quoteToken = "quote-abc"
        )

        val breakdown = service.estimateForRoute(route = route, category = RideCategory.STANDARD)

        assertEquals(FareEstimateSource.LIVE_ROUTE_QUOTE, breakdown.source)
        assertEquals(160.0, breakdown.estimatedFareZar, 0.01)
        assertEquals(
            breakdown.standardSubtotalZar,
            breakdown.baseFareZar + breakdown.distanceFareZar + breakdown.timeFareZar,
            0.01
        )
        assertTrue(breakdown.distanceFareZar > breakdown.timeFareZar)
        assertTrue(breakdown.timeFareZar > 0.0)
    }

    @Test
    fun `reactive activeEstimate updates and clears on route or option changes`() {
        val service = FareEstimationService()
        assertNull(service.activeEstimate.value)

        val route = RideRoute(
            distanceMeters = 12_000,
            durationSeconds = 1_080,
            provider = "offline-estimate"
        )
        val initial = service.updateActiveEstimate(route = route, category = RideCategory.STANDARD)
        assertNotNull(initial)
        assertEquals(FareEstimateSource.OFFLINE_ESTIMATE, service.activeEstimate.value?.source)

        val withReturnDiscount = service.updateActiveEstimate(
            route = route,
            category = RideCategory.STANDARD,
            isRuralArea = true,
            isReturnRequest = true
        )
        assertNotNull(withReturnDiscount)
        assertTrue(withReturnDiscount!!.modifiers.any { it.label.contains("Rural") })
        assertTrue(withReturnDiscount.modifiers.any { it.label.contains("Return Request") })
        assertTrue(withReturnDiscount.estimatedFareZar < initial!!.estimatedFareZar)

        service.clearActiveEstimate()
        assertNull(service.activeEstimate.value)
    }

    @Test
    fun `ride request UI displays distance and time fare estimate before confirmation`() {
        val pickup = RideCoordinates(-23.8891, 29.7145)
        val dest = RideCoordinates(-23.9045, 29.4688)
        val route = RideRoute(
            distanceMeters = 18_400,
            durationSeconds = 1_320,
            provider = "osrm",
            originCoordinates = pickup,
            destinationCoordinates = dest,
            fareEstimate = 320.0,
            quoteToken = "quote-live-18"
        )
        val draft = BookingDraft(
            pickupLabel = "Mankweng Clinic",
            pickupCoords = pickup,
            destinationLabel = "Polokwane Mall of the North",
            destinationCoords = dest,
            category = RideCategory.STANDARD,
            previewRoute = route,
            paymentMethod = PaymentMethod.CASH
        )
        val expectedBreakdown = draft.fareEstimateBreakdown!!

        composeRule.setContent {
            WayfareTheme {
                ChooseRideAndFareSheet(
                    draft = draft,
                    walletBalanceMinor = 0L,
                    isDestinationSaved = false,
                    onToggleSaveDestination = {},
                    onBack = {},
                    onSelectCategory = {},
                    onToggleOffer = {},
                    onOfferTextChange = {},
                    onAdjustOffer = {},
                    onToggleSchedule = {},
                    onSelectPaymentMethod = {},
                    onRetryRoute = {},
                    onRequestRide = {},
                    fareEstimateBreakdown = expectedBreakdown
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fare_estimate_breakdown_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fare_estimate_distance_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fare_estimate_time_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fare_estimate_total_badge")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(LaunchPolicy.formatZar(expectedBreakdown.estimatedFareZar), substring = true)
        composeRule.onNodeWithText("Distance (18.4 km)").assertIsDisplayed()
        composeRule.onNodeWithText("Ride time (22 min)").assertIsDisplayed()
        composeRule.onNodeWithTag("request_ride_cta").performScrollTo().assertIsDisplayed()
    }
}
