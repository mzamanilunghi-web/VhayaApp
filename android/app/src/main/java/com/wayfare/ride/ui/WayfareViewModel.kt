package com.wayfare.ride.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.audio.NotificationSoundStyle
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.data.friendlyApiErrorMessage
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
import com.wayfare.ride.domain.DriverMapEngine
import com.wayfare.ride.domain.DriverPayoutRecord
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.DriverRiderRating
import com.wayfare.ride.domain.DriverSubscriptionSummary
import com.wayfare.ride.domain.HazardType
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NearbyPlaceCategory
import com.wayfare.ride.domain.OfflineCorridorPack
import com.wayfare.ride.domain.OfflineCorridorPackStatus
import com.wayfare.ride.domain.OutboundDriverApproachingHint
import com.wayfare.ride.domain.RoadHazardPin
import com.wayfare.ride.domain.PaymentInitOutcome
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PaymentVerificationResult
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.domain.PaystackReturnMatcher
import com.wayfare.ride.domain.PayoutBatchRecord
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.ReturnFareComparison
import com.wayfare.ride.domain.ReturnRideEngine
import com.wayfare.ride.domain.ReturnRideMatch
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.RuralRunPreset
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SmartRideGuards
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.SupportUpdateAlert
import com.wayfare.ride.domain.TrustedCommunityDriver
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.VhayaFeedbackClassifier
import com.wayfare.ride.domain.VhayaFeedbackMessage
import com.wayfare.ride.domain.VhayaFeedbackTone
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.notifications.IncomingTripChatAlert
import com.wayfare.ride.notifications.RideNotificationHelper
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.service.FareEstimateBreakdown
import com.wayfare.ride.service.FareEstimationService
import com.wayfare.ride.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RiderTab(val label: String) {
    RIDE("Ride"),
    TRIPS("Trips"),
    SUPPORT("Support"),
    WALLET("Wallet")
}

enum class DriverTab(val label: String) {
    DRIVE("Drive"),
    EARNINGS("Earnings"),
    SUPPORT("Support"),
    PROFILE("Profile")
}

enum class AdminTab(val label: String) {
    OVERVIEW("Overview"),
    DRIVERS("Drivers"),
    SUPPORT("Support"),
    PAYOUTS("Payouts")
}

enum class RiderBookingStep {
    HOME_COMPACT,
    SEARCH_DESTINATION,
    MAP_PIN_PICKER,
    CHOOSE_RIDE_FARE
}

data class BookingDraft(
    val step: RiderBookingStep = RiderBookingStep.HOME_COMPACT,
    val pickingForPickup: Boolean = false,
    val pickupLabel: String = "Current pickup location",
    val pickupCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    val destinationLabel: String = "",
    val destinationCoords: RideCoordinates? = null,
    val category: RideCategory = RideCategory.STANDARD,
    val previewRoute: RideRoute? = null,
    val visualRoute: RideRoute? = null,
    val routeLoading: Boolean = false,
    val routeError: String? = null,
    val customOfferText: String = "",
    val isOfferExpanded: Boolean = false,
    val scheduledOffsetMinutes: Int? = null,
    val gpsUsingDeviceLocation: Boolean = false,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val pickupNote: String = "",
    val cashChangeForZar: Int? = null,
    val beneficiaryEnabled: Boolean = false,
    val beneficiaryName: String = "",
    val beneficiaryPhone: String = "",
    val isSharedRide: Boolean = false,
    val seatCount: Int = 1,
    val isReturnTrip: Boolean = false,
    val isReturnRequest: Boolean = false,
    val includesGravelRoad: Boolean = false,
    val activePreset: RuralRunPreset? = null,
    val pinPreviewAddress: String? = null,
    val pinPreviewLoading: Boolean = false,
    val pinConfirming: Boolean = false
) {
    private val baseStandardFare: Double
        get() = previewRoute?.fareEstimate
            ?: previewRoute?.let {
                FareEstimationService.calculateDistanceTimeFare(
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.durationSeconds,
                    category = RideCategory.STANDARD
                )
            }
            ?: 20.0

    val fareEstimateBreakdown: FareEstimateBreakdown?
        get() = previewRoute?.let { route ->
            FareEstimationService.defaultInstance.estimateForRoute(
                route = route,
                category = category,
                isSharedRide = isSharedRide,
                seatCount = seatCount,
                isReturnTrip = isReturnTrip,
                isReturnRequest = isReturnRequest,
                includesGravelRoad = includesGravelRoad,
                isRuralArea = LaunchPolicy.isRuralOrPeriUrban(pickupCoords, destinationCoords)
            )
        }

    val returnFareComparison: ReturnFareComparison
        get() = SmartRideGuards.calculateReturnFareComparison(
            baseEstimateZar = baseStandardFare,
            distanceMeters = previewRoute?.distanceMeters,
            category = category,
            isSharedRide = isSharedRide,
            seatCount = seatCount,
            includesGravelRoad = includesGravelRoad,
            isRuralArea = LaunchPolicy.isRuralOrPeriUrban(pickupCoords, destinationCoords)
        )

    val standardPreReturnEstimate: Double
        get() = returnFareComparison.standardFareZar

    val estimatedFare: Double
        get() = fareEstimateBreakdown?.estimatedFareZar ?: run {
            val ruralCorridor = LaunchPolicy.isRuralOrPeriUrban(pickupCoords, destinationCoords)
            SmartRideGuards.calculateRuralAdjustedFare(
                baseEstimateZar = baseStandardFare,
                category = category,
                isSharedRide = isSharedRide,
                seatCount = seatCount,
                isReturnTrip = isReturnTrip,
                includesGravelRoad = includesGravelRoad,
                isRuralArea = ruralCorridor,
                isReturnRequest = isReturnRequest,
                distanceMeters = previewRoute?.distanceMeters
            )
        }

    val effectiveFare: Double
        get() = customOfferText.toDoubleOrNull()?.takeIf { it >= 20.0 } ?: estimatedFare

    val isOfflineEstimatedRoute: Boolean
        get() = previewRoute != null &&
            (previewRoute.provider == "room-cache" || previewRoute.provider == "offline-estimate")

    val isRouteValidForRequest: Boolean
        get() = previewRoute != null &&
            (!previewRoute.quoteToken.isNullOrBlank() || isOfflineEstimatedRoute) &&
            previewRoute.durationSeconds > LaunchPolicy.MIN_RIDE_DURATION_SECONDS &&
            !routeLoading
}

data class WayfareUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val apiBaseUrl: String = "",
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val lowDataMode: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val notificationSoundStyle: NotificationSoundStyle = NotificationSoundStyle.CAR_HORN,
    val privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    val pendingOfflineCount: Int = 0,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val verificationResendCooldownSeconds: Int = 0,
    val isInitialSyncing: Boolean = false,
    val isRefreshing: Boolean = false,
    val apiConnected: Boolean = false,
    val syncError: String? = null,
    val placeSearchError: String? = null,
    val actionError: String? = null,
    val busyActions: Set<String> = emptySet(),
    val nowEpochMs: Long = System.currentTimeMillis(),
    val locallyExpiredTripIds: Set<String> = emptySet(),
    val expiredTripNotice: RideTrip? = null,
    val showAccountSheet: Boolean = false,
    val showPrivacySheet: Boolean = false,
    val activePaymentVerification: PaymentVerificationResult? = null,
    val activePaystackCheckout: PaymentInitOutcome? = null,
    val pendingMockPayment: PaymentInitOutcome? = null,
    val pendingRatingTripId: String? = null,
    val dismissedOfferIds: Set<String> = emptySet(),
    val declinedDriverTripIds: Set<String> = emptySet(),
    val driverMinRatePerKmZar: Double = 0.0,
    val driverStallAssessment: SmartRideGuards.DriverStallAssessment? = null,
    val riderOffRouteAssessment: SmartRideGuards.OffRouteAssessment? = null,
    val dismissedOffRouteTripIds: Set<String> = emptySet(),
    val fallbackArrivalTimestamps: Map<String, Long> = emptyMap(),
    val cashChangeCreditedTripIds: Set<String> = emptySet(),
    val riderTab: RiderTab = RiderTab.RIDE,
    val driverTab: DriverTab = DriverTab.DRIVE,
    val adminTab: AdminTab = AdminTab.OVERVIEW,
    val booking: BookingDraft = BookingDraft(),
    val activeFareEstimate: FareEstimateBreakdown? = null,
    val placeSuggestions: List<PlaceSearchSuggestion> = emptyList(),
    val searchingPlaces: Boolean = false,
    val nearbyPlaces: List<PlaceSearchSuggestion> = emptyList(),
    val nearbyPlacesLoading: Boolean = false,
    val nearbyPlacesError: String? = null,
    val selectedNearbyCategory: NearbyPlaceCategory = NearbyPlaceCategory.ALL,
    val driverSoundEnabled: Boolean = true,
    val driverHeatmapEnabled: Boolean = true,
    val driverRequestIndex: Int = 0,
    val driverCurrentCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    val driverHasGpsFix: Boolean = false,
    val driverCurrentAddress: String? = null,
    val driverAddressLoading: Boolean = false,
    val driverDriveModeMinimized: Boolean = false,
    val driverNavRoute: RideRoute? = null,
    val driverNavLoading: Boolean = false,
    val driverNavFollowCamera: Boolean = true,
    val isNetworkConnected: Boolean = true,
    val roadHazards: List<RoadHazardPin> = emptyList(),
    val driverCorridorTarget: DriverCorridorTarget? = null,
    val driverQueuedReturnTripId: String? = null,
    val driverRepositionZone: LaunchPolicy.DemandHeatZone? = null,
    val driverRepositionRoute: RideRoute? = null,
    val driverRepositionLoading: Boolean = false,
    val driverVoiceNavEnabled: Boolean = true,
    val driverHeadingUpEnabled: Boolean = false,
    val offlineTilePacks: List<OfflineCorridorPackStatus> = emptyList(),
    val offlinePackDownloadingId: String? = null,
    val offlinePackProgress: Pair<Int, Int>? = null,
    val pendingDriverSummaryTripId: String? = null,
    val driverOnboardingBusy: Boolean = false,
    val driverOnboardingError: String? = null,
    val driverOnboardingNotice: String? = null,
    val walletBalanceMinor: Long = 0L,
    val driverEarnings: DriverEarningsSummary = DriverEarningsSummary(),
    val driverSubscription: DriverSubscriptionSummary? = null,
    val adminOverview: AdminOverviewSummary = AdminOverviewSummary(),
    val driverBankDetails: DriverBankDetails? = null,
    val driverRiderRatings: Map<String, DriverRiderRating> = emptyMap(),
    val trustedDrivers: List<TrustedCommunityDriver> = emptyList(),
    val trips: List<RideTrip> = emptyList(),
    val savedDestinations: List<SavedDestination> = emptyList(),
    val trustedContacts: List<TrustedContact> = emptyList(),
    val walletTransactions: List<WalletTransaction> = emptyList(),
    val driverApplications: List<DriverApplication> = emptyList(),
    val supportCases: List<SupportCase> = emptyList(),
    val payoutRecords: List<DriverPayoutRecord> = emptyList(),
    val userAccounts: List<UserAccountSummary> = emptyList(),
    val payoutBatches: List<PayoutBatchRecord> = emptyList(),
    val activityLogs: List<ActivityLogEntry> = emptyList(),
    val unreadChatCountByTrip: Map<String, Int> = emptyMap(),
    val pendingOpenChatTripId: String? = null
) {
    fun isBusy(key: String): Boolean = key in busyActions

    val isAnyActionBusy: Boolean
        get() = busyActions.isNotEmpty()

    val currentSession: UserSession?
        get() = when (authState) {
            is AuthState.Authenticated -> authState.session
            is AuthState.EmailVerificationPending -> authState.session
            AuthState.Unauthenticated -> null
        }

    val activeRole: RideRole
        get() = currentSession?.role ?: RideRole.RIDER

    val activeUserId: String
        get() = currentSession?.userId.orEmpty()

    val currentDriverApp: DriverApplication?
        get() = driverApplications.firstOrNull { it.userId == activeUserId } ?: driverApplications.firstOrNull()

    val driverWeeklyPlanActive: Boolean
        get() = driverSubscription?.let {
            it.status.equals("active", ignoreCase = true) &&
                (it.expiresAt == null || it.expiresAt > nowEpochMs)
        } ?: false

    val riderActiveTrip: RideTrip?
        get() = trips.firstOrNull { trip ->
            trip.riderId == activeUserId &&
                trip.status.isActive &&
                trip.id !in locallyExpiredTripIds &&
                !LaunchPolicy.isTripExpired(trip, nowEpochMs)
        }

    val riderHistoryTrips: List<RideTrip>
        get() = trips.filter { it.riderId == activeUserId && !it.status.isActive }

    val driverActiveTrip: RideTrip?
        get() {
            val candidates = trips.filter { it.driverId == activeUserId && it.status.isActive }
            if (candidates.isEmpty()) return null
            val outboundPool = candidates.filter { it.id != driverQueuedReturnTripId }.ifEmpty { candidates }
            return outboundPool.firstOrNull { it.status == RideStatus.IN_PROGRESS }
                ?: outboundPool.firstOrNull { it.status == RideStatus.ARRIVED }
                ?: outboundPool.first()
        }

    val driverQueuedReturnTrip: RideTrip?
        get() {
            val queuedId = driverQueuedReturnTripId ?: return null
            val activeId = driverActiveTrip?.id
            if (queuedId == activeId) return null
            return trips.firstOrNull { trip ->
                trip.id == queuedId &&
                    !LaunchPolicy.isTripExpired(trip, nowEpochMs) &&
                    (trip.status.isOpenForDrivers || (trip.driverId == activeUserId && trip.status.isActive))
            }
        }

    val driverReturnRideMatches: List<ReturnRideMatch>
        get() {
            val active = driverActiveTrip ?: return emptyList()
            if (active.status != RideStatus.IN_PROGRESS) return emptyList()
            return ReturnRideEngine.findReturnRideMatches(
                trips = trips,
                outboundTrip = active,
                declinedIds = declinedDriverTripIds,
                expiredIds = locallyExpiredTripIds,
                driverLocation = driverCurrentCoords,
                nowMs = nowEpochMs
            ).filter { it.trip.id != driverQueuedReturnTripId }
        }

    val bookingApproachingReturnDriver: OutboundDriverApproachingHint?
        get() {
            if (booking.step != RiderBookingStep.CHOOSE_RIDE_FARE) return null
            return ReturnRideEngine.findApproachingReturnDriverHint(
                trips = trips,
                riderPickup = booking.pickupCoords,
                riderDest = booking.destinationCoords,
                activeUserId = activeUserId
            )
        }

    val driverAllOpenRequests: List<RideTrip>
        get() = trips
            .filter { trip ->
                trip.status.isOpenForDrivers &&
                    trip.driverId == null &&
                    trip.id !in locallyExpiredTripIds &&
                    trip.id !in declinedDriverTripIds &&
                    !LaunchPolicy.isTripExpired(trip, nowEpochMs)
            }
            .sortedByDescending { LaunchPolicy.matchScoreForDriver(it, driverCurrentCoords) }

    val driverOpenRequests: List<RideTrip>
        get() = driverAllOpenRequests.filter { trip ->
            SmartRideGuards.matchesDriverMinRateFilter(trip, driverMinRatePerKmZar) &&
                DriverMapEngine.matchesCorridorFilter(trip, driverCurrentCoords, driverCorridorTarget)
        }

    val driverFilteredOutCount: Int
        get() = (driverAllOpenRequests.size - driverOpenRequests.size).coerceAtLeast(0)

    val driverCorridorFilteredOutCount: Int
        get() = if (driverCorridorTarget == null) {
            0
        } else {
            driverAllOpenRequests.count { trip ->
                SmartRideGuards.matchesDriverMinRateFilter(trip, driverMinRatePerKmZar) &&
                    !DriverMapEngine.matchesCorridorFilter(trip, driverCurrentCoords, driverCorridorTarget)
            }
        }

    val driverCompletedTrips: List<RideTrip>
        get() = trips.filter { it.driverId == activeUserId && it.status == RideStatus.COMPLETED }

    val pendingRatingTrip: RideTrip?
        get() = pendingRatingTripId?.let { id -> trips.firstOrNull { it.id == id } }

    val pendingDriverSummaryTrip: RideTrip?
        get() = pendingDriverSummaryTripId?.let { id -> trips.firstOrNull { it.id == id } }
}

