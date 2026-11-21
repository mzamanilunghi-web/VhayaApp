package com.wayfare.ride.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    suspend fun getAll(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(trips: List<TripEntity>)

    @Query("DELETE FROM trips")
    suspend fun clearAll()

    @Query("DELETE FROM trips WHERE syncStatus = 'SYNCED'")
    suspend fun clearSyncedOnly()

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM trips WHERE syncStatus != 'SYNCED' ORDER BY createdAt DESC")
    suspend fun getUnsyncedTrips(): List<TripEntity>

    @Transaction
    suspend fun replaceAll(trips: List<TripEntity>) {
        clearAll()
        if (trips.isNotEmpty()) upsertAll(trips)
    }

    @Transaction
    suspend fun replaceSyncedPreservingPending(syncedTrips: List<TripEntity>) {
        val pending = getUnsyncedTrips()
        val syncedClientIds = syncedTrips
            .mapNotNull { it.clientRequestId?.takeIf { c -> c.isNotBlank() } }
            .toSet()
        for (item in pending) {
            val clientReq = item.clientRequestId
            if (!clientReq.isNullOrBlank() && clientReq in syncedClientIds) {
                deleteById(item.id)
            }
        }
        val protectedIds = getUnsyncedTrips().map { it.id }.toSet()
        val nonConflictingServerTrips = syncedTrips.filter { it.id !in protectedIds }
        clearSyncedOnly()
        if (nonConflictingServerTrips.isNotEmpty()) upsertAll(nonConflictingServerTrips)
    }

    @Transaction
    suspend fun replaceOfflinePlaceholderWithServerTrip(
        offlineTripId: String,
        serverTrip: TripEntity
    ) {
        deleteById(offlineTripId)
        upsert(serverTrip)
    }

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int
}

@Dao
interface SavedDestinationDao {
    @Query("SELECT * FROM saved_destinations ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedDestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SavedDestinationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SavedDestinationEntity>)

    @Query("DELETE FROM saved_destinations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_destinations")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM saved_destinations")
    suspend fun count(): Int
}

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<TrustedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrustedContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TrustedContactEntity>)

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM trusted_contacts")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun count(): Int
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet_transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WalletTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: WalletTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<WalletTransactionEntity>)

    @Query("DELETE FROM wallet_transactions")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(txs: List<WalletTransactionEntity>) {
        clearAll()
        if (txs.isNotEmpty()) insertAll(txs)
    }

    @Query("SELECT COUNT(*) FROM wallet_transactions")
    suspend fun count(): Int
}

@Dao
interface DriverApplicationDao {
    @Query("SELECT * FROM driver_applications ORDER BY displayName ASC")
    fun observeAll(): Flow<List<DriverApplicationEntity>>

    @Query("SELECT * FROM driver_applications WHERE userId = :userId LIMIT 1")
    suspend fun getById(userId: String): DriverApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DriverApplicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DriverApplicationEntity>)

    @Query("DELETE FROM driver_applications")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<DriverApplicationEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COUNT(*) FROM driver_applications")
    suspend fun count(): Int
}

