package com.wayfare.ride.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.NearbyPlaceCategory
import com.wayfare.ride.domain.OfflineRideActionPayload
import com.wayfare.ride.domain.OfflineRideCreatePayload
import com.wayfare.ride.domain.PendingOfflineAction
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.service.OfflineSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object WayfareMigrations {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trips ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
            db.execSQL("ALTER TABLE trips ADD COLUMN clientRequestId TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_routes` (
                    `corridorKey` TEXT NOT NULL,
                    `originLat` REAL NOT NULL,
                    `originLon` REAL NOT NULL,
                    `destLat` REAL NOT NULL,
                    `destLon` REAL NOT NULL,
                    `distanceMeters` INTEGER NOT NULL,
                    `durationSeconds` INTEGER NOT NULL,
                    `encodedPolyline` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `fareEstimate` REAL,
                    `cachedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`corridorKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_ride_sync` (
                    `clientRequestId` TEXT NOT NULL,
                    `operationType` TEXT NOT NULL,
                    `tripId` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `syncState` TEXT NOT NULL,
                    `retryCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    `nextRetryAtMs` INTEGER NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`clientRequestId`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_places` (
                    `labelKey` TEXT NOT NULL,
                    `placeId` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `provider` TEXT NOT NULL,
                    `queryTag` TEXT NOT NULL,
                    `cachedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`labelKey`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `road_hazards` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `label` TEXT NOT NULL,
                    `note` TEXT,
                    `reportedBy` TEXT NOT NULL,
                    `reportedAtMs` INTEGER NOT NULL,
                    `expiresAtMs` INTEGER NOT NULL,
                    `confirmations` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trips ADD COLUMN isReturnRequest INTEGER NOT NULL DEFAULT 0")
        }
    }
}

class OfflineSyncCoordinator(
    private val context: Context,
    private val cachedRouteDao: CachedRouteDao,
    private val cachedPlaceDao: CachedPlaceDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
) {
    suspend fun getCachedRoute(
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates,
        nowMs: Long = System.currentTimeMillis()
    ): RideRoute? = withContext(Dispatchers.IO) {
        val corridorKey = CachedRouteEntity.buildCorridorKey(pickupCoords, destCoords)
        val cached = cachedRouteDao.getByKey(corridorKey) ?: return@withContext null
        if (cached.cachedAtMs < nowMs - LaunchPolicy.ROUTE_CACHE_TTL_MS) {
            return@withContext null
        }
        cached.toDomain(pickupCoords, destCoords)
    }

    suspend fun cacheRoute(
        route: RideRoute,
        pickupCoords: RideCoordinates?,
        destCoords: RideCoordinates?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val origin = route.originCoordinates ?: pickupCoords ?: return
        val dest = route.destinationCoordinates ?: destCoords ?: return
        withContext(Dispatchers.IO) {
            cachedRouteDao.upsert(
                CachedRouteEntity(
                    corridorKey = CachedRouteEntity.buildCorridorKey(origin, dest),
                    originLat = origin.latitude,
                    originLon = origin.longitude,
                    destLat = dest.latitude,
                    destLon = dest.longitude,
                    distanceMeters = route.distanceMeters,
                    durationSeconds = route.durationSeconds,
                    encodedPolyline = route.encodedPolyline,
                    provider = route.provider,
                    fareEstimate = route.fareEstimate,
                    cachedAtMs = nowMs
                )
            )
            cachedRouteDao.deleteExpired(nowMs - LaunchPolicy.ROUTE_CACHE_TTL_MS)
        }
    }

    suspend fun cachePlaceSuggestions(
        query: String,
        suggestions: List<PlaceSearchSuggestion>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (suggestions.isEmpty()) return
        val baseTag = query.trim().lowercase()
        val entities = suggestions.map { place ->
            val resolvedCategory = place.category ?: LaunchPolicy.inferPlaceCategory(place.label)
            val resolvedTag = if (baseTag.startsWith("nearby:") && resolvedCategory != null) {
                "nearby:$resolvedCategory"
            } else {
                baseTag
            }
            CachedPlaceEntity(
                labelKey = place.label.trim().lowercase(),
                placeId = place.id,
                label = place.label.trim(),
                latitude = place.latitude,
                longitude = place.longitude,
                provider = place.provider,
                queryTag = resolvedTag,
                cachedAtMs = nowMs
            )
        }
        withContext(Dispatchers.IO) {
            cachedPlaceDao.upsertAll(entities)
            cachedPlaceDao.deleteExpired(nowMs - LaunchPolicy.PLACE_CACHE_TTL_MS)
        }
    }

    suspend fun searchCachedPlaces(
        query: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<PlaceSearchSuggestion> = withContext(Dispatchers.IO) {
        val clean = query.trim().lowercase()
        if (clean.length < 2) return@withContext emptyList()
        cachedPlaceDao.searchMatching(
            pattern = "%$clean%",
            minCachedAtMs = nowMs - LaunchPolicy.PLACE_CACHE_TTL_MS
        ).map { it.toDomain() }
    }

    suspend fun loadNearbyCachedPlaces(
        origin: RideCoordinates,
        category: NearbyPlaceCategory = NearbyPlaceCategory.ALL,
        maxRadiusMeters: Double = 35_000.0,
        limit: Int = 15,
        nowMs: Long = System.currentTimeMillis()
    ): List<PlaceSearchSuggestion> = withContext(Dispatchers.IO) {
        cachedPlaceDao.loadRecent(minCachedAtMs = nowMs - LaunchPolicy.PLACE_CACHE_TTL_MS)
            .map { entity ->
                val domain = entity.toDomain()
                val distM = LaunchPolicy.distanceMeters(origin, domain.coordinates)
                domain.copy(distanceMeters = distM)
            }
            .filter { (it.distanceMeters ?: Double.MAX_VALUE) <= maxRadiusMeters }
            .filter { LaunchPolicy.matchesNearbyCategory(it, category) }
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            .take(limit)
    }

    fun encodeCreatePayload(payload: OfflineRideCreatePayload): String =
        json.encodeToString(payload)

    fun decodeCreatePayload(raw: String): OfflineRideCreatePayload =
        json.decodeFromString(raw)

    fun encodeRideActionPayload(payload: OfflineRideActionPayload): String =
        json.encodeToString(payload)

    fun decodeRideActionPayload(raw: String): OfflineRideActionPayload? =
        runCatching { json.decodeFromString<OfflineRideActionPayload>(raw) }.getOrNull()

    fun encodeLegacyAction(action: PendingOfflineAction): String =
        json.encodeToString(action)

    fun decodeLegacyAction(raw: String): PendingOfflineAction? =
        runCatching { json.decodeFromString<PendingOfflineAction>(raw) }.getOrNull()

    fun scheduleBackgroundSync() {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = "vhaya_offline_sync_worker"
    }
}