private data class LocalNavState(
    val riderTab: RiderTab = RiderTab.RIDE,
    val driverTab: DriverTab = DriverTab.DRIVE,
    val adminTab: AdminTab = AdminTab.OVERVIEW,
    val booking: BookingDraft = BookingDraft(),
    val driverSoundEnabled: Boolean = true,
    val driverHeatmapEnabled: Boolean = true,
    val driverRequestIndex: Int = 0,
    val driverCurrentCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    val driverHasGpsFix: Boolean = false,
    val driverCurrentAddress: String? = null,
    val driverAddressLoading: Boolean = false,
    val driverDriveModeMinimized: Boolean = false,
    val driverNavRoute: RideRoute? = null,
    val driverNavRouteTripKey: String? = null,
    val driverNavLoading: Boolean = false,
    val driverNavFollowCamera: Boolean = true,
    val driverRepositionZone: LaunchPolicy.DemandHeatZone? = null,
    val driverRepositionRoute: RideRoute? = null,
    val driverRepositionLoading: Boolean = false,
    val offlinePackDownloadingId: String? = null,
    val offlinePackProgress: Pair<Int, Int>? = null,
    val pendingDriverSummaryTripId: String? = null,
    val driverOnboardingBusy: Boolean = false,
    val driverOnboardingError: String? = null,
    val driverOnboardingNotice: String? = null,
    val showAccountSheet: Boolean = false,
    val showPrivacySheet: Boolean = false,
    val activePaymentVerification: PaymentVerificationResult? = null,
    val activePaystackCheckout: PaymentInitOutcome? = null,
    val pendingMockPayment: PaymentInitOutcome? = null,
    val pendingRatingTripId: String? = null,
    val dismissedOfferIds: Set<String> = emptySet(),
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val verificationResendCooldownSeconds: Int = 0,
    val isInitialSyncing: Boolean = false,
    val isRefreshing: Boolean = false,
    val apiConnected: Boolean = false,
    val syncError: String? = null,
    val placeSearchError: String? = null,
    val actionError: String? = null,
    val busyActions: Set<String> = emptySet(),
    val nowEpochMs: Long = System.currentTimeMillis(),
    val locallyExpiredTripIds: Set<String> = emptySet(),
    val expiredTripNotice: RideTrip? = null,
    val driverStallAssessment: SmartRideGuards.DriverStallAssessment? = null,
    val riderOffRouteAssessment: SmartRideGuards.OffRouteAssessment? = null,
    val dismissedOffRouteTripIds: Set<String> = emptySet(),
    val fallbackArrivalTimestamps: Map<String, Long> = emptyMap(),
    val cashChangeCreditedTripIds: Set<String> = emptySet(),
    val unreadChatCountByTrip: Map<String, Int> = emptyMap(),
    val pendingOpenChatTripId: String? = null
)

