package com.example.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.remote.DynamicTransitOverlay
import com.example.data.staticdata.MunichSBahnNetwork
import com.example.domain.GeoDistanceUtils
import com.example.domain.TransitRouteGeometryResolver
import com.example.model.GeoPoint
import com.example.model.GeographicScooterRoute
import com.example.model.MapBounds
import com.example.model.MapTransitMode
import com.example.model.NearbyStationResult
import com.example.model.SearchLocation
import com.example.model.TransitBucketVisibility
import com.example.viewmodel.CameraMoveRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

private const val STYLE_SCOOTER_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_SCOOTER_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_NORMAL_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_NORMAL_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val OPENFREEMAP_GLYPHS_URL = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

private val FONT_BOLD = arrayOf("Noto Sans Bold")
private val FONT_REGULAR = arrayOf("Noto Sans Regular")

// Static Base Sources (Munich & Bavaria instant rendering)
private const val SRC_DISTRICTS = "lm-districts-src"
private const val SRC_BASE_SBAHN_LINES = "lm-base-sbahn-lines-src"
private const val SRC_BASE_SBAHN_LABELS = "lm-base-sbahn-labels-src"
private const val SRC_BASE_SBAHN_STATIONS = "lm-base-sbahn-stations-src"
private const val SRC_BASE_SBAHN_TERMINALS = "lm-base-sbahn-terminals-src"
private const val SRC_BASE_BUS_LINES = "lm-base-bus-lines-src"
private const val SRC_BASE_BUS_LABELS = "lm-base-bus-labels-src"
private const val SRC_BASE_BUS_STATIONS = "lm-base-bus-stations-src"
private const val SRC_BASE_BUS_TERMINALS = "lm-base-bus-terminals-src"
private const val SRC_BASE_REGIO_LINES = "lm-base-regio-lines-src"
private const val SRC_BASE_REGIO_LABELS = "lm-base-regio-labels-src"
private const val SRC_BASE_REGIO_STATIONS = "lm-base-regio-stations-src"
private const val SRC_BASE_REGIO_TERMINALS = "lm-base-regio-terminals-src"

// Dynamic Worldwide / DACH Overpass Sources
private const val SRC_DYN_SBAHN_LINES = "lm-dyn-sbahn-lines-src"
private const val SRC_DYN_SBAHN_LABELS = "lm-dyn-sbahn-labels-src"
private const val SRC_DYN_SBAHN_STATIONS = "lm-dyn-sbahn-stations-src"
private const val SRC_DYN_SBAHN_TERMINALS = "lm-dyn-sbahn-terminals-src"
private const val SRC_DYN_UBAHN_TRAM_LINES = "lm-dyn-ubahntram-lines-src"
private const val SRC_DYN_UBAHN_TRAM_LABELS = "lm-dyn-ubahntram-labels-src"
private const val SRC_DYN_UBAHN_TRAM_STATIONS = "lm-dyn-ubahntram-stations-src"
private const val SRC_DYN_UBAHN_TRAM_TERMINALS = "lm-dyn-ubahntram-terminals-src"
private const val SRC_DYN_BUS_LINES = "lm-dyn-bus-lines-src"
private const val SRC_DYN_BUS_LABELS = "lm-dyn-bus-labels-src"
private const val SRC_DYN_BUS_STATIONS = "lm-dyn-bus-stations-src"
private const val SRC_DYN_BUS_TERMINALS = "lm-dyn-bus-terminals-src"
private const val SRC_DYN_REGIO_LINES = "lm-dyn-regio-lines-src"
private const val SRC_DYN_REGIO_LABELS = "lm-dyn-regio-labels-src"
private const val SRC_DYN_REGIO_STATIONS = "lm-dyn-regio-stations-src"
private const val SRC_DYN_REGIO_TERMINALS = "lm-dyn-regio-terminals-src"

// Interactive Overlay Sources
private const val SRC_TARGET_CONNECTORS = "lm-target-connectors-src"
private const val SRC_TARGET_DIST_LABELS = "lm-target-dist-labels-src"
private const val SRC_TARGET_STATION_BADGES = "lm-target-station-badges-src"
private const val SRC_TARGET_PIN = "lm-target-pin-src"
private const val SRC_USER_LOC = "lm-user-loc-src"

// Geographic Route Overlay Sources (used exclusively in Tab "Route")
private const val SRC_ROUTE_TRANSIT_LEGS = "lm-route-transit-legs-src"
private const val SRC_ROUTE_SCOOTER_LEGS = "lm-route-scooter-legs-src"
private const val SRC_ROUTE_SCOOTER_LABELS = "lm-route-scooter-labels-src"
private const val SRC_ROUTE_PINS = "lm-route-pins-src"

// Prefixes for Bucket Layers
private const val PREFIX_DYN_BUS = "lm-dyn-bus"
private const val PREFIX_BASE_BUS = "lm-base-bus"
private const val PREFIX_DYN_REGIO = "lm-dyn-regio"
private const val PREFIX_BASE_REGIO = "lm-base-regio"
private const val PREFIX_DYN_UBAHN_TRAM = "lm-dyn-ubahntram"
private const val PREFIX_DYN_SBAHN = "lm-dyn-sbahn"
private const val PREFIX_BASE_SBAHN = "lm-base-sbahn"

private const val LAYER_DISTRICTS = "lm-districts-layer"
private const val LAYER_TARGET_CONNECTORS_ALT = "lm-target-connectors-alt-layer"
private const val LAYER_TARGET_CONNECTORS = "lm-target-connectors-layer"
private const val LAYER_TARGET_DIST_LABELS = "lm-target-dist-labels-layer"
private const val LAYER_TARGET_STATION_BADGE_CIRCLE = "lm-target-station-badge-circle"
private const val LAYER_TARGET_STATION_BADGE_TEXT = "lm-target-station-badge-text"
private const val LAYER_TARGET_PIN_HALO = "lm-target-pin-halo"
private const val LAYER_TARGET_PIN_CORE = "lm-target-pin-core"
private const val LAYER_USER_LOC_HALO = "lm-user-loc-halo"
private const val LAYER_USER_LOC_CORE = "lm-user-loc-core"
private const val LAYER_ROUTE_TRANSIT_CASING = "lm-route-transit-casing"
private const val LAYER_ROUTE_TRANSIT_LINE = "lm-route-transit-line"
private const val LAYER_ROUTE_TRANSIT_ARROWS = "lm-route-transit-arrows"
private const val LAYER_ROUTE_SCOOTER_LINE = "lm-route-scooter-line"
private const val LAYER_ROUTE_SCOOTER_LABEL = "lm-route-scooter-label"
private const val LAYER_ROUTE_PINS_CIRCLE = "lm-route-pins-circle"
private const val LAYER_ROUTE_PINS_LABEL = "lm-route-pins-label"

private const val IMG_ROUTE_ARROW_WHITE = "lm-route-dir-arrow-white"
private const val IMG_ROUTE_ARROW_DARK = "lm-route-dir-arrow-dark"

private val BUCKET_LAYER_SUFFIXES = listOf(
    "-lines",
    "-line-labels",
    "-stations-circle",
    "-stations-label",
    "-terminals-bg",
    "-terminals-text",
    "-terminal-station-label"
)

private object StyleJsonLoader {
    private val cache = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun loadStyleWithGlyphs(context: Context, styleUrl: String): String? = withContext(Dispatchers.IO) {
        cache[styleUrl]?.let { return@withContext it }

        // 1. Cache-First disk read so offline startup renders downloaded MapLibre tiles immediately
        val diskFile = File(
            File(context.filesDir, "lm_style_cache").apply { mkdirs() },
            "style_${styleUrl.hashCode()}.json"
        )
        if (diskFile.exists() && diskFile.length() > 64L) {
            try {
                val cached = diskFile.readText(Charsets.UTF_8)
                if (cached.trimStart().startsWith("{")) {
                    cache[styleUrl] = cached
                    return@withContext cached
                }
            } catch (_: Exception) {
                // Fall through to network fetch
            }
        }

        // 2. Fetch remotely and persist to disk cache
        try {
            val req = Request.Builder()
                .url(styleUrl)
                .header("User-Agent", "LetzteMeile-Android/0.4")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val raw = resp.body?.string() ?: return@withContext null
                val jsonObj = JSONObject(raw)
                val existingGlyphs = jsonObj.optString("glyphs", "")
                if (existingGlyphs.isBlank()) {
                    jsonObj.put("glyphs", OPENFREEMAP_GLYPHS_URL)
                }
                val patched = jsonObj.toString()
                cache[styleUrl] = patched
                try {
                    diskFile.writeText(patched, Charsets.UTF_8)
                } catch (_: Exception) {
                    // Non-fatal disk cache write failure
                }
                patched
            }
        } catch (_: Exception) {
            null
        }
    }
}

private data class BundledLineTooltipState(
    val id: Long,
    val bucketLabel: String,
    val allRefs: String,
    val refCount: Int
)

private val BUNDLED_LINE_QUERY_LAYERS = arrayOf(
    "$PREFIX_DYN_BUS-line-labels",
    "$PREFIX_DYN_BUS-lines",
    "$PREFIX_DYN_SBAHN-line-labels",
    "$PREFIX_DYN_SBAHN-lines",
    "$PREFIX_DYN_UBAHN_TRAM-line-labels",
    "$PREFIX_DYN_UBAHN_TRAM-lines",
    "$PREFIX_DYN_REGIO-line-labels",
    "$PREFIX_DYN_REGIO-lines"
)

