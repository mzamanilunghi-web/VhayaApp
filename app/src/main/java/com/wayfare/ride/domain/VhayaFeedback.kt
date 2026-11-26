package com.wayfare.ride.domain

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class VhayaFeedbackTone(val defaultTitle: String, val defaultDurationMs: Long) {
    SUCCESS("Done", 3600L),
    ERROR("Attention", 5000L),
    WARNING("Heads up", 4200L),
    INFO("Vhaya", 3200L),
    SUPPORT("Vhaya Support", 5400L)
}

enum class VhayaFeedbackAction {
    NONE,
    OPEN_SUPPORT,
    OPEN_CHAT
}

data class VhayaFeedbackMessage(
    val id: Long,
    val message: String,
    val tone: VhayaFeedbackTone,
    val title: String = tone.defaultTitle,
    val actionLabel: String? = null,
    val action: VhayaFeedbackAction = VhayaFeedbackAction.NONE,
    val durationMs: Long = tone.defaultDurationMs,
    val tripId: String? = null
)

object VhayaFeedbackClassifier {
    private val nextId = AtomicLong(1L)

    fun classify(
        message: String,
        explicitTone: VhayaFeedbackTone? = null,
        title: String? = null,
        actionLabel: String? = null,
        action: VhayaFeedbackAction = VhayaFeedbackAction.NONE,
        durationMs: Long? = null,
        tripId: String? = null
    ): VhayaFeedbackMessage {
        val resolvedTone = explicitTone ?: inferTone(message)
        return VhayaFeedbackMessage(
            id = nextId.getAndIncrement(),
            message = message,
            tone = resolvedTone,
            title = title?.takeIf { it.isNotBlank() } ?: resolvedTone.defaultTitle,
            actionLabel = actionLabel,
            action = action,
            durationMs = durationMs ?: resolvedTone.defaultDurationMs,
            tripId = tripId
        )
    }

    fun inferTone(message: String): VhayaFeedbackTone {
        val lower = message.lowercase(Locale.ROOT)
        return when {
            lower.contains("support") && (lower.contains("update") || lower.contains("reply") || lower.contains("ticket")) ->
                VhayaFeedbackTone.SUPPORT

            listOf(
                "could not",
                "failed",
                "invalid",
                "unable",
                "error",
                "blocked",
                "denied",
                "not confirmed",
                "declined",
                "no longer available",
                "expired —"
            ).any { lower.contains(it) } -> VhayaFeedbackTone.ERROR

            listOf(
                "offline",
                "pending",
                "awaiting",
                "cancelled",
                "canceled",
                "paused",
                "timed out"
            ).any { lower.contains(it) } -> VhayaFeedbackTone.WARNING

            listOf(
                "signed in",
                "completed",
                "confirmed",
                "verified",
                "saved",
                "submitted",
                "sent",
                "added",
                "removed",
                "recorded",
                "released",
                "updated",
                "marked",
                "generated",
                "active",
                "you are online",
                "thanks for rating",
                "synced",
                "accepted",
                "ready",
                "earned",
                "enabled"
            ).any { lower.contains(it) } -> VhayaFeedbackTone.SUCCESS

            else -> VhayaFeedbackTone.INFO
        }
    }
}
