package com.example.data.remote

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.example.domain.GeoDistanceUtils
import com.example.model.GeoPoint
import com.example.model.RouteTransitChoice
import com.example.model.StationType
import com.example.model.TransitStation
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

internal const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
private const val MAX_HTTP_STREAM_BYTES = 18L * 1024L * 1024L // 18 MB hard stream guard
private const val MAX_WAYS_PER_ROUTE = 2000
private const val MAX_POINTS_PER_WAY = 1200
private const val MAX_STOPS_PER_ROUTE = 250
private const val MIN_POINT_DELTA_DEG = 0.00022 // ~24 m decimation keeps shapes crisp at 1/5 RAM

internal enum class TransitBucket {
    SBAHN,
    BUS,
    REGIO_LONG
}

internal data class CompactWay(
    val wayId: Long,
    /** Flat interleaved [lon0, lat0, lon1, lat1, ...] primitive array. */
    val lonLatFlat: DoubleArray
)

internal data class CompactStop(
    val name: String,
    val lat: Double,
    val lon: Double
)

internal data class ParsedTransitRoute(
    val ref: String,
    val bucket: TransitBucket,
    val colorHex: String,
    val textColorHex: String,
    val ways: List<CompactWay>,
    val stops: List<CompactStop>
)

internal data class StreamRoutesResult(
    val routes: List<ParsedTransitRoute>,
    val hadRuntimeError: Boolean
)

internal data class StreamStationsResult(
    val stations: List<TransitStation>,
    val hadRuntimeError: Boolean
)

internal data class StreamRoutesAndStopsResult(
    val routes: List<ParsedTransitRoute>,
    val busStops: List<TransitStation>,
    val hadRuntimeError: Boolean
)

internal data class ParsedMvvBundle(
    val routes: List<ParsedTransitRoute>,
    val stations: List<TransitStation>,
    val sBahnStations: List<TransitStation>,
    val busStations: List<TransitStation>,
    val regioStations: List<TransitStation>,
    val standaloneBusStops: List<TransitStation>
)

/**
 * Memory-safe, zero-JSONObject streaming codec for Overpass HTTP streams, compact Room caches,
 * and MapLibre GeoJSON generation using Android's native [JsonReader] and [JsonWriter].
 */
internal object OverpassStreamCodec {

    internal val SBAHN_REF_REGEX = Regex("^S\\d+[A-Za-z]?$", RegexOption.IGNORE_CASE)
    private val UBAHN_REF_REGEX = Regex("^U\\s?\\d+.*", RegexOption.IGNORE_CASE)
    private val FIRST_NUMBER_REGEX = Regex("\\d+")
    private val EXPRESS_BUS_REGEX = Regex("^X\\s?\\d+.*", RegexOption.IGNORE_CASE)
    private val REGULAR_MAIN_BUS_REGEX = Regex("^\\d{1,3}$")
    private val NIGHT_BUS_REGEX = Regex("^N\\s?\\d+.*", RegexOption.IGNORE_CASE)
    private val REINFORCER_BUS_REGEX = Regex("^\\d+.*V$", RegexOption.IGNORE_CASE)
    private val SPECIAL_4DIGIT_BUS_REGEX = Regex("^\\d{4,}.*")

    private val NATURAL_LINE_REF_COMPARATOR = Comparator<ParsedTransitRoute> { a, b ->
        compareLineRefsNaturally(a.ref, b.ref)
    }

    /**
     * Prioritizes bus lines on bundled corridors by rider importance:
     *  0) Express buses (X660, X200, ...)
     *  1) Regular 2- and 3-digit main buses (50, 511, 630, ...) sorted numerically
     *  2) Night buses (N...), reinforcers (...V), 4-digit on-demand/special buses (6302, 6304), etc.
     */
    private val BUS_PRIORITY_COMPARATOR = Comparator<ParsedTransitRoute> { a, b ->
        val refA = a.ref.trim()
        val refB = b.ref.trim()
        val tierA = busRefPriorityTier(refA)
        val tierB = busRefPriorityTier(refB)
        if (tierA != tierB) {
            tierA.compareTo(tierB)
        } else {
            compareLineRefsNaturally(refA, refB)
        }
    }

    private fun busRefPriorityTier(ref: String): Int = when {
        EXPRESS_BUS_REGEX.matches(ref) -> 0
        REGULAR_MAIN_BUS_REGEX.matches(ref) -> 1
        NIGHT_BUS_REGEX.matches(ref) -> 2
        REINFORCER_BUS_REGEX.matches(ref) -> 3
        SPECIAL_4DIGIT_BUS_REGEX.matches(ref) -> 4
        else -> 5
    }

    private fun compareLineRefsNaturally(rawA: String, rawB: String): Int {
        val refA = rawA.trim()
        val refB = rawB.trim()
        val numA = FIRST_NUMBER_REGEX.find(refA)?.value?.toIntOrNull()
        val numB = FIRST_NUMBER_REGEX.find(refB)?.value?.toIntOrNull()
        val prefixA = refA.takeWhile { !it.isDigit() }.uppercase(Locale.GERMAN)
        val prefixB = refB.takeWhile { !it.isDigit() }.uppercase(Locale.GERMAN)
        val prefixCmp = prefixA.compareTo(prefixB)
        return when {
            prefixCmp != 0 -> prefixCmp
            numA != null && numB != null && numA != numB -> numA.compareTo(numB)
            else -> refA.compareTo(refB, ignoreCase = true)
        }
    }

    internal fun isStrictSBahnRef(ref: String): Boolean =
        SBAHN_REF_REGEX.matches(ref.trim().replace(" ", ""))

