package com.wayfare.ride.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.wayfare.ride.MainActivity
import com.wayfare.ride.R
import com.wayfare.ride.audio.NotificationSoundStyle
import com.wayfare.ride.audio.SoundAlertPlayer
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.ReturnRideMatch
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import java.util.Locale

data class DriverOfferSpec(
    val tripId: String,
    val title: String,
    val shortText: String,
    val bigText: String,
    val fareLabel: String,
    val metaLabel: String,
    val distanceLabel: String,
    val pickupShort: String,
    val destShort: String,
    val countdownLabel: String
)

data class RiderNotificationSpec(
    val notificationId: Int,
    val title: String,
    val shortText: String,
    val bigText: String,
    val progressStep: Int,
    val progressMax: Int,
    val ongoing: Boolean,
    val alertUser: Boolean
)

data class IncomingTripChatAlert(
    val tripId: String,
    val messageId: String,
    val senderRole: String,
    val senderName: String,
    val messageText: String,
    val createdAt: Long
)

object RideNotificationHelper {
    const val CHANNEL_DRIVER_OFFERS = "vhaya_driver_offers_v3"
    const val CHANNEL_RIDER_STATUS = "vhaya_rider_status_v3"
    const val CHANNEL_LIVE_DUTY = "vhaya_live_duty"
    const val CHANNEL_TRIP_CHAT = "vhaya_trip_chat_v1"
    const val CHANNEL_ACCOUNT_UPDATES = "vhaya_account_updates_v1"
    const val CHANNEL_RETURN_RIDES = "vhaya_return_rides_v1"

    const val DRIVER_DUTY_NOTIF_ID = 4201
    const val DRIVER_OFFER_NOTIF_ID = 4202
    const val RIDER_STATUS_NOTIF_ID = 4203
    const val ACCOUNT_VERIFICATION_NOTIF_ID = 4205
    const val RETURN_RIDE_NOTIF_ID = 4206
    const val TRIP_CHAT_NOTIF_ID_BASE = 4300

    private const val VHAYA_EMERALD_ARGB = 0xFF059669.toInt()

