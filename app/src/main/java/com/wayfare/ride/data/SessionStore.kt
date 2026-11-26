package com.wayfare.ride.data

import android.content.Context
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.domain.AppLanguage
import com.wayfare.ride.domain.DriverCorridorTarget
import com.wayfare.ride.domain.DriverPocketRoute
import com.wayfare.ride.domain.OfflineCorridorPackStatus
import com.wayfare.ride.domain.PendingOfflineAction
import com.wayfare.ride.domain.PocketItem
import com.wayfare.ride.domain.PocketItemSize
import com.wayfare.ride.domain.PocketItemStatus
import com.wayfare.ride.domain.PocketItemType
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.TrustedCommunityDriver
import com.wayfare.ride.domain.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionStore(
    context: Context,
    private val apiBaseUrlOverride: String? = null,
    private val firebaseApiKeyOverride: String? = null,
    private val googleWebClientIdOverride: String? = null
) {
    private val prefs = context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun loadApiBaseUrl(): String = (apiBaseUrlOverride ?: BuildConfig.VHAYA_API_BASE_URL).trim().trimEnd('/')

    fun loadFirebaseApiKey(): String = (firebaseApiKeyOverride ?: BuildConfig.FIREBASE_WEB_API_KEY).trim()

    fun loadGoogleWebClientId(): String = googleWebClientIdOverride.orEmpty().trim()

    fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_USER_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
    }

    fun saveSession(session: UserSession?) {
        if (session == null) {
            prefs.edit().remove(KEY_USER_SESSION).apply()
        } else {
            prefs.edit().putString(KEY_USER_SESSION, json.encodeToString(session)).apply()
        }
    }

    fun loadThemeMode(): com.wayfare.ride.ui.theme.AppThemeMode =
        com.wayfare.ride.ui.theme.AppThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))

    fun saveThemeMode(mode: com.wayfare.ride.ui.theme.AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    fun loadNotificationSoundStyle(): com.wayfare.ride.audio.NotificationSoundStyle =
        com.wayfare.ride.audio.NotificationSoundStyle.fromKey(
            prefs.getString(KEY_NOTIFICATION_SOUND_STYLE, null)
        )

    fun saveNotificationSoundStyle(style: com.wayfare.ride.audio.NotificationSoundStyle) {
        prefs.edit().putString(KEY_NOTIFICATION_SOUND_STYLE, style.key).apply()
    }

    fun loadPrivacyPreferences(): PrivacyPreferences {
        val raw = prefs.getString(KEY_PRIVACY_PREFS, null) ?: return PrivacyPreferences()
        return runCatching { json.decodeFromString<PrivacyPreferences>(raw) }.getOrDefault(PrivacyPreferences())
    }

    fun savePrivacyPreferences(privacyPreferences: PrivacyPreferences) {
        prefs.edit().putString(KEY_PRIVACY_PREFS, json.encodeToString(privacyPreferences)).apply()
    }

    fun loadOfflineQueue(): List<PendingOfflineAction> {
        val raw = prefs.getString(KEY_OFFLINE_QUEUE, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingOfflineAction>>(raw) }.getOrDefault(emptyList())
    }

    fun saveOfflineQueue(items: List<PendingOfflineAction>) {
        if (items.isEmpty()) {
            prefs.edit().remove(KEY_OFFLINE_QUEUE).apply()
        } else {
            prefs.edit().putString(KEY_OFFLINE_QUEUE, json.encodeToString(items)).apply()
        }
    }

    fun hasRequestedLocationPermission(): Boolean = prefs.getBoolean(KEY_LOCATION_REQUESTED, false)

    fun markLocationPermissionRequested() {
        prefs.edit().putBoolean(KEY_LOCATION_REQUESTED, true).apply()
    }

    fun loadDeclinedTripIds(): Set<String> {
        val raw = prefs.getString(KEY_DECLINED_TRIP_IDS, null) ?: return emptySet()
        return runCatching { json.decodeFromString<List<String>>(raw).toSet() }.getOrDefault(emptySet())
    }

    fun saveDeclinedTripIds(ids: Set<String>) {
        if (ids.isEmpty()) {
            prefs.edit().remove(KEY_DECLINED_TRIP_IDS).apply()
        } else {
            val trimmed = ids.toList().takeLast(50)
            prefs.edit().putString(KEY_DECLINED_TRIP_IDS, json.encodeToString(trimmed)).apply()
        }
    }

    fun loadDriverBankDetails(): com.wayfare.ride.domain.DriverBankDetails? {
        val raw = prefs.getString(KEY_DRIVER_BANK_DETAILS, null) ?: return null
        return runCatching { json.decodeFromString<com.wayfare.ride.domain.DriverBankDetails>(raw) }.getOrNull()
    }

    fun saveDriverBankDetails(details: com.wayfare.ride.domain.DriverBankDetails?) {
        if (details == null) {
            prefs.edit().remove(KEY_DRIVER_BANK_DETAILS).apply()
        } else {
            prefs.edit().putString(KEY_DRIVER_BANK_DETAILS, json.encodeToString(details)).apply()
        }
    }

    fun loadDriverRiderRatings(): Map<String, com.wayfare.ride.domain.DriverRiderRating> {
        val raw = prefs.getString(KEY_DRIVER_RIDER_RATINGS, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<List<com.wayfare.ride.domain.DriverRiderRating>>(raw).associateBy { it.tripId }
        }.getOrDefault(emptyMap())
    }

    fun saveDriverRiderRatings(ratings: Map<String, com.wayfare.ride.domain.DriverRiderRating>) {
        if (ratings.isEmpty()) {
            prefs.edit().remove(KEY_DRIVER_RIDER_RATINGS).apply()
        } else {
            val trimmed = ratings.values.sortedByDescending { it.ratedAtMs }.take(100)
            prefs.edit().putString(KEY_DRIVER_RIDER_RATINGS, json.encodeToString(trimmed)).apply()
        }
    }

    fun loadDriverMinRatePerKmZar(): Double {
        return prefs.getFloat(KEY_DRIVER_MIN_RATE_PER_KM, 0f).toDouble().coerceAtLeast(0.0)
    }

    fun saveDriverMinRatePerKmZar(rateZar: Double) {
        val clean = rateZar.coerceAtLeast(0.0).toFloat()
        if (clean <= 0f) {
            prefs.edit().remove(KEY_DRIVER_MIN_RATE_PER_KM).apply()
        } else {
            prefs.edit().putFloat(KEY_DRIVER_MIN_RATE_PER_KM, clean).apply()
        }
    }

    fun loadLowDataMode(): Boolean = prefs.getBoolean(KEY_LOW_DATA_MODE, false)

    fun saveLowDataMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_DATA_MODE, enabled).apply()
    }

    fun loadAppLanguage(): AppLanguage = AppLanguage.fromCode(prefs.getString(KEY_APP_LANGUAGE, null))

    fun saveAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
    }

    fun loadTrustedDrivers(): List<TrustedCommunityDriver> {
        val raw = prefs.getString(KEY_TRUSTED_DRIVERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<TrustedCommunityDriver>>(raw) }.getOrDefault(emptyList())
    }

    fun saveTrustedDrivers(drivers: List<TrustedCommunityDriver>) {
        if (drivers.isEmpty()) {
            prefs.edit().remove(KEY_TRUSTED_DRIVERS).apply()
        } else {
            val trimmed = drivers.sortedByDescending { it.savedAtMs }.take(40)
            prefs.edit().putString(KEY_TRUSTED_DRIVERS, json.encodeToString(trimmed)).apply()
        }
    }

    fun loadDriverCorridorTarget(): DriverCorridorTarget? {
        val raw = prefs.getString(KEY_DRIVER_CORRIDOR_TARGET, null) ?: return null
        return runCatching { json.decodeFromString<DriverCorridorTarget>(raw) }.getOrNull()
    }

    fun saveDriverCorridorTarget(target: DriverCorridorTarget?) {
        if (target == null) {
            prefs.edit().remove(KEY_DRIVER_CORRIDOR_TARGET).apply()
        } else {
            prefs.edit().putString(KEY_DRIVER_CORRIDOR_TARGET, json.encodeToString(target)).apply()
        }
    }

    fun loadOfflineTilePacks(): List<OfflineCorridorPackStatus> {
        val raw = prefs.getString(KEY_OFFLINE_TILE_PACKS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<OfflineCorridorPackStatus>>(raw) }.getOrDefault(emptyList())
    }

    fun saveOfflineTilePacks(packs: List<OfflineCorridorPackStatus>) {
        if (packs.isEmpty()) {
            prefs.edit().remove(KEY_OFFLINE_TILE_PACKS).apply()
        } else {
            prefs.edit().putString(KEY_OFFLINE_TILE_PACKS, json.encodeToString(packs)).apply()
        }
    }

    fun loadDriverVoiceNavEnabled(): Boolean = prefs.getBoolean(KEY_DRIVER_VOICE_NAV, true)

    fun saveDriverVoiceNavEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DRIVER_VOICE_NAV, enabled).apply()
    }

    fun loadDriverHeadingUpEnabled(): Boolean = prefs.getBoolean(KEY_DRIVER_HEADING_UP, false)

    fun saveDriverHeadingUpEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DRIVER_HEADING_UP, enabled).apply()
    }

    fun loadDriverQueuedReturnTripId(): String? =
        prefs.getString(KEY_DRIVER_QUEUED_RETURN_TRIP_ID, null)?.takeIf { it.isNotBlank() }

    fun saveDriverQueuedReturnTripId(tripId: String?) {
        if (tripId.isNullOrBlank()) {
            prefs.edit().remove(KEY_DRIVER_QUEUED_RETURN_TRIP_ID).apply()
        } else {
            prefs.edit().putString(KEY_DRIVER_QUEUED_RETURN_TRIP_ID, tripId).apply()
        }
    }

    fun loadPocketRoutes(): List<DriverPocketRoute> {
        val raw = prefs.getString(KEY_POCKET_ROUTES, null)
        if (raw != null) {
            val list = runCatching { json.decodeFromString<List<DriverPocketRoute>>(raw) }.getOrNull()
            if (!list.isNullOrEmpty()) return list
        }
        return DEFAULT_POCKET_ROUTES
    }

    fun savePocketRoutes(routes: List<DriverPocketRoute>) {
        prefs.edit().putString(KEY_POCKET_ROUTES, json.encodeToString(routes)).apply()
    }

    fun loadPocketItems(): List<PocketItem> {
        val raw = prefs.getString(KEY_POCKET_ITEMS, null)
        if (raw != null) {
            val list = runCatching { json.decodeFromString<List<PocketItem>>(raw) }.getOrNull()
            if (!list.isNullOrEmpty()) return list
        }
        return DEFAULT_POCKET_ITEMS
    }

    fun savePocketItems(items: List<PocketItem>) {
        prefs.edit().putString(KEY_POCKET_ITEMS, json.encodeToString(items)).apply()
    }

    fun clearAllUserPreferences() {
        prefs.edit()
            .remove(KEY_USER_SESSION)
            .remove(KEY_PRIVACY_PREFS)
            .remove(KEY_OFFLINE_QUEUE)
            .remove(KEY_DECLINED_TRIP_IDS)
            .remove(KEY_DRIVER_BANK_DETAILS)
            .remove(KEY_DRIVER_RIDER_RATINGS)
            .remove(KEY_DRIVER_MIN_RATE_PER_KM)
            .remove(KEY_TRUSTED_DRIVERS)
            .remove(KEY_DRIVER_CORRIDOR_TARGET)
            .remove(KEY_DRIVER_QUEUED_RETURN_TRIP_ID)
            .remove(KEY_POCKET_ROUTES)
            .remove(KEY_POCKET_ITEMS)
            .apply()
    }

    companion object {
        private const val KEY_USER_SESSION = "user_session_json"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_NOTIFICATION_SOUND_STYLE = "notification_sound_style"
        private const val KEY_PRIVACY_PREFS = "privacy_preferences_json"
        private const val KEY_OFFLINE_QUEUE = "offline_action_queue_json"
        private const val KEY_LOCATION_REQUESTED = "location_permission_requested"
        private const val KEY_DECLINED_TRIP_IDS = "declined_driver_trip_ids"
        private const val KEY_DRIVER_BANK_DETAILS = "driver_bank_details_json"
        private const val KEY_DRIVER_RIDER_RATINGS = "driver_rider_ratings_json"
        private const val KEY_DRIVER_MIN_RATE_PER_KM = "driver_min_rate_per_km_zar"
        private const val KEY_LOW_DATA_MODE = "low_data_mode_enabled"
        private const val KEY_APP_LANGUAGE = "app_language_code"
        private const val KEY_TRUSTED_DRIVERS = "trusted_community_drivers_json"
        private const val KEY_DRIVER_CORRIDOR_TARGET = "driver_corridor_target_json"
        private const val KEY_OFFLINE_TILE_PACKS = "offline_tile_packs_json"
        private const val KEY_DRIVER_VOICE_NAV = "driver_voice_nav_enabled"
        private const val KEY_DRIVER_HEADING_UP = "driver_heading_up_enabled"
        private const val KEY_DRIVER_QUEUED_RETURN_TRIP_ID = "driver_queued_return_trip_id"
        private const val KEY_POCKET_ROUTES = "driver_pocket_routes_json"
        private const val KEY_POCKET_ITEMS = "pocket_items_json"

        val DEFAULT_POCKET_ROUTES = listOf(
            DriverPocketRoute(
                id = "pocket_route_malamulele_tembisa",
                driverId = "driver_samuel_01",
                driverName = "Samuel Maluleke",
                driverPhone = "+27 82 491 8832",
                vehicleModel = "Toyota Hilux 2.8 D-4D",
                vehicleColor = "White",
                originLabel = "Malamulele Main Plaza & Rank",
                originCoordinates = RideCoordinates(-22.9730, 30.6840),
                destinationLabel = "Tembisa Plaza & Mall",
                destinationCoordinates = RideCoordinates(-25.9960, 28.2250),
                departureTimeLabel = "Today 14:30",
                departureTimestampMs = System.currentTimeMillis() + 4 * 3600_000L,
                totalSeats = 3,
                availableSeats = 2,
                seatPriceZar = 250.0,
                totalParcelSlots = 4,
                availableParcelSlots = 3,
                baseParcelPriceZar = 120.0,
                notes = "Passing Polokwane and Pretoria to Tembisa. Large canopy boot."
            ),
            DriverPocketRoute(
                id = "pocket_route_thohoyandou_joburg",
                driverId = "driver_sipho_02",
                driverName = "Sipho Sithole",
                driverPhone = "+27 71 882 1190",
                vehicleModel = "Toyota Quantum 14-Seater",
                vehicleColor = "Silver",
                originLabel = "Thohoyandou Phangami Mall",
                originCoordinates = RideCoordinates(-22.9550, 30.4720),
                destinationLabel = "Johannesburg Park Station",
                destinationCoordinates = RideCoordinates(-26.1950, 28.0430),
                departureTimeLabel = "Tomorrow 05:30",
                departureTimestampMs = System.currentTimeMillis() + 18 * 3600_000L,
                totalSeats = 6,
                availableSeats = 4,
                seatPriceZar = 280.0,
                totalParcelSlots = 6,
                availableParcelSlots = 4,
                baseParcelPriceZar = 140.0,
                notes = "Direct N1 corridor. Drops at Pretoria Bosman and Joburg Noord."
            ),
            DriverPocketRoute(
                id = "pocket_route_giyani_polokwane",
                driverId = "driver_given_03",
                driverName = "Given Chauke",
                driverPhone = "+27 76 331 4452",
                vehicleModel = "VW Polo Vivo",
                vehicleColor = "Grey",
                originLabel = "Giyani Main Taxi Rank & Plaza",
                originCoordinates = RideCoordinates(-23.3020, 30.7180),
                destinationLabel = "Polokwane CBD Rank",
                destinationCoordinates = RideCoordinates(-23.9045, 29.4689),
                departureTimeLabel = "Today 16:00",
                departureTimestampMs = System.currentTimeMillis() + 6 * 3600_000L,
                totalSeats = 3,
                availableSeats = 1,
                seatPriceZar = 120.0,
                totalParcelSlots = 3,
                availableParcelSlots = 2,
                baseParcelPriceZar = 80.0,
                notes = "Via R81 through Sebayeng and Solomondale."
            )
        )

        val DEFAULT_POCKET_ITEMS = listOf(
            PocketItem(
                id = "pocket_item_01",
                type = PocketItemType.PARCEL,
                senderName = "Hlulanigo",
                senderPhone = "+27 73 991 2200",
                recipientName = "Musa Baloyi",
                recipientPhone = "+27 82 555 1920",
                originLabel = "Malamulele Main Plaza & Rank",
                originCoordinates = RideCoordinates(-22.9730, 30.6840),
                destinationLabel = "Tembisa Plaza & Mall",
                destinationCoordinates = RideCoordinates(-25.9960, 28.2250),
                itemDescription = "Box of Clothes & Family Essentials",
                size = PocketItemSize.MEDIUM_BAG,
                fareZar = 140.0,
                assignedDriverRouteId = "pocket_route_malamulele_tembisa",
                assignedDriverName = "Samuel Maluleke",
                status = PocketItemStatus.RESERVED,
                recipientPin = "5921",
                scheduledDepartureNote = "Today 14:30"
            ),
            PocketItem(
                id = "pocket_item_02",
                type = PocketItemType.PARCEL,
                senderName = "Grace M.",
                senderPhone = "+27 84 102 9944",
                recipientName = "Tebogo Nkosi",
                recipientPhone = "+27 79 332 1109",
                originLabel = "Malamulele Main Plaza & Rank",
                originCoordinates = RideCoordinates(-22.9730, 30.6840),
                destinationLabel = "Tembisa Oakmoor Station",
                destinationCoordinates = RideCoordinates(-26.0230, 28.2280),
                itemDescription = "Matric Documents & Transcripts (Envelope)",
                size = PocketItemSize.ENVELOPE,
                fareZar = 50.0,
                status = PocketItemStatus.LISTED,
                recipientPin = "7734"
            )
        )
    }
}

