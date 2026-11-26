package com.example.model

enum class StationType {
    SBAHN,
    BUS,
    UBAHN,
    TRAM,
    REGIO,
    LONG_DISTANCE
}

/**
 * 4-state map overlay toggle on Tab "Karte":
 *  1. Nur S-Bahn
 *  2. Nur Bus (all bus lines, never filtered)
 *  3. Nur Regio / DB (RE, RB, ICE, IC, EC)
 *  4. Alle zusammen
 */
enum class MapTransitMode(
    val shortLabel: String,
    val fullLabel: String
) {
    SBAHN_ONLY("S-Bahn", "Nur S-Bahn"),
    BUS_ONLY("Bus", "Nur Bus"),
    REGIO_LONG_ONLY("Regio/DB", "Nur Regio / DB"),
    ALL("Alle", "Alle Netze zusammen");

    fun next(): MapTransitMode = when (this) {
        SBAHN_ONLY -> BUS_ONLY
        BUS_ONLY -> REGIO_LONG_ONLY
        REGIO_LONG_ONLY -> ALL
        ALL -> SBAHN_ONLY
    }

    val includesSBahn: Boolean
        get() = this == SBAHN_ONLY || this == ALL

    val includesUbahnTram: Boolean
        get() = this == ALL

    val includesBus: Boolean
        get() = this == BUS_ONLY || this == ALL

    val includesRegioLong: Boolean
        get() = this == REGIO_LONG_ONLY || this == ALL

    fun toRouteChoiceOrNull(): RouteTransitChoice? = when (this) {
        SBAHN_ONLY -> RouteTransitChoice.SBAHN
        BUS_ONLY -> RouteTransitChoice.BUS
        REGIO_LONG_ONLY -> RouteTransitChoice.REGIO_DB
        ALL -> null
    }

    fun matchesStation(station: TransitStation): Boolean = when (this) {
        SBAHN_ONLY -> RouteTransitChoice.SBAHN.matchesStation(station)
        BUS_ONLY -> RouteTransitChoice.BUS.matchesStation(station)
        REGIO_LONG_ONLY -> RouteTransitChoice.REGIO_DB.matchesStation(station)
        ALL -> true
    }
}

/**
 * Separate transit network choice for Start and Destination inside Tab "Route".
 */
enum class RouteTransitChoice(
    val shortLabel: String,
    val fullLabel: String
) {
    SBAHN("S-Bahn", "S-Bahn"),
    BUS("Bus", "Bus"),
    REGIO_DB("Regio / DB", "Regio & Fernverkehr");

    fun matches(type: StationType): Boolean = when (this) {
        SBAHN -> type == StationType.SBAHN
        BUS -> type == StationType.BUS
        REGIO_DB -> type == StationType.REGIO || type == StationType.LONG_DISTANCE
    }

    fun matchesStation(station: TransitStation): Boolean {
        if (!matches(station.type)) return false
        if (this == SBAHN && station.lines.isNotEmpty()) {
            val hasStrictSBahn = station.lines.any {
                SBAHN_LINE_REGEX.matches(it.trim().replace(" ", ""))
            }
            val hasGenericSBahn = station.lines.any { it.equals("S-Bahn", ignoreCase = true) }
            return hasStrictSBahn || hasGenericSBahn
        }
        return true
    }

    fun toMapTransitMode(): MapTransitMode = when (this) {
        SBAHN -> MapTransitMode.SBAHN_ONLY
        BUS -> MapTransitMode.BUS_ONLY
        REGIO_DB -> MapTransitMode.REGIO_LONG_ONLY
    }

    companion object {
        val SBAHN_LINE_REGEX = Regex("^S\\d+[A-Za-z]?$", RegexOption.IGNORE_CASE)
    }
}

/**
 * Exact per-bucket layer visibility used by MapLibre so single-mode selections (e.g. S-Bahn only)
 * AND mixed route combinations (e.g. S-Bahn + Bus without Regio) strictly hide unselected networks.
 *
 * U-Bahn / Tram is strictly decoupled from pure S-Bahn and ONLY visible in [MapTransitMode.ALL].
 */