@Composable
fun MunichScooterMapView(
    isMinimalScooterMode: Boolean,
    isProminentLines: Boolean,
    transitMode: MapTransitMode,
    userLocation: GeoPoint?,
    selectedTarget: SearchLocation?,
    nearestStations: List<NearbyStationResult>,
    dynamicOverlay: DynamicTransitOverlay,
    cameraMoveRequest: CameraMoveRequest?,
    onCameraMoveHandled: () -> Unit,
    onMapLongPress: (GeoPoint) -> Unit,
    onMapCameraIdle: (GeoPoint, Double) -> Unit,
    modifier: Modifier = Modifier,
    selectedStationIndex: Int = 0,
    onStationBadgeTap: ((Int) -> Unit)? = null,
    onMapTap: ((GeoPoint) -> Unit)? = null,
    onMapBoundsChanged: ((MapBounds) -> Unit)? = null,
    plannedRoute: GeographicScooterRoute? = null,
    initialCenter: GeoPoint = GeoDistanceUtils.MUNICH_CENTER,
    initialZoom: Double = 11.6,
    requireTwoFingerPan: Boolean = false,
    onSingleFingerPanAttempt: (() -> Unit)? = null,
    darkMapStyleOverride: Boolean? = null,
    bucketVisibilityOverride: TransitBucketVisibility? = null,
    enableBundledLineTooltip: Boolean = true,
    tooltipTopPadding: Dp = 12.dp,
    tooltipUseStatusBarsPadding: Boolean = false,
    attributionAlignment: Alignment = Alignment.BottomEnd,
    attributionPadding: PaddingValues = PaddingValues(end = 8.dp, bottom = 6.dp),
    attributionUseStatusBarsPadding: Boolean = false,
    onOpenLegalInfo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = darkMapStyleOverride ?: isSystemInDarkTheme()
    val effectiveBucketVisibility = bucketVisibilityOverride
        ?: TransitBucketVisibility.fromMapTransitMode(transitMode)

    val latestLongPress by rememberUpdatedState(onMapLongPress)
    val latestStationBadgeTap by rememberUpdatedState(onStationBadgeTap)
    val latestTap by rememberUpdatedState(onMapTap)
    val latestCameraIdle by rememberUpdatedState(onMapCameraIdle)
    val latestBoundsChanged by rememberUpdatedState(onMapBoundsChanged)
    val latestCameraMoveHandled by rememberUpdatedState(onCameraMoveHandled)
    val latestRequireTwoFingerPan by rememberUpdatedState(requireTwoFingerPan)
    val latestSingleFingerAttempt by rememberUpdatedState(onSingleFingerPanAttempt)
    val latestBucketVisibility by rememberUpdatedState(effectiveBucketVisibility)
    val latestDynamicOverlay by rememberUpdatedState(dynamicOverlay)
    val latestPlannedRoute by rememberUpdatedState(plannedRoute)
    val latestEnableBundledTooltip by rememberUpdatedState(enableBundledLineTooltip)

    var bundledLineTooltip by remember { mutableStateOf<BundledLineTooltipState?>(null) }
    var tooltipSeq by remember { mutableLongStateOf(0L) }

    // Auto-hide bundled-line tooltip smoothly after 3 seconds
    LaunchedEffect(bundledLineTooltip?.id) {
        val current = bundledLineTooltip ?: return@LaunchedEffect
        delay(3000L)
        if (bundledLineTooltip?.id == current.id) {
            bundledLineTooltip = null
        }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
        }
    }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyleKey by remember { mutableStateOf<String?>(null) }
    val registeredTargetLabelImages = remember { mutableSetOf<String>() }
    val registeredRouteLabelImages = remember { mutableSetOf<String>() }

    val desiredStyleKey = "${isMinimalScooterMode}_${isDarkTheme}"
    val desiredStyleUrl = when {
        isMinimalScooterMode && isDarkTheme -> STYLE_SCOOTER_DARK
        isMinimalScooterMode && !isDarkTheme -> STYLE_SCOOTER_LIGHT
        !isMinimalScooterMode && isDarkTheme -> STYLE_NORMAL_DARK
        else -> STYLE_NORMAL_LIGHT
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. Load or reload vector style when Scooter/Normal or System Light/Dark changes
    LaunchedEffect(mapLibreMap, desiredStyleKey) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (loadedStyleKey == desiredStyleKey) return@LaunchedEffect

        val patchedJson = StyleJsonLoader.loadStyleWithGlyphs(context, desiredStyleUrl)
        val styleBuilder = if (patchedJson != null) {
            Style.Builder().fromJson(patchedJson)
        } else {
            Style.Builder().fromUri(desiredStyleUrl)
        }

        map.setStyle(styleBuilder) { style ->
            loadedStyleKey = desiredStyleKey
            if (isMinimalScooterMode) {
                applyMinimalScooterStyleFilter(style)
            }
            val currentVis = latestBucketVisibility
            val currentOverlay = latestDynamicOverlay
            val currentPlannedRoute = latestPlannedRoute
            installLetzteMeileLayers(
                style = style,
                isDarkTheme = isDarkTheme,
                isProminentLines = isProminentLines,
                bucketVisibility = currentVis,
                dynamicOverlay = currentOverlay,
                isMinimalScooterMode = isMinimalScooterMode
            )
            registeredTargetLabelImages.clear()
            registeredRouteLabelImages.clear()
            updateBaseBusStopsSource(style)
            updateDynamicOverlaySources(style, currentOverlay)
            updateTargetSources(
                style = style,
                isDarkTheme = isDarkTheme,
                target = selectedTarget,
                nearestStations = nearestStations,
                selectedStationIndex = selectedStationIndex,
                registeredImageIds = registeredTargetLabelImages
            )
            updateUserLocationSource(style, userLocation)
            updatePlannedRouteSources(
                style = style,
                isDarkTheme = isDarkTheme,
                route = currentPlannedRoute,
                registeredImageIds = registeredRouteLabelImages
            )
            applyBucketVisibilityWithRouteSuppression(
                style = style,
                bucketVisibility = currentVis,
                dynamicOverlay = currentOverlay,
                hideBackgroundStationLabels = currentPlannedRoute != null,
                dimBackgroundNetwork = currentPlannedRoute?.transitSegments?.any { it.transitPath.size >= 2 } == true
            )
        }
    }

    // 2. Dynamically adjust line widths, parallel corridor offsets, and station dot radii in place
    LaunchedEffect(mapLibreMap, loadedStyleKey, isProminentLines) {
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect

        val sBahnWidth = buildSBahnLineWidthExpression(isProminentLines)
        val busWidth = buildBusLineWidthExpression(isProminentLines)
        val regioWidth = buildRegioLineWidthExpression(isProminentLines)
        val railOffset = buildParallelLineOffsetExpression(isProminentLines, isBus = false)
        val busOffset = buildParallelLineOffsetExpression(isProminentLines, isBus = true)
        val stationRadius = buildStationRadiusExpression(isProminentLines)

        listOf(PREFIX_BASE_SBAHN, PREFIX_DYN_SBAHN, PREFIX_DYN_UBAHN_TRAM).forEach { p ->
            style.getLayerAs<LineLayer>("$p-lines")?.setProperties(
                PropertyFactory.lineWidth(sBahnWidth),
                PropertyFactory.lineOffset(railOffset)
            )
            style.getLayerAs<CircleLayer>("$p-stations-circle")?.setProperties(
                PropertyFactory.circleRadius(stationRadius)
            )
        }
        listOf(PREFIX_BASE_REGIO, PREFIX_DYN_REGIO).forEach { p ->
            style.getLayerAs<LineLayer>("$p-lines")?.setProperties(
                PropertyFactory.lineWidth(regioWidth),
                PropertyFactory.lineOffset(railOffset)
            )
            style.getLayerAs<CircleLayer>("$p-stations-circle")?.setProperties(
                PropertyFactory.circleRadius(stationRadius)
            )
        }
        listOf(PREFIX_BASE_BUS, PREFIX_DYN_BUS).forEach { p ->
            style.getLayerAs<LineLayer>("$p-lines")?.setProperties(
                PropertyFactory.lineWidth(busWidth),
                PropertyFactory.lineOffset(busOffset)
            )
        }
    }

    // 3. Dynamically toggle transit bucket visibility & suppress static demo layers
    val hasHighlightedTransitRoute = plannedRoute?.transitSegments?.any { it.transitPath.size >= 2 } == true
    LaunchedEffect(mapLibreMap, loadedStyleKey, effectiveBucketVisibility, dynamicOverlay, plannedRoute != null, hasHighlightedTransitRoute) {
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect

        applyBucketVisibilityWithRouteSuppression(
            style = style,
            bucketVisibility = effectiveBucketVisibility,
            dynamicOverlay = dynamicOverlay,
            hideBackgroundStationLabels = plannedRoute != null,
            dimBackgroundNetwork = hasHighlightedTransitRoute
        )
    }

    // 4. Push live MVV / Overpass GeoJSON and immediately hide static demo layers once real data arrives
    LaunchedEffect(mapLibreMap, loadedStyleKey, dynamicOverlay) {
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateDynamicOverlaySources(style, dynamicOverlay)
        applyBucketVisibilityWithRouteSuppression(
            style = style,
            bucketVisibility = latestBucketVisibility,
            dynamicOverlay = dynamicOverlay,
            hideBackgroundStationLabels = plannedRoute != null,
            dimBackgroundNetwork = hasHighlightedTransitRoute
        )
    }

    // 5. Update single-point target pin, numbered station badges & dashed scooter routes (Tab "Karte")
    LaunchedEffect(mapLibreMap, loadedStyleKey, isDarkTheme, selectedTarget, nearestStations, selectedStationIndex) {
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateTargetSources(
            style = style,
            isDarkTheme = isDarkTheme,
            target = selectedTarget,
            nearestStations = nearestStations,
            selectedStationIndex = selectedStationIndex,
            registeredImageIds = registeredTargetLabelImages
        )
    }

    // 6. Update geographic Start-to-Destination route overlay & auto-fit camera (Tab "Route")
    LaunchedEffect(mapLibreMap, loadedStyleKey, isDarkTheme, plannedRoute?.id) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updatePlannedRouteSources(
            style = style,
            isDarkTheme = isDarkTheme,
            route = plannedRoute,
            registeredImageIds = registeredRouteLabelImages
        )

        if (plannedRoute != null) {
            val pts = buildList {
                add(plannedRoute.startLocation.location)
                add(plannedRoute.originStation.station.location)
                plannedRoute.transferStation?.let { add(it.location) }
                add(plannedRoute.destinationStation.station.location)
                add(plannedRoute.destinationLocation.location)
                plannedRoute.transitSegments.forEach { seg ->
                    if (seg.transitPath.size >= 2) {
                        val step = (seg.transitPath.size / 6).coerceAtLeast(1)
                        var idx = 0
                        while (idx < seg.transitPath.size) {
                            add(seg.transitPath[idx])
                            idx += step
                        }
                    }
                }
            }
            val boundsBuilder = LatLngBounds.Builder()
            pts.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
            try {
                val bounds = boundsBuilder.build()
                val density = mapView.resources.displayMetrics.density
                val rawPadLeft = (if (requireTwoFingerPan) 44f else 48f) * density
                val rawPadRight = (if (requireTwoFingerPan) 52f else 48f) * density
                val rawPadTop = (if (requireTwoFingerPan) 58f else 96f) * density
                val rawPadBottom = (if (requireTwoFingerPan) 66f else 84f) * density
                val maxHPad = if (mapView.width > 0) (mapView.width * 0.28f) else rawPadRight
                val maxVPad = if (mapView.height > 0) (mapView.height * 0.34f) else rawPadBottom
                val padLeft = rawPadLeft.coerceAtMost(maxHPad).toInt()
                val padRight = rawPadRight.coerceAtMost(maxHPad).toInt()
                val padTop = rawPadTop.coerceAtMost(maxVPad).toInt()
                val padBottom = rawPadBottom.coerceAtMost(maxVPad).toInt()
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padLeft, padTop, padRight, padBottom),
                    650
                )
            } catch (_: Exception) {
                val mid = plannedRoute.startLocation.location
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(mid.latitude, mid.longitude), 12.2),
                    600
                )
            }
        }
    }

    // 7. Update GPS dot
    LaunchedEffect(mapLibreMap, loadedStyleKey, userLocation) {
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateUserLocationSource(style, userLocation)
    }

    // 8. Smooth camera animation
    LaunchedEffect(mapLibreMap, cameraMoveRequest?.requestId) {
        val req = cameraMoveRequest ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val position = CameraPosition.Builder()
            .target(LatLng(req.target.latitude, req.target.longitude))
            .zoom(req.zoom)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 650)
        latestCameraMoveHandled()
    }

    // 9. Keep scroll gestures aligned with embedded 2-finger mode vs normal 1-finger mode
    LaunchedEffect(mapLibreMap, requireTwoFingerPan) {
        mapLibreMap?.uiSettings?.isScrollGesturesEnabled = !requireTwoFingerPan
    }

    Box(modifier = modifier) {
        @SuppressLint("ClickableViewAccessibility")
        AndroidView(
            factory = { viewContext ->
                val touchSlop = ViewConfiguration.get(viewContext).scaledTouchSlop.toFloat()
                val touchRadiusPx = 22f * viewContext.resources.displayMetrics.density
                var downX = 0f
                var downY = 0f
                var wasMultiTouch = false
                var hintFiredForGesture = false

                mapView.apply {
                    setOnTouchListener { v, event ->
                        // Dismiss bundled-line info tooltip immediately on any new touch/drag/zoom
                        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
                        ) {
                            if (bundledLineTooltip != null) {
                                bundledLineTooltip = null
                            }
                        }

                        val requireTwo = latestRequireTwoFingerPan
                        if (!requireTwo) {
                            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                                event.actionMasked == MotionEvent.ACTION_MOVE
                            ) {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            mapLibreMap?.uiSettings?.isScrollGesturesEnabled = true
                            return@setOnTouchListener false
                        }

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                wasMultiTouch = false
                                hintFiredForGesture = false
                                mapLibreMap?.uiSettings?.isScrollGesturesEnabled = false
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                if (event.pointerCount >= 2) {
                                    wasMultiTouch = true
                                    mapLibreMap?.uiSettings?.isScrollGesturesEnabled = true
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount >= 2 || wasMultiTouch) {
                                    wasMultiTouch = true
                                    mapLibreMap?.uiSettings?.isScrollGesturesEnabled = true
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    val dist = hypot(event.x - downX, event.y - downY)
                                    if (dist > touchSlop * 0.65f && !hintFiredForGesture) {
                                        hintFiredForGesture = true
                                        latestSingleFingerAttempt?.invoke()
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                wasMultiTouch = false
                                hintFiredForGesture = false
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                mapLibreMap?.uiSettings?.isScrollGesturesEnabled = !latestRequireTwoFingerPan
                            }
                        }
                        false
                    }

                    getMapAsync { map ->
                        map.uiSettings.apply {
                            isCompassEnabled = false
                            isTiltGesturesEnabled = false
                            isRotateGesturesEnabled = true
                            isAttributionEnabled = false
                            isLogoEnabled = false
                            isScrollGesturesEnabled = !latestRequireTwoFingerPan
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(initialCenter.latitude, initialCenter.longitude))
                            .zoom(initialZoom)
                            .build()

                        map.addOnCameraMoveStartedListener {
                            if (bundledLineTooltip != null) {
                                bundledLineTooltip = null
                            }
                        }

                        map.addOnMapClickListener { latLng ->
                            val screenPt = map.projection.toScreenLocation(latLng)
                            val hitBox = RectF(
                                screenPt.x - touchRadiusPx,
                                screenPt.y - touchRadiusPx,
                                screenPt.x + touchRadiusPx,
                                screenPt.y + touchRadiusPx
                            )

                            // 0. Check if tap hit one of the numbered station badges (1, 2, 3) directly on the map
                            val badgeTapHandler = latestStationBadgeTap
                            if (badgeTapHandler != null) {
                                try {
                                    val badgeHits = map.queryRenderedFeatures(
                                        hitBox,
                                        LAYER_TARGET_STATION_BADGE_CIRCLE,
                                        LAYER_TARGET_STATION_BADGE_TEXT,
                                        LAYER_TARGET_DIST_LABELS
                                    )
                                    val rankStr = badgeHits.firstOrNull { f ->
                                        f.hasProperty("rank") && !f.getStringProperty("rank").isNullOrBlank()
                                    }?.getStringProperty("rank")
                                    val rankIdx = rankStr?.toIntOrNull()?.minus(1)
                                    if (rankIdx != null && rankIdx in 0..2) {
                                        badgeTapHandler(rankIdx)
                                        return@addOnMapClickListener true
                                    }
                                } catch (_: Exception) {
                                    // Fall through to regular map tap
                                }
                            }

                            // 1. Check if tap hit a bundled (+X) transit line or label first
                            if (latestEnableBundledTooltip) {
                                try {
                                    val features = map.queryRenderedFeatures(hitBox, *BUNDLED_LINE_QUERY_LAYERS)
                                    val bundledHit = features.firstOrNull { f ->
                                        val hasOverflow = f.hasProperty("has_overflow") &&
                                            f.getBooleanProperty("has_overflow") == true
                                        val refCount = if (f.hasProperty("ref_count")) {
                                            f.getNumberProperty("ref_count")?.toInt() ?: 0
                                        } else {
                                            0
                                        }
                                        val allRefs = if (f.hasProperty("all_refs")) {
                                            f.getStringProperty("all_refs").orEmpty()
                                        } else {
                                            ""
                                        }
                                        (hasOverflow || refCount > 3) && allRefs.isNotBlank()
                                    }
                                    if (bundledHit != null) {
                                        val allRefs = bundledHit.getStringProperty("all_refs").orEmpty()
                                        val refCount = bundledHit.getNumberProperty("ref_count")?.toInt() ?: 0
                                        val bucketLabel = if (bundledHit.hasProperty("bucket_label")) {
                                            bundledHit.getStringProperty("bucket_label").orEmpty().ifBlank { "Linien" }
                                        } else {
                                            "Linien"
                                        }
                                        tooltipSeq++
                                        bundledLineTooltip = BundledLineTooltipState(
                                            id = tooltipSeq,
                                            bucketLabel = bucketLabel,
                                            allRefs = allRefs,
                                            refCount = refCount
                                        )
                                        return@addOnMapClickListener true
                                    }
                                } catch (_: Exception) {
                                    // Fall through cleanly to normal tap handling
                                }
                            }

                            // 2. Normal map tap (nearest station lookup)
                            val handler = latestTap
                            if (handler != null) {
                                handler(GeoPoint(latLng.latitude, latLng.longitude))
                                true
                            } else {
                                false
                            }
                        }

                        map.addOnMapLongClickListener { latLng ->
                            bundledLineTooltip = null
                            latestLongPress(GeoPoint(latLng.latitude, latLng.longitude))
                            true
                        }

                        map.addOnCameraIdleListener {
                            val pos = map.cameraPosition
                            val center = pos.target
                            if (center != null) {
                                latestCameraIdle(GeoPoint(center.latitude, center.longitude), pos.zoom)
                            }
                            try {
                                val b = map.projection.visibleRegion.latLngBounds
                                if (b.latitudeNorth > b.latitudeSouth && b.longitudeEast > b.longitudeWest) {
                                    latestBoundsChanged?.invoke(
                                        MapBounds(
                                            south = b.latitudeSouth,
                                            west = b.longitudeWest,
                                            north = b.latitudeNorth,
                                            east = b.longitudeEast
                                        )
                                    )
                                }
                            } catch (_: Exception) {
                                // Ignore transient projection state
                            }
                        }

                        mapLibreMap = map
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle translucent info tooltip for tapped bundled (+X) transit corridors (top placement)
        AnimatedVisibility(
            visible = bundledLineTooltip != null,
            enter = fadeIn(animationSpec = tween(180)) +
                slideInVertically(animationSpec = tween(180)) { -it / 3 },
            exit = fadeOut(animationSpec = tween(260)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .let { if (tooltipUseStatusBarsPadding) it.statusBarsPadding() else it }
                .padding(top = tooltipTopPadding, start = 16.dp, end = 16.dp)
                .widthIn(max = 520.dp)
        ) {
            bundledLineTooltip?.let { tooltip ->
                val icon = when (tooltip.bucketLabel) {
                    "Bus" -> Icons.Filled.DirectionsBus
                    "Regio / DB" -> Icons.Filled.DirectionsRailway
                    else -> Icons.Filled.Train
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 6.dp,
                    modifier = Modifier.testTag("bundled_lines_tooltip")
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Weitere Linien hier (${tooltip.refCount})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = tooltip.allRefs,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Mandatory ODbL + OpenFreeMap clickable attribution pill (positioned cleanly without UI overlap)
        MapAttributionPill(
            onOpenLegalInfo = onOpenLegalInfo,
            modifier = Modifier
                .align(attributionAlignment)
                .let { if (attributionUseStatusBarsPadding) it.statusBarsPadding() else it }
                .padding(attributionPadding)
        )
    }
}

/**
 * Strips distracting POIs, shops, house numbers, icons, and motorway shields in Scooter-Ansicht
 * while keeping Stadtteile AND re-enabling street/road names cleanly when zoomed in (zoom >= 13.5).
 */
private fun applyMinimalScooterStyleFilter(style: Style) {
    val hiddenTokens = listOf(
        "poi", "shop", "housenumber", "house_number", "airport", "aerodrome",
        "highway_shield", "motorway_shield", "water_name", "park_label", "rail_station"
    )
    val streetLabelTokens = listOf(
        "road_label", "road-label", "highway_name", "highway-name",
        "street_label", "road_name", "road-name", "transportation_name"
    )

    style.layers.forEach { layer ->
        val id = layer.id.lowercase(Locale.US)
        val isDistrictPlace = id.contains("place") &&
            (id.contains("suburb") || id.contains("neighbourhood") || id.contains("quarter") ||
                id.contains("city") || id.contains("town"))
        val isStreetLabel = streetLabelTokens.any { token -> id.contains(token) }

        when {
            isDistrictPlace -> Unit
            isStreetLabel -> {
                // Keep street names visible for orientation when zooming in close
                layer.minZoom = maxOf(layer.minZoom, 13.5f)
            }
            hiddenTokens.any { token -> id.contains(token) } -> {
                layer.setProperties(PropertyFactory.visibility(Property.NONE))
            }
        }
    }
}

private fun installLetzteMeileLayers(
    style: Style,
    isDarkTheme: Boolean,
    isProminentLines: Boolean,
    bucketVisibility: TransitBucketVisibility,
    dynamicOverlay: DynamicTransitOverlay,
    isMinimalScooterMode: Boolean
) {
    // 1. Register Static Base Sources
    style.addSource(GeoJsonSource(SRC_DISTRICTS, MunichSBahnNetwork.buildDistrictsGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_SBAHN_LINES, MunichSBahnNetwork.buildLinesGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_SBAHN_LABELS, MunichSBahnNetwork.buildSBahnLineLabelsGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_SBAHN_STATIONS, MunichSBahnNetwork.buildStationsGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_SBAHN_TERMINALS, MunichSBahnNetwork.buildSBahnTerminalBadgesGeoJson()))

    style.addSource(GeoJsonSource(SRC_BASE_BUS_LINES, MunichSBahnNetwork.buildBusLinesGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_BUS_LABELS, MunichSBahnNetwork.buildBusLineLabelsGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_BUS_STATIONS, emptyFeatureCollection()))
    style.addSource(GeoJsonSource(SRC_BASE_BUS_TERMINALS, MunichSBahnNetwork.buildBusTerminalBadgesGeoJson()))

    style.addSource(GeoJsonSource(SRC_BASE_REGIO_LINES, MunichSBahnNetwork.buildRegioLinesGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_REGIO_LABELS, MunichSBahnNetwork.buildRegioLineLabelsGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_REGIO_STATIONS, MunichSBahnNetwork.buildRegioStationsGeoJson()))
    style.addSource(GeoJsonSource(SRC_BASE_REGIO_TERMINALS, MunichSBahnNetwork.buildRegioTerminalBadgesGeoJson()))

    // 2. Register Dynamic Worldwide / DACH Overpass Sources
    listOf(
        SRC_DYN_SBAHN_LINES, SRC_DYN_SBAHN_LABELS, SRC_DYN_SBAHN_STATIONS, SRC_DYN_SBAHN_TERMINALS,
        SRC_DYN_UBAHN_TRAM_LINES, SRC_DYN_UBAHN_TRAM_LABELS, SRC_DYN_UBAHN_TRAM_STATIONS, SRC_DYN_UBAHN_TRAM_TERMINALS,
        SRC_DYN_BUS_LINES, SRC_DYN_BUS_LABELS, SRC_DYN_BUS_STATIONS, SRC_DYN_BUS_TERMINALS,
        SRC_DYN_REGIO_LINES, SRC_DYN_REGIO_LABELS, SRC_DYN_REGIO_STATIONS, SRC_DYN_REGIO_TERMINALS,
        SRC_TARGET_CONNECTORS, SRC_TARGET_DIST_LABELS, SRC_TARGET_STATION_BADGES, SRC_TARGET_PIN, SRC_USER_LOC,
        SRC_ROUTE_TRANSIT_LEGS, SRC_ROUTE_SCOOTER_LEGS, SRC_ROUTE_SCOOTER_LABELS, SRC_ROUTE_PINS
    ).forEach { srcId ->
        style.addSource(GeoJsonSource(srcId, emptyFeatureCollection()))
    }

    // Register crisp Canvas direction chevron icons (white for dark/colored lines, dark for bright lines like S8)
    style.addImage(
        IMG_ROUTE_ARROW_WHITE,
        createDirectionArrowBitmap(
            arrowColor = AndroidColor.WHITE,
            outlineColor = AndroidColor.argb(120, 15, 23, 42)
        )
    )
    style.addImage(
        IMG_ROUTE_ARROW_DARK,
        createDirectionArrowBitmap(
            arrowColor = AndroidColor.parseColor("#111315"),
            outlineColor = AndroidColor.argb(115, 255, 255, 255)
        )
    )

    val labelTextColor = if (isDarkTheme) AndroidColor.parseColor("#F8FAFC") else AndroidColor.parseColor("#0F172A")
    val labelHaloColor = if (isDarkTheme) AndroidColor.parseColor("#0B0F17") else AndroidColor.parseColor("#FFFFFF")
    val districtTextColor = if (isDarkTheme) AndroidColor.parseColor("#94A3B8") else AndroidColor.parseColor("#475569")
    val busTextColor = if (isDarkTheme) AndroidColor.parseColor("#5EEAD4") else AndroidColor.parseColor("#004D5C")
    val separatorColor = if (isDarkTheme) AndroidColor.parseColor("#CBD5E1") else AndroidColor.parseColor("#475569")

    // 3. Munich Stadtteile Labels
    style.addLayer(
        SymbolLayer(LAYER_DISTRICTS, SRC_DISTRICTS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(FONT_BOLD),
            PropertyFactory.textSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(10, 9.5f),
                    Expression.stop(13, 11.5f),
                    Expression.stop(16, 13.0f)
                )
            ),
            PropertyFactory.textLetterSpacing(0.14f),
            PropertyFactory.textColor(districtTextColor),
            PropertyFactory.textHaloColor(labelHaloColor),
            PropertyFactory.textHaloWidth(1.8f),
            PropertyFactory.textOpacity(if (isMinimalScooterMode) 0.88f else 0.52f),
            PropertyFactory.textAllowOverlap(false)
        )
    )

    val hasDynSBahn = hasGeoJsonFeatures(dynamicOverlay.sBahnLinesGeoJson)
    val hasDynBus = hasGeoJsonFeatures(dynamicOverlay.busLinesGeoJson)
    val hasDynRegio = hasGeoJsonFeatures(dynamicOverlay.regioLinesGeoJson)

    val sBahnDynVis = if (bucketVisibility.showSBahn) Property.VISIBLE else Property.NONE
    val sBahnBaseVis = if (bucketVisibility.showSBahn && !hasDynSBahn) Property.VISIBLE else Property.NONE
    val ubahnTramDynVis = if (bucketVisibility.showUbahnTram) Property.VISIBLE else Property.NONE
    val busDynVis = if (bucketVisibility.showBus) Property.VISIBLE else Property.NONE
    val busBaseVis = if (bucketVisibility.showBus && !hasDynBus) Property.VISIBLE else Property.NONE
    val regioDynVis = if (bucketVisibility.showRegioLong) Property.VISIBLE else Property.NONE
    val regioBaseVis = if (bucketVisibility.showRegioLong && !hasDynRegio) Property.VISIBLE else Property.NONE

    val sBahnWidth = buildSBahnLineWidthExpression(isProminentLines)
    val busWidth = buildBusLineWidthExpression(isProminentLines)
    val regioWidth = buildRegioLineWidthExpression(isProminentLines)
    val railOffset = buildParallelLineOffsetExpression(isProminentLines, isBus = false)
    val busOffset = buildParallelLineOffsetExpression(isProminentLines, isBus = true)
    val stationRadius = buildStationRadiusExpression(isProminentLines)
    val busStopRadius = buildBusStopRadiusExpression()

    // 4. Bus Buckets (Dynamic MVV/Overpass + Static Fallback Base)
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_DYN_BUS,
        linesSrc = SRC_DYN_BUS_LINES,
        labelsSrc = SRC_DYN_BUS_LABELS,
        stationsSrc = SRC_DYN_BUS_STATIONS,
        terminalsSrc = SRC_DYN_BUS_TERMINALS,
        visibility = busDynVis,
        lineWidthExpr = busWidth,
        lineOffsetExpr = busOffset,
        stationRadiusExpr = busStopRadius,
        labelTextColor = busTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.84f,
        stationMinZoom = 12.2f,
        labelSpacingPx = 195f,
        terminalRadiusBase = 9.5f,
        isBusBucket = true
    )
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_BASE_BUS,
        linesSrc = SRC_BASE_BUS_LINES,
        labelsSrc = SRC_BASE_BUS_LABELS,
        stationsSrc = SRC_BASE_BUS_STATIONS,
        terminalsSrc = SRC_BASE_BUS_TERMINALS,
        visibility = busBaseVis,
        lineWidthExpr = busWidth,
        lineOffsetExpr = busOffset,
        stationRadiusExpr = busStopRadius,
        labelTextColor = busTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.88f,
        stationMinZoom = 11.5f,
        labelSpacingPx = 195f,
        terminalRadiusBase = 10.0f,
        isBusBucket = true
    )

    // 5. Regio & Fernverkehr Buckets (RE, RB, ICE, IC, EC — Dynamic + Static Fallback Base)
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_DYN_REGIO,
        linesSrc = SRC_DYN_REGIO_LINES,
        labelsSrc = SRC_DYN_REGIO_LABELS,
        stationsSrc = SRC_DYN_REGIO_STATIONS,
        terminalsSrc = SRC_DYN_REGIO_TERMINALS,
        visibility = regioDynVis,
        lineWidthExpr = regioWidth,
        lineOffsetExpr = railOffset,
        stationRadiusExpr = stationRadius,
        labelTextColor = labelTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.90f,
        stationMinZoom = 9.5f,
        labelSpacingPx = 225f,
        terminalRadiusBase = 12.0f,
        isBusBucket = false
    )
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_BASE_REGIO,
        linesSrc = SRC_BASE_REGIO_LINES,
        labelsSrc = SRC_BASE_REGIO_LABELS,
        stationsSrc = SRC_BASE_REGIO_STATIONS,
        terminalsSrc = SRC_BASE_REGIO_TERMINALS,
        visibility = regioBaseVis,
        lineWidthExpr = regioWidth,
        lineOffsetExpr = railOffset,
        stationRadiusExpr = stationRadius,
        labelTextColor = labelTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.94f,
        stationMinZoom = 8.8f,
        labelSpacingPx = 220f,
        terminalRadiusBase = 12.5f,
        isBusBucket = false
    )

    // 6a. Separate U-Bahn & Tram Bucket (exclusively visible in "Alle" mode; hidden in pure S-Bahn mode)
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_DYN_UBAHN_TRAM,
        linesSrc = SRC_DYN_UBAHN_TRAM_LINES,
        labelsSrc = SRC_DYN_UBAHN_TRAM_LABELS,
        stationsSrc = SRC_DYN_UBAHN_TRAM_STATIONS,
        terminalsSrc = SRC_DYN_UBAHN_TRAM_TERMINALS,
        visibility = ubahnTramDynVis,
        lineWidthExpr = sBahnWidth,
        lineOffsetExpr = railOffset,
        stationRadiusExpr = stationRadius,
        labelTextColor = labelTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.88f,
        stationMinZoom = 10.6f,
        labelSpacingPx = 210f,
        terminalRadiusBase = 10.5f,
        isBusBucket = false
    )

    // 6b. Pure S-Bahn Buckets (Dynamic MVV/Overpass S1..S20 + Static Munich Fallback Base)
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_DYN_SBAHN,
        linesSrc = SRC_DYN_SBAHN_LINES,
        labelsSrc = SRC_DYN_SBAHN_LABELS,
        stationsSrc = SRC_DYN_SBAHN_STATIONS,
        terminalsSrc = SRC_DYN_SBAHN_TERMINALS,
        visibility = sBahnDynVis,
        lineWidthExpr = sBahnWidth,
        lineOffsetExpr = railOffset,
        stationRadiusExpr = stationRadius,
        labelTextColor = labelTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.90f,
        stationMinZoom = 10.2f,
        labelSpacingPx = 210f,
        terminalRadiusBase = 11.0f,
        isBusBucket = false
    )
    addTransitBucketLayers(
        style = style,
        prefix = PREFIX_BASE_SBAHN,
        linesSrc = SRC_BASE_SBAHN_LINES,
        labelsSrc = SRC_BASE_SBAHN_LABELS,
        stationsSrc = SRC_BASE_SBAHN_STATIONS,
        terminalsSrc = SRC_BASE_SBAHN_TERMINALS,
        visibility = sBahnBaseVis,
        lineWidthExpr = sBahnWidth,
        lineOffsetExpr = railOffset,
        stationRadiusExpr = stationRadius,
        labelTextColor = labelTextColor,
        labelHaloColor = labelHaloColor,
        separatorColor = separatorColor,
        lineOpacity = 0.95f,
        stationMinZoom = 9.5f,
        labelSpacingPx = 210f,
        terminalRadiusBase = 11.5f,
        isBusBucket = false
    )

    // 7. Scooter Street Routes from Selected Target to Nearest Stations (1, 2, 3):
    //    Unselected alternatives render subtly underneath; selected option renders prominently on top!
    style.addLayer(
        LineLayer(LAYER_TARGET_CONNECTORS_ALT, SRC_TARGET_CONNECTORS)
            .withFilter(Expression.neq(Expression.get("isSelected"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.lineColor(if (isDarkTheme) AndroidColor.parseColor("#64748B") else AndroidColor.parseColor("#475569")),
                PropertyFactory.lineWidth(2.4f),
                PropertyFactory.lineOpacity(0.35f),
                PropertyFactory.lineDasharray(arrayOf(2.0f, 1.2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
    )
    style.addLayer(
        LineLayer(LAYER_TARGET_CONNECTORS, SRC_TARGET_CONNECTORS)
            .withFilter(Expression.eq(Expression.get("isSelected"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.lineColor(if (isDarkTheme) AndroidColor.parseColor("#2DD4BF") else AndroidColor.parseColor("#0065BD")),
                PropertyFactory.lineWidth(4.0f),
                PropertyFactory.lineOpacity(1.0f),
                PropertyFactory.lineDasharray(arrayOf(2.0f, 1.2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
    )

    style.addLayer(
        SymbolLayer(LAYER_TARGET_DIST_LABELS, SRC_TARGET_DIST_LABELS).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_POINT),
            PropertyFactory.iconImage(Expression.get("labelImageId")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconOpacity(
                Expression.switchCase(
                    Expression.eq(Expression.get("isSelected"), Expression.literal(true)),
                    Expression.literal(1.0f),
                    Expression.literal(0.76f)
                )
            ),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )

    // Numbered circular badges (1, 2, 3) directly at each of the 3 target stations
    style.addLayer(
        CircleLayer(LAYER_TARGET_STATION_BADGE_CIRCLE, SRC_TARGET_STATION_BADGES).withProperties(
            PropertyFactory.circleColor(
                Expression.switchCase(
                    Expression.eq(Expression.get("isSelected"), Expression.literal(true)),
                    Expression.literal(if (isDarkTheme) "#0D9488" else "#0065BD"),
                    Expression.literal(if (isDarkTheme) "#334155" else "#64748B")
                )
            ),
            PropertyFactory.circleOpacity(
                Expression.switchCase(
                    Expression.eq(Expression.get("isSelected"), Expression.literal(true)),
                    Expression.literal(1.0f),
                    Expression.literal(0.65f)
                )
            ),
            PropertyFactory.circleRadius(
                Expression.switchCase(
                    Expression.eq(Expression.get("isSelected"), Expression.literal(true)),
                    Expression.literal(12.5f),
                    Expression.literal(9.5f)
                )
            ),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(
                    Expression.eq(Expression.get("isSelected"), Expression.literal(true)),
                    Expression.literal(2.6f),
                    Expression.literal(1.8f)
                )
            )
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_TARGET_STATION_BADGE_TEXT, SRC_TARGET_STATION_BADGES).withProperties(
            PropertyFactory.textField(Expression.get("rank")),
            PropertyFactory.textFont(FONT_BOLD),
            PropertyFactory.textSize(
                Expression.switchCase(
                    Expression.eq(Expression.get("isSelected"), Expression.literal(true)),
                    Expression.literal(12.5f),
                    Expression.literal(10.0f)
                )
            ),
            PropertyFactory.textColor(AndroidColor.WHITE),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
        )
    )

    // 8. GPS User Location Dot
    style.addLayer(
        CircleLayer(LAYER_USER_LOC_HALO, SRC_USER_LOC).withProperties(
            PropertyFactory.circleColor(AndroidColor.parseColor("#3B82F6")),
            PropertyFactory.circleOpacity(0.24f),
            PropertyFactory.circleRadius(14f)
        )
    )
    style.addLayer(
        CircleLayer(LAYER_USER_LOC_CORE, SRC_USER_LOC).withProperties(
            PropertyFactory.circleColor(AndroidColor.parseColor("#2563EB")),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f),
            PropertyFactory.circleRadius(6.5f)
        )
    )

    // 9. Selected Target Pin
    style.addLayer(
        CircleLayer(LAYER_TARGET_PIN_HALO, SRC_TARGET_PIN).withProperties(
            PropertyFactory.circleColor(AndroidColor.parseColor("#0D9488")),
            PropertyFactory.circleOpacity(0.28f),
            PropertyFactory.circleRadius(16f)
        )
    )
    style.addLayer(
        CircleLayer(LAYER_TARGET_PIN_CORE, SRC_TARGET_PIN).withProperties(
            PropertyFactory.circleColor(AndroidColor.parseColor("#0D9488")),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(3.0f),
            PropertyFactory.circleRadius(8.0f)
        )
    )

    // 10. Geographic Start-to-Destination Route Overlay:
    //     a) Highlighted ÖPNV connection (casing + vibrant line + directional chevrons along travel direction)
    val transitCasingColor = if (isDarkTheme) AndroidColor.parseColor("#0B0F17") else AndroidColor.WHITE
    style.addLayer(
        LineLayer(LAYER_ROUTE_TRANSIT_CASING, SRC_ROUTE_TRANSIT_LEGS).withProperties(
            PropertyFactory.lineColor(transitCasingColor),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(9, 7.6f),
                    Expression.stop(12, 10.2f),
                    Expression.stop(15, 12.8f)
                )
            ),
            PropertyFactory.lineOpacity(0.92f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(LAYER_ROUTE_TRANSIT_LINE, SRC_ROUTE_TRANSIT_LEGS).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(9, 4.8f),
                    Expression.stop(12, 6.8f),
                    Expression.stop(15, 9.0f)
                )
            ),
            PropertyFactory.lineOpacity(1.0f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_ROUTE_TRANSIT_ARROWS, SRC_ROUTE_TRANSIT_LEGS).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            PropertyFactory.symbolSpacing(88f),
            PropertyFactory.iconImage(Expression.get("arrowImageId")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconKeepUpright(false),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconOpacity(0.94f)
        )
    )

    //     b) Dashed real street scooter legs & station pins on top
    style.addLayer(
        LineLayer(LAYER_ROUTE_SCOOTER_LINE, SRC_ROUTE_SCOOTER_LEGS).withProperties(
            PropertyFactory.lineColor(if (isDarkTheme) AndroidColor.parseColor("#2DD4BF") else AndroidColor.parseColor("#0D9488")),
            PropertyFactory.lineWidth(3.8f),
            PropertyFactory.lineDasharray(arrayOf(2.0f, 1.2f)),
            PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_ROUTE_SCOOTER_LABEL, SRC_ROUTE_SCOOTER_LABELS).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_POINT),
            PropertyFactory.iconImage(Expression.get("labelImageId")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )
    style.addLayer(
        CircleLayer(LAYER_ROUTE_PINS_CIRCLE, SRC_ROUTE_PINS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(2.8f),
            PropertyFactory.circleRadius(8.0f)
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_ROUTE_PINS_LABEL, SRC_ROUTE_PINS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(FONT_BOLD),
            PropertyFactory.textSize(11.0f),
            PropertyFactory.textVariableAnchor(
                arrayOf(
                    Property.TEXT_ANCHOR_TOP,
                    Property.TEXT_ANCHOR_BOTTOM,
                    Property.TEXT_ANCHOR_LEFT,
                    Property.TEXT_ANCHOR_RIGHT
                )
            ),
            PropertyFactory.textRadialOffset(1.05f),
            PropertyFactory.textColor(labelTextColor),
            PropertyFactory.textHaloColor(labelHaloColor),
            PropertyFactory.textHaloWidth(2.4f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textPadding(3f)
        )
    )
}

/**
 * Adds a full 7-layer transit bucket:
 *  1) Parallel offset lines in official colors (`line-offset` via `offsetIndex`)
 *  2) Traveling multi-color line labels (`Expression.format` coloring each line name individually)
 *  3) Non-terminal station dots (filtered `isTerminal != true` so terminal badges have ZERO duplicate circles!)
 *  4) Non-terminal station names with contrast halo
 *  5) Integrated terminal badge circle (replaces station circle directly at endpoint!)
 *  6) Integrated terminal badge line text (e.g., S2, RE2, ICE, 50)
 *  7) Terminal station name anchored cleanly directly underneath the terminal badge
 */
private fun addTransitBucketLayers(
    style: Style,
    prefix: String,
    linesSrc: String,
    labelsSrc: String,
    stationsSrc: String,
    terminalsSrc: String,
    visibility: String,
    lineWidthExpr: Expression,
    lineOffsetExpr: Expression,
    stationRadiusExpr: Expression,
    labelTextColor: Int,
    labelHaloColor: Int,
    separatorColor: Int,
    lineOpacity: Float,
    stationMinZoom: Float,
    labelSpacingPx: Float,
    terminalRadiusBase: Float,
    isBusBucket: Boolean
) {
    // 1. Parallel Offset Lines
    style.addLayer(
        LineLayer("$prefix-lines", linesSrc).withProperties(
            PropertyFactory.visibility(visibility),
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(lineWidthExpr),
            PropertyFactory.lineOffset(lineOffsetExpr),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(lineOpacity)
        )
    )

    // 2. Multi-Color Traveling Line Labels
    style.addLayer(
        SymbolLayer("$prefix-line-labels", labelsSrc).withProperties(
            PropertyFactory.visibility(visibility),
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            PropertyFactory.symbolSpacing(labelSpacingPx),
            PropertyFactory.textField(buildMultiColorLineLabelExpression(separatorColor)),
            PropertyFactory.textFont(FONT_BOLD),
            PropertyFactory.textSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(9.5, if (isBusBucket) 9.2f else 9.8f),
                    Expression.stop(12.5, if (isBusBucket) 10.6f else 11.4f),
                    Expression.stop(15.5, if (isBusBucket) 11.8f else 12.6f)
                )
            ),
            PropertyFactory.textHaloColor(labelHaloColor),
            PropertyFactory.textHaloWidth(2.6f),
            PropertyFactory.textHaloBlur(0.3f),
            PropertyFactory.textKeepUpright(true),
            PropertyFactory.textMaxAngle(30f),
            PropertyFactory.textPadding(4f)
        )
    )

    // 3. Intermediate Station Dots (terminal stations excluded -> zero duplicate dot!)
    style.addLayer(
        CircleLayer("$prefix-stations-circle", stationsSrc)
            .withFilter(Expression.neq(Expression.get("isTerminal"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.visibility(visibility),
                PropertyFactory.circleColor(if (isBusBucket) AndroidColor.parseColor("#00586A") else labelHaloColor),
                if (isBusBucket) {
                    PropertyFactory.circleStrokeColor(labelHaloColor)
                } else {
                    PropertyFactory.circleStrokeColor(Expression.get("color"))
                },
                PropertyFactory.circleStrokeWidth(if (isBusBucket) 1.4f else 2.2f),
                PropertyFactory.circleRadius(stationRadiusExpr)
            )
    )

    // 4. Intermediate Station Names
    style.addLayer(
        SymbolLayer("$prefix-stations-label", stationsSrc)
            .withFilter(Expression.neq(Expression.get("isTerminal"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.visibility(visibility),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(if (isBusBucket) FONT_REGULAR else FONT_BOLD),
                PropertyFactory.textSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(9.5, if (isBusBucket) 9.2f else 9.5f),
                        Expression.stop(12.5, if (isBusBucket) 10.6f else 11.2f),
                        Expression.stop(15.5, if (isBusBucket) 11.8f else 12.8f)
                    )
                ),
                PropertyFactory.textVariableAnchor(
                    arrayOf(
                        Property.TEXT_ANCHOR_TOP,
                        Property.TEXT_ANCHOR_BOTTOM,
                        Property.TEXT_ANCHOR_LEFT,
                        Property.TEXT_ANCHOR_RIGHT
                    )
                ),
                PropertyFactory.textRadialOffset(if (isBusBucket) 0.65f else 0.8f),
                PropertyFactory.textColor(labelTextColor),
                PropertyFactory.textHaloColor(labelHaloColor),
                PropertyFactory.textHaloWidth(2.1f),
                PropertyFactory.textHaloBlur(0.35f),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textPadding(2f)
            ).apply {
                minZoom = stationMinZoom
            }
    )

    // 5. Integrated Terminal Badge Background (IS the endpoint marker directly)
    style.addLayer(
        CircleLayer("$prefix-terminals-bg", terminalsSrc).withProperties(
            PropertyFactory.visibility(visibility),
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(9, terminalRadiusBase - 1.8f),
                    Expression.stop(12, terminalRadiusBase),
                    Expression.stop(15, terminalRadiusBase + 1.8f)
                )
            ),
            PropertyFactory.circleStrokeColor(labelHaloColor),
            PropertyFactory.circleStrokeWidth(2.0f)
        )
    )

    // 6. Integrated Terminal Badge Line Label inside the endpoint marker
    style.addLayer(
        SymbolLayer("$prefix-terminals-text", terminalsSrc).withProperties(
            PropertyFactory.visibility(visibility),
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(FONT_BOLD),
            PropertyFactory.textSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(9, 8.2f),
                    Expression.stop(12, 9.6f),
                    Expression.stop(15, 10.6f)
                )
            ),
            PropertyFactory.textColor(Expression.get("textColor")),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
        )
    )

    // 7. Terminal Station Name placed directly UNDERNEATH the integrated terminal badge
    style.addLayer(
        SymbolLayer("$prefix-terminal-station-label", stationsSrc)
            .withFilter(Expression.eq(Expression.get("isTerminal"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.visibility(visibility),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(if (isBusBucket) FONT_REGULAR else FONT_BOLD),
                PropertyFactory.textSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(9.5, 9.5f),
                        Expression.stop(12.5, 11.2f),
                        Expression.stop(15.5, 12.6f)
                    )
                ),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 1.38f)),
                PropertyFactory.textColor(labelTextColor),
                PropertyFactory.textHaloColor(labelHaloColor),
                PropertyFactory.textHaloWidth(2.2f),
                PropertyFactory.textHaloBlur(0.35f),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textPadding(2f)
            ).apply {
                minZoom = if (isBusBucket) stationMinZoom else 9.0f
            }
    )
}

