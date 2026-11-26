package com.example.domain

import com.example.data.remote.CompactStop
import com.example.data.remote.CompactWay
import com.example.data.remote.OverpassStreamCodec
import com.example.data.remote.ParsedTransitRoute
import com.example.data.remote.TransitBucket
import com.example.data.staticdata.MunichSBahnNetwork
import com.example.model.GeoPoint
import com.example.model.RouteTransitChoice
import com.example.model.StationType
import com.example.model.TransitStation
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ResolvedTransitSegmentDetails(
    val suggestedLines: List<String>,
    val directionTerminal: String,
    val lineColorHex: String,
    val transitPath: List<GeoPoint>
)

/**
 * Resolves concrete transfer stations (including same-network line changes such as S8 -> S3),
 * travel direction terminals ("Richtung [Endhaltestelle]"), official line colors, and
 * travel-direction-oriented track/road polylines directly from loaded regional JSON transit routes.
 */
internal object TransitRouteGeometryResolver {

    fun findSameModeTransferStation(
        origin: TransitStation,
        destination: TransitStation,
        mode: RouteTransitChoice,
        stationPool: List<TransitStation>,
        loadedRoutes: List<ParsedTransitRoute>
    ): TransitStation? {
        val modeRoutes = filterRoutesForMode(loadedRoutes, mode)
        val originLineKeys = origin.lines.map { normalizeLineRef(it) }.filter { it.isNotBlank() }.toSet()
        val destLineKeys = destination.lines.map { normalizeLineRef(it) }.filter { it.isNotBlank() }.toSet()

        val directDistKm = GeoDistanceUtils.haversineKm(origin.location, destination.location)
        val originNormName = normalizeStationName(origin.name)
        val destNormName = normalizeStationName(destination.name)

        // 1. Primary: find shared interchange stops directly from loaded JSON route relations
        if (modeRoutes.isNotEmpty()) {
            val originRoutes = modeRoutes.filter { route ->
                normalizeLineRef(route.ref) in originLineKeys ||
                    findStopIndex(route.stops, origin) >= 0
            }
            val destRoutes = modeRoutes.filter { route ->
                normalizeLineRef(route.ref) in destLineKeys ||
                    findStopIndex(route.stops, destination) >= 0
            }

            if (originRoutes.isNotEmpty() && destRoutes.isNotEmpty()) {
                val destStopsByNormName = HashMap<String, MutableList<Pair<CompactStop, String>>>()
                val allDestStops = ArrayList<Pair<CompactStop, String>>()
                for (route in destRoutes) {
                    val refLabel = formatLineLabel(route.ref, mode)
                    for (stop in route.stops) {
                        val norm = normalizeStationName(stop.name)
                        if (norm.isNotBlank()) {
                            destStopsByNormName.getOrPut(norm) { ArrayList() }.add(stop to refLabel)
                        }
                        allDestStops.add(stop to refLabel)
                    }
                }

                data class HubCandidate(
                    val name: String,
                    val location: GeoPoint,
                    val originLines: MutableSet<String> = linkedSetOf(),
                    val destLines: MutableSet<String> = linkedSetOf()
                )

                val candidates = LinkedHashMap<String, HubCandidate>()

                for (route in originRoutes) {
                    val origRefLabel = formatLineLabel(route.ref, mode)
                    for (stop in route.stops) {
                        val norm = normalizeStationName(stop.name)
                        if (norm.isBlank()) continue
                        if (norm == originNormName || norm == destNormName) continue
                        val pt = GeoPoint(stop.lat, stop.lon)
                        if (GeoDistanceUtils.haversineKm(pt, origin.location) < 0.15 ||
                            GeoDistanceUtils.haversineKm(pt, destination.location) < 0.15
                        ) {
                            continue
                        }

                        // Match by normalized name within 0.9 km first, then pure proximity <= 0.35 km
                        val nameMatches = destStopsByNormName[norm]?.filter { (dStop, _) ->
                            GeoDistanceUtils.haversineKm(pt, GeoPoint(dStop.lat, dStop.lon)) <= 0.90
                        }.orEmpty()

                        val matchedDestPairs = if (nameMatches.isNotEmpty()) {
                            nameMatches
                        } else {
                            allDestStops.filter { (dStop, _) ->
                                GeoDistanceUtils.haversineKm(pt, GeoPoint(dStop.lat, dStop.lon)) <= 0.35
                            }
                        }

                        if (matchedDestPairs.isNotEmpty()) {
                            val key = "$norm@${(stop.lat * 200).roundToInt()}_${(stop.lon * 200).roundToInt()}"
                            val hub = candidates.getOrPut(key) {
                                HubCandidate(name = stop.name, location = pt)
                            }
                            hub.originLines.add(origRefLabel)
                            matchedDestPairs.forEach { (_, dRef) -> hub.destLines.add(dRef) }
                        }
                    }
                }

                val best = candidates.values.minByOrNull { hub ->
                    val d1 = GeoDistanceUtils.haversineKm(origin.location, hub.location)
                    val d2 = GeoDistanceUtils.haversineKm(hub.location, destination.location)
                    val interchangeBonus = ((hub.originLines.size + hub.destLines.size).coerceAtMost(4) - 2)
                        .coerceAtLeast(0) * 0.18
                    (d1 + d2) - interchangeBonus
                }

                if (best != null) {
                    val combinedLines = (best.originLines + best.destLines).distinct().take(6)
                    val matchingPoolStation = stationPool.firstOrNull { st ->
                        normalizeStationName(st.name) == normalizeStationName(best.name) &&
                            GeoDistanceUtils.haversineKm(st.location, best.location) <= 0.65
                    }
                    return matchingPoolStation?.copy(
                        lines = (combinedLines + matchingPoolStation.lines).distinct().take(8)
                    ) ?: TransitStation(
                        id = "transfer_${mode.name}_${normalizeStationName(best.name).hashCode()}",
                        name = best.name,
                        location = best.location,
                        type = stationTypeFor(mode),
                        lines = combinedLines
                    )
                }
            }
        }

        // 2. Fallback: find shared-line station directly in stationPool
        val poolShared = stationPool.filter { st ->
            val stNorm = normalizeStationName(st.name)
            if (stNorm == originNormName || stNorm == destNormName) return@filter false
            if (GeoDistanceUtils.haversineKm(st.location, origin.location) < 0.15 ||
                GeoDistanceUtils.haversineKm(st.location, destination.location) < 0.15
            ) {
                return@filter false
            }
            val stLines = st.lines.map { normalizeLineRef(it) }.toSet()
            stLines.any { it in originLineKeys } && stLines.any { it in destLineKeys }
        }
        if (poolShared.isNotEmpty()) {
            return poolShared.minByOrNull { hub ->
                GeoDistanceUtils.haversineKm(origin.location, hub.location) +
                    GeoDistanceUtils.haversineKm(hub.location, destination.location)
            }
        }

        // 3. Static Munich S-Bahn fallback when no regional JSON is loaded yet
        if (mode == RouteTransitChoice.SBAHN) {
            val originStaticLines = MunichSBahnNetwork.sBahnLineOrders.filter { line ->
                normalizeLineRef(line.name) in originLineKeys ||
                    line.stationIds.any { sid ->
                        MunichSBahnNetwork.stationsById[sid]?.let {
                            normalizeStationName(it.name) == originNormName
                        } == true
                    }
            }
            val destStaticLines = MunichSBahnNetwork.sBahnLineOrders.filter { line ->
                normalizeLineRef(line.name) in destLineKeys ||
                    line.stationIds.any { sid ->
                        MunichSBahnNetwork.stationsById[sid]?.let {
                            normalizeStationName(it.name) == destNormName
                        } == true
                    }
            }
            val destIdSet = destStaticLines.flatMap { it.stationIds }.toSet()
            val sharedIds = originStaticLines.flatMap { it.stationIds }.filter { it in destIdSet }.distinct()
            val bestStatic = sharedIds.mapNotNull { MunichSBahnNetwork.stationsById[it] }
                .filter {
                    val norm = normalizeStationName(it.name)
                    norm != originNormName && norm != destNormName
                }
                .minByOrNull { hub ->
                    GeoDistanceUtils.haversineKm(origin.location, hub.location) +
                        GeoDistanceUtils.haversineKm(hub.location, destination.location)
                }
            if (bestStatic != null) return bestStatic
        }

        // 4. Geographic bridge fallback: pick the closest station in stationPool along the corridor
        val corridorBridge = stationPool.filter { st ->
            val stNorm = normalizeStationName(st.name)
            stNorm != originNormName &&
                stNorm != destNormName &&
                GeoDistanceUtils.haversineKm(st.location, origin.location) >= 0.25 &&
                GeoDistanceUtils.haversineKm(st.location, destination.location) >= 0.25
        }.minByOrNull { hub ->
            val d1 = GeoDistanceUtils.haversineKm(origin.location, hub.location)
            val d2 = GeoDistanceUtils.haversineKm(hub.location, destination.location)
            val lineOverlapBonus = run {
                val stLines = hub.lines.map { normalizeLineRef(it) }.toSet()
                var bonus = 0.0
                if (stLines.any { it in originLineKeys }) bonus += 0.6
                if (stLines.any { it in destLineKeys }) bonus += 0.6
                bonus
            }
            (d1 + d2) - lineOverlapBonus
        }

        if (corridorBridge != null) {
            val totalBridgeDist = GeoDistanceUtils.haversineKm(origin.location, corridorBridge.location) +
                GeoDistanceUtils.haversineKm(corridorBridge.location, destination.location)
            if (totalBridgeDist <= directDistKm * 2.4 + 4.0) {
                return corridorBridge
            }
        }
        return null
    }