@Dao
interface SupportCaseDao {
    @Query("SELECT * FROM support_cases ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SupportCaseEntity>>

    @Query("SELECT * FROM support_cases WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SupportCaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SupportCaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SupportCaseEntity>)

    @Query("DELETE FROM support_cases")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<SupportCaseEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COALESCE(MAX(id), 100) FROM support_cases")
    suspend fun maxId(): Long

    @Query("SELECT COUNT(*) FROM support_cases")
    suspend fun count(): Int
}

@Dao
interface PayoutDao {
    @Query("SELECT * FROM payout_records ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<PayoutRecordEntity>>

    @Query("SELECT * FROM payout_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PayoutRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PayoutRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PayoutRecordEntity>)

    @Query("DELETE FROM payout_records")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PayoutRecordEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COALESCE(MAX(id), 500) FROM payout_records")
    suspend fun maxId(): Long

    @Query("SELECT COUNT(*) FROM payout_records")
    suspend fun count(): Int
}

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE userId = :userId LIMIT 1")
    suspend fun getById(userId: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UserAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UserAccountEntity>)

    @Query("DELETE FROM user_accounts")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<UserAccountEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COUNT(*) FROM user_accounts")
    suspend fun count(): Int
}

@Dao
interface PayoutBatchDao {
    @Query("SELECT * FROM payout_batches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PayoutBatchEntity>>

    @Query("SELECT * FROM payout_batches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PayoutBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PayoutBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PayoutBatchEntity>)

    @Query("DELETE FROM payout_batches")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PayoutBatchEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COUNT(*) FROM payout_batches")
    suspend fun count(): Int
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT 80")
    fun observeAll(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityLogEntity>)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<ActivityLogEntity>) {
        clearAll()
        if (items.isNotEmpty()) insertAll(items)
    }

    @Query("SELECT COUNT(*) FROM activity_logs")
    suspend fun count(): Int
}

@Dao
interface CachedRouteDao {
    @Query("SELECT * FROM cached_routes WHERE corridorKey = :corridorKey LIMIT 1")
    suspend fun getByKey(corridorKey: String): CachedRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: CachedRouteEntity)

    @Query("DELETE FROM cached_routes WHERE cachedAtMs < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long)

    @Query("DELETE FROM cached_routes")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cached_routes")
    suspend fun count(): Int
}

@Dao
interface PendingRideSyncDao {
    @Query("SELECT * FROM pending_ride_sync ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<PendingRideSyncEntity>>

    @Query("SELECT COUNT(*) FROM pending_ride_sync WHERE syncState = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_ride_sync ORDER BY createdAtMs ASC")
    suspend fun getAllOnce(): List<PendingRideSyncEntity>

    @Query("SELECT * FROM pending_ride_sync WHERE syncState = 'PENDING' ORDER BY createdAtMs ASC")
    suspend fun getPendingOnce(): List<PendingRideSyncEntity>

    @Query("SELECT COUNT(*) FROM pending_ride_sync WHERE syncState = 'PENDING'")
    suspend fun countPending(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingRideSyncEntity)

    @Query("DELETE FROM pending_ride_sync WHERE clientRequestId = :clientRequestId")
    suspend fun deleteByClientRequestId(clientRequestId: String)

    @Query("DELETE FROM pending_ride_sync WHERE operationType = :operationType AND tripId = :tripId")
    suspend fun deleteByTypeAndTrip(operationType: String, tripId: String)

    @Query("DELETE FROM pending_ride_sync WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: String)

    @Query("DELETE FROM pending_ride_sync")
    suspend fun clearAll()

    @Transaction
    suspend fun upsertLocationCoalesced(entity: PendingRideSyncEntity) {
        deleteByTypeAndTrip("driver_location", entity.tripId)
        upsert(entity)
    }
}

@Dao
interface CachedPlaceDao {
    @Query(
        "SELECT * FROM cached_places " +
            "WHERE (LOWER(label) LIKE :pattern OR LOWER(queryTag) LIKE :pattern) " +
            "AND cachedAtMs >= :minCachedAtMs " +
            "ORDER BY cachedAtMs DESC LIMIT 8"
    )
    suspend fun searchMatching(pattern: String, minCachedAtMs: Long): List<CachedPlaceEntity>

    @Query(
        "SELECT * FROM cached_places " +
            "WHERE cachedAtMs >= :minCachedAtMs " +
            "ORDER BY cachedAtMs DESC LIMIT 60"
    )
    suspend fun loadRecent(minCachedAtMs: Long): List<CachedPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<CachedPlaceEntity>)

    @Query("DELETE FROM cached_places WHERE cachedAtMs < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long)

    @Query("DELETE FROM cached_places")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cached_places")
    suspend fun count(): Int
}

@Dao
interface RoadHazardDao {
    @Query("SELECT * FROM road_hazards WHERE expiresAtMs > :nowMs ORDER BY reportedAtMs DESC")
    fun observeActive(nowMs: Long): Flow<List<RoadHazardEntity>>

    @Query("SELECT * FROM road_hazards ORDER BY reportedAtMs DESC")
    fun observeAll(): Flow<List<RoadHazardEntity>>

    @Query("SELECT * FROM road_hazards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RoadHazardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hazard: RoadHazardEntity)

    @Query("UPDATE road_hazards SET confirmations = confirmations + 1, expiresAtMs = :newExpiresAtMs WHERE id = :id")
    suspend fun confirmHazard(id: String, newExpiresAtMs: Long)

    @Query("DELETE FROM road_hazards WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM road_hazards WHERE expiresAtMs <= :nowMs")
    suspend fun deleteExpired(nowMs: Long)

    @Query("SELECT COUNT(*) FROM road_hazards")
    suspend fun count(): Int
}

