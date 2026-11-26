package com.example.ui.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.MapDisplayPreferences
import com.example.data.remote.DynamicTransitOverlay
import com.example.model.GeoPoint
import com.example.model.GeographicScooterRoute
import com.example.model.MapTransitMode
import com.example.model.TransitBucketVisibility
import com.example.ui.map.MunichScooterMapView
import com.example.viewmodel.CameraMoveRequest
import java.util.Locale

private val PinStartGreen = Color(0xFF10B981)
private val PinDestRed = Color(0xFFEF4444)
private val TouchPulseTeal = Color(0xFF2DD4BF)

/**
 * Picks an ideal zoom level for inspecting the first or last scooter mile around Start/Ziel.
 */
fun smartMileZoomForDistance(scooterKm: Double): Double = when {
    scooterKm <= 1.2 -> 15.0
    scooterKm <= 2.8 -> 14.2
    scooterKm <= 5.5 -> 13.4
    else -> 12.6
}

/**
 * Animated dark scrim overlay for the embedded Route preview map explaining 2-finger panning:
 * - Subtle moving map grid in the background
 * - Two minimalist vector fingers gliding smoothly in a pan motion
 * - Two pulsing touch-ripple rings directly beneath both fingertips
 * - Clear white label: "Karte mit 2 Fingern verschieben"
 *
 * Non-blocking so users can immediately place 2 fingers on the map even while visible.
 */