    /**
     * Resolves primary lines, travel direction terminal ("Richtung ..."), official line color,
     * and the travel-direction-oriented polyline (fromStation -> toStation) for a single transit segment.
     */
    fun resolveSegmentDetails(
        mode: RouteTransitChoice,
        fromStation: TransitStation,
        toStation: TransitStation,
        suggestedLines: List<String>,
        loadedRoutes: List<ParsedTransitRoute>
    ): ResolvedTransitSegmentDetails {
        val sameStation = normalizeStationName(fromStation.name) == normalizeStationName(toStation.name) ||
            GeoDistanceUtils.haversineKm(fromStation.location, toStation.location) < 0.08
        if (sameStation) {
            val primary = suggestedLines.firstOrNull().orEmpty()
            return ResolvedTransitSegmentDetails(
                suggestedLines = suggestedLines,
                directionTerminal = toStation.name,
                lineColorHex = officialLineColorHex(primary, mode),
                transitPath = emptyList()
            )
        }

        val modeRoutes = filterRoutesForMode(loadedRoutes, mode)
        val normalizedSuggested = suggestedLines.map { normalizeLineRef(it) }.filter { it.isNotBlank() }

        // Match routes in loaded JSON
        data class ScoredRoute(
            val route: ParsedTransitRoute,
            val fromIdx: Int,
            val toIdx: Int,
            val score: Int
        )

        val candidates = ArrayList<ScoredRoute>()
        for (route in modeRoutes) {
            val normRef = normalizeLineRef(route.ref)
            val lineRank = normalizedSuggested.indexOf(normRef)
            val fromIdx = findStopIndex(route.stops, fromStation)
            val toIdx = findStopIndex(route.stops, toStation)

            val servesBoth = fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx
            val matchesSuggestedLine = lineRank >= 0

            if (!servesBoth && !matchesSuggestedLine) continue

            var score = 0
            if (servesBoth) score += 1000
            if (servesBoth && fromIdx < toIdx) score += 220
            if (matchesSuggestedLine) score += (180 - lineRank * 20).coerceAtLeast(40)
            if (route.ways.isNotEmpty()) score += 140
            score += route.stops.size.coerceAtMost(80)

            candidates.add(ScoredRoute(route, fromIdx, toIdx, score))
        }

        val bestMatch = candidates.maxByOrNull { it.score }

        // Refine suggestedLines if JSON discovered the exact connecting line(s)
        val servingBothRefs = candidates
            .filter { it.fromIdx >= 0 && it.toIdx >= 0 && it.fromIdx != it.toIdx }
            .sortedByDescending { it.score }
            .map { formatLineLabel(it.route.ref, mode) }
            .distinct()

        val effectiveLines = when {
            suggestedLines.isNotEmpty() -> {
                // Keep suggested lines first, prioritizing those proven to connect both stops in JSON
                val provenSet = servingBothRefs.map { normalizeLineRef(it) }.toSet()
                val provenFirst = suggestedLines.filter { normalizeLineRef(it) in provenSet }
                val remaining = suggestedLines.filter { normalizeLineRef(it) !in provenSet }
                (provenFirst + remaining).distinct().take(4)
            }
            servingBothRefs.isNotEmpty() -> servingBothRefs.take(4)
            else -> emptyList()
        }

        val primaryLineName = effectiveLines.firstOrNull()
            ?: bestMatch?.route?.ref
            ?: mode.shortLabel

        // 1. Travel Direction Terminal ("Richtung [Endhaltestelle]")
        val directionTerminal = resolveDirectionTerminal(
            bestMatchRoute = bestMatch?.route,
            fromIdx = bestMatch?.fromIdx ?: -1,
            toIdx = bestMatch?.toIdx ?: -1,
            fromStation = fromStation,
            toStation = toStation,
            mode = mode,
            primaryLineName = primaryLineName
        )

        // 2. Official Line Color Hex
        val resolvedColorHex = bestMatch?.route?.colorHex?.takeIf { isValidHexColor(it) }
            ?: officialLineColorHex(primaryLineName, mode)

        // 3. Travel-Direction-Oriented Geometry (fromStation -> toStation)
        val rawPath = extractDirectedTransitPath(
            bestMatchRoute = bestMatch?.route,
            fromIdx = bestMatch?.fromIdx ?: -1,
            toIdx = bestMatch?.toIdx ?: -1,
            fromStation = fromStation,
            toStation = toStation,
            mode = mode,
            primaryLineName = primaryLineName
        )

        val orientedPath = ensurePathOriented(
            path = simplifyClosePoints(rawPath, minStepMeters = 8.0),
            from = fromStation.location,
            to = toStation.location
        )

        return ResolvedTransitSegmentDetails(
            suggestedLines = effectiveLines,
            directionTerminal = directionTerminal,
            lineColorHex = resolvedColorHex,
            transitPath = orientedPath
        )
    }

