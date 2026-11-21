package com.wayfare.ride.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TripEntity::class,
        SavedDestinationEntity::class,
        TrustedContactEntity::class,
        WalletTransactionEntity::class,
        DriverApplicationEntity::class,
        SupportCaseEntity::class,
        PayoutRecordEntity::class,
        UserAccountEntity::class,
        PayoutBatchEntity::class,
        ActivityLogEntity::class,
        CachedRouteEntity::class,
        PendingRideSyncEntity::class,
        CachedPlaceEntity::class,
        RoadHazardEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(WayfareTypeConverters::class)
abstract class WayfareDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun savedDestinationDao(): SavedDestinationDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun walletDao(): WalletDao
    abstract fun driverApplicationDao(): DriverApplicationDao
    abstract fun supportCaseDao(): SupportCaseDao
    abstract fun payoutDao(): PayoutDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun payoutBatchDao(): PayoutBatchDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun cachedRouteDao(): CachedRouteDao
    abstract fun pendingRideSyncDao(): PendingRideSyncDao
    abstract fun cachedPlaceDao(): CachedPlaceDao
    abstract fun roadHazardDao(): RoadHazardDao

    companion object {
        @Volatile
        private var INSTANCE: WayfareDatabase? = null

        fun getInstance(context: Context): WayfareDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    WayfareDatabase::class.java,
                    "vhaya_local.db"
                )
                    .addMigrations(
                        WayfareMigrations.MIGRATION_7_8,
                        WayfareMigrations.MIGRATION_8_9,
                        WayfareMigrations.MIGRATION_9_10,
                        WayfareMigrations.MIGRATION_10_11
                    )
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