@Composable
fun TwoFingerPanHintOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 420)),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "two_finger_pan_hint")
        val cycle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1650, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gesture_cycle"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .testTag("route_map_two_finger_hint_overlay")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Canvas(modifier = Modifier.size(width = 140.dp, height = 92.dp)) {
                    // Smooth swipe phase: 0.00..0.16 touch down -> 0.16..0.76 glide -> 0.76..1.00 release
                    val rawSwipe = ((cycle - 0.16f) / 0.60f).coerceIn(0f, 1f)
                    // Smoothstep easing for natural finger motion
                    val easedSwipe = rawSwipe * rawSwipe * (3f - 2f * rawSwipe)

                    // Touch contact intensity (peaks during touch & drag, fades out on release)
                    val touchAlpha = when {
                        cycle < 0.14f -> (cycle / 0.14f).coerceIn(0f, 1f)
                        cycle < 0.78f -> 1f
                        else -> (1f - (cycle - 0.78f) / 0.22f).coerceIn(0f, 1f)
                    }

                    // Expanding ripple pulse (runs twice or continuously across active contact)
                    val pulsePhase = ((cycle - 0.08f) / 0.68f).coerceIn(0f, 1f)
                    val pulseAlpha = if (cycle in 0.08f..0.76f) (1f - pulsePhase) * 0.9f else 0f

                    val swipeDx = (easedSwipe - 0.5f) * size.width * 0.30f
                    val swipeDy = (easedSwipe - 0.5f) * size.height * -0.14f

                    // 1. Subtle rounded mini-map backdrop with drifting grid lines
                    val cardMargin = 4.dp.toPx()
                    val cardRect = Rect(
                        left = cardMargin,
                        top = cardMargin,
                        right = size.width - cardMargin,
                        bottom = size.height - cardMargin
                    )
                    val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    val clip = Path().apply { addRoundRect(RoundRect(cardRect, corner)) }

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = cardRect.topLeft,
                        size = cardRect.size,
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = cardRect.topLeft,
                        size = cardRect.size,
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    clipPath(clip) {
                        val gridStep = 18.dp.toPx()
                        val gridShiftX = swipeDx * 0.65f
                        val gridShiftY = swipeDy * 0.65f

                        var gx = -gridStep
                        while (gx < size.width + gridStep * 2) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(gx + gridShiftX, 0f),
                                end = Offset(gx + gridShiftX, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            gx += gridStep
                        }
                        var gy = -gridStep
                        while (gy < size.height + gridStep * 2) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(0f, gy + gridShiftY),
                                end = Offset(size.width, gy + gridShiftY),
                                strokeWidth = 1.dp.toPx()
                            )
                            gy += gridStep
                        }

                        // Subtle diagonal transit line moving with the map grid
                        drawLine(
                            color = TouchPulseTeal.copy(alpha = 0.32f),
                            start = Offset(size.width * 0.12f + gridShiftX, size.height * 0.72f + gridShiftY),
                            end = Offset(size.width * 0.88f + gridShiftX, size.height * 0.28f + gridShiftY),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // 2. Fingertip anchor positions (two side-by-side fingers gliding together)
                    val baseCenterX = size.width * 0.50f + swipeDx
                    val baseCenterY = size.height * 0.44f + swipeDy
                    val fingerGap = 17.dp.toPx()
                    val leftTip = Offset(baseCenterX - fingerGap * 0.52f, baseCenterY - 2.dp.toPx())
                    val rightTip = Offset(baseCenterX + fingerGap * 0.52f, baseCenterY + 3.dp.toPx())

                    // 3. Pulsing touch circles directly under BOTH fingertips
                    listOf(leftTip, rightTip).forEach { tip ->
                        // Expanding outer ripple ring
                        if (pulseAlpha > 0.02f) {
                            drawCircle(
                                color = TouchPulseTeal.copy(alpha = pulseAlpha),
                                radius = (7.dp.toPx() + pulsePhase * 11.dp.toPx()),
                                center = tip,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        // Soft inner touch halo
                        drawCircle(
                            color = TouchPulseTeal.copy(alpha = 0.36f * touchAlpha),
                            radius = (7.5.dp.toPx() - pulsePhase * 1.2.dp.toPx()),
                            center = tip
                        )
                        // Crisp contact center dot
                        drawCircle(
                            color = Color.White.copy(alpha = 0.92f * touchAlpha),
                            radius = 3.2.dp.toPx(),
                            center = tip
                        )
                    }

                    // 4. Minimalist vector 2-finger graphic (index & middle finger + soft palm arc)
                    val fingerWidth = 11.5.dp.toPx()
                    val fingerLength = 31.dp.toPx()
                    val fingerCorner = CornerRadius(fingerWidth * 0.5f, fingerWidth * 0.5f)
                    val fingerAlpha = 0.45f + 0.55f * touchAlpha

                    rotate(degrees = -12f, pivot = Offset(baseCenterX, baseCenterY + 12.dp.toPx())) {
                        // Subtle palm bridge connecting the two fingers at the bottom
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.88f * fingerAlpha),
                            topLeft = Offset(
                                baseCenterX - fingerGap * 0.52f - fingerWidth * 0.46f,
                                baseCenterY + 18.dp.toPx()
                            ),
                            size = Size(fingerGap + fingerWidth * 0.92f, 15.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )

                        // Left finger (index)
                        val leftRectTopLeft = Offset(leftTip.x - fingerWidth / 2f, leftTip.y - 2.dp.toPx())
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.96f * fingerAlpha),
                            topLeft = leftRectTopLeft,
                            size = Size(fingerWidth, fingerLength),
                            cornerRadius = fingerCorner
                        )
                        drawRoundRect(
                            color = Color(0xFF0F172A).copy(alpha = 0.45f),
                            topLeft = leftRectTopLeft,
                            size = Size(fingerWidth, fingerLength),
                            cornerRadius = fingerCorner,
                            style = Stroke(width = 1.2.dp.toPx())
                        )

                        // Right finger (middle)
                        val rightRectTopLeft = Offset(rightTip.x - fingerWidth / 2f, rightTip.y - 2.dp.toPx())
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.96f * fingerAlpha),
                            topLeft = rightRectTopLeft,
                            size = Size(fingerWidth, fingerLength - 2.dp.toPx()),
                            cornerRadius = fingerCorner
                        )
                        drawRoundRect(
                            color = Color(0xFF0F172A).copy(alpha = 0.45f),
                            topLeft = rightRectTopLeft,
                            size = Size(fingerWidth, fingerLength - 2.dp.toPx()),
                            cornerRadius = fingerCorner,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.route_map_two_finger_hint),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Compact, subtle quick-jump pills for "Start" and "Ziel", displayed on BOTH the embedded
 * Route map preview and the Fullscreen Route map.
 */
