package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.CachedOverpassTileDao
import com.example.data.local.CachedOverpassTileEntity
import com.example.data.local.OfflineRegionDao
import com.example.domain.GeoDistanceUtils
import com.example.model.GeoPoint
import com.example.model.MapBounds
import com.example.model.MapTransitMode
import com.example.model.PreferredTransitApp
import com.example.model.RouteTransitChoice
import com.example.model.StationType
import com.example.model.TransitStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.floor

data class DynamicTransitOverlay(
    val sBahnLinesGeoJson: String,
    val sBahnLineLabelsGeoJson: String,
    val sBahnStationsGeoJson: String,
    val sBahnTerminalsGeoJson: String,
    val ubahnTramLinesGeoJson: String = EMPTY_FEATURE_COLLECTION,
    val ubahnTramLineLabelsGeoJson: String = EMPTY_FEATURE_COLLECTION,
    val ubahnTramStationsGeoJson: String = EMPTY_FEATURE_COLLECTION,
    val ubahnTramTerminalsGeoJson: String = EMPTY_FEATURE_COLLECTION,
    val busLinesGeoJson: String,
    val busLineLabelsGeoJson: String,
    val busStationsGeoJson: String,
    val busTerminalsGeoJson: String,
    val regioLinesGeoJson: String,
    val regioLineLabelsGeoJson: String,
    val regioStationsGeoJson: String,
    val regioTerminalsGeoJson: String,
    val discoveredStations: List<TransitStation>
) {
    fun hasAnyFeatures(): Boolean =
        sBahnLinesGeoJson != EMPTY_FEATURE_COLLECTION ||
            ubahnTramLinesGeoJson != EMPTY_FEATURE_COLLECTION ||
            busLinesGeoJson != EMPTY_FEATURE_COLLECTION ||
            regioLinesGeoJson != EMPTY_FEATURE_COLLECTION ||
            discoveredStations.isNotEmpty()

    companion object {
        val EMPTY = DynamicTransitOverlay(
            sBahnLinesGeoJson = EMPTY_FEATURE_COLLECTION,
            sBahnLineLabelsGeoJson = EMPTY_FEATURE_COLLECTION,
            sBahnStationsGeoJson = EMPTY_FEATURE_COLLECTION,
            sBahnTerminalsGeoJson = EMPTY_FEATURE_COLLECTION,
            ubahnTramLinesGeoJson = EMPTY_FEATURE_COLLECTION,
            ubahnTramLineLabelsGeoJson = EMPTY_FEATURE_COLLECTION,
            ubahnTramStationsGeoJson = EMPTY_FEATURE_COLLECTION,
            ubahnTramTerminalsGeoJson = EMPTY_FEATURE_COLLECTION,
            busLinesGeoJson = EMPTY_FEATURE_COLLECTION,
            busLineLabelsGeoJson = EMPTY_FEATURE_COLLECTION,
            busStationsGeoJson = EMPTY_FEATURE_COLLECTION,
            busTerminalsGeoJson = EMPTY_FEATURE_COLLECTION,
            regioLinesGeoJson = EMPTY_FEATURE_COLLECTION,
            regioLineLabelsGeoJson = EMPTY_FEATURE_COLLECTION,
            regioStationsGeoJson = EMPTY_FEATURE_COLLECTION,
            regioTerminalsGeoJson = EMPTY_FEATURE_COLLECTION,
            discoveredStations = emptyList()
        )
    }
}

data class OfflineTransitBundle(
    val transitCacheKey: String,
    val stopsCacheKey: String,
    val stationsCount: Int,
    val busLinesCount: Int = 0,
    val railLinesCount: Int = 0,
    val isPartial: Boolean = false,
    val totalSizeBytes: Long
)

data class ViewportTransitResult(
    val overlay: DynamicTransitOverlay?,
    val fromOfflineRegionId: String? = null,
    val fromLocalCache: Boolean = false,
    val networkWarningHint: String? = null
)

data class RegionalTransitPackage(
    val regionId: String,
    val displayName: String,
    val appLabel: String,
    val defaultTransitApp: PreferredTransitApp,
    val fileName: String,
    val bounds: MapBounds,
    val matchBounds: List<MapBounds> = listOf(bounds),
    val primaryUrl: String,
    val fallbackUrl: String? = null,
    val approxSizeMb: Double = 15.0,
    val localCacheFileName: String = "transit_${regionId.lowercase(Locale.US)}.json",
    val legacyFileName: String? = null
) {
    val tmpFileName: String get() = "$localCacheFileName.tmp"
    val localOverlayRegionId: String get() = "regional_bundle_${regionId.lowercase(Locale.US)}"
    fun containsPoint(point: GeoPoint): Boolean = matchBounds.any { it.contains(point) }
}

data class RegionalPackageStatus(
    val regionId: String,
    val displayName: String,
    val appLabel: String,
    val isDownloaded: Boolean,
    val sizeBytes: Long,
    val remoteSizeBytes: Long? = null,
    val approxSizeMb: Double = 15.0,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null
)

/**
 * Memory-safe, streaming OpenStreetMap / Overpass transit repository with regional pre-built
 * offline JSON packages (`lm_v2_routes` format).
 *
 * Uses [OverpassStreamCodec] (`android.util.JsonReader` / `JsonWriter` directly on HTTP
 * `InputStream`s) — ZERO `org.json.JSONObject` allocations on Overpass responses — and stores
 * compact, deduplicated, decimated payloads in RAM LRU + local internal storage / Room.
 */