/**
 * Formats multi-line corridor labels (`p1`/`c1` .. `p4`/`c4`) so each line number is rendered
 * in its own official line color, separated by subtle mid-dots.
 */
private fun buildMultiColorLineLabelExpression(separatorColor: Int): Expression {
    val sep2 = Expression.switchCase(
        Expression.neq(Expression.get("p2"), Expression.literal("")),
        Expression.literal(" · "),
        Expression.literal("")
    )
    val sep3 = Expression.switchCase(
        Expression.neq(Expression.get("p3"), Expression.literal("")),
        Expression.literal(" · "),
        Expression.literal("")
    )
    val sep4 = Expression.switchCase(
        Expression.neq(Expression.get("p4"), Expression.literal("")),
        Expression.literal(" · "),
        Expression.literal("")
    )

    return Expression.format(
        Expression.formatEntry(
            Expression.get("p1"),
            Expression.FormatOption.formatTextColor(Expression.toColor(Expression.get("c1")))
        ),
        Expression.formatEntry(
            sep2,
            Expression.FormatOption.formatTextColor(separatorColor)
        ),
        Expression.formatEntry(
            Expression.get("p2"),
            Expression.FormatOption.formatTextColor(Expression.toColor(Expression.get("c2")))
        ),
        Expression.formatEntry(
            sep3,
            Expression.FormatOption.formatTextColor(separatorColor)
        ),
        Expression.formatEntry(
            Expression.get("p3"),
            Expression.FormatOption.formatTextColor(Expression.toColor(Expression.get("c3")))
        ),
        Expression.formatEntry(
            sep4,
            Expression.FormatOption.formatTextColor(separatorColor)
        ),
        Expression.formatEntry(
            Expression.get("p4"),
            Expression.FormatOption.formatTextColor(Expression.toColor(Expression.get("c4")))
        )
    )
}

