package com.example.domain

import com.example.model.BatteryRoundTripAssessment
import com.example.model.BatteryTrafficLight
import com.example.model.ScooterProfile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object BatteryRangeCalculator {

    fun assessRoundTrip(
        firstMileKm: Double,
        lastMileKm: Double,
        profile: ScooterProfile
    ): BatteryRoundTripAssessment {
        val oneWayKm = (firstMileKm + lastMileKm).coerceAtLeast(0.0)
        val roundTripKm = oneWayKm * 2.0
        val availableKm = profile.currentAvailableRangeKm
        val remainingAfterRoundTrip = availableKm - roundTripKm
        val oneWayFeasible = availableKm >= oneWayKm
        val safeBufferKm = max(3.0, availableKm * 0.20)

        val status = when {
            remainingAfterRoundTrip >= safeBufferKm -> BatteryTrafficLight.SAFE
            remainingAfterRoundTrip >= 0.0 -> BatteryTrafficLight.TIGHT
            else -> BatteryTrafficLight.CRITICAL
        }

        val summaryLabel = when (status) {
            BatteryTrafficLight.SAFE -> "Hin- & Rückweg sicher"
            BatteryTrafficLight.TIGHT -> "Hin- & Rückweg knapp"
            BatteryTrafficLight.CRITICAL -> if (oneWayFeasible) {
                "Nur Hinweg möglich"
            } else {
                "Akku reicht nicht"
            }
        }

        val detailText = when (status) {
            BatteryTrafficLight.SAFE -> String.format(
                Locale.GERMAN,
                "Hin+Rück %.1f km · Restprognose +%.1f km",
                roundTripKm,
                remainingAfterRoundTrip
            )
            BatteryTrafficLight.TIGHT -> String.format(
                Locale.GERMAN,
                "Hin+Rück %.1f km · nur %.1f km Reserve",
                roundTripKm,
                remainingAfterRoundTrip
            )
            BatteryTrafficLight.CRITICAL -> String.format(
                Locale.GERMAN,
                "Hin+Rück %.1f km · es fehlen %.1f km",
                roundTripKm,
                abs(remainingAfterRoundTrip)
            )
        }

        return BatteryRoundTripAssessment(
            status = status,
            oneWayScooterKm = oneWayKm,
            roundTripScooterKm = roundTripKm,
            availableRangeKm = availableKm,
            remainingKmAfterRoundTrip = remainingAfterRoundTrip,
            oneWayFeasible = oneWayFeasible,
            summaryLabel = summaryLabel,
            detailText = detailText
        )
    }
}