class WayfareViewModel(
    private val repository: WayfareRepository,
    private val fareEstimationService: FareEstimationService = FareEstimationService.defaultInstance
) : ViewModel() {

    private val localNav = MutableStateFlow(LocalNavState())
    val activeFareEstimate = fareEstimationService.activeEstimate
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _feedbackEvents = MutableSharedFlow<VhayaFeedbackMessage>(extraBufferCapacity = 8)
    val feedbackEvents = _feedbackEvents.asSharedFlow()

    private val _openUrlEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openUrlEvents = _openUrlEvents.asSharedFlow()

    private val _supportAlerts = MutableSharedFlow<SupportUpdateAlert>(extraBufferCapacity = 8)
    val supportAlerts = _supportAlerts.asSharedFlow()

    private val _incomingChatAlerts = MutableSharedFlow<IncomingTripChatAlert>(extraBufferCapacity = 8)
    val incomingChatAlerts = _incomingChatAlerts.asSharedFlow()

    private val _returnRideAlerts = MutableSharedFlow<ReturnRideMatch>(extraBufferCapacity = 4)
    val returnRideAlerts = _returnRideAlerts.asSharedFlow()

    private var observedActiveRiderTripId: String? = null
    private var pinPreviewJob: Job? = null
    private var resendCooldownJob: Job? = null
    private var placeSearchJob: Job? = null
    private var nearbyPlacesJob: Job? = null
    private var lastNearbyFetchOrigin: RideCoordinates? = null
    private var lastNearbyFetchCategory: NearbyPlaceCategory? = null
    private var driverNavFetchJob: Job? = null
    private var driverAddressJob: Job? = null
    private var lastDriverNavOrigin: RideCoordinates? = null
    private var lastDriverAddressCoords: RideCoordinates? = null
    private var lastDriverNavFetchMs: Long = 0L
    private var placeSearchRequestId = 0L
    private var verificationCheckJob: Job? = null
    private var lastToastedVerifiedUserId: String? = null
    private var hasCompletedInitialSyncForUserId: String? = null
    private var supportHydratedForUserId: String? = null
    private var lastObservedSupportCases: List<SupportCase> = emptyList()
    private var chatHydratedForUserId: String? = null
    private val seenIncomingChatMessageIds = mutableSetOf<String>()
    private var activeOpenChatTripId: String? = null
    private var trackedReturnRadarOutboundTripId: String? = null
    private val seenReturnMatchTripIds = mutableSetOf<String>()
    private val expiringTripIds = mutableSetOf<String>()
    private val autoPaymentTriggeredTripIds = mutableSetOf<String>()
    private val stallTrackers = mutableMapOf<String, SmartRideGuards.DriverStallTracker>()

    private val coreDataFlow = combine(
        repository.trips,
        repository.savedDestinations,
        repository.trustedContacts,
        repository.walletTransactions,
        repository.driverApplications
    ) { trips, saved, contacts, wallet, drivers ->
        CoreSlice(trips, saved, contacts, wallet, drivers)
    }

    private val opsDataFlow = combine(
        repository.supportCases,
        repository.payoutRecords,
        repository.userAccounts,
        repository.payoutBatches,
        repository.activityLogs
    ) { support, payouts, users, batches, logs ->
        OpsSlice(support, payouts, users, batches, logs)
    }

    private val liveSummaryFlow = combine(
        repository.walletBalanceMinor,
        repository.driverEarnings,
        repository.driverSubscription,
        repository.adminOverview,
        repository.placeSuggestions,
        repository.searchingPlaces,
        repository.lowDataMode,
        repository.appLanguage,
        repository.trustedDrivers,
        repository.roadHazards,
        repository.offlineTilePacks,
        repository.nearbyPlaces,
        repository.nearbyPlacesLoading,
        repository.nearbyPlacesError,
        repository.selectedNearbyCategory
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        LiveSummarySlice(
            walletBalanceMinor = arr[0] as Long,
            driverEarnings = arr[1] as DriverEarningsSummary,
            driverSubscription = arr[2] as DriverSubscriptionSummary?,
            adminOverview = arr[3] as AdminOverviewSummary,
            placeSuggestions = arr[4] as List<PlaceSearchSuggestion>,
            searchingPlaces = arr[5] as Boolean,
            lowDataMode = arr[6] as Boolean,
            appLanguage = arr[7] as AppLanguage,
            trustedDrivers = arr[8] as List<TrustedCommunityDriver>,
            roadHazards = arr[9] as List<RoadHazardPin>,
            offlineTilePacks = arr[10] as List<OfflineCorridorPackStatus>,
            nearbyPlaces = arr[11] as List<PlaceSearchSuggestion>,
            nearbyPlacesLoading = arr[12] as Boolean,
            nearbyPlacesError = arr[13] as String?,
            selectedNearbyCategory = arr[14] as NearbyPlaceCategory
        )
    }

    private val sessionFlow = combine(
        repository.authState,
        repository.apiBaseUrl,
        repository.themeMode,
        repository.notificationSoundStyle,
        repository.privacyPreferences,
        repository.pendingOfflineCount,
        repository.declinedDriverTripIds,
        repository.driverBankDetails,
        repository.driverRiderRatings,
        repository.driverMinRatePerKmZar,
        repository.driverCorridorTarget,
        repository.driverVoiceNavEnabled,
        repository.driverHeadingUpEnabled,
        repository.isNetworkConnected,
        repository.driverQueuedReturnTripId
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        SessionSlice(
            authState = arr[0] as AuthState,
            apiBaseUrl = arr[1] as String,
            themeMode = arr[2] as AppThemeMode,
            notificationSoundStyle = arr[3] as NotificationSoundStyle,
            privacyPreferences = arr[4] as PrivacyPreferences,
            pendingOfflineCount = arr[5] as Int,
            declinedDriverTripIds = arr[6] as Set<String>,
            driverBankDetails = arr[7] as DriverBankDetails?,
            driverRiderRatings = arr[8] as Map<String, DriverRiderRating>,
            driverMinRatePerKmZar = arr[9] as Double,
            driverCorridorTarget = arr[10] as DriverCorridorTarget?,
            driverVoiceNavEnabled = arr[11] as Boolean,
            driverHeadingUpEnabled = arr[12] as Boolean,
            isNetworkConnected = arr[13] as Boolean,
            driverQueuedReturnTripId = arr[14] as String?
        )
    }

    val uiState = combine(
        localNav,
        sessionFlow,
        coreDataFlow,
        opsDataFlow,
        liveSummaryFlow
    ) { nav, session, core, ops, summary ->
        WayfareUiState(
            authState = session.authState,
            apiBaseUrl = session.apiBaseUrl,
            themeMode = session.themeMode,
            lowDataMode = summary.lowDataMode,
            appLanguage = summary.appLanguage,
            notificationSoundStyle = session.notificationSoundStyle,
            privacyPreferences = session.privacyPreferences,
            pendingOfflineCount = session.pendingOfflineCount,
            authBusy = nav.authBusy,
            authError = nav.authError,
            authNotice = nav.authNotice,
            verificationResendCooldownSeconds = nav.verificationResendCooldownSeconds,
            isInitialSyncing = nav.isInitialSyncing,
            isRefreshing = nav.isRefreshing,
            apiConnected = nav.apiConnected,
            syncError = nav.syncError,
            placeSearchError = nav.placeSearchError,
            actionError = nav.actionError,
            busyActions = nav.busyActions,
            nowEpochMs = nav.nowEpochMs,
            locallyExpiredTripIds = nav.locallyExpiredTripIds,
            expiredTripNotice = nav.expiredTripNotice,
            showAccountSheet = nav.showAccountSheet,
            showPrivacySheet = nav.showPrivacySheet,
            activePaymentVerification = nav.activePaymentVerification,
            activePaystackCheckout = nav.activePaystackCheckout,
            pendingMockPayment = nav.pendingMockPayment,
            pendingRatingTripId = nav.pendingRatingTripId,
            dismissedOfferIds = nav.dismissedOfferIds,
            declinedDriverTripIds = session.declinedDriverTripIds,
            driverMinRatePerKmZar = session.driverMinRatePerKmZar,
            driverStallAssessment = nav.driverStallAssessment,
            riderOffRouteAssessment = nav.riderOffRouteAssessment,
            dismissedOffRouteTripIds = nav.dismissedOffRouteTripIds,
            fallbackArrivalTimestamps = nav.fallbackArrivalTimestamps,
            cashChangeCreditedTripIds = nav.cashChangeCreditedTripIds,
            riderTab = nav.riderTab,
            driverTab = nav.driverTab,
            adminTab = nav.adminTab,
            booking = nav.booking,
            activeFareEstimate = fareEstimationService.activeEstimate.value ?: nav.booking.fareEstimateBreakdown,
            placeSuggestions = summary.placeSuggestions,
            searchingPlaces = summary.searchingPlaces,
            nearbyPlaces = summary.nearbyPlaces,
            nearbyPlacesLoading = summary.nearbyPlacesLoading,
            nearbyPlacesError = summary.nearbyPlacesError,
            selectedNearbyCategory = summary.selectedNearbyCategory,
            driverSoundEnabled = nav.driverSoundEnabled,
            driverHeatmapEnabled = nav.driverHeatmapEnabled,
            driverRequestIndex = nav.driverRequestIndex,
            driverCurrentCoords = nav.driverCurrentCoords,
            driverHasGpsFix = nav.driverHasGpsFix,
            driverCurrentAddress = nav.driverCurrentAddress,
            driverAddressLoading = nav.driverAddressLoading,
            driverDriveModeMinimized = nav.driverDriveModeMinimized,
            driverNavRoute = nav.driverNavRoute,
            driverNavLoading = nav.driverNavLoading,
            driverNavFollowCamera = nav.driverNavFollowCamera,
            isNetworkConnected = session.isNetworkConnected,
            roadHazards = summary.roadHazards,
            driverCorridorTarget = session.driverCorridorTarget,
            driverQueuedReturnTripId = session.driverQueuedReturnTripId,
            driverRepositionZone = nav.driverRepositionZone,
            driverRepositionRoute = nav.driverRepositionRoute,
            driverRepositionLoading = nav.driverRepositionLoading,
            driverVoiceNavEnabled = session.driverVoiceNavEnabled,
            driverHeadingUpEnabled = session.driverHeadingUpEnabled,
            offlineTilePacks = summary.offlineTilePacks,
            offlinePackDownloadingId = nav.offlinePackDownloadingId,
            offlinePackProgress = nav.offlinePackProgress,
            pendingDriverSummaryTripId = nav.pendingDriverSummaryTripId,
            driverOnboardingBusy = nav.driverOnboardingBusy,
            driverOnboardingError = nav.driverOnboardingError,
            driverOnboardingNotice = nav.driverOnboardingNotice,
            walletBalanceMinor = summary.walletBalanceMinor,
            driverEarnings = summary.driverEarnings,
            driverSubscription = summary.driverSubscription,
            adminOverview = summary.adminOverview,
            driverBankDetails = session.driverBankDetails,
            driverRiderRatings = session.driverRiderRatings,
            trustedDrivers = summary.trustedDrivers,
            trips = core.trips,
            savedDestinations = core.saved,
            trustedContacts = core.contacts,
            walletTransactions = core.wallet,
            driverApplications = core.drivers,
            supportCases = ops.support,
            payoutRecords = ops.payouts,
            userAccounts = ops.users,
            payoutBatches = ops.batches,
            activityLogs = ops.logs,
            unreadChatCountByTrip = nav.unreadChatCountByTrip,
            pendingOpenChatTripId = nav.pendingOpenChatTripId
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WayfareUiState(
            authState = repository.authState.value,
            apiBaseUrl = repository.apiBaseUrl.value,
            themeMode = repository.themeMode.value,
            lowDataMode = repository.lowDataMode.value,
            appLanguage = repository.appLanguage.value,
            notificationSoundStyle = repository.notificationSoundStyle.value,
            privacyPreferences = repository.privacyPreferences.value,
            pendingOfflineCount = repository.pendingOfflineCount.value,
            declinedDriverTripIds = repository.declinedDriverTripIds.value,
            driverMinRatePerKmZar = repository.driverMinRatePerKmZar.value,
            isNetworkConnected = repository.isNetworkConnected.value,
            driverCorridorTarget = repository.driverCorridorTarget.value,
            driverQueuedReturnTripId = repository.driverQueuedReturnTripId.value,
            driverVoiceNavEnabled = repository.driverVoiceNavEnabled.value,
            driverHeadingUpEnabled = repository.driverHeadingUpEnabled.value,
            offlineTilePacks = repository.offlineTilePacks.value,
            driverBankDetails = repository.driverBankDetails.value,
            driverRiderRatings = repository.driverRiderRatings.value,
            trustedDrivers = repository.trustedDrivers.value
        )
    )

    init {
        viewModelScope.launch {
            localNav
                .map { it.booking }
                .distinctUntilChanged()
                .collect { booking ->
                    fareEstimationService.updateActiveEstimate(
                        route = booking.previewRoute,
                        category = booking.category,
                        isSharedRide = booking.isSharedRide,
                        seatCount = booking.seatCount,
                        isReturnTrip = booking.isReturnTrip,
                        isReturnRequest = booking.isReturnRequest,
                        includesGravelRoad = booking.includesGravelRoad,
                        isRuralArea = LaunchPolicy.isRuralOrPeriUrban(booking.pickupCoords, booking.destinationCoords)
                    )
                }
        }
        viewModelScope.launch {
            repository.authState.collect { auth ->
                if (auth is AuthState.Unauthenticated) {
                    fareEstimationService.clearActiveEstimate()
                    hasCompletedInitialSyncForUserId = null
                    lastToastedVerifiedUserId = null
                    supportHydratedForUserId = null
                    lastObservedSupportCases = emptyList()
                    chatHydratedForUserId = null
                    seenIncomingChatMessageIds.clear()
                    activeOpenChatTripId = null
                    trackedReturnRadarOutboundTripId = null
                    seenReturnMatchTripIds.clear()
                    expiringTripIds.clear()
                    autoPaymentTriggeredTripIds.clear()
                    stallTrackers.clear()
                    driverAddressJob?.cancel()
                    lastDriverAddressCoords = null
                    nearbyPlacesJob?.cancel()
                    lastNearbyFetchOrigin = null
                    lastNearbyFetchCategory = null
                    localNav.update {
                        it.copy(
                            apiConnected = false,
                            driverHasGpsFix = false,
                            driverCurrentAddress = null,
                            driverAddressLoading = false,
                            driverCurrentCoords = LaunchPolicy.DEFAULT_MAP_CENTER,
                            driverStallAssessment = null,
                            riderOffRouteAssessment = null,
                            dismissedOffRouteTripIds = emptySet(),
                            fallbackArrivalTimestamps = emptyMap(),
                            cashChangeCreditedTripIds = emptySet(),
                            unreadChatCountByTrip = emptyMap(),
                            pendingOpenChatTripId = null,
                            driverRepositionZone = null,
                            driverRepositionRoute = null,
                            driverRepositionLoading = false
                        )
                    }
                } else if (auth is AuthState.EmailVerificationPending) {
                    repository.syncVerificationWorkSchedule(triggerImmediateCheck = true)
                    checkEmailVerified(silent = true)
                } else if (auth is AuthState.Authenticated) {
                    if (lastNearbyFetchOrigin == null) {
                        refreshNearbyPlacesIfNeeded(
                            origin = localNav.value.booking.pickupCoords,
                            force = false
                        )
                    }
                    val userId = auth.session.userId
                    if (hasCompletedInitialSyncForUserId != userId) {
                        hasCompletedInitialSyncForUserId = userId
                        localNav.update { it.copy(isInitialSyncing = true) }
                        viewModelScope.launch {
                            repository.refreshActiveSessionData()
                                .onSuccess {
                                    localNav.update {
                                        it.copy(isInitialSyncing = false, apiConnected = true, syncError = null)
                                    }
                                }
                                .onFailure { err ->
                                    val msg = friendlyApiErrorMessage(err, "Unable to sync with Vhaya. Retrying...")
                                    localNav.update {
                                        it.copy(isInitialSyncing = false, apiConnected = false, syncError = msg)
                                    }
                                }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.accountVerificationChanges.collect { change ->
                when (change) {
                    is AccountVerificationChange.EmailVerified -> {
                        localNav.update {
                            it.copy(authBusy = false, authError = null, authNotice = null)
                        }
                        if (lastToastedVerifiedUserId != change.session.userId) {
                            lastToastedVerifiedUserId = change.session.userId
                            toast(
                                message = "Email verified! Welcome to Vhaya.",
                                tone = VhayaFeedbackTone.SUCCESS,
                                title = "Account verified"
                            )
                        }
                    }
                    is AccountVerificationChange.DriverVerificationUpdated -> {
                        if (change.verificationStatus.equals("approved", ignoreCase = true) ||
                            change.role == RideRole.DRIVER
                        ) {
                            toast(
                                message = "Driver verification approved! You can now go online and accept rides.",
                                tone = VhayaFeedbackTone.SUCCESS,
                                title = "Account verified"
                            )
                        } else if (change.verificationStatus.equals("rejected", ignoreCase = true) ||
                            change.applicationStatus.equals("needs_follow_up", ignoreCase = true)
                        ) {
                            toast(
                                message = change.staffNote?.takeIf { it.isNotBlank() }
                                    ?: "Please review your driver verification documents in Profile.",
                                tone = VhayaFeedbackTone.WARNING,
                                title = "Verification update"
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.isAppInForeground.collect { inForeground ->
                if (inForeground) {
                    val active = uiState.value.riderActiveTrip
                    if (active != null) {
                        maybeTriggerCardPaymentCheckout(active)
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.appResumedEvents.collect {
                onAppResumed()
            }
        }

        viewModelScope.launch {
            repository.supportCases.collect { cases ->
                val userId = uiState.value.activeUserId
                if (userId.isBlank()) {
                    lastObservedSupportCases = emptyList()
                    supportHydratedForUserId = null
                    return@collect
                }
                if (supportHydratedForUserId != userId) {
                    lastObservedSupportCases = cases
                    supportHydratedForUserId = userId
                    return@collect
                }
                val alerts = SupportNotificationHelper.diffSupportUpdates(lastObservedSupportCases, cases)
                lastObservedSupportCases = cases
                if (repository.privacyPreferences.value.supportNotificationsEnabled) {
                    alerts.forEach { _supportAlerts.tryEmit(it) }
                }
            }
        }

        viewModelScope.launch {
            repository.trips.collect { allTrips ->
                val userId = uiState.value.activeUserId
                val active = allTrips.firstOrNull { it.riderId == userId && it.status.isActive }
                val prevId = observedActiveRiderTripId
                if (active != null) {
                    observedActiveRiderTripId = active.id
                    maybeTriggerCardPaymentCheckout(active)
                } else if (prevId != null) {
                    val finished = allTrips.firstOrNull { it.id == prevId }
                    if (finished?.status == RideStatus.COMPLETED && finished.riderRating == null) {
                        localNav.update { it.copy(pendingRatingTripId = finished.id) }
                    }
                    observedActiveRiderTripId = null
                }

                val authSession = (repository.authState.value as? AuthState.Authenticated)?.session
                if (authSession == null || userId.isBlank()) {
                    chatHydratedForUserId = null
                    seenIncomingChatMessageIds.clear()
                    activeOpenChatTripId = null
                    if (localNav.value.unreadChatCountByTrip.isNotEmpty() || localNav.value.pendingOpenChatTripId != null) {
                        localNav.update { it.copy(unreadChatCountByTrip = emptyMap(), pendingOpenChatTripId = null) }
                    }
                } else {
                    val chatAlerts = RideNotificationHelper.diffIncomingTripMessages(
                        trips = allTrips,
                        currentUserId = userId,
                        role = authSession.role,
                        seenMessageIds = seenIncomingChatMessageIds,
                        seedOnly = chatHydratedForUserId != userId
                    )
                    chatHydratedForUserId = userId
                    val activeTripIds = allTrips.filter { it.status.isActive }.map { it.id }.toSet()
                    val unopenAlerts = chatAlerts.filter { it.tripId != activeOpenChatTripId }
                    localNav.update { nav ->
                        val updatedUnread = nav.unreadChatCountByTrip
                            .filterKeys { it in activeTripIds }
                            .toMutableMap()
                        unopenAlerts.forEach { alert ->
                            updatedUnread[alert.tripId] = (updatedUnread[alert.tripId] ?: 0) + 1
                        }
                        val nextPending = nav.pendingOpenChatTripId?.takeIf { it in activeTripIds }
                        if (updatedUnread == nav.unreadChatCountByTrip && nextPending == nav.pendingOpenChatTripId) {
                            nav
                        } else {
                            nav.copy(unreadChatCountByTrip = updatedUnread, pendingOpenChatTripId = nextPending)
                        }
                    }
                    unopenAlerts.forEach { _incomingChatAlerts.tryEmit(it) }
                }

                // Clear locally-expired markers once backend reflects terminal or assigned state
                val stillOpenIds = allTrips.filter { it.status.isOpenForDrivers && it.driverId == null }.map { it.id }.toSet()
                localNav.update { nav ->
                    val pruned = nav.locallyExpiredTripIds.intersect(stillOpenIds)
                    if (pruned == nav.locallyExpiredTripIds) nav else nav.copy(locallyExpiredTripIds = pruned)
                }

                // Record fallback ARRIVED timestamp if server hasn't supplied driverArrivedAt or arrivalConfirmedAt
                val nowMs = System.currentTimeMillis()
                val newFallbackArrivals = allTrips
                    .filter { it.status == RideStatus.ARRIVED && it.driverArrivedAt == null && it.arrivalConfirmedAt == null }
                    .mapNotNull { trip ->
                        if (trip.id in localNav.value.fallbackArrivalTimestamps) null else trip.id to nowMs
                    }
                    .toMap()
                if (newFallbackArrivals.isNotEmpty()) {
                    localNav.update { nav ->
                        nav.copy(fallbackArrivalTimestamps = nav.fallbackArrivalTimestamps + newFallbackArrivals)
                    }
                }

                updateSmartTripGuards(allTrips, userId, nowMs)

                // Clean up or promote queued Return Ride state when server status changes
                val queuedId = repository.driverQueuedReturnTripId.value
                val driverActiveCandidates = allTrips.filter { it.driverId == userId && it.status.isActive }
                val activeDriverTrip = driverActiveCandidates.filter { it.id != queuedId }
                    .ifEmpty { driverActiveCandidates }
                    .let { pool ->
                        pool.firstOrNull { it.status == RideStatus.IN_PROGRESS }
                            ?: pool.firstOrNull { it.status == RideStatus.ARRIVED }
                            ?: pool.firstOrNull()
                    }
                if (queuedId != null) {
                    val queuedTrip = allTrips.firstOrNull { it.id == queuedId }
                    val queuedClaimedElsewhere = queuedTrip != null &&
                        ((queuedTrip.driverId != null && queuedTrip.driverId != userId) ||
                            (!queuedTrip.status.isOpenForDrivers && !queuedTrip.status.isActive))
                    val outboundDoneAndQueuedNowPrimary = activeDriverTrip?.id == queuedId
                    if (queuedClaimedElsewhere || outboundDoneAndQueuedNowPrimary) {
                        repository.setDriverQueuedReturnTripId(null)
                    }
                }

                // Automatic Return Ride Radar once outbound driver trip is IN_PROGRESS
                if (authSession?.role == RideRole.DRIVER && activeDriverTrip?.status == RideStatus.IN_PROGRESS) {
                    if (trackedReturnRadarOutboundTripId != activeDriverTrip.id) {
                        trackedReturnRadarOutboundTripId = activeDriverTrip.id
                        seenReturnMatchTripIds.clear()
                    }
                    val currentQueuedId = repository.driverQueuedReturnTripId.value
                    val liveMatches = ReturnRideEngine.findReturnRideMatches(
                        trips = allTrips,
                        outboundTrip = activeDriverTrip,
                        declinedIds = repository.declinedDriverTripIds.value,
                        expiredIds = localNav.value.locallyExpiredTripIds,
                        driverLocation = localNav.value.driverCurrentCoords,
                        nowMs = nowMs
                    ).filter { it.trip.id != currentQueuedId }
                    val unseenMatches = liveMatches.filter { it.trip.id !in seenReturnMatchTripIds }
                    if (unseenMatches.isNotEmpty()) {
                        unseenMatches.forEach { seenReturnMatchTripIds.add(it.trip.id) }
                        if (currentQueuedId == null) {
                            _returnRideAlerts.tryEmit(unseenMatches.first())
                        }
                    }
                } else if (trackedReturnRadarOutboundTripId != null) {
                    trackedReturnRadarOutboundTripId = null
                    seenReturnMatchTripIds.clear()
                }

                // Automatically hydrate/refresh live turn-by-turn street route for active driver trip
                if (activeDriverTrip == null) {
                    if (localNav.value.driverNavRoute != null || localNav.value.driverNavRouteTripKey != null) {
                        driverNavFetchJob?.cancel()
                        lastDriverNavOrigin = null
                        localNav.update {
                            it.copy(
                                driverNavRoute = null,
                                driverNavRouteTripKey = null,
                                driverNavLoading = false,
                                driverNavFollowCamera = true
                            )
                        }
                    }
                } else {
                    if (localNav.value.driverRepositionZone != null || localNav.value.driverRepositionRoute != null) {
                        localNav.update {
                            it.copy(
                                driverRepositionZone = null,
                                driverRepositionRoute = null,
                                driverRepositionLoading = false
                            )
                        }
                    }
                    val legKey = "${activeDriverTrip.id}:${if (activeDriverTrip.status == RideStatus.IN_PROGRESS) "dest" else "pickup"}"
                    if (legKey != localNav.value.driverNavRouteTripKey) {
                        fetchDriverNavigationRouteInternal(
                            trip = activeDriverTrip,
                            originOverride = activeDriverTrip.liveDriverLocation ?: localNav.value.driverCurrentCoords,
                            force = true
                        )
                    }
                }
            }
        }

        // Periodic polling while authenticated (initial login/profile load runs immediately in authState.collect)
        viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                if (repository.authState.value is AuthState.Authenticated) {
                    repository.refreshActiveSessionData()
                        .onSuccess {
                            localNav.update {
                                it.copy(isInitialSyncing = false, apiConnected = true, syncError = null)
                            }
                        }
                        .onFailure { err ->
                            val msg = friendlyApiErrorMessage(err, "Unable to sync with Vhaya. Retrying...")
                            localNav.update {
                                it.copy(isInitialSyncing = false, apiConnected = false, syncError = msg)
                            }
                        }
                }
            }
        }

        // Foreground auto-polling while waiting on email verification so newly verified users transition automatically
        viewModelScope.launch {
            while (isActive) {
                delay(2_500L)
                if (repository.authState.value is AuthState.EmailVerificationPending &&
                    repository.isAppInForeground.value
                ) {
                    checkEmailVerified(silent = true)
                }
            }
        }

        // Live 1-second ticker for open-request countdowns, smart guards, and auto-expiration
        viewModelScope.launch {
            while (isActive) {
                if (repository.authState.value is AuthState.Authenticated) {
                    val now = System.currentTimeMillis()
                    localNav.update { it.copy(nowEpochMs = now) }
                    val stateSnapshot = uiState.value
                    updateSmartTripGuards(stateSnapshot.trips, stateSnapshot.activeUserId, now)
                    checkAndExpireStaleTrips(now)
                }
                delay(1_000L)
            }
        }
    }

    private fun updateSmartTripGuards(allTrips: List<RideTrip>, userId: String, nowMs: Long) {
        if (userId.isBlank()) {
            if (localNav.value.driverStallAssessment != null || localNav.value.riderOffRouteAssessment != null) {
                localNav.update { it.copy(driverStallAssessment = null, riderOffRouteAssessment = null) }
            }
            return
        }
        val riderTrip = allTrips.firstOrNull { it.riderId == userId && it.status.isActive }
        if (riderTrip == null) {
            if (localNav.value.driverStallAssessment != null || localNav.value.riderOffRouteAssessment != null) {
                localNav.update { it.copy(driverStallAssessment = null, riderOffRouteAssessment = null) }
            }
            return
        }
        val driverCoords = riderTrip.liveDriverLocation ?: riderTrip.driverProfile?.lastLocation
        val pickupCoords = riderTrip.pickupCoordinates ?: riderTrip.route?.originCoordinates
        val nextStall = if (riderTrip.status == RideStatus.ACCEPTED && driverCoords != null && pickupCoords != null) {
            val dist = LaunchPolicy.distanceMeters(driverCoords, pickupCoords)
            val tracker = SmartRideGuards.advanceStallTracker(stallTrackers[riderTrip.id], riderTrip.id, dist, nowMs)
            stallTrackers[riderTrip.id] = tracker
            SmartRideGuards.evaluateDriverApproachHealth(riderTrip, driverCoords, tracker, nowMs)
        } else {
            null
        }
        val nextOffRoute = if (riderTrip.status == RideStatus.IN_PROGRESS && driverCoords != null) {
            SmartRideGuards.evaluateOffRoute(driverCoords, riderTrip)
        } else {
            null
        }
        localNav.update { nav ->
            if (nav.driverStallAssessment == nextStall && nav.riderOffRouteAssessment == nextOffRoute) {
                nav
            } else {
                nav.copy(driverStallAssessment = nextStall, riderOffRouteAssessment = nextOffRoute)
            }
        }
    }

    private fun checkAndExpireStaleTrips(nowMs: Long) {
        val state = uiState.value
        val userId = state.activeUserId
        if (userId.isBlank()) return
        state.trips.forEach { trip ->
            if (trip.status.isOpenForDrivers && trip.driverId == null && LaunchPolicy.isTripExpired(trip, nowMs)) {
                if (trip.id !in localNav.value.locallyExpiredTripIds) {
                    localNav.update { it.copy(locallyExpiredTripIds = it.locallyExpiredTripIds + trip.id) }
                }
                if (trip.riderId == userId && trip.id !in expiringTripIds) {
                    expireTripInternal(trip)
                }
            }
        }
    }

    private fun expireTripInternal(trip: RideTrip) {
        expiringTripIds += trip.id
        localNav.update {
            it.copy(
                locallyExpiredTripIds = it.locallyExpiredTripIds + trip.id,
                expiredTripNotice = trip
            )
        }
        toast("Ride request expired — no driver accepted in time")
        viewModelScope.launch {
            repository.expireOpenTrip(trip.id)
                .onFailure {
                    // Refresh session so state stays aligned if another actor updated the trip simultaneously
                    repository.refreshActiveSessionData()
                }
            expiringTripIds -= trip.id
        }
    }

    private fun toast(
        message: String,
        tone: VhayaFeedbackTone? = null,
        title: String? = null
    ) {
        _events.tryEmit(message)
        _feedbackEvents.tryEmit(
            VhayaFeedbackClassifier.classify(
                message = message,
                explicitTone = tone,
                title = title
            )
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        repository.setThemeMode(mode)
    }

    fun setLowDataMode(enabled: Boolean) {
        repository.setLowDataMode(enabled)
        toast(
            message = if (enabled) "Low-Data Map enabled · raster map tiles paused" else "Standard live map tiles enabled",
            tone = VhayaFeedbackTone.INFO
        )
    }

    fun setAppLanguage(language: AppLanguage) {
        repository.setAppLanguage(language)
        toast(
            message = "Language set to ${language.label}",
            tone = VhayaFeedbackTone.INFO
        )
    }

    fun setNotificationSoundStyle(style: NotificationSoundStyle) {
        repository.setNotificationSoundStyle(style)
    }

    // Deep links (vhaya://payment-return?reference=... & vhaya://ride/...)
    fun handleDeepLink(uri: Uri) {
        val scheme = uri.scheme?.lowercase() ?: return
        if (scheme != "vhaya") return
        val host = uri.host?.lowercase().orEmpty()
        when (host) {
            "payment-return" -> {
                val ref = uri.getQueryParameter("reference")
                    ?: uri.getQueryParameter("trxref")
                    ?: return
                verifyPaymentReference(ref)
            }
            "ride" -> {
                val action = uri.getQueryParameter("action")
                val tripId = uri.getQueryParameter("tripId")
                val role = uiState.value.activeRole
                when (role) {
                    RideRole.RIDER -> localNav.update { it.copy(riderTab = RiderTab.RIDE) }
                    RideRole.DRIVER -> localNav.update { it.copy(driverTab = DriverTab.DRIVE) }
                    RideRole.ADMIN -> localNav.update { it.copy(adminTab = AdminTab.OVERVIEW) }
                }
                if (action.equals("accept", ignoreCase = true) && !tripId.isNullOrBlank()) {
                    localNav.update { it.copy(driverTab = DriverTab.DRIVE) }
                    acceptRideRequestFromNotification(tripId)
                } else if (action.equals("chat", ignoreCase = true) && !tripId.isNullOrBlank()) {
                    openTripChat(tripId)
                    viewModelScope.launch { repository.refreshActiveSessionData() }
                } else {
                    viewModelScope.launch { repository.refreshActiveSessionData() }
                }
            }
            "support" -> {
                when (uiState.value.activeRole) {
                    RideRole.RIDER -> localNav.update { it.copy(riderTab = RiderTab.SUPPORT) }
                    RideRole.ADMIN -> localNav.update { it.copy(adminTab = AdminTab.SUPPORT) }
                    RideRole.DRIVER -> localNav.update { it.copy(driverTab = DriverTab.PROFILE) }
                }
                viewModelScope.launch { repository.refreshActiveSessionData() }
            }
        }
    }

    fun acceptRideRequestFromNotification(tripId: String) {
        if (!beginBusy("driver_offer")) return
        viewModelScope.launch {
            try {
                var target = repository.getCachedTripsSnapshot().firstOrNull { it.id == tripId }
                if (target == null) {
                    repository.refreshActiveSessionData()
                    target = repository.getCachedTripsSnapshot().firstOrNull { it.id == tripId }
                }
                val nowMs = System.currentTimeMillis()
                if (target == null || !target.status.isOpenForDrivers || target.driverId != null || LaunchPolicy.isTripExpired(target, nowMs)) {
                    repository.refreshActiveSessionData()
                    toast("This ride request is no longer available")
                    return@launch
                }
                repository.driverSubmitOffer(target, target.fare, localNav.value.driverCurrentCoords)
                    .onSuccess { updated ->
                        val msg = if (updated.driverId != null) {
                            "Ride accepted · head to pickup"
                        } else {
                            "Offer sent at ${LaunchPolicy.formatZar(target.fare)} · awaiting rider"
                        }
                        toast(msg)
                    }
                    .onFailure { reportActionFailure(it, "Could not accept ride request") }
            } finally {
                endBusy("driver_offer")
            }
        }
    }

    fun googleWebClientId(): String = repository.googleWebClientId()

    suspend fun resolveGoogleWebClientId(): Result<String> = repository.resolveGoogleWebClientId()

    // Authentication actions
    fun signIn(email: String, password: String) {
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            handleSignInResult(repository.signInWithEmailPassword(email, password))
        }
    }

    fun signInWithGoogle(idToken: String) {
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            handleSignInResult(repository.signInWithGoogleIdToken(idToken))
        }
    }

    private fun handleSignInResult(result: Result<UserSession>) {
        result
            .onSuccess { session ->
                driverAddressJob?.cancel()
                lastDriverAddressCoords = null
                localNav.update {
                    it.copy(
                        authBusy = false,
                        authError = null,
                        riderTab = RiderTab.RIDE,
                        driverTab = DriverTab.DRIVE,
                        adminTab = AdminTab.OVERVIEW,
                        driverHasGpsFix = false,
                        driverCurrentAddress = null,
                        driverAddressLoading = false
                    )
                }
                if (session.emailVerified) {
                    toast(
                        message = "Signed in as ${session.displayName} (${session.role.label})",
                        tone = VhayaFeedbackTone.SUCCESS,
                        title = "Welcome back"
                    )
                } else {
                    localNav.update {
                        it.copy(
                            authNotice = "Your email isn't verified yet. Tap the link in your inbox and we'll continue automatically."
                        )
                    }
                }
            }
            .onFailure { err ->
                localNav.update { it.copy(authBusy = false, authError = err.message ?: "Sign-in failed") }
            }
    }

    fun showAuthInlineError(message: String) {
        localNav.update { it.copy(authError = message, authNotice = null) }
    }

    fun signUp(displayName: String, email: String, password: String) {
        val cleanName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.isBlank()) {
            showAuthInlineError("Enter your full name.")
            return
        }
        if (!cleanEmail.contains("@") || !cleanEmail.substringAfter("@").contains(".")) {
            showAuthInlineError("Enter a valid email address.")
            return
        }
        LaunchPolicy.evaluatePasswordPolicy(password).validationErrorOrNull()?.let { policyError ->
            showAuthInlineError(policyError)
            return
        }
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.signUpWithEmailPassword(cleanName, cleanEmail, password)
                .onSuccess {
                    startVerificationResendCooldown(45)
                    localNav.update {
                        it.copy(
                            authBusy = false,
                            authNotice = "Account created. Open the verification email in your inbox to continue."
                        )
                    }
                }
                .onFailure { err ->
                    localNav.update { it.copy(authBusy = false, authError = err.message ?: "Could not create account") }
                }
        }
    }

    fun onAppResumed() {
        when (val auth = repository.authState.value) {
            is AuthState.EmailVerificationPending -> checkEmailVerified(silent = true, forceOnResume = true)
            is AuthState.Authenticated -> {
                val active = uiState.value.riderActiveTrip
                if (active != null) {
                    maybeTriggerCardPaymentCheckout(active)
                }
                if (hasCompletedInitialSyncForUserId == auth.session.userId) {
                    viewModelScope.launch {
                        repository.refreshActiveSessionData()
                            .onSuccess {
                                localNav.update { it.copy(apiConnected = true, syncError = null) }
                            }
                    }
                }
            }
            AuthState.Unauthenticated -> Unit
        }
    }

    fun checkEmailVerified(silent: Boolean = false, forceOnResume: Boolean = false) {
        if (silent && !forceOnResume && (verificationCheckJob?.isActive == true || localNav.value.authBusy)) {
            return
        }
        if (!silent) {
            localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        }
        verificationCheckJob = viewModelScope.launch {
            repository.checkEmailVerified()
                .onSuccess { verified ->
                    if (verified) {
                        localNav.update {
                            it.copy(
                                authBusy = false,
                                authError = null,
                                authNotice = null
                            )
                        }
                        val userId = (repository.authState.value as? AuthState.Authenticated)?.session?.userId
                        if (userId != null && lastToastedVerifiedUserId != userId) {
                            lastToastedVerifiedUserId = userId
                            toast(
                                message = "Email verified! Welcome to Vhaya.",
                                tone = VhayaFeedbackTone.SUCCESS,
                                title = "Account verified"
                            )
                        }
                    } else if (!silent) {
                        localNav.update {
                            it.copy(
                                authBusy = false,
                                authNotice = "Still awaiting verification. Tap the link in your inbox, then we'll log you in automatically."
                            )
                        }
                    }
                }
                .onFailure { err ->
                    val msg = err.message ?: "Could not verify email status."
                    val sessionExpired = msg.contains("expired", ignoreCase = true)
                    if (!silent || sessionExpired) {
                        localNav.update {
                            it.copy(
                                authBusy = false,
                                authError = msg
                            )
                        }
                    }
                }
        }
    }

    fun resendVerificationEmail() {
        if (localNav.value.verificationResendCooldownSeconds > 0) return
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.resendVerificationEmail()
                .onSuccess {
                    startVerificationResendCooldown(45)
                    localNav.update {
                        it.copy(
                            authBusy = false,
                            authNotice = "Verification link sent. Check your inbox and spam folder."
                        )
                    }
                }
                .onFailure { err -> localNav.update { it.copy(authBusy = false, authError = err.message) } }
        }
    }

    private fun startVerificationResendCooldown(seconds: Int) {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                localNav.update { it.copy(verificationResendCooldownSeconds = remaining) }
                delay(1_000L)
            }
            localNav.update { it.copy(verificationResendCooldownSeconds = 0) }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            localNav.update { it.copy(authError = "Enter your email address first.") }
            return
        }
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.sendPasswordReset(email)
                .onSuccess {
                    localNav.update {
                        it.copy(authBusy = false, authNotice = "Password reset instructions sent to $email.")
                    }
                }
                .onFailure { err ->
                    localNav.update { it.copy(authBusy = false, authError = err.message) }
                }
        }
    }

    private fun beginBusy(key: String): Boolean {
        if (key in localNav.value.busyActions) return false
        localNav.update { it.copy(busyActions = it.busyActions + key, actionError = null) }
        return true
    }

    private fun endBusy(key: String) {
        localNav.update { it.copy(busyActions = it.busyActions - key) }
    }

    private fun reportActionFailure(error: Throwable?, fallback: String) {
        val msg = friendlyApiErrorMessage(error, fallback)
        localNav.update { it.copy(actionError = msg) }
        toast(msg, tone = VhayaFeedbackTone.ERROR)
    }

    fun dismissSyncError() {
        localNav.update { it.copy(syncError = null, actionError = null) }
    }

    fun dismissExpiredTripNotice() {
        localNav.update { it.copy(expiredTripNotice = null) }
    }

    fun rebookExpiredTrip(trip: RideTrip) {
        rebookTrip(trip)
    }

    fun rebookTrip(trip: RideTrip) {
        val pickupCoords = trip.pickupCoordinates ?: localNav.value.booking.pickupCoords
        val destCoords = trip.destinationCoordinates
        localNav.update { state ->
            state.copy(
                expiredTripNotice = null,
                riderTab = RiderTab.RIDE,
                booking = state.booking.copy(
                    step = if (destCoords != null) RiderBookingStep.CHOOSE_RIDE_FARE else RiderBookingStep.SEARCH_DESTINATION,
                    pickupLabel = trip.pickup,
                    pickupCoords = pickupCoords,
                    destinationLabel = trip.destination,
                    destinationCoords = destCoords,
                    category = trip.category,
                    paymentMethod = trip.paymentMethod,
                    pickupNote = trip.pickupNote.orEmpty(),
                    cashChangeForZar = trip.cashChangeForZar,
                    beneficiaryEnabled = !trip.beneficiaryName.isNullOrBlank(),
                    beneficiaryName = trip.beneficiaryName.orEmpty(),
                    beneficiaryPhone = trip.beneficiaryPhone.orEmpty(),
                    isSharedRide = trip.isSharedRide,
                    seatCount = trip.seatCount.coerceIn(1, 4),
                    isReturnTrip = trip.isReturnTrip,
                    isReturnRequest = trip.isReturnRequest,
                    includesGravelRoad = trip.includesGravelRoad,
                    activePreset = null,
                    previewRoute = null,
                    visualRoute = null,
                    routeLoading = destCoords != null,
                    routeError = null,
                    customOfferText = "",
                    isOfferExpanded = false
                )
            )
        }
        if (destCoords != null) {
            fetchLiveRoutePreview(trip.pickup, trip.destination, pickupCoords, destCoords)
        }
    }

    fun applyRuralPreset(preset: RuralRunPreset) {
        cancelPlaceSearch()
        val currentDest = localNav.value.booking.destinationCoords
        val currentPickup = localNav.value.booking.pickupCoords
        val currentPickupLabel = localNav.value.booking.pickupLabel
        val currentDestLabel = localNav.value.booking.destinationLabel
        localNav.update { state ->
            val nextStep = if (currentDest != null) RiderBookingStep.CHOOSE_RIDE_FARE else RiderBookingStep.SEARCH_DESTINATION
            state.copy(
                riderTab = RiderTab.RIDE,
                placeSearchError = null,
                booking = state.booking.copy(
                    step = nextStep,
                    pickingForPickup = false,
                    category = preset.category,
                    isSharedRide = preset.isSharedRide,
                    seatCount = preset.seatCount.coerceIn(1, 4),
                    isReturnTrip = preset.isReturnTrip,
                    isReturnRequest = false,
                    includesGravelRoad = preset.includesGravelRoad,
                    beneficiaryEnabled = preset.beneficiaryEnabled,
                    pickupNote = preset.defaultPickupNote,
                    activePreset = preset,
                    customOfferText = "",
                    isOfferExpanded = false
                )
            )
        }
        if (currentDest != null) {
            fetchLiveRoutePreview(currentPickupLabel, currentDestLabel, currentPickup, currentDest)
        }
        val mappedCategory = when (preset) {
            RuralRunPreset.CLINIC_RUN -> NearbyPlaceCategory.CLINICS
            RuralRunPreset.PENSION_DAY -> NearbyPlaceCategory.PAYPOINTS
            RuralRunPreset.TOWN_GROCERY -> NearbyPlaceCategory.SHOPPING
            RuralRunPreset.FAMILY_PARCEL -> NearbyPlaceCategory.TRANSIT
        }
        refreshNearbyPlacesIfNeeded(origin = currentPickup, category = mappedCategory, force = false)
        toast(
            message = "${preset.title} ready · ${preset.subtitle}",
            tone = VhayaFeedbackTone.INFO
        )
    }

    fun clearRuralPreset() {
        localNav.update { it.copy(booking = it.booking.copy(activePreset = null)) }
        refreshNearbyPlacesIfNeeded(
            origin = localNav.value.booking.pickupCoords,
            category = NearbyPlaceCategory.ALL,
            force = false
        )
    }

    fun toggleTrustedDriver(driver: DriverProfileSnapshot): Boolean {
        val nowTrusted = repository.toggleTrustedDriver(driver)
        if (nowTrusted) {
            toast(
                message = "${driver.name} added to your Community Drivers Circle — future bids get priority",
                tone = VhayaFeedbackTone.SUCCESS,
                title = "Trusted driver saved"
            )
        } else {
            toast(
                message = "${driver.name} removed from Community Drivers",
                tone = VhayaFeedbackTone.INFO
            )
        }
        return nowTrusted
    }

    fun removeTrustedDriver(driverId: String) {
        val name = repository.trustedDrivers.value.firstOrNull { it.driverId == driverId }?.name ?: "Driver"
        repository.removeTrustedDriver(driverId)
        toast("$name removed from Community Drivers", tone = VhayaFeedbackTone.INFO)
    }

    fun signOut() {
        if (!beginBusy("sign_out")) return
        viewModelScope.launch {
            try {
                repository.signOut()
                observedActiveRiderTripId = null
                hasCompletedInitialSyncForUserId = null
                expiringTripIds.clear()
                localNav.update { LocalNavState() }
            } finally {
                endBusy("sign_out")
            }
        }
    }

    fun clearAuthMessages() {
        localNav.update { it.copy(authError = null, authNotice = null) }
    }

    fun setAccountSheetVisible(visible: Boolean) {
        localNav.update { it.copy(showAccountSheet = visible) }
    }

    fun setPrivacySheetVisible(visible: Boolean) {
        localNav.update { it.copy(showPrivacySheet = visible) }
    }

    fun updatePrivacyPreferences(prefs: PrivacyPreferences) {
        repository.updatePrivacyPreferences(prefs)
    }

    fun setLocationSharingConsent(enabled: Boolean) {
        val current = repository.privacyPreferences.value
        repository.updatePrivacyPreferences(current.copy(locationSharingConsent = enabled))
        toast(if (enabled) "Live location sharing enabled" else "Live location sharing paused")
    }

    fun setTripOnlyPrivacyMode(enabled: Boolean) {
        val current = repository.privacyPreferences.value
        repository.updatePrivacyPreferences(current.copy(tripOnlyPrivacyMode = enabled))
        toast(
            if (enabled) "Trip-only privacy enabled · GPS shares only while on trip"
            else "Standard pickup & trip GPS sharing enabled"
        )
    }

    fun setSupportNotificationsEnabled(enabled: Boolean) {
        val current = repository.privacyPreferences.value
        repository.updatePrivacyPreferences(current.copy(supportNotificationsEnabled = enabled))
    }

    fun hasRequestedLocationPermission(): Boolean = repository.hasRequestedLocationPermission()
    fun markLocationPermissionRequested() = repository.markLocationPermissionRequested()

    fun exportPopiaDataJson(onReady: (String) -> Unit) {
        if (!beginBusy("popia_export")) return
        viewModelScope.launch {
            try {
                val json = repository.buildPopiaExportJson()
                onReady(json)
            } catch (t: Throwable) {
                reportActionFailure(t, "Could not prepare personal data export")
            } finally {
                endBusy("popia_export")
            }
        }
    }

    fun deleteAccountPermanently(reason: String, onCompleted: () -> Unit = {}) {
        if (!beginBusy("popia_delete")) return
        viewModelScope.launch {
            try {
                repository.deleteAccountAndWipeLocalData(reason)
                    .onSuccess { outcome ->
                        observedActiveRiderTripId = null
                        hasCompletedInitialSyncForUserId = null
                        supportHydratedForUserId = null
                        lastObservedSupportCases = emptyList()
                        expiringTripIds.clear()
                        localNav.update { LocalNavState() }
                        onCompleted()
                        val msg = when {
                            outcome.backendDeleted -> "Your Vhaya account and personal data have been permanently deleted."
                            outcome.authDeleted -> "Account login deleted and device wiped. Server trip archive removal requested."
                            else -> "Account wiped from this device."
                        }
                        toast(msg, tone = VhayaFeedbackTone.INFO)
                    }
                    .onFailure { err ->
                        reportActionFailure(err, "Could not delete account")
                    }
            } finally {
                endBusy("popia_delete")
            }
        }
    }

    fun requestPopiaDataDeletion(reason: String) {
        if (!beginBusy("popia_delete")) return
        viewModelScope.launch {
            try {
                repository.requestPopiaDataDeletion(reason)
                    .onSuccess { submittedTicket ->
                        if (submittedTicket) {
                            toast("POPIA deletion request logged with Vhaya Support")
                        } else {
                            toast("Email ${LaunchPolicy.SUPPORT_EMAIL} or use 'Delete account now' for instant erasure.")
                        }
                    }
                    .onFailure { err ->
                        reportActionFailure(err, "Could not log deletion request")
                    }
            } finally {
                endBusy("popia_delete")
            }
        }
    }

    fun clearAllLocalUserDataAndSignOut() {
        if (!beginBusy("popia_wipe")) return
        viewModelScope.launch {
            try {
                repository.clearAllLocalUserDataAndSignOut()
                observedActiveRiderTripId = null
                hasCompletedInitialSyncForUserId = null
                supportHydratedForUserId = null
                lastObservedSupportCases = emptyList()
                expiringTripIds.clear()
                localNav.update { LocalNavState() }
                toast("Local device cache cleared and signed out")
            } finally {
                endBusy("popia_wipe")
            }
        }
    }

    fun flushOfflineQueueNow() {
        viewModelScope.launch {
            val flushed = repository.flushOfflineQueue(force = true)
            if (flushed > 0) {
                toast("Sent $flushed queued update${if (flushed == 1) "" else "s"}")
            }
            refreshNow(showToastOnSuccess = flushed == 0)
        }
    }

    fun retrySync() {
        refreshNow(showToastOnSuccess = true)
    }

    fun refreshNow(showToastOnSuccess: Boolean = false) {
        if (localNav.value.isRefreshing) return
        localNav.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            repository.refreshActiveSessionData()
                .onSuccess {
                    localNav.update {
                        it.copy(isRefreshing = false, apiConnected = true, syncError = null, actionError = null)
                    }
                    if (showToastOnSuccess) toast("Synced with Vhaya")
                }
                .onFailure { err ->
                    val msg = friendlyApiErrorMessage(err, "Refresh failed")
                    localNav.update { it.copy(isRefreshing = false, apiConnected = false, syncError = msg) }
                }
        }
    }

    // Payments & Wallet
    fun initializeCardPayment(trip: RideTrip) {
        if (!beginBusy("card_hold")) return
        viewModelScope.launch {
            try {
                repository.initializeCardPayment(trip.id)
                    .onSuccess { outcome -> handlePaymentInitOutcome(outcome) }
                    .onFailure { reportActionFailure(it, "Could not open Paystack checkout") }
            } finally {
                endBusy("card_hold")
            }
        }
    }

    fun initializeWalletTopUp(amountZar: Double) {
        if (!beginBusy("wallet_topup")) return
        viewModelScope.launch {
            try {
                repository.initializeWalletTopUp(amountZar)
                    .onSuccess { outcome -> handlePaymentInitOutcome(outcome) }
                    .onFailure { reportActionFailure(it, "Could not start wallet top-up") }
            } finally {
                endBusy("wallet_topup")
            }
        }
    }

    fun redeemWalletVoucher(code: String) {
        if (!beginBusy("wallet_voucher")) return
        viewModelScope.launch {
            try {
                repository.redeemWalletVoucher(code)
                    .onSuccess { summary ->
                        toast(
                            message = "Voucher redeemed · Wallet balance R %.2f".format(summary.balanceMinor / 100.0),
                            tone = VhayaFeedbackTone.SUCCESS,
                            title = "Wallet credited"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not redeem voucher") }
            } finally {
                endBusy("wallet_voucher")
            }
        }
    }

    fun sendFamilyWalletCredit(recipient: String, amountZar: Double, note: String? = null) {
        if (!beginBusy("wallet_transfer")) return
        viewModelScope.launch {
            try {
                repository.transferWalletCredit(recipient, amountZar, note)
                    .onSuccess { summary ->
                        toast(
                            message = "Sent ${LaunchPolicy.formatZar(amountZar)} to $recipient · Balance R %.2f".format(summary.balanceMinor / 100.0),
                            tone = VhayaFeedbackTone.SUCCESS,
                            title = "Credit sent"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not send family credit") }
            } finally {
                endBusy("wallet_transfer")
            }
        }
    }

    fun initializeDriverWeeklySubscription(amountMinor: Long = 19900L) {
        if (!beginBusy("driver_pass")) return
        viewModelScope.launch {
            try {
                repository.initializeDriverWeeklySubscription(amountMinor)
                    .onSuccess { outcome -> handlePaymentInitOutcome(outcome) }
                    .onFailure { reportActionFailure(it, "Could not initialize Weekly Driver Pass") }
            } finally {
                endBusy("driver_pass")
            }
        }
    }

    private fun handlePaymentInitOutcome(outcome: PaymentInitOutcome) {
        if (outcome.mode == "mock") {
            if (!BuildConfig.ALLOW_MOCK_PAYMENTS) {
                reportActionFailure(
                    IllegalStateException("Mock payments are disabled in release builds."),
                    "Mock payments are unavailable in this build."
                )
                return
            }
            localNav.update { it.copy(pendingMockPayment = outcome) }
            return
        }

        val authorizationUrl = outcome.authorizationUrl?.takeIf { PaystackReturnMatcher.isSafeCheckoutUrl(it) }
        if (authorizationUrl == null) {
            reportActionFailure(
                IllegalStateException("Payment provider did not return a valid checkout URL."),
                "Could not open payment checkout."
            )
            return
        }
        localNav.update { it.copy(activePaystackCheckout = outcome.copy(authorizationUrl = authorizationUrl)) }
    }

    fun completeInAppCheckout(reference: String, tripIdHint: String? = null) {
        val cleanRef = reference.trim().ifBlank {
            localNav.value.activePaystackCheckout?.reference.orEmpty()
        }
        val resolvedTripId = tripIdHint ?: localNav.value.activePaystackCheckout?.tripId
        localNav.update { it.copy(activePaystackCheckout = null) }
        if (cleanRef.isNotBlank()) {
            verifyPaymentReference(cleanRef, resolvedTripId)
        }
    }

    fun dismissInAppCheckout() {
        localNav.update { it.copy(activePaystackCheckout = null) }
        viewModelScope.launch {
            repository.refreshActiveSessionData()
        }
    }

    fun openActiveCheckoutInExternalBrowser() {
        val url = localNav.value.activePaystackCheckout?.authorizationUrl?.takeIf { it.isNotBlank() } ?: return
        localNav.update { it.copy(activePaystackCheckout = null) }
        _openUrlEvents.tryEmit(url)
    }

    fun submitMockPaymentDecision(success: Boolean) {
        if (!BuildConfig.ALLOW_MOCK_PAYMENTS) {
            localNav.update { it.copy(pendingMockPayment = null) }
            reportActionFailure(
                IllegalStateException("Mock payments are disabled in release builds."),
                "Mock payments are unavailable in this build."
            )
            return
        }
        val pending = localNav.value.pendingMockPayment ?: return
        if (!beginBusy("mock_payment")) return
        localNav.update { it.copy(pendingMockPayment = null) }
        viewModelScope.launch {
            try {
                repository.submitMockPaymentResult(pending.reference, success)
                    .onSuccess { result ->
                        localNav.update { it.copy(activePaymentVerification = result) }
                    }
                    .onFailure { err ->
                        reportActionFailure(err, "Payment confirmation failed")
                    }
            } finally {
                endBusy("mock_payment")
            }
        }
    }

    fun dismissMockPaymentDialog() {
        localNav.update { it.copy(pendingMockPayment = null) }
    }

    fun releaseCardHold(tripId: String) {
        if (!beginBusy("card_hold")) return
        viewModelScope.launch {
            try {
                repository.releaseCardHold(tripId)
                    .onSuccess { toast("Card hold released") }
                    .onFailure { reportActionFailure(it, "Could not release card hold") }
            } finally {
                endBusy("card_hold")
            }
        }
    }

    fun verifyPaymentReference(reference: String, tripIdHint: String? = null) {
        localNav.update {
            it.copy(
                activePaystackCheckout = null,
                activePaymentVerification = PaymentVerificationResult(
                    reference = reference,
                    tripId = tripIdHint,
                    state = PaymentVerificationState.CHECKING,
                    amountMinor = 0L,
                    message = "Verifying Paystack payment..."
                )
            )
        }
        viewModelScope.launch {
            repository.verifyPaymentReference(reference, tripIdHint)
                .onSuccess { res -> localNav.update { it.copy(activePaymentVerification = res) } }
                .onFailure { err ->
                    localNav.update {
                        it.copy(
                            activePaymentVerification = PaymentVerificationResult(
                                reference = reference,
                                tripId = tripIdHint,
                                state = PaymentVerificationState.FAILED,
                                amountMinor = 0L,
                                message = friendlyApiErrorMessage(err, "Payment verification failed.")
                            )
                        )
                    }
                }
        }
    }

    fun dismissPaymentVerification() {
        localNav.update { it.copy(activePaymentVerification = null) }
    }

    // Tabs
    fun selectRiderTab(tab: RiderTab) = localNav.update { it.copy(riderTab = tab) }
    fun selectDriverTab(tab: DriverTab) = localNav.update { it.copy(driverTab = tab) }
    fun selectAdminTab(tab: AdminTab) = localNav.update { it.copy(adminTab = tab) }

    // Requester Booking Actions
    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        val requestId = ++placeSearchRequestId
        repository.clearPlaceSuggestions()
        localNav.update { it.copy(placeSearchError = null) }
        if (query.trim().length < 2) {
            placeSearchJob = null
            return
        }

        val proximity = localNav.value.booking.pickupCoords
        placeSearchJob = viewModelScope.launch {
            val result = repository.searchPlaces(query, proximity)
            if (requestId != placeSearchRequestId) return@launch
            result.onFailure { err ->
                val msg = friendlyApiErrorMessage(err, "Could not search places. Check your connection.")
                localNav.update { it.copy(placeSearchError = msg) }
            }
        }
    }

    private fun cancelPlaceSearch() {
        placeSearchRequestId += 1L
        placeSearchJob?.cancel()
        placeSearchJob = null
        repository.clearPlaceSuggestions()
        localNav.update { it.copy(placeSearchError = null) }
    }

    fun clearPlaceSearchError() {
        localNav.update { it.copy(placeSearchError = null) }
    }

    fun selectNearbyCategory(category: NearbyPlaceCategory) {
        val origin = if (uiState.value.activeRole == RideRole.DRIVER) {
            localNav.value.driverCurrentCoords
        } else {
            localNav.value.booking.pickupCoords
        }
        refreshNearbyPlacesIfNeeded(origin = origin, category = category, force = true)
    }

    fun refreshNearbyPlacesIfNeeded(
        origin: RideCoordinates = localNav.value.booking.pickupCoords,
        category: NearbyPlaceCategory? = null,
        force: Boolean = false
    ) {
        if (!LaunchPolicy.isValidDeviceCoordinate(origin)) return
        val targetCategory = category ?: repository.selectedNearbyCategory.value
        val prevOrigin = lastNearbyFetchOrigin
        val movedMeters = prevOrigin?.let { LaunchPolicy.distanceMeters(it, origin) } ?: Double.MAX_VALUE
        if (!force && lastNearbyFetchCategory == targetCategory && movedMeters < 800.0 && repository.nearbyPlaces.value.isNotEmpty()) {
            return
        }
        lastNearbyFetchOrigin = origin
        lastNearbyFetchCategory = targetCategory
        nearbyPlacesJob?.cancel()
        nearbyPlacesJob = viewModelScope.launch {
            repository.loadNearbyPlaces(origin, targetCategory)
        }
    }

    fun openSearchSheet(forPickup: Boolean = false) {
        cancelPlaceSearch()
        localNav.update {
            it.copy(
                placeSearchError = null,
                booking = it.booking.copy(step = RiderBookingStep.SEARCH_DESTINATION, pickingForPickup = forPickup)
            )
        }
        refreshNearbyPlacesIfNeeded(origin = localNav.value.booking.pickupCoords, force = false)
    }

    fun openMapPinPicker(forPickup: Boolean) {
        cancelPlaceSearch()
        pinPreviewJob?.cancel()
        localNav.update {
            it.copy(
                booking = it.booking.copy(
                    step = RiderBookingStep.MAP_PIN_PICKER,
                    pickingForPickup = forPickup,
                    pinPreviewAddress = null,
                    pinPreviewLoading = false
                )
            )
        }
    }

    fun previewPinLocation(coords: RideCoordinates) {
        pinPreviewJob?.cancel()
        val hubMatch = LaunchPolicy.findNearestHubName(coords)
        if (hubMatch != null) {
            localNav.update { state ->
                state.copy(
                    booking = state.booking.copy(
                        pinPreviewAddress = hubMatch,
                        pinPreviewLoading = false
                    )
                )
            }
            return
        }
        localNav.update { state ->
            state.copy(booking = state.booking.copy(pinPreviewLoading = true))
        }
        pinPreviewJob = viewModelScope.launch {
            delay(420L)
            val resolved = repository.reverseGeocodeLabel(coords).getOrElse {
                LaunchPolicy.nearestLandmarkLabel(coords)
            }
            localNav.update { state ->
                if (state.booking.step == RiderBookingStep.MAP_PIN_PICKER) {
                    state.copy(
                        booking = state.booking.copy(
                            pinPreviewAddress = resolved,
                            pinPreviewLoading = false
                        )
                    )
                } else state
            }
        }
    }

    fun backToCompactHome() {
        pinPreviewJob?.cancel()
        cancelPlaceSearch()
        localNav.update {
            it.copy(
                booking = BookingDraft(
                    pickupLabel = it.booking.pickupLabel,
                    pickupCoords = it.booking.pickupCoords,
                    gpsUsingDeviceLocation = it.booking.gpsUsingDeviceLocation,
                    paymentMethod = it.booking.paymentMethod
                )
            )
        }
    }

    fun stepBackInBooking() {
        if (localNav.value.booking.step == RiderBookingStep.SEARCH_DESTINATION) {
            cancelPlaceSearch()
        }
        pinPreviewJob?.cancel()
        localNav.update { state ->
            val prev = when (state.booking.step) {
                RiderBookingStep.CHOOSE_RIDE_FARE -> RiderBookingStep.SEARCH_DESTINATION
                RiderBookingStep.MAP_PIN_PICKER -> RiderBookingStep.SEARCH_DESTINATION
                RiderBookingStep.SEARCH_DESTINATION -> RiderBookingStep.HOME_COMPACT
                RiderBookingStep.HOME_COMPACT -> RiderBookingStep.HOME_COMPACT
            }
            state.copy(
                booking = state.booking.copy(
                    step = prev,
                    pinPreviewAddress = null,
                    pinPreviewLoading = false
                )
            )
        }
    }

    fun resolvePinLocation(coords: RideCoordinates, forPickup: Boolean, fromGps: Boolean = false) {
        pinPreviewJob?.cancel()
        if (localNav.value.booking.pinConfirming) return
        localNav.update { it.copy(booking = it.booking.copy(pinConfirming = true)) }
        viewModelScope.launch {
            try {
                val res = repository.reverseGeocodeLabel(coords)
                val label = res.getOrElse {
                    if (fromGps) toast("Using nearest landmark for GPS location")
                    LaunchPolicy.nearestLandmarkLabel(coords)
                }
                if (forPickup) {
                    setPickupPoint(label, coords, fromGps)
                } else {
                    selectDestination(label, coords)
                }
            } finally {
                localNav.update { it.copy(booking = it.booking.copy(pinConfirming = false)) }
            }
        }
    }

    fun setPickupPoint(label: String, coords: RideCoordinates, fromGps: Boolean = false) {
        if (localNav.value.booking.step == RiderBookingStep.SEARCH_DESTINATION) {
            cancelPlaceSearch()
        }
        localNav.update { state ->
            val destCoords = state.booking.destinationCoords
            val nextStep = if (state.booking.step == RiderBookingStep.MAP_PIN_PICKER) {
                if (destCoords != null) RiderBookingStep.CHOOSE_RIDE_FARE else RiderBookingStep.SEARCH_DESTINATION
            } else state.booking.step
            state.copy(
                booking = state.booking.copy(
                    step = nextStep,
                    pickupLabel = label,
                    pickupCoords = coords,
                    gpsUsingDeviceLocation = fromGps,
                    routeError = null
                )
            )
        }
        val current = localNav.value.booking
        refreshNearbyPlacesIfNeeded(origin = coords, force = false)
        if (current.destinationCoords != null) {
            fetchLiveRoutePreview(
                pickup = current.pickupLabel,
                destination = current.destinationLabel,
                pickupCoords = coords,
                destCoords = current.destinationCoords
            )
        }
    }

    fun selectDestination(label: String, coords: RideCoordinates) {
        cancelPlaceSearch()
        val pickupLabel = localNav.value.booking.pickupLabel
        val pickupCoords = localNav.value.booking.pickupCoords
        localNav.update { state ->
            state.copy(
                booking = state.booking.copy(
                    step = RiderBookingStep.CHOOSE_RIDE_FARE,
                    destinationLabel = label,
                    destinationCoords = coords,
                    previewRoute = null,
                    visualRoute = null,
                    routeLoading = true,
                    routeError = null,
                    customOfferText = "",
                    isOfferExpanded = false
                )
            )
        }
        fetchLiveRoutePreview(pickupLabel, label, pickupCoords, coords)
    }

    fun refreshRoutePreview() {
        val current = localNav.value.booking
        val destCoords = current.destinationCoords ?: return
        fetchLiveRoutePreview(current.pickupLabel, current.destinationLabel, current.pickupCoords, destCoords)
    }

    private fun fetchLiveRoutePreview(
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates
    ) {
        localNav.update { it.copy(booking = it.booking.copy(routeLoading = true, routeError = null)) }
        viewModelScope.launch {
            val visual = repository.fetchVisualRoadRoute(pickupCoords, destCoords)
            if (visual != null) {
                localNav.update { state ->
                    if (state.booking.destinationCoords == destCoords) {
                        state.copy(booking = state.booking.copy(visualRoute = visual))
                    } else state
                }
            }
        }
        viewModelScope.launch {
            repository.previewRoute(pickup, destination, pickupCoords, destCoords)
                .onSuccess { route ->
                    val shortMsg = if (route.durationSeconds <= LaunchPolicy.MIN_RIDE_DURATION_SECONDS) {
                        "Route must exceed 5 minutes. Choose a further pickup or destination."
                    } else null
                    localNav.update {
                        it.copy(
                            booking = it.booking.copy(
                                previewRoute = route,
                                routeLoading = false,
                                routeError = shortMsg
                            )
                        )
                    }
                }
                .onFailure { err ->
                    localNav.update {
                        it.copy(
                            booking = it.booking.copy(
                                previewRoute = null,
                                routeLoading = false,
                                routeError = err.message ?: "Could not preview route."
                            )
                        )
                    }
                }
        }
    }

    fun selectCategory(category: RideCategory) {
        localNav.update { state ->
            state.copy(booking = state.booking.copy(category = category))
        }
    }

    fun toggleOfferExpanded() {
        localNav.update { state ->
            val nextExpanded = !state.booking.isOfferExpanded
            val defaultText = if (nextExpanded && state.booking.customOfferText.isBlank()) {
                state.booking.estimatedFare.toInt().toString()
            } else state.booking.customOfferText
            state.copy(booking = state.booking.copy(isOfferExpanded = nextExpanded, customOfferText = defaultText))
        }
    }

    fun updateCustomOfferText(text: String) {
        val cleaned = text.filter { it.isDigit() || it == '.' }.take(6)
        localNav.update { it.copy(booking = it.booking.copy(customOfferText = cleaned)) }
    }

    fun adjustCustomOffer(delta: Int) {
        localNav.update { state ->
            val updated = (state.booking.effectiveFare.toInt() + delta).coerceAtLeast(20)
            state.copy(booking = state.booking.copy(isOfferExpanded = true, customOfferText = updated.toString()))
        }
    }

    fun setScheduledOffset(minutesFromNow: Int?) {
        localNav.update { it.copy(booking = it.booking.copy(scheduledOffsetMinutes = minutesFromNow)) }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        localNav.update { state ->
            state.copy(
                booking = state.booking.copy(
                    paymentMethod = method,
                    cashChangeForZar = if (method == PaymentMethod.CASH) state.booking.cashChangeForZar else null
                )
            )
        }
    }

    fun updatePickupNote(note: String) {
        val trimmed = note.take(140)
        localNav.update { it.copy(booking = it.booking.copy(pickupNote = trimmed)) }
    }

    fun togglePickupMicroTag(tag: String) {
        localNav.update { state ->
            val current = state.booking.pickupNote.trim()
            val parts = if (current.isBlank()) {
                emptyList()
            } else {
                current.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
            }.toMutableList()
            if (parts.any { it.equals(tag, ignoreCase = true) }) {
                parts.removeAll { it.equals(tag, ignoreCase = true) }
            } else {
                parts.add(tag)
            }
            val next = parts.joinToString(" · ").take(140)
            state.copy(booking = state.booking.copy(pickupNote = next))
        }
    }

    fun selectCashChangeForZar(amountZar: Int?) {
        localNav.update { it.copy(booking = it.booking.copy(cashChangeForZar = amountZar)) }
    }

    fun toggleBeneficiaryBooking() {
        localNav.update { state ->
            val next = !state.booking.beneficiaryEnabled
            state.copy(
                booking = state.booking.copy(
                    beneficiaryEnabled = next,
                    beneficiaryName = if (next) state.booking.beneficiaryName else "",
                    beneficiaryPhone = if (next) state.booking.beneficiaryPhone else ""
                )
            )
        }
    }

    fun updateBeneficiaryName(name: String) {
        localNav.update { it.copy(booking = it.booking.copy(beneficiaryName = name.take(60))) }
    }

    fun updateBeneficiaryPhone(phone: String) {
        localNav.update { it.copy(booking = it.booking.copy(beneficiaryPhone = phone.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(24))) }
    }

    fun toggleSharedRide() {
        localNav.update { state ->
            val next = !state.booking.isSharedRide
            state.copy(
                booking = state.booking.copy(
                    isSharedRide = next,
                    seatCount = if (next) state.booking.seatCount.coerceIn(1, 4) else 1,
                    customOfferText = ""
                )
            )
        }
    }

    fun setSharedSeatCount(seats: Int) {
        localNav.update { state ->
            state.copy(
                booking = state.booking.copy(
                    isSharedRide = true,
                    seatCount = seats.coerceIn(1, 4),
                    customOfferText = ""
                )
            )
        }
    }

    fun toggleReturnTrip() {
        localNav.update { state ->
            val next = !state.booking.isReturnTrip
            state.copy(
                booking = state.booking.copy(
                    isReturnTrip = next,
                    isReturnRequest = if (next) false else state.booking.isReturnRequest,
                    customOfferText = ""
                )
            )
        }
    }

    fun toggleReturnRequest() {
        localNav.update { state ->
            val next = !state.booking.isReturnRequest
            state.copy(
                booking = state.booking.copy(
                    isReturnRequest = next,
                    isReturnTrip = if (next) false else state.booking.isReturnTrip,
                    customOfferText = ""
                )
            )
        }
    }

    fun toggleGravelRoad() {
        localNav.update { state ->
            state.copy(
                booking = state.booking.copy(
                    includesGravelRoad = !state.booking.includesGravelRoad,
                    customOfferText = ""
                )
            )
        }
    }

    fun submitRideRequest() {
        val draft = localNav.value.booking
        val destCoords = draft.destinationCoords ?: return
        val destLabel = draft.destinationLabel.ifBlank { return }
        if (draft.beneficiaryEnabled && (draft.beneficiaryName.trim().length < 2 || draft.beneficiaryPhone.filter { it.isDigit() }.length < 8)) {
            toast("Enter passenger's name and mobile number", tone = VhayaFeedbackTone.WARNING)
            return
        }
        if (!beginBusy("request_ride")) return
        val customFare = draft.customOfferText.toDoubleOrNull()?.takeIf { it >= 20.0 }
        val pickupAt = draft.scheduledOffsetMinutes?.let { System.currentTimeMillis() + it * 60_000L }

        localNav.update { it.copy(booking = it.booking.copy(routeLoading = true, routeError = null)) }
        viewModelScope.launch {
            try {
                repository.createRideRequest(
                    pickup = draft.pickupLabel,
                    destination = destLabel,
                    pickupCoords = draft.pickupCoords,
                    destCoords = destCoords,
                    category = draft.category,
                    customOfferFare = customFare,
                    pickupAt = pickupAt,
                    paymentMethod = draft.paymentMethod,
                    existingRoute = draft.previewRoute,
                    pickupNote = draft.pickupNote,
                    cashChangeForZar = draft.cashChangeForZar,
                    beneficiaryName = if (draft.beneficiaryEnabled) draft.beneficiaryName.trim() else null,
                    beneficiaryPhone = if (draft.beneficiaryEnabled) draft.beneficiaryPhone.trim() else null,
                    isSharedRide = draft.isSharedRide,
                    seatCount = if (draft.isSharedRide) draft.seatCount else 1,
                    isReturnTrip = draft.isReturnTrip,
                    isReturnRequest = draft.isReturnRequest,
                    includesGravelRoad = draft.includesGravelRoad
                )
                    .onSuccess { createdTrip ->
                        localNav.update {
                            it.copy(
                                expiredTripNotice = null,
                                booking = BookingDraft(
                                    pickupLabel = draft.pickupLabel,
                                    pickupCoords = draft.pickupCoords,
                                    gpsUsingDeviceLocation = draft.gpsUsingDeviceLocation,
                                    paymentMethod = draft.paymentMethod
                                )
                            )
                        }
                        if (createdTrip.isPendingOfflineUpload) {
                            toast(
                                message = "Saved offline · Will auto-dispatch when signal returns",
                                title = "Queued for dispatch"
                            )
                        } else {
                            toast("Vhaya requested · finding nearby drivers")
                        }
                    }
                    .onFailure { err ->
                        val msg = friendlyApiErrorMessage(err, "Could not request ride")
                        localNav.update {
                            it.copy(
                                actionError = msg,
                                booking = it.booking.copy(
                                    routeLoading = false,
                                    routeError = msg
                                )
                            )
                        }
                        toast(msg)
                    }
            } finally {
                endBusy("request_ride")
            }
        }
    }

    private fun isCardCheckoutNeeded(trip: RideTrip): Boolean =
        trip.paymentMethod == PaymentMethod.CARD &&
            trip.status == RideStatus.ACCEPTED &&
            (trip.paymentStatus == PaymentStatus.NONE || trip.paymentStatus == PaymentStatus.PENDING)

    private fun maybeTriggerCardPaymentCheckout(trip: RideTrip) {
        val session = (repository.authState.value as? AuthState.Authenticated)?.session ?: return
        if (session.role != RideRole.RIDER) return
        if (trip.riderId != session.userId) return
        if (!isCardCheckoutNeeded(trip)) return
        if (!repository.isAppInForeground.value) return
        val nav = localNav.value
        if (nav.activePaymentVerification != null ||
            nav.activePaystackCheckout != null ||
            nav.pendingMockPayment != null ||
            "card_hold" in nav.busyActions
        ) {
            return
        }
        if (!autoPaymentTriggeredTripIds.add(trip.id)) return
        toast("Price confirmed · opening Paystack checkout...")
        initializeCardPayment(trip)
    }

    fun acceptDriverOffer(tripId: String, offerId: String) {
        if (!beginBusy("accept_offer")) return
        viewModelScope.launch {
            try {
                repository.acceptDriverOffer(tripId, offerId)
                    .onSuccess { updatedTrip ->
                        if (isCardCheckoutNeeded(updatedTrip)) {
                            if (autoPaymentTriggeredTripIds.add(updatedTrip.id)) {
                                toast("Price confirmed · opening Paystack checkout...")
                                initializeCardPayment(updatedTrip)
                            }
                        } else {
                            toast("Driver confirmed · 6-digit PIN ready")
                        }
                    }
                    .onFailure { reportActionFailure(it, "Could not accept offer") }
            } finally {
                endBusy("accept_offer")
            }
        }
    }

    fun hideOfferLocally(offerId: String) {
        localNav.update { it.copy(dismissedOfferIds = it.dismissedOfferIds + offerId) }
    }

    fun flagDriverOffer(tripId: String, offerId: String, reason: String) {
        if (!beginBusy("flag_offer")) return
        viewModelScope.launch {
            try {
                repository.flagDriverOffer(tripId, offerId, reason)
                    .onSuccess { toast("Offer reported to Vhaya Support") }
                    .onFailure { reportActionFailure(it, "Could not report offer") }
            } finally {
                endBusy("flag_offer")
            }
        }
    }

    fun riderConfirmArrival(tripId: String) {
        if (!beginBusy("confirm_arrival")) return
        viewModelScope.launch {
            try {
                repository.riderConfirmArrival(tripId)
                    .onSuccess { toast("Driver arrival confirmed · share your 6-digit PIN") }
                    .onFailure { reportActionFailure(it, "Could not confirm arrival") }
            } finally {
                endBusy("confirm_arrival")
            }
        }
    }

    fun cancelTrip(tripId: String, reason: String? = null) {
        if (!beginBusy("cancel_trip")) return
        viewModelScope.launch {
            try {
                val targetTrip = uiState.value.trips.firstOrNull { it.id == tripId }
                    ?: repository.getCachedTripsSnapshot().firstOrNull { it.id == tripId }
                val fineZar = if (targetTrip != null && !tripId.startsWith("offline_")) {
                    LaunchPolicy.riderCancellationFee(targetTrip)
                } else {
                    0.0
                }
                repository.cancelTrip(tripId, reason, waiveFee = false)
                    .onSuccess {
                        if (fineZar > 0.0) {
                            toast(
                                message = "Ride cancelled · ${LaunchPolicy.formatZar(fineZar)} cancellation fine applied",
                                tone = VhayaFeedbackTone.WARNING
                            )
                        } else {
                            toast("Ride cancelled")
                        }
                    }
                    .onFailure { reportActionFailure(it, "Could not cancel ride") }
            } finally {
                endBusy("cancel_trip")
            }
        }
    }

    fun switchStalledDriver(trip: RideTrip) {
        if (!beginBusy("switch_driver")) return
        viewModelScope.launch {
            try {
                repository.cancelTrip(trip.id, "Driver stalled or moving away from pickup", waiveFee = true)
                    .onSuccess {
                        stallTrackers.remove(trip.id)
                        rebookExpiredTrip(trip)
                        toast(
                            message = "Switched free of charge (R 0.00 fee) — re-request a closer driver",
                            tone = VhayaFeedbackTone.INFO,
                            title = "Driver released"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not switch driver") }
            } finally {
                endBusy("switch_driver")
            }
        }
    }

    fun cancelWrongVehicleFree(trip: RideTrip) {
        if (!beginBusy("cancel_trip")) return
        viewModelScope.launch {
            try {
                repository.cancelTrip(trip.id, "Vehicle or driver did not match app profile", waiveFee = true)
                    .onSuccess {
                        rebookExpiredTrip(trip)
                        toast(
                            message = "Cancelled at R 0.00 fee — report mismatch if needed",
                            tone = VhayaFeedbackTone.WARNING,
                            title = "Wrong car reported"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not cancel trip") }
            } finally {
                endBusy("cancel_trip")
            }
        }
    }

    fun dismissOffRouteWarning(tripId: String) {
        localNav.update { it.copy(dismissedOffRouteTripIds = it.dismissedOffRouteTripIds + tripId) }
    }

    fun reportChatExtortion(trip: RideTrip, cueSummary: String) {
        if (!beginBusy("support_submit")) return
        viewModelScope.launch {
            try {
                val msg = "[Anti-Extortion Chat Guard] Counterparty requested extra payment or off-app fee ($cueSummary). Rider confirmed fare locked at ${LaunchPolicy.formatZar(trip.fare)}."
                repository.createSupportCase(
                    trip = trip,
                    category = "safety",
                    priority = "urgent",
                    message = msg,
                    requestRefund = false
                )
                    .onSuccess {
                        toast(
                            message = "Urgent fare-pressure report sent to Vhaya Safety",
                            tone = VhayaFeedbackTone.WARNING,
                            title = "Reported"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not submit fare pressure report") }
            } finally {
                endBusy("support_submit")
            }
        }
    }

    fun rateTrip(tripId: String, stars: Int) {
        if (!beginBusy("rate_trip")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.rateTrip(tripId, stars)
                    .onSuccess {
                        localNav.update {
                            if (it.pendingRatingTripId == tripId) it.copy(pendingRatingTripId = null) else it
                        }
                        toast("Thanks for rating $stars ★")
                    }
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            localNav.update {
                                if (it.pendingRatingTripId == tripId) it.copy(pendingRatingTripId = null) else it
                            }
                            toast("Offline · rating queued and will sync automatically")
                        } else {
                            reportActionFailure(err, "Could not save rating")
                        }
                    }
            } finally {
                endBusy("rate_trip")
            }
        }
    }

    fun dismissPostTripRating() {
        localNav.update { it.copy(pendingRatingTripId = null) }
    }

    fun setChatSheetOpen(tripId: String, isOpen: Boolean) {
        if (isOpen) {
            activeOpenChatTripId = tripId
            localNav.update { nav ->
                val updatedUnread = nav.unreadChatCountByTrip - tripId
                val updatedPending = if (nav.pendingOpenChatTripId == tripId) null else nav.pendingOpenChatTripId
                if (updatedUnread == nav.unreadChatCountByTrip && updatedPending == nav.pendingOpenChatTripId) {
                    nav
                } else {
                    nav.copy(unreadChatCountByTrip = updatedUnread, pendingOpenChatTripId = updatedPending)
                }
            }
        } else if (activeOpenChatTripId == tripId) {
            activeOpenChatTripId = null
        }
    }

    fun openTripChat(tripId: String) {
        localNav.update { nav ->
            val routed = when (uiState.value.activeRole) {
                RideRole.RIDER -> nav.copy(riderTab = RiderTab.RIDE)
                RideRole.DRIVER -> nav.copy(driverTab = DriverTab.DRIVE)
                RideRole.ADMIN -> nav
            }
            routed.copy(
                unreadChatCountByTrip = routed.unreadChatCountByTrip - tripId,
                pendingOpenChatTripId = tripId
            )
        }
    }

    fun consumePendingOpenChat(tripId: String) {
        localNav.update { nav ->
            if (nav.pendingOpenChatTripId == tripId) {
                nav.copy(pendingOpenChatTripId = null)
            } else {
                nav
            }
        }
    }

    fun sendTripChat(tripId: String, text: String) {
        if (!beginBusy("chat_send")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.sendTripMessage(tripId, text)
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            toast("Offline · message queued and will send when connected")
                        } else {
                            reportActionFailure(err, "Could not send message")
                        }
                    }
            } finally {
                endBusy("chat_send")
            }
        }
    }

    fun savePlace(label: String, coords: RideCoordinates) {
        viewModelScope.launch {
            repository.toggleSavedDestination(label, coords)
            toast("Saved place added")
        }
    }

    fun deleteSavedPlace(id: String) {
        viewModelScope.launch {
            repository.deleteSavedDestination(id)
            toast("Saved place removed")
        }
    }

    fun addTrustedContact(name: String, phone: String) {
        viewModelScope.launch {
            repository.addTrustedContact(name, phone)
            toast("Trusted contact saved")
        }
    }

    fun removeTrustedContact(id: String) {
        viewModelScope.launch { repository.removeTrustedContact(id) }
    }

    fun createSupportCase(
        trip: RideTrip,
        category: String,
        priority: String,
        message: String,
        requestRefund: Boolean = false,
        attachmentUri: Uri? = null
    ) {
        if (!beginBusy("support_submit")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.createSupportCase(
                    trip = trip,
                    category = category,
                    priority = priority,
                    message = message,
                    requestRefund = requestRefund,
                    attachmentUri = attachmentUri
                )
                    .onSuccess { toast("Support ticket submitted") }
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            toast("Offline · issue queued and will submit automatically")
                        } else {
                            reportActionFailure(err, "Could not submit ticket")
                        }
                    }
            } finally {
                endBusy("support_submit")
            }
        }
    }

    fun sendRiderSupportFollowUp(supportCase: SupportCase, message: String) {
        if (!beginBusy("support_submit")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.sendRiderSupportFollowUp(supportCase, message)
                    .onSuccess { toast("Message sent to Vhaya Support") }
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            toast("Offline · reply queued and will send automatically")
                        } else {
                            reportActionFailure(err, "Could not send message")
                        }
                    }
            } finally {
                endBusy("support_submit")
            }
        }
    }

    // Driver Actions
    fun setDriverOnline(online: Boolean) {
        if (!beginBusy("driver_online")) return
        viewModelScope.launch {
            try {
                repository.setDriverOnline(online)
                    .onSuccess {
                        if (!online) {
                            localNav.update {
                                it.copy(
                                    driverRepositionZone = null,
                                    driverRepositionRoute = null,
                                    driverRepositionLoading = false
                                )
                            }
                        }
                        toast(
                            message = if (online) "You are online — scanning nearby requests" else "You are now offline",
                            tone = if (online) VhayaFeedbackTone.SUCCESS else VhayaFeedbackTone.INFO,
                            title = if (online) "Online" else "Offline"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not update driver availability") }
            } finally {
                endBusy("driver_online")
            }
        }
    }

    fun toggleDriverSound() = localNav.update { it.copy(driverSoundEnabled = !it.driverSoundEnabled) }
    fun toggleDriverHeatmap() = localNav.update { it.copy(driverHeatmapEnabled = !it.driverHeatmapEnabled) }

    fun setDriverMinRatePerKm(rateZar: Double) {
        repository.setDriverMinRatePerKmZar(rateZar)
        localNav.update { it.copy(driverRequestIndex = 0) }
        val label = if (rateZar <= 0.0) "Showing all ride offers" else "Filtering rides R ${rateZar.toInt()}+/km"
        toast(label, tone = VhayaFeedbackTone.INFO)
    }

    fun skipDriverRequest(totalCount: Int) {
        if (totalCount <= 1) return
        localNav.update { it.copy(driverRequestIndex = (it.driverRequestIndex + 1) % totalCount) }
    }

    fun selectDriverRequestById(tripId: String) {
        val list = uiState.value.driverOpenRequests
        val idx = list.indexOfFirst { it.id == tripId }
        if (idx >= 0) {
            localNav.update { it.copy(driverRequestIndex = idx) }
        }
    }

    fun setDriverCorridorTarget(target: DriverCorridorTarget?) {
        repository.setDriverCorridorTarget(target)
        localNav.update { it.copy(driverRequestIndex = 0) }
        if (target == null) {
            toast("Corridor filter cleared — showing all directions", tone = VhayaFeedbackTone.INFO)
        } else {
            toast(
                message = "Filtering rides heading toward ${target.label}",
                tone = VhayaFeedbackTone.SUCCESS,
                title = "Heading Home ON"
            )
        }
    }

    fun navigateToDemandZone(zone: LaunchPolicy.DemandHeatZone) {
        val origin = localNav.value.driverCurrentCoords
        localNav.update {
            it.copy(
                driverRepositionZone = zone,
                driverRepositionRoute = null,
                driverRepositionLoading = true
            )
        }
        viewModelScope.launch {
            val route = repository.fetchDriverNavigationRoute(origin, zone.centroid)
            localNav.update { current ->
                if (current.driverRepositionZone?.id != zone.id) {
                    current
                } else {
                    current.copy(
                        driverRepositionRoute = route,
                        driverRepositionLoading = false
                    )
                }
            }
            toast(
                message = "Routing to ${zone.label} demand hotspot while scanning offers",
                tone = VhayaFeedbackTone.INFO,
                title = "Reposition route active"
            )
        }
    }

    fun clearRepositionZone() {
        localNav.update {
            it.copy(
                driverRepositionZone = null,
                driverRepositionRoute = null,
                driverRepositionLoading = false
            )
        }
    }

    fun toggleDriverVoiceNav() {
        val next = !repository.driverVoiceNavEnabled.value
        repository.setDriverVoiceNavEnabled(next)
        toast(
            message = if (next) "Spoken turn prompts ON" else "Spoken turn prompts muted",
            tone = VhayaFeedbackTone.INFO
        )
    }

    fun toggleDriverHeadingUp() {
        val next = !repository.driverHeadingUpEnabled.value
        repository.setDriverHeadingUpEnabled(next)
    }

    fun reportRoadHazard(
        type: HazardType,
        coords: RideCoordinates,
        label: String,
        note: String? = null
    ) {
        viewModelScope.launch {
            val reporter = uiState.value.currentDriverApp?.displayName
                ?: uiState.value.currentSession?.displayName
                ?: "Driver"
            repository.reportRoadHazard(
                type = type,
                coords = coords,
                label = label,
                note = note,
                reportedBy = reporter
            )
            toast(
                message = "${type.label} hazard pinned near ${label.substringBefore(" ·")}",
                tone = VhayaFeedbackTone.WARNING,
                title = "Hazard shared"
            )
        }
    }

    fun confirmRoadHazard(hazardId: String) {
        viewModelScope.launch {
            repository.confirmRoadHazard(hazardId)
            toast("Hazard confirmed · warning extended", tone = VhayaFeedbackTone.INFO)
        }
    }

    fun clearRoadHazard(hazardId: String) {
        viewModelScope.launch {
            repository.clearRoadHazard(hazardId)
            toast("Road hazard marked clear", tone = VhayaFeedbackTone.SUCCESS)
        }
    }

    fun downloadOfflineCorridorPack(pack: OfflineCorridorPack) {
        if (localNav.value.offlinePackDownloadingId != null) return
        localNav.update {
            it.copy(offlinePackDownloadingId = pack.id, offlinePackProgress = 0 to 1)
        }
        viewModelScope.launch {
            try {
                val status = repository.downloadOfflineCorridorPack(pack) { done, total ->
                    localNav.update { current ->
                        if (current.offlinePackDownloadingId == pack.id) {
                            current.copy(offlinePackProgress = done to total.coerceAtLeast(1))
                        } else {
                            current
                        }
                    }
                }
                toast(
                    message = "Cached ${status.downloadedTiles}/${status.totalTiles} map tiles for ${pack.title}",
                    tone = if (status.downloadedTiles > 0) VhayaFeedbackTone.SUCCESS else VhayaFeedbackTone.WARNING,
                    title = "Offline corridor pack"
                )
            } finally {
                localNav.update {
                    it.copy(offlinePackDownloadingId = null, offlinePackProgress = null)
                }
            }
        }
    }

    fun clearOfflineTileCache() {
        repository.clearOfflineTileCache()
        toast("Offline map tile cache cleared", tone = VhayaFeedbackTone.INFO)
    }

    fun offlineTileCacheStats(): Pair<Int, Long> = repository.getOfflineTileCacheStats()

    fun declineDriverRequest(tripId: String) {
        repository.declineDriverRequest(tripId)
        localNav.update { it.copy(driverRequestIndex = 0) }
        toast("Request declined", tone = VhayaFeedbackTone.INFO, title = "Passed")
    }

    fun updateDriverCoords(coords: RideCoordinates, activeTripId: String? = null, fromGps: Boolean = true) {
        if (!LaunchPolicy.isValidDeviceCoordinate(coords)) return
        val shouldResolveAddress = fromGps && (
            localNav.value.driverCurrentAddress == null ||
                lastDriverAddressCoords == null ||
                LaunchPolicy.distanceMeters(lastDriverAddressCoords!!, coords) >= 120.0
            )
        localNav.update {
            it.copy(
                driverCurrentCoords = coords,
                driverHasGpsFix = fromGps || it.driverHasGpsFix,
                driverAddressLoading = if (shouldResolveAddress) true else it.driverAddressLoading
            )
        }
        if (shouldResolveAddress) {
            lastDriverAddressCoords = coords
            driverAddressJob?.cancel()
            driverAddressJob = viewModelScope.launch {
                val resolvedLabel = repository.reverseGeocodeLabel(coords).getOrElse {
                    LaunchPolicy.nearestLandmarkLabel(coords)
                }
                localNav.update { current ->
                    current.copy(
                        driverCurrentAddress = resolvedLabel,
                        driverAddressLoading = false
                    )
                }
            }
        }
        val prevNearbyOrigin = lastNearbyFetchOrigin
        if (fromGps && (prevNearbyOrigin == null || LaunchPolicy.distanceMeters(prevNearbyOrigin, coords) >= 1_500.0)) {
            refreshNearbyPlacesIfNeeded(origin = coords, force = false)
        }
        val activeTrip = uiState.value.driverActiveTrip
        if (activeTrip != null) {
            val prevOrigin = lastDriverNavOrigin
            val movedMeters = prevOrigin?.let { LaunchPolicy.distanceMeters(it, coords) } ?: Double.MAX_VALUE
            val elapsedMs = System.currentTimeMillis() - lastDriverNavFetchMs
            if (localNav.value.driverNavRoute == null || (movedMeters >= 150.0 && elapsedMs >= 12_000L)) {
                fetchDriverNavigationRouteInternal(activeTrip, originOverride = coords, force = false)
            }
        }
        if (activeTripId != null) {
            viewModelScope.launch {
                repository.updateLiveDriverLocation(activeTripId, coords)
                    .onFailure { toast(friendlyApiErrorMessage(it, "Could not update GPS location"), tone = VhayaFeedbackTone.WARNING) }
            }
        }
    }

    fun setDriverNavFollowCamera(follow: Boolean) {
        localNav.update { it.copy(driverNavFollowCamera = follow) }
    }

    fun refreshDriverNavigationRoute(force: Boolean = true) {
        val activeTrip = uiState.value.driverActiveTrip ?: return
        fetchDriverNavigationRouteInternal(
            trip = activeTrip,
            originOverride = localNav.value.driverCurrentCoords,
            force = force
        )
    }

    private fun fetchDriverNavigationRouteInternal(
        trip: RideTrip,
        originOverride: RideCoordinates,
        force: Boolean
    ) {
        val targetCoords = if (trip.status == RideStatus.IN_PROGRESS) {
            trip.destinationCoordinates ?: trip.route?.destinationCoordinates
        } else {
            trip.pickupCoordinates ?: trip.route?.originCoordinates
        } ?: return

        val legKey = "${trip.id}:${if (trip.status == RideStatus.IN_PROGRESS) "dest" else "pickup"}"
        val now = System.currentTimeMillis()
        if (!force &&
            legKey == localNav.value.driverNavRouteTripKey &&
            localNav.value.driverNavRoute != null &&
            (now - lastDriverNavFetchMs) < 10_000L
        ) {
            return
        }

        val currentLegChanged = legKey != localNav.value.driverNavRouteTripKey
        driverNavFetchJob?.cancel()
        lastDriverNavFetchMs = now
        lastDriverNavOrigin = originOverride
        localNav.update {
            it.copy(
                driverNavRouteTripKey = legKey,
                driverNavRoute = if (currentLegChanged) null else it.driverNavRoute,
                driverNavLoading = true
            )
        }

        driverNavFetchJob = viewModelScope.launch {
            val fetched = repository.fetchDriverNavigationRoute(originOverride, targetCoords)
            val fallbackRoute = if (fetched == null && trip.status == RideStatus.IN_PROGRESS) {
                trip.route
            } else null

            localNav.update { current ->
                if (current.driverNavRouteTripKey != legKey) {
                    current
                } else {
                    current.copy(
                        driverNavRoute = fetched ?: fallbackRoute ?: current.driverNavRoute,
                        driverNavLoading = false
                    )
                }
            }
            if (force && fetched == null) {
                toast(
                    message = "Unable to refresh live street directions right now — check your connection.",
                    tone = VhayaFeedbackTone.WARNING,
                    title = "Route unavailable"
                )
            }
        }
    }

    fun driverSubmitOffer(trip: RideTrip, amount: Double) {
        if (!beginBusy("driver_offer")) return
        viewModelScope.launch {
            try {
                repository.driverSubmitOffer(trip, amount, localNav.value.driverCurrentCoords)
                    .onSuccess { updated ->
                        val msg = when {
                            updated.driverId != null -> "Ride accepted · head to pickup"
                            amount == trip.fare -> "Offer sent at ${LaunchPolicy.formatZar(amount)} · awaiting rider"
                            else -> "Counter-offer ${LaunchPolicy.formatZar(amount)} sent"
                        }
                        toast(msg, tone = VhayaFeedbackTone.SUCCESS)
                    }
                    .onFailure { reportActionFailure(it, "Could not submit offer") }
            } finally {
                endBusy("driver_offer")
            }
        }
    }

    fun driverMarkArrived(tripId: String) {
        if (!beginBusy("driver_arrived")) return
        viewModelScope.launch {
            try {
                repository.driverMarkArrived(tripId, localNav.value.driverCurrentCoords)
                    .onSuccess { toast("Arrived · ask rider for 6-digit PIN", tone = VhayaFeedbackTone.SUCCESS) }
                    .onFailure { reportActionFailure(it, "Arrival check failed") }
            } finally {
                endBusy("driver_arrived")
            }
        }
    }

    fun driverVerifyPinAndStart(tripId: String, pin: String) {
        if (!beginBusy("driver_pin")) return
        viewModelScope.launch {
            try {
                repository.verifyPinAndStartTrip(tripId, pin)
                    .onSuccess {
                        localNav.update { it.copy(driverDriveModeMinimized = false) }
                        toast("PIN verified · Drive Mode active", tone = VhayaFeedbackTone.SUCCESS, title = "Trip started")
                    }
                    .onFailure { reportActionFailure(it, "Invalid PIN") }
            } finally {
                endBusy("driver_pin")
            }
        }
    }

    fun driverCancelTrip(tripId: String, reason: String) {
        if (!beginBusy("cancel_trip")) return
        viewModelScope.launch {
            try {
                repository.cancelTrip(tripId, reason, waiveFee = true)
                    .onSuccess {
                        localNav.update { it.copy(driverDriveModeMinimized = false) }
                        toast("Trip cancelled", tone = VhayaFeedbackTone.WARNING, title = "Trip released")
                    }
                    .onFailure { reportActionFailure(it, "Could not cancel trip") }
            } finally {
                endBusy("cancel_trip")
            }
        }
    }

    fun setDriverDriveModeMinimized(minimized: Boolean) = localNav.update { it.copy(driverDriveModeMinimized = minimized) }

    fun driverQueueReturnRide(match: ReturnRideMatch) {
        if (!beginBusy("queue_return_ride")) return
        val trip = match.trip
        viewModelScope.launch {
            try {
                repository.queueReturnRide(trip, localNav.value.driverCurrentCoords)
                    .onSuccess { acceptedOnServer ->
                        val msg = if (acceptedOnServer) {
                            "Return Ride confirmed & queued after drop-off (${LaunchPolicy.formatZar(trip.fare)})"
                        } else {
                            "Return Ride pinned Up Next — auto-accepts when you finish drop-off"
                        }
                        toast(msg, tone = VhayaFeedbackTone.SUCCESS, title = "Up Next queued")
                    }
                    .onFailure { reportActionFailure(it, "Could not queue Return Ride") }
            } finally {
                endBusy("queue_return_ride")
            }
        }
    }

    fun driverCounterReturnRide(trip: RideTrip, amountZar: Double) {
        if (!beginBusy("counter_return_ride")) return
        val cleanAmount = amountZar.coerceAtLeast(20.0)
        viewModelScope.launch {
            try {
                repository.driverSubmitOffer(trip, cleanAmount, localNav.value.driverCurrentCoords)
                    .onSuccess {
                        repository.setDriverQueuedReturnTripId(trip.id)
                        toast(
                            message = "Return counter-offer ${LaunchPolicy.formatZar(cleanAmount)} sent & pinned Up Next",
                            tone = VhayaFeedbackTone.SUCCESS,
                            title = "Return offer sent"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not send Return Ride offer") }
            } finally {
                endBusy("counter_return_ride")
            }
        }
    }

    fun driverDismissReturnRide(tripId: String) {
        declineDriverRequest(tripId)
    }

    fun driverCancelQueuedReturnRide() {
        val queued = uiState.value.driverQueuedReturnTrip
        val userId = uiState.value.activeUserId
        repository.setDriverQueuedReturnTripId(null)
        if (queued != null && queued.driverId == userId && queued.status.isActive) {
            viewModelScope.launch {
                repository.cancelTrip(queued.id, "Driver cleared queued return ride before pickup", waiveFee = true)
            }
        }
        toast("Queued Return Ride removed", tone = VhayaFeedbackTone.INFO)
    }

    fun driverCompleteTrip(tripId: String) {
        if (!beginBusy("driver_complete")) return
        viewModelScope.launch {
            try {
                val queued = uiState.value.driverQueuedReturnTrip
                repository.completeTrip(tripId)
                    .onSuccess {
                        localNav.update {
                            it.copy(
                                driverDriveModeMinimized = false,
                                pendingDriverSummaryTripId = tripId
                            )
                        }
                        if (queued != null) {
                            if (queued.status.isOpenForDrivers && queued.driverId == null) {
                                repository.driverSubmitOffer(
                                    trip = queued,
                                    amount = queued.fare,
                                    driverLocation = localNav.value.driverCurrentCoords
                                ).onSuccess {
                                    repository.setDriverQueuedReturnTripId(null)
                                    toast(
                                        message = "Drop-off complete · Starting queued Return Ride pickup",
                                        tone = VhayaFeedbackTone.SUCCESS,
                                        title = "Up Next active"
                                    )
                                }.onFailure {
                                    toast(
                                        message = "Trip completed · Tap your queued Return Ride to begin pickup",
                                        tone = VhayaFeedbackTone.INFO,
                                        title = "Trip completed"
                                    )
                                }
                            } else {
                                repository.setDriverQueuedReturnTripId(null)
                                toast(
                                    message = "Drop-off complete · Queued Return Ride is now active",
                                    tone = VhayaFeedbackTone.SUCCESS,
                                    title = "Up Next active"
                                )
                            }
                        } else {
                            toast("Trip completed · fare recorded", tone = VhayaFeedbackTone.SUCCESS, title = "Trip completed")
                        }
                    }
                    .onFailure { reportActionFailure(it, "Could not complete trip") }
            } finally {
                endBusy("driver_complete")
            }
        }
    }

    fun dismissDriverTripSummary() {
        localNav.update { it.copy(pendingDriverSummaryTripId = null) }
    }

    fun driverRateRider(tripId: String, stars: Int, tags: List<String>, requesterName: String) {
        repository.saveDriverRiderRating(tripId, stars, tags)
        toast(
            message = "Rated $requesterName $stars ★",
            tone = VhayaFeedbackTone.SUCCESS,
            title = "Feedback saved"
        )
    }

    fun sendCashChangeToWallet(trip: RideTrip, amountZar: Int) {
        if (!beginBusy("cash_change_wallet")) return
        viewModelScope.launch {
            try {
                repository.creditCashChangeToWallet(trip.id, amountZar)
                    .onSuccess {
                        localNav.update {
                            it.copy(cashChangeCreditedTripIds = it.cashChangeCreditedTripIds + trip.id)
                        }
                        toast(
                            message = "Sent ${LaunchPolicy.formatZar(amountZar.toDouble())} change to rider's Vhaya Wallet",
                            tone = VhayaFeedbackTone.SUCCESS,
                            title = "Change sent"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not credit cash change to wallet") }
            } finally {
                endBusy("cash_change_wallet")
            }
        }
    }

    fun saveDriverBankDetails(
        bankName: String,
        accountHolder: String,
        accountNumber: String,
        branchCode: String,
        accountType: String
    ): Result<Unit> {
        val cleanBank = bankName.trim()
        val cleanHolder = accountHolder.trim()
        val cleanAccount = accountNumber.filter { it.isDigit() }
        val cleanBranch = branchCode.filter { it.isDigit() }
        if (cleanBank.isBlank()) {
            val err = IllegalArgumentException("Select or enter your South African bank.")
            reportActionFailure(err, "Bank name required")
            return Result.failure(err)
        }
        if (cleanHolder.length < 2) {
            val err = IllegalArgumentException("Enter the full account holder name.")
            reportActionFailure(err, "Account holder required")
            return Result.failure(err)
        }
        if (cleanAccount.length < 6) {
            val err = IllegalArgumentException("Enter a valid bank account number (at least 6 digits).")
            reportActionFailure(err, "Invalid account number")
            return Result.failure(err)
        }
        if (cleanBranch.length < 5) {
            val err = IllegalArgumentException("Enter a valid 5–6 digit branch code.")
            reportActionFailure(err, "Invalid branch code")
            return Result.failure(err)
        }
        repository.saveDriverBankDetails(
            DriverBankDetails(
                bankName = cleanBank,
                accountHolder = cleanHolder,
                accountNumber = cleanAccount,
                branchCode = cleanBranch,
                accountType = accountType
            )
        )
        toast(
            message = "$cleanBank (•••• ${cleanAccount.takeLast(4)}) saved for weekly EFT payouts",
            tone = VhayaFeedbackTone.SUCCESS,
            title = "Payout bank saved"
        )
        return Result.success(Unit)
    }

    fun clearDriverOnboardingMessages() {
        localNav.update { it.copy(driverOnboardingError = null, driverOnboardingNotice = null) }
    }

    fun saveDriverOnboarding(app: DriverApplication, submit: Boolean) {
        localNav.update {
            it.copy(driverOnboardingBusy = true, driverOnboardingError = null, driverOnboardingNotice = null)
        }
        viewModelScope.launch {
            repository.saveDriverApplication(app, submit)
                .onSuccess {
                    val msg = if (submit) {
                        "Application submitted! Email your documents to ${LaunchPolicy.DRIVER_DOCS_EMAIL} for manual verification."
                    } else {
                        "Driver draft saved"
                    }
                    localNav.update {
                        it.copy(driverOnboardingBusy = false, driverOnboardingError = null, driverOnboardingNotice = msg)
                    }
                    toast(msg)
                }
                .onFailure { err ->
                    val msg = friendlyApiErrorMessage(err, "Could not save driver application")
                    localNav.update {
                        it.copy(driverOnboardingBusy = false, driverOnboardingError = msg, driverOnboardingNotice = null)
                    }
                    toast(msg)
                }
        }
    }

    // Admin Actions
    fun updateComplianceCheck(userId: String, docType: DriverDocumentType, status: ComplianceStatus, note: String?) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updateComplianceCheck(userId, docType, status, note)
                    .onSuccess { toast("${docType.title} marked ${status.label}") }
                    .onFailure { reportActionFailure(it, "Could not update compliance check") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun decideDriverApplication(userId: String, decision: String, staffNote: String?) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.decideDriverApplication(userId, decision, staffNote)
                    .onSuccess { toast("Driver status updated to $decision") }
                    .onFailure { reportActionFailure(it, "Could not update driver review") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun updateUserRoleOrSuspension(userId: String, role: RideRole? = null, suspend: Boolean? = null, reason: String? = null) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updateUserRoleOrSuspension(userId, role, suspend, reason)
                    .onSuccess { toast("Account updated") }
                    .onFailure { reportActionFailure(it, "Could not update account") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun updateSupportCaseStatus(caseId: Long, status: String, priority: String? = null) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updateSupportCaseStatus(caseId, status, priority)
                    .onSuccess { toast("Ticket #$caseId updated to $status") }
                    .onFailure { reportActionFailure(it, "Could not update ticket") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun replyAdminSupportCase(caseId: Long, message: String) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.addAdminSupportReply(caseId, message)
                    .onFailure { reportActionFailure(it, "Could not send reply") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun recordPayout(
        driverId: String,
        provider: String,
        externalReference: String,
        amountZar: Double,
        periodStart: String?,
        periodEnd: String?,
        note: String?
    ) {
        if (!beginBusy("admin_payout")) return
        viewModelScope.launch {
            try {
                repository.recordPayout(driverId, provider, externalReference, amountZar, periodStart, periodEnd, note)
                    .onSuccess { toast("Payout ${LaunchPolicy.formatZar(amountZar)} recorded") }
                    .onFailure { reportActionFailure(it, "Could not record payout") }
            } finally {
                endBusy("admin_payout")
            }
        }
    }

    fun reconcilePayout(payoutId: Long, status: String, note: String? = null) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.reconcilePayout(payoutId, status, note)
                    .onSuccess { toast("Payout marked $status") }
                    .onFailure { reportActionFailure(it, "Could not update payout") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun runWeeklyPayoutBatch(periodStartIso: String, periodEndIso: String) {
        if (!beginBusy("admin_batch")) return
        viewModelScope.launch {
            try {
                repository.runWeeklyPayoutBatch(periodStartIso, periodEndIso)
                    .onSuccess { toast("Weekly payout batch generated") }
                    .onFailure { reportActionFailure(it, "Could not run weekly batch") }
            } finally {
                endBusy("admin_batch")
            }
        }
    }

    fun approvePayoutBatch(batchId: String) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.approvePayoutBatch(batchId)
                    .onSuccess { toast("Payout batch approved") }
                    .onFailure { reportActionFailure(it, "Could not approve payout batch") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun recordExternalPaymentForPayoutBatch(batchId: String) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.recordExternalPaymentForPayoutBatch(batchId)
                    .onSuccess { toast("External payout payment recorded") }
                    .onFailure { reportActionFailure(it, "Could not record external payment") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    private data class SessionSlice(
        val authState: AuthState,
        val apiBaseUrl: String,
        val themeMode: AppThemeMode,
        val notificationSoundStyle: NotificationSoundStyle,
        val privacyPreferences: PrivacyPreferences,
        val pendingOfflineCount: Int,
        val declinedDriverTripIds: Set<String>,
        val driverBankDetails: DriverBankDetails?,
        val driverRiderRatings: Map<String, DriverRiderRating>,
        val driverMinRatePerKmZar: Double,
        val driverCorridorTarget: DriverCorridorTarget?,
        val driverVoiceNavEnabled: Boolean,
        val driverHeadingUpEnabled: Boolean,
        val isNetworkConnected: Boolean,
        val driverQueuedReturnTripId: String?
    )

    private data class CoreSlice(
        val trips: List<RideTrip>,
        val saved: List<SavedDestination>,
        val contacts: List<TrustedContact>,
        val wallet: List<WalletTransaction>,
        val drivers: List<DriverApplication>
    )

    private data class OpsSlice(
        val support: List<SupportCase>,
        val payouts: List<DriverPayoutRecord>,
        val users: List<UserAccountSummary>,
        val batches: List<PayoutBatchRecord>,
        val logs: List<ActivityLogEntry>
    )

    private data class LiveSummarySlice(
        val walletBalanceMinor: Long,
        val driverEarnings: DriverEarningsSummary,
        val driverSubscription: DriverSubscriptionSummary?,
        val adminOverview: AdminOverviewSummary,
        val placeSuggestions: List<PlaceSearchSuggestion>,
        val searchingPlaces: Boolean,
        val lowDataMode: Boolean,
        val appLanguage: AppLanguage,
        val trustedDrivers: List<TrustedCommunityDriver>,
        val roadHazards: List<RoadHazardPin>,
        val offlineTilePacks: List<OfflineCorridorPackStatus>,
        val nearbyPlaces: List<PlaceSearchSuggestion>,
        val nearbyPlacesLoading: Boolean,
        val nearbyPlacesError: String?,
        val selectedNearbyCategory: NearbyPlaceCategory
    )

    class Factory(
        private val repository: WayfareRepository,
        private val fareEstimationService: FareEstimationService = FareEstimationService.defaultInstance
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WayfareViewModel(repository, fareEstimationService) as T
    }
}
