package com.example.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.FavoritePlaceEntity
import com.example.domain.ExternalAppLauncher
import com.example.domain.GeoDistanceUtils
import com.example.model.BatteryRangeFallbackSuggestion
import com.example.model.BatteryRoundTripAssessment
import com.example.model.GeoPoint
import com.example.model.MapBounds
import com.example.model.MapTransitMode
import com.example.model.NearbyStationResult
import com.example.model.PreferredMapsApp
import com.example.model.ScooterProfile
import com.example.model.SearchLocation
import com.example.model.StationType
import com.example.ui.components.BatteryQuickChip
import com.example.ui.components.BatteryRangeFallbackBanner
import com.example.ui.components.BatteryTrafficLightBanner
import com.example.ui.components.BusCarryWarningPill
import com.example.ui.components.TransitLineBadge
import com.example.viewmodel.MapPointRole
import com.example.viewmodel.MapScreenUiState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

@Composable
fun MapScreen(
    uiState: MapScreenUiState,
    scooterProfile: ScooterProfile,
    preferredMapsApp: PreferredMapsApp,
    favorites: List<FavoritePlaceEntity>,
    onOpenDrawer: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectTarget: (SearchLocation) -> Unit,
    onClearTarget: () -> Unit,
    onMapPointPicked: (GeoPoint) -> Unit,
    onMapCameraIdle: (GeoPoint, Double) -> Unit,
    onMapBoundsChanged: (MapBounds) -> Unit,
    onCameraMoveHandled: () -> Unit,
    onToggleDayNightMap: (currentlyDark: Boolean) -> Unit,
    onCycleTransitMode: () -> Unit,
    onSelectTransitMode: (MapTransitMode) -> Unit,
    onUserLocationFound: (GeoPoint, Boolean) -> Unit,
    onSilentGpsLocationFound: (GeoPoint) -> Unit,
    onOpenBatterySheet: () -> Unit,
    onToggleFavorite: (SearchLocation) -> Unit,
    onSelectStationOption: (Int) -> Unit,
    onSetQuickRouteStart: (SearchLocation) -> Unit,
    onSetQuickRouteDestination: (SearchLocation) -> Unit,
    onClearQuickRouteStart: () -> Unit,
    onClearQuickRouteDestination: () -> Unit,
    onShowFeedback: (String) -> Unit,
    onDismissTransitWarning: () -> Unit,
    onOpenLegalInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isDarkMap = uiState.displayPrefs.isNightMapOverride ?: isSystemInDarkTheme()
    var locationErrorHint by remember { mutableStateOf<String?>(null) }
    var hideMapControls by rememberSaveable { mutableStateOf(false) }
    var isPointCardMinimized by rememberSaveable { mutableStateOf(true) }

    // Start minimized whenever a new point is selected (map tap, GPS, or search) so map & 3 routes stay unobstructed
    LaunchedEffect(uiState.selectedTarget?.id) {
        isPointCardMinimized = true
    }

    // Silent one-time GPS region check on app start if location permission is already granted
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchLastKnownLocation(context) { realPoint ->
                if (realPoint != null) {
                    onSilentGpsLocationFound(realPoint)
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationErrorHint = null
            fetchLastKnownLocation(context) { point ->
                onUserLocationFound(point ?: GeoDistanceUtils.MUNICH_CENTER, point != null)
            }
        } else {
            locationErrorHint = "Standort abgelehnt – tippe auf die Karte oder nutze die Suche."
        }
    }

    fun requestLocationCenter() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            locationErrorHint = null
            fetchLastKnownLocation(context) { point ->
                onUserLocationFound(point ?: GeoDistanceUtils.MUNICH_CENTER, point != null)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val transitMode = uiState.displayPrefs.transitMode
    val transitModeIcon = when (transitMode) {
        MapTransitMode.SBAHN_ONLY -> Icons.Filled.Train
        MapTransitMode.BUS_ONLY -> Icons.Filled.DirectionsBus
        MapTransitMode.REGIO_LONG_ONLY -> Icons.Filled.DirectionsRailway
        MapTransitMode.ALL -> Icons.Filled.Commute
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Full-bleed MapLibre GL OpenGL Vector Map (thin lines permanently locked + Day/Night minimalist layout)
        MunichScooterMapView(
            isMinimalScooterMode = true,
            isProminentLines = false,
            transitMode = transitMode,
            userLocation = uiState.userLocation,
            selectedTarget = uiState.selectedTarget,
            nearestStations = uiState.nearestStationsToTarget,
            selectedStationIndex = uiState.selectedStationIndex,
            onStationBadgeTap = onSelectStationOption,
            dynamicOverlay = uiState.dynamicOverlay,
            cameraMoveRequest = uiState.cameraMoveRequest,
            onCameraMoveHandled = onCameraMoveHandled,
            onMapTap = { point ->
                focusManager.clearFocus()
                onMapPointPicked(point)
            },
            onMapLongPress = { point ->
                focusManager.clearFocus()
                onMapPointPicked(point)
            },
            onMapCameraIdle = onMapCameraIdle,
            onMapBoundsChanged = onMapBoundsChanged,
            darkMapStyleOverride = uiState.displayPrefs.isNightMapOverride,
            tooltipTopPadding = when {
                hideMapControls -> 16.dp
                uiState.quickRouteStart != null || uiState.quickRouteDestination != null -> 164.dp
                else -> 120.dp
            },
            tooltipUseStatusBarsPadding = true,
            attributionAlignment = if (!hideMapControls && uiState.selectedTarget != null && !isPointCardMinimized) {
                Alignment.TopStart
            } else {
                Alignment.BottomEnd
            },
            attributionPadding = when {
                hideMapControls -> PaddingValues(end = 10.dp, bottom = 8.dp)
                uiState.selectedTarget != null && !isPointCardMinimized -> PaddingValues(
                    start = 12.dp,
                    top = if (uiState.quickRouteStart != null || uiState.quickRouteDestination != null) 152.dp else 108.dp
                )
                uiState.selectedTarget != null && isPointCardMinimized -> PaddingValues(end = 10.dp, bottom = 86.dp)
                else -> PaddingValues(end = 10.dp, bottom = 74.dp)
            },
            attributionUseStatusBarsPadding = !hideMapControls && uiState.selectedTarget != null && !isPointCardMinimized,
            onOpenLegalInfo = onOpenLegalInfo,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Search Bar (Burger Menu left) + Quick Battery Chip + Active Network Mode Pill
        AnimatedVisibility(
            visible = !hideMapControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onOpenDrawer()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("map_burger_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.map_menu_open_cd),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.map_search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_search_input")
                        )
                    }

                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(18.dp)
                        )
                    } else if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onClearSearch()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("map_clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.map_clear_search_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Autocomplete Suggestions Dropdown
            AnimatedVisibility(visible = uiState.searchSuggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(uiState.searchSuggestions, key = { it.id }) { suggestion ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        onSelectTarget(suggestion)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = if (suggestion.stationId != null) {
                                        Icons.Filled.Train
                                    } else {
                                        Icons.Filled.Place
                                    },
                                    contentDescription = null,
                                    tint = if (suggestion.stationId != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = suggestion.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Quick Status Strip (Battery Quick-Chip + Active Network & Style Badge)
            if (uiState.searchSuggestions.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatteryQuickChip(
                        profile = scooterProfile,
                        onClick = onOpenBatterySheet
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "${transitMode.shortLabel} · ${if (isDarkMap) "Nacht" else "Tag"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                // Compact Quick-Route Start or Destination indicator while panning the map freely
                val pendingStart = uiState.quickRouteStart
                val pendingDest = uiState.quickRouteDestination
                if (pendingStart != null || pendingDest != null) {
                    val isStartPending = pendingStart != null
                    val anchorLoc = pendingStart ?: pendingDest!!
                    val anchorStationName = if (isStartPending) {
                        uiState.quickRouteStartStation?.station?.name
                    } else {
                        uiState.quickRouteDestinationStation?.station?.name
                    }
                    val stationHint = anchorStationName?.let { " ($it)" } ?: ""
                    val bannerText = if (isStartPending) {
                        "Start: ${anchorLoc.name}$stationHint · Ziel wählen"
                    } else {
                        "Ziel: ${anchorLoc.name}$stationHint · Start wählen"
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        shadowElevation = 3.dp,
                        modifier = Modifier.testTag("map_quick_route_status_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isStartPending) Icons.Filled.TripOrigin else Icons.Filled.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = bannerText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = if (isStartPending) onClearQuickRouteStart else onClearQuickRouteDestination,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("map_quick_route_status_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(
                                        if (isStartPending) {
                                            R.string.map_quick_route_clear_start_cd
                                        } else {
                                            R.string.map_quick_route_clear_dest_cd
                                        }
                                    ),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (locationErrorHint != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = locationErrorHint ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (uiState.transitWarningHint != null && uiState.searchSuggestions.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = onDismissTransitWarning,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.testTag("map_transit_warning_hint")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = uiState.transitWarningHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hinweis schließen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
        }

        // 3. Right-Edge Floating Controls in exact top-to-bottom order:
        //    1. ÖPNV-Auswahl  2. Tag/Nacht-Layout  3. Bedienelemente Aus/An  4. GPS
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            // 1. ÖPNV-Auswahl (Icon always visible + subtle thin progress bar under text while loading)
            AnimatedVisibility(
                visible = !hideMapControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MapToggleControlButton(
                    icon = transitModeIcon,
                    label = transitMode.shortLabel,
                    contentDescription = stringResource(R.string.map_transit_mode_toggle_cd),
                    isActive = true,
                    isLoading = uiState.isLoadingViewportTransit,
                    onClick = onCycleTransitMode,
                    testTag = "map_toggle_transit_mode"
                )
            }

            // 2. Tag/Nacht-Layout
            AnimatedVisibility(
                visible = !hideMapControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MapToggleControlButton(
                    icon = if (isDarkMap) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                    label = if (isDarkMap) "Nacht" else "Tag",
                    contentDescription = stringResource(R.string.map_day_night_toggle_cd),
                    isActive = isDarkMap,
                    onClick = { onToggleDayNightMap(isDarkMap) },
                    testTag = "map_toggle_day_night"
                )
            }

            // 3. Bedienelemente Aus/An (remains visible semi-transparent when UI is hidden)
            MapToggleControlButton(
                icon = if (hideMapControls) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                label = if (hideMapControls) "UI an" else "UI aus",
                contentDescription = stringResource(R.string.map_toggle_ui_visibility_cd),
                isActive = false,
                buttonAlpha = if (hideMapControls) 0.72f else 1f,
                onClick = {
                    focusManager.clearFocus()
                    hideMapControls = !hideMapControls
                },
                testTag = "map_toggle_ui_visibility"
            )

            // 4. GPS (Standort zentrieren)
            AnimatedVisibility(
                visible = !hideMapControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MapToggleControlButton(
                    icon = Icons.Filled.MyLocation,
                    label = "GPS",
                    contentDescription = stringResource(R.string.map_my_location_cd),
                    isActive = uiState.userLocation != null,
                    onClick = { requestLocationCenter() },
                    testTag = "map_my_location_button"
                )
            }
        }

        // 4. Bottom Contextual Card: Pure Single-Point Nearest Stations Radar (NO Start-Ziel Routing!)
        AnimatedVisibility(
            visible = !hideMapControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = uiState.selectedTarget == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 3.dp,
                    modifier = Modifier.widthIn(max = 540.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ElectricScooter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.map_long_press_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.selectedTarget != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                uiState.selectedTarget?.let { target ->
                    val isFav = favorites.any { fav ->
                        fav.id == target.id ||
                            GeoDistanceUtils.haversineKm(
                                GeoPoint(fav.latitude, fav.longitude),
                                target.location
                            ) < 0.025
                    }
                    NearestStationsTargetSheet(
                        target = target,
                        selectedTargetRole = uiState.selectedTargetRole,
                        quickRouteStart = uiState.quickRouteStart,
                        quickRouteDestination = uiState.quickRouteDestination,
                        preferredMapsApp = preferredMapsApp,
                        activeTransitMode = transitMode,
                        nearestStations = uiState.nearestStationsToTarget,
                        selectedStationIndex = uiState.selectedStationIndex,
                        batteryQuickCheck = uiState.targetBatteryQuickCheck,
                        batteryFallbackSuggestion = uiState.batteryFallbackSuggestion,
                        isFavorite = isFav,
                        isMinimized = isPointCardMinimized,
                        onToggleMinimized = { isPointCardMinimized = !isPointCardMinimized },
                        onSwitchTransitMode = onSelectTransitMode,
                        onToggleFavorite = { onToggleFavorite(target) },
                        onSelectStationOption = onSelectStationOption,
                        onSetQuickRouteStart = { onSetQuickRouteStart(target) },
                        onSetQuickRouteDestination = { onSetQuickRouteDestination(target) },
                        onClearQuickRouteStart = onClearQuickRouteStart,
                        onClearQuickRouteDestination = onClearQuickRouteDestination,
                        onLaunchQuickMaps = { station, role ->
                            val res = if (role == MapPointRole.START) {
                                ExternalAppLauncher.openMapsApp(
                                    context = context,
                                    preferredMapsApp = preferredMapsApp,
                                    origin = target.location,
                                    destination = station.station.location,
                                    destinationLabel = station.station.name,
                                    originLabel = target.name
                                )
                            } else {
                                ExternalAppLauncher.openMapsApp(
                                    context = context,
                                    preferredMapsApp = preferredMapsApp,
                                    origin = station.station.location,
                                    destination = target.location,
                                    destinationLabel = target.name,
                                    originLabel = station.station.name
                                )
                            }
                            onShowFeedback(res.message)
                        },
                        onClose = onClearTarget
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun MapToggleControlButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isActive: Boolean,
    isLoading: Boolean = false,
    buttonAlpha: Float = 1f,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        },
        border = BorderStroke(
            0.75.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (buttonAlpha < 0.9f) 2.dp else 4.dp,
        modifier = Modifier
            .alpha(buttonAlpha)
            .size(width = 66.dp, height = 58.dp)
            .testTag(testTag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // Keep transit icon permanently visible — never replace it with a spinning circle!
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLoading) {
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = if (isActive) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(50))
                        .testTag("${testTag}_loading_bar")
                )
            }
        }
    }
}

@Composable
private fun NearestStationsTargetSheet(
    target: SearchLocation,
    selectedTargetRole: MapPointRole,
    quickRouteStart: SearchLocation?,
    quickRouteDestination: SearchLocation?,
    preferredMapsApp: PreferredMapsApp,
    activeTransitMode: MapTransitMode,
    nearestStations: List<NearbyStationResult>,
    selectedStationIndex: Int,
    batteryQuickCheck: BatteryRoundTripAssessment?,
    batteryFallbackSuggestion: BatteryRangeFallbackSuggestion?,
    isFavorite: Boolean,
    isMinimized: Boolean,
    onToggleMinimized: () -> Unit,
    onSwitchTransitMode: (MapTransitMode) -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectStationOption: (Int) -> Unit,
    onSetQuickRouteStart: () -> Unit,
    onSetQuickRouteDestination: () -> Unit,
    onClearQuickRouteStart: () -> Unit,
    onClearQuickRouteDestination: () -> Unit,
    onLaunchQuickMaps: (NearbyStationResult, MapPointRole) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(if (isMinimized) 18.dp else 20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 580.dp)
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
            .testTag("map_target_bottom_sheet")
    ) {
        if (isMinimized) {
            val coordsText = String.format(
                Locale.US,
                "%.4f, %.4f",
                target.location.latitude,
                target.location.longitude
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleMinimized)
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    .testTag("map_target_minimized_bar")
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = coordsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val pulseTransition = rememberInfiniteTransition(label = "expand_arrow_pulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.22f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "expand_arrow_scale"
                )
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.62f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "expand_arrow_alpha"
                )
                IconButton(
                    onClick = onToggleMinimized,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_maximize_target_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha * 0.72f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.map_maximize_card_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_close_target_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_close_target_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 14.dp)
            ) {
                // Clickable top drag-handle + header row to minimize smoothly
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleMinimized)
                        .testTag("map_target_header_toggle")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = target.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("map_favorite_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(
                                    if (isFavorite) R.string.map_remove_favorite_cd else R.string.map_save_favorite_cd
                                ),
                                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onToggleMinimized,
                            modifier = Modifier.testTag("map_minimize_target_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.map_minimize_card_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("map_close_target_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.map_close_target_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

            val isCurrentTargetQuickStart = quickRouteStart != null && (
                quickRouteStart.id == target.id ||
                    GeoDistanceUtils.haversineKm(quickRouteStart.location, target.location) < 0.025
                )
            val isCurrentTargetQuickDest = quickRouteDestination != null && (
                quickRouteDestination.id == target.id ||
                    GeoDistanceUtils.haversineKm(quickRouteDestination.location, target.location) < 0.025
                )
            val effectiveRole = when {
                isCurrentTargetQuickStart -> MapPointRole.START
                isCurrentTargetQuickDest -> MapPointRole.DESTINATION
                else -> selectedTargetRole
            }

            // Section header
            Text(
                text = "Nächste Haltestellen · ${activeTransitMode.shortLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (nearestStations.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Für „${activeTransitMode.fullLabel}“ wurden im aktuellen Ausschnitt noch keine Haltestellen geladen. Zoome näher heran oder wechsle rechts das Netz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                val topThreeStations = nearestStations.take(3)
                val activeIndex = selectedStationIndex.coerceIn(0, (topThreeStations.lastIndex).coerceAtLeast(0))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(topThreeStations, key = { _, item -> item.station.id }) { index, item ->
                        val isSelected = index == activeIndex
                        val rankNumber = index + 1
                        Surface(
                            onClick = { onSelectStationOption(index) },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.75.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("map_nearest_station_card_$rankNumber")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .widthIn(min = 172.dp, max = 232.dp)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$rankNumber",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.station.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.testTag("map_nearest_station_selected_badge_$rankNumber")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = stringResource(R.string.map_station_selected_badge),
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.map_station_selected_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item.station.lines.take(3).forEach { line ->
                                        TransitLineBadge(lineName = line)
                                    }
                                    if (item.station.lines.isEmpty()) {
                                        val fallback = when (item.station.type) {
                                            StationType.BUS -> "Bus"
                                            StationType.REGIO -> "RE"
                                            StationType.LONG_DISTANCE -> "ICE"
                                            else -> "S"
                                        }
                                        TransitLineBadge(lineName = fallback)
                                    }
                                }
                                Text(
                                    text = String.format(
                                        Locale.GERMAN,
                                        "%.1f km · %d min (Scooter)",
                                        item.estimatedScooterKm,
                                        item.estimatedScooterMinutes
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (item.station.type == StationType.BUS) {
                                    BusCarryWarningPill(expanded = false)
                                }
                            }
                        }
                    }
                }
            }

            // Action A: "Quick & Dirty" direct Maps navigation to/from selected station (1, 2, or 3)
            val topThreeForActions = nearestStations.take(3)
            val activeActionIdx = selectedStationIndex.coerceIn(0, (topThreeForActions.lastIndex).coerceAtLeast(0))
            val activeStationForMaps = topThreeForActions.getOrNull(activeActionIdx)
            if (activeStationForMaps != null) {
                val rankNum = activeActionIdx + 1
                val quickMapsLabel = if (effectiveRole == MapPointRole.START) {
                    stringResource(
                        R.string.map_quick_maps_to_station,
                        rankNum,
                        preferredMapsApp.displayName
                    )
                } else {
                    stringResource(
                        R.string.map_quick_maps_from_station,
                        rankNum,
                        preferredMapsApp.displayName
                    )
                }
                Button(
                    onClick = { onLaunchQuickMaps(activeStationForMaps, effectiveRole) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("map_quick_maps_direct_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = quickMapsLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action B: Kombi-Route planen — Start ODER Ziel schon beim ersten Punkt frei wählbar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Start chip
                if (isCurrentTargetQuickStart) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .testTag("map_quick_route_start_active_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.TripOrigin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${stringResource(R.string.map_quick_route_start_active)} ✓",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onClearQuickRouteStart)
                                    .testTag("map_quick_route_clear_start_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.map_quick_route_clear_start_cd),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    val willCompleteRouteAsStart = quickRouteDestination != null
                    Surface(
                        onClick = onSetQuickRouteStart,
                        color = if (willCompleteRouteAsStart) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (willCompleteRouteAsStart) 1.5.dp else 0.75.dp,
                            color = if (willCompleteRouteAsStart) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .testTag("map_quick_route_start_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TripOrigin,
                                contentDescription = null,
                                tint = if (willCompleteRouteAsStart) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (willCompleteRouteAsStart) {
                                    "${stringResource(R.string.map_quick_route_set_start)} → Route"
                                } else {
                                    stringResource(R.string.map_quick_route_set_start)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (willCompleteRouteAsStart) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Destination chip
                if (isCurrentTargetQuickDest) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .testTag("map_quick_route_dest_active_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${stringResource(R.string.map_quick_route_dest_active)} ✓",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onClearQuickRouteDestination)
                                    .testTag("map_quick_route_clear_dest_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.map_quick_route_clear_dest_cd),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    val willCompleteRouteAsDest = quickRouteStart != null
                    Surface(
                        onClick = onSetQuickRouteDestination,
                        color = if (willCompleteRouteAsDest) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (willCompleteRouteAsDest) 1.5.dp else 0.75.dp,
                            color = if (willCompleteRouteAsDest) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .testTag("map_quick_route_dest_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = null,
                                tint = if (willCompleteRouteAsDest) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (willCompleteRouteAsDest) {
                                    "${stringResource(R.string.map_quick_route_set_dest)} → Route"
                                } else {
                                    stringResource(R.string.map_quick_route_set_dest)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (willCompleteRouteAsDest) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            batteryQuickCheck?.let { check ->
                BatteryTrafficLightBanner(assessment = check)
            }

            batteryFallbackSuggestion?.let { suggestion ->
                val targetMode = suggestion.suggestedMapMode
                if (targetMode != null) {
                    BatteryRangeFallbackBanner(
                        suggestion = suggestion,
                        onSwitchClick = { onSwitchTransitMode(targetMode) },
                        testTag = "map_battery_fallback_banner"
                    )
                }
            }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchLastKnownLocation(context: Context, onResult: (GeoPoint?) -> Unit) {
    try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onResult(GeoPoint(loc.latitude, loc.longitude))
                } else {
                    val cts = CancellationTokenSource()
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { freshLoc ->
                            if (freshLoc != null) {
                                onResult(GeoPoint(freshLoc.latitude, freshLoc.longitude))
                            } else {
                                onResult(null)
                            }
                        }
                        .addOnFailureListener { onResult(null) }
                }
            }
            .addOnFailureListener { onResult(null) }
    } catch (_: Exception) {
        onResult(null)
    }
}