data class TransitBucketVisibility(
    val showSBahn: Boolean,
    val showUbahnTram: Boolean,
    val showBus: Boolean,
    val showRegioLong: Boolean
) {
    companion object {
        fun fromMapTransitMode(mode: MapTransitMode): TransitBucketVisibility =
            TransitBucketVisibility(
                showSBahn = mode.includesSBahn,
                showUbahnTram = mode.includesUbahnTram,
                showBus = mode.includesBus,
                showRegioLong = mode.includesRegioLong
            )

        fun fromRouteModes(
            startMode: RouteTransitChoice,
            destinationMode: RouteTransitChoice
        ): TransitBucketVisibility = TransitBucketVisibility(
            showSBahn = startMode == RouteTransitChoice.SBAHN || destinationMode == RouteTransitChoice.SBAHN,
            showUbahnTram = false,
            showBus = startMode == RouteTransitChoice.BUS || destinationMode == RouteTransitChoice.BUS,
            showRegioLong = startMode == RouteTransitChoice.REGIO_DB || destinationMode == RouteTransitChoice.REGIO_DB
        )
    }
}

/**
 * Optional, explicit fallback prompt shown ONLY when the nearest station of the user's strictly
 * chosen transit mode is out of remaining e-scooter battery range (never auto-mixes results).
 */
data class BatteryRangeFallbackSuggestion(
    val message: String,
    val actionLabel: String,
    val suggestedMapMode: MapTransitMode? = null,
    val suggestedRouteMode: RouteTransitChoice? = null,
    val targetIsRouteStart: Boolean? = null
)

enum class PreferredMapsApp(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val packageName: String?
) {
    GOOGLE_MAPS("google_maps", "Google Maps", "Standard · Navigation & Rad/Scooter", "com.google.android.apps.maps"),
    OSMAND("osmand", "OsmAnd", "OpenStreetMap · Offline-Karten", "net.osmand"),
    HERE_WEGO("here_wego", "HERE WeGo", "Offline-Karten & ÖPNV", "com.here.app.maps"),
    SYSTEM_DEFAULT("system_default", "System-Karten-App", "Standard-Geo-App des Geräts", null);

    companion object {
        fun fromId(id: String?): PreferredMapsApp =
            entries.firstOrNull { it.id == id } ?: GOOGLE_MAPS
    }
}

enum class PreferredTransitApp(
    val id: String,
    val displayName: String,
    val regionLabel: String,
    val packageName: String?
) {
    MVV_APP("mvv_app", "MVV-App / MVGO", "München / MVV", "de.mvv.muenchen"),
    MVGO("mvgo", "MVGO", "München Stadt (MVG)", "de.mvg.more"),
    BVG_JELBI("bvg_jelbi", "BVG Jelbi / VBB", "Berlin & Brandenburg (VBB)", "de.bvg.fahrinfo"),
    VBB_APP("vbb_app", "VBB Bus & Bahn", "Berlin & Brandenburg (VBB)", "de.hafas.android.vbb"),
    HVV_SWITCH("hvv_switch", "hvv switch", "Hamburg (HVV)", "de.hochbahn.hvvswitch"),
    RMV_GO("rmv_go", "RMVgo", "Frankfurt / Rhein-Main (RMV)", "de.rmv.android.rmvgo"),
    VVS_MOBIL("vvs_mobil", "VVS Mobil", "Stuttgart (VVS)", "com.mdv.VVSCompanion"),
    VRS_KVB("vrs_kvb", "KVB / VRS", "Köln / Bonn (VRS)", "de.vrs.android"),
    VRR_APP("vrr_app", "VRR / Rheinbahn", "Düsseldorf / Ruhrgebiet (VRR)", "de.vrr.app"),
    VGN_APP("vgn_app", "VGN", "Nürnberg / Franken (VGN)", "de.vgn.fahrplan"),
    SACHSEN_DVB("sachsen_dvb", "DVB / LeipzigMOVE", "Dresden / Leipzig (Sachsen)", "de.dvb.dvbmobil"),
    NDS_BREMEN("nds_bremen", "ÜSTRA / VBN / DB Navigator", "Niedersachsen & Bremen", "de.hafas.android.db"),
    NORD("nord", "NAH.SH / VVW / DB Navigator", "Schleswig-Holstein & MV", "de.hafas.android.db"),
    WESTFALEN("westfalen", "WestfalenTarif / DB Navigator", "Westfalen / Nord-NRW", "de.hafas.android.db"),
    RLP_SL("rlp_sl", "VRM / saarVV / DB Navigator", "Rheinland-Pfalz & Saarland", "de.hafas.android.db"),
    SUEDWEST("suedwest", "VRN / KVV / bwegt", "Baden & Rhein-Neckar", "de.hafas.android.db"),
    MITTE("mitte", "INSA / VMT / DB Navigator", "Thüringen & Sachsen-Anhalt", "de.hafas.android.db"),
    SCHWABEN("schwaben", "AVV / DING / DB Navigator", "Schwaben & Allgäu", "de.hafas.android.db"),
    BAYERN_NORD("bayern_nord", "RVV / VVM / DB Navigator", "Nord- & Ostbayern", "de.hafas.android.db"),
    OEBB_SCOTTY("oebb_scotty", "WienMobil / ÖBB Scotty", "Österreich (VOR / ÖBB)", "de.hafas.android.oebb"),
    SBB_MOBILE("sbb_mobile", "SBB Mobile", "Schweiz (SBB)", "ch.sbb.mobile.android.b2c"),
    DB_NAVIGATOR("db_navigator", "DB Navigator", "Deutschland & DACH (Allrounder)", "de.hafas.android.db");

    companion object {
        fun fromId(id: String?): PreferredTransitApp =
            entries.firstOrNull { it.id == id } ?: DB_NAVIGATOR
    }
}

