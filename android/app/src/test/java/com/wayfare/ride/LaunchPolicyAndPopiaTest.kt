package com.wayfare.ride

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.domain.DriverBankDetails
import com.wayfare.ride.domain.DriverIssueCategory
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.DriverRiderRating
import com.wayfare.ride.domain.IssueCategory
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.LocationPermissionState
import com.wayfare.ride.domain.NavigationStep
import com.wayfare.ride.domain.resolveSupportCategoryLabel
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PopiaDataExporter
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.SupportReply
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.ui.components.alertTrustedContacts
import com.wayfare.ride.ui.components.evaluateLocationPermissionState
import com.wayfare.ride.ui.components.findActivity
import com.wayfare.ride.ui.components.resolveBestLastKnownLocation
import com.wayfare.ride.ui.components.resolveDeviceOrFallbackLocation
import org.junit.Assert.assertNull
import org.robolectric.Robolectric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchPolicyAndPopiaTest {

    private fun sampleTrip(
        status: RideStatus = RideStatus.REQUESTED,
        createdAt: Long = 1_000_000L,
        expiresAt: Long? = null,
        driverId: String? = null
    ) = RideTrip(
        id = "trip-101",
        riderId = "rider-1",
        driverId = driverId,
        pickup = "Mall of the North · Bendor",
        destination = "Savannah Mall · Fauna Park",
        category = RideCategory.STANDARD,
        fare = 80.0,
        pricingMode = PricingMode.ESTIMATE,
        status = status,
        paymentMethod = PaymentMethod.CARD,
        createdAt = createdAt,
        expiresAt = expiresAt,
        pickupCoordinates = RideCoordinates(-23.8768, 29.4979),
        destinationCoordinates = RideCoordinates(-23.9038, 29.4857),
        tripPin = "482910",
        driverProfile = DriverProfileSnapshot(
            id = "driver-1",
            name = "Thabo Mokoena",
            vehicle = "Toyota Corolla Quest",
            vehicleColor = "White",
            registration = "ABC 123 GP"
        )
    )

    @Test
    fun `launch policy fare calculation arrival radius ttl and registration formatting`() {
        assertEquals(20.0, LaunchPolicy.calculateEstimate(500, RideCategory.STANDARD), 0.01)
        assertEquals(100.0, LaunchPolicy.calculateEstimate(5_000, RideCategory.COMFORT), 0.01)
        assertEquals("ABC 123 GP", LaunchPolicy.formatSouthAfricanRegistration("abc123gp"))

        val openTrip = sampleTrip(status = RideStatus.REQUESTED, createdAt = 10_000L)
        assertFalse(LaunchPolicy.isTripExpired(openTrip, nowMs = 10_000L + 60_000L))
        assertTrue(LaunchPolicy.isTripExpired(openTrip, nowMs = 10_000L + LaunchPolicy.OPEN_REQUEST_TTL_MS))

        val assignedTrip = sampleTrip(status = RideStatus.ACCEPTED, createdAt = 10_000L, driverId = "driver-1")
        assertFalse(LaunchPolicy.isTripExpired(assignedTrip, nowMs = 10_000L + LaunchPolicy.OPEN_REQUEST_TTL_MS + 1000L))
        assertTrue(LaunchPolicy.canRiderCancel(RideStatus.REQUESTED))
        assertTrue(LaunchPolicy.canRiderCancel(RideStatus.ARRIVED))
        assertFalse(LaunchPolicy.canRiderCancel(RideStatus.IN_PROGRESS))

        val p1 = RideCoordinates(-23.9045, 29.4689)
        val p2 = RideCoordinates(-23.9052, 29.4689)
        assertTrue(LaunchPolicy.distanceMeters(p1, p2) <= LaunchPolicy.ARRIVAL_RADIUS_METERS)
    }

    @Test
    fun `issue categories cover all five backend support types`() {
        val keys = IssueCategory.entries.map { it.key }.toSet()
        assertEquals(setOf("safety", "payment", "driver", "lost_item", "other"), keys)
        assertEquals(IssueCategory.SAFETY, IssueCategory.fromKey("safety"))
        assertEquals(IssueCategory.LOST_ITEM, IssueCategory.fromKey("LOST_ITEM"))
        assertEquals(IssueCategory.OTHER, IssueCategory.fromKey("unknown_cat"))
    }

    @Test
    fun `popia data exporter serializes complete user bundle and retention schedule`() {
        val session = UserSession(
            userId = "u-1",
            displayName = "Nomsa Dlamini",
            email = "nomsa@example.co.za",
            role = RideRole.RIDER
        )
        val prefs = PrivacyPreferences(
            locationSharingConsent = false,
            tripOnlyPrivacyMode = true,
            supportNotificationsEnabled = true,
            popiaAcceptedAtEpochMs = 1_700_000_000_000L
        )
        val jsonText = PopiaDataExporter.buildExportJson(
            session = session,
            privacyPreferences = prefs,
            trips = listOf(sampleTrip(status = RideStatus.COMPLETED)),
            savedDestinations = listOf(SavedDestination("s1", "Home", -23.9, 29.47, 123L)),
            trustedContacts = listOf(TrustedContact("tc1", "Lerato", "0821234567")),
            walletTransactions = listOf(WalletTransaction("w1", "topup", 15_000L, "ZAR", "ref-1", "Top-up", 123L)),
            supportCases = listOf(
                SupportCase(
                    id = 42L,
                    rideId = "trip-101",
                    category = "lost_item",
                    priority = "normal",
                    status = "open",
                    message = "Left keys",
                    createdAt = 123L,
                    updatedAt = 123L
                )
            ),
            nowEpochMs = 1_700_000_000_000L
        )
        val root = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals("vhaya-popia-export-v1", root["schemaVersion"]?.jsonPrimitive?.content)
        assertEquals(false, root["privacyPreferences"]?.jsonObject?.get("locationSharingConsent")?.jsonPrimitive?.content?.toBoolean())
        assertEquals(1, root["trips"]?.jsonArray?.size)
        assertEquals(1, root["savedDestinations"]?.jsonArray?.size)
        assertEquals(1, root["trustedContacts"]?.jsonArray?.size)
        assertEquals(1, root["walletTransactions"]?.jsonArray?.size)
        assertEquals(1, root["supportCases"]?.jsonArray?.size)
        assertTrue(PopiaDataExporter.RETENTION_SCHEDULE.size >= 4)
    }

    @Test
    fun `support notification diffing detects staff replies status transitions and staff notes`() {
        val baseCase = SupportCase(
            id = 77L,
            rideId = "trip-101",
            category = "payment",
            priority = "high",
            status = "open",
            message = "Fare review",
            createdAt = 100L,
            updatedAt = 100L,
            replies = emptyList()
        )
        assertTrue(SupportNotificationHelper.diffSupportUpdates(emptyList(), listOf(baseCase)).isEmpty())

        val withRiderReply = baseCase.copy(
            replies = listOf(SupportReply("r1", 77L, "Requester", "Checking in", 200L))
        )
        assertTrue(SupportNotificationHelper.diffSupportUpdates(listOf(baseCase), listOf(withRiderReply)).isEmpty())

        val withStaffReply = withRiderReply.copy(
            status = "in_progress",
            replies = withRiderReply.replies + SupportReply("r2", 77L, "Vhaya Support", "Refund approved", 300L)
        )
        val replyAlerts = SupportNotificationHelper.diffSupportUpdates(listOf(withRiderReply), listOf(withStaffReply))
        assertEquals(1, replyAlerts.size)
        assertTrue(replyAlerts.first().isStaffReply)
        assertEquals("Refund approved", replyAlerts.first().summary)

        val resolvedCase = withStaffReply.copy(status = "resolved")
        val statusAlerts = SupportNotificationHelper.diffSupportUpdates(listOf(withStaffReply), listOf(resolvedCase))
        assertEquals(1, statusAlerts.size)
        assertFalse(statusAlerts.first().isStaffReply)
        assertTrue(statusAlerts.first().title.contains("resolved"))

        val noteUpdatedCase = resolvedCase.copy(staffNote = "Payout batch #9 settled")
        val noteAlerts = SupportNotificationHelper.diffSupportUpdates(listOf(resolvedCase), listOf(noteUpdatedCase))
        assertEquals(1, noteAlerts.size)
        assertEquals("Payout batch #9 settled", noteAlerts.first().summary)
    }

    @Test
    fun `location permission evaluator handles all four Android states`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)

        shadowApp.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(
            LocationPermissionState.DENIED_CAN_RETRY,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = false)
        )
        assertEquals(
            LocationPermissionState.DENIED_CAN_RETRY,
            evaluateLocationPermissionState(app, shouldShowRationale = true, hasRequestedBefore = true)
        )
        assertEquals(
            LocationPermissionState.DENIED_PERMANENTLY,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = true)
        )

        shadowApp.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(
            LocationPermissionState.GRANTED_COARSE,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = true)
        )

        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(
            LocationPermissionState.GRANTED_FINE,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = true)
        )
    }

    @Test
    fun `alert trusted contacts dispatches sms or share intent with safety summary`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        val trip = sampleTrip(status = RideStatus.IN_PROGRESS, driverId = "driver-1")

        assertFalse(alertTrustedContacts(app, trip, emptyList()))

        val contacts = listOf(TrustedContact("tc1", "Lerato", "+27821234567"))
        val started = alertTrustedContacts(app, trip, contacts)
        assertTrue(started)

        val launchedIntent: Intent? = shadowApp.nextStartedActivity
        assertNotNull(launchedIntent)
        val nested = launchedIntent?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(it, Intent.EXTRA_INTENT, Intent::class.java)
        }
        val body = launchedIntent?.getStringExtra("sms_body")
            ?: nested?.getStringExtra(Intent.EXTRA_TEXT)
            ?: launchedIntent?.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(body)
        assertTrue(body!!.contains("VHAYA SAFETY ALERT"))
        assertTrue(body.contains("ABC 123 GP"))
    }

    @Test
    fun `driver issue categories support labels and sa bank details masking work properly`() {
        val driverKeys = DriverIssueCategory.entries.map { it.key }.toSet()
        assertEquals(setOf("safety", "rider", "fare_review", "lost_item", "other"), driverKeys)
        assertEquals(DriverIssueCategory.FARE_REVIEW, DriverIssueCategory.fromKey("fare_review"))
        assertEquals(DriverIssueCategory.OTHER, DriverIssueCategory.fromKey("invalid_key"))

        assertEquals("Safety", resolveSupportCategoryLabel("safety"))
        assertEquals("Rider", resolveSupportCategoryLabel("rider"))
        assertEquals("Fare & payout", resolveSupportCategoryLabel("fare_review"))
        assertEquals("Payment", resolveSupportCategoryLabel("payment"))
        assertEquals("Other", resolveSupportCategoryLabel("custom_issue"))

        val bank = DriverBankDetails(
            bankName = "FNB",
            accountHolder = "Thabo Mokoena",
            accountNumber = "62012345678",
            branchCode = "250655",
            accountType = "Cheque"
        )
        assertEquals("•••• 5678", bank.maskedAccountNumber)
        assertTrue(DriverBankDetails.SOUTH_AFRICAN_BANKS.any { it.first == "Capitec" && it.second == "470010" })
        assertTrue(DriverBankDetails.SOUTH_AFRICAN_BANKS.any { it.first == "FNB" && it.second == "250655" })

        val rating = DriverRiderRating(tripId = "trip-1", stars = 5, tags = listOf("On time"))
        assertEquals(5, rating.stars)
        assertEquals(listOf("On time"), rating.tags)
    }

    @Test
    fun `driver approach progress scales smoothly toward pickup and snaps at arrival`() {
        val pickup = RideCoordinates(-23.9045, 29.4689)
        // Missing live coords returns initial dispatched head-start
        val awaitingGps = LaunchPolicy.calculateDriverApproachProgress(
            status = RideStatus.ACCEPTED,
            currentDriverLocation = null,
            pickupLocation = pickup,
            baselineDistanceMeters = null
        )
        assertEquals(0.08f, awaitingGps.progress, 0.001f)
        assertFalse(awaitingGps.isArrivedOrWithinRadius)

        // Start ~3 km south of pickup, then close in ~halfway, then enter 150m arrival radius
        val startCoords = RideCoordinates(-23.9315, 29.4689)
        val initialDistance = LaunchPolicy.distanceMeters(startCoords, pickup)
        val startSnap = LaunchPolicy.calculateDriverApproachProgress(
            status = RideStatus.ACCEPTED,
            currentDriverLocation = startCoords,
            pickupLocation = pickup,
            baselineDistanceMeters = initialDistance
        )
        assertEquals(0.08f, startSnap.progress, 0.01f)
        assertFalse(startSnap.isArrivedOrWithinRadius)

        val halfwayCoords = RideCoordinates(-23.9180, 29.4689)
        val halfwaySnap = LaunchPolicy.calculateDriverApproachProgress(
            status = RideStatus.ACCEPTED,
            currentDriverLocation = halfwayCoords,
            pickupLocation = pickup,
            baselineDistanceMeters = initialDistance
        )
        assertTrue(halfwaySnap.progress > startSnap.progress)
        assertTrue(halfwaySnap.progress in 0.40f..0.65f)
        assertFalse(halfwaySnap.isArrivedOrWithinRadius)

        val nearCoords = RideCoordinates(-23.9052, 29.4689)
        val nearSnap = LaunchPolicy.calculateDriverApproachProgress(
            status = RideStatus.ACCEPTED,
            currentDriverLocation = nearCoords,
            pickupLocation = pickup,
            baselineDistanceMeters = initialDistance
        )
        assertTrue(nearSnap.isArrivedOrWithinRadius)
        assertEquals(0.96f, nearSnap.progress, 0.01f)

        val arrivedSnap = LaunchPolicy.calculateDriverApproachProgress(
            status = RideStatus.ARRIVED,
            currentDriverLocation = nearCoords,
            pickupLocation = pickup,
            baselineDistanceMeters = initialDistance
        )
        assertEquals(1.0f, arrivedSnap.progress, 0.001f)
        assertTrue(arrivedSnap.isArrivedOrWithinRadius)
        assertEquals(0, arrivedSnap.etaMinutes)

        assertEquals("450 m", LaunchPolicy.formatApproachDistance(450.0))
        assertEquals("2.4 km", LaunchPolicy.formatApproachDistance(2400.0))
    }

    @Test
    fun `osrm steps json parses into turn-by-turn maneuvers and tracks driver step progress`() {
        val origin = RideCoordinates(-23.9045, 29.4689)
        val dest = RideCoordinates(-23.8768, 29.4979)
        val rawOsrm = """
            {
              "code": "Ok",
              "routes": [
                {
                  "geometry": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                  "distance": 1850.4,
                  "duration": 240.0,
                  "legs": [
                    {
                      "steps": [
                        {
                          "name": "Hans Van Rensburg St",
                          "distance": 800.0,
                          "duration": 95.0,
                          "maneuver": { "type": "depart", "modifier": "straight", "location": [29.4689, -23.9045] }
                        },
                        {
                          "name": "Grobler St",
                          "distance": 650.0,
                          "duration": 85.0,
                          "maneuver": { "type": "turn", "modifier": "right", "location": [29.4750, -23.9000] }
                        },
                        {
                          "name": "R71",
                          "distance": 400.4,
                          "duration": 60.0,
                          "maneuver": { "type": "roundabout", "exit": 2, "location": [29.4850, -23.8900] }
                        },
                        {
                          "name": "Mall of the North",
                          "distance": 0.0,
                          "duration": 0.0,
                          "maneuver": { "type": "arrive", "location": [29.4979, -23.8768] }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val route = VhayaApiClient().parseOsrmRouteJson(rawOsrm, origin, dest)
        assertNotNull(route)
        assertEquals(4, route!!.steps.size)
        assertEquals("Head on Hans Van Rensburg St", route.steps[0].instruction)
        assertEquals("Turn right onto Grobler St", route.steps[1].instruction)
        assertEquals("At roundabout, take exit 2 onto R71", route.steps[2].instruction)
        assertEquals("Arrive at destination", route.steps[3].instruction)

        // Driver midway on first segment should see Turn right onto Grobler St as upcoming maneuver
        val midFirstSeg = RideCoordinates(-23.9022, 29.4720)
        val firstLegProgress = LaunchPolicy.computeActiveNavigationProgress(midFirstSeg, route, dest)
        assertNotNull(firstLegProgress)
        assertEquals(1, firstLegProgress!!.upcomingStepIndex)
        assertEquals("Turn right onto Grobler St", firstLegProgress.upcomingStep.instruction)
        assertEquals("At roundabout, take exit 2 onto R71", firstLegProgress.nextAfterStep?.instruction)
        assertTrue(firstLegProgress.distanceToManeuverMeters > 200.0)

        // Driver after Grobler St turn along segment 1 -> 2 should advance to Roundabout exit 2
        val onSecondSeg = RideCoordinates(-23.8950, 29.4800)
        val secondLegProgress = LaunchPolicy.computeActiveNavigationProgress(onSecondSeg, route, dest)
        assertNotNull(secondLegProgress)
        assertEquals(2, secondLegProgress!!.upcomingStepIndex)
        assertEquals("At roundabout, take exit 2 onto R71", secondLegProgress.upcomingStep.instruction)
    }

    @Test
    fun `offer price comparison highlights matches counters discounts and driver ETA`() {
        val pickup = RideCoordinates(-23.9045, 29.4689)
        val trip = RideTrip(
            id = "trip-offer-cmp",
            riderId = "rider-1",
            pickup = "Polokwane CBD",
            destination = "Mall of the North",
            category = RideCategory.STANDARD,
            fare = 100.0,
            pricingMode = PricingMode.OFFER,
            status = RideStatus.REQUESTED,
            createdAt = 1_700_000_000_000L,
            pickupCoordinates = pickup,
            destinationCoordinates = RideCoordinates(-23.8768, 29.4979),
            route = RideRoute(distanceMeters = 5000, durationSeconds = 600, encodedPolyline = "abc")
        )

        val matchingOffer = RideOffer(
            id = "offer-match",
            tripId = trip.id,
            driverId = "driver-1",
            amount = 100.0,
            driverProfile = DriverProfileSnapshot(
                id = "driver-1",
                name = "Thabo",
                lastLocation = RideCoordinates(-23.9135, 29.4689)
            )
        )
        val matchSummary = LaunchPolicy.summarizeOfferComparison(matchingOffer, trip)
        assertTrue(matchSummary.isMatchesRiderOffer)
        assertFalse(matchSummary.isCounterHigher)
        assertEquals("Matches your price", matchSummary.badgeText)
        assertNotNull(matchSummary.driverEtaMinutes)
        assertTrue((matchSummary.driverDistanceKm ?: 0.0) > 0.5)

        val counterOffer = matchingOffer.copy(id = "offer-counter", amount = 120.0)
        val counterSummary = LaunchPolicy.summarizeOfferComparison(counterOffer, trip)
        assertTrue(counterSummary.isCounterHigher)
        assertFalse(counterSummary.isMatchesRiderOffer)
        assertTrue(counterSummary.badgeText.contains("+20%"))

        val lowerOffer = matchingOffer.copy(id = "offer-lower", amount = 85.0)
        val lowerSummary = LaunchPolicy.summarizeOfferComparison(lowerOffer, trip)
        assertTrue(lowerSummary.isLower)
        assertTrue(lowerSummary.badgeText.contains("-15%"))
    }

    @Test
    fun `password policy requires 8 chars upper lower number and special symbol`() {
        val emptyEval = LaunchPolicy.evaluatePasswordPolicy("")
        assertFalse(emptyEval.isValid)
        assertEquals(LaunchPolicy.PasswordStrength.EMPTY, emptyEval.strength)
        assertEquals(0, emptyEval.satisfiedCount)

        val shortEval = LaunchPolicy.evaluatePasswordPolicy("Ab1!xyz")
        assertFalse(shortEval.isValid)
        assertFalse(shortEval.hasMinLength)
        assertTrue(shortEval.hasUppercase)
        assertTrue(shortEval.hasLowercase)
        assertTrue(shortEval.hasDigit)
        assertTrue(shortEval.hasSpecialChar)
        assertEquals(LaunchPolicy.PasswordStrength.WEAK, shortEval.strength)
        assertTrue(shortEval.validationErrorOrNull()?.contains("8+ characters") == true)

        val missingSymbol = LaunchPolicy.evaluatePasswordPolicy("Vhaya2026")
        assertFalse(missingSymbol.isValid)
        assertEquals(LaunchPolicy.PasswordStrength.GOOD, missingSymbol.strength)
        assertTrue(missingSymbol.validationErrorOrNull()?.contains("special character") == true)

        val surroundingSpaces = LaunchPolicy.evaluatePasswordPolicy(" Vhaya2026! ")
        assertFalse(surroundingSpaces.isValid)
        assertFalse(surroundingSpaces.hasNoSurroundingSpaces)
        assertTrue(surroundingSpaces.validationErrorOrNull()?.contains("spaces") == true)

        val strongEval = LaunchPolicy.evaluatePasswordPolicy("Vhaya2026!")
        assertTrue(strongEval.isValid)
        assertEquals(LaunchPolicy.PasswordStrength.STRONG, strongEval.strength)
        assertEquals(5, strongEval.satisfiedCount)
        assertEquals(null, strongEval.validationErrorOrNull())
    }

    @Test
    fun `device location resolver accepts real GPS fixes ignores null island and unwraps activity`() {
        assertFalse(LaunchPolicy.isValidDeviceCoordinate(RideCoordinates(0.0, 0.0)))
        assertFalse(LaunchPolicy.isValidDeviceCoordinate(RideCoordinates(Double.NaN, 29.4)))
        assertFalse(LaunchPolicy.isValidDeviceCoordinate(RideCoordinates(95.0, 29.4)))
        assertTrue(LaunchPolicy.isValidDeviceCoordinate(RideCoordinates(-26.2041, 28.0473)))
        assertTrue(LaunchPolicy.isValidDeviceCoordinate(RideCoordinates(37.4220, -122.0841)))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = Shadows.shadowOf(locationManager)

        val nullIsland = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 0.0
            longitude = 0.0
            time = 1_000L
        }
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, nullIsland)
        assertNull(resolveBestLastKnownLocation(app))
        assertEquals(LaunchPolicy.DEFAULT_MAP_CENTER, resolveDeviceOrFallbackLocation(app))

        val johannesburgFix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = -26.2041
            longitude = 28.0473
            time = 2_000L
        }
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, johannesburgFix)
        val resolved = resolveBestLastKnownLocation(app)
        assertNotNull(resolved)
        assertEquals(-26.2041, resolved!!.latitude, 0.0001)
        assertEquals(28.0473, resolved.longitude, 0.0001)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped = ContextWrapper(ContextWrapper(activity))
        assertEquals(activity, wrapped.findActivity())
    }

    @Test
    fun `play store legal urls and https api guard behave safely across debug and release`() {
        assertFalse(LaunchPolicy.isProductionSafeApiUrl("", isDebugBuild = true))
        assertFalse(LaunchPolicy.isProductionSafeApiUrl("http://vhaya.co.za", isDebugBuild = false))
        assertFalse(LaunchPolicy.isProductionSafeApiUrl("http://10.0.2.2:3000", isDebugBuild = false))
        assertTrue(LaunchPolicy.isProductionSafeApiUrl("http://10.0.2.2:3000", isDebugBuild = true))
        assertTrue(LaunchPolicy.isProductionSafeApiUrl("https://api.vhaya.co.za/api/", isDebugBuild = false))

        assertNull(LaunchPolicy.resolvePrivacyPolicyUrl("http://api.vhaya.co.za", isDebugBuild = false))
        assertEquals(
            "https://api.vhaya.co.za/privacy",
            LaunchPolicy.resolvePrivacyPolicyUrl("https://api.vhaya.co.za/api/", isDebugBuild = false)
        )
        assertEquals(
            "https://api.vhaya.co.za/delete-account",
            LaunchPolicy.resolveAccountDeletionUrl("https://api.vhaya.co.za/", isDebugBuild = false)
        )
    }

    @Test
    fun `en route cancellation fine is R10 after driver accepts or arrives and R0 before matching`() {
        assertEquals(0.0, LaunchPolicy.riderCancellationFee(sampleTrip(status = RideStatus.REQUESTED)), 0.001)
        assertEquals(0.0, LaunchPolicy.riderCancellationFee(sampleTrip(status = RideStatus.COUNTERED)), 0.001)
        assertEquals(10.0, LaunchPolicy.riderCancellationFee(sampleTrip(status = RideStatus.ACCEPTED, driverId = "driver-1")), 0.001)
        assertEquals(10.0, LaunchPolicy.riderCancellationFee(sampleTrip(status = RideStatus.ARRIVED, driverId = "driver-1")), 0.001)
        assertEquals(0.0, LaunchPolicy.riderCancellationFee(sampleTrip(status = RideStatus.CANCELLED, driverId = "driver-1")), 0.001)
    }
}