    fun notificationSoundUri(
        context: Context,
        style: NotificationSoundStyle = SoundAlertPlayer.resolveSavedStyle(context)
    ): Uri? = when (style) {
        NotificationSoundStyle.CAR_HORN ->
            Uri.parse("android.resource://${context.packageName}/${R.raw.vhaya_car_horn}")
        NotificationSoundStyle.CAR_CHIME ->
            Uri.parse("android.resource://${context.packageName}/${R.raw.vhaya_car_chime}")
        NotificationSoundStyle.CLASSIC ->
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun activeDriverOffersChannelId(
        context: Context,
        style: NotificationSoundStyle = SoundAlertPlayer.resolveSavedStyle(context)
    ): String = if (style == NotificationSoundStyle.CAR_HORN) {
        CHANNEL_DRIVER_OFFERS
    } else {
        "${CHANNEL_DRIVER_OFFERS}_${style.key}"
    }

    fun activeRiderStatusChannelId(
        context: Context,
        style: NotificationSoundStyle = SoundAlertPlayer.resolveSavedStyle(context)
    ): String = if (style == NotificationSoundStyle.CAR_HORN) {
        CHANNEL_RIDER_STATUS
    } else {
        "${CHANNEL_RIDER_STATUS}_${style.key}"
    }

    fun activeTripChatChannelId(
        context: Context,
        style: NotificationSoundStyle = SoundAlertPlayer.resolveSavedStyle(context)
    ): String = if (style == NotificationSoundStyle.CAR_HORN) {
        CHANNEL_TRIP_CHAT
    } else {
        "${CHANNEL_TRIP_CHAT}_${style.key}"
    }

    private fun notificationAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    fun notificationManager(context: Context): NotificationManager? {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    fun ensureChannels(context: Context) {
        SupportNotificationHelper.ensureChannels(context)
        val manager = notificationManager(context) ?: return
        val style = SoundAlertPlayer.resolveSavedStyle(context)
        val soundUri = notificationSoundUri(context, style)
        val audioAttributes = notificationAudioAttributes()

        // Remove superseded v2 channels that cached the generic system sound
        manager.deleteNotificationChannel("vhaya_driver_offers_v2")
        manager.deleteNotificationChannel("vhaya_rider_status_v2")

        val driverChannelId = activeDriverOffersChannelId(context, style)
        val riderChannelId = activeRiderStatusChannelId(context, style)

        val driverOffersChannel = NotificationChannel(
            driverChannelId,
            "Incoming Ride Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Interactive ride offer cards when you are online in the background."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val riderStatusChannel = NotificationChannel(
            riderChannelId,
            "Live Ride Status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time ride progress updates while waiting for or riding in a Vhaya."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val tripChatChannel = NotificationChannel(
            activeTripChatChannelId(context, style),
            "Trip Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Messages from your rider or driver during an active trip."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val liveDutyChannel = NotificationChannel(
            CHANNEL_LIVE_DUTY,
            "Background Online Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Vhaya connected while you are online or on an active trip."
            setSound(null, null)
        }

        val accountUpdatesChannel = NotificationChannel(
            CHANNEL_ACCOUNT_UPDATES,
            "Account Verification Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies you immediately when your email or driver verification is approved."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val returnRidesChannel = NotificationChannel(
            CHANNEL_RETURN_RIDES,
            "Return Rides Radar",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when an oncoming rider along your return route requests a lift while you are on a trip."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        manager.createNotificationChannel(driverOffersChannel)
        manager.createNotificationChannel(riderStatusChannel)
        manager.createNotificationChannel(tripChatChannel)
        manager.createNotificationChannel(liveDutyChannel)
        manager.createNotificationChannel(accountUpdatesChannel)
        manager.createNotificationChannel(returnRidesChannel)
    }

    fun pickNextEligibleOffer(
        trips: List<RideTrip>,
        declinedIds: Set<String>,
        driverCoords: RideCoordinates? = null,
        nowMs: Long = System.currentTimeMillis()
    ): RideTrip? {
        return trips
            .filter { trip ->
                trip.status.isOpenForDrivers &&
                    trip.driverId == null &&
                    trip.id !in declinedIds &&
                    !LaunchPolicy.isTripExpired(trip, nowMs)
            }
            .maxByOrNull { LaunchPolicy.matchScoreForDriver(it, driverCoords) }
    }

    fun buildDriverOfferSpec(
        trip: RideTrip,
        nowMs: Long = System.currentTimeMillis()
    ): DriverOfferSpec {
        val pickupShort = trip.pickup.substringBefore(" ·").trim().ifBlank { "Pickup" }
        val destShort = trip.destination.substringBefore(" ·").trim().ifBlank { "Destination" }
        val fareLabel = LaunchPolicy.formatZar(trip.fare)
        val distanceMeters = trip.route?.distanceMeters ?: 0
        val distanceLabel = if (distanceMeters > 0) {
            "%.1f km".format(Locale.US, distanceMeters / 1000.0)
        } else {
            "Nearby"
        }
        val distanceSuffix = if (distanceMeters > 0) " · $distanceLabel" else ""
        val remaining = LaunchPolicy.formatCountdownMs(LaunchPolicy.remainingOpenMs(trip, nowMs))
        val metaLabel = if (trip.isReturnRequest) {
            "Return Request · ${trip.category.label} · ${trip.paymentMethod.label}"
        } else {
            "${trip.category.label} · ${trip.paymentMethod.label}"
        }
        val titlePrefix = if (trip.isReturnRequest) "Return Ride" else "New Ride"
        val title = "$titlePrefix · $fareLabel$distanceSuffix"
        val shortText = "$pickupShort → $destShort"
        val bigText = buildString {
            appendLine("Pickup: $pickupShort")
            appendLine("Drop-off: $destShort")
            if (trip.isReturnRequest) appendLine("Rural Return Request (backhaul)")
            append("Pay: ${trip.paymentMethod.label} · ${trip.category.label} · Expires in $remaining")
        }
        return DriverOfferSpec(
            tripId = trip.id,
            title = title,
            shortText = shortText,
            bigText = bigText,
            fareLabel = fareLabel,
            metaLabel = metaLabel,
            distanceLabel = distanceLabel,
            pickupShort = pickupShort,
            destShort = destShort,
            countdownLabel = remaining
        )
    }

    fun buildRiderStatusSpec(
        trip: RideTrip,
        isFirstNotification: Boolean = false,
        previousStatus: RideStatus? = null,
        previousOfferCount: Int = 0
    ): RiderNotificationSpec {
        val pickupShort = trip.pickup.substringBefore(" ·").trim().ifBlank { "Pickup" }
        val destShort = trip.destination.substringBefore(" ·").trim().ifBlank { "Destination" }
        val driver = trip.driverProfile
        val driverLabel = driver?.name?.takeIf { it.isNotBlank() } ?: "Your driver"
        val vehicleSummary = driver?.let {
            listOf(it.vehicleColor, it.vehicle, it.registration)
                .filter { part -> part.isNotBlank() }
                .joinToString(" · ")
        }.orEmpty()
        val pinSuffix = trip.tripPin?.takeIf { it.isNotBlank() }?.let { "PIN $it" }.orEmpty()
        val pendingOffers = trip.offers.filter { it.status.equals("pending", ignoreCase = true) }
        val lowestOffer = pendingOffers.minByOrNull { it.amount }?.amount

        return when (trip.status) {
            RideStatus.REQUESTED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = if (trip.isReturnRequest) {
                    "Return Request · Finding returning driver…"
                } else {
                    "Finding a Vhaya driver…"
                },
                shortText = "$pickupShort → $destShort · ${LaunchPolicy.formatZar(trip.fare)}",
                bigText = if (trip.isReturnRequest) {
                    "Return Request: $pickupShort → $destShort\nMatching with drivers heading near your pickup (within 8 km)."
                } else {
                    "Pickup: $pickupShort\nDrop-off: $destShort\nWe'll update this card as soon as a driver responds."
                },
                progressStep = 1,
                progressMax = 5,
                ongoing = true,
                alertUser = isFirstNotification
            )
            RideStatus.COUNTERED -> {
                val count = pendingOffers.size.coerceAtLeast(1)
                val fareHint = lowestOffer?.let { "From ${LaunchPolicy.formatZar(it)}" } ?: LaunchPolicy.formatZar(trip.fare)
                RiderNotificationSpec(
                    notificationId = RIDER_STATUS_NOTIF_ID,
                    title = "$count driver offer${if (count == 1) "" else "s"} ready",
                    shortText = "$fareHint · Tap to review and confirm",
                    bigText = "$count driver${if (count == 1) " has" else "s have"} offered for $pickupShort → $destShort ($fareHint). Tap to accept.",
                    progressStep = 2,
                    progressMax = 5,
                    ongoing = true,
                    alertUser = isFirstNotification || previousStatus != RideStatus.COUNTERED || pendingOffers.size > previousOfferCount
                )
            }
            RideStatus.ACCEPTED -> {
                val needsCardPayment = trip.paymentMethod == PaymentMethod.CARD &&
                    (trip.paymentStatus == PaymentStatus.NONE ||
                        trip.paymentStatus == PaymentStatus.PENDING ||
                        trip.paymentStatus == PaymentStatus.FAILED)
                RiderNotificationSpec(
                    notificationId = RIDER_STATUS_NOTIF_ID,
                    title = if (needsCardPayment) {
                        "$driverLabel accepted · Pay ${LaunchPolicy.formatZar(trip.fare)}"
                    } else {
                        "$driverLabel is on the way"
                    },
                    shortText = if (needsCardPayment) {
                        listOf("Tap to complete payment", vehicleSummary).filter { it.isNotBlank() }.joinToString(" · ")
                    } else {
                        listOf(vehicleSummary, pinSuffix).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { "Heading to $pickupShort" }
                    },
                    bigText = buildString {
                        append("$driverLabel accepted your ride to $destShort.")
                        if (needsCardPayment) {
                            append("\nTap to complete ${LaunchPolicy.formatZar(trip.fare)} Paystack payment.")
                        }
                        if (vehicleSummary.isNotBlank()) append("\nVehicle: $vehicleSummary")
                        if (pinSuffix.isNotBlank()) append("\nPickup $pinSuffix")
                    },
                    progressStep = 3,
                    progressMax = 5,
                    ongoing = true,
                    alertUser = isFirstNotification || previousStatus != RideStatus.ACCEPTED
                )
            }
            RideStatus.ARRIVED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = if (pinSuffix.isNotBlank()) "$driverLabel arrived · $pinSuffix" else "$driverLabel arrived at pickup",
                shortText = vehicleSummary.ifBlank { "Meet your driver at $pickupShort" },
                bigText = buildString {
                    append("$driverLabel is waiting at $pickupShort.")
                    if (vehicleSummary.isNotBlank()) append("\nVehicle: $vehicleSummary")
                    if (pinSuffix.isNotBlank()) append("\nShare $pinSuffix when boarding.")
                },
                progressStep = 4,
                progressMax = 5,
                ongoing = true,
                alertUser = isFirstNotification || previousStatus != RideStatus.ARRIVED
            )
            RideStatus.IN_PROGRESS -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "On the way to $destShort",
                shortText = listOf(driverLabel, LaunchPolicy.formatZar(trip.fare)).joinToString(" · "),
                bigText = "Riding with $driverLabel to $destShort (${LaunchPolicy.formatZar(trip.fare)} via ${trip.paymentMethod.label}).",
                progressStep = 5,
                progressMax = 5,
                ongoing = true,
                alertUser = isFirstNotification || previousStatus != RideStatus.IN_PROGRESS
            )
            RideStatus.COMPLETED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "Trip completed · ${LaunchPolicy.formatZar(trip.fare)}",
                shortText = "Tap to view receipt and rate $driverLabel",
                bigText = "You arrived at $destShort. Fare: ${LaunchPolicy.formatZar(trip.fare)} (${trip.paymentStatus.label}). Tap to rate your driver.",
                progressStep = 0,
                progressMax = 0,
                ongoing = false,
                alertUser = previousStatus != RideStatus.COMPLETED
            )
            RideStatus.CANCELLED -> {
                val holdNote = if (trip.paymentMethod == PaymentMethod.CARD) {
                    "Card authorization hold released."
                } else {
                    "No charge was taken."
                }
                RiderNotificationSpec(
                    notificationId = RIDER_STATUS_NOTIF_ID,
                    title = "Ride cancelled",
                    shortText = "$pickupShort → $destShort · $holdNote",
                    bigText = "Your ride from $pickupShort to $destShort was cancelled. $holdNote",
                    progressStep = 0,
                    progressMax = 0,
                    ongoing = false,
                    alertUser = previousStatus != RideStatus.CANCELLED
                )
            }
            RideStatus.UNFULFILLED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "No driver matched in time",
                shortText = trip.unfulfillmentReason?.takeIf { it.isNotBlank() } ?: "Tap to request another Vhaya",
                bigText = trip.unfulfillmentReason?.takeIf { it.isNotBlank() }
                    ?: "No nearby driver was available for $pickupShort → $destShort. Tap to re-request.",
                progressStep = 0,
                progressMax = 0,
                ongoing = false,
                alertUser = previousStatus != RideStatus.UNFULFILLED
            )
        }
    }

    fun buildDriverDutyNotification(context: Context, hasActiveTrip: Boolean): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            4210,
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_DUTY)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(context.getString(R.string.notif_driver_online_title))
            .setContentText(
                if (hasActiveTrip) context.getString(R.string.notif_driver_active_body)
                else context.getString(R.string.notif_driver_online_body)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)

        if (!hasActiveTrip) {
            val goOfflineIntent = PendingIntent.getBroadcast(
                context,
                4211,
                Intent(context, RideNotificationActionReceiver::class.java).apply {
                    action = RideNotificationActionReceiver.ACTION_GO_OFFLINE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.notif_action_go_offline), goOfflineIntent)
        }

        return builder.build()
    }

