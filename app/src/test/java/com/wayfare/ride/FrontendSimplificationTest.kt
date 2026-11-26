package com.wayfare.ride

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.ui.BookingDraft
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.auth.VhayaAuthScreen
import com.wayfare.ride.ui.common.AccountAndPlacesSheet
import com.wayfare.ride.ui.driver.DriverGtwNavBanner
import com.wayfare.ride.ui.driver.DriverMapFeaturePillRow
import com.wayfare.ride.ui.driver.DriverProfileTab
import com.wayfare.ride.ui.driver.MinRateFilterStrip
import com.wayfare.ride.ui.rider.ChooseRideAndFareSheet
import com.wayfare.ride.ui.theme.WayfareTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class FrontendSimplificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var server: HttpServer
    private var port: Int = 0
    private val lastIdpBodyRef = AtomicReference("")
    private val idpFailureCodeRef = AtomicReference<String?>(null)
    private val backendAccountDeleted = AtomicBoolean(false)
    private val firebaseAccountDeleted = AtomicBoolean(false)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        lastIdpBodyRef.set("")
        idpFailureCodeRef.set(null)
        backendAccountDeleted.set(false)
        firebaseAccountDeleted.set(false)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            val bodyText = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            val (status, payload) = when (path) {
                "/v1/accounts:createAuthUri" -> 200 to """
                    {
                      "kind": "identitytoolkit#CreateAuthUriResponse",
                      "authUri": "https://accounts.google.com/o/oauth2/auth?response_type=code&client_id=987654-firebase.apps.googleusercontent.com&redirect_uri=https://vhaya.firebaseapp.com/__/auth/handler&providerId=google.com",
                      "providerId": "google.com",
                      "sessionId": "session-123"
                    }
                """.trimIndent()
                "/v1/accounts:signInWithIdp" -> {
                    lastIdpBodyRef.set(bodyText)
                    val failureCode = idpFailureCodeRef.get()
                    if (failureCode != null) {
                        400 to """{"error":{"code":400,"message":"$failureCode"}}"""
                    } else {
                        200 to """
                            {
                              "localId": "google-uid-42",
                              "email": "naledi@gmail.com",
                              "displayName": "Naledi Mokoena",
                              "emailVerified": true,
                              "idToken": "google-firebase-jwt",
                              "refreshToken": "google-refresh-jwt",
                              "expiresIn": "3600"
                            }
                        """.trimIndent()
                    }
                }
                "/v1/accounts:delete" -> {
                    firebaseAccountDeleted.set(true)
                    200 to """{"kind":"identitytoolkit#DeleteAccountResponse"}"""
                }
                "/api/account" -> {
                    if (method.equals("DELETE", ignoreCase = true)) {
                        if (
                            bodyText.contains("\"confirmation\":\"DELETE\"") &&
                            bodyText.contains("\"reason\":\"No longer needed\"")
                        ) {
                            backendAccountDeleted.set(true)
                            200 to """{"deleted":true}"""
                        } else {
                            400 to """{"error":"confirmation is required"}"""
                        }
                    } else {
                        405 to """{"error":"method not allowed"}"""
                    }
                }
                "/api/rides" -> 200 to """{"role":"rider","trips":[]}"""
                "/api/wallet", "/api/support/cases" -> 200 to """{"wallet":{"balanceMinor":12500},"transactions":[],"cases":[]}"""
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

    private fun newApiClient(): VhayaApiClient = VhayaApiClient(
        firebaseAuthBaseUrl = "http://127.0.0.1:$port",
        firebaseTokenBaseUrl = "http://127.0.0.1:$port"
    )

    @Test
    fun `google id token exchanges via signInWithIdp and maps provider errors cleanly`() = runBlocking {
        val storeWithoutClientId = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key",
            googleWebClientIdOverride = ""
        )
        val autoRepo = WayfareRepository(context, db, storeWithoutClientId, newApiClient())
        assertEquals(
            "987654-firebase.apps.googleusercontent.com",
            autoRepo.resolveGoogleWebClientId().getOrThrow()
        )
        assertEquals("987654-firebase.apps.googleusercontent.com", autoRepo.googleWebClientId())

        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key",
            googleWebClientIdOverride = "12345.apps.googleusercontent.com"
        )
        val repo = WayfareRepository(context, db, store, newApiClient())

        assertEquals("12345.apps.googleusercontent.com", repo.googleWebClientId())

        val ok = repo.signInWithGoogleIdToken("sample.google.id.token").getOrThrow()
        assertEquals("google-uid-42", ok.userId)
        assertEquals("Naledi Mokoena", ok.displayName)
        assertTrue(ok.emailVerified)
        assertEquals(RideRole.RIDER, ok.role)
        assertTrue(lastIdpBodyRef.get().contains("providerId=google.com"))
        assertTrue(repo.authState.value is AuthState.Authenticated)

        val storeFromGoogleServices = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "",
            googleWebClientIdOverride = ""
        )
        val googleServicesRepo = WayfareRepository(context, db, storeFromGoogleServices, newApiClient())
        assertEquals(
            context.getString(R.string.default_web_client_id),
            googleServicesRepo.resolveGoogleWebClientId().getOrThrow()
        )
        assertEquals(
            context.getString(R.string.default_web_client_id),
            googleServicesRepo.googleWebClientId()
        )

        idpFailureCodeRef.set("OPERATION_NOT_ALLOWED")
        val disallowed = repo.signInWithGoogleIdToken("sample.google.id.token")
        assertTrue(disallowed.isFailure)
        assertTrue(disallowed.exceptionOrNull()?.message.orEmpty().contains("not enabled", ignoreCase = true))
    }

    @Test
    fun `auth screen shows google sign in button and reveals password checklist only when typing`() {
        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key",
            googleWebClientIdOverride = ""
        )
        val repo = WayfareRepository(context, db, store, newApiClient())
        val vm = WayfareViewModel(repo)

        composeRule.setContent {
            WayfareTheme {
                VhayaAuthScreen(
                    uiState = WayfareUiState(authState = AuthState.Unauthenticated),
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_google_sign_in_btn").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()

        // Switch to Create account tab; password requirements start collapsed
        composeRule.onNodeWithTag("auth_tab_create_account").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("8+ chars").fetchSemanticsNodes().isEmpty())

        // Typing into password reveals the live strength checklist
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("Vhaya")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_password_policy_checklist").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("8+ chars").assertIsDisplayed()
    }

    @Test
    fun `driver map pills hide offline maps and rate filter expands on tap`() {
        val pickup = RideCoordinates(-23.8891, 29.7145)
        var selectedRate = 0.0
        composeRule.setContent {
            WayfareTheme {
                androidx.compose.foundation.layout.Column {
                    DriverMapFeaturePillRow(
                        corridorTarget = DriverCorridorTarget("Mankweng", pickup, 5.0),
                        corridorFilteredOutCount = 0,
                        hazardCount = 1,
                        onOpenCorridorSheet = {},
                        onOpenHazardSheet = {}
                    )
                    MinRateFilterStrip(
                        selectedRate = selectedRate,
                        filteredOutCount = 2,
                        onSelectRate = { selectedRate = it }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Map overlay shows only Heading home & Hazard pills (Offline maps moved to Profile/Account)
        composeRule.onNodeWithTag("driver_corridor_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("driver_hazard_pill").assertIsDisplayed()

        // Rate filter chips start collapsed under Filter: All rides and expand on tap
        assertTrue(composeRule.onAllNodesWithTag("min_rate_chip_15").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Filter: All rides").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("min_rate_chip_15").assertIsDisplayed()
        composeRule.onNodeWithText("R 15+/km").assertIsDisplayed()
    }

    @Test
    fun `gtw active banner shows destination and handles mode toggle and clear`() {
        val origin = RideCoordinates(-23.9045, 29.4689)
        val mankweng = RideCoordinates(-23.8880, 29.7240)
        var modeToggled = false
        var cleared = false
        composeRule.setContent {
            WayfareTheme {
                DriverGtwNavBanner(
                    target = DriverCorridorTarget("Mankweng", mankweng, 6.0, strictFilter = true),
                    driverCoords = origin,
                    route = null,
                    isLoading = false,
                    matchedCount = 2,
                    onToggleMode = { modeToggled = true },
                    onOpenSettings = {},
                    onOpenExternal = {},
                    onClear = { cleared = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("driver_gtw_banner").assertIsDisplayed()
        composeRule.onNodeWithText("GTW → Mankweng").assertIsDisplayed()
        composeRule.onNodeWithTag("driver_gtw_mode_toggle").performClick()
        composeRule.onNodeWithTag("driver_gtw_clear_btn").performClick()
        composeRule.waitForIdle()

        assertTrue(modeToggled)
        assertTrue(cleared)
    }

    @Test
    fun `rider customize ride accordion starts collapsed and discloses return request on tap`() {
        val pickup = RideCoordinates(-23.8891, 29.7145)
        val dest = RideCoordinates(-23.9045, 29.4688)
        val route = RideRoute(
            distanceMeters = 12_000,
            durationSeconds = 900,
            provider = "osrm",
            originCoordinates = pickup,
            destinationCoordinates = dest,
            fareEstimate = 180.0,
            quoteToken = "quote-123"
        )
        val draft = BookingDraft(
            pickupLabel = "Mankweng",
            pickupCoords = pickup,
            destinationLabel = "Polokwane CBD",
            destinationCoords = dest,
            category = RideCategory.STANDARD,
            previewRoute = route,
            paymentMethod = PaymentMethod.CASH
        )

        var backClicked = false
        composeRule.setContent {
            WayfareTheme {
                ChooseRideAndFareSheet(
                    draft = draft,
                    walletBalanceMinor = 0L,
                    isDestinationSaved = false,
                    onToggleSaveDestination = {},
                    onBack = { backClicked = true },
                    onSelectCategory = {},
                    onToggleOffer = {},
                    onOfferTextChange = {},
                    onAdjustOffer = {},
                    onToggleSchedule = {},
                    onSelectPaymentMethod = {},
                    onRetryRoute = {},
                    onRequestRide = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Customize ride accordion starts collapsed and expands on tap to show Return Request
        assertTrue(composeRule.onAllNodesWithTag("rural_chip_return_request").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("ride_sheet_back").performClick()
        composeRule.waitForIdle()
        assertTrue("Expected back button inside ChooseRideAndFareSheet to receive click", backClicked)
        composeRule.onNodeWithTag("toggle_trip_extras_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Hide ride options").assertExists()
        composeRule.onNodeWithTag("rural_chip_return_request").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `user profile sheet shows delete account button and opens confirmation dialog`() {
        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key"
        )
        val repo = WayfareRepository(context, db, store, newApiClient())
        val vm = WayfareViewModel(repo)
        val session = UserSession(
            userId = "user-profile-1",
            displayName = "Thabo Mokoena",
            email = "thabo@example.com",
            role = RideRole.RIDER,
            emailVerified = true
        )

        composeRule.setContent {
            WayfareTheme {
                AccountAndPlacesSheet(
                    uiState = WayfareUiState(
                        authState = AuthState.Authenticated(session),
                        showAccountSheet = true
                    ),
                    viewModel = vm,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("account_sheet_list")
            .performScrollToNode(hasTestTag("account_delete_account_btn"))
        composeRule.onNodeWithTag("account_delete_account_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("account_delete_account_btn").performClick()
    }

    @Test
    fun `driver profile tab shows delete account button for active driver`() {
        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key"
        )
        val repo = WayfareRepository(context, db, store, newApiClient())
        val vm = WayfareViewModel(repo)
        val driverSession = UserSession(
            userId = "driver-profile-1",
            displayName = "Bongani Khumalo",
            email = "bongani@example.com",
            role = RideRole.DRIVER,
            emailVerified = true
        )

        composeRule.setContent {
            WayfareTheme {
                DriverProfileTab(
                    uiState = WayfareUiState(
                        authState = AuthState.Authenticated(driverSession)
                    ),
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bongani Khumalo").assertIsDisplayed()
        composeRule.onNodeWithTag("driver_profile_delete_account_btn")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithText("Delete account").assertIsDisplayed()
    }

    @Test
    fun `delete account repository action erases backend and firebase session cleanly`() = runBlocking {
        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key"
        )
        store.saveSession(
            UserSession(
                userId = "user-del-99",
                displayName = "Thabo Mokoena",
                email = "thabo@example.com",
                role = RideRole.RIDER,
                emailVerified = true,
                idToken = "valid-jwt-token",
                refreshToken = "refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        val repo = WayfareRepository(context, db, store, newApiClient())
        assertTrue(repo.authState.value is AuthState.Authenticated)

        val outcome = repo.deleteAccountAndWipeLocalData("No longer needed").getOrThrow()
        assertTrue(outcome.backendDeleted)
        assertTrue(outcome.authDeleted)
        assertTrue(backendAccountDeleted.get())
        assertTrue(firebaseAccountDeleted.get())
        assertTrue(repo.authState.value is AuthState.Unauthenticated)
    }
}