    private fun resolveDirectionTerminal(
        bestMatchRoute: ParsedTransitRoute?,
        fromIdx: Int,
        toIdx: Int,
        fromStation: TransitStation,
        toStation: TransitStation,
        mode: RouteTransitChoice,
        primaryLineName: String
    ): String {
        val fromNorm = normalizeStationName(fromStation.name)

        // 1. Exact ordered stop list from matched JSON route
        if (bestMatchRoute != null && bestMatchRoute.stops.size >= 2) {
            val stops = bestMatchRoute.stops
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                val candidateTerminal = if (fromIdx < toIdx) stops.last().name else stops.first().name
                if (candidateTerminal.isNotBlank() && normalizeStationName(candidateTerminal) != fromNorm) {
                    return candidateTerminal
                }
            }
            // Partial match: infer direction by comparing endpoint proximity to toStation
            val firstPt = GeoPoint(stops.first().lat, stops.first().lon)
            val lastPt = GeoPoint(stops.last().lat, stops.last().lon)
            val distFirstToFrom = GeoDistanceUtils.haversineKm(firstPt, fromStation.location)
            val distFirstToDest = GeoDistanceUtils.haversineKm(firstPt, toStation.location)
            val distLastToFrom = GeoDistanceUtils.haversineKm(lastPt, fromStation.location)
            val distLastToDest = GeoDistanceUtils.haversineKm(lastPt, toStation.location)
            val scoreForward = distFirstToFrom + distLastToDest
            val scoreBackward = distLastToFrom + distFirstToDest
            val inferred = if (scoreForward <= scoreBackward) stops.last().name else stops.first().name
            if (inferred.isNotBlank() && normalizeStationName(inferred) != fromNorm) {
                return inferred
            }
        }

