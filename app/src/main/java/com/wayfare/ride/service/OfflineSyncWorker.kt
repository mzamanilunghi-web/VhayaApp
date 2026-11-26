package com.wayfare.ride.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wayfare.ride.WayfareApplication
import com.wayfare.ride.domain.AuthState

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as? WayfareApplication)?.repository
            ?: return Result.success()
        if (repo.authState.value !is AuthState.Authenticated) {
            return Result.success()
        }
        val flushed = repo.flushOfflineQueue(force = true)
        if (flushed > 0) {
            repo.refreshActiveSessionData()
        }
        return if (repo.pendingOfflineCount.value > 0) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
