package com.wayfare.ride

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wayfare.ride.data.OfflineMapTileCache
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.DriverMapEngine
import com.wayfare.ride.domain.HazardType
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NavigationStep
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriverMapFeaturesRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var store: SessionStore
    private lateinit var repo: WayfareRepository

    private val polokwane = RideCoordinates(-23.9045, 29.4689)
    private val mankweng = RideCoordinates(-23.8880, 29.7240)
    private val nobodyMidpoint = RideCoordinates(-23.8965, 29.5960)
    private val seshego = RideCoordinates(-23.8290, 29.3570)
    private val mokopaneSouth = RideCoordinates(-24.1940, 29.0090)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val closedPort = ServerSocket(0).use { it.localPort }
        store = SessionStore(context, apiBaseUrlOverride = "http://127.0.0.1:$closedPort")
        store.saveSession(
            UserSession(
                userId = "driver-rural-1",
                displayName = "Thabo Ndlovu",
                email = "thabo@vhaya.co.za",
                role = RideRole.DRIVER,
                emailVerified = true,
                idToken = "driver-token",
                refreshToken = "refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        repo = WayfareRepository(context, db, store, VhayaApiClient())
        repo.clearOfflineTileCache()
    }

    @After
    fun tearDown() {
        repo.clearOfflineTileCache()
        db.close()
    }

    private fun sampleTrip(
        id: String,
        pickup: RideCoordinates,
        dest: RideCoordinates,
        fare: Double = 120.0,
        category: RideCategory = RideCategory.STANDARD,
        routeDistanceMeters: Int = 10_000
    ): RideTrip = RideTrip(
        id = id,
        riderId = "rider-1",
        pickup = "Pickup $id",
        destination = "Dest $id",
        category = category,
        fare = fare,
        pricingMode = PricingMode.ESTIMATE,
        status = RideStatus.REQUESTED,
        paymentStatus = PaymentStatus.NONE,
        paymentMethod = PaymentMethod.CASH,
        createdAt = System.currentTimeMillis(),
        pickupCoordinates = pickup,
        destinationCoordinates = dest,
        route = LaunchPolicy.buildOfflineEstimatedRoute(pickup, dest).copy(distanceMeters = routeDistanceMeters)
    )

    @Test
    fun `multi-request markers flag high yield and corridor match accurately`() {
        val corridor = DriverCorridorTarget(
            label = "Mankweng",
            destination = mankweng,
            maxDetourKm = 6.0
        )
        val highYieldForward = sampleTrip(
            id = "req-forward",
            pickup = nobodyMidpoint,
            dest = mankweng,
            fare = 300.0,
            category = RideCategory.BAKKIE,
            routeDistanceMeters = 12_000
        )
        val lowYieldOpposite = sampleTrip(
            id = "req-opposite",
            pickup = polokwane,
            dest = seshego,
            fare = 90.0,
            routeDistanceMeters = 12_000
        )

        val markers = DriverMapEngine.buildRequestMarkers(
            trips = listOf(highYieldForward, lowYieldOpposite),
            driverLocation = polokwane,
            minRatePerKmZar = 0.0,
            corridorTarget = corridor
        )

        assertEquals(2, markers.size)
        val fwdMarker = markers.first { it.tripId == "req-forward" }
        assertTrue(fwdMarker.isHighYield)
        assertTrue(fwdMarker.corridorMatched)
        assertEquals("Bakkie R300", fwdMarker.fareLabel)

        val oppMarker = markers.first { it.tripId == "req-opposite" }
        assertFalse(oppMarker.isHighYield)
        assertFalse(oppMarker.corridorMatched)
    }

    @Test
    fun `deadhead vs paid trip breakdown computes exact distance shares`() {
        val trip = sampleTrip(
            id = "req-deadhead",
            pickup = nobodyMidpoint,
            dest = mankweng,
            routeDistanceMeters = 13_000
        )

        val breakdown = DriverMapEngine.computeDeadheadVsPaid(trip, polokwane)
        assertNotNull(breakdown)
        val deadheadKm = breakdown!!.deadheadKm
        val paidKm = breakdown.paidTripKm
        assertTrue(deadheadKm in 10.0..16.0)
        assertEquals(13.0, paidKm, 0.01)
        assertEquals(deadheadKm + paidKm, breakdown.totalKm, 0.05)
        assertTrue(breakdown.paidShareFraction in 0.40f..0.60f)
    }

    @Test
    fun `heading home corridor filter matches forward trips and rejects reverse trips with SessionStore persistence`() {
        val corridor = DriverCorridorTarget(
            label = "Mankweng",
            destination = mankweng,
            maxDetourKm = 6.0
        )
        val forwardTrip = sampleTrip("trip-fwd", pickup = polokwane, dest = mankweng)
        val midwayForwardTrip = sampleTrip("trip-mid", pickup = nobodyMidpoint, dest = mankweng)
        val backwardsTrip = sampleTrip("trip-back", pickup = polokwane, dest = seshego)
        val farDetourTrip = sampleTrip("trip-far", pickup = mokopaneSouth, dest = polokwane)

        assertTrue(DriverMapEngine.matchesCorridorFilter(forwardTrip, polokwane, corridor))
        assertTrue(DriverMapEngine.matchesCorridorFilter(midwayForwardTrip, polokwane, corridor))
        assertFalse(DriverMapEngine.matchesCorridorFilter(backwardsTrip, polokwane, corridor))
        assertFalse(DriverMapEngine.matchesCorridorFilter(farDetourTrip, polokwane, corridor))

        repo.setDriverCorridorTarget(corridor)
        assertEquals(corridor, repo.driverCorridorTarget.value)
        assertEquals(corridor, store.loadDriverCorridorTarget())

        repo.setDriverCorridorTarget(null)
        assertNull(repo.driverCorridorTarget.value)
        assertNull(store.loadDriverCorridorTarget())
    }

    @Test
    fun `drive to demand zone routing falls back offline cleanly and persists voice heading preferences`() = runBlocking {
        val navRoute = repo.fetchDriverNavigationRoute(polokwane, mankweng)
        assertNotNull(navRoute)
        assertEquals("offline-estimate", navRoute!!.provider)
        assertTrue(navRoute.distanceMeters > 20_000)

        repo.setDriverVoiceNavEnabled(false)
        repo.setDriverHeadingUpEnabled(true)
        assertFalse(repo.driverVoiceNavEnabled.value)
        assertTrue(repo.driverHeadingUpEnabled.value)
        assertFalse(store.loadDriverVoiceNavEnabled())
        assertTrue(store.loadDriverHeadingUpEnabled())
    }

    @Test
    fun `spoken turn prompt resolver deduplicates tiers across navigation progress`() {
        val step1 = NavigationStep(
            instruction = "Turn left onto R71",
            distanceMeters = 600,
            durationSeconds = 45,
            location = nobodyMidpoint,
            maneuverType = "turn",
            maneuverModifier = "left"
        )
        val stepArrive = NavigationStep(
            instruction = "Arrive at Mankweng",
            distanceMeters = 50,
            durationSeconds = 5,
            location = mankweng,
            maneuverType = "arrive",
            maneuverModifier = null
        )

        val tooFar = LaunchPolicy.ActiveNavigationProgress(0, step1, stepArrive, 700.0, 2000, 120)
        assertNull(DriverMapEngine.resolveSpokenTurnPrompt("leg1", tooFar, null))

        val approach = LaunchPolicy.ActiveNavigationProgress(0, step1, stepArrive, 260.0, 1500, 90)
        val firstPrompt = DriverMapEngine.resolveSpokenTurnPrompt("leg1", approach, null)
        assertNotNull(firstPrompt)
        assertTrue(firstPrompt!!.first.contains("250 meters"))
        assertEquals("leg1:0:approach", firstPrompt.second)

        // Same tier should not repeat
        assertNull(DriverMapEngine.resolveSpokenTurnPrompt("leg1", approach, firstPrompt.second))

        // Immediate turn tier triggers once
        val immediate = LaunchPolicy.ActiveNavigationProgress(0, step1, stepArrive, 50.0, 1200, 70)
        val nowPrompt = DriverMapEngine.resolveSpokenTurnPrompt("leg1", immediate, firstPrompt.second)
        assertNotNull(nowPrompt)
        assertEquals("Turn left onto R71", nowPrompt!!.first)
        assertEquals("leg1:0:now", nowPrompt.second)

        // Arrival step triggers arrival phrase
        val arrive = LaunchPolicy.ActiveNavigationProgress(1, stepArrive, null, 35.0, 35, 4)
        val arrivePrompt = DriverMapEngine.resolveSpokenTurnPrompt("leg1", arrive, nowPrompt.second)
        assertEquals("You have arrived at your destination.", arrivePrompt?.first)
    }

    @Test
    fun `road hazard pins persist in Room, increment confirmations, filter expired, and clear cleanly`() = runBlocking {
        val pin = repo.reportRoadHazard(
            type = HazardType.FLOODED_DRIFT,
            coords = nobodyMidpoint,
            label = "R71 Nobody Drift",
            note = "Water over bridge",
            reportedBy = "Thabo"
        )

        var active = repo.roadHazards.first()
        assertEquals(1, active.size)
        assertEquals(HazardType.FLOODED_DRIFT, active.first().type)
        assertEquals(1, active.first().confirmations)

        repo.confirmRoadHazard(pin.id)
        active = repo.roadHazards.first()
        assertEquals(2, active.first().confirmations)

        // Verify expired hazards are excluded by active query
        val expiredEntity = db.roadHazardDao().getById(pin.id)!!.copy(
            expiresAtMs = System.currentTimeMillis() - 10_000L
        )
        db.roadHazardDao().upsert(expiredEntity)
        val activeAfterExpiry = db.roadHazardDao().observeActive(System.currentTimeMillis()).first()
        assertTrue(activeAfterExpiry.isEmpty())

        repo.clearRoadHazard(pin.id)
        assertEquals(0, db.roadHazardDao().count())
    }

    @Test
    fun `offline tile cache computes corridor tiles, stores and resolves cached PNGs on disk, and clears cleanly`() {
        val tiles = DriverMapEngine.computeTilesForCorridor(
            origin = polokwane,
            destination = mankweng,
            zooms = 12..14,
            maxTiles = 96
        )
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size <= 96)

        val tile = tiles.first()
        assertNull(OfflineMapTileCache.getCachedTileFile(context, tile.zoom, tile.x, tile.y))

        val sampleBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        OfflineMapTileCache.saveTileBytesForTesting(context, tile.zoom, tile.x, tile.y, sampleBytes)

        val hitFile = OfflineMapTileCache.getCachedTileFile(context, tile.zoom, tile.x, tile.y)
        assertNotNull(hitFile)
        assertTrue(hitFile!!.exists())

        val (countBeforeClear, bytesBeforeClear) = repo.getOfflineTileCacheStats()
        assertEquals(1, countBeforeClear)
        assertEquals(sampleBytes.size.toLong(), bytesBeforeClear)

        repo.clearOfflineTileCache()
        assertNull(OfflineMapTileCache.getCachedTileFile(context, tile.zoom, tile.x, tile.y))
        val (countAfterClear, _) = repo.getOfflineTileCacheStats()
        assertEquals(0, countAfterClear)
    }

    @Test
    fun `gtw evaluateGtwMatch computes added detour and rankTripsForGtw supports strict and boost modes`() {
        val strictGtw = DriverCorridorTarget(
            label = "Mankweng",
            destination = mankweng,
            maxDetourKm = 6.0,
            strictFilter = true
        )
        val boostGtw = strictGtw.copy(strictFilter = false)

        val alignedTrip = sampleTrip("gtw-aligned", pickup = nobodyMidpoint, dest = mankweng, routeDistanceMeters = 13_000)
        val offRouteTrip = sampleTrip("gtw-offroute", pickup = polokwane, dest = seshego, routeDistanceMeters = 14_000)

        val match = DriverMapEngine.evaluateGtwMatch(alignedTrip, polokwane, strictGtw)
        assertNotNull(match)
        assertTrue(match!!.directionalAlignment > 0.9)
        assertTrue(match.addedDetourKm < 3.0)

        assertNull(DriverMapEngine.evaluateGtwMatch(offRouteTrip, polokwane, strictGtw))

        val strictRanked = DriverMapEngine.rankTripsForGtw(
            trips = listOf(offRouteTrip, alignedTrip),
            driverLocation = polokwane,
            corridor = strictGtw
        )
        assertEquals(listOf("gtw-aligned"), strictRanked.map { it.id })

        val boostRanked = DriverMapEngine.rankTripsForGtw(
            trips = listOf(offRouteTrip, alignedTrip),
            driverLocation = polokwane,
            corridor = boostGtw
        )
        assertEquals(listOf("gtw-aligned", "gtw-offroute"), boostRanked.map { it.id })
    }

    @Test
    fun `gtw syncDriverCorridorTarget persists locally even when backend endpoint is unavailable`() = runBlocking {
        val gtwTarget = DriverCorridorTarget(
            label = "Mankweng",
            destination = mankweng,
            maxDetourKm = 6.0,
            strictFilter = false,
            updatedAtMs = 123456L
        )
        val result = repo.syncDriverCorridorTarget(gtwTarget)
        assertEquals(gtwTarget, result)
        assertEquals(gtwTarget, repo.driverCorridorTarget.value)
        assertEquals(gtwTarget, store.loadDriverCorridorTarget())

        val cleared = repo.syncDriverCorridorTarget(null)
        assertNull(cleared)
        assertNull(repo.driverCorridorTarget.value)
        assertNull(store.loadDriverCorridorTarget())
    }
}