        // 2. Static Munich S-Bahn stop order fallback
        if (mode == RouteTransitChoice.SBAHN) {
            val targetRef = normalizeLineRef(primaryLineName)
            val matchingLines = MunichSBahnNetwork.sBahnLineOrders.filter {
                normalizeLineRef(it.name) == targetRef
            }
            for (staticLine in matchingLines) {
                if (staticLine.stationIds.size >= 2) {
                    val staticStops = staticLine.stationIds.mapNotNull { MunichSBahnNetwork.stationsById[it] }
                    val sFromIdx = findTransitStationIndex(staticStops, fromStation)
                    val sToIdx = findTransitStationIndex(staticStops, toStation)
                    if (sFromIdx >= 0 && sToIdx >= 0 && sFromIdx != sToIdx) {
                        val term = if (sFromIdx < sToIdx) staticStops.last().name else staticStops.first().name
                        if (term.isNotBlank() && normalizeStationName(term) != fromNorm) {
                            return term
                        }
                    }
                }
            }
        }

        // 3. Clean fallback: Ausstiegshaltestelle
        return toStation.name
    }

    private fun extractDirectedTransitPath(
        bestMatchRoute: ParsedTransitRoute?,
        fromIdx: Int,
        toIdx: Int,
        fromStation: TransitStation,
        toStation: TransitStation,
        mode: RouteTransitChoice,
        primaryLineName: String
    ): List<GeoPoint> {
        val directDistKm = GeoDistanceUtils.haversineKm(fromStation.location, toStation.location)

        // 1. Primary: real track/road polyline via Dijkstra on the matched JSON route's CompactWays
        if (bestMatchRoute != null && bestMatchRoute.ways.isNotEmpty()) {
            val wayPath = extractShortestPathOverWays(
                ways = bestMatchRoute.ways,
                from = fromStation.location,
                to = toStation.location
            )
            if (wayPath.size >= 2) {
                val pathLenKm = computePolylineLengthKm(wayPath)
                if (pathLenKm <= maxOf(directDistKm * 3.4, directDistKm + 9.0)) {
                    return wayPath
                }
            }
        }

        // 2. Secondary: ordered intermediate stops from matched JSON route
        if (bestMatchRoute != null && fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            val slice = if (fromIdx < toIdx) {
                bestMatchRoute.stops.subList(fromIdx, toIdx + 1)
            } else {
                bestMatchRoute.stops.subList(toIdx, fromIdx + 1).reversed()
            }
            if (slice.size >= 2) {
                return slice.map { GeoPoint(it.lat, it.lon) }
            }
        }

        // 3. Tertiary: static Munich S-Bahn line stops fallback
        if (mode == RouteTransitChoice.SBAHN) {
            val targetRef = normalizeLineRef(primaryLineName)
            val matchingLines = MunichSBahnNetwork.sBahnLineOrders.filter {
                normalizeLineRef(it.name) == targetRef
            }
            for (staticLine in matchingLines) {
                if (staticLine.stationIds.size >= 2) {
                    val staticStops = staticLine.stationIds.mapNotNull { MunichSBahnNetwork.stationsById[it] }
                    val sFromIdx = findTransitStationIndex(staticStops, fromStation)
                    val sToIdx = findTransitStationIndex(staticStops, toStation)
                    if (sFromIdx >= 0 && sToIdx >= 0 && sFromIdx != sToIdx) {
                        val slice = if (sFromIdx < sToIdx) {
                            staticStops.subList(sFromIdx, sToIdx + 1)
                        } else {
                            staticStops.subList(sToIdx, sFromIdx + 1).reversed()
                        }
                        if (slice.size >= 2) {
                            return slice.map { it.location }
                        }
                    }
                }
            }
        }

        // 4. Final clean fallback: direct line from origin stop to destination stop
        return listOf(fromStation.location, toStation.location)
    }

    /**
     * Builds an undirected spatial graph from a transit route's OSM [CompactWay]s, splits the
     * nearest ways at the exact vertices closest to [from] and [to], and runs Dijkstra so the
     * extracted polyline follows real tracks/streets strictly from [from] -> [to].
     */
    private fun extractShortestPathOverWays(
        ways: List<CompactWay>,
        from: GeoPoint,
        to: GeoPoint
    ): List<GeoPoint> {
        val rawPolylines = ArrayList<List<GeoPoint>>(ways.size)
        for (way in ways) {
            val pts = wayToPoints(way)
            if (pts.size >= 2) rawPolylines.add(pts)
        }
        if (rawPolylines.isEmpty()) return emptyList()

        // Locate nearest vertex on any way for [from] and [to]
        var fromWayIdx = -1
        var fromVertIdx = -1
        var bestFromDist = Double.MAX_VALUE

        var toWayIdx = -1
        var toVertIdx = -1
        var bestToDist = Double.MAX_VALUE

        for (wIdx in rawPolylines.indices) {
            val pts = rawPolylines[wIdx]
            for (vIdx in pts.indices) {
                val dFrom = GeoDistanceUtils.haversineKm(pts[vIdx], from)
                if (dFrom < bestFromDist) {
                    bestFromDist = dFrom
                    fromWayIdx = wIdx
                    fromVertIdx = vIdx
                }
                val dTo = GeoDistanceUtils.haversineKm(pts[vIdx], to)
                if (dTo < bestToDist) {
                    bestToDist = dTo
                    toWayIdx = wIdx
                    toVertIdx = vIdx
                }
            }
        }

        // Reject if the route's ways don't come reasonably near either station
        if (fromWayIdx < 0 || toWayIdx < 0 || bestFromDist > 1.8 || bestToDist > 1.8) {
            return emptyList()
        }

        // Fast path: both stations fall on the same single OSM way
        if (fromWayIdx == toWayIdx) {
            val pts = rawPolylines[fromWayIdx]
            if (fromVertIdx == toVertIdx) return listOf(from, to)
            return if (fromVertIdx < toVertIdx) {
                pts.subList(fromVertIdx, toVertIdx + 1)
            } else {
                pts.subList(toVertIdx, fromVertIdx + 1).reversed()
            }
        }

        val startPoint = rawPolylines[fromWayIdx][fromVertIdx]
        val endPoint = rawPolylines[toWayIdx][toVertIdx]

        // Split the ways containing startPoint and endPoint at their exact nearest vertices
        val polylines = ArrayList<List<GeoPoint>>(rawPolylines.size + 4)
        for (wIdx in rawPolylines.indices) {
            val pts = rawPolylines[wIdx]
            val splitIndices = mutableListOf<Int>()
            if (wIdx == fromWayIdx && fromVertIdx in 1 until pts.lastIndex) {
                splitIndices.add(fromVertIdx)
            }
            if (wIdx == toWayIdx && toVertIdx in 1 until pts.lastIndex) {
                splitIndices.add(toVertIdx)
            }
            if (splitIndices.isEmpty()) {
                polylines.add(pts)
            } else {
                splitIndices.sort()
                var prev = 0
                for (split in splitIndices) {
                    if (split > prev) {
                        polylines.add(pts.subList(prev, split + 1))
                        prev = split
                    }
                }
                if (prev < pts.lastIndex) {
                    polylines.add(pts.subList(prev, pts.size))
                }
            }
        }

        // Cluster nearby way endpoints (~35 m) into shared graph nodes
        val nodeLocations = ArrayList<GeoPoint>()
        val gridBuckets = HashMap<Long, MutableList<Int>>()

        fun gridKey(latBucket: Int, lonBucket: Int): Long =
            (latBucket.toLong() shl 32) or (lonBucket.toLong() and 0xffffffffL)

        fun getOrCreateNode(pt: GeoPoint): Int {
            val latB = (pt.latitude * 2500.0).roundToInt() // ~44 m cell
            val lonB = (pt.longitude * 1600.0).roundToInt() // ~45 m cell at 48°N
            var bestNode = -1
            var bestDist = 0.045 // 45 m merge threshold
            for (dLat in -1..1) {
                for (dLon in -1..1) {
                    val bucket = gridBuckets[gridKey(latB + dLat, lonB + dLon)] ?: continue
                    for (nodeId in bucket) {
                        val d = GeoDistanceUtils.haversineKm(nodeLocations[nodeId], pt)
                        if (d < bestDist) {
                            bestDist = d
                            bestNode = nodeId
                        }
                    }
                }
            }
            if (bestNode >= 0) return bestNode
            val newId = nodeLocations.size
            nodeLocations.add(pt)
            gridBuckets.getOrPut(gridKey(latB, lonB)) { ArrayList(2) }.add(newId)
            return newId
        }

        data class GraphEdge(
            val toNode: Int,
            val weightKm: Double,
            val points: List<GeoPoint> // oriented from sourceNode -> toNode
        )

        val adjacency = HashMap<Int, MutableList<GraphEdge>>()
        for (pts in polylines) {
            val u = getOrCreateNode(pts.first())
            val v = getOrCreateNode(pts.last())
            if (u == v) continue
            val lenKm = computePolylineLengthKm(pts).coerceAtLeast(0.001)
            adjacency.getOrPut(u) { ArrayList(2) }.add(GraphEdge(v, lenKm, pts))
            adjacency.getOrPut(v) { ArrayList(2) }.add(GraphEdge(u, lenKm, pts.asReversed()))
        }

        val startNode = getOrCreateNode(startPoint)
        val endNode = getOrCreateNode(endPoint)
        if (startNode == endNode) return listOf(from, to)

        // Bridge small OSM platform/switch gaps (up to 450 m) between low-degree endpoints
        val endpointNodes = adjacency.entries.filter { it.value.size <= 2 }.map { it.key }
        if (endpointNodes.size in 2..400) {
            for (i in endpointNodes.indices) {
                val u = endpointNodes[i]
                val ptU = nodeLocations[u]
                for (j in i + 1 until endpointNodes.size) {
                    val v = endpointNodes[j]
                    val dist = GeoDistanceUtils.haversineKm(ptU, nodeLocations[v])
                    if (dist in 0.045..0.45) {
                        val bridgePts = listOf(ptU, nodeLocations[v])
                        val penaltyWeight = dist * 2.5
                        adjacency.getOrPut(u) { ArrayList() }.add(GraphEdge(v, penaltyWeight, bridgePts))
                        adjacency.getOrPut(v) { ArrayList() }.add(GraphEdge(u, penaltyWeight, bridgePts.asReversed()))
                    }
                }
            }
        }

        // Dijkstra shortest path from startNode -> endNode
        val nodeCount = nodeLocations.size
        val dist = DoubleArray(nodeCount) { Double.POSITIVE_INFINITY }
        val prevNode = IntArray(nodeCount) { -1 }
        val prevEdge = arrayOfNulls<GraphEdge>(nodeCount)
        val visited = BooleanArray(nodeCount)

        val pq = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        dist[startNode] = 0.0
        pq.add(startNode to 0.0)

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break
            if (visited[u]) continue
            visited[u] = true
            if (u == endNode) break

            val edges = adjacency[u] ?: continue
            for (edge in edges) {
                if (visited[edge.toNode]) continue
                val nd = d + edge.weightKm
                if (nd < dist[edge.toNode]) {
                    dist[edge.toNode] = nd
                    prevNode[edge.toNode] = u
                    prevEdge[edge.toNode] = edge
                    pq.add(edge.toNode to nd)
                }
            }
        }

        if (!visited[endNode]) return emptyList()

        // Reconstruct strictly ordered path from startNode -> endNode
        val edgeChain = ArrayList<GraphEdge>()
        var cur = endNode
        while (cur != startNode && cur >= 0) {
            val edge = prevEdge[cur] ?: break
            edgeChain.add(edge)
            cur = prevNode[cur]
        }
        if (cur != startNode || edgeChain.isEmpty()) return emptyList()
        edgeChain.reverse()

        val result = ArrayList<GeoPoint>(edgeChain.size * 6)
        for (edge in edgeChain) {
            for (pt in edge.points) {
                if (result.isEmpty() || GeoDistanceUtils.haversineKm(result.last(), pt) > 0.004) {
                    result.add(pt)
                }
            }
        }
        return result
    }

    private fun wayToPoints(way: CompactWay): List<GeoPoint> {
        val flat = way.lonLatFlat
        val count = flat.size / 2
        if (count < 2) return emptyList()
        val pts = ArrayList<GeoPoint>(count)
        for (i in 0 until count) {
            val lon = flat[i * 2]
            val lat = flat[i * 2 + 1]
            pts.add(GeoPoint(latitude = lat, longitude = lon))
        }
        return pts
    }

    private fun computePolylineLengthKm(points: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += GeoDistanceUtils.haversineKm(points[i], points[i + 1])
        }
        return total
    }

    private fun simplifyClosePoints(points: List<GeoPoint>, minStepMeters: Double): List<GeoPoint> {
        if (points.size <= 2) return points
        val minStepKm = minStepMeters / 1000.0
        val out = ArrayList<GeoPoint>(points.size)
        out.add(points.first())
        for (i in 1 until points.lastIndex) {
            if (GeoDistanceUtils.haversineKm(out.last(), points[i]) >= minStepKm) {
                out.add(points[i])
            }
        }
        out.add(points.last())
        return out
    }

    private fun ensurePathOriented(
        path: List<GeoPoint>,
        from: GeoPoint,
        to: GeoPoint
    ): List<GeoPoint> {
        if (path.size < 2) return path
        val forwardErr = GeoDistanceUtils.haversineKm(path.first(), from) +
            GeoDistanceUtils.haversineKm(path.last(), to)
        val reverseErr = GeoDistanceUtils.haversineKm(path.first(), to) +
            GeoDistanceUtils.haversineKm(path.last(), from)
        return if (reverseErr + 0.02 < forwardErr) path.asReversed() else path
    }

    private fun filterRoutesForMode(
        routes: List<ParsedTransitRoute>,
        mode: RouteTransitChoice
    ): List<ParsedTransitRoute> = when (mode) {
        RouteTransitChoice.SBAHN -> routes.filter {
            it.bucket == TransitBucket.SBAHN && OverpassStreamCodec.isStrictSBahnRef(it.ref)
        }
        RouteTransitChoice.BUS -> routes.filter { it.bucket == TransitBucket.BUS }
        RouteTransitChoice.REGIO_DB -> routes.filter { it.bucket == TransitBucket.REGIO_LONG }
    }

    private fun findStopIndex(stops: List<CompactStop>, target: TransitStation): Int {
        if (stops.isEmpty()) return -1
        val targetNorm = normalizeStationName(target.name)

        // 1. Exact/substring normalized name match within 1.4 km
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        for (i in stops.indices) {
            val stop = stops[i]
            val stopNorm = normalizeStationName(stop.name)
            if (stopNorm == targetNorm ||
                (targetNorm.length >= 4 && stopNorm.contains(targetNorm)) ||
                (stopNorm.length >= 4 && targetNorm.contains(stopNorm))
            ) {
                val d = GeoDistanceUtils.haversineKm(GeoPoint(stop.lat, stop.lon), target.location)
                if (d <= 1.4 && d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
        }
        if (bestIdx >= 0) return bestIdx

        // 2. Pure geographic match within 0.45 km
        for (i in stops.indices) {
            val stop = stops[i]
            val d = GeoDistanceUtils.haversineKm(GeoPoint(stop.lat, stop.lon), target.location)
            if (d <= 0.45 && d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun findTransitStationIndex(stops: List<TransitStation>, target: TransitStation): Int {
        val targetNorm = normalizeStationName(target.name)
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        for (i in stops.indices) {
            val st = stops[i]
            val norm = normalizeStationName(st.name)
            val d = GeoDistanceUtils.haversineKm(st.location, target.location)
            if (norm == targetNorm && d <= 1.5 && d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        if (bestIdx >= 0) return bestIdx
        for (i in stops.indices) {
            val d = GeoDistanceUtils.haversineKm(stops[i].location, target.location)
            if (d <= 0.5 && d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun stationTypeFor(mode: RouteTransitChoice): StationType = when (mode) {
        RouteTransitChoice.SBAHN -> StationType.SBAHN
        RouteTransitChoice.BUS -> StationType.BUS
        RouteTransitChoice.REGIO_DB -> StationType.REGIO
    }

    private fun formatLineLabel(ref: String, mode: RouteTransitChoice): String {
        val trimmed = ref.trim()
        return if (mode == RouteTransitChoice.BUS && !trimmed.startsWith("Bus", ignoreCase = true)) {
            "Bus $trimmed"
        } else {
            trimmed
        }
    }

    private fun normalizeLineRef(raw: String): String =
        raw.replace("Bus", "", ignoreCase = true)
            .replace(" ", "")
            .trim()
            .uppercase(Locale.GERMAN)

    private fun normalizeStationName(name: String): String =
        name.lowercase(Locale.GERMAN)
            .replace("münchen-", "")
            .replace("münchen ", "")
            .replace("s-bahnhof", "")
            .replace("s-bahn", "")
            .replace("bahnhof", "")
            .replace("bf.", "")
            .replace("(", " ")
            .replace(")", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isValidHexColor(hex: String): Boolean =
        hex.length == 7 && hex.startsWith("#") && hex.substring(1).all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }

    fun officialLineColorHex(lineName: String, mode: RouteTransitChoice): String {
        val clean = lineName.trim().uppercase(Locale.GERMAN)
        return when {
            clean.startsWith("ICE") -> "#D5001C"
            clean.startsWith("IC") || clean.startsWith("EC") || clean.startsWith("RJ") -> "#B91C1C"
            clean.startsWith("RE") || clean.startsWith("IRE") || clean.startsWith("REX") -> "#C8102E"
            clean.startsWith("RB") || clean.startsWith("MEX") || clean.startsWith("BRB") -> "#1D4ED8"
            clean.startsWith("ALX") || clean.startsWith("BOB") -> "#0F766E"
            clean.startsWith("S1") && (clean.length == 2 || !clean[2].isDigit()) -> "#16BAE7"
            clean.startsWith("S20") -> "#ED6B83"
            clean.startsWith("S2") && (clean.length == 2 || !clean[2].isDigit()) -> "#76B82A"
            clean.startsWith("S3") -> "#951B81"
            clean.startsWith("S4") -> "#E30613"
            clean.startsWith("S5") -> "#005C9E"
            clean.startsWith("S6") -> "#00975F"
            clean.startsWith("S7") -> "#963833"
            clean.startsWith("S8") -> "#F7A600"
            clean.startsWith("S") -> "#00975F"
            clean.startsWith("U") -> "#0065BD"
            clean.startsWith("TRAM") || clean.startsWith("STR") -> "#D91A1A"
            mode == RouteTransitChoice.BUS || clean.startsWith("BUS") || clean.startsWith("X") || clean.all { it.isDigit() } -> "#00586A"
            mode == RouteTransitChoice.REGIO_DB -> "#C8102E"
            else -> "#0065BD"
        }
    }

    /** Returns true when the hex color is bright (e.g. S8 yellow #F7A600) and needs a dark direction chevron. */
    fun isLightLineColor(hex: String): Boolean {
        if (!isValidHexColor(hex)) return false
        val r = hex.substring(1, 3).toIntOrNull(16) ?: return false
        val g = hex.substring(3, 5).toIntOrNull(16) ?: return false
        val b = hex.substring(5, 7).toIntOrNull(16) ?: return false
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return abs(luminance) > 0.62
    }
}