private fun applyBucketVisibilityWithRouteSuppression(
    style: Style,
    bucketVisibility: TransitBucketVisibility,
    dynamicOverlay: DynamicTransitOverlay,
    hideBackgroundStationLabels: Boolean,
    dimBackgroundNetwork: Boolean = false
) {
    val hasDynSBahn = hasGeoJsonFeatures(dynamicOverlay.sBahnLinesGeoJson)
    val hasDynBus = hasGeoJsonFeatures(dynamicOverlay.busLinesGeoJson)
    val hasDynRegio = hasGeoJsonFeatures(dynamicOverlay.regioLinesGeoJson)

    setBucketVisibility(style, PREFIX_BASE_SBAHN, bucketVisibility.showSBahn && !hasDynSBahn, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.95f)
    setBucketVisibility(style, PREFIX_DYN_SBAHN, bucketVisibility.showSBahn, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.90f)
    setBucketVisibility(style, PREFIX_DYN_UBAHN_TRAM, bucketVisibility.showUbahnTram, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.88f)
    setBucketVisibility(style, PREFIX_BASE_BUS, bucketVisibility.showBus && !hasDynBus, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.88f)
    setBucketVisibility(style, PREFIX_DYN_BUS, bucketVisibility.showBus, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.84f)
    setBucketVisibility(style, PREFIX_BASE_REGIO, bucketVisibility.showRegioLong && !hasDynRegio, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.94f)
    setBucketVisibility(style, PREFIX_DYN_REGIO, bucketVisibility.showRegioLong, hideBackgroundStationLabels, dimBackgroundNetwork, normalLineOpacity = 0.90f)
}

