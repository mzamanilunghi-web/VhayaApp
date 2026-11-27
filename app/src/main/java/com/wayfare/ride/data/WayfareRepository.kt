package com.wayfare.ride.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.R
import com.wayfare.ride.domain.AccountVerificationChange
import com.wayfare.ride.domain.ActivityLogEntry
import com.wayfare.ride.domain.AdminOverviewSummary
import com.wayfare.ride.domain.AppLanguage
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.ComplianceStatus
import com.wayfare.ride.domain.DriverApplication
import com.wayfare.ride.domain.DriverBankDetails
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.DriverDocumentType
import com.wayfare.ride.domain.DriverEarningsSummary
import com.wayfare.ride.domain.DriverPayoutRecord
import com.wayfare.ride.domain.DriverPocketRoute
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.DriverRiderRating
import com.wayfare.ride.domain.DriverSubscriptionSummary
import com.wayfare.ride.domain.HazardType
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NearbyPlaceCategory
import com.wayfare.ride.domain.OfflineCorridorPack
import com.wayfare.ride.domain.OfflineCorridorPackStatus
import com.wayfare.ride.domain.PocketItem
import com.wayfare.ride.domain.PocketItemSize
import com.wayfare.ride.domain.PocketItemStatus
import com.wayfare.ride.domain.PocketItemType
import com.wayfare.ride.domain.RoadHazardPin
import com.wayfare.ride.domain.OfflineRideActionPayload
import com.wayfare.ride.domain.OfflineRideCreatePayload
import com.wayfare.ride.domain.PaymentInitOutcome
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PaymentVerificationResult
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.domain.PayoutBatchRecord
import com.wayfare.ride.domain.PendingOfflineAction
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.PolylineCodec
import com.wayfare.ride.domain.PopiaDataExporter
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.TrustedCommunityDriver
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.WalletSummary
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.service.FareEstimationService
import com.wayfare.ride.service.VerificationSyncScheduler
import com.wayfare.ride.ui.theme.AppThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WayfareRepository(
    private val context: Context,
    private val db: WayfareDatabase,
    private val sessionStore: SessionStore,
    private val apiClient: VhayaApiClient
) {
    private val tripDao = db.tripDao()
    private val savedDao = db.savedDestinationDao()
    private val contactDao = db.trustedContactDao()
    private val walletDao = db.walletDao()
    private val driverDao = db.driverApplicationDao()
    private val supportDao = db.supportCaseDao()
    private val payoutDao = db.payoutDao()
    private val userDao = db.userAccountDao()
    private val payoutBatchDao = db.payoutBatchDao()
    private val activityLogDao = db.activityLogDao()
    private val cachedRouteDao = db.cachedRouteDao()
    private val pendingSyncDao = db.pendingRideSyncDao()
    private val cachedPlaceDao = db.cachedPlaceDao()
    private val roadHazardDao = db.roadHazardDao()

    private val repoJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val syncCoordinator = OfflineSyncCoordinator(
        context = context,
        cachedRouteDao = cachedRouteDao,
        cachedPlaceDao = cachedPlaceDao,
        json = repoJson
    )
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tokenRefreshMutex = Mutex()
    private val offlineQueueMutex = Mutex()
    private val emailVerifyMutex = Mutex()

    private val _apiBaseUrl = MutableStateFlow(sessionStore.loadApiBaseUrl())
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _themeMode = MutableStateFlow(sessionStore.loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _lowDataMode = MutableStateFlow(sessionStore.loadLowDataMode())
    val lowDataMode: StateFlow<Boolean> = _lowDataMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(sessionStore.loadAppLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _notificationSoundStyle = MutableStateFlow(sessionStore.loadNotificationSoundStyle())
    val notificationSoundStyle: StateFlow<com.wayfare.ride.audio.NotificationSoundStyle> =
        _notificationSoundStyle.asStateFlow()

    private val _privacyPreferences = MutableStateFlow(sessionStore.loadPrivacyPreferences())
    val privacyPreferences: StateFlow<PrivacyPreferences> = _privacyPreferences.asStateFlow()

    private val _pendingOfflineCount = MutableStateFlow(sessionStore.loadOfflineQueue().size)
    val pendingOfflineCount: StateFlow<Int> = _pendingOfflineCount.asStateFlow()

    private val _isNetworkConnected = MutableStateFlow(checkCurrentNetworkConnected())
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _declinedDriverTripIds = MutableStateFlow(sessionStore.loadDeclinedTripIds())
    val declinedDriverTripIds: StateFlow<Set<String>> = _declinedDriverTripIds.asStateFlow()

    private val _driverBankDetails = MutableStateFlow(sessionStore.loadDriverBankDetails())
    val driverBankDetails: StateFlow<DriverBankDetails?> = _driverBankDetails.asStateFlow()

    private val _driverRiderRatings = MutableStateFlow(sessionStore.loadDriverRiderRatings())
    val driverRiderRatings: StateFlow<Map<String, DriverRiderRating>> = _driverRiderRatings.asStateFlow()

    private val _driverMinRatePerKmZar = MutableStateFlow(sessionStore.loadDriverMinRatePerKmZar())
    val driverMinRatePerKmZar: StateFlow<Double> = _driverMinRatePerKmZar.asStateFlow()

    private val _trustedDrivers = MutableStateFlow(sessionStore.loadTrustedDrivers())
    val trustedDrivers: StateFlow<List<TrustedCommunityDriver>> = _trustedDrivers.asStateFlow()

    private val _driverCorridorTarget = MutableStateFlow(
        sessionStore.loadDriverCorridorTarget()?.takeUnless { it.isExpired() }
    )
    val driverCorridorTarget: StateFlow<DriverCorridorTarget?> = _driverCorridorTarget.asStateFlow()

    private val _recentGtwTargets = MutableStateFlow(sessionStore.loadRecentGtwTargets())
    val recentGtwTargets: StateFlow<List<DriverCorridorTarget>> = _recentGtwTargets.asStateFlow()

    private val _driverQueuedReturnTripId = MutableStateFlow(sessionStore.loadDriverQueuedReturnTripId())
    val driverQueuedReturnTripId: StateFlow<String?> = _driverQueuedReturnTripId.asStateFlow()

    private val _offlineTilePacks = MutableStateFlow(sessionStore.loadOfflineTilePacks())
    val offlineTilePacks: StateFlow<List<OfflineCorridorPackStatus>> = _offlineTilePacks.asStateFlow()

    private val _driverVoiceNavEnabled = MutableStateFlow(sessionStore.loadDriverVoiceNavEnabled())
    val driverVoiceNavEnabled: StateFlow<Boolean> = _driverVoiceNavEnabled.asStateFlow()

    private val _driverHeadingUpEnabled = MutableStateFlow(sessionStore.loadDriverHeadingUpEnabled())
    val driverHeadingUpEnabled: StateFlow<Boolean> = _driverHeadingUpEnabled.asStateFlow()

    private val _pocketRoutes = MutableStateFlow(sessionStore.loadPocketRoutes())
    val pocketRoutes: StateFlow<List<DriverPocketRoute>> = _pocketRoutes.asStateFlow()

    private val _pocketItems = MutableStateFlow(sessionStore.loadPocketItems())
    val pocketItems: StateFlow<List<PocketItem>> = _pocketItems.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _appResumedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val appResumedEvents: SharedFlow<Unit> = _appResumedEvents.asSharedFlow()

    private val _accountVerificationChanges = MutableSharedFlow<AccountVerificationChange>(extraBufferCapacity = 8)
    val accountVerificationChanges: SharedFlow<AccountVerificationChange> = _accountVerificationChanges.asSharedFlow()

    @Volatile
    private var observedVerificationUserId: String? = null
    @Volatile
    private var observedDriverVerificationStatus: String? = null
    @Volatile
    private var observedDriverRole: RideRole? = null

    private val _firebaseApiKey = MutableStateFlow(sessionStore.loadFirebaseApiKey())
    @Volatile
    private var discoveredGoogleWebClientId: String? = null

    private val _authState = MutableStateFlow(resolveInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _walletBalanceMinor = MutableStateFlow(0L)
    val walletBalanceMinor: StateFlow<Long> = _walletBalanceMinor.asStateFlow()

    private val _driverEarnings = MutableStateFlow(DriverEarningsSummary())
    val driverEarnings: StateFlow<DriverEarningsSummary> = _driverEarnings.asStateFlow()

    private val _driverSubscription = MutableStateFlow<DriverSubscriptionSummary?>(null)
    val driverSubscription: StateFlow<DriverSubscriptionSummary?> = _driverSubscription.asStateFlow()

    private val _adminOverview = MutableStateFlow(AdminOverviewSummary())
    val adminOverview: StateFlow<AdminOverviewSummary> = _adminOverview.asStateFlow()

    private val _placeSuggestions = MutableStateFlow<List<PlaceSearchSuggestion>>(emptyList())
    val placeSuggestions: StateFlow<List<PlaceSearchSuggestion>> = _placeSuggestions.asStateFlow()

    private val _searchingPlaces = MutableStateFlow(false)
    val searchingPlaces: StateFlow<Boolean> = _searchingPlaces.asStateFlow()
    private val placeSearchGeneration = AtomicLong(0L)

    private val _nearbyPlaces = MutableStateFlow<List<PlaceSearchSuggestion>>(emptyList())
    val nearbyPlaces: StateFlow<List<PlaceSearchSuggestion>> = _nearbyPlaces.asStateFlow()

    private val _nearbyPlacesLoading = MutableStateFlow(false)
    val nearbyPlacesLoading: StateFlow<Boolean> = _nearbyPlacesLoading.asStateFlow()

    private val _nearbyPlacesError = MutableStateFlow<String?>(null)
    val nearbyPlacesError: StateFlow<String?> = _nearbyPlacesError.asStateFlow()

    private val _selectedNearbyCategory = MutableStateFlow(NearbyPlaceCategory.ALL)
    val selectedNearbyCategory: StateFlow<NearbyPlaceCategory> = _selectedNearbyCategory.asStateFlow()
    private val nearbyPlacesGeneration = AtomicLong(0L)

    val trips: Flow<List<RideTrip>> = tripDao.observeAll().map { list -> list.map { it.toDomain() } }
    val pendingSyncOperations: Flow<List<PendingRideSyncEntity>> = pendingSyncDao.observeAll()
    val savedDestinations: Flow<List<SavedDestination>> = savedDao.observeAll().map { list -> list.map { it.toDomain() } }
    val trustedContacts: Flow<List<TrustedContact>> = contactDao.observeAll().map { list -> list.map { it.toDomain() } }
    val walletTransactions: Flow<List<WalletTransaction>> = walletDao.observeAll().map { list -> list.map { it.toDomain() } }
    val driverApplications: Flow<List<DriverApplication>> = driverDao.observeAll().map { list -> list.map { it.toDomain() } }
    val supportCases: Flow<List<SupportCase>> = supportDao.observeAll().map { list -> list.map { it.toDomain() } }
    val payoutRecords: Flow<List<DriverPayoutRecord>> = payoutDao.observeAll().map { list -> list.map { it.toDomain() } }
    val userAccounts: Flow<List<UserAccountSummary>> = userDao.observeAll().map { list -> list.map { it.toDomain() } }
    val payoutBatches: Flow<List<PayoutBatchRecord>> = payoutBatchDao.observeAll().map { list -> list.map { it.toDomain() } }
    val activityLogs: Flow<List<ActivityLogEntry>> = activityLogDao.observeAll().map { list -> list.map { it.toDomain() } }
    val roadHazards: Flow<List<RoadHazardPin>> = roadHazardDao.observeAll().map { list ->
        val now = System.currentTimeMillis()
        list.filter { it.expiresAtMs > now }.map { it.toDomain() }
    }

    init {
        repoScope.launch {
            migrateLegacyOfflineQueueIfNeeded()
            roadHazardDao.deleteExpired(System.currentTimeMillis())
            val cachedDriver = driverDao.observeAll().firstOrNull()?.firstOrNull()?.toDomain()
            syncVerificationWorkSchedule(
                triggerImmediateCheck = _authState.value !is AuthState.Unauthenticated,
                driverApp = cachedDriver
            )
            pendingSyncDao.observePendingCount().collect { count ->
                _pendingOfflineCount.value = count
            }
        }
        registerNetworkConnectivityCallback()
    }

    private fun checkCurrentNetworkConnected(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching true
            val active = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    private fun registerNetworkConnectivityCallback() {
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkConnected.value = true
                    repoScope.launch {
                        if (_authState.value is AuthState.Authenticated && _apiBaseUrl.value.isNotBlank()) {
                            flushOfflineQueue(force = true)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _isNetworkConnected.value = checkCurrentNetworkConnected()
                }
            })
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        sessionStore.saveThemeMode(mode)
        _themeMode.value = mode
    }

    fun setLowDataMode(enabled: Boolean) {
        sessionStore.saveLowDataMode(enabled)
        _lowDataMode.value = enabled
    }

    fun setAppLanguage(language: AppLanguage) {
        sessionStore.saveAppLanguage(language)
        _appLanguage.value = language
    }

    fun setNotificationSoundStyle(style: com.wayfare.ride.audio.NotificationSoundStyle) {
        sessionStore.saveNotificationSoundStyle(style)
        _notificationSoundStyle.value = style
        com.wayfare.ride.notifications.RideNotificationHelper.ensureChannels(context)
    }

    fun updatePrivacyPreferences(updated: PrivacyPreferences) {
        sessionStore.savePrivacyPreferences(updated)
        _privacyPreferences.value = updated
    }

    fun hasRequestedLocationPermission(): Boolean = sessionStore.hasRequestedLocationPermission()
    fun markLocationPermissionRequested() = sessionStore.markLocationPermissionRequested()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun notifyAppResumed() {
        _isAppInForeground.value = true
        _appResumedEvents.tryEmit(Unit)
    }

    fun declineDriverRequest(tripId: String) {
        if (tripId.isBlank()) return
        val updated = (_declinedDriverTripIds.value + tripId).toList().takeLast(50).toSet()
        sessionStore.saveDeclinedTripIds(updated)
        _declinedDriverTripIds.value = updated
    }

    fun setDriverMinRatePerKmZar(rateZar: Double) {
        val normalized = rateZar.coerceAtLeast(0.0)
        sessionStore.saveDriverMinRatePerKmZar(normalized)
        _driverMinRatePerKmZar.value = normalized
    }

    fun setDriverQueuedReturnTripId(tripId: String?) {
        val cleaned = tripId?.trim()?.takeIf { it.isNotEmpty() }
        sessionStore.saveDriverQueuedReturnTripId(cleaned)
        _driverQueuedReturnTripId.value = cleaned
    }

    fun setDriverCorridorTarget(target: DriverCorridorTarget?) {
        val cleaned = normalizeCorridorTarget(target)
        sessionStore.saveDriverCorridorTarget(cleaned)
        _driverCorridorTarget.value = cleaned
        if (cleaned != null) {
            _recentGtwTargets.value = sessionStore.saveRecentGtwTarget(cleaned)
        }
    }

    suspend fun syncDriverCorridorTarget(target: DriverCorridorTarget?): DriverCorridorTarget? {
        val cleaned = normalizeCorridorTarget(target)
        sessionStore.saveDriverCorridorTarget(cleaned)
        _driverCorridorTarget.value = cleaned
        if (cleaned != null) {
            _recentGtwTargets.value = sessionStore.saveRecentGtwTarget(cleaned)
        }
        if (_authState.value is AuthState.Authenticated && _apiBaseUrl.value.trim().isNotBlank()) {
            withValidToken { apiUrl, token, _ ->
                apiClient.saveDriverGtwTarget(apiUrl, token, cleaned)
                    .onSuccess { outcome ->
                        if (outcome is VhayaApiClient.GtwSyncOutcome.Supported && _driverCorridorTarget.value == cleaned) {
                            val synced = outcome.target?.let {
                                it.copy(
                                    arriveByEpochMs = it.arriveByEpochMs ?: cleaned?.arriveByEpochMs,
                                    expiresAtMs = it.expiresAtMs ?: cleaned?.expiresAtMs
                                )
                            }
                            sessionStore.saveDriverCorridorTarget(synced)
                            _driverCorridorTarget.value = synced
                        }
                    }
                Result.success(Unit)
            }
        }
        return _driverCorridorTarget.value
    }

    fun clearExpiredGtwTargetIfNeeded(nowMs: Long = System.currentTimeMillis()): DriverCorridorTarget? {
        val current = _driverCorridorTarget.value ?: return null
        if (!current.isExpired(nowMs)) return null
        sessionStore.saveDriverCorridorTarget(null)
        _driverCorridorTarget.value = null
        return current
    }

    private fun normalizeCorridorTarget(target: DriverCorridorTarget?): DriverCorridorTarget? {
        return target?.let {
            it.copy(
                label = it.label.trim().ifBlank { "GTW Destination" },
                maxDetourKm = it.maxDetourKm.coerceIn(1.5, 25.0)
            )
        }
    }

    fun setDriverVoiceNavEnabled(enabled: Boolean) {
        sessionStore.saveDriverVoiceNavEnabled(enabled)
        _driverVoiceNavEnabled.value = enabled
    }

    fun setDriverHeadingUpEnabled(enabled: Boolean) {
        sessionStore.saveDriverHeadingUpEnabled(enabled)
        _driverHeadingUpEnabled.value = enabled
    }

    fun createDriverPocketRoute(
        originLabel: String,
        originCoordinates: RideCoordinates,
        destinationLabel: String,
        destinationCoordinates: RideCoordinates,
        departureTimeLabel: String,
        departureTimestampMs: Long,
        totalSeats: Int = 3,
        seatPriceZar: Double = 250.0,
        totalParcelSlots: Int = 4,
        baseParcelPriceZar: Double = 120.0,
        notes: String = ""
    ): DriverPocketRoute {
        val session = (authState.value as? AuthState.Authenticated)?.session ?: sessionStore.loadSession()
        val route = DriverPocketRoute(
            id = "pocket_route_${System.currentTimeMillis()}",
            driverId = session?.userId ?: "driver_me",
            driverName = session?.displayName?.takeIf { it.isNotBlank() } ?: "Samuel Maluleke",
            driverPhone = "+27 82 491 8832",
            vehicleModel = "Toyota Hilux / Quantum",
            vehicleColor = "White",
            originLabel = originLabel,
            originCoordinates = originCoordinates,
            destinationLabel = destinationLabel,
            destinationCoordinates = destinationCoordinates,
            departureTimeLabel = departureTimeLabel,
            departureTimestampMs = departureTimestampMs,
            totalSeats = totalSeats,
            availableSeats = totalSeats,
            seatPriceZar = seatPriceZar,
            totalParcelSlots = totalParcelSlots,
            availableParcelSlots = totalParcelSlots,
            baseParcelPriceZar = baseParcelPriceZar,
            notes = notes
        )
        val updated = listOf(route) + _pocketRoutes.value.filter { it.id != route.id }
        _pocketRoutes.value = updated
        sessionStore.savePocketRoutes(updated)
        return route
    }

    fun bookParcelIntoDriverPocket(
        routeId: String,
        itemDescription: String,
        size: PocketItemSize,
        recipientName: String,
        recipientPhone: String,
        originLabel: String? = null,
        originCoordinates: RideCoordinates? = null,
        destinationLabel: String? = null,
        destinationCoordinates: RideCoordinates? = null
    ): PocketItem? {
        val route = _pocketRoutes.value.firstOrNull { it.id == routeId } ?: return null
        val session = (authState.value as? AuthState.Authenticated)?.session ?: sessionStore.loadSession()
        val item = PocketItem(
            id = "pocket_item_${System.currentTimeMillis()}",
            type = PocketItemType.PARCEL,
            senderName = session?.displayName?.takeIf { it.isNotBlank() } ?: "Rider",
            senderPhone = "+27 71 000 0000",
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            originLabel = originLabel ?: route.originLabel,
            originCoordinates = originCoordinates ?: route.originCoordinates,
            destinationLabel = destinationLabel ?: route.destinationLabel,
            destinationCoordinates = destinationCoordinates ?: route.destinationCoordinates,
            itemDescription = itemDescription,
            size = size,
            fareZar = size.baseFeeZar,
            assignedDriverRouteId = route.id,
            assignedDriverName = route.driverName,
            status = PocketItemStatus.RESERVED,
            recipientPin = (1000..9999).random().toString(),
            scheduledDepartureNote = route.departureTimeLabel
        )
        val updatedItems = listOf(item) + _pocketItems.value
        _pocketItems.value = updatedItems
        sessionStore.savePocketItems(updatedItems)

        val updatedRoutes = _pocketRoutes.value.map { r ->
            if (r.id == routeId) r.copy(availableParcelSlots = (r.availableParcelSlots - 1).coerceAtLeast(0))
            else r
        }
        _pocketRoutes.value = updatedRoutes
        sessionStore.savePocketRoutes(updatedRoutes)

        return item
    }

    fun registerRiderPocketRequest(
        originLabel: String,
        originCoordinates: RideCoordinates,
        destinationLabel: String,
        destinationCoordinates: RideCoordinates,
        itemDescription: String,
        size: PocketItemSize,
        offeredFareZar: Double = size.baseFeeZar,
        recipientName: String = "",
        recipientPhone: String = "",
        type: PocketItemType = PocketItemType.PARCEL
    ): PocketItem {
        val session = (authState.value as? AuthState.Authenticated)?.session ?: sessionStore.loadSession()
        val item = PocketItem(
            id = "pocket_item_${System.currentTimeMillis()}",
            type = type,
            senderName = session?.displayName?.takeIf { it.isNotBlank() } ?: "Rider",
            senderPhone = "+27 71 000 0000",
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            originLabel = originLabel,
            originCoordinates = originCoordinates,
            destinationLabel = destinationLabel,
            destinationCoordinates = destinationCoordinates,
            itemDescription = itemDescription,
            size = size,
            fareZar = offeredFareZar,
            status = PocketItemStatus.LISTED,
            recipientPin = (1000..9999).random().toString()
        )
        val updated = listOf(item) + _pocketItems.value
        _pocketItems.value = updated
        sessionStore.savePocketItems(updated)
        return item
    }

    fun claimPocketItemForDriver(
        itemId: String,
        driverRouteId: String? = null
    ): PocketItem? {
        val session = (authState.value as? AuthState.Authenticated)?.session ?: sessionStore.loadSession()
        var claimedItem: PocketItem? = null
        val updated = _pocketItems.value.map { item ->
            if (item.id == itemId) {
                val assigned = item.copy(
                    status = PocketItemStatus.RESERVED,
                    assignedDriverRouteId = driverRouteId,
                    assignedDriverName = session?.displayName?.takeIf { it.isNotBlank() } ?: "Driver"
                )
                claimedItem = assigned
                assigned
            } else item
        }
        _pocketItems.value = updated
        sessionStore.savePocketItems(updated)
        return claimedItem
    }

    fun updatePocketItemStatus(
        itemId: String,
        newStatus: PocketItemStatus
    ): PocketItem? {
        var updatedItem: PocketItem? = null
        val updated = _pocketItems.value.map { item ->
            if (item.id == itemId) {
                val res = item.copy(status = newStatus)
                updatedItem = res
                res
            } else item
        }
        _pocketItems.value = updated
        sessionStore.savePocketItems(updated)
        return updatedItem
    }

    fun reserveSeatInDriverPocket(
        routeId: String,
        seatCount: Int = 1,
        passengerName: String = "",
        passengerPhone: String = "",
        pickupNote: String = ""
    ): PocketItem? {
        val cleanSeats = seatCount.coerceIn(1, 4)
        val route = _pocketRoutes.value.firstOrNull { it.id == routeId } ?: return null
        if (route.availableSeats < cleanSeats) return null
        val session = (authState.value as? AuthState.Authenticated)?.session ?: sessionStore.loadSession()
        val resolvedName = passengerName.trim().ifBlank {
            session?.displayName?.takeIf { it.isNotBlank() } ?: "Vhaya Passenger"
        }
        val cleanNote = pickupNote.trim()
        val seatLabel = if (cleanSeats == 1) "1 seat reserved" else "$cleanSeats seats reserved"
        val item = PocketItem(
            id = "pocket_seat_${System.currentTimeMillis()}",
            type = PocketItemType.SEAT,
            senderName = resolvedName,
            senderPhone = passengerPhone.trim().ifBlank { "+27 71 000 0000" },
            recipientName = resolvedName,
            recipientPhone = passengerPhone.trim(),
            originLabel = route.originLabel,
            originCoordinates = route.originCoordinates,
            destinationLabel = route.destinationLabel,
            destinationCoordinates = route.destinationCoordinates,
            itemDescription = if (cleanNote.isNotBlank()) "$seatLabel · $cleanNote" else "$seatLabel with ${route.driverName}",
            size = PocketItemSize.ENVELOPE,
            fareZar = route.seatPriceZar * cleanSeats,
            assignedDriverRouteId = route.id,
            assignedDriverName = route.driverName,
            status = PocketItemStatus.RESERVED,
            recipientPin = (1000..9999).random().toString(),
            scheduledDepartureNote = route.departureTimeLabel
        )
        val updatedItems = listOf(item) + _pocketItems.value
        _pocketItems.value = updatedItems
        sessionStore.savePocketItems(updatedItems)

        val updatedRoutes = _pocketRoutes.value.map { r ->
            if (r.id == routeId) r.copy(availableSeats = (r.availableSeats - cleanSeats).coerceAtLeast(0))
            else r
        }
        _pocketRoutes.value = updatedRoutes
        sessionStore.savePocketRoutes(updatedRoutes)
        return item
    }

    fun verifyPocketItemPinAndDeliver(itemId: String, enteredPin: String): Result<PocketItem> {
        val cleanPin = enteredPin.filter { it.isDigit() }.trim()
        val target = _pocketItems.value.firstOrNull { it.id == itemId }
            ?: return Result.failure(IllegalArgumentException("Pocket item not found."))
        val expected = target.recipientPin.filter { it.isDigit() }.trim()
        if (expected.isNotBlank() && cleanPin != expected) {
            return Result.failure(IllegalArgumentException("Incorrect 4-digit PIN. Ask the recipient or sender to confirm."))
        }
        val delivered = updatePocketItemStatus(itemId, PocketItemStatus.DELIVERED)
            ?: return Result.failure(IllegalStateException("Could not mark item delivered."))
        return Result.success(delivered)
    }

    suspend fun reportRoadHazard(
        type: HazardType,
        coords: RideCoordinates,
        label: String,
        note: String?,
        reportedBy: String
    ): RoadHazardPin {
        val now = System.currentTimeMillis()
        val pin = RoadHazardPin(
            id = "hazard_${UUID.randomUUID()}",
            type = type,
            latitude = coords.latitude,
            longitude = coords.longitude,
            label = label.trim().ifBlank { LaunchPolicy.nearestLandmarkLabel(coords) },
            note = note?.trim()?.takeIf { it.isNotBlank() }?.take(120),
            reportedBy = reportedBy.trim().ifBlank { "Driver" },
            reportedAtMs = now,
            expiresAtMs = now + type.defaultTtlMs,
            confirmations = 1
        )
        withContext(Dispatchers.IO) {
            roadHazardDao.upsert(pin.toEntity())
            roadHazardDao.deleteExpired(now)
        }
        return pin
    }

    suspend fun confirmRoadHazard(hazardId: String) {
        withContext(Dispatchers.IO) {
            val existing = roadHazardDao.getById(hazardId) ?: return@withContext
            val hazardType = HazardType.fromKey(existing.type)
            val newExpiry = System.currentTimeMillis() + hazardType.defaultTtlMs
            roadHazardDao.confirmHazard(hazardId, newExpiry)
        }
    }

    suspend fun clearRoadHazard(hazardId: String) {
        withContext(Dispatchers.IO) {
            roadHazardDao.deleteById(hazardId)
        }
    }

    suspend fun downloadOfflineCorridorPack(
        pack: OfflineCorridorPack,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> }
    ): OfflineCorridorPackStatus {
        val status = OfflineMapTileCache.prefetchCorridorPack(
            context = context,
            pack = pack,
            onProgress = onProgress
        )
        // Pre-warm route geometry in Room for both corridor directions
        fetchDriverNavigationRoute(pack.origin, pack.destination)
        fetchDriverNavigationRoute(pack.destination, pack.origin)
        val updated = (_offlineTilePacks.value.filterNot { it.packId == pack.id } + status)
            .sortedByDescending { it.cachedAtMs }
        sessionStore.saveOfflineTilePacks(updated)
        _offlineTilePacks.value = updated
        return status
    }

    fun clearOfflineTileCache() {
        OfflineMapTileCache.clear(context)
        sessionStore.saveOfflineTilePacks(emptyList())
        _offlineTilePacks.value = emptyList()
    }

    fun getOfflineTileCacheStats(): Pair<Int, Long> = OfflineMapTileCache.stats(context)

    fun saveDriverBankDetails(details: DriverBankDetails) {
        val cleaned = details.copy(
            bankName = details.bankName.trim(),
            accountHolder = details.accountHolder.trim(),
            accountNumber = details.accountNumber.filter { it.isDigit() },
            branchCode = details.branchCode.filter { it.isDigit() },
            accountType = details.accountType.trim().ifBlank { "Cheque" },
            updatedAtMs = System.currentTimeMillis()
        )
        sessionStore.saveDriverBankDetails(cleaned)
        _driverBankDetails.value = cleaned
    }

    fun saveDriverRiderRating(tripId: String, stars: Int, tags: List<String>) {
        if (tripId.isBlank()) return
        val entry = DriverRiderRating(
            tripId = tripId,
            stars = stars.coerceIn(1, 5),
            tags = tags.distinct(),
            ratedAtMs = System.currentTimeMillis()
        )
        val updated = _driverRiderRatings.value + (tripId to entry)
        sessionStore.saveDriverRiderRatings(updated)
        _driverRiderRatings.value = updated
    }

    suspend fun syncDriverRiderRating(tripId: String, stars: Int, tags: List<String>) {
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        withValidToken { apiUrl, token, _ ->
            apiClient.performRideAction(
                apiUrl,
                token,
                tripId,
                buildJsonObject {
                    put("action", "rateRider")
                    put("rating", stars.coerceIn(1, 5))
                    if (cleanTags.isNotEmpty()) {
                        putJsonArray("tags") {
                            cleanTags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        }
                    }
                }
            )
        }
    }

    fun isDriverTrusted(driverId: String?): Boolean {
        val cleanId = driverId?.trim().orEmpty()
        if (cleanId.isBlank()) return false
        return _trustedDrivers.value.any { it.driverId == cleanId }
    }

    fun toggleTrustedDriver(driver: DriverProfileSnapshot): Boolean {
        val cleanId = driver.id.trim()
        if (cleanId.isBlank()) return false
        val current = _trustedDrivers.value
        val existing = current.firstOrNull { it.driverId == cleanId }
        val (updated, isNowTrusted) = if (existing != null) {
            current.filterNot { it.driverId == cleanId } to false
        } else {
            val added = TrustedCommunityDriver(
                driverId = cleanId,
                name = driver.name.trim().ifBlank { "Community driver" },
                vehicle = driver.vehicle.trim(),
                vehicleColor = driver.vehicleColor.trim(),
                registration = driver.registration.trim(),
                completedRidesTogether = 1,
                savedAtMs = System.currentTimeMillis()
            )
            (listOf(added) + current).take(30) to true
        }
        sessionStore.saveTrustedDrivers(updated)
        _trustedDrivers.value = updated
        return isNowTrusted
    }

    fun removeTrustedDriver(driverId: String) {
        val cleanId = driverId.trim()
        if (cleanId.isBlank()) return
        val updated = _trustedDrivers.value.filterNot { it.driverId == cleanId }
        sessionStore.saveTrustedDrivers(updated)
        _trustedDrivers.value = updated
    }

    suspend fun getCachedTripsSnapshot(): List<RideTrip> = withContext(Dispatchers.IO) {
        tripDao.getAll().map { it.toDomain() }
    }

    suspend fun getCachedDriverProfileSnapshot(): DriverApplication? = withContext(Dispatchers.IO) {
        driverDao.observeAll().firstOrNull()?.firstOrNull()?.toDomain()
    }

    private fun resolveInitialAuthState(): AuthState {
        val saved = sessionStore.loadSession() ?: return AuthState.Unauthenticated
        if (saved.idToken.isNullOrBlank()) {
            sessionStore.saveSession(null)
            return AuthState.Unauthenticated
        }
        return if (!saved.emailVerified) {
            AuthState.EmailVerificationPending(saved)
        } else {
            AuthState.Authenticated(saved)
        }
    }

    private fun currentSessionOrNull(): UserSession? = when (val st = _authState.value) {
        is AuthState.Authenticated -> st.session
        is AuthState.EmailVerificationPending -> st.session
        AuthState.Unauthenticated -> null
    }

    private fun resourceStringOrBlank(@StringRes resId: Int): String =
        runCatching { context.getString(resId).trim() }.getOrDefault("")

    private fun resolveEffectiveFirebaseApiKey(): String =
        _firebaseApiKey.value.trim().ifBlank { resourceStringOrBlank(R.string.google_api_key) }

    private fun firebaseAuthOrNull(): FirebaseAuth? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        Firebase.auth
    }.getOrNull()

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isCanceled) {
                cont.cancel()
            } else {
                val exception = task.exception
                if (exception != null) {
                    cont.resumeWithException(exception)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(task.result as T)
                }
            }
        }
    }

    private suspend fun ensureFreshToken(forceRefresh: Boolean = false): Result<Pair<String, UserSession>> {
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("We're having trouble connecting to Vhaya. Please try again in a moment."))
        }
        val session = (authState.value as? AuthState.Authenticated)?.session
            ?: return Result.failure(IllegalStateException("Sign in to Vhaya to continue."))
        val token = session.idToken
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Session token missing. Please sign in again."))
        }

        val now = System.currentTimeMillis()
        val needsRefresh = forceRefresh ||
            (session.expiresAtEpochMs > 0L && now >= session.expiresAtEpochMs - 60_000L)
        if (!needsRefresh) {
            return Result.success(apiUrl to session)
        }

        val fbKey = resolveEffectiveFirebaseApiKey()
        val refreshToken = session.refreshToken.orEmpty()
        val sdkUser = firebaseAuthOrNull()?.currentUser
        if ((fbKey.isBlank() || refreshToken.isBlank()) && sdkUser == null) {
            return if (forceRefresh) {
                Result.failure(IllegalStateException("Your session expired. Sign in again."))
            } else {
                Result.success(apiUrl to session)
            }
        }

        return tokenRefreshMutex.withLock {
            val latest = (authState.value as? AuthState.Authenticated)?.session ?: session
            val latestNeeds = forceRefresh ||
                (latest.expiresAtEpochMs > 0L && System.currentTimeMillis() >= latest.expiresAtEpochMs - 60_000L)
            if (!latestNeeds && !latest.idToken.isNullOrBlank()) {
                return@withLock Result.success(apiUrl to latest)
            }
            val latestRefreshToken = latest.refreshToken.orEmpty()
            val currentSdkUser = firebaseAuthOrNull()?.currentUser
            if (latestRefreshToken.isBlank() && currentSdkUser != null) {
                return@withLock try {
                    val tokenResult = currentSdkUser.getIdToken(true).awaitTask()
                    val freshJwt = tokenResult.token?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Session expired. Sign in again.")
                    val expRaw = tokenResult.expirationTimestamp
                    val expiresAtMs = when {
                        expRaw <= 0L -> System.currentTimeMillis() + 3_600_000L
                        expRaw < 10_000_000_000L -> expRaw * 1000L
                        else -> expRaw
                    }
                    val updatedSession = latest.copy(
                        idToken = freshJwt,
                        expiresAtEpochMs = expiresAtMs
                    )
                    sessionStore.saveSession(updatedSession)
                    _authState.value = AuthState.Authenticated(updatedSession)
                    Result.success(apiUrl to updatedSession)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    signOut()
                    Result.failure(IllegalStateException("Session expired. Sign in again.", e))
                }
            }
            val refreshed = apiClient.refreshIdToken(fbKey, latestRefreshToken)
            if (refreshed.isSuccess) {
                val r = refreshed.getOrThrow()
                val updatedSession = latest.copy(
                    idToken = r.idToken,
                    refreshToken = r.refreshToken,
                    expiresAtEpochMs = r.expiresAtEpochMs
                )
                sessionStore.saveSession(updatedSession)
                _authState.value = AuthState.Authenticated(updatedSession)
                Result.success(apiUrl to updatedSession)
            } else {
                val err = refreshed.exceptionOrNull()
                if (err is ApiHttpException && err.statusCode == 401) {
                    signOut()
                }
                Result.failure(err ?: IllegalStateException("Session expired. Sign in again."))
            }
        }
    }

    private suspend fun <T> withValidToken(
        block: suspend (apiUrl: String, token: String, session: UserSession) -> Result<T>
    ): Result<T> {
        val auth = ensureFreshToken(forceRefresh = false)
        if (auth.isFailure) return Result.failure(auth.exceptionOrNull()!!)
        val (apiUrl, session) = auth.getOrThrow()
        val first = block(apiUrl, session.idToken.orEmpty(), session)
        val err = first.exceptionOrNull()
        if (err is ApiHttpException && err.statusCode == 401) {
            val retryAuth = ensureFreshToken(forceRefresh = true)
            if (retryAuth.isFailure) return Result.failure(retryAuth.exceptionOrNull()!!)
            val (retryUrl, retrySession) = retryAuth.getOrThrow()
            return block(retryUrl, retrySession.idToken.orEmpty(), retrySession)
        }
        return first
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<UserSession> {
        val cleanEmail = email.trim().lowercase()
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        val fbKey = resolveEffectiveFirebaseApiKey()

        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("We're having trouble connecting to Vhaya. Please try again in a moment."))
        }
        if (fbKey.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        if (cleanEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your email address and password."))
        }

        val authRes = apiClient.firebaseSignIn(fbKey, cleanEmail, password)
        if (authRes.isFailure) return authRes

        return completeAuthenticatedSignIn(apiUrl, authRes.getOrThrow())
    }

    fun googleWebClientId(): String =
        sessionStore.loadGoogleWebClientId()
            .ifBlank { discoveredGoogleWebClientId.orEmpty() }
            .ifBlank { resourceStringOrBlank(R.string.default_web_client_id) }

    suspend fun resolveGoogleWebClientId(): Result<String> {
        val explicit = sessionStore.loadGoogleWebClientId()
        if (explicit.isNotBlank()) return Result.success(explicit)
        val cached = discoveredGoogleWebClientId
        if (!cached.isNullOrBlank()) return Result.success(cached)
        val explicitFbKey = sessionStore.loadFirebaseApiKey()
        val bundledGoogleApiKey = resourceStringOrBlank(R.string.google_api_key)
        if (explicitFbKey.isNotBlank() && explicitFbKey != bundledGoogleApiKey) {
            return apiClient.firebaseResolveGoogleClientId(explicitFbKey).onSuccess { resolved ->
                discoveredGoogleWebClientId = resolved
            }
        }
        val bundledClientId = resourceStringOrBlank(R.string.default_web_client_id)
        if (bundledClientId.isNotBlank()) {
            discoveredGoogleWebClientId = bundledClientId
            return Result.success(bundledClientId)
        }
        val fallbackFbKey = resolveEffectiveFirebaseApiKey()
        if (fallbackFbKey.isBlank()) {
            return Result.failure(
                IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly.")
            )
        }
        return apiClient.firebaseResolveGoogleClientId(fallbackFbKey).onSuccess { resolved ->
            discoveredGoogleWebClientId = resolved
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<UserSession> {
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google sign-in did not return an ID token."))
        }

        val explicitFbKey = sessionStore.loadFirebaseApiKey()
        val bundledGoogleApiKey = resourceStringOrBlank(R.string.google_api_key)
        val useExternalFirebaseRest =
            explicitFbKey.isNotBlank() && explicitFbKey != bundledGoogleApiKey

        val authRes = if (useExternalFirebaseRest) {
            apiClient.firebaseSignInWithGoogleIdToken(explicitFbKey, idToken)
        } else {
            signInGoogleWithFirebaseSdk(idToken)
        }
        if (authRes.isFailure) return authRes

        if (apiUrl.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Signed in with Firebase, but VHAYA_API_BASE_URL is not configured yet to connect to the Vhaya backend API."
                )
            )
        }

        return completeAuthenticatedSignIn(apiUrl, authRes.getOrThrow())
    }

    private suspend fun signInGoogleWithFirebaseSdk(idToken: String): Result<UserSession> {
        val auth = firebaseAuthOrNull()
            ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).awaitTask()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase Google sign-in did not return a user."))
            val tokenResult = firebaseUser.getIdToken(false).awaitTask()
            val firebaseJwt = tokenResult.token?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Firebase sign-in did not return an ID token."))
            val expRaw = tokenResult.expirationTimestamp
            val expiresAtMs = when {
                expRaw <= 0L -> System.currentTimeMillis() + 3_600_000L
                expRaw < 10_000_000_000L -> expRaw * 1000L
                else -> expRaw
            }
            Result.success(
                UserSession(
                    userId = firebaseUser.uid,
                    displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                        ?: firebaseUser.email?.substringBefore("@")?.ifBlank { "Vhaya User" }
                        ?: "Vhaya User",
                    email = firebaseUser.email.orEmpty(),
                    role = RideRole.RIDER,
                    emailVerified = true,
                    idToken = firebaseJwt,
                    refreshToken = null,
                    expiresAtEpochMs = expiresAtMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Firebase Google sign-in failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Firebase Google sign-in failed.", e)
            )
        }
    }

    private suspend fun completeAuthenticatedSignIn(
        apiUrl: String,
        initialSession: UserSession
    ): Result<UserSession> {
        var session = initialSession
        if (!session.emailVerified) {
            sessionStore.saveSession(session)
            _authState.value = AuthState.EmailVerificationPending(session)
            syncVerificationWorkSchedule(triggerImmediateCheck = true)
            return Result.success(session)
        }

        val token = session.idToken.orEmpty()
        val ridesRes = apiClient.fetchRidesAndRole(apiUrl, token)
        if (ridesRes.isFailure) {
            return Result.failure(ridesRes.exceptionOrNull() ?: IllegalStateException("Could not reach Vhaya backend."))
        }
        val (backendRole, backendTrips) = ridesRes.getOrThrow()
        session = session.copy(role = backendRole)
        tripDao.replaceSyncedPreservingPending(backendTrips.map { it.toEntity() })
        sessionStore.saveSession(session)
        _authState.value = AuthState.Authenticated(session)
        refreshRoleScopedData(apiUrl, token, session)
        val loadedDriver = withContext(Dispatchers.IO) { driverDao.getById(session.userId)?.toDomain() }
        syncVerificationWorkSchedule(triggerImmediateCheck = false, driverApp = loadedDriver)
        return Result.success(session)
    }

    suspend fun signUpWithEmailPassword(displayName: String, email: String, password: String): Result<UserSession> {
        val cleanName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        val fbKey = resolveEffectiveFirebaseApiKey()

        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("We're having trouble connecting to Vhaya. Please try again in a moment."))
        }
        if (fbKey.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your full name."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.substringAfter("@").contains(".")) {
            return Result.failure(IllegalArgumentException("Enter a valid email address."))
        }
        LaunchPolicy.evaluatePasswordPolicy(password).validationErrorOrNull()?.let { reason ->
            return Result.failure(IllegalArgumentException(reason))
        }

        val res = apiClient.firebaseSignUp(fbKey, cleanName, cleanEmail, password)
        res.onSuccess { session ->
            sessionStore.saveSession(session)
            _authState.value = AuthState.EmailVerificationPending(session)
            syncVerificationWorkSchedule(triggerImmediateCheck = false)
        }
        return res
    }

    suspend fun checkEmailVerified(): Result<Boolean> = emailVerifyMutex.withLock {
        if (_authState.value is AuthState.Authenticated) {
            return@withLock Result.success(true)
        }
        val pending = (_authState.value as? AuthState.EmailVerificationPending)?.session
            ?: return@withLock Result.failure(IllegalStateException("No pending email verification session."))
        val fbKey = resolveEffectiveFirebaseApiKey()
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (fbKey.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }

        var activeSession = pending
        val now = System.currentTimeMillis()
        val refreshToken = activeSession.refreshToken.orEmpty()
        val needsPreRefresh = activeSession.idToken.isNullOrBlank() ||
            (activeSession.expiresAtEpochMs > 0L && now >= activeSession.expiresAtEpochMs - 60_000L)
        if (needsPreRefresh && refreshToken.isNotBlank()) {
            apiClient.refreshIdToken(fbKey, refreshToken).onSuccess { refreshed ->
                activeSession = activeSession.copy(
                    idToken = refreshed.idToken,
                    refreshToken = refreshed.refreshToken,
                    expiresAtEpochMs = refreshed.expiresAtEpochMs
                )
                sessionStore.saveSession(activeSession)
                if (_authState.value is AuthState.EmailVerificationPending) {
                    _authState.value = AuthState.EmailVerificationPending(activeSession)
                }
            }
        }

        val activeToken = activeSession.idToken.orEmpty()
        if (activeToken.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Your session has expired. Please sign in again."))
        }

        var lookup = apiClient.lookupEmailVerificationStatus(fbKey, activeToken)
        if (lookup is VhayaApiClient.EmailVerificationLookup.TokenExpired) {
            val latestRefresh = activeSession.refreshToken.orEmpty()
            if (latestRefresh.isBlank()) {
                signOut()
                return@withLock Result.failure(IllegalStateException("Your verification session expired. Please sign in again."))
            }
            val refreshedRes = apiClient.refreshIdToken(fbKey, latestRefresh)
            if (refreshedRes.isFailure) {
                val err = refreshedRes.exceptionOrNull()
                if (err is ApiHttpException && err.statusCode == 401) {
                    signOut()
                }
                return@withLock Result.failure(
                    IllegalStateException("Your verification session expired. Please sign in again.", err)
                )
            }
            val refreshed = refreshedRes.getOrThrow()
            activeSession = activeSession.copy(
                idToken = refreshed.idToken,
                refreshToken = refreshed.refreshToken,
                expiresAtEpochMs = refreshed.expiresAtEpochMs
            )
            sessionStore.saveSession(activeSession)
            if (_authState.value is AuthState.EmailVerificationPending) {
                _authState.value = AuthState.EmailVerificationPending(activeSession)
            }
            lookup = apiClient.lookupEmailVerificationStatus(fbKey, activeSession.idToken.orEmpty())
            if (lookup is VhayaApiClient.EmailVerificationLookup.TokenExpired) {
                signOut()
                return@withLock Result.failure(IllegalStateException("Your verification session expired. Please sign in again."))
            }
        }

        return@withLock when (lookup) {
            is VhayaApiClient.EmailVerificationLookup.Failure -> Result.failure(lookup.error)
            is VhayaApiClient.EmailVerificationLookup.TokenExpired -> {
                signOut()
                Result.failure(IllegalStateException("Your verification session expired. Please sign in again."))
            }
            is VhayaApiClient.EmailVerificationLookup.Verified -> {
                if (!lookup.verified) {
                    Result.success(false)
                } else {
                    // Refresh the ID token so subsequent backend requests include email_verified=true in JWT claims.
                    val latestRefresh = activeSession.refreshToken.orEmpty()
                    if (latestRefresh.isNotBlank()) {
                        apiClient.refreshIdToken(fbKey, latestRefresh).onSuccess { refreshed ->
                            activeSession = activeSession.copy(
                                idToken = refreshed.idToken,
                                refreshToken = refreshed.refreshToken,
                                expiresAtEpochMs = refreshed.expiresAtEpochMs
                            )
                        }
                    }
                    val verifiedSession = activeSession.copy(emailVerified = true)
                    sessionStore.saveSession(verifiedSession)
                    _authState.value = AuthState.Authenticated(verifiedSession)
                    _accountVerificationChanges.tryEmit(AccountVerificationChange.EmailVerified(verifiedSession))
                    syncVerificationWorkSchedule(triggerImmediateCheck = false)
                    if (apiUrl.isNotBlank()) {
                        repoScope.launch {
                            refreshActiveSessionData()
                        }
                    }
                    Result.success(true)
                }
            }
        }
    }

    suspend fun resendVerificationEmail(): Result<Unit> = emailVerifyMutex.withLock {
        val pending = (_authState.value as? AuthState.EmailVerificationPending)?.session
            ?: return@withLock Result.failure(IllegalStateException("No pending verification session."))
        val fbKey = resolveEffectiveFirebaseApiKey()
        if (fbKey.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        var activeSession = pending
        val now = System.currentTimeMillis()
        val refreshToken = activeSession.refreshToken.orEmpty()
        val needsRefresh = activeSession.idToken.isNullOrBlank() ||
            (activeSession.expiresAtEpochMs > 0L && now >= activeSession.expiresAtEpochMs - 60_000L)
        if (needsRefresh && refreshToken.isNotBlank()) {
            apiClient.refreshIdToken(fbKey, refreshToken).onSuccess { refreshed ->
                activeSession = activeSession.copy(
                    idToken = refreshed.idToken,
                    refreshToken = refreshed.refreshToken,
                    expiresAtEpochMs = refreshed.expiresAtEpochMs
                )
                sessionStore.saveSession(activeSession)
                if (_authState.value is AuthState.EmailVerificationPending) {
                    _authState.value = AuthState.EmailVerificationPending(activeSession)
                }
            }
        }
        val token = activeSession.idToken.orEmpty()
        if (token.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Your session has expired. Please sign in again."))
        }
        val first = apiClient.sendEmailVerification(fbKey, token)
        val firstErrMsg = first.exceptionOrNull()?.message.orEmpty()
        if (first.isFailure && refreshToken.isNotBlank() &&
            (firstErrMsg.contains("TOKEN_EXPIRED", ignoreCase = true) ||
                firstErrMsg.contains("INVALID_ID_TOKEN", ignoreCase = true))
        ) {
            val refreshedRes = apiClient.refreshIdToken(fbKey, refreshToken)
            if (refreshedRes.isSuccess) {
                val refreshed = refreshedRes.getOrThrow()
                activeSession = activeSession.copy(
                    idToken = refreshed.idToken,
                    refreshToken = refreshed.refreshToken,
                    expiresAtEpochMs = refreshed.expiresAtEpochMs
                )
                sessionStore.saveSession(activeSession)
                if (_authState.value is AuthState.EmailVerificationPending) {
                    _authState.value = AuthState.EmailVerificationPending(activeSession)
                }
                return@withLock apiClient.sendEmailVerification(fbKey, refreshed.idToken)
            }
        }
        return@withLock first
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val fbKey = resolveEffectiveFirebaseApiKey()
        if (fbKey.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        if (email.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your email address first."))
        }
        return apiClient.sendPasswordReset(fbKey, email)
    }

    suspend fun signOut() {
        observedVerificationUserId = null
        observedDriverVerificationStatus = null
        observedDriverRole = null
        VerificationSyncScheduler.cancelVerificationSync(context)
        withContext(NonCancellable) {
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(800L) {
                    androidx.credentials.CredentialManager.create(context)
                        .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                }
            }.onFailure { err ->
                Log.w("Auth", "Failed to clear credential state: ${err.message}")
            }
            runCatching {
                firebaseAuthOrNull()?.signOut()
            }.onFailure { err ->
                Log.w("Auth", "Failed to sign out Firebase user: ${err.message}")
            }
        }
        sessionStore.saveSession(null)
        _authState.value = AuthState.Unauthenticated
        _walletBalanceMinor.value = 0L
        _driverEarnings.value = DriverEarningsSummary()
        _driverSubscription.value = null
        _adminOverview.value = AdminOverviewSummary()
        _placeSuggestions.value = emptyList()
        _searchingPlaces.value = false
        nearbyPlacesGeneration.incrementAndGet()
        _nearbyPlaces.value = emptyList()
        _nearbyPlacesLoading.value = false
        _nearbyPlacesError.value = null
        _selectedNearbyCategory.value = NearbyPlaceCategory.ALL
        _declinedDriverTripIds.value = emptySet()
        _driverBankDetails.value = null
        _driverRiderRatings.value = emptyMap()
        _trustedDrivers.value = emptyList()
        _driverCorridorTarget.value = null
        _driverQueuedReturnTripId.value = null
        sessionStore.saveDeclinedTripIds(emptySet())
        sessionStore.saveDriverBankDetails(null)
        sessionStore.saveDriverRiderRatings(emptyMap())
        sessionStore.saveTrustedDrivers(emptyList())
        sessionStore.saveDriverCorridorTarget(null)
        sessionStore.saveDriverQueuedReturnTripId(null)
        tripDao.clearAll()
        walletDao.clearAll()
        driverDao.clearAll()
        supportDao.clearAll()
        payoutDao.clearAll()
        userDao.clearAll()
        payoutBatchDao.clearAll()
        activityLogDao.clearAll()
    }

    suspend fun refreshActiveSessionData(): Result<Unit> {
        if (_authState.value !is AuthState.Authenticated) return Result.success(Unit)
        return withValidToken { apiUrl, token, session ->
            flushOfflineQueueInternal(apiUrl, token)
            val ridesRes = apiClient.fetchRidesAndRole(apiUrl, token)
            if (ridesRes.isFailure) {
                val err = ridesRes.exceptionOrNull()
                return@withValidToken Result.failure(IllegalStateException(friendlyApiErrorMessage(err)))
            }
            val (backendRole, backendTrips) = ridesRes.getOrThrow()
            var updatedSession = session
            if (backendRole != session.role) {
                updatedSession = session.copy(role = backendRole)
                sessionStore.saveSession(updatedSession)
                _authState.value = AuthState.Authenticated(updatedSession)
            }
            val existingById = tripDao.getAll().associateBy { it.id }
            val mergedTrips = backendTrips.map { remote ->
                val cached = existingById[remote.id]
                if (cached != null && remote.status.isActive) {
                    remote.copy(
                        pickupNote = remote.pickupNote ?: cached.pickupNote,
                        cashChangeForZar = remote.cashChangeForZar ?: cached.cashChangeForZar,
                        beneficiaryName = remote.beneficiaryName ?: cached.beneficiaryName,
                        beneficiaryPhone = remote.beneficiaryPhone ?: cached.beneficiaryPhone,
                        isSharedRide = remote.isSharedRide || cached.isSharedRide,
                        seatCount = if (remote.seatCount > 1) remote.seatCount else cached.seatCount,
                        isReturnTrip = remote.isReturnTrip || cached.isReturnTrip,
                        isReturnRequest = remote.isReturnRequest || cached.isReturnRequest,
                        includesGravelRoad = remote.includesGravelRoad || cached.includesGravelRoad
                    )
                } else {
                    remote
                }
            }
            tripDao.replaceSyncedPreservingPending(mergedTrips.map { it.toEntity() })
            val partialErr = refreshRoleScopedData(apiUrl, token, updatedSession)
            val latestDriver = withContext(Dispatchers.IO) {
                driverDao.getById(updatedSession.userId)?.toDomain()
            }
            syncVerificationWorkSchedule(triggerImmediateCheck = false, driverApp = latestDriver)
            if (partialErr != null) {
                Result.failure(IllegalStateException(partialErr))
            } else {
                Result.success(Unit)
            }
        }
    }

    fun needsBackgroundVerificationPolling(driverApp: DriverApplication? = null): Boolean {
        return when (val auth = _authState.value) {
            AuthState.Unauthenticated -> false
            is AuthState.EmailVerificationPending -> true
            is AuthState.Authenticated -> {
                if (auth.session.role == RideRole.ADMIN) return false
                val status = driverApp?.verificationStatus?.trim()?.lowercase().orEmpty()
                val appStatus = driverApp?.applicationStatus?.trim()?.lowercase().orEmpty()
                status == "pending" || appStatus == "submitted" || appStatus == "under_review"
            }
        }
    }

    fun syncVerificationWorkSchedule(
        triggerImmediateCheck: Boolean = false,
        driverApp: DriverApplication? = null
    ) {
        if (needsBackgroundVerificationPolling(driverApp)) {
            VerificationSyncScheduler.schedulePeriodicVerificationSync(context)
            if (triggerImmediateCheck) {
                VerificationSyncScheduler.scheduleImmediateVerificationCheck(context)
            }
        } else {
            VerificationSyncScheduler.cancelVerificationSync(context)
        }
    }

    suspend fun pollVerificationStatusNow(): Result<Boolean> {
        return when (val auth = _authState.value) {
            AuthState.Unauthenticated -> Result.success(false)
            is AuthState.EmailVerificationPending -> {
                val res = checkEmailVerified()
                if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
                if (res.getOrThrow() && _apiBaseUrl.value.trim().isNotBlank()) {
                    refreshActiveSessionData()
                }
                val latestDriver = (_authState.value as? AuthState.Authenticated)
                    ?.let { withContext(Dispatchers.IO) { driverDao.getById(it.session.userId)?.toDomain() } }
                val stillPending = needsBackgroundVerificationPolling(latestDriver)
                syncVerificationWorkSchedule(triggerImmediateCheck = false, driverApp = latestDriver)
                Result.success(stillPending)
            }
            is AuthState.Authenticated -> {
                val res = refreshActiveSessionData()
                val latestDriver = withContext(Dispatchers.IO) {
                    driverDao.getById(auth.session.userId)?.toDomain()
                }
                val stillPending = needsBackgroundVerificationPolling(latestDriver)
                syncVerificationWorkSchedule(triggerImmediateCheck = false, driverApp = latestDriver)
                if (res.isFailure) {
                    Result.failure(res.exceptionOrNull()!!)
                } else {
                    Result.success(stillPending)
                }
            }
        }
    }

    private fun evaluateDriverVerificationSnapshot(
        userId: String,
        role: RideRole,
        profile: DriverApplication?
    ) {
        val normalizedStatus = profile?.verificationStatus?.trim()?.lowercase()
        val normalizedAppStatus = profile?.applicationStatus?.trim()?.lowercase().orEmpty()
        if (observedVerificationUserId != userId) {
            observedVerificationUserId = userId
            observedDriverVerificationStatus = normalizedStatus
            observedDriverRole = role
            return
        }
        val prevStatus = observedDriverVerificationStatus
        val prevRole = observedDriverRole
        observedDriverVerificationStatus = normalizedStatus
        observedDriverRole = role

        val roleUpgraded = prevRole == RideRole.RIDER && role == RideRole.DRIVER
        val statusChanged = normalizedStatus != null && prevStatus != null && normalizedStatus != prevStatus
        if (roleUpgraded || (statusChanged && normalizedStatus in setOf("approved", "rejected", "needs_follow_up"))) {
            _accountVerificationChanges.tryEmit(
                AccountVerificationChange.DriverVerificationUpdated(
                    verificationStatus = normalizedStatus ?: if (role == RideRole.DRIVER) "approved" else "unverified",
                    applicationStatus = normalizedAppStatus,
                    role = role,
                    staffNote = profile?.staffNote
                )
            )
        }
    }

    private suspend fun refreshRoleScopedData(
        apiUrl: String,
        token: String,
        session: UserSession
    ): String? = coroutineScope {
        val errors = mutableListOf<String>()
        when (session.role) {
            RideRole.RIDER -> {
                val walletJob = async { apiClient.fetchWallet(apiUrl, token) }
                val supportJob = async { apiClient.fetchSupportCases(apiUrl, token) }
                val driverProfileJob = async {
                    apiClient.fetchDriverProfile(apiUrl, token, session.displayName, session.email)
                }

                walletJob.await()
                    .onSuccess { (summary, txs) ->
                        _walletBalanceMinor.value = summary.balanceMinor
                        walletDao.replaceAll(txs.map { it.toEntity() })
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load wallet") }
                supportJob.await()
                    .onSuccess { cases ->
                        supportDao.replaceAll(cases.map { it.toEntity() })
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load support cases") }
                driverProfileJob.await()
                    .onSuccess { profile ->
                        if (profile != null) {
                            driverDao.replaceAll(listOf(profile.toEntity()))
                        } else {
                            driverDao.clearAll()
                        }
                        evaluateDriverVerificationSnapshot(session.userId, session.role, profile)
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver status") }
            }
            RideRole.DRIVER -> {
                val profileJob = async {
                    apiClient.fetchDriverProfile(apiUrl, token, session.displayName, session.email)
                }
                val earningsJob = async { apiClient.fetchDriverEarnings(apiUrl, token) }
                val subscriptionJob = async { apiClient.fetchDriverSubscription(apiUrl, token) }
                val supportJob = async { apiClient.fetchSupportCases(apiUrl, token) }
                val gtwJob = async { runCatching { apiClient.fetchDriverGtwTarget(apiUrl, token) }.getOrNull() }

                profileJob.await()
                    .onSuccess { profile ->
                        if (profile != null) {
                            driverDao.replaceAll(listOf(profile.toEntity()))
                        } else {
                            driverDao.clearAll()
                        }
                        evaluateDriverVerificationSnapshot(session.userId, session.role, profile)
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver profile") }
                earningsJob.await()
                    .onSuccess { earnings ->
                        _driverEarnings.value = earnings
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load earnings") }
                subscriptionJob.await()
                    .onSuccess { sub ->
                        _driverSubscription.value = sub
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver pass status") }
                supportJob.await()
                    .onSuccess { cases ->
                        supportDao.replaceAll(cases.map { it.toEntity() })
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load support cases") }
                gtwJob.await()
                    ?.onSuccess { outcome ->
                        if (outcome is VhayaApiClient.GtwSyncOutcome.Supported) {
                            val remote = outcome.target?.takeUnless { it.isExpired() }
                            val local = _driverCorridorTarget.value
                            val shouldAdoptRemote = when {
                                local == null -> remote != null
                                local.updatedAtMs == 0L -> true
                                remote != null -> remote.updatedAtMs >= local.updatedAtMs
                                else -> false
                            }
                            if (shouldAdoptRemote) {
                                sessionStore.saveDriverCorridorTarget(remote)
                                _driverCorridorTarget.value = remote
                            }
                        }
                    }
            }
            RideRole.ADMIN -> {
                val overviewJob = async { apiClient.fetchAdminOverview(apiUrl, token) }
                val activityJob = async { apiClient.fetchAdminActivity(apiUrl, token) }
                val peopleJob = async { apiClient.fetchAdminPeople(apiUrl, token) }
                val appsJob = async { apiClient.fetchAdminDriverApplications(apiUrl, token) }
                val casesJob = async { apiClient.fetchAdminSupportCases(apiUrl, token) }
                val payoutsJob = async { apiClient.fetchAdminPayouts(apiUrl, token) }
                val batchesJob = async { apiClient.fetchAdminPayoutBatches(apiUrl, token) }

                overviewJob.await()
                    .onSuccess { _adminOverview.value = it }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load admin overview") }
                activityJob.await()
                    .onSuccess { activityLogDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load activity log") }
                peopleJob.await()
                    .onSuccess { userDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load user directory") }
                appsJob.await()
                    .onSuccess { driverDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver applications") }
                casesJob.await()
                    .onSuccess { supportDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load support queue") }
                payoutsJob.await()
                    .onSuccess { payoutDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load payouts") }
                batchesJob.await()
                    .onSuccess { payoutBatchDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load payout batches") }
            }
        }
        errors.firstOrNull()
    }

    // Places & Routing
    suspend fun loadNearbyPlaces(
        origin: RideCoordinates,
        category: NearbyPlaceCategory = _selectedNearbyCategory.value
    ): Result<List<PlaceSearchSuggestion>> {
        val requestId = nearbyPlacesGeneration.incrementAndGet()
        _selectedNearbyCategory.value = category
        _nearbyPlacesError.value = null

        val cachedNearby = syncCoordinator.loadNearbyCachedPlaces(
            origin = origin,
            category = category,
            maxRadiusMeters = 35_000.0,
            limit = 15
        )
        val curatedLocal = LaunchPolicy.nearbyCuratedOfflineFallback(
            origin = origin,
            category = category,
            maxRadiusKm = 35.0,
            limit = 12
        )
        val initialPlaces = (cachedNearby + curatedLocal)
            .distinctBy { it.label.lowercase() }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            .take(20)

        if (requestId == nearbyPlacesGeneration.get()) {
            _nearbyPlaces.value = initialPlaces
        }

        if (_lowDataMode.value && initialPlaces.isNotEmpty()) {
            if (requestId == nearbyPlacesGeneration.get()) {
                _nearbyPlacesLoading.value = false
            }
            return Result.success(initialPlaces)
        }

        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (requestId == nearbyPlacesGeneration.get()) {
            _nearbyPlacesLoading.value = true
        }
        return try {
            val token = currentSessionOrNull()?.idToken
            val res = apiClient.searchNearbyPlaces(
                baseUrl = apiUrl,
                token = token,
                coordinates = origin,
                category = category,
                radiusMeters = 15_000
            )
            val remoteList = res.getOrNull()
            if (remoteList != null) {
                if (remoteList.isNotEmpty()) {
                    syncCoordinator.cachePlaceSuggestions("nearby:${category.key}", remoteList)
                }
                val merged = (remoteList + initialPlaces)
                    .map { place ->
                        val resolvedCat = LaunchPolicy.inferPlaceCategory(place.label, place.category)
                        val distM = place.distanceMeters
                            ?: LaunchPolicy.distanceMeters(origin, place.coordinates)
                        place.copy(category = resolvedCat, distanceMeters = distM)
                    }
                    .filter { LaunchPolicy.matchesNearbyCategory(it, category) }
                    .distinctBy { it.label.substringBefore(" ·").trim().lowercase() }
                    .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                    .take(25)
                if (requestId == nearbyPlacesGeneration.get()) {
                    _nearbyPlaces.value = merged
                    _nearbyPlacesError.value = null
                }
                Result.success(merged)
            } else if (initialPlaces.isNotEmpty()) {
                if (requestId == nearbyPlacesGeneration.get()) {
                    _nearbyPlaces.value = initialPlaces
                }
                Result.success(initialPlaces)
            } else {
                val msg = friendlyApiErrorMessage(res.exceptionOrNull(), "Could not load nearby places.")
                if (requestId == nearbyPlacesGeneration.get()) {
                    _nearbyPlacesError.value = msg
                }
                res
            }
        } finally {
            if (requestId == nearbyPlacesGeneration.get()) {
                _nearbyPlacesLoading.value = false
            }
        }
    }

    suspend fun searchPlaces(query: String, proximity: RideCoordinates?): Result<List<PlaceSearchSuggestion>> {
        val requestId = placeSearchGeneration.incrementAndGet()
        val clean = query.trim()
        if (clean.length < 2) {
            if (requestId == placeSearchGeneration.get()) {
                _placeSuggestions.value = emptyList()
                _searchingPlaces.value = false
            }
            return Result.success(emptyList())
        }
        val cachedPlaceMatches = syncCoordinator.searchCachedPlaces(clean).map { place ->
            val dist = place.distanceMeters ?: proximity?.let { LaunchPolicy.distanceMeters(it, place.coordinates) }
            place.copy(
                category = LaunchPolicy.inferPlaceCategory(place.label, place.category),
                distanceMeters = dist
            )
        }
        // Only mix Polokwane/Limpopo offline landmarks if user has no GPS proximity OR is within 50 km
        val landmarkMatches = LaunchPolicy.searchOfflineLandmarks(clean, proximity).filter { place ->
            proximity == null || (place.distanceMeters ?: Double.MAX_VALUE) <= 50_000.0
        }
        val offlineMatches = (cachedPlaceMatches + landmarkMatches)
            .distinctBy { it.label.lowercase() }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            .take(10)

        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (requestId == placeSearchGeneration.get()) {
            _placeSuggestions.value = offlineMatches
            _searchingPlaces.value = true
        }
        return try {
            val token = currentSessionOrNull()?.idToken
            val res = apiClient.searchPlaces(apiUrl, token, clean, proximity)
            val remoteList = res.getOrNull()
            if (remoteList != null && remoteList.isNotEmpty()) {
                syncCoordinator.cachePlaceSuggestions(clean, remoteList)
            }
            val combined = remoteList?.let { remote ->
                (remote + offlineMatches)
                    .distinctBy { it.label.lowercase() }
                    .let { merged ->
                        if (proximity != null) {
                            merged.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                        } else {
                            merged
                        }
                    }
                    .take(12)
            }
            if (combined != null) {
                if (requestId == placeSearchGeneration.get()) {
                    _placeSuggestions.value = combined
                }
                Result.success(combined)
            } else if (offlineMatches.isNotEmpty()) {
                if (requestId == placeSearchGeneration.get()) {
                    _placeSuggestions.value = offlineMatches
                }
                Result.success(offlineMatches)
            } else {
                res
            }
        } finally {
            if (requestId == placeSearchGeneration.get()) {
                _searchingPlaces.value = false
            }
        }
    }

    fun clearPlaceSuggestions() {
        placeSearchGeneration.incrementAndGet()
        _placeSuggestions.value = emptyList()
        _searchingPlaces.value = false
    }

    suspend fun reverseGeocodeLabel(coords: RideCoordinates): Result<String> {
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (apiUrl.isNotBlank()) {
            val token = currentSessionOrNull()?.idToken
            val apiLabel = apiClient.reverseGeocode(apiUrl, token, coords)
                .getOrNull()
                ?.label
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (apiLabel != null) {
                return Result.success(apiLabel)
            }
        }
        val androidLabel = reverseGeocodeWithAndroidGeocoder(coords)
        if (!androidLabel.isNullOrBlank()) {
            return Result.success(androidLabel)
        }
        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("Location details are temporarily unavailable. Please try again shortly."))
        }
        return Result.success(LaunchPolicy.nearestLandmarkLabel(coords))
    }

    private suspend fun reverseGeocodeWithAndroidGeocoder(coords: RideCoordinates): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(coords.latitude, coords.longitude, 1) { list ->
                        if (cont.isActive) cont.resume(list)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(coords.latitude, coords.longitude, 1).orEmpty()
            }
            val addr = addresses.firstOrNull() ?: return@runCatching null
            val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { addr.featureName?.takeIf { !it.isNullOrBlank() && it != addr.countryName }.orEmpty() }
            val area = addr.subLocality?.takeIf { it.isNotBlank() }
                ?: addr.locality?.takeIf { it.isNotBlank() }
                ?: addr.subAdminArea?.takeIf { it.isNotBlank() }
            listOfNotNull(street.takeIf { it.isNotBlank() }, area)
                .distinct()
                .joinToString(", ")
                .ifBlank { addr.getAddressLine(0)?.takeIf { it.isNotBlank() } }
        }.getOrNull()
    }

    suspend fun previewRoute(
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates?,
        destCoords: RideCoordinates?
    ): Result<RideRoute> {
        if (_lowDataMode.value && pickupCoords != null && destCoords != null) {
            val lowDataCached = syncCoordinator.getCachedRoute(pickupCoords, destCoords)
            if (lowDataCached != null) {
                return Result.success(lowDataCached)
            }
        }

        val liveRes = withValidToken { apiUrl, token, _ ->
            apiClient.previewRoute(apiUrl, token, pickup, destination, pickupCoords, destCoords)
                .map { route ->
                    val decodedSize = PolylineCodec.decode(route.encodedPolyline).size
                    val origin = route.originCoordinates ?: pickupCoords
                    val dest = route.destinationCoordinates ?: destCoords
                    if (!_lowDataMode.value && decodedSize <= 2 && origin != null && dest != null) {
                        val visual = apiClient.fetchOsrmVisualRoute(origin, dest)
                        if (visual != null && PolylineCodec.decode(visual.encodedPolyline).size > decodedSize) {
                            route.copy(encodedPolyline = visual.encodedPolyline)
                        } else {
                            route
                        }
                    } else {
                        route
                    }
                }
        }
        if (liveRes.isSuccess) {
            val route = liveRes.getOrThrow()
            syncCoordinator.cacheRoute(route, pickupCoords, destCoords)
            return Result.success(route)
        }

        val err = liveRes.exceptionOrNull()
        if (pickupCoords != null && destCoords != null &&
            (isTransientNetworkFailure(err) || _apiBaseUrl.value.trim().isBlank())
        ) {
            val cached = syncCoordinator.getCachedRoute(pickupCoords, destCoords)
            if (cached != null) {
                return Result.success(cached)
            }
            return Result.success(LaunchPolicy.buildOfflineEstimatedRoute(pickupCoords, destCoords))
        }
        return liveRes
    }

    suspend fun fetchVisualRoadRoute(
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates
    ): RideRoute? = apiClient.fetchOsrmVisualRoute(pickupCoords, destCoords)

    suspend fun fetchDriverNavigationRoute(
        originCoords: RideCoordinates,
        targetCoords: RideCoordinates
    ): RideRoute? {
        val live = if (_isNetworkConnected.value) {
            apiClient.fetchOsrmVisualRoute(originCoords, targetCoords)
        } else {
            null
        }
        if (live != null) {
            syncCoordinator.cacheRoute(live, originCoords, targetCoords)
            return live
        }
        syncCoordinator.getCachedRoute(originCoords, targetCoords)?.let { return it }
        return LaunchPolicy.buildOfflineEstimatedRoute(originCoords, targetCoords)
    }

    // Rider Actions
    suspend fun createRideRequest(
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates,
        category: RideCategory,
        customOfferFare: Double?,
        pickupAt: Long?,
        paymentMethod: PaymentMethod,
        existingRoute: RideRoute?,
        pickupNote: String? = null,
        cashChangeForZar: Int? = null,
        beneficiaryName: String? = null,
        beneficiaryPhone: String? = null,
        isSharedRide: Boolean = false,
        seatCount: Int = 1,
        isReturnTrip: Boolean = false,
        isReturnRequest: Boolean = false,
        includesGravelRoad: Boolean = false
    ): Result<RideTrip> {
        val session = currentSessionOrNull()
            ?: return Result.failure(IllegalStateException("Sign in first to request a ride."))
        val clientRequestId = UUID.randomUUID().toString()
        val cleanPickupNote = pickupNote?.trim()?.takeIf { it.isNotBlank() }?.take(140)
        val cleanCashChange = cashChangeForZar?.takeIf { paymentMethod == PaymentMethod.CASH && it > 0 }
        val cleanBeneficiaryName = beneficiaryName?.trim()?.takeIf { it.isNotBlank() }?.take(80)
        val cleanBeneficiaryPhone = beneficiaryPhone?.trim()?.takeIf { it.isNotBlank() }?.take(32)
        val cleanSeatCount = if (isSharedRide) seatCount.coerceIn(1, 4) else 1

        var transientFailure: Throwable? = null
        val authRes = ensureFreshToken(forceRefresh = false)
        if (authRes.isSuccess) {
            val (apiUrl, refreshedSession) = authRes.getOrThrow()
            val token = refreshedSession.idToken.orEmpty()
            val routeResult = if (existingRoute != null && !existingRoute.quoteToken.isNullOrBlank()) {
                Result.success(existingRoute)
            } else {
                apiClient.previewRoute(apiUrl, token, pickup, destination, pickupCoords, destCoords)
            }

            if (routeResult.isSuccess) {
                val route = routeResult.getOrThrow()
                val quoteToken = route.quoteToken
                if (!quoteToken.isNullOrBlank()) {
                    val baseStandardFare = route.fareEstimate ?: FareEstimationService.calculateDistanceTimeFare(
                        distanceMeters = route.distanceMeters,
                        durationSeconds = route.durationSeconds,
                        category = RideCategory.STANDARD
                    )
                    val ruralBaseEstimate = SmartRideGuards.calculateRuralAdjustedFare(
                        baseEstimateZar = baseStandardFare,
                        category = category,
                        isSharedRide = isSharedRide,
                        seatCount = cleanSeatCount,
                        isReturnTrip = isReturnTrip,
                        isReturnRequest = isReturnRequest,
                        includesGravelRoad = includesGravelRoad,
                        isRuralArea = LaunchPolicy.isRuralOrPeriUrban(pickupCoords, destCoords),
                        distanceMeters = route.distanceMeters
                    )
                    val isOffer = (customOfferFare != null && customOfferFare != ruralBaseEstimate) ||
                        isSharedRide || isReturnTrip || isReturnRequest || includesGravelRoad
                    val pricingMode = if (isOffer) PricingMode.OFFER else PricingMode.ESTIMATE
                    val fare = (customOfferFare ?: ruralBaseEstimate).coerceAtLeast(20.0)

                    val created = apiClient.createRide(
                        baseUrl = apiUrl,
                        token = token,
                        pickup = pickup,
                        destination = destination,
                        category = category,
                        pricingMode = pricingMode,
                        fare = fare,
                        paymentMethod = paymentMethod,
                        pickupAt = pickupAt,
                        quoteToken = quoteToken,
                        pickupNote = cleanPickupNote,
                        cashChangeForZar = cleanCashChange,
                        beneficiaryName = cleanBeneficiaryName,
                        beneficiaryPhone = cleanBeneficiaryPhone,
                        isSharedRide = isSharedRide,
                        seatCount = cleanSeatCount,
                        isReturnTrip = isReturnTrip,
                        isReturnRequest = isReturnRequest,
                        includesGravelRoad = includesGravelRoad,
                        clientRequestId = clientRequestId
                    )
                    if (created.isSuccess) {
                        val trip = created.getOrThrow()
                        val enriched = trip.copy(
                            pickupNote = trip.pickupNote ?: cleanPickupNote,
                            cashChangeForZar = trip.cashChangeForZar ?: cleanCashChange,
                            beneficiaryName = trip.beneficiaryName ?: cleanBeneficiaryName,
                            beneficiaryPhone = trip.beneficiaryPhone ?: cleanBeneficiaryPhone,
                            isSharedRide = trip.isSharedRide || isSharedRide,
                            seatCount = if (trip.seatCount > 1) trip.seatCount else cleanSeatCount,
                            isReturnTrip = trip.isReturnTrip || isReturnTrip,
                            isReturnRequest = trip.isReturnRequest || isReturnRequest,
                            includesGravelRoad = trip.includesGravelRoad || includesGravelRoad,
                            syncStatus = "SYNCED",
                            clientRequestId = trip.clientRequestId ?: clientRequestId
                        )
                        tripDao.upsert(enriched.toEntity())
                        return Result.success(enriched)
                    } else {
                        val createErr = created.exceptionOrNull()
                        if (!isTransientNetworkFailure(createErr)) {
                            return Result.failure(createErr ?: IllegalStateException("Could not request ride"))
                        }
                        transientFailure = createErr
                    }
                } else {
                    transientFailure = IllegalStateException("Offline route preview without live quote token.")
                }
            } else {
                val routeErr = routeResult.exceptionOrNull()
                if (!isTransientNetworkFailure(routeErr)) {
                    return Result.failure(routeErr ?: IllegalStateException("Could not preview route."))
                }
                transientFailure = routeErr
            }
        } else {
            val authErr = authRes.exceptionOrNull()
            if (!isTransientNetworkFailure(authErr)) {
                return Result.failure(authErr ?: IllegalStateException("Authentication required."))
            }
            transientFailure = authErr
        }

        // Offline-first fallback: queue ride request in Room & emit optimistic local Trip
        val fallbackRoute = existingRoute ?: withContext(Dispatchers.IO) {
            cachedRouteDao.getByKey(CachedRouteEntity.buildCorridorKey(pickupCoords, destCoords))
                ?.toDomain(pickupCoords, destCoords)
        } ?: LaunchPolicy.buildOfflineEstimatedRoute(pickupCoords, destCoords)

        if (fallbackRoute.durationSeconds <= LaunchPolicy.MIN_RIDE_DURATION_SECONDS) {
            return Result.failure(
                IllegalArgumentException("Route must exceed 5 minutes. Choose a further pickup or destination.")
            )
        }

        val baseStandardFare = fallbackRoute.fareEstimate
            ?: FareEstimationService.calculateDistanceTimeFare(
                distanceMeters = fallbackRoute.distanceMeters,
                durationSeconds = fallbackRoute.durationSeconds,
                category = RideCategory.STANDARD
            )
        val ruralBaseEstimate = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = baseStandardFare,
            category = category,
            isSharedRide = isSharedRide,
            seatCount = cleanSeatCount,
            isReturnTrip = isReturnTrip,
            isReturnRequest = isReturnRequest,
            includesGravelRoad = includesGravelRoad,
            isRuralArea = LaunchPolicy.isRuralOrPeriUrban(pickupCoords, destCoords),
            distanceMeters = fallbackRoute.distanceMeters
        )
        val isOffer = (customOfferFare != null && customOfferFare != ruralBaseEstimate) ||
            isSharedRide || isReturnTrip || isReturnRequest || includesGravelRoad
        val pricingMode = if (isOffer) PricingMode.OFFER else PricingMode.ESTIMATE
        val fare = (customOfferFare ?: ruralBaseEstimate).coerceAtLeast(20.0)
        val nowMs = System.currentTimeMillis()
        val offlineTripId = "offline_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val optimisticTrip = RideTrip(
            id = offlineTripId,
            riderId = session.userId,
            pickup = pickup.trim(),
            destination = destination.trim(),
            category = category,
            currency = "ZAR",
            fare = fare,
            pricingMode = pricingMode,
            status = RideStatus.REQUESTED,
            paymentStatus = PaymentStatus.NONE,
            paymentMethod = paymentMethod,
            createdAt = nowMs,
            pickupAt = pickupAt,
            pickupCoordinates = pickupCoords,
            destinationCoordinates = destCoords,
            route = fallbackRoute,
            pickupNote = cleanPickupNote,
            cashChangeForZar = cleanCashChange,
            beneficiaryName = cleanBeneficiaryName,
            beneficiaryPhone = cleanBeneficiaryPhone,
            isSharedRide = isSharedRide,
            seatCount = cleanSeatCount,
            isReturnTrip = isReturnTrip,
            isReturnRequest = isReturnRequest,
            includesGravelRoad = includesGravelRoad,
            syncStatus = "PENDING_UPLOAD",
            clientRequestId = clientRequestId
        )

        val payloadJson = repoJson.encodeToString(
            OfflineRideCreatePayload(
                clientRequestId = clientRequestId,
                pickup = pickup.trim(),
                destination = destination.trim(),
                pickupCoordinates = pickupCoords,
                destinationCoordinates = destCoords,
                category = category,
                fare = fare,
                customOfferFare = customOfferFare,
                pickupAt = pickupAt,
                paymentMethod = paymentMethod,
                pickupNote = cleanPickupNote,
                cashChangeForZar = cleanCashChange,
                beneficiaryName = cleanBeneficiaryName,
                beneficiaryPhone = cleanBeneficiaryPhone,
                isSharedRide = isSharedRide,
                seatCount = cleanSeatCount,
                isReturnTrip = isReturnTrip,
                isReturnRequest = isReturnRequest,
                includesGravelRoad = includesGravelRoad
            )
        )
        val syncEntity = PendingRideSyncEntity(
            clientRequestId = clientRequestId,
            operationType = "create_ride",
            tripId = offlineTripId,
            payloadJson = payloadJson,
            syncState = "PENDING",
            retryCount = 0,
            lastError = transientFailure?.message,
            nextRetryAtMs = 0L,
            createdAtMs = nowMs
        )

        offlineQueueMutex.withLock {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    tripDao.upsert(optimisticTrip.toEntity())
                    pendingSyncDao.upsert(syncEntity)
                }
                _pendingOfflineCount.value = pendingSyncDao.countPending()
            }
        }
        syncCoordinator.scheduleBackgroundSync()
        return Result.success(optimisticTrip)
    }

    suspend fun acceptDriverOffer(tripId: String, offerId: String): Result<RideTrip> =
        performCriticalRideActionWithOfflineFallback(
            tripId = tripId,
            payload = buildJsonObject {
                put("action", "chooseOffer")
                put("offerId", offerId)
            }
        ) { current ->
            val selectedOffer = current.offers.firstOrNull { it.id == offerId }
            current.copy(
                driverId = selectedOffer?.driverId ?: current.driverId,
                driverProfile = selectedOffer?.driverProfile ?: current.driverProfile,
                fare = selectedOffer?.amount ?: current.fare,
                status = RideStatus.ACCEPTED,
                syncStatus = "PENDING_UPLOAD"
            )
        }

    suspend fun flagDriverOffer(tripId: String, offerId: String, reason: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "flagOffer")
            put("offerId", offerId)
            put("reason", reason.trim().ifBlank { "Concern flagged by requester" })
        })

    suspend fun riderConfirmArrival(tripId: String): Result<RideTrip> =
        performCriticalRideActionWithOfflineFallback(
            tripId = tripId,
            payload = buildJsonObject { put("action", "confirmArrival") }
        ) { current ->
            current.copy(
                arrivalConfirmationMethod = "rider",
                arrivalConfirmedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
        }

    suspend fun cancelTrip(
        tripId: String,
        reason: String? = null,
        waiveFee: Boolean = false
    ): Result<RideTrip> {
        val localTrip = withContext(Dispatchers.IO) { tripDao.getById(tripId) }
        if (localTrip != null && localTrip.id.startsWith("offline_")) {
            offlineQueueMutex.withLock {
                withContext(Dispatchers.IO) {
                    db.withTransaction {
                        pendingSyncDao.deleteAllForTrip(tripId)
                        tripDao.deleteById(tripId)
                    }
                    _pendingOfflineCount.value = pendingSyncDao.countPending()
                }
            }
            return Result.success(
                localTrip.toDomain().copy(
                    status = RideStatus.CANCELLED,
                    unfulfillmentReason = reason?.trim()?.takeIf { it.isNotBlank() }
                        ?: "Canceled locally before upload"
                )
            )
        }
        val cleanReason = reason?.trim()?.takeIf { it.isNotBlank() }
        val domainTrip = localTrip?.toDomain()
        val feeZar = if (!waiveFee && domainTrip != null) {
            LaunchPolicy.riderCancellationFee(domainTrip)
        } else {
            0.0
        }
        val feeMinor = kotlin.math.round(feeZar * 100.0).toLong()
        return performCriticalRideActionWithOfflineFallback(
            tripId = tripId,
            payload = buildJsonObject {
                put("action", "cancel")
                if (cleanReason != null) put("reason", cleanReason)
                if (waiveFee) put("waiveFee", true)
                if (feeMinor > 0L) {
                    put("cancellationFeeZar", feeZar)
                    put("cancellationFeeMinor", feeMinor)
                }
            },
            refreshAfterSuccess = true
        ) { current ->
            current.copy(
                status = RideStatus.CANCELLED,
                unfulfillmentReason = cleanReason ?: current.unfulfillmentReason,
                syncStatus = "PENDING_UPLOAD"
            )
        }.onSuccess { updated ->
            if (updated.syncStatus == "SYNCED") {
                refreshActiveSessionData()
            } else if (feeMinor > 0L) {
                withContext(Dispatchers.IO) {
                    walletDao.insert(
                        WalletTransactionEntity(
                            id = "fee-$tripId-${System.currentTimeMillis()}",
                            type = "cancellation_fee",
                            amountMinor = -feeMinor,
                            currency = LaunchPolicy.CURRENCY,
                            reference = tripId,
                            description = "En-route cancellation fine (${LaunchPolicy.formatZar(feeZar)})",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                _walletBalanceMinor.value = (_walletBalanceMinor.value - feeMinor).coerceAtLeast(0L)
            }
        }
    }

    suspend fun expireOpenTrip(tripId: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "cancel")
            put("reason", "Request expired — no driver matched")
        }).onSuccess { refreshActiveSessionData() }

    suspend fun rateTrip(tripId: String, stars: Int, feedbackTags: List<String> = emptyList()): Result<RideTrip> {
        val cleanTags = feedbackTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val res = performRideActionAndCache(tripId, buildJsonObject {
            put("action", "rate")
            put("rating", stars.coerceIn(1, 5))
            if (cleanTags.isNotEmpty()) {
                putJsonArray("feedbackTags") {
                    cleanTags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
        })
        if (res.isFailure && isTransientNetworkFailure(res.exceptionOrNull())) {
            enqueueOfflineAction(
                PendingOfflineAction(
                    id = "rate-$tripId-${System.currentTimeMillis()}",
                    kind = "trip_rate",
                    tripId = tripId,
                    rating = stars.coerceIn(1, 5),
                    feedbackTags = cleanTags
                )
            )
        }
        return res
    }

    suspend fun boostTripFare(tripId: String, newFareZar: Double): Result<RideTrip> {
        val cleanFare = kotlin.math.round(newFareZar.coerceAtLeast(20.0) * 100.0) / 100.0
        return performRideActionAndCache(
            tripId,
            buildJsonObject {
                put("action", "boostFare")
                put("fare", cleanFare)
            }
        )
    }

    suspend fun updateTripPickupNote(tripId: String, note: String): Result<RideTrip> {
        val clean = note.trim().take(140)
        return performRideActionAndCache(
            tripId,
            buildJsonObject {
                put("action", "updatePickupNote")
                put("pickupNote", clean)
            }
        )
    }

    suspend fun withdrawDriverOffer(tripId: String, offerId: String): Result<RideTrip> {
        return performRideActionAndCache(
            tripId,
            buildJsonObject {
                put("action", "withdrawOffer")
                put("offerId", offerId)
            }
        )
    }

    suspend fun sendTripMessage(tripId: String, text: String): Result<RideTrip> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a message."))
        val res = performRideActionAndCache(tripId, buildJsonObject {
            put("action", "message")
            put("channel", "driver")
            put("message", trimmed)
        })
        if (res.isFailure && isTransientNetworkFailure(res.exceptionOrNull())) {
            enqueueOfflineAction(
                PendingOfflineAction(
                    id = "msg-${UUID.randomUUID().toString().take(8)}",
                    kind = "trip_message",
                    tripId = tripId,
                    message = trimmed
                )
            )
        }
        return res
    }

    suspend fun createSupportCase(
        trip: RideTrip,
        category: String,
        priority: String,
        message: String,
        requestRefund: Boolean = false,
        attachmentUri: Uri? = null
    ): Result<RideTrip> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Describe the issue before submitting."))

        val attachmentSummary = attachmentUri?.let { inspectAttachmentSummary(it) }
        val telemetrySummary = SmartRideGuards.buildSupportTelemetrySummary(trip)
        val composedMessage = buildString {
            if (requestRefund) {
                append("[Refund / Fare Review Requested · ${LaunchPolicy.formatZar(trip.fare)} via ${trip.paymentMethod.label}] ")
            }
            append(trimmed)
            if (!attachmentSummary.isNullOrBlank()) {
                append("\n\n[Evidence photo attached: $attachmentSummary]")
            }
            append("\n\n").append(telemetrySummary)
        }
        val effectivePriority = when {
            category == "safety" -> if (priority == "urgent") "urgent" else "high"
            requestRefund && priority == "normal" -> "high"
            priority == "urgent" -> "high"
            else -> priority
        }

        val res = performRideActionAndCache(trip.id, buildJsonObject {
            put("action", "message")
            put("channel", "support")
            put("category", category)
            put("priority", effectivePriority)
            put("message", composedMessage)
            put("evidence", SmartRideGuards.buildSupportEvidenceJson(trip))
        }).onSuccess {
            refreshActiveSessionData()
        }
        if (res.isFailure && isTransientNetworkFailure(res.exceptionOrNull())) {
            enqueueOfflineAction(
                PendingOfflineAction(
                    id = "sup-${UUID.randomUUID().toString().take(8)}",
                    kind = "support_create",
                    tripId = trip.id,
                    category = category,
                    priority = effectivePriority,
                    message = composedMessage
                )
            )
        }
        return res
    }

    suspend fun sendRiderSupportFollowUp(supportCase: SupportCase, message: String): Result<RideTrip> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a message."))
        val safePriority = if (supportCase.category == "safety") supportCase.priority else "normal"
        val res = performRideActionAndCache(supportCase.rideId, buildJsonObject {
            put("action", "message")
            put("channel", "support")
            put("category", supportCase.category)
            put("priority", safePriority)
            put("message", trimmed)
        }).onSuccess {
            refreshActiveSessionData()
        }
        if (res.isFailure && isTransientNetworkFailure(res.exceptionOrNull())) {
            enqueueOfflineAction(
                PendingOfflineAction(
                    id = "sup-rep-${UUID.randomUUID().toString().take(8)}",
                    kind = "support_reply",
                    tripId = supportCase.rideId,
                    caseId = supportCase.id,
                    category = supportCase.category,
                    priority = safePriority,
                    message = trimmed
                )
            )
        }
        return res
    }

    private suspend fun performRideActionAndCache(tripId: String, payload: JsonObject): Result<RideTrip> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.performRideAction(apiUrl, token, tripId, payload).onSuccess { trip ->
                tripDao.upsert(trip.copy(syncStatus = "SYNCED").toEntity())
            }
        }
    }

    private suspend fun performCriticalRideActionWithOfflineFallback(
        tripId: String,
        payload: JsonObject,
        refreshAfterSuccess: Boolean = false,
        optimisticTransform: (RideTrip) -> RideTrip
    ): Result<RideTrip> {
        val liveRes = performRideActionAndCache(tripId, payload)
        if (liveRes.isSuccess) return liveRes

        val err = liveRes.exceptionOrNull()
        if (!isTransientNetworkFailure(err)) {
            return liveRes
        }

        val cachedEntity = withContext(Dispatchers.IO) { tripDao.getById(tripId) } ?: return liveRes
        val optimisticTrip = optimisticTransform(cachedEntity.toDomain()).copy(syncStatus = "PENDING_UPLOAD")
        val clientRequestId = "act-${UUID.randomUUID()}"
        val actionPayload = OfflineRideActionPayload(
            clientRequestId = clientRequestId,
            tripId = tripId,
            actionJson = payload.toString(),
            refreshAfterSuccess = refreshAfterSuccess
        )
        val syncEntity = PendingRideSyncEntity(
            clientRequestId = clientRequestId,
            operationType = "ride_action",
            tripId = tripId,
            payloadJson = syncCoordinator.encodeRideActionPayload(actionPayload),
            syncState = "PENDING",
            retryCount = 0,
            lastError = err?.message,
            nextRetryAtMs = 0L,
            createdAtMs = System.currentTimeMillis()
        )
        offlineQueueMutex.withLock {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    tripDao.upsert(optimisticTrip.toEntity())
                    pendingSyncDao.upsert(syncEntity)
                }
                _pendingOfflineCount.value = pendingSyncDao.countPending()
            }
        }
        syncCoordinator.scheduleBackgroundSync()
        return Result.success(optimisticTrip)
    }

    // Payments & Wallet
    suspend fun initializeCardPayment(tripId: String): Result<PaymentInitOutcome> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.initializePayment(apiUrl, token, tripId).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun submitMockPaymentResult(reference: String, success: Boolean): Result<PaymentVerificationResult> {
        if (!BuildConfig.ALLOW_MOCK_PAYMENTS) {
            return Result.failure(IllegalStateException("Mock payments are disabled in this build."))
        }
        return withValidToken { apiUrl, token, _ ->
            apiClient.submitMockPaymentResult(apiUrl, token, reference, success).also {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun releaseCardHold(tripId: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.releasePaymentHold(apiUrl, token, tripId).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun initializeWalletTopUp(amountZar: Double): Result<PaymentInitOutcome> {
        val amountMinor = (amountZar * 100).toLong()
        if (amountMinor < 500L) {
            return Result.failure(IllegalArgumentException("Minimum top-up is R 5.00."))
        }
        return withValidToken { apiUrl, token, _ ->
            apiClient.initializeWalletTopUp(apiUrl, token, amountMinor)
        }
    }

    suspend fun verifyPaymentReference(reference: String, tripIdHint: String? = null): Result<PaymentVerificationResult> {
        return withValidToken { apiUrl, token, session ->
            when {
                reference.startsWith("wallet-topup-") -> {
                    apiClient.verifyWalletTopUp(apiUrl, token, reference).map { (result, walletSummary) ->
                        _walletBalanceMinor.value = walletSummary.balanceMinor
                        refreshActiveSessionData()
                        result
                    }
                }
                reference.startsWith("driver-subscription-") -> {
                    val sub = apiClient.fetchDriverSubscription(apiUrl, token).getOrNull()
                    _driverSubscription.value = sub
                    refreshActiveSessionData()
                    val state = when (sub?.status?.lowercase()) {
                        "active" -> PaymentVerificationState.PAID
                        "failed", "cancelled", "expired" -> PaymentVerificationState.FAILED
                        else -> PaymentVerificationState.PENDING
                    }
                    Result.success(
                        PaymentVerificationResult(
                            reference = reference,
                            state = state,
                            amountMinor = sub?.amountMinor ?: 0L,
                            message = when (state) {
                                PaymentVerificationState.PAID -> "Your Vhaya Weekly Driver Pass is now active for 7 days."
                                PaymentVerificationState.FAILED -> "Weekly Driver Pass payment was not completed."
                                else -> "Paystack is still confirming your Weekly Driver Pass payment."
                            }
                        )
                    )
                }
                else -> {
                    apiClient.verifyPayment(apiUrl, token, reference).map { res ->
                        refreshRoleScopedData(apiUrl, token, session)
                        res.copy(tripId = res.tripId ?: tripIdHint)
                    }
                }
            }
        }
    }

    // Driver Operations
    suspend fun setDriverOnline(online: Boolean): Result<Boolean> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.setDriverOnline(apiUrl, token, online).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun saveDriverApplication(updated: DriverApplication, submitForReview: Boolean): Result<DriverApplication> {
        val formattedReg = LaunchPolicy.formatSouthAfricanRegistration(updated.registration)
        val normalized = updated.copy(registration = formattedReg)
        return withValidToken { apiUrl, token, session ->
            apiClient.saveDriverApplication(
                baseUrl = apiUrl,
                token = token,
                app = normalized,
                submit = submitForReview,
                fallbackName = session.displayName,
                fallbackEmail = session.email
            ).onSuccess { saved ->
                driverDao.upsert(saved.toEntity())
                syncVerificationWorkSchedule(triggerImmediateCheck = false, driverApp = saved)
            }
        }
    }

    suspend fun uploadDriverDocument(
        draft: DriverApplication?,
        docType: DriverDocumentType,
        uri: Uri
    ): Result<DriverApplication> {
        val payloadResult = withContext(Dispatchers.IO) {
            runCatching {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected file.")
                if (rawBytes.isEmpty()) error("Selected file is empty.")

                val reportedMime = context.contentResolver.getType(uri)
                val (finalBytes, finalMime) = prepareUploadBytesAndMime(rawBytes, reportedMime)
                val fileName = resolveFileName(uri, finalMime, docType)
                Triple(fileName, finalMime, finalBytes)
            }
        }
        if (payloadResult.isFailure) return Result.failure(payloadResult.exceptionOrNull()!!)
        val (fileName, mimeType, bytes) = payloadResult.getOrThrow()

        return withValidToken { apiUrl, token, session ->
            // Best-effort draft sync before upload without blocking on partial form validation
            val localExisting = driverDao.getById(session.userId)?.toDomain()
            val remoteExisting = if (localExisting == null) {
                apiClient.fetchDriverProfile(apiUrl, token, session.displayName, session.email).getOrNull()
                    ?.also { driverDao.upsert(it.toEntity()) }
            } else {
                localExisting
            }

            var draftFailure: Throwable? = null
            val hasDraftDetails = draft != null && (
                draft.contactPhone.isNotBlank() ||
                    draft.city.isNotBlank() ||
                    draft.vehicle.isNotBlank() ||
                    draft.registration.isNotBlank()
                )
            if (remoteExisting == null || (remoteExisting.applicationStatus == "draft" && hasDraftDetails)) {
                val draftApp = (draft ?: DriverApplication(
                    userId = session.userId,
                    displayName = session.displayName,
                    email = session.email
                )).let {
                    it.copy(registration = LaunchPolicy.formatSouthAfricanRegistration(it.registration))
                }
                val saveDraft = apiClient.saveDriverApplication(
                    baseUrl = apiUrl,
                    token = token,
                    app = draftApp,
                    submit = false,
                    fallbackName = session.displayName,
                    fallbackEmail = session.email
                )
                saveDraft.onSuccess { driverDao.upsert(it.toEntity()) }
                saveDraft.onFailure { draftFailure = it }
            }

            val uploadResult = apiClient.uploadDriverDocument(
                baseUrl = apiUrl,
                token = token,
                docType = docType,
                fileName = fileName,
                contentType = mimeType,
                bytes = bytes,
                fallbackName = session.displayName,
                fallbackEmail = session.email
            )
            uploadResult.onSuccess { updatedProfile ->
                driverDao.upsert(updatedProfile.toEntity())
            }
            if (uploadResult.isFailure && remoteExisting == null && draftFailure != null) {
                val uploadErr = uploadResult.exceptionOrNull()
                if (uploadErr is ApiHttpException && uploadErr.statusCode == 401) {
                    uploadResult
                } else {
                    Result.failure(uploadErr ?: draftFailure)
                }
            } else {
                uploadResult
            }
        }
    }

    suspend fun driverSubmitOffer(trip: RideTrip, amount: Double, driverLocation: RideCoordinates?): Result<RideTrip> {
        val isAccept = amount == trip.fare
        val payload = if (isAccept) {
            buildJsonObject {
                put("action", "accept")
                if (driverLocation != null) {
                    put("location", buildJsonObject {
                        put("latitude", driverLocation.latitude)
                        put("longitude", driverLocation.longitude)
                    })
                }
            }
        } else {
            buildJsonObject {
                put("action", "counter")
                put("amount", amount.coerceAtLeast(20.0))
            }
        }
        return performCriticalRideActionWithOfflineFallback(trip.id, payload) { current ->
            if (isAccept) {
                current.copy(status = RideStatus.ACCEPTED, syncStatus = "PENDING_UPLOAD")
            } else {
                current.copy(status = RideStatus.COUNTERED, syncStatus = "PENDING_UPLOAD")
            }
        }
    }

    suspend fun queueReturnRide(trip: RideTrip, driverLocation: RideCoordinates?): Result<Boolean> {
        val acceptRes = driverSubmitOffer(trip, trip.fare, driverLocation)
        if (acceptRes.isSuccess) {
            setDriverQueuedReturnTripId(trip.id)
            return Result.success(true)
        }
        val err = acceptRes.exceptionOrNull()
        if (err is ApiHttpException && err.statusCode in setOf(400, 403, 409, 422)) {
            setDriverQueuedReturnTripId(trip.id)
            return Result.success(false)
        }
        return Result.failure(err ?: IllegalStateException("Could not queue Return Ride"))
    }

    suspend fun updateLiveDriverLocation(tripId: String, coords: RideCoordinates): Result<Unit> {
        val res = withValidToken { apiUrl, token, _ ->
            apiClient.updateDriverLocation(apiUrl, token, tripId, coords).onSuccess {
                refreshActiveSessionData()
            }
        }
        if (res.isFailure && isTransientNetworkFailure(res.exceptionOrNull())) {
            enqueueOfflineAction(
                PendingOfflineAction(
                    id = "loc-$tripId",
                    kind = "driver_location",
                    tripId = tripId,
                    latitude = coords.latitude,
                    longitude = coords.longitude
                ),
                replaceLatestLocationForTrip = true
            )
        }
        return res
    }

    suspend fun driverMarkArrived(tripId: String, driverLocation: RideCoordinates?): Result<RideTrip> {
        val liveRes = withValidToken { apiUrl, token, _ ->
            if (driverLocation != null) {
                val locRes = apiClient.updateDriverLocation(apiUrl, token, tripId, driverLocation)
                if (locRes.isFailure) return@withValidToken Result.failure(locRes.exceptionOrNull()!!)
            }
            apiClient.performRideAction(
                apiUrl,
                token,
                tripId,
                buildJsonObject { put("action", "advance") }
            ).onSuccess { trip ->
                tripDao.upsert(trip.copy(syncStatus = "SYNCED").toEntity())
            }
        }
        if (liveRes.isSuccess) return liveRes
        val err = liveRes.exceptionOrNull()
        if (!isTransientNetworkFailure(err)) return liveRes

        if (driverLocation != null) {
            enqueueOfflineAction(
                PendingOfflineAction(
                    id = "loc-$tripId",
                    kind = "driver_location",
                    tripId = tripId,
                    latitude = driverLocation.latitude,
                    longitude = driverLocation.longitude
                ),
                replaceLatestLocationForTrip = true
            )
        }
        return performCriticalRideActionWithOfflineFallback(
            tripId = tripId,
            payload = buildJsonObject { put("action", "advance") }
        ) { current ->
            current.copy(
                status = RideStatus.ARRIVED,
                driverArrivedAt = current.driverArrivedAt ?: System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
        }
    }

    suspend fun verifyPinAndStartTrip(tripId: String, enteredPin: String): Result<RideTrip> {
        val cleanPin = enteredPin.trim()
        if (cleanPin.length != 6) {
            return Result.failure(IllegalArgumentException("Enter the rider's 6-digit pickup PIN."))
        }
        val cachedPin = withContext(Dispatchers.IO) { tripDao.getById(tripId)?.tripPin }
        val payload = buildJsonObject {
            put("action", "advance")
            put("pin", cleanPin)
        }
        val liveRes = performRideActionAndCache(tripId, payload)
        if (liveRes.isSuccess) return liveRes
        val err = liveRes.exceptionOrNull()
        if (!isTransientNetworkFailure(err)) return liveRes
        if (!cachedPin.isNullOrBlank() && cachedPin != cleanPin) {
            return Result.failure(IllegalArgumentException("Incorrect 6-digit pickup PIN."))
        }
        return performCriticalRideActionWithOfflineFallback(tripId, payload) { current ->
            current.copy(status = RideStatus.IN_PROGRESS, syncStatus = "PENDING_UPLOAD")
        }
    }

    suspend fun completeTrip(tripId: String): Result<RideTrip> =
        performCriticalRideActionWithOfflineFallback(
            tripId = tripId,
            payload = buildJsonObject { put("action", "advance") },
            refreshAfterSuccess = true
        ) { current ->
            current.copy(status = RideStatus.COMPLETED, syncStatus = "PENDING_UPLOAD")
        }.onSuccess { updated ->
            if (updated.syncStatus == "SYNCED") {
                refreshActiveSessionData()
            }
        }

    suspend fun initializeDriverWeeklySubscription(amountMinor: Long = 19900L): Result<PaymentInitOutcome> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.initializeDriverSubscription(apiUrl, token, amountMinor).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun creditCashChangeToWallet(tripId: String, amountZar: Int): Result<Long> {
        if (amountZar !in 1..500) {
            return Result.failure(IllegalArgumentException("Enter a change amount between R 1 and R 500."))
        }
        val amountMinor = amountZar * 100L
        return withValidToken { apiUrl, token, _ ->
            val res = apiClient.creditCashChangeToWallet(
                baseUrl = apiUrl,
                token = token,
                tripId = tripId,
                amountMinor = amountMinor,
                note = "Cash change credited to rider Vhaya Wallet for trip $tripId"
            )
            if (res.isSuccess) {
                refreshActiveSessionData()
                Result.success(amountMinor)
            } else {
                val err = res.exceptionOrNull()
                if (err is ApiHttpException && err.statusCode in setOf(404, 405, 501)) {
                    Result.failure(
                        IllegalStateException(
                            "Cash-to-wallet change endpoint is not enabled on this server yet."
                        )
                    )
                } else {
                    Result.failure(err ?: IllegalStateException("Could not credit change to rider wallet."))
                }
            }
        }
    }

    suspend fun redeemWalletVoucher(code: String): Result<WalletSummary> {
        val cleaned = code.trim().uppercase()
        if (cleaned.length < 4) {
            return Result.failure(IllegalArgumentException("Enter a valid spaza or promo voucher code."))
        }
        return withValidToken { apiUrl, token, _ ->
            val res = apiClient.redeemWalletVoucher(apiUrl, token, cleaned)
            if (res.isSuccess) {
                val (summary, txs) = res.getOrThrow()
                _walletBalanceMinor.value = summary.balanceMinor
                if (txs.isNotEmpty()) walletDao.replaceAll(txs.map { it.toEntity() })
                refreshActiveSessionData()
                Result.success(summary)
            } else {
                val err = res.exceptionOrNull()
                if (err is ApiHttpException && err.statusCode in setOf(404, 405, 501)) {
                    Result.failure(
                        IllegalStateException(
                            "Spaza voucher redemption is not enabled on this server yet."
                        )
                    )
                } else {
                    Result.failure(err ?: IllegalStateException("Could not redeem voucher."))
                }
            }
        }
    }

    suspend fun transferWalletCredit(recipient: String, amountZar: Double, note: String? = null): Result<WalletSummary> {
        val cleanRecipient = recipient.trim()
        if (cleanRecipient.length < 5) {
            return Result.failure(IllegalArgumentException("Enter a family member's phone number or email."))
        }
        val amountMinor = (amountZar * 100).toLong()
        if (amountMinor < 500L) {
            return Result.failure(IllegalArgumentException("Minimum family credit transfer is R 5.00."))
        }
        return withValidToken { apiUrl, token, _ ->
            val res = apiClient.transferWalletCredit(apiUrl, token, cleanRecipient, amountMinor, note)
            if (res.isSuccess) {
                val (summary, txs) = res.getOrThrow()
                _walletBalanceMinor.value = summary.balanceMinor
                if (txs.isNotEmpty()) walletDao.replaceAll(txs.map { it.toEntity() })
                refreshActiveSessionData()
                Result.success(summary)
            } else {
                val err = res.exceptionOrNull()
                if (err is ApiHttpException && err.statusCode in setOf(404, 405, 501)) {
                    Result.failure(
                        IllegalStateException(
                            "Family wallet transfers are not enabled on this server yet."
                        )
                    )
                } else {
                    Result.failure(err ?: IllegalStateException("Could not transfer ride credit."))
                }
            }
        }
    }

    // Admin Actions
    suspend fun updateComplianceCheck(userId: String, docType: DriverDocumentType, status: ComplianceStatus, note: String?): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.updateComplianceCheck(apiUrl, token, userId, docType, status, note).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun decideDriverApplication(userId: String, decision: String, staffNote: String?): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.decideDriverApplication(apiUrl, token, userId, decision, staffNote).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun updateUserRoleOrSuspension(userId: String, role: RideRole? = null, suspend: Boolean? = null, reason: String? = null): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            if (role != null) {
                val res = apiClient.updateUserRole(apiUrl, token, userId, role)
                if (res.isFailure) return@withValidToken res
            }
            if (suspend != null) {
                val res = apiClient.updateUserSuspension(apiUrl, token, userId, suspend, reason)
                if (res.isFailure) return@withValidToken res
            }
            refreshActiveSessionData()
            Result.success(Unit)
        }
    }

    suspend fun updateSupportCaseStatus(caseId: Long, status: String, priority: String? = null): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.updateAdminSupportCase(apiUrl, token, caseId, status, priority).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun addAdminSupportReply(caseId: Long, message: String): Result<Unit> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a reply."))
        return withValidToken { apiUrl, token, _ ->
            apiClient.replyAdminSupportCase(apiUrl, token, caseId, trimmed).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun recordPayout(
        driverId: String,
        provider: String,
        externalReference: String,
        amountZar: Double,
        periodStart: String?,
        periodEnd: String?,
        note: String?
    ): Result<Unit> {
        val amountMinor = (amountZar * 100).toLong()
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Enter a valid payout amount."))
        val cleanRef = externalReference.trim()
        if (!Regex("^[A-Za-z0-9._:-]{3,128}$").matches(cleanRef)) {
            return Result.failure(IllegalArgumentException("Reference must be 3–128 characters (letters, digits, ., _, :, -)."))
        }
        return withValidToken { apiUrl, token, _ ->
            apiClient.recordAdminPayout(
                baseUrl = apiUrl,
                token = token,
                driverId = driverId,
                provider = provider,
                externalReference = cleanRef,
                amountMinor = amountMinor,
                periodStart = periodStart?.trim()?.takeIf { it.isNotEmpty() },
                periodEnd = periodEnd?.trim()?.takeIf { it.isNotEmpty() },
                note = note
            ).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun reconcilePayout(payoutId: Long, status: String, note: String? = null): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.reconcileAdminPayout(apiUrl, token, payoutId, status, note).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun runWeeklyPayoutBatch(periodStartIso: String, periodEndIso: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.runWeeklyPayoutBatch(apiUrl, token, periodStartIso.trim(), periodEndIso.trim()).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun approvePayoutBatch(batchId: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.approveAdminPayoutBatch(apiUrl, token, batchId).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun recordExternalPaymentForPayoutBatch(batchId: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.recordExternalPaymentForAdminPayoutBatch(apiUrl, token, batchId).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    // Personal Local Helpers (Saved Places & Trusted Contacts)
    suspend fun toggleSavedDestination(label: String, coordinates: RideCoordinates) {
        val id = "saved-${label.lowercase().replace(Regex("[^a-z0-9]+"), "-").take(24)}"
        savedDao.upsert(
            SavedDestination(
                id = id,
                label = label,
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                savedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    suspend fun deleteSavedDestination(id: String) = savedDao.deleteById(id)

    suspend fun addTrustedContact(name: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) return
        contactDao.upsert(TrustedContact("tc-${UUID.randomUUID().toString().take(6)}", name.trim(), phone.trim()).toEntity())
    }

    suspend fun removeTrustedContact(id: String) = contactDao.deleteById(id)

    private fun prepareUploadBytesAndMime(rawBytes: ByteArray, reportedMime: String?): Pair<ByteArray, String> {
        val directMime = detectSupportedMime(rawBytes, reportedMime)
        if (directMime == "application/pdf") {
            if (rawBytes.size > 8 * 1024 * 1024) {
                error("PDF exceeds the 8 MB upload limit.")
            }
            return rawBytes to "application/pdf"
        }
        if ((directMime == "image/jpeg" || directMime == "image/png") && rawBytes.size <= 4 * 1024 * 1024) {
            return rawBytes to directMime
        }

        // Convert HEIC/WebP or oversized photos into a clean JPEG under 4 MB
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sampleSize = 1
            val maxDim = 2048
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            if (bitmap != null) {
                try {
                    val out = ByteArrayOutputStream()
                    var quality = 88
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    while (out.size() > 4 * 1024 * 1024 && quality > 50) {
                        out.reset()
                        quality -= 12
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    val jpegBytes = out.toByteArray()
                    if (jpegBytes.isNotEmpty() && jpegBytes.size <= 8 * 1024 * 1024) {
                        return jpegBytes to "image/jpeg"
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }

        if (directMime != null) {
            if (rawBytes.size > 8 * 1024 * 1024) {
                error("Document exceeds the 8 MB upload limit.")
            }
            return rawBytes to directMime
        }
        error("Unsupported file type. Upload a JPG, PNG, or PDF file.")
    }

    private fun detectSupportedMime(bytes: ByteArray, reportedMime: String?): String? {
        if (bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return "application/pdf"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        return when (reportedMime?.lowercase()) {
            "application/pdf" -> "application/pdf"
            "image/png" -> "image/png"
            "image/jpeg", "image/jpg" -> "image/jpeg"
            else -> null
        }
    }

    private fun resolveFileName(uri: Uri, mimeType: String, docType: DriverDocumentType): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val ext = when (mimeType) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            else -> "jpg"
        }
        if (!queried.isNullOrBlank()) {
            val sanitized = queried.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (mimeType == "image/jpeg" && !sanitized.endsWith(".jpg", ignoreCase = true) && !sanitized.endsWith(".jpeg", ignoreCase = true)) {
                return "${sanitized.substringBeforeLast('.')}.$ext"
            }
            return sanitized
        }
        return "${docType.key}.$ext"
    }

    // POPIA Privacy Controls, Data Export & Offline Queue Helpers
    suspend fun buildPopiaExportJson(): String = withContext(Dispatchers.IO) {
        val session = currentSessionOrNull()
        val tripsList = tripDao.getAll().map { it.toDomain() }
        val savedList = savedDao.observeAllOnceSnapshot()
        val contactsList = contactDao.observeAllOnceSnapshot()
        val walletList = walletDao.observeAllOnceSnapshot()
        val supportList = supportDao.observeAllOnceSnapshot()
        PopiaDataExporter.buildExportJson(
            session = session,
            privacyPreferences = _privacyPreferences.value,
            trips = tripsList,
            savedDestinations = savedList,
            trustedContacts = contactsList,
            walletTransactions = walletList,
            supportCases = supportList
        )
    }

    data class AccountDeletionOutcome(
        val backendDeleted: Boolean,
        val authDeleted: Boolean,
        val supportTicketLogged: Boolean
    )

    suspend fun deleteAccountAndWipeLocalData(reason: String): Result<AccountDeletionOutcome> {
        val cleanReason = reason.trim().ifBlank { "User requested permanent account and personal data deletion." }
        return withValidToken { apiUrl, token, _ ->
            val backendResult = apiClient.deleteAccount(apiUrl, token, cleanReason)
            if (backendResult.isFailure) {
                return@withValidToken Result.failure(backendResult.exceptionOrNull()!!)
            }
            val backendStatus = backendResult.getOrThrow()
            var ticketLogged = false
            if (backendStatus == VhayaApiClient.AccountDeletionBackendStatus.ENDPOINT_UNAVAILABLE) {
                val latestTrip = withContext(Dispatchers.IO) {
                    tripDao.getAll().map { it.toDomain() }.firstOrNull()
                }
                if (latestTrip != null) {
                    ticketLogged = createSupportCase(
                        trip = latestTrip,
                        category = "other",
                        priority = "high",
                        message = "[POPIA DATA & ACCOUNT DELETION REQUEST] $cleanReason"
                    ).isSuccess
                }
            }
            val explicitFbKey = sessionStore.loadFirebaseApiKey()
            val bundledGoogleApiKey = resourceStringOrBlank(R.string.google_api_key)
            val sdkUser = firebaseAuthOrNull()?.currentUser
            val authDeleted = if (sdkUser != null && (explicitFbKey.isBlank() || explicitFbKey == bundledGoogleApiKey)) {
                try {
                    sdkUser.delete().awaitTask()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withValidToken Result.failure(e)
                }
            } else {
                val firebaseKey = resolveEffectiveFirebaseApiKey()
                val authDeleteResult = apiClient.firebaseDeleteAccount(firebaseKey, token)
                if (authDeleteResult.isFailure) {
                    return@withValidToken Result.failure(authDeleteResult.exceptionOrNull()!!)
                }
                firebaseKey.isNotBlank()
            }
            clearAllLocalUserDataAndSignOut()
            Result.success(
                AccountDeletionOutcome(
                    backendDeleted = backendStatus == VhayaApiClient.AccountDeletionBackendStatus.DELETED_ON_SERVER,
                    authDeleted = authDeleted,
                    supportTicketLogged = ticketLogged
                )
            )
        }
    }

    suspend fun requestPopiaDataDeletion(reason: String): Result<Boolean> {
        val cleanReason = reason.trim().ifBlank { "User requested POPIA account and personal data erasure." }
        val latestTrip = withContext(Dispatchers.IO) {
            tripDao.getAll().map { it.toDomain() }.firstOrNull()
        }
        return if (latestTrip != null) {
            createSupportCase(
                trip = latestTrip,
                category = "other",
                priority = "high",
                message = "[POPIA DATA & ACCOUNT DELETION REQUEST] $cleanReason"
            ).map { true }
        } else {
            Result.success(false)
        }
    }

    suspend fun clearAllLocalUserDataAndSignOut() {
        offlineQueueMutex.withLock {
            sessionStore.saveOfflineQueue(emptyList())
            withContext(Dispatchers.IO) {
                pendingSyncDao.clearAll()
            }
            _pendingOfflineCount.value = 0
        }
        withContext(Dispatchers.IO) {
            tripDao.clearAll()
            savedDao.clearAll()
            contactDao.clearAll()
            walletDao.clearAll()
            driverDao.clearAll()
            supportDao.clearAll()
            payoutDao.clearAll()
            userDao.clearAll()
            payoutBatchDao.clearAll()
            activityLogDao.clearAll()
            cachedRouteDao.clearAll()
            cachedPlaceDao.clearAll()
        }
        sessionStore.clearAllUserPreferences()
        _privacyPreferences.value = PrivacyPreferences()
        _driverMinRatePerKmZar.value = 0.0
        _trustedDrivers.value = emptyList()
        _driverCorridorTarget.value = null
        _driverQueuedReturnTripId.value = null
        _walletBalanceMinor.value = 0L
        _driverEarnings.value = DriverEarningsSummary()
        _driverSubscription.value = null
        signOut()
    }

    private suspend fun inspectAttachmentSummary(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val mime = context.contentResolver.getType(uri)
            val (prepared, finalMime) = prepareUploadBytesAndMime(rawBytes, mime)
            val name = resolveFileName(uri, finalMime, DriverDocumentType.IDENTITY)
                .replace(DriverDocumentType.IDENTITY.key, "issue_photo")
            val sizeKb = (prepared.size / 1024).coerceAtLeast(1)
            "$name ($finalMime, $sizeKb KB)"
        }.getOrNull()
    }

    private suspend fun migrateLegacyOfflineQueueIfNeeded() {
        offlineQueueMutex.withLock {
            val legacy = sessionStore.loadOfflineQueue()
            if (legacy.isNotEmpty()) {
                for (item in legacy) {
                    val entity = PendingRideSyncEntity(
                        clientRequestId = item.id,
                        operationType = item.kind,
                        tripId = item.tripId,
                        payloadJson = repoJson.encodeToString(item),
                        syncState = "PENDING",
                        retryCount = 0,
                        lastError = null,
                        nextRetryAtMs = 0L,
                        createdAtMs = item.createdAtMs
                    )
                    if (item.kind == "driver_location") {
                        pendingSyncDao.upsertLocationCoalesced(entity)
                    } else {
                        pendingSyncDao.upsert(entity)
                    }
                }
                sessionStore.saveOfflineQueue(emptyList())
            }
            _pendingOfflineCount.value = pendingSyncDao.countPending()
        }
    }

    private suspend fun enqueueOfflineAction(
        action: PendingOfflineAction,
        replaceLatestLocationForTrip: Boolean = false
    ) {
        offlineQueueMutex.withLock {
            withContext(Dispatchers.IO) {
                migrateLegacyQueueInsideLock()
                val entity = PendingRideSyncEntity(
                    clientRequestId = action.id,
                    operationType = action.kind,
                    tripId = action.tripId,
                    payloadJson = repoJson.encodeToString(action),
                    syncState = "PENDING",
                    retryCount = 0,
                    lastError = null,
                    nextRetryAtMs = 0L,
                    createdAtMs = action.createdAtMs
                )
                if (replaceLatestLocationForTrip) {
                    pendingSyncDao.upsertLocationCoalesced(entity)
                } else {
                    pendingSyncDao.upsert(entity)
                }
                val all = pendingSyncDao.getPendingOnce()
                if (all.size > 30) {
                    val toRemove = all.filter { it.operationType != "create_ride" }.take(all.size - 30)
                    for (old in toRemove) {
                        pendingSyncDao.deleteByClientRequestId(old.clientRequestId)
                    }
                }
                _pendingOfflineCount.value = pendingSyncDao.countPending()
            }
        }
        syncCoordinator.scheduleBackgroundSync()
    }

    private suspend fun migrateLegacyQueueInsideLock() {
        val legacy = sessionStore.loadOfflineQueue()
        if (legacy.isEmpty()) return
        for (item in legacy) {
            val entity = PendingRideSyncEntity(
                clientRequestId = item.id,
                operationType = item.kind,
                tripId = item.tripId,
                payloadJson = repoJson.encodeToString(item),
                syncState = "PENDING",
                retryCount = 0,
                lastError = null,
                nextRetryAtMs = 0L,
                createdAtMs = item.createdAtMs
            )
            if (item.kind == "driver_location") {
                pendingSyncDao.upsertLocationCoalesced(entity)
            } else {
                pendingSyncDao.upsert(entity)
            }
        }
        sessionStore.saveOfflineQueue(emptyList())
    }

    suspend fun flushOfflineQueue(force: Boolean = false): Int {
        val auth = ensureFreshToken(forceRefresh = false)
        if (auth.isFailure) return 0
        val (apiUrl, session) = auth.getOrThrow()
        return flushOfflineQueueInternal(apiUrl, session.idToken.orEmpty(), force = force)
    }

    private suspend fun flushOfflineQueueInternal(
        apiUrl: String,
        token: String,
        force: Boolean = false
    ): Int {
        if (token.isBlank()) return 0
        return offlineQueueMutex.withLock {
            withContext(Dispatchers.IO) {
                migrateLegacyQueueInsideLock()
                val queued = pendingSyncDao.getPendingOnce()
                if (queued.isEmpty()) {
                    _pendingOfflineCount.value = 0
                    return@withContext 0
                }
                var flushedCount = 0
                var networkBlocked = false
                val now = System.currentTimeMillis()

                for (item in queued) {
                    if (networkBlocked) break
                    if (!force && item.nextRetryAtMs > now) continue

                    val result: Result<Any> = when (item.operationType) {
                        "create_ride" -> executeQueuedRideCreate(apiUrl, token, item)
                        "ride_action" -> executeQueuedRideAction(apiUrl, token, item)
                        "trip_message" -> {
                            val action = syncCoordinator.decodeLegacyAction(item.payloadJson)
                            if (action == null) {
                                Result.success(Unit)
                            } else {
                                apiClient.performRideAction(
                                    apiUrl,
                                    token,
                                    action.tripId,
                                    buildJsonObject {
                                        put("action", "message")
                                        put("channel", "driver")
                                        put("message", action.message)
                                    }
                                )
                            }
                        }
                        "support_create", "support_reply" -> {
                            val action = syncCoordinator.decodeLegacyAction(item.payloadJson)
                            if (action == null) {
                                Result.success(Unit)
                            } else {
                                apiClient.performRideAction(
                                    apiUrl,
                                    token,
                                    action.tripId,
                                    buildJsonObject {
                                        put("action", "message")
                                        put("channel", "support")
                                        put("category", action.category)
                                        put("priority", action.priority)
                                        put("message", action.message)
                                    }
                                )
                            }
                        }
                        "trip_rate" -> {
                            val action = syncCoordinator.decodeLegacyAction(item.payloadJson)
                            if (action == null) {
                                Result.success(Unit)
                            } else {
                                apiClient.performRideAction(
                                    apiUrl,
                                    token,
                                    action.tripId,
                                    buildJsonObject {
                                        put("action", "rate")
                                        put("rating", action.rating)
                                        if (action.feedbackTags.isNotEmpty()) {
                                            putJsonArray("feedbackTags") {
                                                action.feedbackTags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        "driver_location" -> {
                            val action = syncCoordinator.decodeLegacyAction(item.payloadJson)
                            if (action == null) {
                                Result.success(Unit)
                            } else {
                                apiClient.updateDriverLocation(
                                    apiUrl,
                                    token,
                                    action.tripId,
                                    RideCoordinates(action.latitude, action.longitude)
                                )
                            }
                        }
                        else -> Result.success(Unit)
                    }

                    if (result.isSuccess) {
                        pendingSyncDao.deleteByClientRequestId(item.clientRequestId)
                        flushedCount++
                    } else {
                        val err = result.exceptionOrNull()
                        if (isTransientNetworkFailure(err)) {
                            networkBlocked = true
                            val nextRetry = now + LaunchPolicy.computeOfflineSyncBackoffMs(item.retryCount)
                            pendingSyncDao.upsert(
                                item.copy(
                                    retryCount = item.retryCount + 1,
                                    lastError = err?.message,
                                    nextRetryAtMs = nextRetry
                                )
                            )
                        } else if (item.operationType == "create_ride") {
                            // Preserve ride locally with SYNC_FAILED state so rider sees why it couldn't upload
                            val placeholder = tripDao.getById(item.tripId)
                            db.withTransaction {
                                if (placeholder != null) {
                                    tripDao.upsert(
                                        placeholder.copy(
                                            syncStatus = "SYNC_FAILED",
                                            unfulfillmentReason = friendlyApiErrorMessage(err, "Upload rejected")
                                        )
                                    )
                                }
                                pendingSyncDao.upsert(
                                    item.copy(
                                        syncState = "FAILED_PERMANENT",
                                        lastError = friendlyApiErrorMessage(err, "Upload rejected")
                                    )
                                )
                            }
                        } else {
                            // Drop permanent 4xx errors on non-create actions so they don't wedge queue
                            if (item.operationType == "ride_action") {
                                val cached = tripDao.getById(item.tripId)
                                if (cached != null && cached.syncStatus == "PENDING_UPLOAD") {
                                    tripDao.upsert(cached.copy(syncStatus = "SYNCED"))
                                }
                            }
                            pendingSyncDao.deleteByClientRequestId(item.clientRequestId)
                        }
                    }
                }
                val remainingPending = pendingSyncDao.countPending()
                _pendingOfflineCount.value = remainingPending
                if (remainingPending > 0) {
                    syncCoordinator.scheduleBackgroundSync()
                }
                flushedCount
            }
        }
    }

    private suspend fun executeQueuedRideAction(
        apiUrl: String,
        token: String,
        item: PendingRideSyncEntity
    ): Result<Any> {
        val actionPayload = syncCoordinator.decodeRideActionPayload(item.payloadJson)
            ?: return Result.success(Unit)
        val parsedJson = runCatching {
            repoJson.decodeFromString<JsonObject>(actionPayload.actionJson)
        }.getOrElse {
            return Result.success(Unit)
        }
        val res = apiClient.performRideAction(apiUrl, token, actionPayload.tripId, parsedJson)
        return res.onSuccess { updatedTrip ->
            tripDao.upsert(updatedTrip.copy(syncStatus = "SYNCED").toEntity())
        }
    }

    private suspend fun executeQueuedRideCreate(
        apiUrl: String,
        token: String,
        item: PendingRideSyncEntity
    ): Result<RideTrip> {
        val payload = runCatching {
            syncCoordinator.decodeCreatePayload(item.payloadJson)
        }.getOrElse { parseErr ->
            return Result.failure(parseErr)
        }
        val routeRes = apiClient.previewRoute(
            baseUrl = apiUrl,
            token = token,
            pickup = payload.pickup,
            destination = payload.destination,
            pickupCoords = payload.pickupCoordinates,
            destCoords = payload.destinationCoordinates
        )
        if (routeRes.isFailure) {
            return Result.failure(routeRes.exceptionOrNull() ?: IllegalStateException("Could not preview route."))
        }
        val route = routeRes.getOrThrow()
        val quoteToken = route.quoteToken
        if (quoteToken.isNullOrBlank()) {
            return Result.failure(java.io.IOException("Live quote token unavailable yet."))
        }
        val baseStandardFare = route.fareEstimate ?: FareEstimationService.calculateDistanceTimeFare(
            distanceMeters = route.distanceMeters,
            durationSeconds = route.durationSeconds,
            category = RideCategory.STANDARD
        )
        val ruralBaseEstimate = SmartRideGuards.calculateRuralAdjustedFare(
            baseEstimateZar = baseStandardFare,
            category = payload.category,
            isSharedRide = payload.isSharedRide,
            seatCount = payload.seatCount,
            isReturnTrip = payload.isReturnTrip,
            isReturnRequest = payload.isReturnRequest,
            includesGravelRoad = payload.includesGravelRoad,
            isRuralArea = LaunchPolicy.isRuralOrPeriUrban(payload.pickupCoordinates, payload.destinationCoordinates),
            distanceMeters = route.distanceMeters
        )
        val fareDecision = LaunchPolicy.resolveQueuedRideFare(
            offlineFare = payload.fare,
            customOfferFare = payload.customOfferFare,
            isSharedRide = payload.isSharedRide,
            isReturnTrip = payload.isReturnTrip,
            isReturnRequest = payload.isReturnRequest,
            includesGravelRoad = payload.includesGravelRoad,
            liveRuralEstimateZar = ruralBaseEstimate
        )

        val createdRes = apiClient.createRide(
            baseUrl = apiUrl,
            token = token,
            pickup = payload.pickup,
            destination = payload.destination,
            category = payload.category,
            pricingMode = fareDecision.finalPricingMode,
            fare = fareDecision.finalFare,
            paymentMethod = payload.paymentMethod,
            pickupAt = payload.pickupAt,
            quoteToken = quoteToken,
            pickupNote = payload.pickupNote,
            cashChangeForZar = payload.cashChangeForZar,
            beneficiaryName = payload.beneficiaryName,
            beneficiaryPhone = payload.beneficiaryPhone,
            isSharedRide = payload.isSharedRide,
            seatCount = payload.seatCount,
            isReturnTrip = payload.isReturnTrip,
            isReturnRequest = payload.isReturnRequest,
            includesGravelRoad = payload.includesGravelRoad,
            clientRequestId = payload.clientRequestId
        )
        if (createdRes.isFailure) {
            return createdRes
        }
        val serverTrip = createdRes.getOrThrow().let { trip ->
            trip.copy(
                pickupNote = trip.pickupNote ?: payload.pickupNote,
                cashChangeForZar = trip.cashChangeForZar ?: payload.cashChangeForZar,
                beneficiaryName = trip.beneficiaryName ?: payload.beneficiaryName,
                beneficiaryPhone = trip.beneficiaryPhone ?: payload.beneficiaryPhone,
                isSharedRide = trip.isSharedRide || payload.isSharedRide,
                seatCount = if (trip.seatCount > 1) trip.seatCount else payload.seatCount,
                isReturnTrip = trip.isReturnTrip || payload.isReturnTrip,
                isReturnRequest = trip.isReturnRequest || payload.isReturnRequest,
                includesGravelRoad = trip.includesGravelRoad || payload.includesGravelRoad,
                syncStatus = "SYNCED",
                clientRequestId = trip.clientRequestId ?: payload.clientRequestId
            )
        }
        tripDao.replaceOfflinePlaceholderWithServerTrip(item.tripId, serverTrip.toEntity())
        return Result.success(serverTrip)
    }

    private fun isTransientNetworkFailure(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is java.io.IOException) return true
        val msg = error.message.orEmpty().lowercase()
        return msg.contains("timed out") ||
            msg.contains("connection") ||
            msg.contains("network") ||
            msg.contains("reach vhaya") ||
            msg.contains("unreachable") ||
            msg.contains("failed to connect") ||
            msg.contains("quote token unavailable")
    }
}

private suspend fun SavedDestinationDao.observeAllOnceSnapshot(): List<SavedDestination> =
    observeAll().firstOrNull()?.map { it.toDomain() } ?: emptyList()

private suspend fun TrustedContactDao.observeAllOnceSnapshot(): List<TrustedContact> =
    observeAll().firstOrNull()?.map { it.toDomain() } ?: emptyList()

private suspend fun WalletDao.observeAllOnceSnapshot(): List<WalletTransaction> =
    observeAll().firstOrNull()?.map { it.toDomain() } ?: emptyList()

private suspend fun SupportCaseDao.observeAllOnceSnapshot(): List<SupportCase> =
    observeAll().firstOrNull()?.map { it.toDomain() } ?: emptyList()
