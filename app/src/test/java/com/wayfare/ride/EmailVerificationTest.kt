package com.wayfare.ride

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sun.net.httpserver.HttpServer
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.AccountVerificationChange
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.service.AccountVerificationWorker
import com.wayfare.ride.service.VerificationSyncScheduler
import com.wayfare.ride.ui.WayfareViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
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
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmailVerificationTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var server: HttpServer
    private var port: Int = 0

    private val lookupVerified = AtomicBoolean(false)
    private val lookupReturnsExpiredOnce = AtomicBoolean(false)
    private val lookupStringTrue = AtomicBoolean(false)
    private val driverRoleRef = AtomicReference("rider")
    private val driverVerificationStatusRef = AtomicReference("pending")
    private val driverAppStatusRef = AtomicReference("submitted")
    private val refreshCount = AtomicInteger(0)
    private val ridesCount = AtomicInteger(0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        lookupVerified.set(false)
        lookupReturnsExpiredOnce.set(false)
        lookupStringTrue.set(false)
        driverRoleRef.set("rider")
        driverVerificationStatusRef.set("pending")
        driverAppStatusRef.set("submitted")
        refreshCount.set(0)
        ridesCount.set(0)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val bodyText = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            val (status, payload) = when (path) {
                "/v1/accounts:lookup" -> {
                    if (lookupReturnsExpiredOnce.compareAndSet(true, false)) {
                        400 to """{"error":{"code":400,"message":"TOKEN_EXPIRED"}}"""
                    } else {
                        val isVerified = lookupVerified.get()
                        val verifiedJson = if (lookupStringTrue.get() && isVerified) {
                            """"true""""
                        } else {
                            isVerified.toString()
                        }
                        200 to """{"users":[{"localId":"u_new_1","email":"newuser@vhaya.test","emailVerified":$verifiedJson}]}"""
                    }
                }
                "/v1/token" -> {
                    val seq = refreshCount.incrementAndGet()
                    assertTrue(bodyText.contains("refresh_token="))
                    200 to """{"id_token":"fresh_id_token_$seq","refresh_token":"fresh_refresh_$seq","expires_in":"3600","user_id":"u_new_1"}"""
                }
                "/api/rides" -> {
                    ridesCount.incrementAndGet()
                    200 to """{"role":"${driverRoleRef.get()}","trips":[]}"""
                }
                "/api/driver/profile" -> {
                    200 to """
                        {
                          "profile": {
                            "userId": "u_new_1",
                            "displayName": "Thabo New",
                            "email": "newuser@vhaya.test",
                            "applicationStatus": "${driverAppStatusRef.get()}",
                            "verificationStatus": "${driverVerificationStatusRef.get()}"
                          }
                        }
                    """.trimIndent()
                }
                "/api/driver/earnings" -> {
                    200 to """{"todayMinor":0,"weekMinor":0,"completedCount":0}"""
                }
                "/api/driver/subscription" -> {
                    200 to """{"subscription":null}"""
                }
                "/api/wallet", "/api/support/cases" -> {
                    200 to """{"wallet":{"balanceMinor":0},"transactions":[],"cases":[]}"""
                }
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
        runCatching { VerificationSyncScheduler.cancelVerificationSync(context) }
        server.stop(0)
        db.close()
    }

    private fun newApiClient(): VhayaApiClient = VhayaApiClient(
        firebaseAuthBaseUrl = "http://127.0.0.1:$port",
        firebaseTokenBaseUrl = "http://127.0.0.1:$port"
    )

    private fun seedPendingSession(
        expiresAtEpochMs: Long = System.currentTimeMillis() + 3_600_000L
    ): SessionStore {
        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key"
        )
        store.saveSession(
            UserSession(
                userId = "u_new_1",
                displayName = "Thabo New",
                email = "newuser@vhaya.test",
                role = RideRole.RIDER,
                emailVerified = false,
                idToken = "initial_id_token",
                refreshToken = "initial_refresh_token",
                expiresAtEpochMs = expiresAtEpochMs
            )
        )
        return store
    }

    private fun seedAuthenticatedSession(): SessionStore {
        val store = SessionStore(
            context = context,
            apiBaseUrlOverride = "http://127.0.0.1:$port",
            firebaseApiKeyOverride = "test-firebase-key"
        )
        store.saveSession(
            UserSession(
                userId = "u_new_1",
                displayName = "Thabo New",
                email = "newuser@vhaya.test",
                role = RideRole.RIDER,
                emailVerified = true,
                idToken = "valid_id_token",
                refreshToken = "valid_refresh_token",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        return store
    }

    @Test
    fun `lookupEmailVerificationStatus parses boolean string true and expired token`() = runBlocking {
        val client = newApiClient()

        lookupVerified.set(false)
        assertEquals(
            VhayaApiClient.EmailVerificationLookup.Verified(false),
            client.lookupEmailVerificationStatus("k", "tok")
        )

        lookupVerified.set(true)
        lookupStringTrue.set(true)
        assertEquals(
            VhayaApiClient.EmailVerificationLookup.Verified(true),
            client.lookupEmailVerificationStatus("k", "tok")
        )

        lookupReturnsExpiredOnce.set(true)
        assertEquals(
            VhayaApiClient.EmailVerificationLookup.TokenExpired,
            client.lookupEmailVerificationStatus("k", "tok")
        )
    }

    @Test
    fun `checkEmailVerified refreshes expired token and immediately transitions to Authenticated with fresh JWT`() = runBlocking {
        val store = seedPendingSession(expiresAtEpochMs = System.currentTimeMillis() - 10_000L)
        val repo = WayfareRepository(context, db, store, newApiClient())
        assertTrue(repo.authState.value is AuthState.EmailVerificationPending)

        lookupVerified.set(true)
        val result = repo.checkEmailVerified()
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow())

        val auth = repo.authState.value as? AuthState.Authenticated
            ?: throw AssertionError("Expected AuthState.Authenticated after verification")
        assertTrue(auth.session.emailVerified)
        assertTrue((auth.session.idToken ?: "").startsWith("fresh_id_token_"))
        assertTrue(refreshCount.get() >= 1)
    }

    @Test
    fun `checkEmailVerified recovers from TOKEN_EXPIRED lookup response automatically`() = runBlocking {
        val store = seedPendingSession()
        val repo = WayfareRepository(context, db, store, newApiClient())

        lookupReturnsExpiredOnce.set(true)
        lookupVerified.set(true)

        val result = repo.checkEmailVerified()
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow())
        assertTrue(repo.authState.value is AuthState.Authenticated)
        assertEquals(2, refreshCount.get()) // 1 to retry lookup, 1 to mint fresh JWT claim
    }

    @Test
    fun `onAppResumed automatically detects verification without showing busy flicker when unverified`() {
        val store = seedPendingSession()
        val repo = WayfareRepository(context, db, store, newApiClient())
        val vm = WayfareViewModel(repo)

        // First resume while still unverified: silent check must NOT show error or flash notice
        lookupVerified.set(false)
        vm.onAppResumed()
        Thread.sleep(200)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(repo.authState.value is AuthState.EmailVerificationPending)
        assertFalse(vm.uiState.value.authBusy)
        assertNull(vm.uiState.value.authNotice)
        assertNull(vm.uiState.value.authError)

        // Now user verifies email on their phone and switches back to Vhaya -> onAppResumed unlocks immediately
        lookupVerified.set(true)
        vm.onAppResumed()

        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline && repo.authState.value !is AuthState.Authenticated) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val auth = repo.authState.value as? AuthState.Authenticated
            ?: throw AssertionError("Expected automatic transition to Authenticated on resume")
        assertTrue(auth.session.emailVerified)
        assertFalse(vm.uiState.value.authBusy)
    }

    @Test
    fun `VerificationSyncScheduler enqueues periodic WorkManager job and worker detects email then driver approval`() = runBlocking {
        val store = seedPendingSession()
        val repo = WayfareRepository(context, db, store, newApiClient())
        repo.syncVerificationWorkSchedule(triggerImmediateCheck = true)

        val workManager = WorkManager.getInstance(context)
        val periodicInfos = workManager
            .getWorkInfosForUniqueWork(VerificationSyncScheduler.UNIQUE_PERIODIC_VERIFICATION_WORK)
            .get(3, TimeUnit.SECONDS)
        assertTrue(periodicInfos.isNotEmpty())
        assertTrue(
            periodicInfos.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            }
        )

        val emittedEvents = mutableListOf<AccountVerificationChange>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.accountVerificationChanges.collect { emittedEvents += it }
        }
        try {
            // 1) User verifies email while app is closed -> WorkManager worker detects EmailVerified + keeps periodic sync since driver app is still pending
            lookupVerified.set(true)
            val emailWorkerResult = AccountVerificationWorker.executeVerificationSync(repo, context)
            assertEquals(ListenableWorker.Result.success(), emailWorkerResult)
            assertTrue(repo.authState.value is AuthState.Authenticated)
            assertTrue(emittedEvents.any { it is AccountVerificationChange.EmailVerified })

            // 2) Admin approves driver application -> worker detects DriverVerificationUpdated + cancels periodic sync
            driverRoleRef.set("driver")
            driverVerificationStatusRef.set("approved")
            driverAppStatusRef.set("approved")

            val driverWorkerResult = AccountVerificationWorker.executeVerificationSync(repo, context)
            assertEquals(ListenableWorker.Result.success(), driverWorkerResult)
            assertTrue(
                emittedEvents.any {
                    it is AccountVerificationChange.DriverVerificationUpdated &&
                        it.verificationStatus == "approved" &&
                        it.role == RideRole.DRIVER
                }
            )

            val afterCancel = workManager
                .getWorkInfosForUniqueWork(VerificationSyncScheduler.UNIQUE_PERIODIC_VERIFICATION_WORK)
                .get(3, TimeUnit.SECONDS)
            assertTrue(afterCancel.all { it.state == WorkInfo.State.CANCELLED || it.state.isFinished })
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `WayfareViewModel triggers immediate profile and verification load upon authenticated init`() {
        driverRoleRef.set("driver")
        driverVerificationStatusRef.set("approved")
        driverAppStatusRef.set("approved")

        val store = seedAuthenticatedSession()
        val repo = WayfareRepository(context, db, store, newApiClient())
        val vm = WayfareViewModel(repo)

        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline && !vm.uiState.value.apiConnected) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(vm.uiState.value.apiConnected)
        assertEquals(RideRole.DRIVER, vm.uiState.value.activeRole)
        assertEquals("approved", vm.uiState.value.currentDriverApp?.verificationStatus)
    }
}
