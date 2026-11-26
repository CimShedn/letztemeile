package com.example.data.remote

import android.content.Context
import com.example.domain.GeoDistanceUtils
import com.example.model.GeoPoint
import com.example.model.ScooterProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ScooterLegRoute(
    val path: List<GeoPoint>,
    val distanceKm: Double,
    val durationMinutes: Int,
    val isOfflineEstimate: Boolean
)

data class SnappedRoadPoint(
    val point: GeoPoint,
    val distanceMeters: Double,
    val roadName: String? = null
)

/**
 * Real street & bike-path scooter routing using keyless OpenStreetMap OSRM Bicycle/Foot APIs
 * with fast in-memory caching. Never draws crow-flies straight lines across buildings.
 */
class ScooterBikeRoutingRepository(
    private val context: Context
) {
    companion object {
        /** Max distance in meters to snap a tapped map pin onto a drivable road / bikeway. */
        const val MAX_SNAP_RADIUS_METERS = 130.0
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Tight-timeout client for interactive snap-to-road lookups so map taps never stall
    private val nearestClient = OkHttpClient.Builder()
        .connectTimeout(2200, TimeUnit.MILLISECONDS)
        .readTimeout(2200, TimeUnit.MILLISECONDS)
        .callTimeout(2600, TimeUnit.MILLISECONDS)
        .build()

    // Keyless OSM street/bike routing servers (FOSSGIS bike primary + OSRM public bike + FOSSGIS foot mirror)
    private val routeUrlTemplates = listOf(
        "https://routing.openstreetmap.de/routed-bike/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
        "https://router.project-osrm.org/route/v1/bike/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
        "https://routing.openstreetmap.de/routed-foot/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson"
    )

    // Matching OSRM nearest endpoints for snap-to-road (bike/scooter drivable network first)
    private val nearestUrlTemplates = listOf(
        "https://routing.openstreetmap.de/routed-bike/nearest/v1/driving/%.6f,%.6f?number=1",
        "https://router.project-osrm.org/nearest/v1/bike/%.6f,%.6f?number=1",
        "https://routing.openstreetmap.de/routed-foot/nearest/v1/driving/%.6f,%.6f?number=1"
    )

    private data class CachedGeometry(
        val path: List<GeoPoint>,
        val distanceKm: Double
    )

    private val routeCache = ConcurrentHashMap<String, CachedGeometry>()
    private val nearestCache = ConcurrentHashMap<String, SnappedRoadPoint>()

    /**
     * Queries the nearest drivable bike/scooter road point via OSRM `/nearest`.
     * Returns `null` silently if offline, on network/timeout errors, or if the closest road
     * exceeds [maxRadiusMeters] (e.g. deep inside parks or fields).
     */
    suspend fun snapToNearestRoad(
        point: GeoPoint,
        maxRadiusMeters: Double = MAX_SNAP_RADIUS_METERS
    ): SnappedRoadPoint? = withContext(Dispatchers.IO) {
        val cacheKey = String.format(Locale.US, "%.5f,%.5f", point.latitude, point.longitude)
        nearestCache[cacheKey]?.let { cached ->
            return@withContext if (cached.distanceMeters <= maxRadiusMeters) cached else null
        }

        if (!NetworkConnectivityHelper.isOnline(context)) {
            return@withContext null
        }

        for (template in nearestUrlTemplates) {
            try {
                val url = String.format(Locale.US, template, point.longitude, point.latitude)
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LetzteMeile-Android/0.4 (E-Scooter Snap-to-Road)")
                    .header("Accept", "application/json")
                    .build()

                nearestClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val parsed = parseOsrmNearestResponse(body, point) ?: return@use

                    if (nearestCache.size > 300) {
                        nearestCache.clear()
                    }
                    nearestCache[cacheKey] = parsed

                    return@withContext if (parsed.distanceMeters <= maxRadiusMeters) parsed else null
                }
            } catch (_: Exception) {
                // Try fallback OSRM mirror or silently keep original point
            }
        }

        null
    }

    private fun parseOsrmNearestResponse(
        rawJson: String,
        originalPoint: GeoPoint
    ): SnappedRoadPoint? {
        return try {
            val root = JSONObject(rawJson)
            if (!root.optString("code").equals("Ok", ignoreCase = true)) return null
            val waypoints = root.optJSONArray("waypoints") ?: return null
            if (waypoints.length() == 0) return null
            val wp0 = waypoints.optJSONObject(0) ?: return null
            val location = wp0.optJSONArray("location") ?: return null
            if (location.length() < 2) return null

            val lon = location.optDouble(0, Double.NaN)
            val lat = location.optDouble(1, Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null

            val snappedPoint = GeoPoint(lat, lon)
            val haversineMeters = GeoDistanceUtils.haversineKm(originalPoint, snappedPoint) * 1000.0
            val roadName = wp0.optString("name", "").trim().takeIf { it.isNotBlank() }

            SnappedRoadPoint(
                point = snappedPoint,
                distanceMeters = haversineMeters,
                roadName = roadName
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun routeScooterLeg(
        from: GeoPoint,
        to: GeoPoint,
        profile: ScooterProfile
    ): ScooterLegRoute = withContext(Dispatchers.IO) {
        val airKm = GeoDistanceUtils.haversineKm(from, to)

        // Very short distance (< 45m): no API call needed, direct path is exact
        if (airKm < 0.045) {
            return@withContext ScooterLegRoute(
                path = listOf(from, to),
                distanceKm = airKm,
                durationMinutes = 1,
                isOfflineEstimate = false
            )
        }

        val cacheKey = String.format(
            Locale.US,
            "%.5f,%.5f->%.5f,%.5f",
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude
        )

        // 1. Instant cache hit (works offline too!)
        routeCache[cacheKey]?.let { cached ->
            return@withContext ScooterLegRoute(
                path = cached.path,
                distanceKm = cached.distanceKm,
                durationMinutes = GeoDistanceUtils.estimateScooterMinutes(cached.distanceKm, profile),
                isOfflineEstimate = false
            )
        }

        // 2. If offline, fall back immediately without waiting for network timeout
        if (!NetworkConnectivityHelper.isOnline(context)) {
            return@withContext buildOfflineFallback(from, to, airKm, profile)
        }

        // 3. Query real OSM Bicycle street/bike-path routing API
        for (template in routeUrlTemplates) {
            try {
                val url = String.format(
                    Locale.US,
                    template,
                    from.longitude,
                    from.latitude,
                    to.longitude,
                    to.latitude
                )
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LetzteMeile-Android/0.4 (E-Scooter Bike Routing)")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val parsed = parseOsrmResponse(body) ?: return@use

                    if (routeCache.size > 300) {
                        routeCache.clear()
                    }
                    routeCache[cacheKey] = parsed

                    return@withContext ScooterLegRoute(
                        path = parsed.path,
                        distanceKm = parsed.distanceKm,
                        durationMinutes = GeoDistanceUtils.estimateScooterMinutes(parsed.distanceKm, profile),
                        isOfflineEstimate = false
                    )
                }
            } catch (_: Exception) {
                // Try fallback OSRM endpoint or drop cleanly to offline fallback
            }
        }

        // 4. Automatic offline fallback (never fails!)
        buildOfflineFallback(from, to, airKm, profile)
    }

    private fun buildOfflineFallback(
        from: GeoPoint,
        to: GeoPoint,
        airKm: Double,
        profile: ScooterProfile
    ): ScooterLegRoute {
        val estimatedKm = airKm * GeoDistanceUtils.SCOOTER_DETOUR_FACTOR
        return ScooterLegRoute(
            // Never return a fake 2-point straight-line Luftlinie across buildings
            path = emptyList(),
            distanceKm = estimatedKm,
            durationMinutes = GeoDistanceUtils.estimateScooterMinutes(estimatedKm, profile),
            isOfflineEstimate = true
        )
    }

    private fun parseOsrmResponse(rawJson: String): CachedGeometry? {
        return try {
            val root = JSONObject(rawJson)
            if (!root.optString("code").equals("Ok", ignoreCase = true)) return null
            val routes = root.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val route0 = routes.optJSONObject(0) ?: return null
            val distanceMeters = route0.optDouble("distance", Double.NaN)
            if (distanceMeters.isNaN() || distanceMeters < 0.0) return null

            val geom = route0.optJSONObject("geometry") ?: return null
            val coords = geom.optJSONArray("coordinates") ?: return null
            if (coords.length() < 2) return null

            val points = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val pair = coords.optJSONArray(i) ?: continue
                if (pair.length() < 2) continue
                val lon = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    points.add(GeoPoint(lat, lon))
                }
            }
            if (points.size < 2) return null

            CachedGeometry(
                path = points,
                distanceKm = distanceMeters / 1000.0
            )
        } catch (_: Exception) {
            null
        }
    }
}
