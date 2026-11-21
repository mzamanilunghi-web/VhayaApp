package com.wayfare.ride.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object VerificationSyncScheduler {
    const val UNIQUE_PERIODIC_VERIFICATION_WORK = "vhaya_periodic_account_verification"
    const val UNIQUE_IMMEDIATE_VERIFICATION_WORK = "vhaya_immediate_account_verification"

    fun schedulePeriodicVerificationSync(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<AccountVerificationWorker>(
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_VERIFICATION_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun scheduleImmediateVerificationCheck(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<AccountVerificationWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15L,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_VERIFICATION_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancelVerificationSync(context: Context) {
        runCatching {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(UNIQUE_PERIODIC_VERIFICATION_WORK)
            manager.cancelUniqueWork(UNIQUE_IMMEDIATE_VERIFICATION_WORK)
        }
    }
}