    fun buildDriverOfferNotification(context: Context, trip: RideTrip): Notification {
        ensureChannels(context)
        val spec = buildDriverOfferSpec(trip)

        // Android 12+ requires Activity PendingIntent directly to avoid notification trampoline bans
        val acceptActivityIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("vhaya://ride?action=accept&tripId=${Uri.encode(trip.id)}"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            ("accept-${trip.id}").hashCode(),
            acceptActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineBroadcastIntent = Intent(context, RideNotificationActionReceiver::class.java).apply {
            action = RideNotificationActionReceiver.ACTION_DECLINE_OFFER
            putExtra(RideNotificationActionReceiver.EXTRA_TRIP_ID, trip.id)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            ("decline-${trip.id}").hashCode(),
            declineBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            ("open-${trip.id}").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val collapsedViews = RemoteViews(
            context.packageName,
            R.layout.notification_driver_offer_collapsed
        ).apply {
            setTextViewText(R.id.notif_collapsed_fare, spec.fareLabel)
            setTextViewText(R.id.notif_collapsed_meta, spec.metaLabel)
            setTextViewText(R.id.notif_collapsed_timer, spec.countdownLabel)
            setTextViewText(R.id.notif_collapsed_route, spec.shortText)
        }

        val expandedViews = RemoteViews(
            context.packageName,
            R.layout.notification_driver_offer_expanded
        ).apply {
            setTextViewText(R.id.notif_expanded_meta, spec.metaLabel)
            setTextViewText(R.id.notif_expanded_timer, spec.countdownLabel)
            setTextViewText(R.id.notif_expanded_fare, spec.fareLabel)
            setTextViewText(R.id.notif_expanded_distance, spec.distanceLabel)
            setTextViewText(R.id.notif_expanded_pickup, spec.pickupShort)
            setTextViewText(R.id.notif_expanded_destination, spec.destShort)
            setOnClickPendingIntent(R.id.notif_btn_accept, acceptPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_decline, declinePendingIntent)
        }

        return NotificationCompat.Builder(context, activeDriverOffersChannelId(context))
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(spec.title)
            .setContentText(spec.shortText)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setSound(notificationSoundUri(context))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    fun buildRiderStatusNotification(context: Context, spec: RiderNotificationSpec): Notification {
        ensureChannels(context)
        val openRideIntent = PendingIntent.getActivity(
            context,
            4220,
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, activeRiderStatusChannelId(context))
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(spec.title)
            .setContentText(spec.shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.bigText))
            .setOngoing(spec.ongoing)
            .setAutoCancel(!spec.ongoing)
            .setOnlyAlertOnce(!spec.alertUser)
            .setSilent(!spec.alertUser)
            .setPriority(if (spec.alertUser) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openRideIntent)

        if (spec.alertUser) {
            builder.setSound(notificationSoundUri(context))
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        if (spec.progressMax > 0) {
            builder.setProgress(spec.progressMax, spec.progressStep.coerceIn(0, spec.progressMax), false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    fun showDriverOfferNotification(context: Context, trip: RideTrip) {
        if (!SupportNotificationHelper.hasNotificationPermission(context)) return
        val manager = notificationManager(context) ?: return
        val notification = buildDriverOfferNotification(context, trip)
        runCatching {
            manager.notify(DRIVER_OFFER_NOTIF_ID, notification)
        }
    }

    fun showDriverOfferNotificationIfBackground(
        context: Context,
        trip: RideTrip,
        isAppInForeground: Boolean
    ): Boolean {
        if (isAppInForeground || !SupportNotificationHelper.hasNotificationPermission(context)) {
            return false
        }
        val manager = notificationManager(context) ?: return false
        val notification = buildDriverOfferNotification(context, trip)
        return runCatching {
            manager.notify(DRIVER_OFFER_NOTIF_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun dismissDriverOfferNotification(context: Context) {
        runCatching {
            notificationManager(context)?.cancel(DRIVER_OFFER_NOTIF_ID)
        }
    }

    fun showOrUpdateRiderStatusNotification(
        context: Context,
        trip: RideTrip,
        previousStatus: RideStatus?,
        previousOfferCount: Int,
        isFirstNotification: Boolean
    ): Notification {
        val spec = buildRiderStatusSpec(
            trip = trip,
            isFirstNotification = isFirstNotification,
            previousStatus = previousStatus,
            previousOfferCount = previousOfferCount
        )
        val notification = buildRiderStatusNotification(context, spec)
        if (SupportNotificationHelper.hasNotificationPermission(context)) {
            runCatching {
                notificationManager(context)?.notify(spec.notificationId, notification)
            }
        }
        return notification
    }

    fun dismissRiderStatusNotification(context: Context) {
        runCatching {
            notificationManager(context)?.cancel(RIDER_STATUS_NOTIF_ID)
        }
    }

    fun diffIncomingTripMessages(
        trips: List<RideTrip>,
        currentUserId: String,
        role: RideRole,
        seenMessageIds: MutableSet<String>,
        seedOnly: Boolean
    ): List<IncomingTripChatAlert> {
        if (currentUserId.isBlank()) return emptyList()
        val ownSender = if (role == RideRole.DRIVER) "driver" else "rider"
        val alerts = mutableListOf<IncomingTripChatAlert>()
        for (trip in trips) {
            if (!trip.status.isActive) continue
            val belongsToUser = when (role) {
                RideRole.RIDER -> trip.riderId == currentUserId
                RideRole.DRIVER -> trip.driverId == currentUserId
                RideRole.ADMIN -> false
            }
            if (!belongsToUser) continue

            val counterpartyName = when (role) {
                RideRole.RIDER -> trip.driverProfile?.name?.takeIf { it.isNotBlank() } ?: "Your driver"
                RideRole.DRIVER -> "Your rider"
                RideRole.ADMIN -> "Vhaya"
            }

            for (msg in trip.messages) {
                if (!msg.channel.equals("driver", ignoreCase = true)) continue
                val isNew = seenMessageIds.add(msg.id)
                if (seedOnly || !isNew) continue
                if (msg.sender.equals(ownSender, ignoreCase = true)) continue
                if (msg.message.isBlank()) continue
                alerts += IncomingTripChatAlert(
                    tripId = trip.id,
                    messageId = msg.id,
                    senderRole = msg.sender,
                    senderName = counterpartyName,
                    messageText = msg.message.trim(),
                    createdAt = msg.createdAt
                )
            }
        }
        return alerts
    }

    fun tripChatNotificationId(tripId: String): Int =
        TRIP_CHAT_NOTIF_ID_BASE + (tripId.hashCode() and 0x0FFF)

    fun buildTripChatNotification(
        context: Context,
        alert: IncomingTripChatAlert
    ): Notification {
        ensureChannels(context)
        val openChatIntent = PendingIntent.getActivity(
            context,
            ("chat-${alert.tripId}").hashCode(),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("vhaya://ride?action=chat&tripId=${Uri.encode(alert.tripId)}"),
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Message from ${alert.senderName}"
        return NotificationCompat.Builder(context, activeTripChatChannelId(context))
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(title)
            .setContentText(alert.messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.messageText))
            .setSound(notificationSoundUri(context))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openChatIntent)
            .build()
    }

    fun showTripChatNotification(context: Context, alert: IncomingTripChatAlert) {
        if (!SupportNotificationHelper.hasNotificationPermission(context)) return
        val manager = notificationManager(context) ?: return
        val notification = buildTripChatNotification(context, alert)
        runCatching {
            manager.notify(tripChatNotificationId(alert.tripId), notification)
        }
    }

    fun dismissTripChatNotification(context: Context, tripId: String) {
        runCatching {
            notificationManager(context)?.cancel(tripChatNotificationId(tripId))
        }
    }

    fun buildAccountVerificationNotification(
        context: Context,
        title: String,
        body: String
    ): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            ACCOUNT_VERIFICATION_NOTIF_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ACCOUNT_UPDATES)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSound(notificationSoundUri(context))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    fun showAccountVerificationNotification(
        context: Context,
        title: String,
        body: String
    ) {
        if (!SupportNotificationHelper.hasNotificationPermission(context)) return
        val manager = notificationManager(context) ?: return
        val notification = buildAccountVerificationNotification(context, title, body)
        runCatching {
            manager.notify(ACCOUNT_VERIFICATION_NOTIF_ID, notification)
        }
    }

    fun buildReturnRideMatchNotification(
        context: Context,
        match: ReturnRideMatch
    ): Notification {
        ensureChannels(context)
        val trip = match.trip
        val pickupShort = trip.pickup.substringBefore(" ·").trim().ifBlank { "Pickup" }
        val destShort = trip.destination.substringBefore(" ·").trim().ifBlank { "Destination" }
        val fareLabel = LaunchPolicy.formatZar(trip.fare)
        val title = "Return Ride match · $fareLabel"
        val shortText = "$pickupShort → $destShort (${match.proximitySummary})"
        val bigText = buildString {
            appendLine("$pickupShort → $destShort")
            append("${match.proximitySummary} · ${LaunchPolicy.formatZar(trip.fare)} · Queue next after drop-off")
        }
        val openAppIntent = PendingIntent.getActivity(
            context,
            ("return-${trip.id}").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_RETURN_RIDES)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSound(notificationSoundUri(context))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    fun showReturnRideMatchNotification(
        context: Context,
        match: ReturnRideMatch
    ) {
        if (!SupportNotificationHelper.hasNotificationPermission(context)) return
        val manager = notificationManager(context) ?: return
        val notification = buildReturnRideMatchNotification(context, match)
        runCatching {
            manager.notify(RETURN_RIDE_NOTIF_ID, notification)
        }
    }
}