private fun hasGeoJsonFeatures(geoJson: String): Boolean =
    geoJson.isNotBlank() && geoJson != emptyFeatureCollection()

private fun setBucketVisibility(
    style: Style,
    prefix: String,
    visible: Boolean,
    hideStationLabels: Boolean = false,
    dimBackgroundNetwork: Boolean = false,
    normalLineOpacity: Float = 0.90f
) {
    val lineAlpha = if (dimBackgroundNetwork) 0.18f else normalLineOpacity
    val labelAlpha = if (dimBackgroundNetwork) 0.20f else 1.0f
    val circleAlpha = if (dimBackgroundNetwork) 0.20f else 1.0f

    BUCKET_LAYER_SUFFIXES.forEach { suffix ->
        val isStationLabelLayer = suffix == "-stations-label" || suffix == "-terminal-station-label"
        val layerVisible = visible && !(hideStationLabels && isStationLabelLayer)
        val vis = if (layerVisible) Property.VISIBLE else Property.NONE
        val layer = style.getLayer("$prefix$suffix") ?: return@forEach
        layer.setProperties(PropertyFactory.visibility(vis))
        when (suffix) {
            "-lines" -> (layer as? LineLayer)?.setProperties(PropertyFactory.lineOpacity(lineAlpha))
            "-line-labels", "-terminals-text" -> (layer as? SymbolLayer)?.setProperties(PropertyFactory.textOpacity(labelAlpha))
            "-stations-circle", "-terminals-bg" -> (layer as? CircleLayer)?.setProperties(
                PropertyFactory.circleOpacity(circleAlpha),
                PropertyFactory.circleStrokeOpacity(circleAlpha)
            )
        }
    }
}