class OverpassTransitRepository(
    private val context: Context,
    private val tileDao: CachedOverpassTileDao,
    private val offlineRegionDao: OfflineRegionDao
) {
    companion object {
        const val MIN_RAIL_ZOOM = 10.6
        const val MIN_BUS_ZOOM = 11.8
        const val MIN_BUS_ZOOM_ONLY = 11.0
        const val REGIONAL_BASE_URL = "https://rotorvision2013.de/letztemeile/"
        const val MVV_BUNDLE_URL = "${REGIONAL_BASE_URL}mvv_transit.json"
        const val MVV_LOCAL_REGION_ID = "regional_bundle_mvv"
        private const val MAX_REGIONAL_BUNDLE_BYTES = 512L * 1024L * 1024L
        private const val MAX_RAM_CACHE_ENTRIES = 12
        private const val MAX_PARSED_BUNDLES_IN_RAM = 2

        val MVV_BOUNDS = MapBounds(
            south = 47.65,
            west = 10.90,
            north = 48.65,
            east = 12.35
        )

        /**
         * Vollständige Regionen-Tabelle für vorgefertigte Offline-ÖPNV-JSONs (`lm_v2_routes`).
         *
         * WICHTIGE REIHENFOLGE (Priorität bei GPS-Überschneidungen):
         * Alle 9 spezifischen deutschen Regionen stehen VOR den großen Länder-Paketen (`AT`, `CH`),
         * damit im Grenzgebiet (z. B. Rosenheim, Bodensee) niemals das falsche Netz greift.
         *
         * Jede Region besitzt eine [RegionalTransitPackage.primaryUrl] (NAS) sowie eine optionale
         * [RegionalTransitPackage.fallbackUrl] (z. B. späterer GitHub-Release-Link).
         */
        val REGIONAL_PACKAGES: Map<String, RegionalTransitPackage> = linkedMapOf(
            "MVV" to RegionalTransitPackage(
                regionId = "MVV",
                displayName = "München / MVV",
                appLabel = "MVV-App / MVGO",
                defaultTransitApp = PreferredTransitApp.MVV_APP,
                fileName = "mvv_transit.json",
                bounds = MVV_BOUNDS,
                primaryUrl = "${REGIONAL_BASE_URL}mvv_transit.json",
                fallbackUrl = null,
                approxSizeMb = 20.0,
                legacyFileName = "mvv_transit.json"
            ),
            "VBB" to RegionalTransitPackage(
                regionId = "VBB",
                displayName = "Berlin & Brandenburg (VBB)",
                appLabel = "BVG Jelbi / VBB",
                defaultTransitApp = PreferredTransitApp.BVG_JELBI,
                fileName = "vbb_transit.json",
                bounds = MapBounds(south = 51.90, west = 12.60, north = 52.95, east = 14.20),
                primaryUrl = "${REGIONAL_BASE_URL}vbb_transit.json",
                fallbackUrl = null,
                approxSizeMb = 30.0
            ),
            "HVV" to RegionalTransitPackage(
                regionId = "HVV",
                displayName = "Hamburg (HVV)",
                appLabel = "hvv switch",
                defaultTransitApp = PreferredTransitApp.HVV_SWITCH,
                fileName = "hvv_transit.json",
                bounds = MapBounds(south = 53.20, west = 9.40, north = 53.95, east = 10.65),
                primaryUrl = "${REGIONAL_BASE_URL}hvv_transit.json",
                fallbackUrl = null,
                approxSizeMb = 18.0
            ),
            "RMV" to RegionalTransitPackage(
                regionId = "RMV",
                displayName = "Frankfurt / Rhein-Main (RMV)",
                appLabel = "RMVgo",
                defaultTransitApp = PreferredTransitApp.RMV_GO,
                fileName = "rmv_transit.json",
                bounds = MapBounds(south = 49.75, west = 7.95, north = 50.65, east = 9.25),
                primaryUrl = "${REGIONAL_BASE_URL}rmv_transit.json",
                fallbackUrl = null,
                approxSizeMb = 28.0
            ),
            "VVS" to RegionalTransitPackage(
                regionId = "VVS",
                displayName = "Stuttgart (VVS)",
                appLabel = "VVS Mobil",
                defaultTransitApp = PreferredTransitApp.VVS_MOBIL,
                fileName = "vvs_transit.json",
                bounds = MapBounds(south = 48.45, west = 8.75, north = 49.15, east = 9.85),
                primaryUrl = "${REGIONAL_BASE_URL}vvs_transit.json",
                fallbackUrl = null,
                approxSizeMb = 18.0
            ),
            "VRS" to RegionalTransitPackage(
                regionId = "VRS",
                displayName = "Köln / Bonn (VRS)",
                appLabel = "KVB / VRS",
                defaultTransitApp = PreferredTransitApp.VRS_KVB,
                fileName = "vrs_transit.json",
                bounds = MapBounds(south = 50.55, west = 6.45, north = 51.05, east = 7.65),
                primaryUrl = "${REGIONAL_BASE_URL}vrs_transit.json",
                fallbackUrl = null,
                approxSizeMb = 16.0
            ),
            "VRR" to RegionalTransitPackage(
                regionId = "VRR",
                displayName = "Düsseldorf / Ruhrgebiet (VRR)",
                appLabel = "VRR / Rheinbahn",
                defaultTransitApp = PreferredTransitApp.VRR_APP,
                fileName = "vrr_transit.json",
                bounds = MapBounds(south = 51.05, west = 6.15, north = 51.75, east = 7.65),
                primaryUrl = "${REGIONAL_BASE_URL}vrr_transit.json",
                fallbackUrl = null,
                approxSizeMb = 32.0
            ),
            "VGN" to RegionalTransitPackage(
                regionId = "VGN",
                displayName = "Nürnberg / Franken (VGN)",
                appLabel = "VGN",
                defaultTransitApp = PreferredTransitApp.VGN_APP,
                fileName = "vgn_transit.json",
                bounds = MapBounds(south = 49.10, west = 10.45, north = 49.85, east = 11.65),
                primaryUrl = "${REGIONAL_BASE_URL}vgn_transit.json",
                fallbackUrl = null,
                approxSizeMb = 15.0
            ),
            "SACHSEN" to RegionalTransitPackage(
                regionId = "SACHSEN",
                displayName = "Dresden / Leipzig (Sachsen)",
                appLabel = "DVB / LeipzigMOVE",
                defaultTransitApp = PreferredTransitApp.SACHSEN_DVB,
                fileName = "sachsen_transit.json",
                bounds = MapBounds(south = 50.15, west = 11.85, north = 51.70, east = 15.05),
                primaryUrl = "${REGIONAL_BASE_URL}sachsen_transit.json",
                fallbackUrl = null,
                approxSizeMb = 25.0
            ),
            // Stufe 2: 8 deutsche Flächen-Regionen (nach den 9 Ballungsräumen, vor AT/CH!)
            "NDS_BREMEN" to RegionalTransitPackage(
                regionId = "NDS_BREMEN",
                displayName = "Niedersachsen & Bremen",
                appLabel = "ÜSTRA / VBN / DB Navigator",
                defaultTransitApp = PreferredTransitApp.NDS_BREMEN,
                fileName = "nds_bremen_transit.json",
                bounds = MapBounds(south = 51.28, west = 6.65, north = 53.90, east = 11.60),
                primaryUrl = "${REGIONAL_BASE_URL}nds_bremen_transit.json",
                fallbackUrl = null,
                approxSizeMb = 35.0
            ),
            "NORD" to RegionalTransitPackage(
                regionId = "NORD",
                displayName = "Schleswig-Holstein & MV",
                appLabel = "NAH.SH / VVW / DB Navigator",
                defaultTransitApp = PreferredTransitApp.NORD,
                fileName = "nord_transit.json",
                bounds = MapBounds(south = 53.10, west = 8.20, north = 55.10, east = 14.45),
                primaryUrl = "${REGIONAL_BASE_URL}nord_transit.json",
                fallbackUrl = null,
                approxSizeMb = 25.0
            ),
            "WESTFALEN" to RegionalTransitPackage(
                regionId = "WESTFALEN",
                displayName = "Westfalen / Nord-NRW",
                appLabel = "WestfalenTarif / DB Navigator",
                defaultTransitApp = PreferredTransitApp.WESTFALEN,
                fileName = "westfalen_transit.json",
                bounds = MapBounds(south = 50.70, west = 7.15, north = 52.55, east = 9.50),
                primaryUrl = "${REGIONAL_BASE_URL}westfalen_transit.json",
                fallbackUrl = null,
                approxSizeMb = 25.0
            ),
            "RLP_SL" to RegionalTransitPackage(
                regionId = "RLP_SL",
                displayName = "Rheinland-Pfalz & Saarland",
                appLabel = "VRM / saarVV / DB Navigator",
                defaultTransitApp = PreferredTransitApp.RLP_SL,
                fileName = "rlp_sl_transit.json",
                bounds = MapBounds(south = 49.10, west = 6.10, north = 50.95, east = 8.55),
                primaryUrl = "${REGIONAL_BASE_URL}rlp_sl_transit.json",
                fallbackUrl = null,
                approxSizeMb = 25.0
            ),
            "SUEDWEST" to RegionalTransitPackage(
                regionId = "SUEDWEST",
                displayName = "Baden & Rhein-Neckar",
                appLabel = "VRN / KVV / bwegt",
                defaultTransitApp = PreferredTransitApp.SUEDWEST,
                fileName = "suedwest_transit.json",
                bounds = MapBounds(south = 47.50, west = 7.50, north = 49.80, east = 10.15),
                primaryUrl = "${REGIONAL_BASE_URL}suedwest_transit.json",
                fallbackUrl = null,
                approxSizeMb = 30.0
            ),
            "MITTE" to RegionalTransitPackage(
                regionId = "MITTE",
                displayName = "Thüringen & Sachsen-Anhalt",
                appLabel = "INSA / VMT / DB Navigator",
                defaultTransitApp = PreferredTransitApp.MITTE,
                fileName = "mitte_transit.json",
                bounds = MapBounds(south = 50.20, west = 9.85, north = 53.05, east = 13.20),
                primaryUrl = "${REGIONAL_BASE_URL}mitte_transit.json",
                fallbackUrl = null,
                approxSizeMb = 25.0
            ),
            "SCHWABEN" to RegionalTransitPackage(
                regionId = "SCHWABEN",
                displayName = "Schwaben & Allgäu",
                appLabel = "AVV / DING / DB Navigator",
                defaultTransitApp = PreferredTransitApp.SCHWABEN,
                fileName = "schwaben_transit.json",
                bounds = MapBounds(south = 47.25, west = 9.60, north = 48.95, east = 11.65),
                primaryUrl = "${REGIONAL_BASE_URL}schwaben_transit.json",
                fallbackUrl = null,
                approxSizeMb = 20.0
            ),
            "BAYERN_NORD" to RegionalTransitPackage(
                regionId = "BAYERN_NORD",
                displayName = "Nord- & Ostbayern",
                appLabel = "RVV / VVM / DB Navigator",
                defaultTransitApp = PreferredTransitApp.BAYERN_NORD,
                fileName = "bayern_nord_transit.json",
                bounds = MapBounds(south = 48.40, west = 8.95, north = 50.60, east = 13.85),
                primaryUrl = "${REGIONAL_BASE_URL}bayern_nord_transit.json",
                fallbackUrl = null,
                approxSizeMb = 28.0
            ),
            // Stufe 3: Große Länder-Pakete immer ZULETZT prüfen (Österreich mit 2 präzisen Teil-Boxen)!
            "AT" to RegionalTransitPackage(
                regionId = "AT",
                displayName = "Österreich (VOR / ÖBB)",
                appLabel = "WienMobil / ÖBB Scotty",
                defaultTransitApp = PreferredTransitApp.OEBB_SCOTTY,
                fileName = "at_transit.json",
                bounds = MapBounds(south = 46.35, west = 9.50, north = 49.05, east = 17.20),
                matchBounds = listOf(
                    // West-Österreich (Vorarlberg & Tirol)
                    MapBounds(south = 46.70, west = 9.50, north = 47.75, east = 12.65),
                    // Ost- & Zentral-Österreich (Salzburg, OÖ, NÖ, Wien, Steiermark, Kärnten, Burgenland)
                    MapBounds(south = 46.35, west = 12.10, north = 49.05, east = 17.20)
                ),
                primaryUrl = "${REGIONAL_BASE_URL}at_transit.json",
                fallbackUrl = null,
                approxSizeMb = 119.0
            ),
            "CH" to RegionalTransitPackage(
                regionId = "CH",
                displayName = "Schweiz (SBB)",
                appLabel = "SBB Mobile",
                defaultTransitApp = PreferredTransitApp.SBB_MOBILE,
                fileName = "ch_transit.json",
                bounds = MapBounds(south = 45.80, west = 5.95, north = 47.85, east = 10.50),
                primaryUrl = "${REGIONAL_BASE_URL}ch_transit.json",
                fallbackUrl = null,
                approxSizeMb = 95.0
            )
        )

        fun getPackageForRegionId(regionId: String): RegionalTransitPackage? =
            REGIONAL_PACKAGES[regionId.uppercase(Locale.US)]

        fun findPackageForPoint(point: GeoPoint): RegionalTransitPackage? =
            REGIONAL_PACKAGES.values.firstOrNull { it.containsPoint(point) }

        fun isRegionalBundleRegionId(fromOfflineRegionId: String?): Boolean =
            fromOfflineRegionId != null && fromOfflineRegionId.startsWith("regional_bundle_")

        fun regionIdForTransitApp(app: PreferredTransitApp): String = when (app) {
            PreferredTransitApp.MVV_APP, PreferredTransitApp.MVGO -> "MVV"
            PreferredTransitApp.BVG_JELBI, PreferredTransitApp.VBB_APP -> "VBB"
            PreferredTransitApp.HVV_SWITCH -> "HVV"
            PreferredTransitApp.RMV_GO -> "RMV"
            PreferredTransitApp.VVS_MOBIL -> "VVS"
            PreferredTransitApp.VRS_KVB -> "VRS"
            PreferredTransitApp.VRR_APP -> "VRR"
            PreferredTransitApp.VGN_APP -> "VGN"
            PreferredTransitApp.SACHSEN_DVB -> "SACHSEN"
            PreferredTransitApp.NDS_BREMEN -> "NDS_BREMEN"
            PreferredTransitApp.NORD -> "NORD"
            PreferredTransitApp.WESTFALEN -> "WESTFALEN"
            PreferredTransitApp.RLP_SL -> "RLP_SL"
            PreferredTransitApp.SUEDWEST -> "SUEDWEST"
            PreferredTransitApp.MITTE -> "MITTE"
            PreferredTransitApp.SCHWABEN -> "SCHWABEN"
            PreferredTransitApp.BAYERN_NORD -> "BAYERN_NORD"
            PreferredTransitApp.OEBB_SCOTTY -> "AT"
            PreferredTransitApp.SBB_MOBILE -> "CH"
            PreferredTransitApp.DB_NAVIGATOR -> "DB"
        }

        fun regionIdForDetectedRegionKey(regionKey: String): String {
            val normalized = regionKey.uppercase(Locale.US)
            if (REGIONAL_PACKAGES.containsKey(normalized)) return normalized
            return when (regionKey) {
                "munich_mvv" -> "MVV"
                "berlin_bvg", "brandenburg_vbb" -> "VBB"
                "hamburg_hvv" -> "HVV"
                "austria_oebb", "OEBB" -> "AT"
                "switzerland_sbb", "SBB" -> "CH"
                else -> "DB"
            }
        }

        fun minBusZoomFor(busOnlyMode: Boolean): Double =
            if (busOnlyMode) MIN_BUS_ZOOM_ONLY else MIN_BUS_ZOOM
    }

    private data class RamCachedOverlay(
        val bounds: MapBounds,
        val includesBuses: Boolean,
        val fromOfflineRegionId: String?,
        val overlay: DynamicTransitOverlay,
        val cachedAt: Long
    )

    private val ramCacheLock = Any()
    private val ramCache = ArrayList<RamCachedOverlay>(MAX_RAM_CACHE_ENTRIES)

    private val regionalMutexes = ConcurrentHashMap<String, Mutex>()
    private val parsedBundleLock = Any()
    private val cachedRegionalBundles = LinkedHashMap<String, ParsedMvvBundle>(4, 0.75f, true)

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val offlineClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(32, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val bundleClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val endpoints = listOf(
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )

    private val ttlMillis = 24L * 60L * 60L * 1000L

    fun isInMvvArea(point: GeoPoint): Boolean = findPackageForPoint(point) != null

    private fun mutexForRegion(regionId: String): Mutex =
        regionalMutexes.computeIfAbsent(regionId) { Mutex() }

    private fun packageFile(pkg: RegionalTransitPackage): File =
        File(context.filesDir, pkg.localCacheFileName)

    private fun packageTmpFile(pkg: RegionalTransitPackage): File =
        File(context.filesDir, pkg.tmpFileName)

    private fun packageLegacyFile(pkg: RegionalTransitPackage): File? =
        pkg.legacyFileName?.let { File(context.filesDir, it) }

    private fun getCachedParsedBundle(regionId: String): ParsedMvvBundle? =
        synchronized(parsedBundleLock) { cachedRegionalBundles[regionId] }

    private fun putCachedParsedBundle(regionId: String, bundle: ParsedMvvBundle) {
        synchronized(parsedBundleLock) {
            cachedRegionalBundles[regionId] = bundle
            while (cachedRegionalBundles.size > MAX_PARSED_BUNDLES_IN_RAM) {
                val eldestKey = cachedRegionalBundles.entries.firstOrNull()?.key ?: break
                cachedRegionalBundles.remove(eldestKey)
            }
        }
    }

    private fun removeCachedParsedBundle(regionId: String) {
        synchronized(parsedBundleLock) { cachedRegionalBundles.remove(regionId) }
    }

    fun hasLocalPackageFor(pkg: RegionalTransitPackage): Boolean {
        if (getCachedParsedBundle(pkg.regionId) != null) return true
        if (packageFile(pkg).let { it.exists() && it.length() >= 64L }) return true
        return packageLegacyFile(pkg)?.let { it.exists() && it.length() >= 64L } == true
    }

    fun hasLocalPackageForRegionId(regionId: String): Boolean =
        getPackageForRegionId(regionId)?.let { hasLocalPackageFor(it) } == true

    /**
     * Returns parsed transit routes (`lm_v2_routes`) from the already-loaded RAM cache or local
     * downloaded regional JSON files covering [origin] and [destination] (never blocks on network).
     */
    internal suspend fun getLocalTransitRoutesFor(
        origin: GeoPoint,
        destination: GeoPoint
    ): List<ParsedTransitRoute> = withContext(Dispatchers.IO) {
        val packages = linkedSetOf<RegionalTransitPackage>()
        findPackageForPoint(origin)?.let { packages.add(it) }
        findPackageForPoint(destination)?.let { packages.add(it) }

        val collected = mutableListOf<ParsedTransitRoute>()
        val loadedIds = hashSetOf<String>()
        for (pkg in packages) {
            val bundle = ensurePackageLoaded(pkg, allowNetworkDownload = false)
            if (bundle != null) {
                collected.addAll(bundle.routes)
                loadedIds.add(pkg.regionId)
            }
        }
        if (collected.isEmpty()) {
            synchronized(parsedBundleLock) {
                cachedRegionalBundles.forEach { (id, bundle) ->
                    if (id !in loadedIds) {
                        collected.addAll(bundle.routes)
                    }
                }
            }
        }
        collected
    }

    private val remoteSizeCache = ConcurrentHashMap<String, Long>()

    private val headProbeClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getLocalPackageSizeBytes(pkg: RegionalTransitPackage): Long {
        val primary = packageFile(pkg)
        if (primary.exists() && primary.length() >= 64L) return primary.length()
        val legacy = packageLegacyFile(pkg)
        if (legacy != null && legacy.exists() && legacy.length() >= 64L) return legacy.length()
        return 0L
    }

    fun getAllRegionalPackageSnapshots(): List<RegionalPackageStatus> =
        REGIONAL_PACKAGES.values.map { pkg ->
            val bytes = getLocalPackageSizeBytes(pkg)
            RegionalPackageStatus(
                regionId = pkg.regionId,
                displayName = pkg.displayName,
                appLabel = pkg.appLabel,
                isDownloaded = bytes >= 64L,
                sizeBytes = bytes,
                remoteSizeBytes = remoteSizeCache[pkg.regionId],
                approxSizeMb = pkg.approxSizeMb
            )
        }

    /**
     * Issues lightweight HTTP HEAD requests in parallel to discover exact server Content-Length
     * values for regional bundles not yet cached in [remoteSizeCache].
     */
    suspend fun fetchRemotePackageSizes(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!NetworkConnectivityHelper.isOnline(context)) {
            return@withContext remoteSizeCache.toMap()
        }
        val missing = REGIONAL_PACKAGES.values.filter { !remoteSizeCache.containsKey(it.regionId) }
        if (missing.isEmpty()) {
            return@withContext remoteSizeCache.toMap()
        }
        coroutineScope {
            missing.map { pkg ->
                async {
                    val bytes = probeRemoteContentLength(pkg.primaryUrl)
                        ?: pkg.fallbackUrl?.let { probeRemoteContentLength(it) }
                    if (bytes != null && bytes > 0L) {
                        remoteSizeCache[pkg.regionId] = bytes
                    }
                }
            }.awaitAll()
        }
        remoteSizeCache.toMap()
    }

    private fun probeRemoteContentLength(url: String): Long? {
        if (url.isBlank()) return null
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "LetzteMeile-Android/0.9 (Size Probe)")
                .build()
            headProbeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Streams a regional JSON file directly from network to disk (`transit_<regionId>.json.tmp`
     * -> atomic rename to `transit_<regionId>.json`) without loading the full file into RAM.
     * Returns [Result.success] with file size in bytes, or [Result.failure] with a concrete
     * diagnostic message (HTTP status code or Exception class name).
     */
    suspend fun downloadPackageToDiskOnly(
        regionId: String,
        forceRefresh: Boolean = true,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Long> = withContext(Dispatchers.IO) {
        val pkg = getPackageForRegionId(regionId)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Unbekannte Region: $regionId")
            )

        if (!NetworkConnectivityHelper.isOnline(context)) {
            return@withContext Result.failure(
                IllegalStateException("Keine Internetverbindung (Offline)")
            )
        }

        mutexForRegion(pkg.regionId).withLock {
            val targetFile = packageFile(pkg)
            if (!forceRefresh && targetFile.exists() && targetFile.length() >= 64L) {
                return@withLock Result.success(targetFile.length())
            }

            val downloadResult = downloadPackageWithFailover(
                pkg = pkg,
                targetFile = targetFile,
                onProgress = onProgress
            )
            if (downloadResult.isSuccess) {
                removeCachedParsedBundle(pkg.regionId)
                clearRamCacheForRegion(pkg.localOverlayRegionId)
                packageLegacyFile(pkg)?.let { legacy ->
                    if (legacy.exists() && legacy.absolutePath != targetFile.absolutePath) {
                        try { legacy.delete() } catch (_: Exception) {}
                    }
                }
            }
            downloadResult
        }
    }

    /**
     * Deletes ONLY the local `transit_<regionId>.json` file (and RAM entries) for [regionId].
     */
    suspend fun deleteLocalRegionalPackage(regionId: String): Boolean = withContext(Dispatchers.IO) {
        val pkg = getPackageForRegionId(regionId) ?: return@withContext false
        mutexForRegion(pkg.regionId).withLock {
            removeCachedParsedBundle(pkg.regionId)
            clearRamCacheForRegion(pkg.localOverlayRegionId)
            var deletedAny = false
            listOfNotNull(
                packageFile(pkg),
                packageTmpFile(pkg),
                packageLegacyFile(pkg),
                pkg.legacyFileName?.let { File(context.filesDir, "$it.tmp") }
            ).forEach { file ->
                try {
                    if (file.exists() && file.delete()) {
                        deletedAny = true
                    }
                } catch (_: Exception) {
                    // Non-fatal
                }
            }
            deletedAny
        }
    }

    private fun clearRamCacheForRegion(localOverlayRegionId: String) {
        synchronized(ramCacheLock) {
            ramCache.removeAll { it.fromOfflineRegionId == localOverlayRegionId }
        }
    }

    /**
     * Thread-safe, single-flight loader for any configured [RegionalTransitPackage].
     *
     * 1. Returns immediately from RAM if already parsed.
     * 2. Checks local per-region file `transit_<regionId>.json` (migrating legacy `mvv_transit.json`
     *    transparently if present) so previously downloaded regions load offline without re-downloading.
     * 3. If missing/empty and [allowNetworkDownload] is true, tries [RegionalTransitPackage.primaryUrl]
     *    first and automatically falls back to [RegionalTransitPackage.fallbackUrl] (if configured),
     *    streaming directly to a `.tmp` file on disk and atomically renaming it after completion.
     * 4. Streams the local file once via [OverpassStreamCodec.parseLocalBundleFile] (`JsonReader`)
     *    and caches routes + deduplicated stations in RAM LRU.
     */
    private suspend fun ensurePackageLoaded(
        pkg: RegionalTransitPackage,
        allowNetworkDownload: Boolean
    ): ParsedMvvBundle? = withContext(Dispatchers.IO) {
        getCachedParsedBundle(pkg.regionId)?.let { return@withContext it }

        mutexForRegion(pkg.regionId).withLock {
            getCachedParsedBundle(pkg.regionId)?.let { return@withLock it }

            val targetFile = packageFile(pkg)
            if (!targetFile.exists() || targetFile.length() < 64L) {
                // Seamlessly migrate legacy mvv_transit.json -> transit_mvv.json if present
                val legacy = packageLegacyFile(pkg)
                if (legacy != null && legacy.exists() && legacy.length() >= 64L) {
                    try {
                        if (!legacy.renameTo(targetFile)) {
                            legacy.copyTo(targetFile, overwrite = true)
                            legacy.delete()
                        }
                    } catch (_: Exception) {
                        // Fall through to network download if migration fails
                    }
                }
            }

            if (!targetFile.exists() || targetFile.length() < 64L) {
                if (!allowNetworkDownload || !NetworkConnectivityHelper.isOnline(context)) {
                    return@withLock null
                }
                val downloadResult = downloadPackageWithFailover(pkg, targetFile)
                if (downloadResult.isFailure) {
                    return@withLock null
                }
            }

            val parsed = try {
                OverpassStreamCodec.parseLocalBundleFile(targetFile)
            } catch (oom: OutOfMemoryError) {
                Log.e("OverpassTransit", "OutOfMemoryError parsing ${targetFile.name}", oom)
                return@withLock null
            }
            if (parsed != null && parsed.routes.isNotEmpty()) {
                putCachedParsedBundle(pkg.regionId, parsed)
                Log.d(
                    "OverpassTransit",
                    "Regional offline bundle [${pkg.regionId}] ready (${targetFile.name}): routes=${parsed.routes.size}, stations=${parsed.stations.size}"
                )
                return@withLock parsed
            } else {
                Log.w("OverpassTransit", "Local ${targetFile.name} invalid or empty – removing corrupt file")
                try {
                    targetFile.delete()
                } catch (_: Exception) {
                    // Ignore cleanup error
                }
                return@withLock null
            }
        }
    }

    private suspend fun downloadPackageWithFailover(
        pkg: RegionalTransitPackage,
        targetFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Long> {
        val candidateUrls = listOfNotNull(
            pkg.primaryUrl.takeIf { it.isNotBlank() },
            pkg.fallbackUrl?.takeIf { it.isNotBlank() }
        )
        if (candidateUrls.isEmpty()) {
            return Result.failure(IllegalStateException("Keine Download-URL konfiguriert"))
        }

        var lastError: Throwable? = null
        for ((index, url) in candidateUrls.withIndex()) {
            val label = if (index == 0) "primary" else "fallback"
            val result = downloadUrlToFileAtomically(
                url = url,
                targetFile = targetFile,
                tmpFile = packageTmpFile(pkg),
                logLabel = "${pkg.regionId}/$label",
                onProgress = onProgress
            )
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            if (index == 0 && candidateUrls.size > 1) {
                Log.w("OverpassTransit", "Primary URL failed for ${pkg.regionId} – trying fallbackUrl")
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Download fehlgeschlagen"))
    }

    private suspend fun downloadUrlToFileAtomically(
        url: String,
        targetFile: File,
        tmpFile: File,
        logLabel: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Long> {
        try {
            if (tmpFile.exists()) tmpFile.delete()
        } catch (_: Exception) {
            // Ignore stale tmp cleanup failure
        }

        val req = Request.Builder()
            .url(url)
            .get()
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Pragma", "no-cache")
            .header("User-Agent", "LetzteMeile-Android/0.9 (Regional Bundle)")
            .build()
        val call = bundleClient.newCall(req)
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        val startMs = System.currentTimeMillis()
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val reason = resp.message.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                    val msg = "HTTP ${resp.code}$reason"
                    Log.w("OverpassTransit", "Bundle [$logLabel] $msg from $url")
                    return Result.failure(IllegalStateException(msg))
                }
                val body = resp.body
                    ?: return Result.failure(IllegalStateException("Leere Server-Antwort (HTTP ${resp.code})"))
                val contentLength = body.contentLength()
                if (contentLength > MAX_REGIONAL_BUNDLE_BYTES) {
                    val mb = contentLength / (1024 * 1024)
                    val msg = "Datei zu groß (${mb} MB, Limit ${MAX_REGIONAL_BUNDLE_BYTES / (1024 * 1024)} MB)"
                    Log.w("OverpassTransit", "Bundle [$logLabel] $msg")
                    return Result.failure(IllegalStateException(msg))
                }

                var totalWritten = 0L
                var lastReportedMs = 0L
                var lastReportedBytes = 0L
                val buffer = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            totalWritten += read
                            if (totalWritten > MAX_REGIONAL_BUNDLE_BYTES) {
                                throw IllegalStateException(
                                    "Dateigröße überschreitet ${MAX_REGIONAL_BUNDLE_BYTES / (1024 * 1024)} MB-Limit"
                                )
                            }
                            output.write(buffer, 0, read)
                            val nowMs = System.currentTimeMillis()
                            if (totalWritten - lastReportedBytes >= 256L * 1024L || nowMs - lastReportedMs >= 180L) {
                                lastReportedBytes = totalWritten
                                lastReportedMs = nowMs
                                onProgress(totalWritten, contentLength)
                            }
                        }
                        output.flush()
                    }
                }
                onProgress(totalWritten, if (contentLength > 0L) contentLength else totalWritten)

                if (totalWritten < 64L || !validateBundleFileHeader(tmpFile)) {
                    tmpFile.delete()
                    return Result.failure(
                        IllegalStateException("Ungültige oder leere JSON-Datei ($totalWritten Bytes)")
                    )
                }

                if (targetFile.exists() && !tmpFile.renameTo(targetFile)) {
                    targetFile.delete()
                }
                val renamed = if (tmpFile.exists()) tmpFile.renameTo(targetFile) else targetFile.exists()
                if (!renamed || !targetFile.exists() || targetFile.length() < 64L) {
                    tmpFile.delete()
                    return Result.failure(
                        IllegalStateException("Datei konnte lokal nicht gespeichert werden (renameTo fehlgeschlagen)")
                    )
                }

                Log.d(
                    "OverpassTransit",
                    "Downloaded ${targetFile.name} [$logLabel] (${totalWritten / 1024} KB) in ${System.currentTimeMillis() - startMs}ms"
                )
                return Result.success(targetFile.length())
            }
        } catch (ce: CancellationException) {
            call.cancel()
            try { tmpFile.delete() } catch (_: Exception) {}
            throw ce
        } catch (e: Throwable) {
            val detail = e.message?.trim()?.take(90)?.takeIf { it.isNotEmpty() }
            val diagnostic = if (detail != null) {
                "${e.javaClass.simpleName}: $detail"
            } else {
                e.javaClass.simpleName
            }
            Log.w("OverpassTransit", "Failed downloading [$logLabel] from $url: $diagnostic")
            try { tmpFile.delete() } catch (_: Exception) {}
            return Result.failure(IllegalStateException(diagnostic, e))
        } finally {
            cancelHandle.dispose()
        }
    }

    /**
     * Lightweight O(1)-memory check on the first 4 KB of a downloaded bundle to ensure the server
     * did not return an HTML error page or empty response before replacing the local file.
     */
    private fun validateBundleFileHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 64L) return false
        return try {
            val probe = ByteArray(4096)
            val read = file.inputStream().use { it.read(probe) }
            if (read <= 0) return false
            val head = String(probe, 0, read, Charsets.UTF_8).trimStart()
            head.startsWith("{") && (head.contains("\"lm_v2_routes\"") || head.contains("\"lm_v2_stops\""))
        } catch (_: Exception) {
            false
        }
    }

    private fun buildRegionalOverlay(bundle: ParsedMvvBundle, center: GeoPoint): DynamicTransitOverlay {
        return OverpassStreamCodec.buildDynamicOverlay(
            allRoutes = bundle.routes,
            focusCenter = center,
            extraBusStops = bundle.standaloneBusStops
        )
    }

    /**
     * Downloads (or refreshes) the configured offline JSON package for [regionId] if a URL entry
     * exists in [REGIONAL_PACKAGES].
     *
     * Returns `null` if no offline JSON package is configured for [regionId] yet; otherwise returns
     * `Result.success(routesCount to stationsCount)` or `Result.failure(...)` with diagnostic details.
     */
    suspend fun downloadRegionalPackageIfAvailable(
        regionId: String,
        forceRefresh: Boolean = false
    ): Result<Pair<Int, Int>>? = withContext(Dispatchers.IO) {
        val pkg = getPackageForRegionId(regionId) ?: return@withContext null

        if (forceRefresh) {
            clearRamCache()
            val diskDownload = downloadPackageToDiskOnly(regionId = pkg.regionId, forceRefresh = true)
            if (diskDownload.isFailure) {
                return@withContext Result.failure(
                    diskDownload.exceptionOrNull()
                        ?: IllegalStateException("Download für ${pkg.displayName} fehlgeschlagen")
                )
            }
        } else if (!hasLocalPackageFor(pkg)) {
            val diskDownload = downloadPackageToDiskOnly(regionId = pkg.regionId, forceRefresh = false)
            if (diskDownload.isFailure) {
                return@withContext Result.failure(
                    diskDownload.exceptionOrNull()
                        ?: IllegalStateException("Download für ${pkg.displayName} fehlgeschlagen")
                )
            }
        }

        val bundle = ensurePackageLoaded(pkg, allowNetworkDownload = false)
            ?: return@withContext Result.failure(
                IllegalStateException("JSON-Datenpaket für ${pkg.displayName} konnte nicht verarbeitet werden (evtl. OutOfMemoryError / ungültiges Format).")
            )
        Result.success(bundle.routes.size to bundle.stations.size)
    }

    fun tileKeyFor(center: GeoPoint, needRail: Boolean, needBus: Boolean): String {
        val latStep = 0.08
        val lonStep = 0.12
        val latCell = floor(center.latitude / latStep).toInt()
        val lonCell = floor(center.longitude / lonStep).toInt()
        val scope = when {
            needRail && needBus -> "all_v3"
            needBus -> "busonly_v3"
            else -> "rail_v2"
        }
        return "${scope}_${latCell}_${lonCell}"
    }

    private fun busSupplementKeyFor(center: GeoPoint): String {
        val latCell = floor(center.latitude / 0.055).toInt()
        val lonCell = floor(center.longitude / 0.080).toInt()
        return "bus_sup_v3_${latCell}_${lonCell}"
    }

    private fun busStopsSupplementKeyFor(center: GeoPoint): String {
        val latCell = floor(center.latitude / 0.055).toInt()
        val lonCell = floor(center.longitude / 0.080).toInt()
        return "bus_stops_v3_${latCell}_${lonCell}"
    }

    /**
     * Instant zero-I/O in-memory RAM cache lookup to prevent redundant reloads on small pans.
     */
    fun getRamCachedOverlay(
        center: GeoPoint,
        needsBuses: Boolean,
        requiredOfflineRegionId: String? = null
    ): ViewportTransitResult? {
        val now = System.currentTimeMillis()
        synchronized(ramCacheLock) {
            for (i in ramCache.indices.reversed()) {
                val entry = ramCache[i]
                if (now - entry.cachedAt >= ttlMillis) continue
                if (needsBuses && !entry.includesBuses) continue
                if (requiredOfflineRegionId != null && entry.fromOfflineRegionId != requiredOfflineRegionId) continue
                if (entry.bounds.contains(center)) {
                    return ViewportTransitResult(
                        overlay = entry.overlay,
                        fromOfflineRegionId = entry.fromOfflineRegionId,
                        fromLocalCache = true
                    )
                }
            }
        }
        return null
    }

    private fun putRamCache(
        bounds: MapBounds,
        includesBuses: Boolean,
        fromOfflineRegionId: String?,
        overlay: DynamicTransitOverlay
    ) {
        val entry = RamCachedOverlay(
            bounds = bounds,
            includesBuses = includesBuses,
            fromOfflineRegionId = fromOfflineRegionId,
            overlay = overlay,
            cachedAt = System.currentTimeMillis()
        )
        synchronized(ramCacheLock) {
            if (ramCache.size >= MAX_RAM_CACHE_ENTRIES) {
                ramCache.removeAt(0)
            }
            ramCache.add(entry)
        }
    }

    private fun bufferedBoundsAround(center: GeoPoint, includesBuses: Boolean): MapBounds {
        val halfLat = if (includesBuses) 0.010 else 0.036
        val halfLon = if (includesBuses) 0.015 else 0.052
        return MapBounds(
            south = center.latitude - halfLat,
            west = center.longitude - halfLon,
            north = center.latitude + halfLat,
            east = center.longitude + halfLon
        )
    }

    /**
     * Fast Room-only check (zero network): returns the saved Offline Region overlay if [center]
     * lies inside any downloaded Offline Region with valid non-empty transit data.
     *
     * When [needsBuses] is true and the device is online, returns null if the Offline Region pack
     * contains zero bus routes near [center] (e.g. an older Munich+Freising pack missing Freising
     * buses) AND no supplementary local bus tile exists — allowing [loadViewportTransit] to fetch
     * and merge only the missing regional bus lines seamlessly.
     */
    suspend fun loadCoveringOfflineRegionOverlay(
        center: GeoPoint,
        needsBuses: Boolean = false
    ): Pair<String, DynamicTransitOverlay>? = withContext(Dispatchers.IO) {
        // STRICT REGIONAL PACKAGE PRIORITY: Inside any configured regional package, serve exclusively from local JSON!
        val regionalPkg = findPackageForPoint(center)
        if (regionalPkg != null) {
            val bundle = ensurePackageLoaded(regionalPkg, allowNetworkDownload = false) ?: return@withContext null
            val overlay = buildRegionalOverlay(bundle, center)
            if (overlay.hasAnyFeatures()) {
                putRamCache(
                    bounds = bufferedBoundsAround(center, includesBuses = true),
                    includesBuses = true,
                    fromOfflineRegionId = regionalPkg.localOverlayRegionId,
                    overlay = overlay
                )
                return@withContext regionalPkg.localOverlayRegionId to overlay
            }
            return@withContext null
        }

        val offlineRegions = try {
            offlineRegionDao.getRegionsContaining(center.latitude, center.longitude)
        } catch (_: Exception) {
            emptyList()
        }
        val online = NetworkConnectivityHelper.isOnline(context)
        val now = System.currentTimeMillis()

        for (region in offlineRegions) {
            val pinned = tileDao.getTile(region.transitCacheKey) ?: continue
            if (!OverpassStreamCodec.hasUsableCachedData(pinned.payloadJson)) continue
            val routes = OverpassStreamCodec.parseCachedRoutesList(pinned.payloadJson, maxRoutes = 1200)
            if (routes.isEmpty()) continue

            val offlineBusStops = loadNearbyOfflineBusStops(region.stopsCacheKey, center)
            val hasNearbyBuses = OverpassStreamCodec.hasBusRouteNear(routes, center, maxDistKm = 6.5)

            if (!needsBuses || hasNearbyBuses) {
                val overlay = OverpassStreamCodec.buildDynamicOverlay(routes, center, offlineBusStops)
                if (overlay.hasAnyFeatures()) {
                    val ramBounds = if (needsBuses) {
                        bufferedBoundsAround(center, includesBuses = true)
                    } else {
                        MapBounds(region.south, region.west, region.north, region.east)
                    }
                    putRamCache(ramBounds, includesBuses = hasNearbyBuses, fromOfflineRegionId = region.id, overlay = overlay)
                    return@withContext region.id to overlay
                }
            } else {
                // Offline region lacks nearby buses (e.g. Freising in an older pack): check local bus supplement first
                val supBusRoutes = loadCachedSupplementBusRoutes(center, now, online)
                val supBusStops = loadCachedSupplementBusStops(center, now, online)
                if (supBusRoutes.isNotEmpty() || !online) {
                    val mergedOverlay = OverpassStreamCodec.buildDynamicOverlay(
                        allRoutes = routes + supBusRoutes,
                        focusCenter = center,
                        extraBusStops = offlineBusStops + supBusStops
                    )
                    if (mergedOverlay.hasAnyFeatures()) {
                        putRamCache(
                            bounds = bufferedBoundsAround(center, includesBuses = supBusRoutes.isNotEmpty()),
                            includesBuses = supBusRoutes.isNotEmpty(),
                            fromOfflineRegionId = region.id,
                            overlay = mergedOverlay
                        )
                        return@withContext region.id to mergedOverlay
                    }
                }
                // Online & no local bus data near center -> return null so loadViewportTransit supplements buses
                return@withContext null
            }
        }
        null
    }

    /**
     * Fast Room-only check (zero network): returns true if [center] has a valid local cache
     * (RAM cache, covering Offline Region with required bus coverage, or fresh Room tile).
     */
    suspend fun hasInstantLocalCache(
        center: GeoPoint,
        needRail: Boolean,
        needBus: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val regionalPkg = findPackageForPoint(center)
        if (regionalPkg != null) {
            return@withContext hasLocalPackageFor(regionalPkg)
        }

        if (getRamCachedOverlay(center, needBus) != null) return@withContext true

        val now = System.currentTimeMillis()
        val online = NetworkConnectivityHelper.isOnline(context)
        val regions = try {
            offlineRegionDao.getRegionsContaining(center.latitude, center.longitude)
        } catch (_: Exception) {
            emptyList()
        }
        for (region in regions) {
            val pinned = tileDao.getTile(region.transitCacheKey)
            if (pinned != null && OverpassStreamCodec.hasUsableCachedData(pinned.payloadJson)) {
                if (!needBus || !online) return@withContext true
                val routes = OverpassStreamCodec.parseCachedRoutesList(pinned.payloadJson, maxRoutes = 1200)
                if (OverpassStreamCodec.hasBusRouteNear(routes, center, maxDistKm = 6.5)) {
                    return@withContext true
                }
                if (loadCachedSupplementBusRoutes(center, now, online).isNotEmpty()) {
                    return@withContext true
                }
                return@withContext false
            }
        }
        val key = tileKeyFor(center, needRail, needBus)
        val tile = tileDao.getTile(key)
        tile != null &&
            OverpassStreamCodec.hasUsableCachedData(tile.payloadJson) &&
            (now - tile.fetchedAt < ttlMillis || !online)
    }

    /**
     * Strict Offline-First & Streaming Cache-First viewport loader:
     *  0. In-memory RAM LRU cache -> instant zero-I/O return!
     *  1. Downloaded Offline Regions covering [center] -> instant local load (or targeted 1-request
     *     bus supplement only when an older pack is missing regional buses like Freising).
     *  2. Exact non-empty tile in Room cache -> instant local load via streaming JsonReader.
     *  3. Mode-selective Overpass streaming (skips rail query in BUS_ONLY; combines bus routes + stops
     *     in 1 HTTP request; fast 14s failover across mirrors; coroutine-cancellable!).
     */
    suspend fun loadViewportTransit(
        center: GeoPoint,
        zoom: Double,
        needRail: Boolean,
        includeBuses: Boolean,
        busOnlyMode: Boolean = false
    ): ViewportTransitResult = withContext(Dispatchers.IO) {
        if (zoom < MIN_RAIL_ZOOM) return@withContext ViewportTransitResult(overlay = null)

        // STRICT REGIONAL PACKAGE OFFLINE PRIORITY: Inside any configured regional package, NEVER query Overpass!
        val regionalPkg = findPackageForPoint(center)
        if (regionalPkg != null) {
            getRamCachedOverlay(
                center = center,
                needsBuses = true,
                requiredOfflineRegionId = regionalPkg.localOverlayRegionId
            )?.let { return@withContext it }
            val bundle = ensurePackageLoaded(regionalPkg, allowNetworkDownload = true)
            if (bundle != null) {
                val overlay = buildRegionalOverlay(bundle, center)
                if (overlay.hasAnyFeatures()) {
                    putRamCache(
                        bounds = bufferedBoundsAround(center, includesBuses = true),
                        includesBuses = true,
                        fromOfflineRegionId = regionalPkg.localOverlayRegionId,
                        overlay = overlay
                    )
                    return@withContext ViewportTransitResult(
                        overlay = overlay,
                        fromOfflineRegionId = regionalPkg.localOverlayRegionId,
                        fromLocalCache = true,
                        networkWarningHint = null
                    )
                }
            }
            val onlineNow = NetworkConnectivityHelper.isOnline(context)
            return@withContext ViewportTransitResult(
                overlay = null,
                fromLocalCache = false,
                networkWarningHint = if (onlineNow) {
                    "${regionalPkg.displayName}-Datenpaket konnte nicht heruntergeladen werden – bitte später erneut versuchen."
                } else {
                    null
                }
            )
        }

        val fetchBuses = includeBuses && zoom >= minBusZoomFor(busOnlyMode)
        val fetchRail = needRail
        if (!fetchRail && !fetchBuses) return@withContext ViewportTransitResult(overlay = null)

        val key = tileKeyFor(center, fetchRail, fetchBuses)
        val railKey = tileKeyFor(center, needRail = true, needBus = false)
        val now = System.currentTimeMillis()
        val online = NetworkConnectivityHelper.isOnline(context)

        // 0. PRIORITY 0: Instant RAM cache (eliminates redundant reloads on small pans)
        getRamCachedOverlay(center, fetchBuses)?.let { return@withContext it }

        // 1. PRIORITY 1: Saved Offline Regions covering this coordinate
        val offlineRegions = try {
            offlineRegionDao.getRegionsContaining(center.latitude, center.longitude)
        } catch (_: Exception) {
            emptyList()
        }
        for (region in offlineRegions) {
            val pinned = tileDao.getTile(region.transitCacheKey) ?: continue
            if (!OverpassStreamCodec.hasUsableCachedData(pinned.payloadJson)) continue
            val offlineRoutes = OverpassStreamCodec.parseCachedRoutesList(pinned.payloadJson, maxRoutes = 1200)
            if (offlineRoutes.isEmpty()) continue

            val offlineBusStops = loadNearbyOfflineBusStops(region.stopsCacheKey, center)
            val hasNearbyBuses = OverpassStreamCodec.hasBusRouteNear(offlineRoutes, center, maxDistKm = 6.5)

            if (!fetchBuses || hasNearbyBuses) {
                val overlay = OverpassStreamCodec.buildDynamicOverlay(offlineRoutes, center, offlineBusStops)
                if (overlay.hasAnyFeatures()) {
                    val ramBounds = if (fetchBuses) {
                        bufferedBoundsAround(center, includesBuses = true)
                    } else {
                        MapBounds(region.south, region.west, region.north, region.east)
                    }
                    putRamCache(ramBounds, includesBuses = hasNearbyBuses, fromOfflineRegionId = region.id, overlay = overlay)
                    return@withContext ViewportTransitResult(
                        overlay = overlay,
                        fromOfflineRegionId = region.id,
                        fromLocalCache = true
                    )
                }
            } else {
                // Offline region has rail lines but lacks bus routes near `center` (e.g. Freising)
                val cachedSupBus = loadCachedSupplementBusRoutes(center, now, online)
                val cachedSupStops = loadCachedSupplementBusStops(center, now, online)
                if (cachedSupBus.isNotEmpty() || !online) {
                    val overlay = OverpassStreamCodec.buildDynamicOverlay(
                        allRoutes = offlineRoutes + cachedSupBus,
                        focusCenter = center,
                        extraBusStops = offlineBusStops + cachedSupStops
                    )
                    putRamCache(
                        bounds = bufferedBoundsAround(center, includesBuses = cachedSupBus.isNotEmpty()),
                        includesBuses = cachedSupBus.isNotEmpty(),
                        fromOfflineRegionId = region.id,
                        overlay = overlay
                    )
                    return@withContext ViewportTransitResult(
                        overlay = overlay,
                        fromOfflineRegionId = region.id,
                        fromLocalCache = true
                    )
                }

                // Online: fetch ONLY the missing regional bus routes + bus stops around `center` and merge!
                val fetchedBus = fetchFocusedBusRoutesAndStops(center, dedupKeys = HashSet())
                val fetchedRoutes = fetchedBus.first.orEmpty()
                val fetchedStops = fetchedBus.second
                if (fetchedRoutes.isNotEmpty()) {
                    saveBusSupplementToRoom(center, fetchedRoutes, fetchedStops, now)
                }
                val mergedOverlay = OverpassStreamCodec.buildDynamicOverlay(
                    allRoutes = offlineRoutes + fetchedRoutes,
                    focusCenter = center,
                    extraBusStops = offlineBusStops + fetchedStops
                )
                putRamCache(
                    bounds = bufferedBoundsAround(center, includesBuses = fetchedRoutes.isNotEmpty()),
                    includesBuses = fetchedRoutes.isNotEmpty(),
                    fromOfflineRegionId = region.id,
                    overlay = mergedOverlay
                )
                val warn = if (fetchedBus.first == null) {
                    "Regionale Buslinien wegen API-Zeitlimit nicht geladen – Offline-Bahnnetz bleibt aktiv."
                } else {
                    null
                }
                return@withContext ViewportTransitResult(
                    overlay = mergedOverlay,
                    fromOfflineRegionId = region.id,
                    fromLocalCache = fetchedRoutes.isEmpty(),
                    networkWarningHint = warn
                )
            }
        }

        // 2. PRIORITY 2: Exact Room tile cache (self-healing: purge poisoned/empty cached tiles)
        val exactTile = tileDao.getTile(key)
        val validExactTile = exactTile?.takeIf { OverpassStreamCodec.hasUsableCachedData(it.payloadJson) }
        if (exactTile != null && validExactTile == null) {
            try {
                tileDao.deleteByKey(key)
            } catch (_: Exception) {
                // Ignore
            }
        }
        if (validExactTile != null && (now - validExactTile.fetchedAt < ttlMillis || !online)) {
            val cachedRoutes = OverpassStreamCodec.parseCachedRoutesList(validExactTile.payloadJson, maxRoutes = 1200)
            val cachedStops = if (fetchBuses) loadCachedSupplementBusStops(center, now, online) else emptyList()
            val needsBusRefresh = fetchBuses && online && !OverpassStreamCodec.hasBusRouteNear(cachedRoutes, center, maxDistKm = 6.5)
            if (cachedRoutes.isNotEmpty() && !needsBusRefresh) {
                val parsed = OverpassStreamCodec.buildDynamicOverlay(cachedRoutes, center, cachedStops)
                if (parsed.hasAnyFeatures()) {
                    putRamCache(
                        bounds = bufferedBoundsAround(center, includesBuses = fetchBuses),
                        includesBuses = fetchBuses,
                        fromOfflineRegionId = null,
                        overlay = parsed
                    )
                    return@withContext ViewportTransitResult(overlay = parsed, fromLocalCache = true)
                }
            }
        }

        // 3. Offline & not cached: return stale valid tile if present, never hang on network timeout
        if (!online) {
            val fallbackTile = validExactTile
                ?: tileDao.getTile(railKey)?.takeIf { OverpassStreamCodec.hasUsableCachedData(it.payloadJson) }
            val parsedFallback = fallbackTile?.let {
                OverpassStreamCodec.parseCachedRoutesToOverlay(it.payloadJson, center)
            }
            return@withContext ViewportTransitResult(
                overlay = parsedFallback,
                fromLocalCache = parsedFallback != null
            )
        }

        // 4. Online & not cached: stream ONLY the needed Overpass buckets directly via JsonReader!
        val latStep = 0.08
        val lonStep = 0.12
        val south = floor(center.latitude / latStep) * latStep - 0.01
        val north = south + latStep + 0.02
        val west = floor(center.longitude / lonStep) * lonStep - 0.015
        val east = west + lonStep + 0.03
        val railBbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", south, west, north, east)

        val dedupKeys = HashSet<String>()

        // Rail query: ONLY when rail layers are needed (skipped in BUS_ONLY mode -> half latency!)
        val railRoutes = if (fetchRail) {
            val cachedRailTile = tileDao.getTile(railKey)?.takeIf {
                OverpassStreamCodec.hasUsableCachedData(it.payloadJson) && now - it.fetchedAt < ttlMillis
            }
            if (cachedRailTile != null) {
                OverpassStreamCodec.parseCachedRoutesList(cachedRailTile.payloadJson, maxRoutes = 200)
            } else {
                val railQuery = buildString {
                    append("[out:json][timeout:10][maxsize:12582912];")
                    append("relation[\"type\"=\"route\"][\"route\"~\"^(light_rail|subway|train)$\"]($railBbox);")
                    append("out geom($railBbox) qt 45;")
                }
                executeStreamingQuery(railQuery) { stream ->
                    val res = OverpassStreamCodec.streamOverpassRoutes(stream, dedupKeys, maxRoutes = 80)
                    if (res.hadRuntimeError) null else res.routes
                }
            }
        } else {
            // Preserve any cached rail routes for smooth mode switching without doing a rail network call
            tileDao.getTile(railKey)
                ?.takeIf { OverpassStreamCodec.hasUsableCachedData(it.payloadJson) }
                ?.let { OverpassStreamCodec.parseCachedRoutesList(it.payloadJson, maxRoutes = 200) }
                ?: emptyList()
        }

        // Worldwide, operator-agnostic Bus queries (strictly separated: A = routes, B = fast stops)
        val busFetchResult = if (fetchBuses) {
            fetchFocusedBusRoutesAndStops(center, dedupKeys)
        } else {
            null to emptyList()
        }
        val busRoutes = busFetchResult.first
        val busStops = busFetchResult.second

        val railFailed = fetchRail && railRoutes == null
        val busFailed = fetchBuses && busRoutes == null

        // If ALL attempted queries failed AND no bus stops came back, NEVER poison the cache!
        if ((!fetchRail || railFailed) && (!fetchBuses || (busFailed && busStops.isEmpty()))) {
            val staleOverlay = validExactTile?.let {
                OverpassStreamCodec.parseCachedRoutesToOverlay(it.payloadJson, center)
            }
            return@withContext ViewportTransitResult(
                overlay = staleOverlay,
                fromLocalCache = staleOverlay != null,
                networkWarningHint = "ÖPNV-Server überlastet (Timeout) – bestehende Daten bleiben erhalten."
            )
        }

        val combinedRoutes = buildList {
            railRoutes?.let { addAll(it) }
            busRoutes?.let { addAll(it) }
        }

        if (combinedRoutes.isEmpty() && busStops.isEmpty()) {
            val staleOverlay = validExactTile?.let {
                OverpassStreamCodec.parseCachedRoutesToOverlay(it.payloadJson, center)
            }
            val hint = if (busFailed || railFailed) {
                "ÖPNV-Abfrage teilweise fehlgeschlagen – alte Daten bleiben erhalten."
            } else {
                null
            }
            return@withContext ViewportTransitResult(
                overlay = staleOverlay,
                fromLocalCache = staleOverlay != null,
                networkWarningHint = hint
            )
        }

        val overlay = OverpassStreamCodec.buildDynamicOverlay(combinedRoutes, center, busStops)
        val hasActualBusLines = fetchBuses && !busFailed && !busRoutes.isNullOrEmpty()
        putRamCache(
            bounds = bufferedBoundsAround(center, includesBuses = hasActualBusLines),
            includesBuses = hasActualBusLines,
            fromOfflineRegionId = null,
            overlay = overlay
        )

        // Write ULTRA-COMPACT serialized routes/stops to Room ONLY with verified non-empty data
        try {
            tileDao.pruneExpired(now - ttlMillis)
            if (busStops.isNotEmpty()) {
                val compactStops = OverpassStreamCodec.serializeCompactStations(busStops)
                tileDao.upsert(CachedOverpassTileEntity(tileKey = busStopsSupplementKeyFor(center), payloadJson = compactStops, fetchedAt = now))
            }
            if (fetchBuses && !busFailed && combinedRoutes.isNotEmpty()) {
                val compactJson = OverpassStreamCodec.serializeCompactRoutes(combinedRoutes)
                tileDao.upsert(CachedOverpassTileEntity(tileKey = key, payloadJson = compactJson, fetchedAt = now))
            } else if (!fetchBuses && !railRoutes.isNullOrEmpty()) {
                val compactJson = OverpassStreamCodec.serializeCompactRoutes(railRoutes)
                tileDao.upsert(CachedOverpassTileEntity(tileKey = railKey, payloadJson = compactJson, fetchedAt = now))
            } else if (fetchBuses && busFailed && !railRoutes.isNullOrEmpty()) {
                // Save rail tile safely without poisoning the bus tile!
                val compactJson = OverpassStreamCodec.serializeCompactRoutes(railRoutes)
                tileDao.upsert(CachedOverpassTileEntity(tileKey = railKey, payloadJson = compactJson, fetchedAt = now))
            }
        } catch (_: Exception) {
            // Non-fatal cache write failure
        }

        val warning = when {
            busFailed && busStops.isNotEmpty() -> "Buslinien verzögert – Bushaltestellen wurden geladen."
            busFailed -> "Buslinien wegen API-Zeitlimit nicht vollständig geladen – tippe später auf „Cache neu laden“."
            railFailed -> "Bahnlinien wegen API-Zeitlimit verzögert – Buslinien geladen."
            else -> null
        }

        ViewportTransitResult(
            overlay = overlay,
            fromLocalCache = false,
            networkWarningHint = warning
        )
    }

    /**
     * Strictly separates bus route relations (Query A) and bus stop nodes (Query B) so a timeout
     * on route relations never prevents bus stops from appearing immediately on the map.
     */
    private suspend fun fetchFocusedBusRoutesAndStops(
        center: GeoPoint,
        dedupKeys: MutableSet<String>
    ): Pair<List<ParsedTransitRoute>?, List<TransitStation>> {
        val bSouth = center.latitude - 0.016
        val bNorth = center.latitude + 0.016
        val bWest = center.longitude - 0.024
        val bEast = center.longitude + 0.024
        val busBbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", bSouth, bWest, bNorth, bEast)

        // Query A: Bus route relations only (strict server-side clipping & qt 25 limit)
        val routesQuery = buildString {
            append("[out:json][timeout:10][maxsize:8388608];")
            append("relation[\"type\"=\"route\"][\"route\"=\"bus\"]($busBbox);")
            append("out geom($busBbox) qt 25;")
        }
        val routes = executeStreamingQuery(routesQuery) { stream ->
            val res = OverpassStreamCodec.streamOverpassRoutes(
                inputStream = stream,
                dedupKeys = dedupKeys,
                maxRoutes = 25
            )
            if (res.hadRuntimeError) null else res.routes
        }

        // Brief delay to avoid Overpass rate-limiting between consecutive queries
        delay(450)

        // Query B: Ultra-fast bus stops query (always executed so stops appear even if Query A fails)
        val stopsQuery = buildString {
            append("[out:json][timeout:8];")
            append("node[\"highway\"=\"bus_stop\"]($busBbox);")
            append("out body qt 200;")
        }
        val stops = executeStreamingQuery(stopsQuery) { stream ->
            val res = OverpassStreamCodec.streamOverpassStations(
                inputStream = stream,
                mode = RouteTransitChoice.BUS,
                maxStations = 200
            )
            if (res.hadRuntimeError) null else res.stations
        }.orEmpty()

        Log.d("OverpassTransit", "Bus fetch result: routes=${routes?.size}, stops=${stops.size}")
        return routes to stops
    }

    private suspend fun loadNearbyOfflineBusStops(stopsCacheKey: String, center: GeoPoint): List<TransitStation> {
        val tile = tileDao.getTile(stopsCacheKey) ?: return emptyList()
        if (!OverpassStreamCodec.hasUsableCachedData(tile.payloadJson)) return emptyList()
        return OverpassStreamCodec.parseCachedStations(tile.payloadJson, RouteTransitChoice.BUS)
            .orEmpty()
            .filter { GeoDistanceUtils.haversineKm(it.location, center) <= 6.5 }
    }

    private suspend fun loadCachedSupplementBusRoutes(
        center: GeoPoint,
        now: Long,
        online: Boolean
    ): List<ParsedTransitRoute> {
        val tile = tileDao.getTile(busSupplementKeyFor(center)) ?: return emptyList()
        if (!OverpassStreamCodec.hasUsableCachedData(tile.payloadJson)) return emptyList()
        if (online && now - tile.fetchedAt >= ttlMillis) return emptyList()
        return OverpassStreamCodec.parseCachedRoutesList(tile.payloadJson, maxRoutes = 200)
            .filter { it.bucket == TransitBucket.BUS }
    }

    private suspend fun loadCachedSupplementBusStops(
        center: GeoPoint,
        now: Long,
        online: Boolean
    ): List<TransitStation> {
        val tile = tileDao.getTile(busStopsSupplementKeyFor(center)) ?: return emptyList()
        if (!OverpassStreamCodec.hasUsableCachedData(tile.payloadJson)) return emptyList()
        if (online && now - tile.fetchedAt >= ttlMillis) return emptyList()
        return OverpassStreamCodec.parseCachedStations(tile.payloadJson, RouteTransitChoice.BUS).orEmpty()
    }

    private suspend fun saveBusSupplementToRoom(
        center: GeoPoint,
        busRoutes: List<ParsedTransitRoute>,
        busStops: List<TransitStation>,
        now: Long
    ) {
        try {
            if (busRoutes.isNotEmpty()) {
                val compactRoutes = OverpassStreamCodec.serializeCompactRoutes(busRoutes)
                tileDao.upsert(CachedOverpassTileEntity(busSupplementKeyFor(center), compactRoutes, now))
            }
            if (busStops.isNotEmpty()) {
                val compactStops = OverpassStreamCodec.serializeCompactStations(busStops)
                tileDao.upsert(CachedOverpassTileEntity(busStopsSupplementKeyFor(center), compactStops, now))
            }
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    /**
     * Clears in-memory RAM cache, deletes all locally stored regional transit JSON bundles
     * (including `mvv_transit.json` and any `.tmp` files), and clears temporary Room tile caches.
     */
    suspend fun clearTransitCache() = withContext(Dispatchers.IO) {
        clearRamCache()
        for (pkg in REGIONAL_PACKAGES.values) {
            mutexForRegion(pkg.regionId).withLock {
                removeCachedParsedBundle(pkg.regionId)
                try {
                    packageFile(pkg).delete()
                    packageTmpFile(pkg).delete()
                    packageLegacyFile(pkg)?.delete()
                    pkg.legacyFileName?.let { File(context.filesDir, "$it.tmp").delete() }
                } catch (_: Exception) {
                    // Non-fatal
                }
            }
        }
        try {
            tileDao.clearTemporaryTiles()
            tileDao.purgeEmptyTiles()
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    /**
     * Forces a fresh network download (`CacheControl.FORCE_NETWORK`) of the active regional package
     * (deleting its local `transit_<regionId>.json`, `.tmp`, RAM cache, and temporary Room tiles)
     * while preserving already-cached JSON packages of other regions on disk.
     */
    suspend fun refreshMvvBundleNow(regionId: String = "MVV"): Result<Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            clearRamCache()
            try {
                tileDao.clearTemporaryTiles()
                tileDao.purgeEmptyTiles()
            } catch (_: Exception) {
                // Non-fatal
            }
            val targetId = if (getPackageForRegionId(regionId) != null) regionId else "MVV"
            downloadRegionalPackageIfAvailable(targetId, forceRefresh = true)
                ?: Result.failure(IllegalStateException("Kein Offline-Datenpaket für Region $targetId konfiguriert."))
        }

    fun clearRamCache() {
        synchronized(ramCacheLock) {
            ramCache.clear()
        }
    }

    /**
     * Strict Offline-First & Streaming Cache-First station query around [center] for [mode].
     *
     * Inside regional packages (e.g. `mvv_transit.json`), stations are filtered STRICTLY by bucket
     * and line ref:
     * - [RouteTransitChoice.SBAHN]: ONLY bucket 0 (`TransitBucket.SBAHN`) with S-Bahn ref (`^S\d+`)
     * - [RouteTransitChoice.BUS]: ONLY bucket 1 (`TransitBucket.BUS`)
     * - [RouteTransitChoice.REGIO_DB]: ONLY bucket 2 (`TransitBucket.REGIO_LONG`)
     * Never mixes other transit modes into the returned list.
     */
    suspend fun queryNearbyStations(
        center: GeoPoint,
        mode: RouteTransitChoice
    ): List<TransitStation> = withContext(Dispatchers.IO) {
        // STRICT REGIONAL PACKAGE PRIORITY: Inside any configured regional package, serve stations directly from RAM!
        val regionalPkg = findPackageForPoint(center)
        if (regionalPkg != null) {
            val bundle = ensurePackageLoaded(regionalPkg, allowNetworkDownload = true) ?: return@withContext emptyList()
            val strictCandidates = when (mode) {
                RouteTransitChoice.SBAHN -> bundle.sBahnStations
                RouteTransitChoice.BUS -> bundle.busStations
                RouteTransitChoice.REGIO_DB -> bundle.regioStations
            }
            val maxRadiusKm = when (mode) {
                RouteTransitChoice.BUS -> 6.0
                RouteTransitChoice.SBAHN -> 14.0
                RouteTransitChoice.REGIO_DB -> 25.0
            }
            val matchingWithDist = strictCandidates
                .asSequence()
                .filter { mode.matchesStation(it) }
                .map { station -> station to GeoDistanceUtils.haversineKm(station.location, center) }
                .sortedBy { it.second }
                .toList()

            val withinRadius = matchingWithDist
                .filter { it.second <= maxRadiusKm }
                .take(80)
                .map { it.first }

            return@withContext if (withinRadius.size >= 5) {
                withinRadius
            } else {
                matchingWithDist.take(25).map { it.first }
            }
        }

        val latStep = if (mode == RouteTransitChoice.BUS) 0.015 else 0.025
        val lonStep = if (mode == RouteTransitChoice.BUS) 0.020 else 0.035
        val latCell = floor(center.latitude / latStep).toInt()
        val lonCell = floor(center.longitude / lonStep).toInt()
        val cacheKey = "stops_v3_${mode.name}_${latCell}_${lonCell}"
        val now = System.currentTimeMillis()
        val online = NetworkConnectivityHelper.isOnline(context)

        // 1. PRIORITY 1: Pinned Offline Regions covering this point (zero network!)
        val offlineRegions = try {
            offlineRegionDao.getRegionsContaining(center.latitude, center.longitude)
        } catch (_: Exception) {
            emptyList()
        }
        for (region in offlineRegions) {
            val pinnedStops = tileDao.getTile(region.stopsCacheKey)
            if (pinnedStops != null && OverpassStreamCodec.hasUsableCachedData(pinnedStops.payloadJson)) {
                val parsedOffline = OverpassStreamCodec.parseCachedStations(pinnedStops.payloadJson, mode).orEmpty()
                val nearbyOffline = parsedOffline.filter {
                    mode.matchesStation(it) &&
                        GeoDistanceUtils.haversineKm(it.location, center) <= (if (mode == RouteTransitChoice.BUS) 5.0 else 14.0)
                }
                if (nearbyOffline.isNotEmpty()) {
                    return@withContext nearbyOffline
                }
            }
        }

        // 2. PRIORITY 2: Exact Room stops cache (ignore empty poisoned rows)
        val cachedTile = tileDao.getTile(cacheKey)?.takeIf {
            OverpassStreamCodec.hasUsableCachedData(it.payloadJson)
        }
        if (cachedTile != null && (now - cachedTile.fetchedAt < ttlMillis || !online)) {
            val parsedCached = OverpassStreamCodec.parseCachedStations(cachedTile.payloadJson, mode)
                ?.filter { mode.matchesStation(it) }
            if (!parsedCached.isNullOrEmpty()) {
                return@withContext parsedCached
            }
        }

        // 3. Offline & not cached: never hang waiting for Overpass
        if (!online) {
            cachedTile?.let {
                OverpassStreamCodec.parseCachedStations(it.payloadJson, mode)
                    ?.filter { st -> mode.matchesStation(st) }
                    ?.let { list -> return@withContext list }
            }
            return@withContext emptyList()
        }

        // 4. Online & not cached: stream lightweight 'out center tags qt;' station query directly!
        val qLat = (latCell + 0.5) * latStep
        val qLon = (lonCell + 0.5) * lonStep
        val latStr = String.format(Locale.US, "%.5f", qLat)
        val lonStr = String.format(Locale.US, "%.5f", qLon)

        val query = when (mode) {
            RouteTransitChoice.SBAHN -> buildString {
                append("[out:json][timeout:10][maxsize:8388608];(")
                append("node[\"railway\"~\"^(station|halt)$\"](around:7500,$latStr,$lonStr);")
                append("way[\"railway\"~\"^(station|halt)$\"](around:7500,$latStr,$lonStr);")
                append("node[\"station\"=\"light_rail\"](around:7500,$latStr,$lonStr);")
                append(");out center tags qt;")
            }
            RouteTransitChoice.BUS -> buildString {
                append("[out:json][timeout:10][maxsize:8388608];(")
                append("node[\"highway\"=\"bus_stop\"](around:3200,$latStr,$lonStr);")
                append("node[\"public_transport\"~\"^(platform|stop_position)$\"][\"bus\"=\"yes\"](around:3200,$latStr,$lonStr);")
                append("node[\"public_transport\"=\"station\"][\"bus\"=\"yes\"](around:3200,$latStr,$lonStr);")
                append(");out center tags qt;")
            }
            RouteTransitChoice.REGIO_DB -> buildString {
                append("[out:json][timeout:10][maxsize:8388608];(")
                append("node[\"railway\"~\"^(station|halt)$\"](around:16000,$latStr,$lonStr);")
                append("way[\"railway\"~\"^(station|halt)$\"](around:16000,$latStr,$lonStr);")
                append(");out center tags qt;")
            }
        }

        val streamedStations = executeStreamingQuery(query) { stream ->
            val res = OverpassStreamCodec.streamOverpassStations(stream, mode)
            if (res.hadRuntimeError) null else res.stations.filter { mode.matchesStation(it) }
        } ?: return@withContext cachedTile?.let {
            OverpassStreamCodec.parseCachedStations(it.payloadJson, mode)
                ?.filter { st -> mode.matchesStation(st) }
        }.orEmpty()

        // Never cache an empty stops result into Room!
        if (streamedStations.isNotEmpty()) {
            try {
                val compactStopsJson = OverpassStreamCodec.serializeCompactStations(streamedStations)
                tileDao.pruneExpired(now - ttlMillis)
                tileDao.upsert(
                    CachedOverpassTileEntity(
                        tileKey = cacheKey,
                        payloadJson = compactStopsJson,
                        fetchedAt = now
                    )
                )
            } catch (_: Exception) {
                // Non-fatal cache write failure
            }
        }

        streamedStations
    }

    /**
     * Strict map-tap station lookup respecting all 4 [MapTransitMode] filter states:
     * - [MapTransitMode.SBAHN_ONLY]: ONLY bucket 0 with `^S\d+`
     * - [MapTransitMode.BUS_ONLY]: ONLY bucket 1
     * - [MapTransitMode.REGIO_LONG_ONLY]: ONLY bucket 2
     * - [MapTransitMode.ALL]: All buckets allowed (including U-Bahn/Tram/Bus/S-Bahn/Regio)
     */
    suspend fun queryNearbyStationsForMapMode(
        center: GeoPoint,
        transitMode: MapTransitMode
    ): List<TransitStation> = withContext(Dispatchers.IO) {
        if (transitMode != MapTransitMode.ALL) {
            val strictMode = transitMode.toRouteChoiceOrNull() ?: return@withContext emptyList()
            return@withContext queryNearbyStations(center, strictMode)
        }

        // MapTransitMode.ALL -> all buckets allowed
        val regionalPkg = findPackageForPoint(center)
        if (regionalPkg != null) {
            val bundle = ensurePackageLoaded(regionalPkg, allowNetworkDownload = true) ?: return@withContext emptyList()
            return@withContext bundle.stations
                .asSequence()
                .map { st -> st to GeoDistanceUtils.haversineKm(st.location, center) }
                .sortedBy { it.second }
                .take(80)
                .map { it.first }
                .toList()
        }

        // Outside configured regional bundles, merge strict queries for all three modes
        val combined = buildList {
            addAll(queryNearbyStations(center, RouteTransitChoice.SBAHN))
            addAll(queryNearbyStations(center, RouteTransitChoice.BUS))
            addAll(queryNearbyStations(center, RouteTransitChoice.REGIO_DB))
        }
        combined.distinctBy { "${it.type}_${it.name.lowercase(Locale.GERMAN)}" }
    }

    /**
     * Zero-network local lookup of the closest station belonging to an ALTERNATIVE transit mode
     * (strictly excluding [excludedMode]). Used ONLY to compose an optional battery-range fallback
     * hint when the user's selected mode is out of remaining scooter battery range.
     */
    suspend fun findClosestAlternativeStationLocal(
        center: GeoPoint,
        excludedMode: RouteTransitChoice
    ): Pair<RouteTransitChoice, TransitStation>? = withContext(Dispatchers.IO) {
        val regionalPkg = findPackageForPoint(center) ?: return@withContext null
        val bundle = ensurePackageLoaded(regionalPkg, allowNetworkDownload = false) ?: return@withContext null

        var bestChoice: RouteTransitChoice? = null
        var bestStation: TransitStation? = null
        var bestDistKm = Double.MAX_VALUE

        for (candidateMode in RouteTransitChoice.entries) {
            if (candidateMode == excludedMode) continue
            val pool = when (candidateMode) {
                RouteTransitChoice.SBAHN -> bundle.sBahnStations
                RouteTransitChoice.BUS -> bundle.busStations
                RouteTransitChoice.REGIO_DB -> bundle.regioStations
            }
            val nearest = pool.minByOrNull { GeoDistanceUtils.haversineKm(it.location, center) } ?: continue
            val dist = GeoDistanceUtils.haversineKm(nearest.location, center)
            if (dist < bestDistKm) {
                bestDistKm = dist
                bestChoice = candidateMode
                bestStation = nearest
            }
        }
        if (bestChoice != null && bestStation != null) bestChoice to bestStation else null
    }

    /**
     * Downloads and permanently pins compact ÖPNV lines (S-Bahn, Bus, Regio/DB) AND stations/stops
     * for a user-chosen Offline Region into Room using direct HTTP streaming (tiny RAM footprint).
     */
    suspend fun downloadOfflineRegionTransit(
        regionId: String,
        bounds: MapBounds,
        onStatusUpdate: (String) -> Unit = {}
    ): OfflineTransitBundle = withContext(Dispatchers.IO) {
        val transitCacheKey = "offline_transit_$regionId"
        val stopsCacheKey = "offline_stops_$regionId"
        val now = System.currentTimeMillis()

        // Inside any configured regional package: reuse/download the pre-built JSON directly (zero Overpass!)
        val regionalPkg = findPackageForPoint(bounds.center())
        if (regionalPkg != null) {
            onStatusUpdate("ÖPNV-Netz wird verarbeitet & gespeichert… (${regionalPkg.displayName})")
            val bundle = ensurePackageLoaded(regionalPkg, allowNetworkDownload = true)
            if (bundle != null) {
                val stationsInBounds = bundle.stations.count { bounds.contains(it.location) }
                val busCount = bundle.routes.count { it.bucket == TransitBucket.BUS }
                val railCount = bundle.routes.count { it.bucket != TransitBucket.BUS }
                clearRamCache()
                return@withContext OfflineTransitBundle(
                    transitCacheKey = transitCacheKey,
                    stopsCacheKey = stopsCacheKey,
                    stationsCount = if (stationsInBounds > 0) stationsInBounds else bundle.stations.size,
                    busLinesCount = busCount,
                    railLinesCount = railCount,
                    isPartial = false,
                    totalSizeBytes = packageFile(regionalPkg).length().coerceAtLeast(1L)
                )
            }
            return@withContext OfflineTransitBundle(
                transitCacheKey = transitCacheKey,
                stopsCacheKey = stopsCacheKey,
                stationsCount = 0,
                isPartial = true,
                totalSizeBytes = 0L
            )
        }

        // Cap maximum region span so every Overpass sub-query stays fast and memory-safe
        val center = bounds.center()
        val halfLat = ((bounds.north - bounds.south) / 2.0).coerceIn(0.02, 0.22)
        val halfLon = ((bounds.east - bounds.west) / 2.0).coerceIn(0.03, 0.28)
        val south = center.latitude - halfLat
        val north = center.latitude + halfLat
        val west = center.longitude - halfLon
        val east = center.longitude + halfLon
        val fullBbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", south, west, north, east)

        val collectedRoutes = mutableListOf<ParsedTransitRoute>()
        val dedupKeys = HashSet<String>()
        var partialFailure = false

        // Sub-step A: Stream Rail routes (S-Bahn, U-Bahn, Tram, Regio/DB)
        onStatusUpdate("ÖPNV-Netz wird verarbeitet & gespeichert… (S-Bahn & Regio)")
        val railQuery = buildString {
            append("[out:json][timeout:30][maxsize:16777216];")
            append("relation[\"type\"=\"route\"][\"route\"~\"^(light_rail|subway|train)$\"]($fullBbox);")
            append("out geom($fullBbox) qt;")
        }
        val railRoutes = executeStreamingQuery(railQuery, useLongTimeout = true) { stream ->
            val res = OverpassStreamCodec.streamOverpassRoutes(stream, dedupKeys, maxRoutes = 110)
            if (res.hadRuntimeError) null else res.routes
        }
        if (railRoutes != null) {
            collectedRoutes.addAll(railRoutes)
        } else {
            partialFailure = true
        }

        // Sub-step B: Stream ALL Bus routes (city & regional, no operator filter) in subdivided sub-tiles
        val subBoxes = subdivideBoundsForOfflineQueries(south, west, north, east)
        var busSuccessCount = 0
        subBoxes.forEachIndexed { idx, box ->
            val tileNote = if (subBoxes.size > 1) "Buslinien ${idx + 1}/${subBoxes.size}" else "Buslinien"
            onStatusUpdate("ÖPNV-Netz wird verarbeitet & gespeichert… ($tileNote)")
            val boxStr = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", box.south, box.west, box.north, box.east)
            val busQuery = buildString {
                append("[out:json][timeout:30][maxsize:14680064];")
                append("relation[\"type\"=\"route\"][\"route\"=\"bus\"]($boxStr);")
                append("out geom($boxStr) qt;")
            }
            val tileBusRoutes = executeStreamingQuery(busQuery, useLongTimeout = true) { stream ->
                val res = OverpassStreamCodec.streamOverpassRoutes(stream, dedupKeys, maxRoutes = 180)
                if (res.hadRuntimeError) null else res.routes
            }
            if (tileBusRoutes != null) {
                collectedRoutes.addAll(tileBusRoutes)
                busSuccessCount++
            } else {
                partialFailure = true
            }
        }

        // Sub-step C: Stream Stations & Bus Stops (including public_transport=platform) in subdivided sub-tiles
        onStatusUpdate("ÖPNV-Netz wird verarbeitet & gespeichert… (Haltestellen)")
        val collectedStations = LinkedHashMap<String, TransitStation>()
        subBoxes.forEach { box ->
            val boxStr = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", box.south, box.west, box.north, box.east)
            val stopsQuery = buildString {
                append("[out:json][timeout:30][maxsize:10485760];(")
                append("node[\"railway\"~\"^(station|halt)$\"]($boxStr);")
                append("way[\"railway\"~\"^(station|halt)$\"]($boxStr);")
                append("node[\"public_transport\"=\"station\"]($boxStr);")
                append("node[\"station\"~\"^(subway|light_rail)$\"]($boxStr);")
                append("node[\"highway\"=\"bus_stop\"]($boxStr);")
                append("node[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"]($boxStr);")
                append(");out center tags qt;")
            }
            val tileStops = executeStreamingQuery(stopsQuery, useLongTimeout = true) { stream ->
                val res = OverpassStreamCodec.streamOverpassAllStations(stream, maxStations = 1500)
                if (res.hadRuntimeError) null else res.stations
            }
            if (tileStops != null) {
                for (st in tileStops) {
                    collectedStations.putIfAbsent("${st.type}_${st.name.lowercase(Locale.GERMAN)}", st)
                }
            } else {
                partialFailure = true
            }
        }

        onStatusUpdate("ÖPNV-Netz wird verarbeitet & gespeichert… (Speichern)")
        var totalBytes = 0L

        // STRICT EMPTY-CACHE GUARD: write compact Room payloads ONLY when non-empty!
        if (collectedRoutes.isNotEmpty()) {
            val compactRoutesJson = OverpassStreamCodec.serializeCompactRoutes(collectedRoutes)
            tileDao.upsert(CachedOverpassTileEntity(transitCacheKey, compactRoutesJson, now))
            totalBytes += compactRoutesJson.toByteArray().size.toLong()
        }
        val finalStations = collectedStations.values.toList()
        if (finalStations.isNotEmpty()) {
            val compactStopsJson = OverpassStreamCodec.serializeCompactStations(finalStations)
            tileDao.upsert(CachedOverpassTileEntity(stopsCacheKey, compactStopsJson, now))
            totalBytes += compactStopsJson.toByteArray().size.toLong()
        }
        clearRamCache()

        OfflineTransitBundle(
            transitCacheKey = transitCacheKey,
            stopsCacheKey = stopsCacheKey,
            stationsCount = finalStations.size,
            busLinesCount = busSuccessCount,
            railLinesCount = railRoutes?.size ?: 0,
            isPartial = partialFailure || collectedRoutes.isEmpty(),
            totalSizeBytes = totalBytes
        )
    }

    suspend fun deleteOfflineRegionTransit(transitCacheKey: String, stopsCacheKey: String) =
        withContext(Dispatchers.IO) {
            clearRamCache()
            try {
                tileDao.deleteByKey(transitCacheKey)
                tileDao.deleteByKey(stopsCacheKey)
            } catch (_: Exception) {
                // Non-fatal cleanup error
            }
        }

    private fun subdivideBoundsForOfflineQueries(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): List<MapBounds> {
        val latSpan = north - south
        val lonSpan = east - west
        val rows = if (latSpan > 0.12) 2 else 1
        val cols = if (lonSpan > 0.16) 2 else 1
        val result = mutableListOf<MapBounds>()
        val dLat = latSpan / rows
        val dLon = lonSpan / cols
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                result.add(
                    MapBounds(
                        south = south + r * dLat,
                        west = west + c * dLon,
                        north = south + (r + 1) * dLat,
                        east = west + (c + 1) * dLon
                    )
                )
            }
        }
        return result
    }

    /**
     * Executes a coroutine-cancellable Overpass POST query and streams the HTTP response body
     * [InputStream] directly into [streamConsumer] without ever reading the full payload into RAM.
     */
    private suspend fun <T> executeStreamingQuery(
        query: String,
        useLongTimeout: Boolean = false,
        streamConsumer: (InputStream) -> T?
    ): T? {
        val httpClient = if (useLongTimeout) offlineClient else client
        for (endpoint in endpoints) {
            currentCoroutineContext().ensureActive()
            val body = FormBody.Builder().add("data", query).build()
            val req = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", "LetzteMeile-Android/0.7 (Transit Map)")
                .build()
            val call = httpClient.newCall(req)
            val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            val startMs = System.currentTimeMillis()
            try {
                call.execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - startMs
                    if (!resp.isSuccessful) {
                        Log.w("OverpassTransit", "HTTP ${resp.code} from $endpoint in ${elapsed}ms")
                        return@use
                    }
                    val responseBody = resp.body ?: return@use
                    val contentLength = responseBody.contentLength()
                    if (contentLength > 18L * 1024L * 1024L) return@use
                    val parsed = streamConsumer(responseBody.byteStream())
                    if (parsed != null) {
                        Log.d("OverpassTransit", "OK from $endpoint in ${System.currentTimeMillis() - startMs}ms")
                        return parsed
                    }
                }
            } catch (ce: CancellationException) {
                call.cancel()
                throw ce
            } catch (_: OutOfMemoryError) {
                System.gc()
            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    Log.w("OverpassTransit", "Failed $endpoint in ${System.currentTimeMillis() - startMs}ms: ${e.javaClass.simpleName}")
                }
            } finally {
                cancelHandle.dispose()
            }
        }
        return null
    }
}
