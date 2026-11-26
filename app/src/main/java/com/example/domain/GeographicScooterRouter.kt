package com.example.domain

import com.example.data.remote.OverpassTransitRepository
import com.example.data.remote.PhotonGeocodingApi
import com.example.data.remote.ScooterBikeRoutingRepository
import com.example.data.staticdata.MunichSBahnNetwork
import com.example.model.GeoPoint
import com.example.model.GeographicScooterRoute
import com.example.model.GeographicTransitSegment
import com.example.model.NearbyStationResult
import com.example.model.RouteTransitChoice
import com.example.model.ScooterProfile
import com.example.model.SearchLocation
import com.example.model.StationType
import com.example.model.TransitStation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * Purely geographic & network-based Scooter-First router (ZERO DB-API, ZERO live timetables).
 * Combines:
 *  - Place / address / coordinate search via Komoot Photon (OSM) + known stations
 *  - Separate transit network selection for Start (S-Bahn / Bus / Regio-DB) and Destination
 *  - Real OSM Bicycle street/bike-path scooter routing with automatic offline straight-line fallback
 *  - Smart geographic transfer hub selection when Start and Destination use different networks
 *  - Full round-trip e-scooter battery calculation using real routed distances
 */
class GeographicScooterRouter(
    private val photonApi: PhotonGeocodingApi,
    private val overpassRepo: OverpassTransitRepository,
    private val bikeRoutingRepo: ScooterBikeRoutingRepository
) {

    suspend fun searchPlaces(
        query: String,
        biasCenter: GeoPoint = GeoDistanceUtils.MUNICH_CENTER
    ): List<SearchLocation> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val results = mutableListOf<SearchLocation>()

        GeoDistanceUtils.parseCoordinateQuery(trimmed)?.let { coord ->
            results.add(
                SearchLocation(
                    id = "coord_${coord.latitude}_${coord.longitude}",
                    name = String.format(Locale.US, "%.5f, %.5f", coord.latitude, coord.longitude),
                    subtitle = "Koordinate",
                    location = coord
                )
            )
            return results
        }

        val lower = trimmed.lowercase(Locale.GERMAN)

        // Instant local matches for known S-Bahn & Regio hubs
        (MunichSBahnNetwork.stations + MunichSBahnNetwork.regioLongStations)
            .distinctBy { it.id }
            .filter { it.name.lowercase(Locale.GERMAN).contains(lower) }
            .take(3)
            .forEach { st ->
                results.add(
                    SearchLocation(
                        id = "station_${st.id}",
                        name = st.name,
                        subtitle = "${st.lines.joinToString(" · ")} · ${st.district.ifBlank { "Bahnhof" }}",
                        location = st.location,
                        stationId = st.id
                    )
                )
            }

        // Worldwide / DACH Photon OpenStreetMap Geocoder biased to current map center
        try {
            val photon = photonApi.searchPlaces(
                query = trimmed,
                biasLat = biasCenter.latitude,
                biasLon = biasCenter.longitude,
                limit = 8
            )
            photon.features.orEmpty().forEachIndexed { idx, feature ->
                val coords = feature.geometry?.coordinates
                if (coords != null && coords.size >= 2) {
                    val lon = coords[0]
                    val lat = coords[1]
                    val props = feature.properties
                    val street = props?.street?.trim().orEmpty()
                    val house = props?.housenumber?.trim().orEmpty()
                    val name = props?.name?.trim().orEmpty()
                    val postcode = props?.postcode?.trim().orEmpty()
                    val city = props?.city?.trim().orEmpty()
                    val district = props?.district?.trim().orEmpty()
                    val locality = props?.locality?.trim().orEmpty()
                    val country = props?.country?.trim().orEmpty()

                    val primaryName = when {
                        street.isNotEmpty() && house.isNotEmpty() -> "$street $house"
                        name.isNotEmpty() -> name
                        street.isNotEmpty() -> street
                        city.isNotEmpty() -> city
                        else -> trimmed
                    }

                    // Format Line 2 cleanly as "PLZ Ort" (e.g. "85386 Eching") without prepending small hamlets
                    // such as "Ottenburg" when a real city/municipality name is already available.
                    val placeName = when {
                        city.isNotEmpty() -> city
                        district.isNotEmpty() -> district
                        locality.isNotEmpty() -> locality
                        else -> ""
                    }
                    val plzOrt = listOf(postcode, placeName)
                        .filter { it.isNotBlank() && !it.equals(primaryName, ignoreCase = true) }
                        .joinToString(" ")
                        .trim()

                    val isGerman = country.equals("Deutschland", ignoreCase = true) ||
                        country.equals("Germany", ignoreCase = true)
                    val sub = buildString {
                        if (plzOrt.isNotEmpty()) {
                            append(plzOrt)
                            if (country.isNotEmpty() && !isGerman) {
                                append(", ")
                                append(country)
                            }
                        } else if (country.isNotEmpty()) {
                            append(country)
                        } else {
                            append("OpenStreetMap")
                        }
                    }

                    results.add(
                        SearchLocation(
                            id = "photon_${idx}_${lat}_${lon}",
                            name = primaryName,
                            subtitle = sub,
                            location = GeoPoint(lat, lon)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Keep instant local matches even when offline
        }

        MunichSBahnNetwork.districts
            .filter { it.name.lowercase(Locale.GERMAN).contains(lower) }
            .take(2)
            .forEach { d ->
                results.add(
                    SearchLocation(
                        id = "district_${d.name}",
                        name = d.name,
                        subtitle = "Stadtteil · München",
                        location = d.location
                    )
                )
            }

        return results.distinctBy { "${it.name.lowercase()}_${it.subtitle.lowercase()}" }.take(8)
    }

    /**
     * Computes a purely geographic, network-based Scooter-First route between Start and Destination.
     * Supports independent transit mode selection at Start and Destination (e.g. Start = S-Bahn, Ziel = Bus)
     * and automatically locates the optimal transfer hub when switching networks.
     */
    suspend fun planGeographicRoute(
        start: SearchLocation,
        destination: SearchLocation,
        startMode: RouteTransitChoice,
        destinationMode: RouteTransitChoice,
        knownDynamicStations: List<TransitStation>,
        profile: ScooterProfile,
        preferredOriginStation: TransitStation? = null,
        preferredDestinationStation: TransitStation? = null
    ): Result<GeographicScooterRoute> = coroutineScope {
        try {
            // ALWAYS query real, complete OSM stops via Overpass around Start & Destination (Room-cached 24h)
            // so nearby stations (e.g. S-Bahn Eching 100m away) are NEVER missed by static lists.
            val startFetchedDef = async {
                overpassRepo.queryNearbyStations(start.location, startMode)
            }
            val destFetchedDef = async {
                overpassRepo.queryNearbyStations(destination.location, destinationMode)
            }
            // When switching networks (e.g. S-Bahn -> Bus or Bus -> S-Bahn), also fetch the rail stops
            // near the bus endpoint so we discover the true nearest transfer hub via OSM too.
            val transferHubFetchedDef = async {
                when {
                    startMode != RouteTransitChoice.BUS && destinationMode == RouteTransitChoice.BUS ->
                        overpassRepo.queryNearbyStations(destination.location, startMode)
                    startMode == RouteTransitChoice.BUS && destinationMode != RouteTransitChoice.BUS ->
                        overpassRepo.queryNearbyStations(start.location, destinationMode)
                    else -> emptyList()
                }
            }

            val loadedRoutesDef = async {
                overpassRepo.getLocalTransitRoutesFor(start.location, destination.location)
            }

            val startOsmStations = startFetchedDef.await()
            val destOsmStations = destFetchedDef.await()
            val transferOsmStations = transferHubFetchedDef.await()
            val loadedRoutes = loadedRoutesDef.await()

            val startPool = stationPoolFor(
                mode = startMode,
                dynamicStations = startOsmStations + transferOsmStations + knownDynamicStations
            )
            val destPool = stationPoolFor(
                mode = destinationMode,
                dynamicStations = destOsmStations + transferOsmStations + knownDynamicStations
            )

            val originStation = if (preferredOriginStation != null) {
                val airKm = GeoDistanceUtils.haversineKm(start.location, preferredOriginStation.location)
                val estKm = airKm * GeoDistanceUtils.SCOOTER_DETOUR_FACTOR
                NearbyStationResult(
                    station = preferredOriginStation,
                    airDistanceKm = airKm,
                    estimatedScooterKm = estKm,
                    estimatedScooterMinutes = GeoDistanceUtils.estimateScooterMinutes(estKm, profile)
                )
            } else {
                GeoDistanceUtils.findNearestStations(
                    point = start.location,
                    stations = startPool,
                    profile = profile,
                    limit = 1
                ).firstOrNull() ?: return@coroutineScope Result.failure(
                    IllegalStateException("Keine Haltestelle für „${startMode.shortLabel}“ in der Nähe des Startpunkts gefunden.")
                )
            }

            val destStation = if (preferredDestinationStation != null) {
                val airKm = GeoDistanceUtils.haversineKm(destination.location, preferredDestinationStation.location)
                val estKm = airKm * GeoDistanceUtils.SCOOTER_DETOUR_FACTOR
                NearbyStationResult(
                    station = preferredDestinationStation,
                    airDistanceKm = airKm,
                    estimatedScooterKm = estKm,
                    estimatedScooterMinutes = GeoDistanceUtils.estimateScooterMinutes(estKm, profile)
                )
            } else {
                GeoDistanceUtils.findNearestStations(
                    point = destination.location,
                    stations = destPool,
                    profile = profile,
                    limit = 1
                ).firstOrNull() ?: return@coroutineScope Result.failure(
                    IllegalStateException("Keine Haltestelle für „${destinationMode.shortLabel}“ in der Nähe des Zielpunkts gefunden.")
                )
            }

            val transferStation: TransitStation?
            val segments: List<GeographicTransitSegment>

            val isSameStation = originStation.station.name.equals(destStation.station.name, ignoreCase = true) ||
                GeoDistanceUtils.haversineKm(originStation.station.location, destStation.station.location) < 0.08

            if (startMode == destinationMode) {
                val sharedLines = if (isSameStation) {
                    emptyList()
                } else {
                    findCommonLines(originStation.station, destStation.station)
                }

                if (isSameStation || sharedLines.isNotEmpty()) {
                    transferStation = null
                    val initialLines = when {
                        sharedLines.isNotEmpty() -> sharedLines
                        else -> originStation.station.lines.take(3)
                    }
                    val resolved = TransitRouteGeometryResolver.resolveSegmentDetails(
                        mode = startMode,
                        fromStation = originStation.station,
                        toStation = destStation.station,
                        suggestedLines = initialLines,
                        loadedRoutes = loadedRoutes
                    )
                    val displayLines = resolved.suggestedLines.ifEmpty { initialLines }
                    val hint = when {
                        isSameStation ->
                            "Start- & Zielhaltestelle identisch – direkte Scooter-Strecke genügt"
                        else ->
                            "Direkte ${startMode.shortLabel}-Verbindung (${displayLines.joinToString(" / ")})"
                    }
                    segments = listOf(
                        GeographicTransitSegment(
                            mode = startMode,
                            fromStation = originStation.station,
                            toStation = destStation.station,
                            suggestedLines = displayLines,
                            airDistanceKm = GeoDistanceUtils.haversineKm(
                                originStation.station.location,
                                destStation.station.location
                            ),
                            requiresBusWarning = startMode == RouteTransitChoice.BUS,
                            connectionHint = hint,
                            directionTerminal = resolved.directionTerminal,
                            lineColorHex = resolved.lineColorHex,
                            transitPath = resolved.transitPath
                        )
                    )
                } else {
                    // Same network (e.g. S8 -> S3) without shared direct line: compute concrete transfer station!
                    val sameModeHub = TransitRouteGeometryResolver.findSameModeTransferStation(
                        origin = originStation.station,
                        destination = destStation.station,
                        mode = startMode,
                        stationPool = startPool,
                        loadedRoutes = loadedRoutes
                    )
                    if (sameModeHub != null) {
                        transferStation = sameModeHub
                        val leg1InitialLines = findCommonLines(originStation.station, sameModeHub).ifEmpty {
                            originStation.station.lines.take(3)
                        }
                        val leg2InitialLines = findCommonLines(sameModeHub, destStation.station).ifEmpty {
                            destStation.station.lines.take(3)
                        }
                        val leg1Resolved = TransitRouteGeometryResolver.resolveSegmentDetails(
                            mode = startMode,
                            fromStation = originStation.station,
                            toStation = sameModeHub,
                            suggestedLines = leg1InitialLines,
                            loadedRoutes = loadedRoutes
                        )
                        val leg2Resolved = TransitRouteGeometryResolver.resolveSegmentDetails(
                            mode = destinationMode,
                            fromStation = sameModeHub,
                            toStation = destStation.station,
                            suggestedLines = leg2InitialLines,
                            loadedRoutes = loadedRoutes
                        )
                        val leg1 = GeographicTransitSegment(
                            mode = startMode,
                            fromStation = originStation.station,
                            toStation = sameModeHub,
                            suggestedLines = leg1Resolved.suggestedLines.ifEmpty { leg1InitialLines },
                            airDistanceKm = GeoDistanceUtils.haversineKm(
                                originStation.station.location,
                                sameModeHub.location
                            ),
                            requiresBusWarning = startMode == RouteTransitChoice.BUS,
                            connectionHint = "${startMode.shortLabel} bis Umstieg ${sameModeHub.name}",
                            directionTerminal = leg1Resolved.directionTerminal,
                            lineColorHex = leg1Resolved.lineColorHex,
                            transitPath = leg1Resolved.transitPath
                        )
                        val leg2 = GeographicTransitSegment(
                            mode = destinationMode,
                            fromStation = sameModeHub,
                            toStation = destStation.station,
                            suggestedLines = leg2Resolved.suggestedLines.ifEmpty { leg2InitialLines },
                            airDistanceKm = GeoDistanceUtils.haversineKm(
                                sameModeHub.location,
                                destStation.station.location
                            ),
                            requiresBusWarning = destinationMode == RouteTransitChoice.BUS,
                            connectionHint = "Weiter mit ${destinationMode.shortLabel} bis ${destStation.station.name}",
                            directionTerminal = leg2Resolved.directionTerminal,
                            lineColorHex = leg2Resolved.lineColorHex,
                            transitPath = leg2Resolved.transitPath
                        )
                        segments = listOf(leg1, leg2)
                    } else {
                        transferStation = null
                        val fallbackLines = (originStation.station.lines.take(2) + destStation.station.lines.take(2))
                            .distinct()
                            .take(4)
                        val resolved = TransitRouteGeometryResolver.resolveSegmentDetails(
                            mode = startMode,
                            fromStation = originStation.station,
                            toStation = destStation.station,
                            suggestedLines = fallbackLines,
                            loadedRoutes = loadedRoutes
                        )
                        segments = listOf(
                            GeographicTransitSegment(
                                mode = startMode,
                                fromStation = originStation.station,
                                toStation = destStation.station,
                                suggestedLines = resolved.suggestedLines.ifEmpty { fallbackLines },
                                airDistanceKm = GeoDistanceUtils.haversineKm(
                                    originStation.station.location,
                                    destStation.station.location
                                ),
                                requiresBusWarning = startMode == RouteTransitChoice.BUS,
                                connectionHint = "${startMode.shortLabel}-Verbindung mit Netzumstieg",
                                directionTerminal = resolved.directionTerminal,
                                lineColorHex = resolved.lineColorHex,
                                transitPath = resolved.transitPath
                            )
                        )
                    }
                }
            } else {
                // Mixed modes (e.g., Start = S-Bahn, Ziel = Bus): find the best geographic transfer hub!
                val hub = findBestTransferHub(
                    origin = originStation.station,
                    destination = destStation.station,
                    startMode = startMode,
                    destMode = destinationMode,
                    startPool = startPool,
                    destPool = destPool
                )
                transferStation = hub

                val leg1InitialLines = findCommonLines(originStation.station, hub).ifEmpty {
                    originStation.station.lines.take(3)
                }
                val leg2InitialLines = findCommonLines(hub, destStation.station).ifEmpty {
                    destStation.station.lines.take(3)
                }
                val leg1Resolved = TransitRouteGeometryResolver.resolveSegmentDetails(
                    mode = startMode,
                    fromStation = originStation.station,
                    toStation = hub,
                    suggestedLines = leg1InitialLines,
                    loadedRoutes = loadedRoutes
                )
                val leg2Resolved = TransitRouteGeometryResolver.resolveSegmentDetails(
                    mode = destinationMode,
                    fromStation = hub,
                    toStation = destStation.station,
                    suggestedLines = leg2InitialLines,
                    loadedRoutes = loadedRoutes
                )

                val leg1 = GeographicTransitSegment(
                    mode = startMode,
                    fromStation = originStation.station,
                    toStation = hub,
                    suggestedLines = leg1Resolved.suggestedLines.ifEmpty { leg1InitialLines },
                    airDistanceKm = GeoDistanceUtils.haversineKm(originStation.station.location, hub.location),
                    requiresBusWarning = startMode == RouteTransitChoice.BUS,
                    connectionHint = "${startMode.shortLabel} bis Umstieg ${hub.name}",
                    directionTerminal = leg1Resolved.directionTerminal,
                    lineColorHex = leg1Resolved.lineColorHex,
                    transitPath = leg1Resolved.transitPath
                )
                val leg2 = GeographicTransitSegment(
                    mode = destinationMode,
                    fromStation = hub,
                    toStation = destStation.station,
                    suggestedLines = leg2Resolved.suggestedLines.ifEmpty { leg2InitialLines },
                    airDistanceKm = GeoDistanceUtils.haversineKm(hub.location, destStation.station.location),
                    requiresBusWarning = destinationMode == RouteTransitChoice.BUS,
                    connectionHint = "Umstieg auf ${destinationMode.shortLabel} bis ${destStation.station.name}",
                    directionTerminal = leg2Resolved.directionTerminal,
                    lineColorHex = leg2Resolved.lineColorHex,
                    transitPath = leg2Resolved.transitPath
                )
                segments = listOf(leg1, leg2)
            }

            // Route BOTH scooter legs over real streets & bike paths (OSRM Bicycle),
            // automatically falling back to 1.25x straight-line estimate when offline.
            val firstLegDef = async {
                bikeRoutingRepo.routeScooterLeg(
                    from = start.location,
                    to = originStation.station.location,
                    profile = profile
                )
            }
            val lastLegDef = async {
                bikeRoutingRepo.routeScooterLeg(
                    from = destStation.station.location,
                    to = destination.location,
                    profile = profile
                )
            }

            val firstLeg = firstLegDef.await()
            val lastLeg = lastLegDef.await()

            val routedOriginStation = originStation.copy(
                estimatedScooterKm = firstLeg.distanceKm,
                estimatedScooterMinutes = firstLeg.durationMinutes,
                routePath = firstLeg.path,
                isOfflineEstimate = firstLeg.isOfflineEstimate
            )
            val routedDestStation = destStation.copy(
                estimatedScooterKm = lastLeg.distanceKm,
                estimatedScooterMinutes = lastLeg.durationMinutes,
                routePath = lastLeg.path,
                isOfflineEstimate = lastLeg.isOfflineEstimate
            )

            val totalOneWayKm = routedOriginStation.estimatedScooterKm + routedDestStation.estimatedScooterKm
            val totalOneWayMin = routedOriginStation.estimatedScooterMinutes + routedDestStation.estimatedScooterMinutes
            val batteryAssessment = BatteryRangeCalculator.assessRoundTrip(
                firstMileKm = routedOriginStation.estimatedScooterKm,
                lastMileKm = routedDestStation.estimatedScooterKm,
                profile = profile
            )
            val anyOfflineEstimate = routedOriginStation.isOfflineEstimate || routedDestStation.isOfflineEstimate

            Result.success(
                GeographicScooterRoute(
                    id = "${routedOriginStation.station.id}_${transferStation?.id ?: "direct"}_${routedDestStation.station.id}_${startMode.name}_${destinationMode.name}",
                    startLocation = start,
                    destinationLocation = destination,
                    startMode = startMode,
                    destinationMode = destinationMode,
                    originStation = routedOriginStation,
                    transferStation = transferStation,
                    destinationStation = routedDestStation,
                    transitSegments = segments,
                    totalScooterOneWayKm = totalOneWayKm,
                    totalScooterOneWayMinutes = totalOneWayMin,
                    hasBusWarning = startMode == RouteTransitChoice.BUS || destinationMode == RouteTransitChoice.BUS,
                    batteryAssessment = batteryAssessment,
                    isOfflineEstimate = anyOfflineEstimate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Strictly prefers real stations from `mvv_transit.json` / Overpass (`dynamicStations`).
     * Falls back to static [MunichSBahnNetwork] coordinates ONLY as a last-resort emergency fallback
     * when no JSON/OSM stations have been loaded for [mode] yet.
     */
    private fun stationPoolFor(
        mode: RouteTransitChoice,
        dynamicStations: List<TransitStation>
    ): List<TransitStation> {
        val dynamic = dynamicStations.filter { mode.matchesStation(it) }
        if (dynamic.isNotEmpty()) {
            val deduped = ArrayList<TransitStation>(dynamic.size)
            for (station in dynamic) {
                val norm = normalizeStationName(station.name)
                val matchIdx = deduped.indexOfFirst { existing ->
                    normalizeStationName(existing.name) == norm &&
                        GeoDistanceUtils.haversineKm(existing.location, station.location) <= 0.55
                }
                if (matchIdx >= 0) {
                    val existing = deduped[matchIdx]
                    val extraLines = station.lines.filterNot {
                        it.equals("S-Bahn", ignoreCase = true) ||
                            it.equals("Bus", ignoreCase = true) ||
                            it.equals("Regio / DB", ignoreCase = true)
                    }
                    if (extraLines.isNotEmpty()) {
                        deduped[matchIdx] = existing.copy(lines = (existing.lines + extraLines).distinct().take(6))
                    }
                } else {
                    deduped.add(station)
                }
            }
            return deduped
        }

        // Last-resort emergency fallback ONLY if no mvv_transit.json / OSM data is loaded yet
        return when (mode) {
            RouteTransitChoice.SBAHN -> MunichSBahnNetwork.stations
            RouteTransitChoice.BUS -> MunichSBahnNetwork.busTerminalStopsAsStations
            RouteTransitChoice.REGIO_DB -> MunichSBahnNetwork.regioLongStations
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

    /**
     * Selects the smartest geographic transfer hub when switching networks (e.g., S-Bahn -> Bus):
     * - For Rail -> Bus: picks the rail station (on Start's rail network) closest to the destination bus stop,
     *   with a strong bonus if a nearby bus corridor links that rail station to the destination bus stop.
     * - For Bus -> Rail: picks the rail station (on Destination's rail network) closest to the origin bus stop
     *   along the corridor toward destination.
     * - For S-Bahn <-> Regio/DB: prioritizes true shared interchange stations (same station name/ID or <400m apart).
     */
    private fun findBestTransferHub(
        origin: TransitStation,
        destination: TransitStation,
        startMode: RouteTransitChoice,
        destMode: RouteTransitChoice,
        startPool: List<TransitStation>,
        destPool: List<TransitStation>
    ): TransitStation {
        // Case 1: Start = Rail (S-Bahn or Regio/DB), Dest = Bus -> transfer at the best rail station near Dest Bus
        if (destMode == RouteTransitChoice.BUS) {
            val candidates = startPool.filter { it.id != origin.id }.ifEmpty { startPool }
            return candidates.minByOrNull { railHub ->
                val distToDestBus = GeoDistanceUtils.haversineKm(railHub.location, destination.location)
                val distFromOrigin = GeoDistanceUtils.haversineKm(origin.location, railHub.location)
                val directDist = GeoDistanceUtils.haversineKm(origin.location, destination.location)
                // Penalize backtracking past destination slightly, reward direct bus-line match near the hub
                val detourPenalty = ((distFromOrigin + distToDestBus) - directDist).coerceAtLeast(0.0) * 0.35
                val busCorridorBonus = if (hasNearbyMatchingBusLine(railHub, destination, destPool)) -1.2 else 0.0
                distToDestBus + detourPenalty + busCorridorBonus
            } ?: destination
        }

        // Case 2: Start = Bus, Dest = Rail (S-Bahn or Regio/DB) -> transfer at the best rail station near Origin Bus
        if (startMode == RouteTransitChoice.BUS) {
            val candidates = destPool.filter { it.id != destination.id }.ifEmpty { destPool }
            return candidates.minByOrNull { railHub ->
                val distFromOriginBus = GeoDistanceUtils.haversineKm(origin.location, railHub.location)
                val distToDest = GeoDistanceUtils.haversineKm(railHub.location, destination.location)
                val directDist = GeoDistanceUtils.haversineKm(origin.location, destination.location)
                val detourPenalty = ((distFromOriginBus + distToDest) - directDist).coerceAtLeast(0.0) * 0.35
                val busCorridorBonus = if (hasNearbyMatchingBusLine(railHub, origin, startPool)) -1.2 else 0.0
                distFromOriginBus + detourPenalty + busCorridorBonus
            } ?: origin
        }

        // Case 3: S-Bahn <-> Regio / DB -> prefer shared rail interchange hubs (e.g. Moosach, Feldmoching, Freising, Hbf, Pasing, Ost)
        val regioCandidates = if (startMode == RouteTransitChoice.REGIO_DB) startPool else destPool
        val sBahnCandidates = if (startMode == RouteTransitChoice.SBAHN) startPool else destPool
        val sharedInterchanges = regioCandidates.filter { regio ->
            sBahnCandidates.any { sb ->
                sb.id == regio.id ||
                    sb.name.equals(regio.name, ignoreCase = true) ||
                    GeoDistanceUtils.haversineKm(sb.location, regio.location) <= 0.45
            }
        }.ifEmpty { regioCandidates }

        return sharedInterchanges.minByOrNull { hub ->
            GeoDistanceUtils.haversineKm(origin.location, hub.location) +
                GeoDistanceUtils.haversineKm(hub.location, destination.location)
        } ?: destination
    }

    private fun hasNearbyMatchingBusLine(
        railHub: TransitStation,
        targetBusStop: TransitStation,
        busPool: List<TransitStation>
    ): Boolean {
        val targetLines = targetBusStop.lines.map { normalizeLine(it) }.toSet()
        if (targetLines.isEmpty()) return false
        return busPool.any { stop ->
            GeoDistanceUtils.haversineKm(railHub.location, stop.location) <= 0.45 &&
                stop.lines.any { normalizeLine(it) in targetLines }
        }
    }

    private fun findCommonLines(a: TransitStation, b: TransitStation): List<String> {
        val setB = b.lines.map { normalizeLine(it) }.toSet()
        return a.lines.filter { normalizeLine(it) in setB }.distinct().take(4)
    }

    private fun normalizeLine(raw: String): String =
        raw.replace("Bus", "", ignoreCase = true).replace(" ", "").uppercase(Locale.GERMAN)
}