private fun buildSBahnLineWidthExpression(isProminent: Boolean): Expression {
    return if (isProminent) {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, 3.2f),
            Expression.stop(12, 5.0f),
            Expression.stop(15, 6.8f),
            Expression.stop(18, 8.6f)
        )
    } else {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, 1.1f),
            Expression.stop(12, 1.7f),
            Expression.stop(15, 2.4f),
            Expression.stop(18, 3.1f)
        )
    }
}

private fun buildRegioLineWidthExpression(isProminent: Boolean): Expression {
    return if (isProminent) {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(8, 3.4f),
            Expression.stop(12, 5.4f),
            Expression.stop(15, 7.2f),
            Expression.stop(18, 8.8f)
        )
    } else {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(8, 1.3f),
            Expression.stop(12, 2.0f),
            Expression.stop(15, 2.7f),
            Expression.stop(18, 3.4f)
        )
    }
}

private fun buildBusLineWidthExpression(isProminent: Boolean): Expression {
    return if (isProminent) {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, 2.2f),
            Expression.stop(12, 3.6f),
            Expression.stop(15, 5.2f),
            Expression.stop(18, 6.6f)
        )
    } else {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, 0.9f),
            Expression.stop(12, 1.35f),
            Expression.stop(15, 1.9f),
            Expression.stop(18, 2.5f)
        )
    }
}

private fun buildParallelLineOffsetExpression(isProminent: Boolean, isBus: Boolean): Expression {
    // Shared tracks/streets are bundled into a single clean line with combined multi-line labels
    // (e.g. "633 · 634 · 636") rather than fanning parallel bars across the map.
    return Expression.literal(0.0f)
}

private fun buildStationRadiusExpression(isProminent: Boolean): Expression {
    return if (isProminent) {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(10, 4.0f),
            Expression.stop(13, 5.8f),
            Expression.stop(16, 7.4f)
        )
    } else {
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(10, 2.5f),
            Expression.stop(13, 3.9f),
            Expression.stop(16, 5.2f)
        )
    }
}

private fun buildBusStopRadiusExpression(): Expression {
    return Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(10.5, 2.5f),
        Expression.stop(13.5, 4.2f),
        Expression.stop(16.5, 5.6f)
    )
}

private fun updateBaseBusStopsSource(style: Style) {
    val features = JSONArray()
    MunichSBahnNetwork.busTerminalStopsAsStations.forEach { stop ->
        val isTerminal = MunichSBahnNetwork.isBusTerminalStop(stop.id)
        val f = JSONObject().apply {
            put("type", "Feature")
            put("properties", JSONObject().apply {
                put("id", stop.id)
                put("name", stop.name)
                put("color", "#00586A")
                put("isTerminal", isTerminal)
            })
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().put(stop.location.longitude).put(stop.location.latitude))
            })
        }
        features.put(f)
    }
    val json = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
    style.getSourceAs<GeoJsonSource>(SRC_BASE_BUS_STATIONS)?.setGeoJson(json)
}

