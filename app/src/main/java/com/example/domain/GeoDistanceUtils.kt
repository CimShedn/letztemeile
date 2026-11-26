package com.example.domain

import com.example.model.GeoPoint
import com.example.model.NearbyStationResult
import com.example.model.ScooterProfile
import com.example.model.TransitStation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistanceUtils {

    private const val EARTH_RADIUS_KM = 6371.0
    const val SCOOTER_DETOUR_FACTOR = 1.25

    val MUNICH_CENTER = GeoPoint(48.137154, 11.576124)

    fun haversineKm(from: GeoPoint, to: GeoPoint): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun estimatedScooterKm(from: GeoPoint, to: GeoPoint): Double {
        return haversineKm(from, to) * SCOOTER_DETOUR_FACTOR
    }

    fun estimateScooterMinutes(distanceKm: Double, profile: ScooterProfile): Int {
        if (distanceKm <= 0.05) return 1
        // Effective urban average accounts for intersections & traffic lights (~82% of top speed)
        val effectiveSpeedKmh = max(8.0, profile.maxSpeedKmh * 0.82)
        return max(1, ((distanceKm / effectiveSpeedKmh) * 60.0).roundToInt())
    }

    fun findNearestStations(
        point: GeoPoint,
        stations: List<TransitStation>,
        profile: ScooterProfile,
        limit: Int = 5
    ): List<NearbyStationResult> {
        return stations
            .distinctBy { it.id }
            .map { station ->
                val airKm = haversineKm(point, station.location)
                val scooterKm = airKm * SCOOTER_DETOUR_FACTOR
                val minutes = estimateScooterMinutes(scooterKm, profile)
                NearbyStationResult(
                    station = station,
                    airDistanceKm = airKm,
                    estimatedScooterKm = scooterKm,
                    estimatedScooterMinutes = minutes
                )
            }
            .sortedBy { it.airDistanceKm }
            .take(limit)
    }

    fun parseCoordinateQuery(query: String): GeoPoint? {
        val cleaned = query.trim().replace(";", ",").replace(" ", ",")
        val parts = cleaned.split(",").filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat in -90.0..90.0 && lon in -180.0..180.0) {
            return GeoPoint(lat, lon)
        }
        return null
    }
}