data class AppSettings(
    val preferredMapsApp: PreferredMapsApp = PreferredMapsApp.GOOGLE_MAPS,
    val preferredTransitApp: PreferredTransitApp = PreferredTransitApp.MVV_APP,
    val autoRegionDetection: Boolean = true
)

data class TransitRegionRecommendation(
    val regionKey: String,
    val regionDisplayName: String,
    val recommendedApp: PreferredTransitApp
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class MapBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    fun contains(point: GeoPoint): Boolean =
        point.latitude in south..north && point.longitude in west..east

    fun center(): GeoPoint = GeoPoint(
        latitude = (south + north) / 2.0,
        longitude = (west + east) / 2.0
    )
}

data class TransitStation(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val type: StationType,
    val lines: List<String>,
    val district: String = ""
)

data class SBahnLine(
    val name: String,
    val colorHex: String,
    val stationIds: List<String>
)

data class MunichDistrict(
    val name: String,
    val location: GeoPoint
)

data class NearbyStationResult(
    val station: TransitStation,
    val airDistanceKm: Double,
    val estimatedScooterKm: Double,
    val estimatedScooterMinutes: Int,
    val routePath: List<GeoPoint> = emptyList(),
    val isOfflineEstimate: Boolean = true
)

data class SearchLocation(
    val id: String,
    val name: String,
    val subtitle: String,
    val location: GeoPoint,
    val stationId: String? = null
)

/**
 * Purely geographic / network-based public transit leg (NO live schedules, NO clock times, NO DB-API).
 */
data class GeographicTransitSegment(
    val mode: RouteTransitChoice,
    val fromStation: TransitStation,
    val toStation: TransitStation,
    val suggestedLines: List<String>,
    val airDistanceKm: Double,
    val requiresBusWarning: Boolean,
    val connectionHint: String,
    val directionTerminal: String = toStation.name,
    val lineColorHex: String = "#0065BD",
    val transitPath: List<GeoPoint> = emptyList()
)

/**
 * Complete Scooter-First Start-to-Destination geographic route calculated without live APIs.
 */
data class GeographicScooterRoute(
    val id: String,
    val startLocation: SearchLocation,
    val destinationLocation: SearchLocation,
    val startMode: RouteTransitChoice,
    val destinationMode: RouteTransitChoice,
    val originStation: NearbyStationResult,
    val transferStation: TransitStation?,
    val destinationStation: NearbyStationResult,
    val transitSegments: List<GeographicTransitSegment>,
    val totalScooterOneWayKm: Double,
    val totalScooterOneWayMinutes: Int,
    val hasBusWarning: Boolean,
    val batteryAssessment: BatteryRoundTripAssessment,
    val isOfflineEstimate: Boolean = false
) {
    /**
     * Compact route headline for cards & map header:
     * - With transfer: e.g. "S8 → Umstieg Ostbahnhof → S3"
     * - Direct: e.g. "S8 · Direktverbindung" (or "S-Bahn → S-Bahn" when no line ref is known)
     */
    fun compactHeadline(): String {
        val hub = transferStation
        if (hub != null && transitSegments.size >= 2) {
            val line1 = transitSegments[0].suggestedLines.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: transitSegments[0].mode.shortLabel
            val line2 = transitSegments[1].suggestedLines.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: transitSegments[1].mode.shortLabel
            return "$line1 → Umstieg ${hub.name} → $line2"
        }
        val singleLine = transitSegments.firstOrNull()?.suggestedLines?.firstOrNull()?.takeIf { it.isNotBlank() }
        return if (singleLine != null && startMode == destinationMode) {
            "$singleLine · ${startMode.shortLabel} direkt"
        } else {
            "${startMode.shortLabel} → ${destinationMode.shortLabel}"
        }
    }
}

enum class BatteryTrafficLight {
    SAFE,
    TIGHT,
    CRITICAL
}