    private class BoundedInputStream(
        source: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(source) {
        private var bytesRead: Long = 0L

        override fun read(): Int {
            if (bytesRead >= maxBytes) throw IOException("Overpass response exceeded ${maxBytes / (1024 * 1024)} MB limit")
            val b = super.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val remaining = maxBytes - bytesRead
            if (remaining <= 0L) throw IOException("Overpass response exceeded ${maxBytes / (1024 * 1024)} MB limit")
            val toRead = minOf(len.toLong(), remaining).toInt()
            val count = super.read(b, off, toRead)
            if (count > 0) bytesRead += count
            return count
        }
    }

    /**
     * Streams an Overpass HTTP [InputStream] directly via [JsonReader] without ever materializing
     * the response body as a String or JSONObject tree in RAM.
     */
    fun streamOverpassRoutes(
        inputStream: InputStream,
        dedupKeys: MutableSet<String> = HashSet(),
        maxRoutes: Int = 180
    ): StreamRoutesResult {
        val bounded = BufferedInputStream(BoundedInputStream(inputStream, MAX_HTTP_STREAM_BYTES), 16 * 1024)
        JsonReader(InputStreamReader(bounded, Charsets.UTF_8)).use { reader ->
            return readRoutesFromReader(reader, dedupKeys, maxRoutes)
        }
    }

    /**
     * Streams BOTH bus route relations AND standalone bus stop nodes from ONE combined Overpass
     * HTTP [InputStream] in a single streaming pass (1 HTTP round-trip, zero JSONObject).
     */
    fun streamOverpassBusRoutesAndStops(
        inputStream: InputStream,
        dedupKeys: MutableSet<String> = HashSet(),
        maxRoutes: Int = 110,
        maxBusStops: Int = 450
    ): StreamRoutesAndStopsResult {
        val bounded = BufferedInputStream(BoundedInputStream(inputStream, MAX_HTTP_STREAM_BYTES), 16 * 1024)
        val routes = mutableListOf<ParsedTransitRoute>()
        val rawStops = mutableListOf<TransitStation>()
        var hadRuntimeError = false

        JsonReader(InputStreamReader(bounded, Charsets.UTF_8)).use { reader ->
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return StreamRoutesAndStopsResult(emptyList(), emptyList(), hadRuntimeError = true)
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "remark" -> {
                        if (isOverpassErrorRemark(nextStringOrEmpty(reader).lowercase(Locale.US))) {
                            hadRuntimeError = true
                        }
                    }
                    "elements" -> {
                        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            var idx = 0L
                            while (reader.hasNext()) {
                                idx++
                                readCombinedBusElement(
                                    reader = reader,
                                    dedupKeys = dedupKeys,
                                    routesOut = routes,
                                    stopsOut = rawStops,
                                    maxRoutes = maxRoutes,
                                    maxBusStops = maxBusStops,
                                    fallbackIndex = idx
                                )
                            }
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        return StreamRoutesAndStopsResult(
            routes = routes,
            busStops = deduplicateStations(rawStops),
            hadRuntimeError = hadRuntimeError
        )
    }

    /**
     * Streams Overpass station nodes/ways directly from an HTTP [InputStream] via [JsonReader].
     */
    fun streamOverpassStations(
        inputStream: InputStream,
        mode: RouteTransitChoice,
        maxStations: Int = 1500
    ): StreamStationsResult {
        val bounded = BufferedInputStream(BoundedInputStream(inputStream, MAX_HTTP_STREAM_BYTES), 16 * 1024)
        JsonReader(InputStreamReader(bounded, Charsets.UTF_8)).use { reader ->
            return readStationsFromReader(reader, mode, maxStations)
        }
    }

    /**
     * Streams a local `mvv_transit.json` bundle file directly from disk via [FileInputStream] +
     * [JsonReader] without ever loading the file as a String or JSONObject into heap RAM.
     *
     * Supports `"lm_v2_routes"` (and optional `"lm_v2_stops"` if present) and extracts a complete,
     * deduplicated [TransitStation] index for S-Bahn/U-Bahn, Bus, and Regio.
     */
    fun parseLocalBundleFile(
        file: File,
        maxRoutes: Int = 20000,
        maxStandaloneStops: Int = 25000
    ): ParsedMvvBundle? {
        if (!file.exists() || file.length() < 24L) return null
        return try {
            val routes = mutableListOf<ParsedTransitRoute>()
            val explicitStops = mutableListOf<TransitStation>()

            BufferedInputStream(FileInputStream(file), 32 * 1024).use { bis ->
                JsonReader(InputStreamReader(bis, Charsets.UTF_8)).use { reader ->
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) return null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "lm_v2_routes" -> {
                                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        if (routes.size >= maxRoutes) {
                                            reader.skipValue()
                                            continue
                                        }
                                        readCompactRouteObject(reader)?.let { routes.add(it) }
                                    }
                                    reader.endArray()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            "lm_v2_stops" -> {
                                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        if (explicitStops.size >= maxStandaloneStops) {
                                            reader.skipValue()
                                            continue
                                        }
                                        readCompactStation(reader)?.let { explicitStops.add(it) }
                                    }
                                    reader.endArray()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            if (routes.isEmpty() && explicitStops.isEmpty()) return null

            val rawSBahn = ArrayList<TransitStation>(routes.size * 2)
            val rawBus = ArrayList<TransitStation>(routes.size * 8 + explicitStops.size)
            val rawRegio = ArrayList<TransitStation>(routes.size * 2)
            val rawOther = ArrayList<TransitStation>(routes.size * 2)

            for (route in routes) {
                val trimmedRef = route.ref.trim()
                val stationType = when (route.bucket) {
                    TransitBucket.SBAHN -> when {
                        isStrictSBahnRef(trimmedRef) -> StationType.SBAHN
                        UBAHN_REF_REGEX.matches(trimmedRef) -> StationType.UBAHN
                        else -> StationType.TRAM
                    }
                    TransitBucket.BUS -> StationType.BUS
                    TransitBucket.REGIO_LONG -> StationType.REGIO
                }
                val lineLabel = if (stationType == StationType.BUS &&
                    !trimmedRef.startsWith("Bus", ignoreCase = true)
                ) {
                    "Bus $trimmedRef"
                } else {
                    trimmedRef
                }
                val targetList = when {
                    route.bucket == TransitBucket.SBAHN && stationType == StationType.SBAHN -> rawSBahn
                    route.bucket == TransitBucket.BUS -> rawBus
                    route.bucket == TransitBucket.REGIO_LONG -> rawRegio
                    else -> rawOther
                }
                for (stop in route.stops) {
                    targetList.add(
                        TransitStation(
                            id = "mvv_${stationType.name}_${stop.name.lowercase(Locale.GERMAN).hashCode()}_${round5(stop.lat)}_${round5(stop.lon)}",
                            name = stop.name,
                            location = GeoPoint(stop.lat, stop.lon),
                            type = stationType,
                            lines = listOf(lineLabel)
                        )
                    )
                }
            }

            // Explicit standalone stops only enrich their matching strict bucket (never pollute S-Bahn!)
            for (stop in explicitStops) {
                when (stop.type) {
                    StationType.BUS -> rawBus.add(stop)
                    StationType.REGIO, StationType.LONG_DISTANCE -> rawRegio.add(stop)
                    StationType.SBAHN -> {
                        if (stop.lines.any { isStrictSBahnRef(it) }) {
                            rawSBahn.add(stop)
                        } else {
                            rawOther.add(stop)
                        }
                    }
                    StationType.UBAHN, StationType.TRAM -> rawOther.add(stop)
                }
            }

            val sBahnStations = deduplicateStations(rawSBahn)
            val busStations = deduplicateStations(rawBus)
            val regioStations = deduplicateStations(rawRegio)
            val otherStations = deduplicateStations(rawOther)
            val allStations = sBahnStations + busStations + regioStations + otherStations
            val standaloneBusStops = if (explicitStops.isNotEmpty()) {
                deduplicateStations(explicitStops.filter { it.type == StationType.BUS })
            } else {
                emptyList()
            }

            ParsedMvvBundle(
                routes = routes,
                stations = allStations,
                sBahnStations = sBahnStations,
                busStations = busStations,
                regioStations = regioStations,
                standaloneBusStops = standaloneBusStops
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Streams either compact Room-cached routes (`lm_v2_routes`) OR legacy Overpass (`elements`)
     * payloads using [JsonReader] (never [org.json.JSONObject]) and returns the parsed route list.
     */
    fun parseCachedRoutesList(
        payloadJson: String,
        maxRoutes: Int = 1200
    ): List<ParsedTransitRoute> {
        if (payloadJson.length < 24) return emptyList()
        return try {
            JsonReader(StringReader(payloadJson)).use { reader ->
                readRoutesFromReader(reader, HashSet(), maxRoutes = maxRoutes).routes
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Streams either compact Room-cached routes (`lm_v2_routes`) OR legacy Overpass (`elements`)
     * payloads using [JsonReader] (never [org.json.JSONObject]).
     */
    fun parseCachedRoutesToOverlay(
        payloadJson: String,
        focusCenter: GeoPoint? = null,
        extraBusStops: List<TransitStation> = emptyList()
    ): DynamicTransitOverlay? {
        val routes = parseCachedRoutesList(payloadJson, maxRoutes = 1200)
        if (routes.isEmpty() && extraBusStops.isEmpty()) return null
        return buildDynamicOverlay(routes, focusCenter, extraBusStops)
    }

    /**
     * Returns true if at least one BUS route in [routes] passes within [maxDistKm] of [center].
     * Used to detect stale/incomplete regional caches (e.g. Freising missing buses in a Munich pack).
     */
    fun hasBusRouteNear(
        routes: List<ParsedTransitRoute>,
        center: GeoPoint,
        maxDistKm: Double = 6.5
    ): Boolean {
        val maxDegApprox = maxDistKm / 111.0
        val maxDegSq = maxDegApprox * maxDegApprox
        for (r in routes) {
            if (r.bucket != TransitBucket.BUS) continue
            if (approxRouteDistanceSq(r, center) <= maxDegSq) {
                return true
            }
        }
        return false
    }

    /**
     * Streams either compact Room-cached stations (`lm_v2_stops`) OR legacy Overpass (`elements`)
     * payloads using [JsonReader] (never [org.json.JSONObject]).
     */
    fun parseCachedStations(
        payloadJson: String,
        mode: RouteTransitChoice
    ): List<TransitStation>? {
        if (payloadJson.length < 24) return null
        return try {
            JsonReader(StringReader(payloadJson)).use { reader ->
                val result = readStationsFromReader(reader, mode, maxStations = 2000)
                result.stations
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fast streaming probe verifying that a cached Room payload contains at least one usable
     * route or station entry without loading or parsing the whole document.
     */
    fun hasUsableCachedData(payloadJson: String): Boolean {
        if (payloadJson.length < 28) return false
        return try {
            JsonReader(StringReader(payloadJson)).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_OBJECT) return false
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "lm_v2_routes", "lm_v2_stops", "elements" -> {
                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                reader.beginArray()
                                val hasItem = reader.hasNext()
                                return hasItem
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Serializes deduplicated & simplified routes into an ultra-compact Room cache payload
     * (~50x smaller than raw Overpass JSON).
     */
    fun serializeCompactRoutes(routes: List<ParsedTransitRoute>): String {
        val sw = StringWriter(minOf(routes.size * 512, 256 * 1024).coerceAtLeast(1024))
        JsonWriter(sw).use { w ->
            w.beginObject()
            w.name("lm_v2_routes").beginArray()
            for (r in routes) {
                w.beginObject()
                w.name("r").value(r.ref)
                w.name("b").value(r.bucket.ordinal)
                w.name("c").value(r.colorHex)
                w.name("tc").value(r.textColorHex)
                w.name("w").beginArray()
                for (way in r.ways) {
                    w.beginObject()
                    w.name("i").value(way.wayId)
                    w.name("p").beginArray()
                    val pts = way.lonLatFlat
                    for (k in pts.indices) {
                        w.value(round5(pts[k]))
                    }
                    w.endArray()
                    w.endObject()
                }
                w.endArray()
                if (r.stops.isNotEmpty()) {
                    w.name("s").beginArray()
                    for (st in r.stops) {
                        w.beginObject()
                        w.name("n").value(st.name)
                        w.name("a").value(round5(st.lat))
                        w.name("o").value(round5(st.lon))
                        w.endObject()
                    }
                    w.endArray()
                }
                w.endObject()
            }
            w.endArray()
            w.endObject()
        }
        return sw.toString()
    }

    /**
     * Serializes deduplicated stations into a compact Room cache payload.
     */
    fun serializeCompactStations(stations: List<TransitStation>): String {
        val sw = StringWriter(minOf(stations.size * 128, 128 * 1024).coerceAtLeast(512))
        JsonWriter(sw).use { w ->
            w.beginObject()
            w.name("lm_v2_stops").beginArray()
            for (st in stations) {
                w.beginObject()
                w.name("id").value(st.id)
                w.name("n").value(st.name)
                w.name("a").value(round5(st.location.latitude))
                w.name("o").value(round5(st.location.longitude))
                w.name("t").value(st.type.ordinal)
                if (st.lines.isNotEmpty()) {
                    w.name("l").beginArray()
                    for (line in st.lines) w.value(line)
                    w.endArray()
                }
                w.endObject()
            }
            w.endArray()
            w.endObject()
        }
        return sw.toString()
    }

    /**
     * Builds the MapLibre [DynamicTransitOverlay] GeoJSON layers directly using [JsonWriter]
     * (zero JSONObject / JSONArray heap trees).
     */
    fun buildDynamicOverlay(
        allRoutes: List<ParsedTransitRoute>,
        focusCenter: GeoPoint? = null,
        extraBusStops: List<TransitStation> = emptyList()
    ): DynamicTransitOverlay {
        // Strictly split bucket 0 into:
        //  - Pure S-Bahn (^S\d+[A-Za-z]?$, e.g. S1, S2, S20)
        //  - U-Bahn / Tram (all remaining bucket 0 routes, e.g. U1-U8, Tram 16/19/23)
        // No route truncation so branched lines (e.g. S1 Freising + Flughafen) render completely.
        val bucketZeroRoutes = allRoutes.filter { it.bucket == TransitBucket.SBAHN }
        val pureSBahnRoutes = bucketZeroRoutes.filter { isStrictSBahnRef(it.ref) }
        val ubahnTramRoutes = bucketZeroRoutes.filterNot { isStrictSBahnRef(it.ref) }
        val busRoutesRaw = allRoutes.filter { it.bucket == TransitBucket.BUS }
        val busRoutes = if (focusCenter != null && busRoutesRaw.size > 90) {
            busRoutesRaw
                .sortedBy { route -> approxRouteDistanceSq(route, focusCenter) }
                .take(90)
        } else {
            busRoutesRaw.take(90)
        }
        val regioRoutes = allRoutes.filter { it.bucket == TransitBucket.REGIO_LONG }

        val focusedExtraBusStops = if (focusCenter != null && extraBusStops.size > 220) {
            extraBusStops
                .filter { it.type == StationType.BUS }
                .sortedBy { GeoDistanceUtils.haversineKm(it.location, focusCenter) }
                .take(220)
        } else {
            extraBusStops.filter { it.type == StationType.BUS }.take(220)
        }

        val sBahnPack = buildBucketGeoJson(pureSBahnRoutes, StationType.SBAHN)
        val ubahnTramPack = buildBucketGeoJson(ubahnTramRoutes, StationType.UBAHN)
        val busPack = buildBucketGeoJson(busRoutes, StationType.BUS, extraStops = focusedExtraBusStops)
        val regioPack = buildBucketGeoJson(regioRoutes, StationType.REGIO)

        return DynamicTransitOverlay(
            sBahnLinesGeoJson = sBahnPack.linesJson,
            sBahnLineLabelsGeoJson = sBahnPack.labelsJson,
            sBahnStationsGeoJson = sBahnPack.stationsJson,
            sBahnTerminalsGeoJson = sBahnPack.terminalsJson,
            ubahnTramLinesGeoJson = ubahnTramPack.linesJson,
            ubahnTramLineLabelsGeoJson = ubahnTramPack.labelsJson,
            ubahnTramStationsGeoJson = ubahnTramPack.stationsJson,
            ubahnTramTerminalsGeoJson = ubahnTramPack.terminalsJson,
            busLinesGeoJson = busPack.linesJson,
            busLineLabelsGeoJson = busPack.labelsJson,
            busStationsGeoJson = busPack.stationsJson,
            busTerminalsGeoJson = busPack.terminalsJson,
            regioLinesGeoJson = regioPack.linesJson,
            regioLineLabelsGeoJson = regioPack.labelsJson,
            regioStationsGeoJson = regioPack.stationsJson,
            regioTerminalsGeoJson = regioPack.terminalsJson,
            discoveredStations = (sBahnPack.stations + ubahnTramPack.stations + busPack.stations + regioPack.stations)
                .distinctBy { "${it.type}_${it.name.lowercase(Locale.GERMAN)}" }
        )
    }

    // ---- Internal Streaming Readers ----

    private fun readRoutesFromReader(
        reader: JsonReader,
        dedupKeys: MutableSet<String>,
        maxRoutes: Int
    ): StreamRoutesResult {
        val routes = mutableListOf<ParsedTransitRoute>()
        var hadRuntimeError = false

        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            return StreamRoutesResult(emptyList(), hadRuntimeError = true)
        }
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "remark" -> {
                    val remark = nextStringOrEmpty(reader).lowercase(Locale.US)
                    if (isOverpassErrorRemark(remark)) {
                        hadRuntimeError = true
                    }
                }
                "lm_v2_routes" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val parsed = readCompactRouteObject(reader)
                            if (parsed != null && routes.size < maxRoutes) {
                                val key = buildRouteDedupKey(parsed.bucket, parsed.ref, parsed.ways)
                                if (dedupKeys.add(key)) {
                                    routes.add(parsed)
                                }
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "elements" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (routes.size >= maxRoutes) {
                                reader.skipValue()
                                continue
                            }
                            val parsed = readOverpassRelationElement(reader, dedupKeys)
                            if (parsed != null) {
                                routes.add(parsed)
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return StreamRoutesResult(routes = routes, hadRuntimeError = hadRuntimeError)
    }

    private fun readOverpassRelationElement(
        reader: JsonReader,
        dedupKeys: MutableSet<String>
    ): ParsedTransitRoute? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var isRelation = false
        var routeType = ""
        var refRaw = ""
        var nameRaw = ""
        var service = ""
        var osmColour = ""
        val ways = mutableListOf<CompactWay>()
        val stops = mutableListOf<CompactStop>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> {
                    isRelation = nextStringOrEmpty(reader) == "relation"
                }
                "tags" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "route" -> routeType = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "ref" -> refRaw = nextStringOrEmpty(reader).trim()
                                "name" -> nameRaw = nextStringOrEmpty(reader).trim()
                                "service" -> service = nextStringOrEmpty(reader)
                                "colour" -> {
                                    val c = nextStringOrEmpty(reader).trim()
                                    if (c.isNotEmpty()) osmColour = c
                                }
                                "route_color" -> {
                                    val c = nextStringOrEmpty(reader).trim()
                                    if (osmColour.isEmpty() && c.isNotEmpty()) osmColour = c
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                "members" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readOverpassMember(reader, ways, stops)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!isRelation || ways.isEmpty()) return null
        val ref = refRaw.ifBlank {
            Regex("\\b[A-Za-z]{0,3}\\d+[A-Za-z]?\\b").find(nameRaw)?.value
                ?: nameRaw.split(" ", ":", "-").firstOrNull { it.length in 1..6 }
                ?: return null
        }
        val bucket = classifyBucket(routeType, ref, service)
        val dedupKey = buildRouteDedupKey(bucket, ref, ways)
        if (!dedupKeys.add(dedupKey)) return null

        val colorHex = normalizeOsmColor(osmColour, bucket, ref)
        val textColorHex = contrastingTextHex(colorHex)
        return ParsedTransitRoute(
            ref = ref,
            bucket = bucket,
            colorHex = colorHex,
            textColorHex = textColorHex,
            ways = ways,
            stops = stops
        )
    }

    private fun readCombinedBusElement(
        reader: JsonReader,
        dedupKeys: MutableSet<String>,
        routesOut: MutableList<ParsedTransitRoute>,
        stopsOut: MutableList<TransitStation>,
        maxRoutes: Int,
        maxBusStops: Int,
        fallbackIndex: Long
    ) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }

        var elemType = ""
        var id = fallbackIndex
        var lat = Double.NaN
        var lon = Double.NaN
        var routeType = ""
        var refRaw = ""
        var nameRaw = ""
        var service = ""
        var osmColour = ""
        var highwayTag = ""
        var busTag = ""
        var routeRef = ""
        var linesTag = ""
        var lineTag = ""
        val ways = mutableListOf<CompactWay>()
        val stops = mutableListOf<CompactStop>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> elemType = nextStringOrEmpty(reader)
                "id" -> id = nextLongOrZero(reader)
                "lat" -> lat = nextDoubleOrNaN(reader)
                "lon" -> lon = nextDoubleOrNaN(reader)
                "tags" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "route" -> routeType = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "ref" -> refRaw = nextStringOrEmpty(reader).trim()
                                "name" -> nameRaw = nextStringOrEmpty(reader).trim()
                                "service" -> service = nextStringOrEmpty(reader)
                                "highway" -> highwayTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "bus" -> busTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "route_ref" -> routeRef = nextStringOrEmpty(reader)
                                "lines" -> linesTag = nextStringOrEmpty(reader)
                                "line" -> lineTag = nextStringOrEmpty(reader)
                                "colour" -> {
                                    val c = nextStringOrEmpty(reader).trim()
                                    if (c.isNotEmpty()) osmColour = c
                                }
                                "route_color" -> {
                                    val c = nextStringOrEmpty(reader).trim()
                                    if (osmColour.isEmpty() && c.isNotEmpty()) osmColour = c
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                "members" -> {
                    if (routesOut.size < maxRoutes && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readOverpassMember(reader, ways, stops)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (elemType == "relation" && routesOut.size < maxRoutes && ways.isNotEmpty()) {
            val ref = refRaw.ifBlank {
                Regex("\\b[A-Za-z]{0,3}\\d+[A-Za-z]?\\b").find(nameRaw)?.value
                    ?: nameRaw.split(" ", ":", "-").firstOrNull { it.length in 1..6 }
                    ?: return
            }
            val bucket = classifyBucket(routeType, ref, service)
            val dedupKey = buildRouteDedupKey(bucket, ref, ways)
            if (dedupKeys.add(dedupKey)) {
                val colorHex = normalizeOsmColor(osmColour, bucket, ref)
                val textColorHex = contrastingTextHex(colorHex)
                routesOut.add(
                    ParsedTransitRoute(
                        ref = ref,
                        bucket = bucket,
                        colorHex = colorHex,
                        textColorHex = textColorHex,
                        ways = ways,
                        stops = stops
                    )
                )
            }
        } else if (elemType == "node" && stopsOut.size < maxBusStops &&
            nameRaw.isNotBlank() && !lat.isNaN() && !lon.isNaN() &&
            (highwayTag == "bus_stop" || busTag == "yes")
        ) {
            val extractedLines = extractLinesFromTagStrings(routeRef, linesTag, lineTag, RouteTransitChoice.BUS)
            stopsOut.add(
                TransitStation(
                    id = "osm_BUS_$id",
                    name = nameRaw,
                    location = GeoPoint(lat, lon),
                    type = StationType.BUS,
                    lines = extractedLines
                )
            )
        }
    }

    private fun readOverpassMember(
        reader: JsonReader,
        waysOut: MutableList<CompactWay>,
        stopsOut: MutableList<CompactStop>
    ) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }

        var memberType = ""
        var role = ""
        var refId = 0L
        var lat = Double.NaN
        var lon = Double.NaN
        var stopName = ""
        var geomFlat: DoubleArray? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> memberType = nextStringOrEmpty(reader)
                "role" -> role = nextStringOrEmpty(reader)
                "ref" -> refId = nextLongOrZero(reader)
                "lat" -> lat = nextDoubleOrNaN(reader)
                "lon" -> lon = nextDoubleOrNaN(reader)
                "name" -> stopName = nextStringOrEmpty(reader).trim()
                "geometry" -> {
                    if (waysOut.size < MAX_WAYS_PER_ROUTE && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        geomFlat = readDecimatedGeometryArray(reader)
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (memberType == "way" && !role.contains("platform") && geomFlat != null && geomFlat.size >= 4) {
            if (waysOut.size < MAX_WAYS_PER_ROUTE) {
                waysOut.add(CompactWay(wayId = refId, lonLatFlat = geomFlat))
            }
        } else if (memberType == "node" && (role.contains("stop") || role.contains("platform"))) {
            if (!lat.isNaN() && !lon.isNaN() && stopName.isNotEmpty() && stopsOut.size < MAX_STOPS_PER_ROUTE) {
                stopsOut.add(CompactStop(name = stopName, lat = lat, lon = lon))
            }
        }
    }

    /**
     * Streams a `geometry: [{"lat":..., "lon":...}, ...]` array directly into a primitive flat
     * `[lon0, lat0, lon1, lat1, ...]` DoubleArray with ~25 m point decimation to keep heap minimal.
     */
    private fun readDecimatedGeometryArray(reader: JsonReader): DoubleArray? {
        var buf = DoubleArray(32)
        var size = 0
        var lastKeptLon = Double.NaN
        var lastKeptLat = Double.NaN
        var pendingLon = Double.NaN
        var pendingLat = Double.NaN
        var hasPending = false

        fun appendPoint(lon: Double, lat: Double) {
            if (size + 2 > buf.size) {
                buf = buf.copyOf(buf.size * 2)
            }
            buf[size++] = lon
            buf[size++] = lat
            lastKeptLon = lon
            lastKeptLat = lat
        }

        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            var ptLat = Double.NaN
            var ptLon = Double.NaN
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "lat" -> ptLat = nextDoubleOrNaN(reader)
                    "lon" -> ptLon = nextDoubleOrNaN(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (ptLat.isNaN() || ptLon.isNaN()) continue
            if (size == 0) {
                appendPoint(ptLon, ptLat)
                hasPending = false
            } else {
                val delta = abs(ptLat - lastKeptLat) + abs(ptLon - lastKeptLon)
                if (delta >= MIN_POINT_DELTA_DEG && size < MAX_POINTS_PER_WAY * 2) {
                    appendPoint(ptLon, ptLat)
                    hasPending = false
                } else {
                    pendingLon = ptLon
                    pendingLat = ptLat
                    hasPending = true
                }
            }
        }
        reader.endArray()

        // Always keep the exact final vertex of the way so adjacent ways connect seamlessly
        if (hasPending && !pendingLon.isNaN() && !pendingLat.isNaN()) {
            appendPoint(pendingLon, pendingLat)
        }
        return if (size >= 4) buf.copyOf(size) else null
    }

    private fun readCompactRouteObject(reader: JsonReader): ParsedTransitRoute? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var ref = ""
        var bucketIdx = 0
        var colorHex = "#0065BD"
        var textColorHex = "#FFFFFF"
        val ways = mutableListOf<CompactWay>()
        val stops = mutableListOf<CompactStop>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "r" -> ref = nextStringOrEmpty(reader)
                "b" -> bucketIdx = nextIntOrZero(reader)
                "c" -> colorHex = nextStringOrEmpty(reader)
                "tc" -> textColorHex = nextStringOrEmpty(reader)
                "w" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readCompactWay(reader)?.let { ways.add(it) }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "s" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readCompactStop(reader)?.let { stops.add(it) }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (ref.isBlank() || ways.isEmpty()) return null
        val bucket = TransitBucket.entries.getOrNull(bucketIdx) ?: TransitBucket.SBAHN
        return ParsedTransitRoute(
            ref = ref,
            bucket = bucket,
            colorHex = colorHex,
            textColorHex = textColorHex,
            ways = ways,
            stops = stops
        )
    }

    private fun readCompactWay(reader: JsonReader): CompactWay? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var wayId = 0L
        var buf = DoubleArray(16)
        var size = 0

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "i" -> wayId = nextLongOrZero(reader)
                "p" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val v = nextDoubleOrNaN(reader)
                            if (!v.isNaN()) {
                                if (size == buf.size) buf = buf.copyOf(buf.size * 2)
                                buf[size++] = v
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (size >= 4) CompactWay(wayId, buf.copyOf(size)) else null
    }

    private fun readCompactStop(reader: JsonReader): CompactStop? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var name = ""
        var lat = Double.NaN
        var lon = Double.NaN
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "n" -> name = nextStringOrEmpty(reader)
                "a" -> lat = nextDoubleOrNaN(reader)
                "o" -> lon = nextDoubleOrNaN(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (name.isNotBlank() && !lat.isNaN() && !lon.isNaN()) {
            CompactStop(name, lat, lon)
        } else {
            null
        }
    }

    private fun readStationsFromReader(
        reader: JsonReader,
        mode: RouteTransitChoice,
        maxStations: Int
    ): StreamStationsResult {
        val rawStations = mutableListOf<TransitStation>()
        var hadRuntimeError = false

        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            return StreamStationsResult(emptyList(), hadRuntimeError = true)
        }
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "remark" -> {
                    val remark = nextStringOrEmpty(reader).lowercase(Locale.US)
                    if (isOverpassErrorRemark(remark)) hadRuntimeError = true
                }
                "lm_v2_stops" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val st = readCompactStation(reader)
                            if (st != null && mode.matchesStation(st) && rawStations.size < maxStations) {
                                rawStations.add(st)
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "elements" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        var idx = 0L
                        while (reader.hasNext()) {
                            idx++
                            if (rawStations.size >= maxStations) {
                                reader.skipValue()
                                continue
                            }
                            readOverpassStationElement(reader, mode, idx)?.let { rawStations.add(it) }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return StreamStationsResult(
            stations = deduplicateStations(rawStations),
            hadRuntimeError = hadRuntimeError
        )
    }

    private fun readCompactStation(reader: JsonReader): TransitStation? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var id = ""
        var name = ""
        var lat = Double.NaN
        var lon = Double.NaN
        var typeOrd = 0
        val lines = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextStringOrEmpty(reader)
                "n" -> name = nextStringOrEmpty(reader)
                "a" -> lat = nextDoubleOrNaN(reader)
                "o" -> lon = nextDoubleOrNaN(reader)
                "t" -> typeOrd = nextIntOrZero(reader)
                "l" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val l = nextStringOrEmpty(reader)
                            if (l.isNotBlank()) lines.add(l)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (name.isBlank() || lat.isNaN() || lon.isNaN()) return null
        val type = StationType.entries.getOrNull(typeOrd) ?: StationType.SBAHN
        return TransitStation(
            id = id.ifBlank { "st_${name.hashCode()}" },
            name = name,
            location = GeoPoint(lat, lon),
            type = type,
            lines = lines
        )
    }

    private fun readOverpassStationElement(
        reader: JsonReader,
        mode: RouteTransitChoice,
        fallbackIndex: Long
    ): TransitStation? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var id = fallbackIndex
        var lat = Double.NaN
        var lon = Double.NaN
        var name = ""
        var railway = ""
        var usage = ""
        var stationTag = ""
        var subwayTag = ""
        var lightRailTag = ""
        var trainTag = ""
        var highwayTag = ""
        var busTag = ""
        var routeRef = ""
        var linesTag = ""
        var lineTag = ""
        var isDisused = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextLongOrZero(reader)
                "lat" -> lat = nextDoubleOrNaN(reader)
                "lon" -> lon = nextDoubleOrNaN(reader)
                "center" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "lat" -> if (lat.isNaN()) lat = nextDoubleOrNaN(reader) else reader.skipValue()
                                "lon" -> if (lon.isNaN()) lon = nextDoubleOrNaN(reader) else reader.skipValue()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                "tags" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = nextStringOrEmpty(reader).trim()
                                "railway" -> railway = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "usage" -> usage = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "station" -> stationTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "subway" -> subwayTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "light_rail" -> lightRailTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "train" -> trainTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "highway" -> highwayTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "bus" -> busTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "route_ref" -> routeRef = nextStringOrEmpty(reader)
                                "lines" -> linesTag = nextStringOrEmpty(reader)
                                "line" -> lineTag = nextStringOrEmpty(reader)
                                "disused:railway", "abandoned:railway", "construction:railway" -> {
                                    isDisused = true
                                    reader.skipValue()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (name.isBlank() || lat.isNaN() || lon.isNaN()) return null
        if (isDisused || usage == "freight" || railway == "yard" || railway == "freight") return null

        val isSubway = stationTag == "subway" || subwayTag == "yes"
        val isLightRail = stationTag == "light_rail" || lightRailTag == "yes"
        val isTrain = trainTag == "yes" || railway == "station" || railway == "halt"
        val isBus = highwayTag == "bus_stop" || busTag == "yes"

        val stationType = when (mode) {
            RouteTransitChoice.SBAHN -> {
                if (isBus && !isTrain && !isLightRail) return null
                if (isSubway && !isTrain && !isLightRail) return null
                if (!isTrain && !isLightRail) return null
                StationType.SBAHN
            }
            RouteTransitChoice.BUS -> {
                if (!isBus) return null
                StationType.BUS
            }
            RouteTransitChoice.REGIO_DB -> {
                if (isSubway && trainTag != "yes") return null
                if (railway != "station" && railway != "halt") return null
                StationType.REGIO
            }
        }

        val extractedLines = extractLinesFromTagStrings(routeRef, linesTag, lineTag, mode)
        return TransitStation(
            id = "osm_${mode.name}_$id",
            name = name,
            location = GeoPoint(lat, lon),
            type = stationType,
            lines = extractedLines
        )
    }

    /**
     * Streams ALL station types (S-Bahn, Bus, Regio) from one Overpass stops stream in a single pass
     * for Offline Region downloads.
     */
    fun streamOverpassAllStations(
        inputStream: InputStream,
        maxStations: Int = 2400
    ): StreamStationsResult {
        val bounded = BufferedInputStream(BoundedInputStream(inputStream, MAX_HTTP_STREAM_BYTES), 16 * 1024)
        val raw = mutableListOf<TransitStation>()
        var hadRuntimeError = false

        JsonReader(InputStreamReader(bounded, Charsets.UTF_8)).use { reader ->
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return StreamStationsResult(emptyList(), hadRuntimeError = true)
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "remark" -> {
                        if (isOverpassErrorRemark(nextStringOrEmpty(reader).lowercase(Locale.US))) {
                            hadRuntimeError = true
                        }
                    }
                    "elements" -> {
                        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            var idx = 0L
                            while (reader.hasNext()) {
                                idx++
                                if (raw.size >= maxStations) {
                                    reader.skipValue()
                                    continue
                                }
                                readOverpassAnyStationElement(reader, idx)?.let { raw.add(it) }
                            }
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        return StreamStationsResult(deduplicateStations(raw), hadRuntimeError)
    }

    private fun readOverpassAnyStationElement(reader: JsonReader, fallbackIndex: Long): TransitStation? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var id = fallbackIndex
        var lat = Double.NaN
        var lon = Double.NaN
        var name = ""
        var railway = ""
        var usage = ""
        var stationTag = ""
        var subwayTag = ""
        var lightRailTag = ""
        var trainTag = ""
        var highwayTag = ""
        var busTag = ""
        var routeRef = ""
        var linesTag = ""
        var lineTag = ""
        var isDisused = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextLongOrZero(reader)
                "lat" -> lat = nextDoubleOrNaN(reader)
                "lon" -> lon = nextDoubleOrNaN(reader)
                "center" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "lat" -> if (lat.isNaN()) lat = nextDoubleOrNaN(reader) else reader.skipValue()
                                "lon" -> if (lon.isNaN()) lon = nextDoubleOrNaN(reader) else reader.skipValue()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                "tags" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = nextStringOrEmpty(reader).trim()
                                "railway" -> railway = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "usage" -> usage = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "station" -> stationTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "subway" -> subwayTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "light_rail" -> lightRailTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "train" -> trainTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "highway" -> highwayTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "bus" -> busTag = nextStringOrEmpty(reader).lowercase(Locale.US)
                                "route_ref" -> routeRef = nextStringOrEmpty(reader)
                                "lines" -> linesTag = nextStringOrEmpty(reader)
                                "line" -> lineTag = nextStringOrEmpty(reader)
                                "disused:railway", "abandoned:railway", "construction:railway" -> {
                                    isDisused = true
                                    reader.skipValue()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (name.isBlank() || lat.isNaN() || lon.isNaN()) return null
        if (isDisused || usage == "freight" || railway == "yard" || railway == "freight") return null

        val isSubway = stationTag == "subway" || subwayTag == "yes"
        val isLightRail = stationTag == "light_rail" || lightRailTag == "yes"
        val isTrain = trainTag == "yes" || railway == "station" || railway == "halt"
        val isBus = highwayTag == "bus_stop" || busTag == "yes"

        val (stationType, mode) = when {
            isSubway && !isTrain && !isLightRail -> StationType.UBAHN to RouteTransitChoice.SBAHN
            isTrain || isLightRail -> StationType.SBAHN to RouteTransitChoice.SBAHN
            isBus -> StationType.BUS to RouteTransitChoice.BUS
            else -> return null
        }

        val lines = extractLinesFromTagStrings(routeRef, linesTag, lineTag, mode)
        return TransitStation(
            id = "osm_${stationType.name}_$id",
            name = name,
            location = GeoPoint(lat, lon),
            type = stationType,
            lines = lines
        )
    }

    // ---- Streaming GeoJSON Builder (Zero JSONObject!) ----

    private data class BucketGeoJsonPack(
        val linesJson: String,
        val labelsJson: String,
        val stationsJson: String,
        val terminalsJson: String,
        val stations: List<TransitStation>
    )

    private fun buildBucketGeoJson(
        routes: List<ParsedTransitRoute>,
        stationType: StationType,
        extraStops: List<TransitStation> = emptyList()
    ): BucketGeoJsonPack {
        if (routes.isEmpty() && extraStops.isEmpty()) {
            return BucketGeoJsonPack(
                EMPTY_FEATURE_COLLECTION,
                EMPTY_FEATURE_COLLECTION,
                EMPTY_FEATURE_COLLECTION,
                EMPTY_FEATURE_COLLECTION,
                emptyList()
            )
        }

        val routesByWayId = LinkedHashMap<Long, MutableList<ParsedTransitRoute>>()
        routes.forEach { r ->
            r.ways.forEach { way ->
                if (way.wayId != 0L) {
                    val list = routesByWayId.getOrPut(way.wayId) { mutableListOf() }
                    if (list.none { it.ref.equals(r.ref, ignoreCase = true) }) {
                        list.add(r)
                    }
                }
            }
        }
        val wayComparator = if (stationType == StationType.BUS) {
            BUS_PRIORITY_COMPARATOR
        } else {
            NATURAL_LINE_REF_COMPARATOR
        }
        routesByWayId.values.forEach { list ->
            if (list.size > 1) {
                list.sortWith(wayComparator)
            }
        }

        val bucketLabel = when (stationType) {
            StationType.BUS -> "Bus"
            StationType.REGIO, StationType.LONG_DISTANCE -> "Regio / DB"
            StationType.UBAHN, StationType.TRAM -> "U-Bahn / Tram"
            StationType.SBAHN -> "S-Bahn"
        }

        val linesSw = StringWriter(32 * 1024)
        val labelsSw = StringWriter(16 * 1024)
        val emittedLineWayIds = HashSet<Long>()
        val seenLabelWayIds = HashSet<Long>()

        JsonWriter(linesSw).use { lw ->
            JsonWriter(labelsSw).use { lblW ->
                lw.beginObject().name("type").value("FeatureCollection").name("features").beginArray()
                lblW.beginObject().name("type").value("FeatureCollection").name("features").beginArray()

                for (route in routes) {
                    for (way in route.ways) {
                        val pts = way.lonLatFlat
                        if (pts.size < 4) continue

                        // Bundle identical OSM way IDs within a bucket into ONE clean line feature
                        // instead of fanning out N parallel bars across the map.
                        val isSharedWayId = way.wayId != 0L
                        if (isSharedWayId && !emittedLineWayIds.add(way.wayId)) {
                            continue
                        }

                        val sharing = if (isSharedWayId) {
                            routesByWayId[way.wayId].orEmpty().ifEmpty { listOf(route) }
                        } else {
                            listOf(route)
                        }
                        val bundledColor = if (sharing.size <= 1) {
                            route.colorHex
                        } else {
                            when (stationType) {
                                StationType.BUS -> "#00586A"
                                StationType.REGIO, StationType.LONG_DISTANCE -> "#C8102E"
                                StationType.UBAHN, StationType.TRAM -> "#0065BD"
                                StationType.SBAHN -> "#00975F"
                            }
                        }
                        val hasOverflow = sharing.size > 3
                        val allRefsFull = sharing.joinToString(" · ") { it.ref.trim() }
                        val bundledRefLabel = if (!hasOverflow) {
                            allRefsFull
                        } else {
                            sharing.take(3).joinToString(" · ") { it.ref.trim() } + " · +${sharing.size - 3}"
                        }

                        lw.beginObject()
                        lw.name("type").value("Feature")
                        lw.name("properties").beginObject()
                        lw.name("line").value(bundledRefLabel)
                        lw.name("all_refs").value(allRefsFull)
                        lw.name("ref_count").value(sharing.size)
                        lw.name("has_overflow").value(hasOverflow)
                        lw.name("bucket_label").value(bucketLabel)
                        lw.name("color").value(bundledColor)
                        lw.name("offsetIndex").value(0.0)
                        lw.endObject()
                        lw.name("geometry").beginObject()
                        lw.name("type").value("LineString")
                        lw.name("coordinates").beginArray()
                        var k = 0
                        while (k + 1 < pts.size) {
                            lw.beginArray().value(round5(pts[k])).value(round5(pts[k + 1])).endArray()
                            k += 2
                        }
                        lw.endArray().endObject().endObject()

                        val emitLabel = if (isSharedWayId) seenLabelWayIds.add(way.wayId) else true
                        if (emitLabel) {
                            val labelSlots: List<Pair<String, String>> = if (!hasOverflow) {
                                sharing.map { it.ref.trim() to it.colorHex }
                            } else {
                                sharing.take(3).map { it.ref.trim() to it.colorHex } +
                                    ("+${sharing.size - 3}" to bundledColor)
                            }
                            lblW.beginObject()
                            lblW.name("type").value("Feature")
                            lblW.name("properties").beginObject()
                            lblW.name("all_refs").value(allRefsFull)
                            lblW.name("ref_count").value(sharing.size)
                            lblW.name("has_overflow").value(hasOverflow)
                            lblW.name("bucket_label").value(bucketLabel)
                            for (slot in 1..4) {
                                val item = labelSlots.getOrNull(slot - 1)
                                lblW.name("p$slot").value(item?.first ?: "")
                                lblW.name("c$slot").value(item?.second ?: bundledColor)
                            }
                            lblW.endObject()
                            lblW.name("geometry").beginObject()
                            lblW.name("type").value("LineString")
                            lblW.name("coordinates").beginArray()
                            var m = 0
                            while (m + 1 < pts.size) {
                                lblW.beginArray().value(round5(pts[m])).value(round5(pts[m + 1])).endArray()
                                m += 2
                            }
                            lblW.endArray().endObject().endObject()
                        }
                    }
                }

                lw.endArray().endObject()
                lblW.endArray().endObject()
            }
        }

        val terminalCoordKeys = HashSet<String>()
        val seenStops = HashSet<String>()
        val discoveredStations = mutableListOf<TransitStation>()
        val terminalsSw = StringWriter(4 * 1024)
        val stationsSw = StringWriter(8 * 1024)

        JsonWriter(terminalsSw).use { tw ->
            JsonWriter(stationsSw).use { sw ->
                tw.beginObject().name("type").value("FeatureCollection").name("features").beginArray()
                sw.beginObject().name("type").value("FeatureCollection").name("features").beginArray()

                for (route in routes) {
                    val firstWay = route.ways.firstOrNull()?.lonLatFlat
                    val lastWay = route.ways.lastOrNull()?.lonLatFlat
                    val firstStopName = route.stops.firstOrNull()?.name ?: ""
                    val lastStopName = route.stops.lastOrNull()?.name ?: ""

                    val endpoints = mutableListOf<Triple<Double, Double, String>>()
                    if (firstWay != null && firstWay.size >= 2) {
                        endpoints.add(Triple(firstWay[0], firstWay[1], firstStopName))
                    }
                    if (lastWay != null && lastWay.size >= 2) {
                        endpoints.add(Triple(lastWay[lastWay.size - 2], lastWay[lastWay.size - 1], lastStopName))
                    }

                    for ((lon, lat, stName) in endpoints) {
                        val key = String.format(Locale.US, "%.3f_%.3f", lat, lon)
                        terminalCoordKeys.add(key)
                        tw.beginObject()
                        tw.name("type").value("Feature")
                        tw.name("properties").beginObject()
                        tw.name("label").value(route.ref.take(6))
                        tw.name("stationName").value(stName)
                        tw.name("color").value(route.colorHex)
                        tw.name("textColor").value(route.textColorHex)
                        tw.endObject()
                        tw.name("geometry").beginObject()
                        tw.name("type").value("Point")
                        tw.name("coordinates").beginArray().value(round5(lon)).value(round5(lat)).endArray()
                        tw.endObject()
                        tw.endObject()
                    }

                    val actualStationType = when (stationType) {
                        StationType.SBAHN -> StationType.SBAHN
                        StationType.UBAHN, StationType.TRAM -> when {
                            UBAHN_REF_REGEX.matches(route.ref.trim()) -> StationType.UBAHN
                            else -> StationType.TRAM
                        }
                        else -> stationType
                    }
                    val stopLineLabel = if (actualStationType == StationType.BUS &&
                        !route.ref.startsWith("Bus", ignoreCase = true)
                    ) {
                        "Bus ${route.ref}"
                    } else {
                        route.ref
                    }

                    for (stop in route.stops) {
                        val dedup = "${stop.name.lowercase(Locale.GERMAN)}_${String.format(Locale.US, "%.3f_%.3f", stop.lat, stop.lon)}"
                        val stId = "osm_${actualStationType.name}_$dedup"
                        if (seenStops.add(dedup)) {
                            val coordKey = String.format(Locale.US, "%.3f_%.3f", stop.lat, stop.lon)
                            val isTerminal = terminalCoordKeys.contains(coordKey)
                            sw.beginObject()
                            sw.name("type").value("Feature")
                            sw.name("properties").beginObject()
                            sw.name("id").value(stId)
                            sw.name("name").value(stop.name)
                            sw.name("color").value(route.colorHex)
                            sw.name("isTerminal").value(isTerminal)
                            sw.endObject()
                            sw.name("geometry").beginObject()
                            sw.name("type").value("Point")
                            sw.name("coordinates").beginArray().value(round5(stop.lon)).value(round5(stop.lat)).endArray()
                            sw.endObject()
                            sw.endObject()
                        }

                        discoveredStations.add(
                            TransitStation(
                                id = stId,
                                name = stop.name,
                                location = GeoPoint(stop.lat, stop.lon),
                                type = actualStationType,
                                lines = listOf(stopLineLabel)
                            )
                        )
                    }
                }

                for (extra in extraStops) {
                    val lat = extra.location.latitude
                    val lon = extra.location.longitude
                    val dedup = "${extra.name.lowercase(Locale.GERMAN)}_${String.format(Locale.US, "%.3f_%.3f", lat, lon)}"
                    if (seenStops.add(dedup)) {
                        val coordKey = String.format(Locale.US, "%.3f_%.3f", lat, lon)
                        val isTerminal = terminalCoordKeys.contains(coordKey)
                        val stId = extra.id.ifBlank { "osm_${stationType.name}_$dedup" }
                        sw.beginObject()
                        sw.name("type").value("Feature")
                        sw.name("properties").beginObject()
                        sw.name("id").value(stId)
                        sw.name("name").value(extra.name)
                        sw.name("color").value("#00586A")
                        sw.name("isTerminal").value(isTerminal)
                        sw.endObject()
                        sw.name("geometry").beginObject()
                        sw.name("type").value("Point")
                        sw.name("coordinates").beginArray().value(round5(lon)).value(round5(lat)).endArray()
                        sw.endObject()
                        sw.endObject()
                    }
                    discoveredStations.add(extra)
                }

                tw.endArray().endObject()
                sw.endArray().endObject()
            }
        }

        return BucketGeoJsonPack(
            linesJson = linesSw.toString(),
            labelsJson = labelsSw.toString(),
            stationsJson = stationsSw.toString(),
            terminalsJson = terminalsSw.toString(),
            stations = deduplicateStations(discoveredStations)
        )
    }

    // ---- Shared Utilities ----

    private fun buildRouteDedupKey(
        bucket: TransitBucket,
        ref: String,
        ways: List<CompactWay>
    ): String {
        val normRef = ref.uppercase(Locale.GERMAN)
        if (bucket != TransitBucket.BUS) {
            // Preserve distinct branches (e.g. S1 Freising vs S1 Flughafen) while still collapsing
            // opposite directions of the exact same branch.
            val firstFlat = ways.firstOrNull()?.lonLatFlat
            val lastFlat = ways.lastOrNull()?.lonLatFlat
            if (firstFlat != null && firstFlat.size >= 2 && lastFlat != null && lastFlat.size >= 2) {
                val cellA = "${floor(firstFlat[1] / 0.04).toInt()}_${floor(firstFlat[0] / 0.05).toInt()}"
                val cellB = "${floor(lastFlat[lastFlat.size - 1] / 0.04).toInt()}_${floor(lastFlat[lastFlat.size - 2] / 0.05).toInt()}"
                val ordered = if (cellA <= cellB) "${cellA}__$cellB" else "${cellB}__$cellA"
                return "${bucket.name}_${normRef}_$ordered"
            }
            return "${bucket.name}_$normRef"
        }
        // Spatial grid (~13 km cells) so buses in Freising and Munich with identical ref numbers never drop each other
        val firstFlat = ways.firstOrNull()?.lonLatFlat
        if (firstFlat != null && firstFlat.size >= 2) {
            val lonCell = floor(firstFlat[0] / 0.16).toInt()
            val latCell = floor(firstFlat[1] / 0.12).toInt()
            return "BUS_${normRef}_${latCell}_${lonCell}"
        }
        return "BUS_$normRef"
    }

    private fun isOverpassErrorRemark(remarkLower: String): Boolean =
        remarkLower.contains("timeout") ||
            remarkLower.contains("error") ||
            remarkLower.contains("memory") ||
            remarkLower.contains("rate") ||
            remarkLower.contains("too many")

    private fun deduplicateStations(rawStations: List<TransitStation>): List<TransitStation> {
        val deduped = ArrayList<TransitStation>(rawStations.size.coerceAtMost(4096))
        val indexByTypeAndName = HashMap<String, MutableList<Int>>(rawStations.size.coerceAtMost(4096))

        for (candidate in rawStations) {
            val normName = normalizeStationName(candidate.name)
            val bucketKey = "${candidate.type.ordinal}_$normName"
            val bucketIndices = indexByTypeAndName[bucketKey]
            var matchedIdx = -1
            if (bucketIndices != null) {
                for (idx in bucketIndices) {
                    val existing = deduped[idx]
                    if (GeoDistanceUtils.haversineKm(existing.location, candidate.location) <= 0.45) {
                        matchedIdx = idx
                        break
                    }
                }
            }
            if (matchedIdx >= 0) {
                val existing = deduped[matchedIdx]
                val mergedLines = (existing.lines + candidate.lines).distinct().take(8)
                deduped[matchedIdx] = existing.copy(lines = mergedLines)
            } else {
                val newIdx = deduped.size
                deduped.add(candidate)
                indexByTypeAndName.getOrPut(bucketKey) { mutableListOf() }.add(newIdx)
            }
        }
        return deduped
    }

    private fun extractLinesFromTagStrings(
        routeRef: String,
        linesTag: String,
        lineTag: String,
        mode: RouteTransitChoice
    ): List<String> {
        val raw = listOf(routeRef, linesTag, lineTag)
            .filter { it.isNotBlank() }
            .flatMap { it.split(";", ",", "/", "|") }
            .map { it.trim() }
            .filter { it.length in 1..10 }
            .distinct()
            .take(5)

        if (raw.isNotEmpty()) {
            return if (mode == RouteTransitChoice.BUS) {
                raw.map { if (it.startsWith("Bus", ignoreCase = true)) it else "Bus $it" }
            } else {
                raw
            }
        }
        return when (mode) {
            RouteTransitChoice.SBAHN -> listOf("S-Bahn")
            RouteTransitChoice.BUS -> listOf("Bus")
            RouteTransitChoice.REGIO_DB -> listOf("Regio / DB")
        }
    }

    private fun normalizeStationName(name: String): String {
        return name.lowercase(Locale.GERMAN)
            .replace("bahnhof", "")
            .replace("bf.", "")
            .replace("s-bahnhof", "")
            .replace("s-bahn", "")
            .replace("(", " ")
            .replace(")", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun classifyBucket(routeType: String, ref: String, service: String): TransitBucket {
        if (routeType == "bus") return TransitBucket.BUS
        val upper = ref.uppercase(Locale.GERMAN)
        val srv = service.lowercase(Locale.US)
        return when {
            upper.startsWith("ICE") || upper.startsWith("IC") || upper.startsWith("EC") ||
                upper.startsWith("RJ") || upper.startsWith("TGV") || upper.startsWith("FLX") ||
                upper.startsWith("RE") || upper.startsWith("RB") || upper.startsWith("IRE") ||
                upper.startsWith("MEX") || upper.startsWith("BRB") || upper.startsWith("ALX") ||
                upper.startsWith("BOB") || upper.startsWith("REX") ||
                srv.contains("regional") || srv.contains("long_distance") || srv.contains("high_speed") ->
                TransitBucket.REGIO_LONG
            routeType == "light_rail" || routeType == "subway" || upper.startsWith("S") || upper.startsWith("U") ->
                TransitBucket.SBAHN
            else -> TransitBucket.REGIO_LONG
        }
    }

    private fun normalizeOsmColor(raw: String, bucket: TransitBucket, ref: String): String {
        val cleaned = raw.trim().lowercase(Locale.US)
        if (cleaned.matches(Regex("^#[0-9a-f]{6}$"))) return cleaned.uppercase(Locale.US)
        if (cleaned.matches(Regex("^#[0-9a-f]{3}$"))) {
            val r = cleaned[1]
            val g = cleaned[2]
            val b = cleaned[3]
            return "#$r$r$g$g$b$b".uppercase(Locale.US)
        }
        val named = mapOf(
            "red" to "#DC2626", "darkred" to "#991B1B", "blue" to "#0065BD",
            "darkblue" to "#1E3A8A", "lightblue" to "#16BAE7", "green" to "#15803D",
            "darkgreen" to "#00975F", "lightgreen" to "#76B82A", "yellow" to "#F59E0B",
            "orange" to "#EA580C", "purple" to "#951B81", "violet" to "#7C3AED",
            "magenta" to "#DB2777", "pink" to "#ED6B83", "brown" to "#963833",
            "cyan" to "#0891B2", "teal" to "#00586A", "black" to "#1F2937"
        )
        named[cleaned]?.let { return it }

        val upper = ref.uppercase(Locale.GERMAN)
        return when (bucket) {
            TransitBucket.REGIO_LONG -> when {
                upper.startsWith("ICE") -> "#D5001C"
                upper.startsWith("IC") || upper.startsWith("EC") -> "#B91C1C"
                upper.startsWith("RE") -> "#C8102E"
                upper.startsWith("RB") -> "#1D4ED8"
                else -> "#0F766E"
            }
            TransitBucket.SBAHN -> when {
                upper.startsWith("S1") -> "#16BAE7"
                upper.startsWith("S20") -> "#ED6B83"
                upper.startsWith("S2") -> "#76B82A"
                upper.startsWith("S3") -> "#951B81"
                upper.startsWith("S4") -> "#E30613"
                upper.startsWith("S5") -> "#005C9E"
                upper.startsWith("S6") -> "#00975F"
                upper.startsWith("S7") -> "#963833"
                upper.startsWith("S8") -> "#F7A600"
                upper.startsWith("S9") -> "#991B1B"
                else -> "#00975F"
            }
            TransitBucket.BUS -> {
                val palette = listOf("#00586A", "#0E7490", "#0F766E", "#6D28D9", "#0369A1")
                palette[(upper.hashCode() and 0x7FFFFFFF) % palette.size]
            }
        }
    }

    private fun contrastingTextHex(bgHex: String): String {
        return try {
            val clean = bgHex.removePrefix("#")
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            if (luminance > 175) "#111315" else "#FFFFFF"
        } catch (_: Exception) {
            "#FFFFFF"
        }
    }

    private fun approxRouteDistanceSq(route: ParsedTransitRoute, center: GeoPoint): Double {
        var best = Double.MAX_VALUE
        for (way in route.ways) {
            val pts = way.lonLatFlat
            val count = pts.size / 2
            if (count == 0) continue
            val sampleIndices = intArrayOf(0, count / 2, count - 1)
            for (idx in sampleIndices) {
                val lon = pts[idx * 2]
                val lat = pts[idx * 2 + 1]
                val dLat = lat - center.latitude
                val dLon = (lon - center.longitude) * 0.67
                val d2 = dLat * dLat + dLon * dLon
                if (d2 < best) best = d2
            }
        }
        return best
    }

    private fun round5(value: Double): Double = round(value * 100000.0) / 100000.0

    private fun nextStringOrEmpty(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun nextDoubleOrNaN(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextDouble()
            JsonToken.STRING -> reader.nextString().toDoubleOrNull() ?: Double.NaN
            JsonToken.NULL -> {
                reader.nextNull()
                Double.NaN
            }
            else -> {
                reader.skipValue()
                Double.NaN
            }
        }
    }

    private fun nextLongOrZero(reader: JsonReader): Long {
        return when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextLong()
            JsonToken.STRING -> reader.nextString().toLongOrNull() ?: 0L
            JsonToken.NULL -> {
                reader.nextNull()
                0L
            }
            else -> {
                reader.skipValue()
                0L
            }
        }
    }

    private fun nextIntOrZero(reader: JsonReader): Int = nextLongOrZero(reader).toInt()
}
