package com.wayfare.ride.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
enum class RideRole(val label: String) {
    @SerialName("rider")
    RIDER("Requester"),
    @SerialName("driver")
    DRIVER("Driver"),
    @SerialName("admin")
    ADMIN("Admin")
}

@Serializable
enum class RideCategory(val label: String, val caption: String) {
    @SerialName("standard")
    STANDARD("Standard", "Everyday"),
    @SerialName("comfort")
    COMFORT("Comfort", "Extra comfort"),
    @SerialName("xl")
    XL("XL", "More seats"),
    @SerialName("bakkie")
    BAKKIE("Bakkie", "Loads & gravel"),
    @SerialName("parcel")
    PARCEL("Parcel", "Send bag/box")
}

@Serializable
enum class AppLanguage(val code: String, val label: String) {
    @SerialName("en")
    ENGLISH("en", "English"),
    @SerialName("nso")
    SEPEDI("nso", "Sepedi"),
    @SerialName("ve")
    TSHIVENDA("ve", "Tshivenda"),
    @SerialName("ts")
    XITSONGA("ts", "Xitsonga");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

@Serializable
enum class PricingMode(val label: String) {
    @SerialName("estimate")
    ESTIMATE("Route estimate"),
    @SerialName("offer")
    OFFER("Your fare offer")
}

@Serializable
enum class RideStatus(val label: String) {
    @SerialName("requested")
    REQUESTED("Request saved"),
    @SerialName("countered")
    COUNTERED("Driver offers available"),
    @SerialName("accepted")
    ACCEPTED("Driver accepted"),
    @SerialName("arrived")
    ARRIVED("Driver arrived"),
    @SerialName("in_progress")
    IN_PROGRESS("On the way"),
    @SerialName("completed")
    COMPLETED("Completed"),
    @SerialName("cancelled")
    CANCELLED("Cancelled"),
    @SerialName("unfulfilled")
    UNFULFILLED("Unable to fulfill");

    val isActive: Boolean
        get() = this == REQUESTED || this == COUNTERED || this == ACCEPTED || this == ARRIVED || this == IN_PROGRESS