data class BatteryRoundTripAssessment(
    val status: BatteryTrafficLight,
    val oneWayScooterKm: Double,
    val roundTripScooterKm: Double,
    val availableRangeKm: Double,
    val remainingKmAfterRoundTrip: Double,
    val oneWayFeasible: Boolean,
    val summaryLabel: String,
    val detailText: String
)

data class ScooterProfile(
    val activeScooterId: String = "preset_soflow_so_one_pro_v1",
    val modelName: String = "SoFlow SO ONE Pro V1",
    val manufacturerRangeKm: Int = 65,
    val realWorldFactorPercent: Int = 70,
    val maxSpeedKmh: Int = 20,
    val currentBatteryPercent: Int = 75,
    val batteryCapacityWh: Int? = 480,
    val nominalPowerW: Int? = 500,
    val peakPowerW: Int? = 1200
) {
    val effectiveFullRangeKm: Double
        get() = manufacturerRangeKm * (realWorldFactorPercent / 100.0)

    val currentAvailableRangeKm: Double
        get() = effectiveFullRangeKm * (currentBatteryPercent / 100.0)

    fun specsSummary(): String = buildList {
        add("$manufacturerRangeKm km")
        add("$realWorldFactorPercent%")
        add("$maxSpeedKmh km/h")
        batteryCapacityWh?.let { add("$it Wh") }
        when {
            nominalPowerW != null && peakPowerW != null -> add("${nominalPowerW}/${peakPowerW} W")
            nominalPowerW != null -> add("$nominalPowerW W")
        }
    }.joinToString(" · ")
}

data class ScooterPreset(
    val id: String,
    val modelName: String,
    val manufacturerRangeKm: Int,
    val realWorldFactorPercent: Int,
    val maxSpeedKmh: Int,
    val batteryCapacityWh: Int? = null,
    val nominalPowerW: Int? = null,
    val peakPowerW: Int? = null
) {
    fun specsSummary(): String = buildList {
        add("$manufacturerRangeKm km")
        add("$realWorldFactorPercent%")
        add("$maxSpeedKmh km/h")
        batteryCapacityWh?.let { add("$it Wh") }
        when {
            nominalPowerW != null && peakPowerW != null -> add("${nominalPowerW}/${peakPowerW} W")
            nominalPowerW != null -> add("$nominalPowerW W")
        }
    }.joinToString(" · ")
}

val DefaultScooterPresets = listOf(
    ScooterPreset(
        id = "preset_soflow_so_one_pro_v1",
        modelName = "SoFlow SO ONE Pro V1",
        manufacturerRangeKm = 65,
        realWorldFactorPercent = 70,
        maxSpeedKmh = 20,
        batteryCapacityWh = 480,
        nominalPowerW = 500,
        peakPowerW = 1200
    ),
    ScooterPreset(
        id = "preset_ninebot_max_g2d",
        modelName = "Ninebot Max G2D",
        manufacturerRangeKm = 70,
        realWorldFactorPercent = 70,
        maxSpeedKmh = 20,
        batteryCapacityWh = 551,
        nominalPowerW = 450,
        peakPowerW = 900
    ),
    ScooterPreset(
        id = "preset_xiaomi_4_pro",
        modelName = "Xiaomi Electric Scooter 4 Pro",
        manufacturerRangeKm = 55,
        realWorldFactorPercent = 68,
        maxSpeedKmh = 20,
        batteryCapacityWh = 446,
        nominalPowerW = 350,
        peakPowerW = 700
    ),
    ScooterPreset(
        id = "preset_vmax_vx2_pro",
        modelName = "VMAX VX2 Pro GT-B",
        manufacturerRangeKm = 60,
        realWorldFactorPercent = 75,
        maxSpeedKmh = 22,
        batteryCapacityWh = 792,
        nominalPowerW = 500,
        peakPowerW = 1300
    ),
    ScooterPreset(
        id = "preset_niu_kqi3_max",
        modelName = "NIU KQi3 Max",
        manufacturerRangeKm = 65,
        realWorldFactorPercent = 70,
        maxSpeedKmh = 20,
        batteryCapacityWh = 608,
        nominalPowerW = 450,
        peakPowerW = 900
    ),
    ScooterPreset(
        id = "preset_compact_commuter",
        modelName = "Kompakt-Scooter (Kurzstrecke)",
        manufacturerRangeKm = 30,
        realWorldFactorPercent = 65,
        maxSpeedKmh = 20,
        batteryCapacityWh = 275,
        nominalPowerW = 300,
        peakPowerW = 500
    )
)