private fun updateDynamicOverlaySources(style: Style, overlay: DynamicTransitOverlay) {
    style.getSourceAs<GeoJsonSource>(SRC_DYN_SBAHN_LINES)?.setGeoJson(overlay.sBahnLinesGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_SBAHN_LABELS)?.setGeoJson(overlay.sBahnLineLabelsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_SBAHN_STATIONS)?.setGeoJson(overlay.sBahnStationsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_SBAHN_TERMINALS)?.setGeoJson(overlay.sBahnTerminalsGeoJson)

    style.getSourceAs<GeoJsonSource>(SRC_DYN_UBAHN_TRAM_LINES)?.setGeoJson(overlay.ubahnTramLinesGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_UBAHN_TRAM_LABELS)?.setGeoJson(overlay.ubahnTramLineLabelsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_UBAHN_TRAM_STATIONS)?.setGeoJson(overlay.ubahnTramStationsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_UBAHN_TRAM_TERMINALS)?.setGeoJson(overlay.ubahnTramTerminalsGeoJson)

    style.getSourceAs<GeoJsonSource>(SRC_DYN_BUS_LINES)?.setGeoJson(overlay.busLinesGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_BUS_LABELS)?.setGeoJson(overlay.busLineLabelsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_BUS_STATIONS)?.setGeoJson(overlay.busStationsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_BUS_TERMINALS)?.setGeoJson(overlay.busTerminalsGeoJson)

    style.getSourceAs<GeoJsonSource>(SRC_DYN_REGIO_LINES)?.setGeoJson(overlay.regioLinesGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_REGIO_LABELS)?.setGeoJson(overlay.regioLineLabelsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_REGIO_STATIONS)?.setGeoJson(overlay.regioStationsGeoJson)
    style.getSourceAs<GeoJsonSource>(SRC_DYN_REGIO_TERMINALS)?.setGeoJson(overlay.regioTerminalsGeoJson)
}

private val routeLabelBitmapSeq = AtomicLong(0L)

private fun updateTargetSources(
    style: Style,
    isDarkTheme: Boolean,
    target: SearchLocation?,
    nearestStations: List<NearbyStationResult>,
    selectedStationIndex: Int,
    registeredImageIds: MutableSet<String>
) {
    val previousImageIds = registeredImageIds.toList()
    if (target == null) {
        style.getSourceAs<GeoJsonSource>(SRC_TARGET_PIN)?.setGeoJson(emptyFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_TARGET_CONNECTORS)?.setGeoJson(emptyFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_TARGET_DIST_LABELS)?.setGeoJson(emptyFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_TARGET_STATION_BADGES)?.setGeoJson(emptyFeatureCollection())
        previousImageIds.forEach { style.removeImage(it) }
        registeredImageIds.clear()
        return
    }

    val pinJson = JSONObject().apply {
        put("type", "FeatureCollection")
        put(
            "features",
            JSONArray().put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", JSONObject().put("name", target.name))
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put(
                            "coordinates",
                            JSONArray().put(target.location.longitude).put(target.location.latitude)
                        )
                    })
                }
            )
        )
    }.toString()
    style.getSourceAs<GeoJsonSource>(SRC_TARGET_PIN)?.setGeoJson(pinJson)

    val topThree = nearestStations.take(3)
    val activeIdx = selectedStationIndex.coerceIn(0, (topThree.lastIndex).coerceAtLeast(0))
    val lineFeatures = JSONArray()
    val distLabelFeatures = JSONArray()
    val badgeFeatures = JSONArray()
    val newImageIds = mutableListOf<String>()

    val labelHaloColor = if (isDarkTheme) AndroidColor.parseColor("#0B0F17") else AndroidColor.parseColor("#FFFFFF")

    // Emit unselected items first and selected item last so active badge & label always draw on top
    val orderedIndices = topThree.indices.sortedBy { if (it == activeIdx) 1 else 0 }
    orderedIndices.forEach { index ->
        val result = topThree[index]
        val rank = (index + 1).toString()
        val isSelected = index == activeIdx

        // Numbered badge (1, 2, 3) directly at the target station
        badgeFeatures.put(
            JSONObject().apply {
                put("type", "Feature")
                put(
                    "properties",
                    JSONObject()
                        .put("rank", rank)
                        .put("isSelected", isSelected)
                )
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put(
                        "coordinates",
                        JSONArray()
                            .put(result.station.location.longitude)
                            .put(result.station.location.latitude)
                    )
                })
            }
        )

        // Draw real street-routed polylines only (no straight crow-flies Luftlinie)
        if (result.routePath.size >= 2) {
            val distanceText = String.format(
                Locale.GERMAN,
                "%.1f km · %d min",
                result.estimatedScooterKm,
                result.estimatedScooterMinutes
            )
            val badgeFill = when {
                isSelected && isDarkTheme -> AndroidColor.parseColor("#34D399")
                isSelected -> AndroidColor.parseColor("#059669")
                isDarkTheme -> AndroidColor.parseColor("#2E3440")
                else -> AndroidColor.parseColor("#64748B")
            }
            val badgeTextColor = when {
                isSelected && isDarkTheme -> AndroidColor.parseColor("#052E1E")
                isSelected -> AndroidColor.WHITE
                isDarkTheme -> AndroidColor.parseColor("#F3F4F6")
                else -> AndroidColor.WHITE
            }
            val badgeBorderColor = when {
                isSelected && isDarkTheme -> AndroidColor.parseColor("#052E1E")
                isSelected -> AndroidColor.WHITE
                isDarkTheme -> AndroidColor.parseColor("#AEB6C2")
                else -> AndroidColor.WHITE
            }
            val labelTextColor = when {
                isSelected && isDarkTheme -> AndroidColor.parseColor("#34D399")
                isSelected -> AndroidColor.parseColor("#047857")
                isDarkTheme -> AndroidColor.parseColor("#AEB6C2")
                else -> AndroidColor.parseColor("#334155")
            }
            val bitmap = renderRouteLabelBitmap(
                rankNumber = rank,
                distanceText = distanceText,
                badgeDiameterDp = if (isSelected) 19.0f else 16.0f,
                badgeTextSizeDp = if (isSelected) 11.0f else 9.5f,
                badgeBorderDp = if (isSelected) 1.5f else 1.2f,
                gapDp = if (isSelected) 4.5f else 3.8f,
                labelTextSizeDp = if (isSelected) 12.0f else 10.2f,
                labelHaloStrokeDp = if (isSelected) 4.4f else 4.0f,
                badgeFillColor = badgeFill,
                badgeTextColor = badgeTextColor,
                badgeBorderColor = badgeBorderColor,
                labelTextColor = labelTextColor,
                labelHaloColor = labelHaloColor
            )
            val imageId = "lm-target-lbl-$rank-${if (isSelected) "sel" else "alt"}-${routeLabelBitmapSeq.incrementAndGet()}"
            style.addImage(imageId, bitmap)
            newImageIds.add(imageId)

            lineFeatures.put(
                buildPolylineFeature(
                    points = result.routePath,
                    props = JSONObject()
                        .put("rank", rank)
                        .put("isSelected", isSelected)
                )
            )
            val midpoint = computePolylineMidpoint(result.routePath)
            distLabelFeatures.put(
                buildPointFeature(
                    point = midpoint,
                    props = JSONObject()
                        .put("labelImageId", imageId)
                        .put("rank", rank)
                        .put("isSelected", isSelected)
                )
            )
        }
    }

    val linesJson = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", lineFeatures)
    }.toString()
    style.getSourceAs<GeoJsonSource>(SRC_TARGET_CONNECTORS)?.setGeoJson(linesJson)

    val distLabelsJson = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", distLabelFeatures)
    }.toString()
    style.getSourceAs<GeoJsonSource>(SRC_TARGET_DIST_LABELS)?.setGeoJson(distLabelsJson)

    val badgesJson = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", badgeFeatures)
    }.toString()
    style.getSourceAs<GeoJsonSource>(SRC_TARGET_STATION_BADGES)?.setGeoJson(badgesJson)

    // Remove superseded label bitmaps only AFTER the GeoJSON source points to the new IDs (zero flicker)
    previousImageIds.forEach { style.removeImage(it) }
    registeredImageIds.clear()
    registeredImageIds.addAll(newImageIds)
}

private fun updateUserLocationSource(style: Style, userLocation: GeoPoint?) {
    if (userLocation == null) {
        style.getSourceAs<GeoJsonSource>(SRC_USER_LOC)?.setGeoJson(emptyFeatureCollection())
        return
    }
    val json = JSONObject().apply {
        put("type", "FeatureCollection")
        put(
            "features",
            JSONArray().put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", JSONObject())
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put(
                            "coordinates",
                            JSONArray().put(userLocation.longitude).put(userLocation.latitude)
                        )
                    })
                }
            )
        )
    }.toString()
    style.getSourceAs<GeoJsonSource>(SRC_USER_LOC)?.setGeoJson(json)
}

