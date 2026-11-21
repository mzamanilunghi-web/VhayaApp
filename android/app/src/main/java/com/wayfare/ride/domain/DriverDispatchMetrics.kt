package com.wayfare.ride.domain

import kotlin.math.roundToInt

data class DriverTripMetrics(
    val pickupKm: Double?,
    val pickupEtaMin: Int?,
    val tripKm: Double?,
    val tripEtaMin: Int?,
    val ratePerKmZar: Double?,
    val quickBidAmountsZar: List<Double>,
    val myPendingOffer: RideOffer?
)

object DriverDispatchMetrics {
    fun compute(
        trip: RideTrip,
        driverCoords: RideCoordinates?,
        driverUserId: String
    ): DriverTripMetrics {
        val pickupPoint = trip.pickupCoordinates ?: trip.route?.originCoordinates
        val pickupKm = LaunchPolicy.haversineKm(driverCoords, pickupPoint)
        val pickupEtaMin = pickupKm?.let { (it * 2.4).roundToInt().coerceAtLeast(1) }

        val tripKm = trip.route?.distanceMeters
            ?.takeIf { it > 0 }
            ?.div(1000.0)
            ?: LaunchPolicy.haversineKm(pickupPoint, trip.destinationCoordinates)

        val tripEtaMin = trip.route?.durationSeconds
            ?.takeIf { it > 0 }
            ?.let { (it / 60.0).roundToInt().coerceAtLeast(1) }
            ?: tripKm?.let { (it * 2.1).roundToInt().coerceAtLeast(2) }

        val ratePerKmZar = if (tripKm != null && tripKm >= 0.3 && trip.fare > 0.0) {
            trip.fare / tripKm
        } else {
            null
        }

        val quickBidAmountsZar = if (trip.pricingMode == PricingMode.OFFER) {
            val base = trip.fare.roundToInt().coerceAtLeast(20)
            val step = if (base >= 200) 20 else 10
            listOf(
                (base + step).toDouble(),
                (base + step * 2).toDouble(),
                (base + step * 3).toDouble()
            ).distinct()
        } else {
            emptyList()
        }

        val myPendingOffer = trip.offers.firstOrNull { offer ->
            driverUserId.isNotBlank() &&
                offer.driverId == driverUserId &&
                offer.status.equals("pending", ignoreCase = true)
        }

        return DriverTripMetrics(
            pickupKm = pickupKm,
            pickupEtaMin = pickupEtaMin,
            tripKm = tripKm,
            tripEtaMin = tripEtaMin,
            ratePerKmZar = ratePerKmZar,
            quickBidAmountsZar = quickBidAmountsZar,
            myPendingOffer = myPendingOffer
        )
    }
}
