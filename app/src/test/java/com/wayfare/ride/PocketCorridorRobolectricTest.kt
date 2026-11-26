package com.wayfare.ride

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PocketItemSize
import com.wayfare.ride.domain.PocketItemStatus
import com.wayfare.ride.domain.PocketItemType
import com.wayfare.ride.domain.RideCoordinates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PocketCorridorRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var store: SessionStore
    private lateinit var repo: WayfareRepository

    private val malamulele = RideCoordinates(-22.9730, 30.6840)
    private val tembisa = RideCoordinates(-25.9960, 28.2250)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = SessionStore(context)
        repo = WayfareRepository(context, db, store, VhayaApiClient())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun sessionStore_loadsDefaultPocketRoutesIncludingMalamuleleToTembisa() = runBlocking {
        val routes = store.loadPocketRoutes()
        assertTrue("Expected default pocket routes to be non-empty", routes.isNotEmpty())
        val malamuleleToTembisa = routes.firstOrNull {
            it.originLabel.contains("Malamulele", ignoreCase = true) &&
                it.destinationLabel.contains("Tembisa", ignoreCase = true)
        }
        assertNotNull("Expected Malamulele to Tembisa route to exist by default", malamuleleToTembisa)
        assertEquals("Samuel Maluleke", malamuleleToTembisa!!.driverName)
        assertEquals(3, malamuleleToTembisa.availableParcelSlots)
        assertEquals(120.0, malamuleleToTembisa.baseParcelPriceZar, 0.01)
    }

    @Test
    fun driver_canCreateNewPocketRoute() = runBlocking {
        val created = repo.createDriverPocketRoute(
            originLabel = "Malamulele Main Plaza",
            originCoordinates = malamulele,
            destinationLabel = "Tembisa Plaza",
            destinationCoordinates = tembisa,
            departureTimeLabel = "Tomorrow 06:00",
            departureTimestampMs = System.currentTimeMillis() + 86400_000L,
            totalSeats = 4,
            seatPriceZar = 260.0,
            totalParcelSlots = 5,
            baseParcelPriceZar = 130.0,
            notes = "Direct N1 corridor with canopy"
        )

        assertNotNull(created.id)
        val routes = repo.pocketRoutes.first()
        assertTrue(routes.any { it.id == created.id })
    }

    @Test
    fun rider_canBookParcelIntoDriverPocket_andDecrementSlots() = runBlocking {
        val routes = repo.pocketRoutes.first()
        val targetRoute = routes.first()
        val initialSlots = targetRoute.availableParcelSlots

        val bookedItem = repo.bookParcelIntoDriverPocket(
            routeId = targetRoute.id,
            itemDescription = "Box of spare mechanical parts",
            size = PocketItemSize.MEDIUM_BAG,
            recipientName = "Khuliso Nemukula",
            recipientPhone = "+27 82 123 4567"
        )

        assertNotNull(bookedItem)
        assertEquals(PocketItemStatus.RESERVED, bookedItem!!.status)
        assertEquals(targetRoute.id, bookedItem.assignedDriverRouteId)
        assertTrue(bookedItem.recipientPin.isNotBlank())
        assertEquals(PocketItemSize.MEDIUM_BAG.baseFeeZar, bookedItem.fareZar, 0.01)

        val updatedRoutes = repo.pocketRoutes.first()
        val updatedRoute = updatedRoutes.first { it.id == targetRoute.id }
        assertEquals(initialSlots - 1, updatedRoute.availableParcelSlots)
    }

    @Test
    fun rider_canPostToPocketBoard_andDriverCanClaimAndDeliver() = runBlocking {
        val postedItem = repo.registerRiderPocketRequest(
            originLabel = "Malamulele Main Plaza & Rank",
            originCoordinates = malamulele,
            destinationLabel = "Tembisa Plaza & Mall",
            destinationCoordinates = tembisa,
            itemDescription = "5kg Bag of Farm Produce",
            size = PocketItemSize.SMALL_BOX,
            offeredFareZar = 90.0,
            recipientName = "Akani Mabasa",
            recipientPhone = "+27 72 999 8811"
        )

        assertEquals(PocketItemStatus.LISTED, postedItem.status)

        // Passing driver claims the item
        val claimed = repo.claimPocketItemForDriver(postedItem.id, "pocket_route_malamulele_tembisa")
        assertNotNull(claimed)
        assertEquals(PocketItemStatus.RESERVED, claimed!!.status)

        // Driver updates status to COLLECTED -> IN_TRANSIT -> DELIVERED
        val collected = repo.updatePocketItemStatus(postedItem.id, PocketItemStatus.COLLECTED)
        assertEquals(PocketItemStatus.COLLECTED, collected!!.status)

        val inTransit = repo.updatePocketItemStatus(postedItem.id, PocketItemStatus.IN_TRANSIT)
        assertEquals(PocketItemStatus.IN_TRANSIT, inTransit!!.status)

        val delivered = repo.updatePocketItemStatus(postedItem.id, PocketItemStatus.DELIVERED)
        assertEquals(PocketItemStatus.DELIVERED, delivered!!.status)
    }

    @Test
    fun launchPolicy_searchesTembisaHubSuccessfully() {
        val results = LaunchPolicy.searchOfflineLandmarks("Tembisa")
        assertTrue("Expected Tembisa hub in offline landmarks", results.isNotEmpty())
        val match = results.first { it.label.contains("Tembisa", ignoreCase = true) }
        assertTrue(match.label.contains("Tembisa", ignoreCase = true))
    }
}
