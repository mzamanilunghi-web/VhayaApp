package com.wayfare.ride

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.TripEntity
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareMigrations
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.LaunchPolicy
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomOfflineSyncRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverTrips = linkedMapOf<String, RideTrip>()
    private val recordedRideCreateBodies = CopyOnWriteArrayList<String>()
    private val recordedRideActionBodies = CopyOnWriteArrayList<Pair<String, String>>()
    private val previewRequestCount = AtomicInteger(0)
    @Volatile
    private var mockRouteFareEstimate: Double = 175.0
    private var nextTripSeq = 1

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port
        server.createContext("/") { exchange -> handleRequest(exchange) }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        db.close()
    }

    private fun seedSession(): SessionStore {
        val store = SessionStore(context, apiBaseUrlOverride = "http://127.0.0.1:$serverPort")
        store.saveSession(
            UserSession(
                userId = "rider-rural-1",
                displayName = "Mpho Makhado",
                email = "mpho@vhaya.co.za",
                role = RideRole.RIDER,
                emailVerified = true,
                idToken = "valid-jwt-token",
                refreshToken = "refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        return store
    }

    private fun newRepository(baseUrl: String = "http://127.0.0.1:$serverPort"): WayfareRepository {
        val store = SessionStore(context, apiBaseUrlOverride = baseUrl)
        return WayfareRepository(context, db, store, VhayaApiClient())
    }

    private fun handleRequest(exchange: HttpExchange) {
        val method = exchange.requestMethod
        val path = exchange.requestURI.path
        val body = exchange.requestBody.bufferedReader().use { it.readText() }

        fun respond(status: Int, payload: String) {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        when {
            path == "/api/rides" && method == "GET" -> {
                val tripsJson = serverTrips.values.joinToString(",") { json.encodeToString(it) }
                respond(200, """{"role":"rider","trips":[$tripsJson]}""")
            }

            path == "/api/places/search" && method == "POST" -> {
                val obj = json.parseToJsonElement(body).jsonObject
                val q = obj["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val placesJson = if (q.contains("turfloop", ignoreCase = true) || q.contains("shop", ignoreCase = true)) {
                    """
                    {
                      "places": [
                        {
                          "id": "remote-turfloop-spaza",
                          "label": "Turfloop Junction Hardware & Spaza",
                          "subtitle": "R71 Road, Turfloop",
                          "latitude": -23.8861,
                          "longitude": 29.7332,
                          "provider": "vhaya_places"
                        }
                      ]
                    }
                    """.trimIndent()
                } else {
                    """{"places":[]}"""
                }
                respond(200, placesJson)
            }

            path == "/api/routes/preview" && method == "POST" -> {
                previewRequestCount.incrementAndGet()
                val routeJson = """
                    {
                      "route": {
                        "distanceMeters": 18400,
                        "durationSeconds": 1320,
                        "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                        "provider": "osrm",
                        "originCoordinates": {"latitude": -23.8891, "longitude": 29.7145},
                        "destinationCoordinates": {"latitude": -23.9045, "longitude": 29.4688},
                        "fareEstimate": $mockRouteFareEstimate,
                        "quoteToken": "live-quote-99"
                      }
                    }
                """.trimIndent()
                respond(200, routeJson)
            }

            path == "/api/rides" && method == "POST" -> {
                recordedRideCreateBodies += body
                val obj = json.parseToJsonElement(body).jsonObject
                val id = "server-trip-${nextTripSeq++}"
                val clientReqId = obj["clientRequestId"]?.jsonPrimitive?.contentOrNull
                val fare = obj["fare"]?.jsonPrimitive?.doubleOrNull ?: 175.0
                val pricingModeStr = obj["pricingMode"]?.jsonPrimitive?.contentOrNull ?: "offer"
                val pricingMode = if (pricingModeStr.equals("estimate", ignoreCase = true)) {
                    PricingMode.ESTIMATE
                } else {
                    PricingMode.OFFER
                }
                val isReturnReq = obj["isReturnRequest"]?.jsonPrimitive?.booleanOrNull == true
                val trip = RideTrip(
                    id = id,
                    riderId = "rider-rural-1",
                    pickup = obj["pickup"]?.jsonPrimitive?.contentOrNull ?: "Mankweng",
                    destination = obj["destination"]?.jsonPrimitive?.contentOrNull ?: "Polokwane",
                    category = RideCategory.BAKKIE,
                    fare = fare,
                    pricingMode = pricingMode,
                    status = RideStatus.REQUESTED,
                    paymentMethod = PaymentMethod.CASH,
                    paymentStatus = PaymentStatus.NONE,
                    createdAt = System.currentTimeMillis(),
                    pickupCoordinates = RideCoordinates(-23.8891, 29.7145),
                    destinationCoordinates = RideCoordinates(-23.9045, 29.4688),
                    route = RideRoute(distanceMeters = 18400, durationSeconds = 1320, quoteToken = "live-quote-99"),
                    isReturnRequest = isReturnReq,
                    clientRequestId = clientReqId
                )
                serverTrips[id] = trip
                respond(200, """{"trip":${json.encodeToString(trip)}}""")
            }

            path.startsWith("/api/rides/") && path.endsWith("/actions") && method == "POST" -> {
                val tripId = path.removePrefix("/api/rides/").removeSuffix("/actions")
                recordedRideActionBodies += (tripId to body)
                val obj = json.parseToJsonElement(body).jsonObject
                val action = obj["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val existing = serverTrips[tripId]
                if (existing == null) {
                    respond(404, """{"error":"Trip not found"}""")
                } else {
                    val updated = when (action) {
                        "advance" -> when (existing.status) {
                            RideStatus.ACCEPTED -> existing.copy(status = RideStatus.ARRIVED)
                            RideStatus.ARRIVED -> existing.copy(status = RideStatus.IN_PROGRESS)
                            RideStatus.IN_PROGRESS -> existing.copy(status = RideStatus.COMPLETED)
                            else -> existing
                        }
                        "cancel" -> existing.copy(
                            status = RideStatus.CANCELLED,
                            unfulfillmentReason = obj["reason"]?.jsonPrimitive?.contentOrNull
                        )
                        else -> existing
                    }
                    serverTrips[tripId] = updated
                    respond(200, """{"trip":${json.encodeToString(updated)}}""")
                }
            }

            path == "/api/wallet" && method == "GET" ->
                respond(200, """{"wallet":{"balanceMinor":0,"currency":"ZAR"},"transactions":[]}""")

            path == "/api/support-cases" && method == "GET" ->
                respond(200, """{"cases":[]}""")

            path == "/api/driver/profile" && method == "GET" ->
                respond(404, """{"error":"No profile"}""")

            else -> respond(404, """{"error":"Unhandled mock endpoint $method $path"}""")
        }
    }

    @Test
    fun `room caches corridor route online and falls back to cache or estimate offline`() = runBlocking {
        seedSession()
        val mankweng = RideCoordinates(-23.8891, 29.7145)
        val polokwane = RideCoordinates(-23.9045, 29.4688)

        val onlineRepo = newRepository()
        val liveRoute = onlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        assertEquals("live-quote-99", liveRoute.quoteToken)
        assertEquals(18400, liveRoute.distanceMeters)
        assertEquals(1, db.cachedRouteDao().count())

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")

        // Cached corridor hit
        val cachedFallback = offlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        assertEquals("room-cache", cachedFallback.provider)
        assertEquals(18400, cachedFallback.distanceMeters)
        assertEquals(175.0, cachedFallback.fareEstimate ?: 0.0, 0.01)
        assertNull(cachedFallback.quoteToken)

        // Uncached rural corridor -> deterministic Haversine estimate
        val tzaneen = RideCoordinates(-23.8332, 30.1635)
        val estimatedFallback = offlineRepo.previewRoute("Mankweng", "Tzaneen", mankweng, tzaneen).getOrThrow()
        assertEquals("offline-estimate", estimatedFallback.provider)
        assertTrue(estimatedFallback.distanceMeters > 30_000)
        assertTrue(estimatedFallback.durationSeconds > LaunchPolicy.MIN_RIDE_DURATION_SECONDS)
    }

    @Test
    fun `low data mode returns cached corridor immediately without hitting route api`() = runBlocking {
        seedSession()
        val mankweng = RideCoordinates(-23.8891, 29.7145)
        val polokwane = RideCoordinates(-23.9045, 29.4688)

        val onlineRepo = newRepository()
        onlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        assertEquals(1, previewRequestCount.get())

        onlineRepo.setLowDataMode(true)
        val lowDataRoute = onlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        assertEquals("room-cache", lowDataRoute.provider)
        assertEquals(18400, lowDataRoute.distanceMeters)
        assertEquals(1, previewRequestCount.get())
    }

    @Test
    fun `place search caches remote results in room and serves them offline`() = runBlocking {
        seedSession()
        val mankweng = RideCoordinates(-23.8891, 29.7145)

        val onlineRepo = newRepository()
        val onlineResults = onlineRepo.searchPlaces("Turfloop Junction", mankweng).getOrThrow()
        assertTrue(onlineResults.any { it.label == "Turfloop Junction Hardware & Spaza" })
        assertTrue(db.cachedPlaceDao().count() >= 1)

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")
        val offlineResults = offlineRepo.searchPlaces("Turfloop Junction", mankweng).getOrThrow()
        assertTrue(offlineResults.any { it.label == "Turfloop Junction Hardware & Spaza" })
    }

    @Test
    fun `offline ride request persists across refresh and flushes idempotently on reconnect`() = runBlocking {
        seedSession()
        val mankweng = RideCoordinates(-23.8891, 29.7145)
        val polokwane = RideCoordinates(-23.9045, 29.4688)

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")

        val estimated = offlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        val offlineTrip = offlineRepo.createRideRequest(
            pickup = "Mankweng",
            destination = "Polokwane",
            pickupCoords = mankweng,
            destCoords = polokwane,
            category = RideCategory.BAKKIE,
            customOfferFare = 240.0,
            pickupAt = null,
            paymentMethod = PaymentMethod.CASH,
            existingRoute = estimated,
            pickupNote = "Next to clinic gate",
            includesGravelRoad = true
        ).getOrThrow()

        assertTrue(offlineTrip.id.startsWith("offline_"))
        assertEquals("PENDING_UPLOAD", offlineTrip.syncStatus)
        assertNotNull(offlineTrip.clientRequestId)
        assertEquals(1, offlineRepo.pendingOfflineCount.value)
        assertFalse(LaunchPolicy.isTripExpired(offlineTrip, offlineTrip.createdAt + LaunchPolicy.OPEN_REQUEST_TTL_MS * 2))

        // Failed flush triggers exponential backoff retry tracking without dropping the ride
        val zeroFlushed = offlineRepo.flushOfflineQueue(force = true)
        assertEquals(0, zeroFlushed)
        val queuedEntity = db.pendingRideSyncDao().getAllOnce().single()
        assertEquals(1, queuedEntity.retryCount)
        assertTrue(queuedEntity.nextRetryAtMs > System.currentTimeMillis())

        // Conflict-preserving server refresh must NOT wipe un-synced offline placeholder
        val existingServerTrip = RideTrip(
            id = "older-completed-trip",
            riderId = "rider-rural-1",
            pickup = "Seshego",
            destination = "Polokwane",
            category = RideCategory.STANDARD,
            fare = 90.0,
            pricingMode = PricingMode.ESTIMATE,
            status = RideStatus.COMPLETED,
            createdAt = System.currentTimeMillis() - 86_400_000L
        )
        db.tripDao().replaceSyncedPreservingPending(
            listOf(
                TripEntity(
                    id = existingServerTrip.id,
                    riderId = existingServerTrip.riderId,
                    driverId = null,
                    pickup = existingServerTrip.pickup,
                    destination = existingServerTrip.destination,
                    category = existingServerTrip.category.name,
                    currency = existingServerTrip.currency,
                    fare = existingServerTrip.fare,
                    pricingMode = existingServerTrip.pricingMode.name,
                    status = existingServerTrip.status.name,
                    paymentStatus = existingServerTrip.paymentStatus.name,
                    paymentMethod = existingServerTrip.paymentMethod.name,
                    paymentReference = null,
                    createdAt = existingServerTrip.createdAt,
                    pickupAt = null,
                    expiresAt = null,
                    pickupCoordinatesJson = null,
                    destinationCoordinatesJson = null,
                    routeJson = null,
                    offersJson = "[]",
                    messagesJson = "[]",
                    riderRating = null,
                    riderRatingTagsJson = "[]",
                    tripPin = null,
                    arrivalConfirmationMethod = null,
                    arrivalConfirmedAt = null,
                    driverProfileJson = null,
                    liveDriverLocationJson = null,
                    unfulfillmentReason = null
                )
            )
        )
        val tripsDuringSync = offlineRepo.trips.first()
        assertTrue(tripsDuringSync.any { it.id == offlineTrip.id && it.syncStatus == "PENDING_UPLOAD" })
        assertTrue(tripsDuringSync.any { it.id == "older-completed-trip" })

        // Without force, exponential backoff skips immediate retry
        val onlineRepo = newRepository()
        assertEquals(0, onlineRepo.flushOfflineQueue(force = false))

        // Forced reconnect sync uploads with matching clientRequestId & replaces offline placeholder
        val flushed = onlineRepo.flushOfflineQueue(force = true)
        assertEquals(1, flushed)
        assertEquals(0, onlineRepo.pendingOfflineCount.value)
        assertEquals(1, recordedRideCreateBodies.size)
        assertTrue(recordedRideCreateBodies.single().contains(offlineTrip.clientRequestId!!))

        val finalTrips = onlineRepo.trips.first()
        assertFalse(finalTrips.any { it.id == offlineTrip.id })
        val syncedTrip = finalTrips.firstOrNull { it.id.startsWith("server-trip-") }
        assertNotNull(syncedTrip)
        assertEquals("SYNCED", syncedTrip?.syncStatus)
        assertEquals("Next to clinic gate", syncedTrip?.pickupNote)
        assertEquals(true, syncedTrip?.includesGravelRoad)
    }

    @Test
    fun `fare drift protection caps upward live jump above 15 percent when flushing offline ride`() = runBlocking {
        seedSession()
        val mankweng = RideCoordinates(-23.8891, 29.7145)
        val polokwane = RideCoordinates(-23.9045, 29.4688)

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")

        // Create offline ride using offline route estimate (no customOfferFare)
        val estimated = offlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        val offlineTrip = offlineRepo.createRideRequest(
            pickup = "Mankweng",
            destination = "Polokwane",
            pickupCoords = mankweng,
            destCoords = polokwane,
            category = RideCategory.STANDARD,
            customOfferFare = null,
            pickupAt = null,
            paymentMethod = PaymentMethod.CASH,
            existingRoute = estimated
        ).getOrThrow()

        // Simulate live server fare jumping massively (>15% drift above offlineFare) upon reconnect
        mockRouteFareEstimate = offlineTrip.fare * 2.1

        val onlineRepo = newRepository()
        val flushed = onlineRepo.flushOfflineQueue(force = true)
        assertEquals(1, flushed)
        val uploadedBody = json.parseToJsonElement(recordedRideCreateBodies.single()).jsonObject
        val uploadedFare = uploadedBody["fare"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val uploadedMode = uploadedBody["pricingMode"]?.jsonPrimitive?.contentOrNull
        assertEquals(offlineTrip.fare, uploadedFare, 0.01)
        assertEquals("offer", uploadedMode)
    }

    @Test
    fun `offline critical ride action queues optimistically preserves state across refresh and syncs on reconnect`() = runBlocking {
        seedSession()
        val activeTrip = RideTrip(
            id = "synced-active-trip-77",
            riderId = "rider-rural-1",
            driverId = "driver-9",
            pickup = "Mankweng Hospital",
            destination = "Polokwane CBD",
            category = RideCategory.STANDARD,
            fare = 150.0,
            pricingMode = PricingMode.ESTIMATE,
            status = RideStatus.IN_PROGRESS,
            paymentMethod = PaymentMethod.CASH,
            createdAt = System.currentTimeMillis() - 600_000L,
            syncStatus = "SYNCED"
        )
        serverTrips[activeTrip.id] = activeTrip

        val onlineRepo = newRepository()
        onlineRepo.refreshActiveSessionData()
        assertEquals(RideStatus.IN_PROGRESS, db.tripDao().getById(activeTrip.id)?.let { RideStatus.valueOf(it.status) })

        // Go offline and complete the ride
        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")
        val completedResult = offlineRepo.completeTrip(activeTrip.id)
        assertTrue(completedResult.isSuccess)
        val optimisticTrip = completedResult.getOrThrow()
        assertEquals(RideStatus.COMPLETED, optimisticTrip.status)
        assertEquals("PENDING_UPLOAD", optimisticTrip.syncStatus)
        assertEquals(1, offlineRepo.pendingOfflineCount.value)
        assertEquals("ride_action", db.pendingRideSyncDao().getAllOnce().single().operationType)

        // Simulate a stale server refresh still claiming IN_PROGRESS before flush -> local PENDING_UPLOAD must win
        db.tripDao().replaceSyncedPreservingPending(
            listOf(
                TripEntity(
                    id = activeTrip.id,
                    riderId = activeTrip.riderId,
                    driverId = activeTrip.driverId,
                    pickup = activeTrip.pickup,
                    destination = activeTrip.destination,
                    category = activeTrip.category.name,
                    currency = activeTrip.currency,
                    fare = activeTrip.fare,
                    pricingMode = activeTrip.pricingMode.name,
                    status = RideStatus.IN_PROGRESS.name,
                    paymentStatus = activeTrip.paymentStatus.name,
                    paymentMethod = activeTrip.paymentMethod.name,
                    paymentReference = null,
                    createdAt = activeTrip.createdAt,
                    pickupAt = null,
                    expiresAt = null,
                    pickupCoordinatesJson = null,
                    destinationCoordinatesJson = null,
                    routeJson = null,
                    offersJson = "[]",
                    messagesJson = "[]",
                    riderRating = null,
                    riderRatingTagsJson = "[]",
                    tripPin = null,
                    arrivalConfirmationMethod = null,
                    arrivalConfirmedAt = null,
                    driverProfileJson = null,
                    liveDriverLocationJson = null,
                    unfulfillmentReason = null
                )
            )
        )
        val preserved = db.tripDao().getById(activeTrip.id)
        assertEquals(RideStatus.COMPLETED.name, preserved?.status)
        assertEquals("PENDING_UPLOAD", preserved?.syncStatus)

        // Reconnect and flush -> sends /api/rides/synced-active-trip-77/actions and clears queue
        val flushed = onlineRepo.flushOfflineQueue(force = true)
        assertEquals(1, flushed)
        assertEquals(0, onlineRepo.pendingOfflineCount.value)
        assertEquals(1, recordedRideActionBodies.size)
        assertEquals(activeTrip.id, recordedRideActionBodies.single().first)

        val syncedAfterFlush = db.tripDao().getById(activeTrip.id)
        assertEquals(RideStatus.COMPLETED.name, syncedAfterFlush?.status)
        assertEquals("SYNCED", syncedAfterFlush?.syncStatus)
    }

    @Test
    fun `explicit migrations 7 to 8 and 8 to 9 preserve existing trips and add offline sync tables`() {
        val dbFile = File(context.cacheDir, "migration_test_v7_v9.db")
        if (dbFile.exists()) dbFile.delete()

        // Create legacy v7 trips table without syncStatus/clientRequestId
        val rawV7Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawV7Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trips` (
                `id` TEXT NOT NULL,
                `riderId` TEXT NOT NULL,
                `driverId` TEXT,
                `pickup` TEXT NOT NULL,
                `destination` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `fare` REAL NOT NULL,
                `pricingMode` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `paymentStatus` TEXT NOT NULL,
                `paymentMethod` TEXT NOT NULL,
                `paymentReference` TEXT,
                `createdAt` INTEGER NOT NULL,
                `pickupAt` INTEGER,
                `expiresAt` INTEGER,
                `pickupCoordinatesJson` TEXT,
                `destinationCoordinatesJson` TEXT,
                `routeJson` TEXT,
                `offersJson` TEXT NOT NULL,
                `messagesJson` TEXT NOT NULL,
                `riderRating` INTEGER,
                `riderRatingTagsJson` TEXT NOT NULL,
                `tripPin` TEXT,
                `arrivalConfirmationMethod` TEXT,
                `arrivalConfirmedAt` INTEGER,
                `driverProfileJson` TEXT,
                `liveDriverLocationJson` TEXT,
                `unfulfillmentReason` TEXT,
                `pickupNote` TEXT,
                `cashChangeForZar` INTEGER,
                `riderCompletedRides` INTEGER,
                `riderVerified` INTEGER,
                `driverArrivedAt` INTEGER,
                `beneficiaryName` TEXT,
                `beneficiaryPhone` TEXT,
                `isSharedRide` INTEGER NOT NULL,
                `seatCount` INTEGER NOT NULL,
                `isReturnTrip` INTEGER NOT NULL,
                `includesGravelRoad` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        rawV7Db.execSQL(
            """
            INSERT INTO `trips` (
                id, riderId, driverId, pickup, destination, category, currency, fare,
                pricingMode, status, paymentStatus, paymentMethod, paymentReference,
                createdAt, pickupAt, expiresAt, pickupCoordinatesJson, destinationCoordinatesJson,
                routeJson, offersJson, messagesJson, riderRating, riderRatingTagsJson,
                tripPin, arrivalConfirmationMethod, arrivalConfirmedAt, driverProfileJson,
                liveDriverLocationJson, unfulfillmentReason, pickupNote, cashChangeForZar,
                riderCompletedRides, riderVerified, driverArrivedAt, beneficiaryName,
                beneficiaryPhone, isSharedRide, seatCount, isReturnTrip, includesGravelRoad
            ) VALUES (
                'legacy-trip-v7', 'rider-1', NULL, 'Ga-Maja', 'Polokwane', 'STANDARD', 'ZAR', 140.0,
                'ESTIMATE', 'COMPLETED', 'NONE', 'CASH', NULL,
                1700000000000, NULL, NULL, NULL, NULL,
                NULL, '[]', '[]', NULL, '[]',
                NULL, NULL, NULL, NULL,
                NULL, NULL, NULL, NULL,
                NULL, NULL, NULL, NULL,
                NULL, 0, 1, 0, 1
            )
            """.trimIndent()
        )
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `saved_destinations` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `trusted_contacts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, PRIMARY KEY(`id`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `wallet_transactions` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `reference` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `driver_applications` (`userId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `email` TEXT, `contactPhone` TEXT NOT NULL, `city` TEXT NOT NULL, `applicationStatus` TEXT NOT NULL, `verificationStatus` TEXT NOT NULL, `vehicle` TEXT NOT NULL, `vehicleColor` TEXT NOT NULL, `registration` TEXT NOT NULL, `licenceExpiry` TEXT NOT NULL, `operatingLicenceReference` TEXT NOT NULL, `isOnline` INTEGER NOT NULL, `documentsUploadedJson` TEXT NOT NULL, `documentsConsent` INTEGER NOT NULL, `staffNote` TEXT, `submittedAt` INTEGER, `reviewedAt` INTEGER, `complianceChecksJson` TEXT NOT NULL, `gravelReady` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `support_cases` (`id` INTEGER NOT NULL, `rideId` TEXT NOT NULL, `reporterName` TEXT NOT NULL, `category` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, `assignedTo` TEXT, `staffNote` TEXT, `message` TEXT NOT NULL, `pickup` TEXT NOT NULL, `destination` TEXT NOT NULL, `rideStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `repliesJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `payout_records` (`id` INTEGER NOT NULL, `driverId` TEXT NOT NULL, `driverName` TEXT NOT NULL, `provider` TEXT NOT NULL, `externalReference` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, `periodStart` TEXT, `periodEnd` TEXT, `reconciliationStatus` TEXT NOT NULL, `note` TEXT, `recordedAt` INTEGER NOT NULL, `reconciledAt` INTEGER, PRIMARY KEY(`id`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `user_accounts` (`userId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `email` TEXT NOT NULL, `role` TEXT NOT NULL, `isSuspended` INTEGER NOT NULL, `suspensionReason` TEXT, `rideCount` INTEGER NOT NULL, `completedRideCount` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `payout_batches` (`id` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, `periodStart` INTEGER, `periodEnd` INTEGER, `status` TEXT NOT NULL, `totalAmountMinor` INTEGER NOT NULL, `driverCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `paidAt` INTEGER, PRIMARY KEY(`id`))")
        rawV7Db.execSQL("CREATE TABLE IF NOT EXISTS `activity_logs` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `actor` TEXT NOT NULL, `subject` TEXT NOT NULL, `summary` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        rawV7Db.version = 7
        rawV7Db.close()

        val migratedDb = Room.databaseBuilder(context, WayfareDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .addMigrations(
                WayfareMigrations.MIGRATION_7_8,
                WayfareMigrations.MIGRATION_8_9,
                WayfareMigrations.MIGRATION_9_10,
                WayfareMigrations.MIGRATION_10_11
            )
            .build()

        runBlocking {
            val migratedTrip = migratedDb.tripDao().getById("legacy-trip-v7")
            assertNotNull(migratedTrip)
            assertEquals("SYNCED", migratedTrip?.syncStatus)
            assertEquals("Ga-Maja", migratedTrip?.pickup)
            assertEquals(true, migratedTrip?.includesGravelRoad)
            assertEquals(false, migratedTrip?.isReturnRequest)
            assertEquals(0, migratedDb.cachedRouteDao().count())
            assertEquals(0, migratedDb.pendingRideSyncDao().countPending())
            assertEquals(0, migratedDb.cachedPlaceDao().count())
            assertEquals(0, migratedDb.roadHazardDao().count())
        }

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `canceling offline queued ride removes trip and sync job without network call`() = runBlocking {
        seedSession()
        val mankweng = RideCoordinates(-23.8891, 29.7145)
        val polokwane = RideCoordinates(-23.9045, 29.4688)

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")

        val estimated = offlineRepo.previewRoute("Mankweng", "Polokwane", mankweng, polokwane).getOrThrow()
        val offlineTrip = offlineRepo.createRideRequest(
            pickup = "Mankweng",
            destination = "Polokwane",
            pickupCoords = mankweng,
            destCoords = polokwane,
            category = RideCategory.STANDARD,
            customOfferFare = null,
            pickupAt = null,
            paymentMethod = PaymentMethod.CASH,
            existingRoute = estimated
        ).getOrThrow()

        assertEquals(1, offlineRepo.pendingOfflineCount.value)

        val cancelRes = offlineRepo.cancelTrip(offlineTrip.id, "Changed mind")
        assertTrue(cancelRes.isSuccess)
        assertEquals(RideStatus.CANCELLED, cancelRes.getOrThrow().status)
        assertEquals(0, offlineRepo.pendingOfflineCount.value)
        assertTrue(offlineRepo.trips.first().none { it.id == offlineTrip.id })
        assertTrue(db.pendingRideSyncDao().getAllOnce().isEmpty())
    }

    @Test
    fun `backoff policy scales exponentially up to five minute cap`() {
        assertEquals(5_000L, LaunchPolicy.computeOfflineSyncBackoffMs(0))
        assertEquals(10_000L, LaunchPolicy.computeOfflineSyncBackoffMs(1))
        assertEquals(20_000L, LaunchPolicy.computeOfflineSyncBackoffMs(2))
        assertEquals(40_000L, LaunchPolicy.computeOfflineSyncBackoffMs(3))
        assertEquals(300_000L, LaunchPolicy.computeOfflineSyncBackoffMs(6))
        assertEquals(300_000L, LaunchPolicy.computeOfflineSyncBackoffMs(20))
    }

    @Test
    fun `offline return request preserves flag across room storage and upload flush`() = runBlocking {
        seedSession()
        val gaMaja = RideCoordinates(-24.0840, 29.5910)
        val polokwane = RideCoordinates(-23.9045, 29.4688)

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")
        val estimated = offlineRepo.previewRoute("Ga-Maja", "Polokwane", gaMaja, polokwane).getOrThrow()

        val offlineReturnTrip = offlineRepo.createRideRequest(
            pickup = "Ga-Maja Clinic",
            destination = "Polokwane CBD",
            pickupCoords = gaMaja,
            destCoords = polokwane,
            category = RideCategory.STANDARD,
            customOfferFare = 148.0,
            pickupAt = null,
            paymentMethod = PaymentMethod.CASH,
            existingRoute = estimated,
            isReturnTrip = false,
            isReturnRequest = true
        ).getOrThrow()

        assertTrue(offlineReturnTrip.isReturnRequest)
        assertFalse(offlineReturnTrip.isReturnTrip)

        val storedEntity = db.tripDao().getById(offlineReturnTrip.id)
        assertEquals(true, storedEntity?.isReturnRequest)

        val onlineRepo = newRepository()
        val flushed = onlineRepo.flushOfflineQueue(force = true)
        assertEquals(1, flushed)

        val uploadedBody = json.parseToJsonElement(recordedRideCreateBodies.single()).jsonObject
        assertEquals(true, uploadedBody["isReturnRequest"]?.jsonPrimitive?.booleanOrNull)
        val syncedTrip = onlineRepo.trips.first().firstOrNull { it.id.startsWith("server-trip-") }
        assertNotNull(syncedTrip)
        assertEquals(true, syncedTrip?.isReturnRequest)
    }
}