private fun updatePlannedRouteSources(
    style: Style,
    isDarkTheme: Boolean,
    route: GeographicScooterRoute?,
    registeredImageIds: MutableSet<String>
) {
    val previousImageIds = registeredImageIds.toList()
    if (route == null) {
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE_TRANSIT_LEGS)?.setGeoJson(emptyFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE_SCOOTER_LEGS)?.setGeoJson(emptyFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE_SCOOTER_LABELS)?.setGeoJson(emptyFeatureCollection())
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE_PINS)?.setGeoJson(emptyFeatureCollection())
        previousImageIds.forEach { style.removeImage(it) }
        registeredImageIds.clear()
        return
    }

    // 0. Highlighted Public Transit Segments (directed fromStation -> toStation for travel arrows)
    val transitFeatures = JSONArray()
    route.transitSegments.forEach { seg ->
        if (seg.transitPath.size >= 2) {
            val arrowId = if (TransitRouteGeometryResolver.isLightLineColor(seg.lineColorHex)) {
                IMG_ROUTE_ARROW_DARK
            } else {
                IMG_ROUTE_ARROW_WHITE
            }
            transitFeatures.put(
                buildPolylineFeature(
                    points = seg.transitPath,
                    props = JSONObject()
                        .put("color", seg.lineColorHex)
                        .put("arrowImageId", arrowId)
                )
            )
        }
    }
    style.getSourceAs<GeoJsonSource>(SRC_ROUTE_TRANSIT_LEGS)?.setGeoJson(
        JSONObject().put("type", "FeatureCollection").put("features", transitFeatures).toString()
    )

    // 1. Real Street/Bike-Path Scooter First & Last Mile Polylines + Horizontal Midpoint Badge Labels
    val scooterFeatures = JSONArray()
    val scooterLabelFeatures = JSONArray()
    val newImageIds = mutableListOf<String>()

    val labelHaloColor = if (isDarkTheme) AndroidColor.parseColor("#0B0F17") else AndroidColor.parseColor("#FFFFFF")
    val routeBadgeFill = AndroidColor.parseColor("#0D9488")
    val routeTextColor = if (isDarkTheme) AndroidColor.parseColor("#5EEAD4") else AndroidColor.parseColor("#0F766E")

    if (route.originStation.routePath.size >= 2) {
        val originLabel = String.format(
            Locale.GERMAN,
            "%.1f km · %d min",
            route.originStation.estimatedScooterKm,
            route.originStation.estimatedScooterMinutes
        )
        val bitmap = renderRouteLabelBitmap(
            rankNumber = "1",
            distanceText = originLabel,
            badgeDiameterDp = 18.5f,
            badgeTextSizeDp = 10.8f,
            badgeBorderDp = 1.5f,
            gapDp = 4.5f,
            labelTextSizeDp = 11.5f,
            labelHaloStrokeDp = 4.4f,
            badgeFillColor = routeBadgeFill,
            badgeTextColor = AndroidColor.WHITE,
            badgeBorderColor = AndroidColor.WHITE,
            labelTextColor = routeTextColor,
            labelHaloColor = labelHaloColor
        )
        val imageId = "lm-route-lbl-1-${routeLabelBitmapSeq.incrementAndGet()}"
        style.addImage(imageId, bitmap)
        newImageIds.add(imageId)

        scooterFeatures.put(
            buildPolylineFeature(
                points = route.originStation.routePath,
                props = JSONObject()
            )
        )
        scooterLabelFeatures.put(
            buildPointFeature(
                point = computePolylineMidpoint(route.originStation.routePath),
                props = JSONObject().put("labelImageId", imageId)
            )
        )
    }

    if (route.destinationStation.routePath.size >= 2) {
        val destLabel = String.format(
            Locale.GERMAN,
            "%.1f km · %d min",
            route.destinationStation.estimatedScooterKm,
            route.destinationStation.estimatedScooterMinutes
        )
        val bitmap = renderRouteLabelBitmap(
            rankNumber = "2",
            distanceText = destLabel,
            badgeDiameterDp = 18.5f,
            badgeTextSizeDp = 10.8f,
            badgeBorderDp = 1.5f,
            gapDp = 4.5f,
            labelTextSizeDp = 11.5f,
            labelHaloStrokeDp = 4.4f,
            badgeFillColor = routeBadgeFill,
            badgeTextColor = AndroidColor.WHITE,
            badgeBorderColor = AndroidColor.WHITE,
            labelTextColor = routeTextColor,
            labelHaloColor = labelHaloColor
        )
        val imageId = "lm-route-lbl-2-${routeLabelBitmapSeq.incrementAndGet()}"
        style.addImage(imageId, bitmap)
        newImageIds.add(imageId)

        scooterFeatures.put(
            buildPolylineFeature(
                points = route.destinationStation.routePath,
                props = JSONObject()
            )
        )
        scooterLabelFeatures.put(
            buildPointFeature(
                point = computePolylineMidpoint(route.destinationStation.routePath),
                props = JSONObject().put("labelImageId", imageId)
            )
        )
    }
    style.getSourceAs<GeoJsonSource>(SRC_ROUTE_SCOOTER_LEGS)?.setGeoJson(
        JSONObject().put("type", "FeatureCollection").put("features", scooterFeatures).toString()
    )
    style.getSourceAs<GeoJsonSource>(SRC_ROUTE_SCOOTER_LABELS)?.setGeoJson(
        JSONObject().put("type", "FeatureCollection").put("features", scooterLabelFeatures).toString()
    )

    previousImageIds.forEach { style.removeImage(it) }
    registeredImageIds.clear()
    registeredImageIds.addAll(newImageIds)

    // 2. Key Route Pins (Start, Einstieg, optional Umstieg, Ausstieg, Ziel) — deduplicated if same station!
    val pinFeatures = JSONArray()
    pinFeatures.put(buildPinFeature(route.startLocation.location, "Start", "#10B981"))
    pinFeatures.put(buildPinFeature(route.originStation.station.location, route.originStation.station.name, "#0065BD"))
    route.transferStation?.let { hub ->
        val tooCloseToOrigin = GeoDistanceUtils.haversineKm(hub.location, route.originStation.station.location) < 0.08
        val tooCloseToDest = GeoDistanceUtils.haversineKm(hub.location, route.destinationStation.station.location) < 0.08
        if (!tooCloseToOrigin && !tooCloseToDest) {
            pinFeatures.put(buildPinFeature(hub.location, "Umstieg: ${hub.name}", "#F59E0B"))
        }
    }
    val sameStation = GeoDistanceUtils.haversineKm(
        route.originStation.station.location,
        route.destinationStation.station.location
    ) < 0.08
    if (!sameStation) {
        pinFeatures.put(buildPinFeature(route.destinationStation.station.location, route.destinationStation.station.name, "#0065BD"))
    }
    pinFeatures.put(buildPinFeature(route.destinationLocation.location, "Ziel", "#EF4444"))

    style.getSourceAs<GeoJsonSource>(SRC_ROUTE_PINS)?.setGeoJson(
        JSONObject().put("type", "FeatureCollection").put("features", pinFeatures).toString()
    )
}

/**
 * Draws a sleek, compact forward-pointing chevron (`›` pointing right +X) at 3x DPI
 * for MapLibre `SYMBOL_PLACEMENT_LINE` + `ICON_ROTATION_ALIGNMENT_MAP` travel direction arrows.
 */
private fun createDirectionArrowBitmap(
    arrowColor: Int,
    outlineColor: Int
): Bitmap {
    val scale = 3.0f
    val sizePx = ceil(13.0f * scale).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.density = DisplayMetrics.DENSITY_XXHIGH
    val canvas = Canvas(bitmap)

    val chevronPath = Path().apply {
        moveTo(4.0f * scale, 2.7f * scale)
        lineTo(9.4f * scale, 6.5f * scale)
        lineTo(4.0f * scale, 10.3f * scale)
    }

    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineColor
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = arrowColor
        style = Paint.Style.STROKE
        strokeWidth = 1.95f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    canvas.drawPath(chevronPath, outlinePaint)
    canvas.drawPath(chevronPath, fgPaint)
    return bitmap
}

/**
 * Renders a razor-sharp 3x-DPI Canvas bitmap combining a circular numbered badge (1, 2, 3)
 * with its km/min text horizontally aligned on a single optical centerline and framed by a crisp halo.
 */
private fun renderRouteLabelBitmap(
    rankNumber: String,
    distanceText: String,
    badgeDiameterDp: Float,
    badgeTextSizeDp: Float,
    badgeBorderDp: Float,
    gapDp: Float,
    labelTextSizeDp: Float,
    labelHaloStrokeDp: Float,
    badgeFillColor: Int,
    badgeTextColor: Int,
    badgeBorderColor: Int,
    labelTextColor: Int,
    labelHaloColor: Int
): Bitmap {
    val scale = 3.0f
    val padPx = 3.5f * scale
    val badgeDiameterPx = badgeDiameterDp * scale
    val badgeRadiusPx = badgeDiameterPx * 0.5f
    val badgeBorderPx = badgeBorderDp * scale
    val gapPx = gapDp * scale

    val boldTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    val badgeDigitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = badgeTextColor
        textSize = badgeTextSizeDp * scale
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
    }
    val labelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelTextColor
        textSize = labelTextSizeDp * scale
        typeface = boldTypeface
        textAlign = Paint.Align.LEFT
    }
    val labelHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelHaloColor
        textSize = labelTextSizeDp * scale
        typeface = boldTypeface
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        strokeWidth = labelHaloStrokeDp * scale
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val textWidthPx = labelFillPaint.measureText(distanceText)
    val fontMetrics = labelFillPaint.fontMetrics
    val textHeightPx = (fontMetrics.descent - fontMetrics.ascent) + labelHaloStrokeDp * scale
    val contentHeightPx = max(badgeDiameterPx + badgeBorderPx, textHeightPx)

    val totalWidthPx = ceil(padPx + badgeDiameterPx + gapPx + textWidthPx + labelHaloStrokeDp * scale * 0.5f + padPx).toInt().coerceAtLeast(1)
    val totalHeightPx = ceil(padPx + contentHeightPx + padPx).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(totalWidthPx, totalHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = DisplayMetrics.DENSITY_XXHIGH
    val canvas = Canvas(bitmap)

    val centerY = totalHeightPx * 0.5f
    val badgeCenterX = padPx + badgeRadiusPx

    // 1. Circular badge fill + crisp thin contrast border
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = badgeFillColor
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = badgeBorderColor
        style = Paint.Style.STROKE
        strokeWidth = badgeBorderPx
    }
    val circleDrawRadius = badgeRadiusPx - badgeBorderPx * 0.5f
    canvas.drawCircle(badgeCenterX, centerY, circleDrawRadius, fillPaint)
    canvas.drawCircle(badgeCenterX, centerY, circleDrawRadius, borderPaint)

    // 2. Centered bold digit inside the circle
    val digitBounds = Rect()
    badgeDigitPaint.getTextBounds(rankNumber, 0, rankNumber.length, digitBounds)
    val digitBaselineY = centerY - digitBounds.exactCenterY()
    canvas.drawText(rankNumber, badgeCenterX, digitBaselineY, badgeDigitPaint)

    // 3. Crisp haloed km/min text aligned optically to the circular badge centerline
    val capBounds = Rect()
    labelFillPaint.getTextBounds("0", 0, 1, capBounds)
    val textX = padPx + badgeDiameterPx + gapPx
    val textBaselineY = centerY - capBounds.exactCenterY()
    canvas.drawText(distanceText, textX, textBaselineY, labelHaloPaint)
    canvas.drawText(distanceText, textX, textBaselineY, labelFillPaint)

    return bitmap
}

/**
 * Computes the exact geographic midpoint at 50% cumulative distance along a route polyline,
 * so distance/duration labels render cleanly as horizontal point symbols without curve distortion.
 */
private fun computePolylineMidpoint(points: List<GeoPoint>): GeoPoint {
    if (points.size <= 1) return points.firstOrNull() ?: GeoDistanceUtils.MUNICH_CENTER
    if (points.size == 2) {
        return GeoPoint(
            latitude = (points[0].latitude + points[1].latitude) * 0.5,
            longitude = (points[0].longitude + points[1].longitude) * 0.5
        )
    }

    val segmentLengths = DoubleArray(points.size - 1)
    var totalDistance = 0.0
    for (i in 0 until points.lastIndex) {
        val dist = GeoDistanceUtils.haversineKm(points[i], points[i + 1])
        segmentLengths[i] = dist
        totalDistance += dist
    }
    if (totalDistance <= 1e-6) {
        return points[points.size / 2]
    }

    val halfTarget = totalDistance * 0.5
    var walked = 0.0
    for (i in segmentLengths.indices) {
        val segLen = segmentLengths[i]
        if (walked + segLen >= halfTarget && segLen > 1e-9) {
            val frac = ((halfTarget - walked) / segLen).coerceIn(0.0, 1.0)
            val a = points[i]
            val b = points[i + 1]
            return GeoPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * frac,
                longitude = a.longitude + (b.longitude - a.longitude) * frac
            )
        }
        walked += segLen
    }
    return points[points.size / 2]
}

private fun buildPointFeature(point: GeoPoint, props: JSONObject): JSONObject {
    return JSONObject().apply {
        put("type", "Feature")
        put("properties", props)
        put("geometry", JSONObject().apply {
            put("type", "Point")
            put("coordinates", JSONArray().put(point.longitude).put(point.latitude))
        })
    }
}

private fun buildPolylineFeature(points: List<GeoPoint>, props: JSONObject): JSONObject {
    val coords = JSONArray()
    points.forEach { pt ->
        coords.put(JSONArray().put(pt.longitude).put(pt.latitude))
    }
    return JSONObject().apply {
        put("type", "Feature")
        put("properties", props)
        put("geometry", JSONObject().apply {
            put("type", "LineString")
            put("coordinates", coords)
        })
    }
}

private fun buildPinFeature(point: GeoPoint, label: String, colorHex: String): JSONObject {
    return JSONObject().apply {
        put("type", "Feature")
        put("properties", JSONObject().put("label", label).put("color", colorHex))
        put("geometry", JSONObject().apply {
            put("type", "Point")
            put("coordinates", JSONArray().put(point.longitude).put(point.latitude))
        })
    }
}

private fun emptyFeatureCollection(): String = """{"type":"FeatureCollection","features":[]}"""
