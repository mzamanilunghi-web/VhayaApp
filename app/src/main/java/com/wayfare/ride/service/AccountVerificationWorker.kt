package com.wayfare.ride.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wayfare.ride.WayfareApplication
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.AccountVerificationChange
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.notifications.RideNotificationHelper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

class AccountVerificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as? WayfareApplication)?.repository
            ?: return Result.success()
        return executeVerificationSync(repo, applicationContext)
    }

    companion object {
        internal suspend fun executeVerificationSync(
            repo: WayfareRepository,
            appContext: Context
        ): Result {
            if (repo.authState.value is AuthState.Unauthenticated) {
                VerificationSyncScheduler.cancelVerificationSync(appContext)
                return Result.success()
            }

            val detectedChanges = mutableListOf<AccountVerificationChange>()
            var stillPendingVerification = false
            var syncResult: kotlin.Result<Boolean> = kotlin.Result.success(false)

            coroutineScope {
                val collectorJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    repo.accountVerificationChanges.collect { change ->
                        detectedChanges += change
                    }
                }
                try {
                    syncResult = repo.pollVerificationStatusNow()
                    stillPendingVerification = syncResult.getOrDefault(repo.needsBackgroundVerificationPolling())
                } finally {
                    collectorJob.cancel()
                }
            }

            if (!repo.isAppInForeground.value) {
                for (change in detectedChanges) {
                    when (change) {
                        is AccountVerificationChange.EmailVerified -> {
                            RideNotificationHelper.showAccountVerificationNotification(
                                context = appContext,
                                title = "Email verified · Welcome to Vhaya",
                                body = "Your Vhaya account is now active. Tap to open the app."
                            )
                        }
                        is AccountVerificationChange.DriverVerificationUpdated -> {
                            if (change.verificationStatus.equals("approved", ignoreCase = true) ||
                                change.role == RideRole.DRIVER
                            ) {
                                RideNotificationHelper.showAccountVerificationNotification(
                                    context = appContext,
                                    title = "Driver verification approved",
                                    body = "Your Vhaya driver account is verified. You can go online and accept trips."
                                )
                            } else if (change.verificationStatus.equals("rejected", ignoreCase = true) ||
                                change.applicationStatus.equals("needs_follow_up", ignoreCase = true)
                            ) {
                                RideNotificationHelper.showAccountVerificationNotification(
                                    context = appContext,
                                    title = "Driver verification update",
                                    body = change.staffNote?.takeIf { it.isNotBlank() }
                                        ?: "Please review your verification documents in Vhaya."
                                )
                            }
                        }
                    }
                }
            }

            if (!stillPendingVerification || repo.authState.value is AuthState.Unauthenticated) {
                VerificationSyncScheduler.cancelVerificationSync(appContext)
            }

            return if (syncResult.isFailure && stillPendingVerification) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}