@Composable
fun RouteMapQuickJumpRow(
    onJumpStart: () -> Unit,
    onJumpDestination: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        QuickJumpPill(
            label = stringResource(R.string.route_map_jump_start),
            dotColor = PinStartGreen,
            onClick = onJumpStart,
            testTag = "route_map_jump_start"
        )
        QuickJumpPill(
            label = stringResource(R.string.route_map_jump_destination),
            dotColor = PinDestRed,
            onClick = onJumpDestination,
            testTag = "route_map_jump_destination"
        )
    }
}

@Composable
private fun QuickJumpPill(
    label: String,
    dotColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Compact 48dp touch-target Fullscreen expand button shown on top-right of the embedded Route map.
 */
@Composable
fun RouteMapFullscreenExpandButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
        modifier = modifier
            .size(48.dp)
            .testTag("route_map_fullscreen_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = stringResource(R.string.route_map_fullscreen_cd),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Fullscreen Route map view with normal 1-finger pan/zoom gestures, quick-jump buttons
 * ("Start" / "Ziel"), and a clear "Zurück zur Route" button that preserves all route state.
 */
@Composable
fun RouteFullscreenMapOverlay(
    route: GeographicScooterRoute,
    mapDisplayPrefs: MapDisplayPreferences,
    dynamicOverlay: DynamicTransitOverlay,
    userLocation: GeoPoint?,
    cameraMoveRequest: CameraMoveRequest?,
    onCameraMoveHandled: () -> Unit,
    onJumpStart: () -> Unit,
    onJumpDestination: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        val routeBucketVis = TransitBucketVisibility.fromRouteModes(
            startMode = route.startMode,
            destinationMode = route.destinationMode
        )
        val routeTransitMode = if (route.startMode == route.destinationMode) {
            route.startMode.toMapTransitMode()
        } else {
            MapTransitMode.ALL
        }
        MunichScooterMapView(
            isMinimalScooterMode = mapDisplayPrefs.isMinimalScooterMode,
            isProminentLines = false,
            transitMode = routeTransitMode,
            bucketVisibilityOverride = routeBucketVis,
            userLocation = userLocation,
            selectedTarget = null,
            nearestStations = emptyList(),
            dynamicOverlay = dynamicOverlay,
            cameraMoveRequest = cameraMoveRequest,
            onCameraMoveHandled = onCameraMoveHandled,
            onMapLongPress = {},
            onMapCameraIdle = { _, _ -> },
            plannedRoute = route,
            initialCenter = route.startLocation.location,
            initialZoom = 12.0,
            requireTwoFingerPan = false,
            darkMapStyleOverride = mapDisplayPrefs.isNightMapOverride,
            tooltipTopPadding = 76.dp,
            attributionAlignment = Alignment.BottomEnd,
            attributionPadding = PaddingValues(end = 12.dp, bottom = 12.dp),
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar: Compact Route Summary + Prominent "Zurück zur Route" Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 4.dp,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = route.compactHeadline(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            Locale.GERMAN,
                            "Scooter %.1f km · ca. %d min",
                            route.totalScooterOneWayKm,
                            route.totalScooterOneWayMinutes
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                onClick = onExitFullscreen,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("route_map_exit_fullscreen_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloseFullscreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.route_map_back_to_route),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Bottom-Start Quick-Jump Buttons ("Start" / "Ziel")
        RouteMapQuickJumpRow(
            onJumpStart = onJumpStart,
            onJumpDestination = onJumpDestination,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 16.dp)
        )
    }
}
