package com.wayfare.ride

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import com.wayfare.ride.data.OfflineSyncCoordinator
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NearbyPlaceCategory
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RuralRunPreset
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NearbyPlacesDiscoveryTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val (status, payload) = when {
                path == "/api/places/nearby" -> 200 to """
                    {
                      "places": [
                        {
                          "id": "soweto-hosp-1",
                          "label": "Chris Hani Baragwanath Hospital · Soweto",
                          "latitude": -26.2611,
                          "longitude": 27.9436,
                          "provider": "vhaya_api",
                          "category": "clinic"
                        },
                        {
                          "id": "soweto-mall-1",
                          "label": "Maponya Mall · Klipspruit",
                          "latitude": -26.2597,
                          "longitude": 27.9025,
                          "provider": "vhaya_api",
                          "category": "shopping"
                        }
                      ]
                    }
                """.trimIndent()
                else -> 404 to """{"error":"not found"}"""
            }
            val bytes = payload.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        db.close()
    }

    @Test
    fun `parseOverpassPlacesJson classifies clinics malls paypoints ranks deduplicates and sorts by distance`() {
        val client = VhayaApiClient()
        val origin = RideCoordinates(-26.2485, 27.8540) // Soweto Protea North

        val rawOverpass = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 101,
                  "lat": -26.2600,
                  "lon": 27.9020,
                  "tags": { "name": "Maponya Mall", "shop": "mall", "addr:suburb": "Klipspruit" }
                },
                {
                  "type": "way",
                  "id": 202,
                  "center": { "lat": -26.2492, "lon": 27.8570 },
                  "tags": { "name": "Protea North Clinic", "amenity": "clinic", "addr:suburb": "Protea North" }
                },
                {
                  "type": "node",
                  "id": 203,
                  "lat": -26.2750,
                  "lon": 27.8900,
                  "tags": { "name": "Protea North Clinic", "amenity": "clinic" }
                },
                {
                  "type": "node",
                  "id": 303,
                  "lat": -26.2520,
                  "lon": 27.8610,
                  "tags": { "name": "Protea Post Office", "amenity": "post_office" }
                },
                {
                  "type": "node",
                  "id": 404,
                  "lat": -26.2550,
                  "lon": 27.8700,
                  "tags": { "name": "Naledi Taxi Rank", "amenity": "taxi" }
                },
                {
                  "type": "node",
                  "id": 505,
                  "lat": -26.2500,
                  "lon": 27.8500,
                  "tags": { "amenity": "clinic" }
                }
              ]
            }
        """.trimIndent()

        val parsed = client.parseOverpassPlacesJson(rawOverpass, origin, NearbyPlaceCategory.ALL)
        // Unnamed node 505 ignored, duplicate Protea North Clinic collapsed to closer node 202
        // Area suffix omitted when name already contains suburb ("Protea North")
        assertEquals(4, parsed.size)
        assertEquals("Protea North Clinic", parsed[0].label)
        assertEquals("clinic", parsed[0].category)
        assertEquals("Protea Post Office", parsed[1].label)
        assertEquals("pension", parsed[1].category)
        assertEquals("Naledi Taxi Rank", parsed[2].label)
        assertEquals("transit", parsed[2].category)
        assertEquals("Maponya Mall · Klipspruit", parsed[3].label)
        assertEquals("shopping", parsed[3].category)

        assertTrue((parsed[0].distanceMeters ?: 0.0) < (parsed[1].distanceMeters ?: 0.0))
        assertTrue((parsed[1].distanceMeters ?: 0.0) < (parsed[2].distanceMeters ?: 0.0))
        assertTrue((parsed[2].distanceMeters ?: 0.0) < (parsed[3].distanceMeters ?: 0.0))
    }

    @Test
    fun `parseNominatimPlacesJson extracts concise names infers categories and sorts by distance`() {
        val client = VhayaApiClient()
        val origin = RideCoordinates(-29.8587, 31.0218) // Durban CBD
        val rawNominatim = """
            [
              {
                "place_id": 9001,
                "lat": "-29.8732",
                "lon": "30.9854",
                "name": "King Edward VIII Hospital",
                "display_name": "King Edward VIII Hospital, Umbilo, Durban, KwaZulu-Natal",
                "category": "amenity",
                "type": "hospital",
                "address": { "suburb": "Umbilo", "city": "Durban" }
              },
              {
                "place_id": 9002,
                "lat": "-29.8555",
                "lon": "31.0260",
                "name": "The Workshop Shopping Centre",
                "display_name": "The Workshop Shopping Centre, Central, Durban",
                "category": "shop",
                "type": "mall",
                "address": { "suburb": "Central", "city": "Durban" }
              }
            ]
        """.trimIndent()

        val parsed = client.parseNominatimPlacesJson(rawNominatim, origin)
        assertEquals(2, parsed.size)
        assertEquals("The Workshop Shopping Centre · Central", parsed[0].label)
        assertEquals("shopping", parsed[0].category)
        assertEquals("King Edward VIII Hospital · Umbilo", parsed[1].label)
        assertEquals("clinic", parsed[1].category)
    }

    @Test
    fun `nearbyCuratedOfflineFallback does not leak Polokwane places outside Limpopo`() {
        val soweto = RideCoordinates(-26.2485, 27.8540)
        val capeTown = RideCoordinates(-33.9249, 18.4241)
        val mankweng = RideCoordinates(-23.8891, 29.7145)

        assertTrue(LaunchPolicy.nearbyCuratedOfflineFallback(soweto, NearbyPlaceCategory.ALL).isEmpty())
        assertTrue(LaunchPolicy.nearbyCuratedOfflineFallback(capeTown, NearbyPlaceCategory.CLINICS).isEmpty())

        val nearbyClinicsMankweng = LaunchPolicy.nearbyCuratedOfflineFallback(
            origin = mankweng,
            category = NearbyPlaceCategory.CLINICS
        )
        assertTrue(nearbyClinicsMankweng.isNotEmpty())
        assertTrue(nearbyClinicsMankweng.first().label.contains("Mankweng Hospital"))
    }

    @Test
    fun `offlineSyncCoordinator filters cached nearby places by radius and category`() = runBlocking {
        val coordinator = OfflineSyncCoordinator(
            context = context,
            cachedRouteDao = db.cachedRouteDao(),
            cachedPlaceDao = db.cachedPlaceDao(),
            json = json
        )
        val origin = RideCoordinates(-26.2485, 27.8540)
        coordinator.cachePlaceSuggestions(
            query = "nearby:all",
            suggestions = listOf(
                PlaceSearchSuggestion(
                    id = "c1",
                    label = "Chiawelo Community Health Centre",
                    latitude = -26.2830,
                    longitude = 27.8480,
                    provider = "osm_overpass",
                    category = "clinic"
                ),
                PlaceSearchSuggestion(
                    id = "s1",
                    label = "Protea Glen Mall",
                    latitude = -26.2710,
                    longitude = 27.8010,
                    provider = "osm_overpass",
                    category = "shopping"
                ),
                PlaceSearchSuggestion(
                    id = "far1",
                    label = "Pretoria Clinic",
                    latitude = -25.7479,
                    longitude = 28.2293,
                    provider = "osm_overpass",
                    category = "clinic"
                )
            )
        )

        val nearbyClinics = coordinator.loadNearbyCachedPlaces(
            origin = origin,
            category = NearbyPlaceCategory.CLINICS,
            maxRadiusMeters = 35_000.0
        )
        assertEquals(1, nearbyClinics.size)
        assertEquals("Chiawelo Community Health Centre", nearbyClinics.single().label)

        val nearbyShopping = coordinator.loadNearbyCachedPlaces(
            origin = origin,
            category = NearbyPlaceCategory.SHOPPING,
            maxRadiusMeters = 35_000.0
        )
        assertEquals(1, nearbyShopping.size)
        assertEquals("Protea Glen Mall", nearbyShopping.single().label)
    }

    @Test
    fun `repository loadNearbyPlaces fetches live nearby places and falls back to Room cache offline`() = runBlocking {
        val sowetoOrigin = RideCoordinates(-26.2580, 27.9100)
        val onlineStore = SessionStore(context, apiBaseUrlOverride = "http://127.0.0.1:$serverPort")
        onlineStore.saveSession(
            UserSession(
                userId = "rider-soweto-1",
                displayName = "Lerato Mokoena",
                email = "lerato@vhaya.co.za",
                role = RideRole.RIDER,
                emailVerified = true,
                idToken = "jwt-1",
                refreshToken = "ref-1",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        val onlineRepo = WayfareRepository(context, db, onlineStore, VhayaApiClient(backendBaseUrlProvider = { "http://127.0.0.1:$serverPort" }))
        val liveClinics = onlineRepo.loadNearbyPlaces(sowetoOrigin, NearbyPlaceCategory.CLINICS).getOrThrow()
        assertEquals(1, liveClinics.size)
        assertEquals("Chris Hani Baragwanath Hospital · Soweto", liveClinics.single().label)

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineStore = SessionStore(context, apiBaseUrlOverride = "http://127.0.0.1:$closedPort")
        val offlineRepo = WayfareRepository(context, db, offlineStore, VhayaApiClient(backendBaseUrlProvider = { "http://127.0.0.1:$closedPort" }))
        val offlineClinics = offlineRepo.loadNearbyPlaces(sowetoOrigin, NearbyPlaceCategory.CLINICS).getOrThrow()
        assertEquals(1, offlineClinics.size)
        assertEquals("Chris Hani Baragwanath Hospital · Soweto", offlineClinics.single().label)
    }

    @Test
    fun `viewModel applyRuralPreset switches selectedNearbyCategory to match preset`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val store = SessionStore(context, apiBaseUrlOverride = "http://127.0.0.1:$closedPort")
        store.saveSession(
            UserSession(
                userId = "rider-preset-cat-1",
                displayName = "Tshepo",
                email = "tshepo@vhaya.co.za",
                role = RideRole.RIDER,
                emailVerified = true,
                idToken = "jwt-1",
                refreshToken = "ref-1",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        val repo = WayfareRepository(context, db, store, VhayaApiClient())
        val vmStore = ViewModelStore()
        val vm = ViewModelProvider(vmStore, WayfareViewModel.Factory(repo))[WayfareViewModel::class.java]
        try {
            vm.applyRuralPreset(RuralRunPreset.CLINIC_RUN)
            val clinicState = awaitUiState(vm) { it.selectedNearbyCategory == NearbyPlaceCategory.CLINICS }
            assertEquals(NearbyPlaceCategory.CLINICS, clinicState.selectedNearbyCategory)

            vm.applyRuralPreset(RuralRunPreset.PENSION_DAY)
            val pensionState = awaitUiState(vm) { it.selectedNearbyCategory == NearbyPlaceCategory.PAYPOINTS }
            assertEquals(NearbyPlaceCategory.PAYPOINTS, pensionState.selectedNearbyCategory)

            vm.applyRuralPreset(RuralRunPreset.TOWN_GROCERY)
            val shopState = awaitUiState(vm) { it.selectedNearbyCategory == NearbyPlaceCategory.SHOPPING }
            assertEquals(NearbyPlaceCategory.SHOPPING, shopState.selectedNearbyCategory)

            vm.clearRuralPreset()
            val resetState = awaitUiState(vm) { it.selectedNearbyCategory == NearbyPlaceCategory.ALL }
            assertEquals(NearbyPlaceCategory.ALL, resetState.selectedNearbyCategory)
        } finally {
            vmStore.clear()
        }
    }

    private fun awaitUiState(
        vm: WayfareViewModel,
        timeoutMs: Long = 3_000L,
        predicate: (WayfareUiState) -> Boolean
    ): WayfareUiState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val current = vm.uiState.value
            if (predicate(current)) return current
            Thread.sleep(20)
        }
        return vm.uiState.value
    }
}
