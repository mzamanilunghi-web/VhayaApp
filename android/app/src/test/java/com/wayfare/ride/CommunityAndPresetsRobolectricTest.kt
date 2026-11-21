package com.wayfare.ride

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.RuralRunPreset
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.ui.RiderBookingStep
import com.wayfare.ride.ui.RiderTab
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommunityAndPresetsRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildRepository(): WayfareRepository {
        val store = SessionStore(context, apiBaseUrlOverride = "")
        store.saveSession(
            UserSession(
                userId = "rider-community-1",
                displayName = "Tshepo Baloyi",
                email = "tshepo@vhaya.co.za",
                role = RideRole.RIDER,
                emailVerified = true,
                idToken = "test-token",
                refreshToken = "refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        return WayfareRepository(context, db, store, VhayaApiClient())
    }

    private fun awaitUiState(vm: WayfareViewModel, predicate: (WayfareUiState) -> Boolean): WayfareUiState {
        var attempts = 0
        while (!predicate(vm.uiState.value) && attempts < 100) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            attempts++
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return vm.uiState.value
    }

    private fun sampleDriver(id: String = "drv-trusted-1", name: String = "Ntate Kgosi") = DriverProfileSnapshot(
        id = id,
        name = name,
        rating = 4.9,
        completedRides = 142,
        vehicle = "Toyota Hilux",
        vehicleColor = "White",
        registration = "CZ 45 GP"
    )

    @Test
    fun fareSplit_calculatesExactAndRoundedShareAndShareText() {
        val split3 = SmartRideGuards.calculateFareSplit(totalFareZar = 100.0, riderCount = 3)
        assertEquals(3, split3.riderCount)
        assertEquals(33.33, split3.exactPerPersonZar, 0.01)
        assertEquals(34, split3.roundedCashPerPersonZar)

        val splitClamped = SmartRideGuards.calculateFareSplit(totalFareZar = 80.0, riderCount = 1)
        assertEquals(2, splitClamped.riderCount)
        assertEquals(40.0, splitClamped.exactPerPersonZar, 0.01)

        val msg = SmartRideGuards.buildFareSplitShareText(
            pickup = "Ga-Mothapo Store",
            destination = "Mankweng Hospital",
            totalFareZar = 120.0,
            riderCount = 3,
            driverLabel = "Ntate Kgosi · White Hilux (CZ 45 GP)"
        )
        assertTrue(msg.contains("VHAYA LIFT SHARE SPLIT"))
        assertTrue(msg.contains("Ga-Mothapo Store"))
        assertTrue(msg.contains("Mankweng Hospital"))
        assertTrue(msg.contains("3 riders"))
        assertTrue(msg.contains("R 40"))
        assertTrue(msg.contains("Ntate Kgosi"))
    }

    @Test
    fun offerScore_boostsTrustedCommunityDriversToTop() {
        val trustedOffer = RideOffer(
            id = "offer_trusted",
            driverId = "drv_trusted",
            driverProfile = sampleDriver(id = "drv_trusted", name = "Ntate Kgosi"),
            amount = 95.0,
            status = "pending",
            createdAt = 1_000L
        )
        val untrustedCheaperOffer = RideOffer(
            id = "offer_other",
            driverId = "drv_other",
            driverProfile = sampleDriver(id = "drv_other", name = "Other Driver"),
            amount = 85.0,
            status = "pending",
            createdAt = 1_000L
        )

        val trustedSet = setOf("drv_trusted")
        val trustedScore = LaunchPolicy.offerScore(trustedOffer, trustedSet)
        val otherScore = LaunchPolicy.offerScore(untrustedCheaperOffer, trustedSet)
        assertTrue(
            "Trusted community driver ($trustedScore) should outrank slightly cheaper untrusted offer ($otherScore)",
            trustedScore > otherScore
        )
    }

    @Test
    fun trustedDriver_persistsInRepositoryAndClearsOnSignOut() = runBlocking {
        val repo = buildRepository()
        val driver = sampleDriver()

        assertFalse(repo.isDriverTrusted(driver.id))
        assertTrue(repo.toggleTrustedDriver(driver))
        assertTrue(repo.isDriverTrusted(driver.id))
        assertEquals(1, repo.trustedDrivers.value.size)

        // Verify persistence across repository instances using SessionStore
        val repoReloaded = WayfareRepository(context, db, SessionStore(context, ""), VhayaApiClient())
        assertTrue(repoReloaded.isDriverTrusted(driver.id))
        assertEquals("Ntate Kgosi", repoReloaded.trustedDrivers.value.first().name)

        // Toggle off removes driver
        assertFalse(repoReloaded.toggleTrustedDriver(driver))
        assertFalse(repoReloaded.isDriverTrusted(driver.id))

        // Add again and verify signOut clears local driver circle
        assertTrue(repoReloaded.toggleTrustedDriver(driver))
        repoReloaded.signOut()
        assertTrue(repoReloaded.trustedDrivers.value.isEmpty())
    }

    @Test
    fun ruralPresets_andOneTapRebook_updateViewModelBookingDraftAccurately() {
        val repo = buildRepository()
        val vmStore = ViewModelStore()
        val vm = ViewModelProvider(vmStore, WayfareViewModel.Factory(repo))[WayfareViewModel::class.java]
        try {
            vm.applyRuralPreset(RuralRunPreset.CLINIC_RUN)
            val clinicDraft = awaitUiState(vm) { it.booking.activePreset == RuralRunPreset.CLINIC_RUN }.booking
            assertEquals(RiderBookingStep.SEARCH_DESTINATION, clinicDraft.step)
            assertEquals(RuralRunPreset.CLINIC_RUN, clinicDraft.activePreset)
            assertEquals(RideCategory.STANDARD, clinicDraft.category)
            assertTrue(clinicDraft.isReturnTrip)
            assertTrue(clinicDraft.includesGravelRoad)
            assertEquals("Clinic visit — wait at gate", clinicDraft.pickupNote)

            vm.clearRuralPreset()
            val clearedState = awaitUiState(vm) { it.booking.activePreset == null }
            assertNull(clearedState.booking.activePreset)

            vm.applyRuralPreset(RuralRunPreset.TOWN_GROCERY)
            val groceryDraft = awaitUiState(vm) { it.booking.activePreset == RuralRunPreset.TOWN_GROCERY }.booking
            assertEquals(RideCategory.BAKKIE, groceryDraft.category)
            assertTrue(groceryDraft.includesGravelRoad)
            assertFalse(groceryDraft.isReturnTrip)

            vm.applyRuralPreset(RuralRunPreset.FAMILY_PARCEL)
            val parcelDraft = awaitUiState(vm) { it.booking.activePreset == RuralRunPreset.FAMILY_PARCEL }.booking
            assertEquals(RideCategory.PARCEL, parcelDraft.category)
            assertTrue(parcelDraft.beneficiaryEnabled)

            val pastTrip = RideTrip(
                id = "trip_hist_1",
                riderId = "rider-community-1",
                driverId = "drv_trusted",
                driverProfile = sampleDriver(),
                pickup = "Ga-Mothapo Village",
                destination = "Mall of the North",
                category = RideCategory.BAKKIE,
                fare = 160.0,
                pricingMode = PricingMode.OFFER,
                status = RideStatus.COMPLETED,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.CASH,
                createdAt = 1_000_000L,
                pickupCoordinates = RideCoordinates(-23.95, 29.62),
                destinationCoordinates = RideCoordinates(-23.876, 29.498),
                pickupNote = "Call near clinic gate",
                isSharedRide = true,
                seatCount = 2,
                isReturnTrip = true,
                includesGravelRoad = true
            )

            vm.selectRiderTab(RiderTab.TRIPS)
            awaitUiState(vm) { it.riderTab == RiderTab.TRIPS }
            vm.rebookTrip(pastTrip)
            val rebookState = awaitUiState(vm) { it.booking.step == RiderBookingStep.CHOOSE_RIDE_FARE }
            assertEquals(RiderTab.RIDE, rebookState.riderTab)
            assertEquals(RiderBookingStep.CHOOSE_RIDE_FARE, rebookState.booking.step)
            assertEquals("Mall of the North", rebookState.booking.destinationLabel)
            assertEquals(RideCategory.BAKKIE, rebookState.booking.category)
            assertTrue(rebookState.booking.isSharedRide)
            assertEquals(2, rebookState.booking.seatCount)
            assertTrue(rebookState.booking.isReturnTrip)
            assertTrue(rebookState.booking.includesGravelRoad)
        } finally {
            vmStore.clear()
        }
    }
}