    val isOpenForDrivers: Boolean
        get() = this == REQUESTED || this == COUNTERED
}

@Serializable
enum class PaymentStatus(val label: String) {
    @SerialName("none")
    NONE("Not authorized"),
    @SerialName("pending")
    PENDING("Authorization pending"),
    @SerialName("authorized")
    AUTHORIZED("Fare reserved on card"),
    @SerialName("capture_pending")
    CAPTURE_PENDING("Capture pending"),
    @SerialName("capture_failed")
    CAPTURE_FAILED("Capture failed"),
    @SerialName("release_pending")
    RELEASE_PENDING("Release pending"),
    @SerialName("release_failed")
    RELEASE_FAILED("Release failed"),
    @SerialName("paid")
    PAID("Ride fare captured"),
    @SerialName("failed")
    FAILED("Authorization failed"),
    @SerialName("released")
    RELEASED("Card hold released"),
    @SerialName("expired")
    EXPIRED("Authorization expired"),
    @SerialName("refunded")
    REFUNDED("Refunded")
}

@Serializable
enum class PaymentMethod(val label: String) {
    @SerialName("cash")
    CASH("Cash"),
    @SerialName("wallet")
    WALLET("Wallet"),
    @SerialName("card")
    CARD("Card")
}

@Serializable
data class RideCoordinates(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class NavigationStep(
    val index: Int = 0,
    val instruction: String,
    val streetName: String = "",
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    val maneuverType: String = "continue",
    val maneuverModifier: String? = null,
    val exitNumber: Int? = null,
    val location: RideCoordinates? = null
)

@Serializable
data class RideRoute(
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    val encodedPolyline: String = "",
    val provider: String = "osrm",
    val originCoordinates: RideCoordinates? = null,
    val destinationCoordinates: RideCoordinates? = null,
    val fareEstimate: Double? = null,
    val quoteToken: String? = null,
    val steps: List<NavigationStep> = emptyList()
)

@Serializable
data class PlaceSearchSuggestion(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double? = null,
    val provider: String = "osm",
    val category: String? = null
) {
    val coordinates: RideCoordinates
        get() = RideCoordinates(latitude, longitude)
}

@Serializable
data class DriverProfileSnapshot(
    val id: String,
    val name: String,
    val vehicle: String = "",
    val vehicleColor: String = "",
    val registration: String = "",
    val fare: Double = 0.0,
    val verificationStatus: String = "approved",
    val rating: Double = 4.9,
    val completedRides: Int = 0,
    val acceptanceRate: Double = 0.95,
    val lastLocation: RideCoordinates? = null,
    val verifiedDocsCount: Int? = null,
    val operatingPermitVerified: Boolean? = null,
    val gravelReady: Boolean? = null,
    val homeArea: String? = null
)

@Serializable
data class RideOffer(
    val id: String,
    val tripId: String = "",
    val driverId: String,
    val amount: Double,
    val status: String = "pending", // pending, accepted, declined, withdrawn
    val createdAt: Long = 0L,
    val driverProfile: DriverProfileSnapshot
)

@Serializable
data class RideMessage(
    val id: String,
    val tripId: String = "",
    val sender: String, // rider, driver, admin, support
    val channel: String = "driver", // driver, support
    val message: String,
    val createdAt: Long
)

@Serializable
data class TrustedContact(
    val id: String,
    val name: String,
    val phone: String
)

@Serializable
data class SavedDestination(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val savedAt: Long
)

@Serializable
data class RideTrip(
    val id: String,
    val riderId: String,
    val driverId: String? = null,
    val pickup: String,
    val destination: String,
    val category: RideCategory,
    val currency: String = "ZAR",
    val fare: Double,
    val pricingMode: PricingMode,
    val status: RideStatus,
    val paymentStatus: PaymentStatus = PaymentStatus.NONE,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paymentReference: String? = null,
    val createdAt: Long,
    val pickupAt: Long? = null,
    val expiresAt: Long? = null,
    val pickupCoordinates: RideCoordinates? = null,
    val destinationCoordinates: RideCoordinates? = null,
    val route: RideRoute? = null,
    val offers: List<RideOffer> = emptyList(),
    val messages: List<RideMessage> = emptyList(),
    val riderRating: Int? = null,
    val riderRatingTags: List<String> = emptyList(),
    val tripPin: String? = null,
    val arrivalConfirmationMethod: String? = null, // gps, rider
    val arrivalConfirmedAt: Long? = null,
    val driverProfile: DriverProfileSnapshot? = null,
    val liveDriverLocation: RideCoordinates? = null,
    val unfulfillmentReason: String? = null,
    val pickupNote: String? = null,
    val cashChangeForZar: Int? = null,
    val riderCompletedRides: Int? = null,
    val riderVerified: Boolean? = null,
    val driverArrivedAt: Long? = null,
    val beneficiaryName: String? = null,
    val beneficiaryPhone: String? = null,
    val isSharedRide: Boolean = false,
    val seatCount: Int = 1,
    val isReturnTrip: Boolean = false,
    val isReturnRequest: Boolean = false,
    val includesGravelRoad: Boolean = false,
    val syncStatus: String = "SYNCED", // SYNCED, PENDING_UPLOAD, SYNC_FAILED
    val clientRequestId: String? = null
) {
    val isPendingOfflineUpload: Boolean
        get() = syncStatus == "PENDING_UPLOAD"
}

@Serializable
enum class DriverDocumentType(val key: String, val title: String, val hint: String) {
    @SerialName("identity")
    IDENTITY("identity", "Identity document", "Use a clear image or PDF of your ID or passport."),
    @SerialName("driver_license")
    DRIVER_LICENSE("driver_license", "Driver licence", "Make sure the details and expiry date are readable."),
    @SerialName("vehicle_registration")
    VEHICLE_REGISTRATION("vehicle_registration", "Vehicle registration", "Upload the registration document for this vehicle."),
    @SerialName("operating_permit")
    OPERATING_PERMIT("operating_permit", "Operating licence or permit", "If you are still applying, save your draft and add it later.")
}

@Serializable
enum class ComplianceStatus(val key: String, val label: String) {
    @SerialName("not_reviewed")
    NOT_REVIEWED("not_reviewed", "Not reviewed"),
    @SerialName("verified")
    VERIFIED("verified", "Verified"),
    @SerialName("needs_follow_up")
    NEEDS_FOLLOW_UP("needs_follow_up", "Needs follow-up"),
    @SerialName("not_required")
    NOT_REQUIRED("not_required", "Not required")
}

@Serializable
data class DriverComplianceCheck(
    val userId: String = "",
    val documentType: DriverDocumentType,
    val status: ComplianceStatus = ComplianceStatus.NOT_REVIEWED,
    val expiresOn: String? = null,
    val staffNote: String? = null,
    val reviewedAt: Long? = null
)

@Serializable
data class DriverApplication(
    val userId: String,
    val displayName: String = "",
    val email: String? = null,
    val contactPhone: String = "",
    val city: String = "",
    val applicationStatus: String = "draft", // draft, submitted, under_review, needs_follow_up, approved, rejected
    val verificationStatus: String = "unverified", // unverified, pending, approved, rejected
    val vehicle: String = "",
    val vehicleColor: String = "",
    val registration: String = "",
    val licenceExpiry: String = "",
    val operatingLicenceReference: String = "",
    val isOnline: Boolean = false,
    val documentsUploaded: List<String> = emptyList(),
    val documentsConsent: Boolean = false,
    val staffNote: String? = null,
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null,
    val complianceChecks: List<DriverComplianceCheck> = emptyList(),
    val gravelReady: Boolean = false
)

@Serializable
data class SupportReply(
    val id: String,
    val caseId: Long = 0L,
    val senderLabel: String = "Vhaya Support",
    val message: String,
    val createdAt: Long
)

@Serializable
data class SupportCase(
    val id: Long,
    val rideId: String,
    val reporterName: String = "Requester",
    val category: String, // safety, payment, driver, rider, lost_item, fare_review, other
    val priority: String, // normal, high, urgent
    val status: String, // open, in_progress, resolved
    val assignedTo: String? = null,
    val staffNote: String? = null,
    val message: String = "",
    val pickup: String = "",
    val destination: String = "",
    val rideStatus: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val replies: List<SupportReply> = emptyList()
)

@Serializable
data class WalletTransaction(
    val id: String,
    val type: String, // topup, credit, debit, refund, payout
    val amountMinor: Long,
    val currency: String = "ZAR",
    val reference: String,
    val description: String,
    val createdAt: Long
)

@Serializable
data class WalletSummary(
    val id: String = "",
    val userId: String = "",
    val balanceMinor: Long = 0L,
    val currency: String = "ZAR"
)

@Serializable
data class DriverPayoutRecord(
    val id: Long,
    val driverId: String,
    val driverName: String = "",
    val provider: String, // paystack, bank_transfer, cash, other
    val externalReference: String,
    val amountMinor: Long,
    val currency: String = "ZAR",
    val periodLabel: String = "",
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val reconciliationStatus: String, // unreconciled, matched, exception
    val note: String? = null,
    val recordedAt: Long,
    val reconciledAt: Long? = null
) {
    val resolvedPeriodLabel: String
        get() = when {
            periodLabel.isNotBlank() -> periodLabel
            !periodStart.isNullOrBlank() && !periodEnd.isNullOrBlank() ->
                "${periodStart.take(10)} → ${periodEnd.take(10)}"
            !periodStart.isNullOrBlank() -> periodStart.take(10)
            else -> "Settlement period"
        }
}

@Serializable
data class UserAccountSummary(
    val userId: String,
    val displayName: String,
    val email: String,
    val role: RideRole,
    val isSuspended: Boolean = false,
    val suspensionReason: String? = null,
    val rideCount: Int = 0,
    val completedRideCount: Int = 0
)

@Serializable
data class UserSession(
    val userId: String,
    val displayName: String,
    val email: String,
    val role: RideRole,
    val emailVerified: Boolean = true,
    val idToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtEpochMs: Long = 0L,
    val apiSessionId: String? = null
)

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data class EmailVerificationPending(val session: UserSession) : AuthState
    data class Authenticated(val session: UserSession) : AuthState
}

sealed class AccountVerificationChange {
    data class EmailVerified(val session: UserSession) : AccountVerificationChange()
    data class DriverVerificationUpdated(
        val verificationStatus: String,
        val applicationStatus: String,
        val role: RideRole,
        val staffNote: String? = null
    ) : AccountVerificationChange()
}

@Serializable
data class ActivityLogEntry(
    val id: String,
    val kind: String,
    val actor: String,
    val subject: String,
    val summary: String,
    val createdAt: Long
)

@Serializable
data class PayoutBatchRecord(
    val id: String,
    val periodLabel: String = "",
    val periodStart: Long? = null,
    val periodEnd: Long? = null,
    val status: String, // open, submitted, paid, reconciled
    val totalAmountMinor: Long,
    val driverCount: Int,
    val createdAt: Long,
    val paidAt: Long? = null
) {
    val resolvedPeriodLabel: String
        get() {
            if (periodLabel.isNotBlank()) return periodLabel
            if (periodStart != null && periodEnd != null) {
                val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
                return "${fmt.format(Date(periodStart))} – ${fmt.format(Date(periodEnd))}"
            }
            return "Weekly batch"
        }
}

@Serializable
data class DriverEarningRecord(
    val rideId: String,
    val pickup: String,
    val destination: String,
    val fareMinor: Long,
    val currency: String = "ZAR",
    val completedAt: Long,
    val commissionMinor: Long = 0L,
    val netMinor: Long = 0L,
    val paidMinor: Long = 0L
)

@Serializable
data class DriverEarningsSummary(
    val driverId: String = "",
    val currency: String? = "ZAR",
    val estimatedEarningsMinor: Long = 0L,
    val paidEarningsMinor: Long = 0L,
    val totalCommissionMinor: Long = 0L,
    val totalNetMinor: Long = 0L,
    val outstandingEarningsMinor: Long = 0L,
    val rideCount: Int = 0,
    val paidRideCount: Int = 0,
    val records: List<DriverEarningRecord> = emptyList()
)

@Serializable
data class DriverSubscriptionSummary(
    val id: String,
    val provider: String = "paystack",
    val externalReference: String = "",
    val amountMinor: Long = 0L,
    val currency: String = "ZAR",
    val status: String = "pending",
    val startedAt: Long = 0L,
    val expiresAt: Long? = null
)

@Serializable
data class DriverSubscriptionPlan(
    val id: String,
    val name: String,
    val description: String? = null,
    val amountMinor: Long,
    val currency: String = "ZAR",
    val billingInterval: String,
    val renewalMode: String = "manual"
)

@Serializable
data class AdminOverviewSummary(
    val users: Int = 0,
    val suspendedUsers: Int = 0,
    val rideRequests: Int = 0,
    val activeRides: Int = 0,
    val pendingDriverReviews: Int = 0,
    val paymentIssues: Int = 0,
    val activeSupportCases: Int = 0,
    val urgentSupportCases: Int = 0
)

data class PaymentInitOutcome(
    val tripId: String?,
    val provider: String,
    val mode: String,
    val reference: String,
    val amountMinor: Long,
    val status: PaymentStatus,
    val authorizationUrl: String? = null
)

enum class PaymentVerificationState {
    CHECKING,
    AUTHORIZED,
    PAID,
    PENDING,
    FAILED
}

data class PaymentVerificationResult(
    val reference: String,
    val tripId: String? = null,
    val state: PaymentVerificationState,
    val amountMinor: Long = 0L,
    val message: String = ""
)

enum class IssueCategory(
    val key: String,
    val label: String,
    val description: String,
    val defaultPriority: String
) {
    SAFETY("safety", "Safety", "Driver behavior, unsafe driving, or safety incident", "urgent"),
    PAYMENT("payment", "Payment", "Fare dispute, double charge, or card hold issue", "high"),
    DRIVER("driver", "Driver", "Driver conduct, professionalism, or wrong vehicle", "high"),
    LOST_ITEM("lost_item", "Lost item", "Phone, bag, or item left behind in the vehicle", "normal"),
    OTHER("other", "Other", "Route question, app bug, or general feedback", "normal");

    companion object {
        fun fromKey(key: String?): IssueCategory =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER
    }
}

@Serializable
data class PrivacyPreferences(
    val locationSharingConsent: Boolean = true,
    val tripOnlyPrivacyMode: Boolean = false,
    val supportNotificationsEnabled: Boolean = true,
    val popiaAcceptedAtEpochMs: Long? = null
)

@Serializable
data class PendingOfflineAction(
    val id: String,
    val kind: String, // trip_message, support_create, support_reply, trip_rate, driver_location
    val tripId: String = "",
    val caseId: Long = 0L,
    val category: String = "other",
    val priority: String = "normal",
    val message: String = "",
    val rating: Int = 5,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class OfflineRideCreatePayload(
    val clientRequestId: String,
    val pickup: String,
    val destination: String,
    val pickupCoordinates: RideCoordinates,
    val destinationCoordinates: RideCoordinates,
    val category: RideCategory,
    val fare: Double,
    val customOfferFare: Double? = null,
    val pickupAt: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val pickupNote: String? = null,
    val cashChangeForZar: Int? = null,
    val beneficiaryName: String? = null,
    val beneficiaryPhone: String? = null,
    val isSharedRide: Boolean = false,
    val seatCount: Int = 1,
    val isReturnTrip: Boolean = false,
    val isReturnRequest: Boolean = false,
    val includesGravelRoad: Boolean = false
)

@Serializable
data class OfflineRideActionPayload(
    val clientRequestId: String,
    val tripId: String,
    val actionJson: String,
    val refreshAfterSuccess: Boolean = false
)

enum class LocationPermissionState {
    GRANTED_FINE,
    GRANTED_COARSE,
    DENIED_CAN_RETRY,
    DENIED_PERMANENTLY;

    val isGranted: Boolean
        get() = this == GRANTED_FINE || this == GRANTED_COARSE
}

data class SupportUpdateAlert(
    val caseId: Long,
    val title: String,
    val summary: String,
    val status: String,
    val isStaffReply: Boolean
)

enum class DriverIssueCategory(
    val key: String,
    val label: String,
    val description: String,
    val defaultPriority: String
) {
    SAFETY("safety", "Safety", "Unsafe behavior, accident, or urgent road incident", "urgent"),
    RIDER("rider", "Rider", "Rider no-show, pickup dispute, or conduct issue", "high"),
    FARE_REVIEW("fare_review", "Fare & payout", "Fare discrepancy, toll, wait time, or EFT payout query", "high"),
    LOST_ITEM("lost_item", "Lost item", "Item left behind in your vehicle by a rider", "normal"),
    OTHER("other", "Other", "Navigation, app bug, or general driver support", "normal");

    companion object {
        fun fromKey(key: String?): DriverIssueCategory =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER
    }
}

fun resolveSupportCategoryLabel(key: String?): String {
    DriverIssueCategory.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.let { return it.label }
    return IssueCategory.fromKey(key).label
}

@Serializable
data class DriverRiderRating(
    val tripId: String,
    val stars: Int,
    val tags: List<String> = emptyList(),
    val ratedAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class DriverBankDetails(
    val bankName: String,
    val accountHolder: String,
    val accountNumber: String,
    val branchCode: String,
    val accountType: String = "Cheque",
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val maskedAccountNumber: String
        get() {
            val digits = accountNumber.filter { it.isDigit() }
            return if (digits.length > 4) "•••• ${digits.takeLast(4)}" else digits
        }

    companion object {
        val SOUTH_AFRICAN_BANKS: List<Pair<String, String>> = listOf(
            "FNB" to "250655",
            "Standard Bank" to "051001",
            "Absa" to "632005",
            "Nedbank" to "198765",
            "Capitec" to "470010",
            "TymeBank" to "678910",
            "Discovery Bank" to "679000"
        )
    }
}

@Serializable
data class TrustedCommunityDriver(
    val driverId: String,
    val name: String,
    val vehicle: String = "",
    val vehicleColor: String = "",
    val registration: String = "",
    val completedRidesTogether: Int = 1,
    val savedAtMs: Long = System.currentTimeMillis()
)

enum class RuralRunPreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val category: RideCategory,
    val isSharedRide: Boolean = false,
    val seatCount: Int = 1,
    val isReturnTrip: Boolean = false,
    val includesGravelRoad: Boolean = false,
    val beneficiaryEnabled: Boolean = false,
    val defaultPickupNote: String = "",
    val suggestedFilterKeyword: String = ""
) {
    CLINIC_RUN(
        key = "clinic",
        title = "Clinic Run",
        subtitle = "Wait & return · Gate",
        category = RideCategory.STANDARD,
        isReturnTrip = true,
        includesGravelRoad = true,
        defaultPickupNote = "Clinic visit — wait at gate",
        suggestedFilterKeyword = "Clinic"
    ),
    PENSION_DAY(
        key = "pension",
        title = "Pension Day",
        subtitle = "Shared · Return",
        category = RideCategory.STANDARD,
        isSharedRide = true,
        seatCount = 1,
        isReturnTrip = true,
        defaultPickupNote = "Pension / SASSA pay point",
        suggestedFilterKeyword = "Mall"
    ),
    TOWN_GROCERY(
        key = "grocery",
        title = "Grocery Bakkie",
        subtitle = "Bakkie · Gravel bags",
        category = RideCategory.BAKKIE,
        includesGravelRoad = true,
        defaultPickupNote = "Town groceries / heavy parcels",
        suggestedFilterKeyword = "Plaza"
    ),
    FAMILY_PARCEL(
        key = "parcel",
        title = "Send Parcel",
        subtitle = "Parcel · Recipient PIN",
        category = RideCategory.PARCEL,
        beneficiaryEnabled = true,
        defaultPickupNote = "Call recipient on arrival",
        suggestedFilterKeyword = ""
    )
}

enum class NearbyPlaceCategory(
    val key: String,
    val chipLabel: String,
    val sectionTitle: String
) {
    ALL("all", "Nearby", "Closest places near you"),
    CLINICS("clinic", "Clinics", "Nearby clinics & hospitals"),
    SHOPPING("shopping", "Malls & Shops", "Nearby malls, plazas & groceries"),
    PAYPOINTS("pension", "Paypoints", "Nearby post offices, banks & civic hubs"),
    TRANSIT("transit", "Ranks & Stations", "Nearby taxi & bus ranks")
}

