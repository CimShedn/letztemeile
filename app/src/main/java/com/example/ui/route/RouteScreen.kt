package com.example.ui.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.FavoritePlaceEntity
import com.example.data.local.MapDisplayPreferences
import com.example.data.remote.DynamicTransitOverlay
import com.example.domain.ExternalAppLauncher
import com.example.domain.GeoDistanceUtils
import com.example.model.AppSettings
import com.example.model.BatteryRangeFallbackSuggestion
import com.example.model.GeoPoint
import com.example.model.GeographicScooterRoute
import com.example.model.GeographicTransitSegment
import com.example.model.MapTransitMode
import com.example.model.RouteTransitChoice
import com.example.model.ScooterProfile
import com.example.model.SearchLocation
import com.example.model.TransitBucketVisibility
import com.example.ui.components.BatteryQuickChip
import com.example.ui.components.BatteryRangeFallbackBanner
import com.example.ui.components.BatteryTrafficLightBanner
import com.example.ui.components.BusCarryWarningPill
import com.example.ui.components.TransitLineBadge
import androidx.compose.ui.graphics.Color
import com.example.ui.map.MunichScooterMapView
import com.example.ui.theme.lineColorForName
import com.example.viewmodel.CameraMoveRequest
import com.example.viewmodel.RouteScreenUiState
import com.example.viewmodel.RouteSearchField
import com.example.viewmodel.RoutingStatus
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RouteScreen(
    uiState: RouteScreenUiState,
    scooterProfile: ScooterProfile,
    appSettings: AppSettings,
    mapDisplayPrefs: MapDisplayPreferences,
    dynamicOverlay: DynamicTransitOverlay,
    userLocation: GeoPoint?,
    lastMapCameraCenter: GeoPoint,
    favorites: List<FavoritePlaceEntity>,
    onFieldQueryChanged: (RouteSearchField, String) -> Unit,
    onSuggestionSelected: (SearchLocation) -> Unit,
    onDismissSuggestions: () -> Unit,
    onClearField: (RouteSearchField) -> Unit,
    onBuildQuickPointLocation: (GeoPoint) -> SearchLocation,
    onResolvePreviewLocation: suspend (GeoPoint) -> SearchLocation,
    onSetLocationFromMap: (RouteSearchField, SearchLocation) -> Unit,
    onSetStartMode: (RouteTransitChoice) -> Unit,
    onSetDestinationMode: (RouteTransitChoice) -> Unit,
    onClearPreferredOriginStation: () -> Unit,
    onClearPreferredDestinationStation: () -> Unit,
    onSwapLocations: () -> Unit,
    onGpsStartFound: (GeoPoint) -> Unit,
    onUpdateUserLocation: (GeoPoint) -> Unit,
    onExecuteSearch: () -> Unit,
    onOpenBatterySheet: () -> Unit,
    onReorderFavorites: (List<String>) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onShowFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var mapPickerField by remember { mutableStateOf<RouteSearchField?>(null) }
    var isRouteMapFullscreen by remember { mutableStateOf(false) }
    var showCopyHelperSheet by remember { mutableStateOf(false) }
    var isFavoritesExpanded by remember { mutableStateOf(false) }
    var favoritesTargetField by remember { mutableStateOf(RouteSearchField.DESTINATION) }
    var routeMapCameraRequest by remember { mutableStateOf<CameraMoveRequest?>(null) }
    var cameraRequestSeq by remember { mutableLongStateOf(1L) }
    var showTwoFingerHint by remember { mutableStateOf(false) }
    var twoFingerHintTrigger by remember { mutableIntStateOf(0) }
    var hasShownInitialTwoFingerHint by remember { mutableStateOf(false) }

    val activeRoute = (uiState.routingStatus as? RoutingStatus.Success)?.route

    // Show the animated 2-finger pan hint briefly (~1.9s) on first route display or on any 1-finger drag attempt
    LaunchedEffect(twoFingerHintTrigger) {
        if (twoFingerHintTrigger > 0) {
            showTwoFingerHint = true
            delay(1900L)
            showTwoFingerHint = false
        }
    }

    LaunchedEffect(activeRoute?.id) {
        if (activeRoute != null && !hasShownInitialTwoFingerHint && !isRouteMapFullscreen) {
            hasShownInitialTwoFingerHint = true
            twoFingerHintTrigger++
        }
        if (activeRoute == null) {
            isRouteMapFullscreen = false
            showCopyHelperSheet = false
        }
    }

    fun jumpCameraToStart(route: GeographicScooterRoute) {
        cameraRequestSeq++
        routeMapCameraRequest = CameraMoveRequest(
            target = route.startLocation.location,
            zoom = smartMileZoomForDistance(route.originStation.estimatedScooterKm),
            requestId = cameraRequestSeq
        )
    }

    fun jumpCameraToDestination(route: GeographicScooterRoute) {
        cameraRequestSeq++
        routeMapCameraRequest = CameraMoveRequest(
            target = route.destinationLocation.location,
            zoom = smartMileZoomForDistance(route.destinationStation.estimatedScooterKm),
            requestId = cameraRequestSeq
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchRouteLastLocation(context) { point ->
                onGpsStartFound(point ?: userLocation ?: GeoDistanceUtils.MUNICH_CENTER)
            }
        } else {
            onShowFeedback("GPS abgelehnt – wähle den Start per Suche oder auf der Karte.")
        }
    }

    fun requestGpsStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchRouteLastLocation(context) { point ->
                onGpsStartFound(point ?: userLocation ?: GeoDistanceUtils.MUNICH_CENTER)
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

    // Full-screen interactive Map Picker overlay when user chooses "Auf Karte setzen"
    if (mapPickerField != null) {
        val pickingField = mapPickerField!!
        val activePickerChoice = if (pickingField == RouteSearchField.START) {
            uiState.startMode
        } else {
            uiState.destinationMode
        }
        BackHandler { mapPickerField = null }
        RouteMapPickerOverlay(
            field = pickingField,
            initialCenter = userLocation ?: lastMapCameraCenter,
            isMinimalScooterMode = mapDisplayPrefs.isMinimalScooterMode,
            isProminentLines = false,
            darkMapStyleOverride = mapDisplayPrefs.isNightMapOverride,
            activeTransitChoice = activePickerChoice,
            dynamicOverlay = dynamicOverlay,
            userLocation = userLocation,
            onGpsLocationFound = onUpdateUserLocation,
            onShowFeedback = onShowFeedback,
            onBuildQuickPointLocation = onBuildQuickPointLocation,
            onResolvePreviewLocation = onResolvePreviewLocation,
            onPointConfirmed = { location ->
                onSetLocationFromMap(pickingField, location)
                mapPickerField = null
            },
            onCancel = { mapPickerField = null },
            modifier = modifier
        )
        return
    }

    // Full-screen interactive Route Map overlay (1-finger pan/zoom enabled, preserves Route state)
    if (isRouteMapFullscreen && activeRoute != null) {
        BackHandler { isRouteMapFullscreen = false }
        RouteFullscreenMapOverlay(
            route = activeRoute,
            mapDisplayPrefs = mapDisplayPrefs,
            dynamicOverlay = dynamicOverlay,
            userLocation = userLocation,
            cameraMoveRequest = routeMapCameraRequest,
            onCameraMoveHandled = { routeMapCameraRequest = null },
            onJumpStart = { jumpCameraToStart(activeRoute) },
            onJumpDestination = { jumpCameraToDestination(activeRoute) },
            onExitFullscreen = { isRouteMapFullscreen = false },
            modifier = modifier
        )
        return
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Header + Live Battery Quick Chip
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.route_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Geografische Scooter-First Verbindung & Haltestellen-Wahl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    BatteryQuickChip(
                        profile = scooterProfile,
                        onClick = onOpenBatterySheet
                    )
                }
            }

            // Start & Destination Input Form Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        // --- STARTPUNKT ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.route_start_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SmallActionPill(
                                        icon = Icons.Filled.MyLocation,
                                        label = "GPS",
                                        onClick = {
                                            focusManager.clearFocus()
                                            requestGpsStart()
                                        },
                                        testTag = "route_start_gps_button"
                                    )
                                    SmallActionPill(
                                        icon = Icons.Filled.AddLocationAlt,
                                        label = "Auf Karte",
                                        onClick = {
                                            focusManager.clearFocus()
                                            mapPickerField = RouteSearchField.START
                                        },
                                        testTag = "route_start_map_pick_button"
                                    )
                                }
                            }

                            RouteAutocompleteTextField(
                                value = if (uiState.activeField == RouteSearchField.START) {
                                    uiState.startQuery
                                } else {
                                    uiState.startLocation?.name ?: uiState.startQuery
                                },
                                placeholder = "Adresse, Ort oder Bahnhof suchen…",
                                suggestions = if (uiState.activeField == RouteSearchField.START) {
                                    uiState.fieldSuggestions
                                } else {
                                    emptyList()
                                },
                                hasFavorites = favorites.isNotEmpty(),
                                isFavoritesPickerActive = isFavoritesExpanded && favoritesTargetField == RouteSearchField.START,
                                favoriteButtonCd = stringResource(R.string.route_favorites_pick_start_cd),
                                favoriteButtonTestTag = "route_start_favorites_button",
                                onFavoriteClick = {
                                    focusManager.clearFocus()
                                    if (isFavoritesExpanded && favoritesTargetField == RouteSearchField.START) {
                                        isFavoritesExpanded = false
                                    } else {
                                        favoritesTargetField = RouteSearchField.START
                                        isFavoritesExpanded = true
                                    }
                                },
                                onValueChange = { onFieldQueryChanged(RouteSearchField.START, it) },
                                onClear = { onClearField(RouteSearchField.START) },
                                onSuggestionClick = { loc ->
                                    focusManager.clearFocus()
                                    onSuggestionSelected(loc)
                                },
                                onDismissSuggestions = onDismissSuggestions,
                                testTag = "route_start_input"
                            )

                            RouteTransitChoiceRow(
                                label = "Start-Netz",
                                selected = uiState.startMode,
                                onSelect = onSetStartMode,
                                testTagPrefix = "route_start_mode"
                            )

                            uiState.preferredOriginStation?.let { preferredStart ->
                                PreferredStationLockChip(
                                    label = stringResource(
                                        R.string.route_preferred_origin_station,
                                        preferredStart.name
                                    ),
                                    onClear = onClearPreferredOriginStation,
                                    testTag = "route_preferred_origin_station_chip"
                                )
                            }
                        }

                        // Divider + Swap Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    onSwapLocations()
                                },
                                enabled = uiState.startLocation != null || uiState.destinationLocation != null,
                                modifier = Modifier.testTag("route_swap_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = stringResource(R.string.route_swap_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // --- ZIELPUNKT (Adresssuche oder Karte – KEIN GPS-Button!) ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.route_destination_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                SmallActionPill(
                                    icon = Icons.Filled.AddLocationAlt,
                                    label = "Auf Karte",
                                    onClick = {
                                        focusManager.clearFocus()
                                        mapPickerField = RouteSearchField.DESTINATION
                                    },
                                    testTag = "route_dest_map_pick_button"
                                )
                            }

                            RouteAutocompleteTextField(
                                value = if (uiState.activeField == RouteSearchField.DESTINATION) {
                                    uiState.destinationQuery
                                } else {
                                    uiState.destinationLocation?.name ?: uiState.destinationQuery
                                },
                                placeholder = "Ziel-Adresse, Ort oder Bahnhof…",
                                suggestions = if (uiState.activeField == RouteSearchField.DESTINATION) {
                                    uiState.fieldSuggestions
                                } else {
                                    emptyList()
                                },
                                hasFavorites = favorites.isNotEmpty(),
                                isFavoritesPickerActive = isFavoritesExpanded && favoritesTargetField == RouteSearchField.DESTINATION,
                                favoriteButtonCd = stringResource(R.string.route_favorites_pick_dest_cd),
                                favoriteButtonTestTag = "route_dest_favorites_button",
                                onFavoriteClick = {
                                    focusManager.clearFocus()
                                    if (isFavoritesExpanded && favoritesTargetField == RouteSearchField.DESTINATION) {
                                        isFavoritesExpanded = false
                                    } else {
                                        favoritesTargetField = RouteSearchField.DESTINATION
                                        isFavoritesExpanded = true
                                    }
                                },
                                onValueChange = { onFieldQueryChanged(RouteSearchField.DESTINATION, it) },
                                onClear = { onClearField(RouteSearchField.DESTINATION) },
                                onSuggestionClick = { loc ->
                                    focusManager.clearFocus()
                                    onSuggestionSelected(loc)
                                },
                                onDismissSuggestions = onDismissSuggestions,
                                testTag = "route_destination_input"
                            )

                            RouteTransitChoiceRow(
                                label = "Ziel-Netz",
                                selected = uiState.destinationMode,
                                onSelect = onSetDestinationMode,
                                testTagPrefix = "route_dest_mode"
                            )

                            uiState.preferredDestinationStation?.let { preferredDest ->
                                PreferredStationLockChip(
                                    label = stringResource(
                                        R.string.route_preferred_dest_station,
                                        preferredDest.name
                                    ),
                                    onClear = onClearPreferredDestinationStation,
                                    testTag = "route_preferred_dest_station_chip"
                                )
                            }
                        }

                        // Expandable Favorites Picker for Start & Ziel with Drag & Drop reordering
                        if (favorites.isNotEmpty() || isFavoritesExpanded) {
                            RouteFavoritesAccordionPanel(
                                favorites = favorites,
                                expanded = isFavoritesExpanded,
                                targetField = favoritesTargetField,
                                onToggleExpanded = {
                                    focusManager.clearFocus()
                                    isFavoritesExpanded = !isFavoritesExpanded
                                },
                                onTargetFieldChanged = { favoritesTargetField = it },
                                onSelectFavorite = { target, loc ->
                                    focusManager.clearFocus()
                                    onSetLocationFromMap(target, loc)
                                    isFavoritesExpanded = false
                                },
                                onReorderFavorites = onReorderFavorites,
                                onDeleteFavorite = onDeleteFavorite
                            )
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onExecuteSearch()
                            },
                            enabled = uiState.startLocation != null &&
                                uiState.destinationLocation != null &&
                                uiState.routingStatus !is RoutingStatus.Loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("route_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Route,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.route_search_button))
                        }
                    }
                }
            }

            // Geographic Routing Result Section
            when (val status = uiState.routingStatus) {
                RoutingStatus.Idle -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ElectricScooter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = stringResource(R.string.route_empty_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.route_empty_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                RoutingStatus.Loading -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(26.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Suche optimale Haltestellen & Umstiegspunkt…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is RoutingStatus.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                OutlinedButton(onClick = onExecuteSearch) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.route_retry))
                                }
                            }
                        }
                    }
                }

                is RoutingStatus.Success -> {
                    val route = status.route
                    // 1. Embedded Interactive Geographic Route Map Preview (2-finger pan + fullscreen + Start/Ziel)
                    item(key = "route_map_${route.id}") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .testTag("route_preview_map_card")
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
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
                                    cameraMoveRequest = routeMapCameraRequest,
                                    onCameraMoveHandled = { routeMapCameraRequest = null },
                                    onMapLongPress = {},
                                    onMapCameraIdle = { _, _ -> },
                                    plannedRoute = route,
                                    initialCenter = route.startLocation.location,
                                    initialZoom = 11.8,
                                    requireTwoFingerPan = true,
                                    onSingleFingerPanAttempt = { twoFingerHintTrigger++ },
                                    darkMapStyleOverride = mapDisplayPrefs.isNightMapOverride,
                                    attributionAlignment = Alignment.BottomEnd,
                                    attributionPadding = PaddingValues(end = 8.dp, bottom = 6.dp),
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Top-End Fullscreen Toggle Button
                                RouteMapFullscreenExpandButton(
                                    onClick = {
                                        showTwoFingerHint = false
                                        isRouteMapFullscreen = true
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                )

                                // Bottom-Start Quick-Jump Buttons ("Start" & "Ziel")
                                RouteMapQuickJumpRow(
                                    onJumpStart = { jumpCameraToStart(route) },
                                    onJumpDestination = { jumpCameraToDestination(route) },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                )

                                // Animated 2-Finger Pan Hint Overlay
                                TwoFingerPanHintOverlay(
                                    visible = showTwoFingerHint,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // 2. Geographic Route Breakdown + Battery Check + 2 Simple External App Buttons
                    item(key = "route_card_${route.id}") {
                        GeographicRouteResultCard(
                            route = route,
                            appSettings = appSettings,
                            batteryFallbackSuggestion = uiState.batteryFallbackSuggestion,
                            onApplyFallbackSuggestion = { suggestion ->
                                val newMode = suggestion.suggestedRouteMode ?: return@GeographicRouteResultCard
                                if (suggestion.targetIsRouteStart == true) {
                                    onSetStartMode(newMode)
                                } else {
                                    onSetDestinationMode(newMode)
                                }
                            },
                            onOpenFirstMileMapsApp = {
                                val res = ExternalAppLauncher.openMapsApp(
                                    context = context,
                                    preferredMapsApp = appSettings.preferredMapsApp,
                                    origin = route.startLocation.location,
                                    destination = route.originStation.station.location,
                                    destinationLabel = route.originStation.station.name,
                                    originLabel = route.startLocation.name
                                )
                                onShowFeedback(res.message)
                            },
                            onOpenLastMileMapsApp = {
                                val res = ExternalAppLauncher.openMapsApp(
                                    context = context,
                                    preferredMapsApp = appSettings.preferredMapsApp,
                                    origin = route.destinationStation.station.location,
                                    destination = route.destinationLocation.location,
                                    destinationLabel = route.destinationLocation.name,
                                    originLabel = route.destinationStation.station.name
                                )
                                onShowFeedback(res.message)
                            },
                            onOpenTransitApp = {
                                val res = ExternalAppLauncher.openTransitApp(
                                    context = context,
                                    preferredTransitApp = appSettings.preferredTransitApp,
                                    originStationName = route.originStation.station.name,
                                    destinationStationName = route.destinationStation.station.name,
                                    transferStationName = route.transferStation?.name
                                )
                                onShowFeedback(res.message)
                            },
                            onOpenCopyHelper = {
                                showCopyHelperSheet = true
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showCopyHelperSheet && activeRoute != null) {
            RouteCopyHelperSheet(
                route = activeRoute,
                preferredTransitApp = appSettings.preferredTransitApp,
                onDismiss = { showCopyHelperSheet = false },
                onLaunchFeedback = onShowFeedback
            )
        }
    }
}

@Composable
private fun SmallActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RouteAutocompleteTextField(
    value: String,
    placeholder: String,
    suggestions: List<SearchLocation>,
    hasFavorites: Boolean,
    isFavoritesPickerActive: Boolean,
    favoriteButtonCd: String,
    favoriteButtonTestTag: String,
    onFavoriteClick: () -> Unit,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSuggestionClick: (SearchLocation) -> Unit,
    onDismissSuggestions: () -> Unit,
    testTag: String
) {
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.map_clear_search_cd),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.testTag(favoriteButtonTestTag)
                    ) {
                        Icon(
                            imageVector = if (hasFavorites || isFavoritesPickerActive) {
                                Icons.Filled.Star
                            } else {
                                Icons.Filled.StarBorder
                            },
                            contentDescription = favoriteButtonCd,
                            tint = when {
                                isFavoritesPickerActive -> MaterialTheme.colorScheme.primary
                                hasFavorites -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords -> fieldWidthPx = coords.size.width }
                .testTag(testTag)
        )

        // Floating autocomplete dropdown anchored directly below this exact text field.
        // focusable = false keeps the soft keyboard open while typing!
        DropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = onDismissSuggestions,
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .then(
                    if (fieldWidthPx > 0) {
                        Modifier.width(with(density) { fieldWidthPx.toDp() })
                    } else {
                        Modifier.fillMaxWidth(0.9f)
                    }
                )
                .heightIn(max = 240.dp)
                .testTag("${testTag}_suggestions_dropdown")
        ) {
            suggestions.take(6).forEachIndexed { idx, loc ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(loc) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (loc.stationId != null) Icons.Filled.Train else Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = loc.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (loc.subtitle.isNotBlank()) {
                            Text(
                                text = loc.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (idx < suggestions.take(6).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun RouteTransitChoiceRow(
    label: String,
    selected: RouteTransitChoice,
    onSelect: (RouteTransitChoice) -> Unit,
    testTagPrefix: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RouteTransitChoice.entries.forEach { choice ->
                val isSelected = selected == choice
                val icon = when (choice) {
                    RouteTransitChoice.SBAHN -> Icons.Filled.Train
                    RouteTransitChoice.BUS -> Icons.Filled.DirectionsBus
                    RouteTransitChoice.REGIO_DB -> Icons.Filled.DirectionsRailway
                }
                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }

                Surface(
                    onClick = { onSelect(choice) },
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = BorderStroke(if (isSelected) 1.5.dp else 0.75.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .testTag("${testTagPrefix}_${choice.name.lowercase(Locale.US)}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = choice.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferredStationLockChip(
    label: String,
    onClear: () -> Unit,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear)
                    .testTag("${testTag}_clear")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.route_preferred_station_clear_cd),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun GeographicRouteResultCard(
    route: GeographicScooterRoute,
    appSettings: AppSettings,
    batteryFallbackSuggestion: BatteryRangeFallbackSuggestion?,
    onApplyFallbackSuggestion: (BatteryRangeFallbackSuggestion) -> Unit,
    onOpenFirstMileMapsApp: () -> Unit,
    onOpenLastMileMapsApp: () -> Unit,
    onOpenTransitApp: () -> Unit,
    onOpenCopyHelper: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("geographic_route_result_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Summary Row (Pure Geographic — NO live clock times)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.compactHeadline(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("route_compact_headline")
                    )
                    Text(
                        text = String.format(
                            Locale.GERMAN,
                            "Scooter gesamt (einfach): %.1f km · ca. %d min",
                            route.totalScooterOneWayKm,
                            route.totalScooterOneWayMinutes
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!route.isOfflineEstimate) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("route_bike_route_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.routing_bike_route_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Step 1: First Mile (Scooter -> Origin Station)
            ScooterSegmentRow(
                label = "1. Erste Meile → ${route.originStation.station.name}",
                distanceKm = route.originStation.estimatedScooterKm,
                durationMin = route.originStation.estimatedScooterMinutes,
                onOpenMaps = onOpenFirstMileMapsApp,
                mapsButtonTestTag = "route_first_mile_leg_maps_button"
            )

            // Step 2+: Vertical ÖPNV Timeline (line badges, travel directions & highlighted transfer node)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("route_transit_timeline_card")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    route.transitSegments.forEachIndexed { idx, seg ->
                        if (idx > 0) {
                            TimelineTransferNode(
                                stationName = seg.fromStation.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                        GeographicTransitSegmentRow(
                            segment = seg,
                            isFirstSegment = idx == 0,
                            isLastSegment = idx == route.transitSegments.lastIndex
                        )
                    }
                }
            }

            // Step 3: Last Mile (Destination Station -> Target)
            ScooterSegmentRow(
                label = "Letzte Meile ab ${route.destinationStation.station.name}",
                distanceKm = route.destinationStation.estimatedScooterKm,
                durationMin = route.destinationStation.estimatedScooterMinutes,
                onOpenMaps = onOpenLastMileMapsApp,
                mapsButtonTestTag = "route_last_mile_leg_maps_button"
            )

            // Bus Carry Warning Pill (clickable info dialog!)
            if (route.hasBusWarning) {
                BusCarryWarningPill(
                    expanded = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Full Round-Trip Battery Assessment (Hin- & Rückweg)
            BatteryTrafficLightBanner(assessment = route.batteryAssessment)

            batteryFallbackSuggestion?.let { suggestion ->
                BatteryRangeFallbackBanner(
                    suggestion = suggestion,
                    onSwitchClick = { onApplyFallbackSuggestion(suggestion) },
                    testTag = "route_battery_fallback_banner"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // External App Buttons: Regional Transit App + Splitscreen Copy Helper
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.route_external_transit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onOpenTransitApp,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("route_open_transit_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = appSettings.preferredTransitApp.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenCopyHelper,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("route_copy_helper_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Splitscreen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.route_copy_helper_button),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTransferNode(
    stationName: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag("route_timeline_transfer_node")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(24.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Umstieg am $stationName",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun GeographicTransitSegmentRow(
    segment: GeographicTransitSegment,
    isFirstSegment: Boolean = true,
    isLastSegment: Boolean = true
) {
    val badges = segment.suggestedLines.ifEmpty { listOf(segment.mode.shortLabel) }
    val primaryLine = badges.first()
    val trackColor = remember(segment.lineColorHex, primaryLine) {
        try {
            Color(android.graphics.Color.parseColor(segment.lineColorHex))
        } catch (_: Exception) {
            lineColorForName(primaryLine)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Vertical timeline rail colored in official line accent
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isFirstSegment) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(trackColor)
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .padding(vertical = 2.dp)
                    .background(trackColor.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
            )
            if (isLastSegment) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(trackColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLastSegment) 0.dp else 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    badges.take(3).forEach { line ->
                        TransitLineBadge(lineName = line)
                    }
                }
                Text(
                    text = String.format(Locale.GERMAN, "ca. %.1f km", segment.airDistanceKm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${segment.fromStation.name} → ${segment.toStation.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val directionLabel = segment.directionTerminal.ifBlank { segment.toStation.name }
            Text(
                text = "→ Richtung $directionLabel",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("route_segment_direction")
            )
            if (segment.fromStation.name.equals(segment.toStation.name, ignoreCase = true)) {
                Text(
                    text = segment.connectionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScooterSegmentRow(
    label: String,
    distanceKm: Double,
    durationMin: Int,
    onOpenMaps: (() -> Unit)? = null,
    mapsButtonTestTag: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ElectricScooter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = String.format(Locale.GERMAN, "%.1f km · ca. %d min (Scooter)", distanceKm, durationMin),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
        if (onOpenMaps != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onOpenMaps,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .let { mod -> if (mapsButtonTestTag != null) mod.testTag(mapsButtonTestTag) else mod }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = stringResource(R.string.route_open_leg_maps_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.route_leg_maps_pill),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteMapPickerOverlay(
    field: RouteSearchField,
    initialCenter: GeoPoint,
    isMinimalScooterMode: Boolean,
    isProminentLines: Boolean,
    darkMapStyleOverride: Boolean?,
    activeTransitChoice: RouteTransitChoice,
    dynamicOverlay: DynamicTransitOverlay,
    userLocation: GeoPoint?,
    onGpsLocationFound: (GeoPoint) -> Unit,
    onShowFeedback: (String) -> Unit,
    onBuildQuickPointLocation: (GeoPoint) -> SearchLocation,
    onResolvePreviewLocation: suspend (GeoPoint) -> SearchLocation,
    onPointConfirmed: (SearchLocation) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = if (field == RouteSearchField.START) {
        "Startpunkt auf der Karte wählen"
    } else {
        "Zielpunkt auf der Karte wählen"
    }
    val pickerTransitMode = activeTransitChoice.toMapTransitMode()
    val pickerBucketVis = TransitBucketVisibility.fromMapTransitMode(pickerTransitMode)

    var pickerCameraRequest by remember { mutableStateOf<CameraMoveRequest?>(null) }
    var pickerCameraSeq by remember { mutableLongStateOf(1L) }

    // Two-step preview state: tapping places/moves the preview pin; only "Übernehmen" commits it
    var pendingLocation by remember(field) { mutableStateOf<SearchLocation?>(null) }
    var isResolvingPreview by remember(field) { mutableStateOf(false) }
    var isPreviewMinimized by remember(field) { mutableStateOf(false) }
    var previewTapSeq by remember(field) { mutableLongStateOf(0L) }

    LaunchedEffect(previewTapSeq) {
        if (previewTapSeq == 0L) return@LaunchedEffect
        val snapshot = pendingLocation ?: return@LaunchedEffect
        isResolvingPreview = true
        val resolved = try {
            onResolvePreviewLocation(snapshot.location)
        } catch (_: Exception) {
            snapshot
        }
        if (pendingLocation?.id == snapshot.id) {
            pendingLocation = resolved
        }
        isResolvingPreview = false
    }

    val handlePreviewTap: (GeoPoint) -> Unit = { point ->
        pendingLocation = onBuildQuickPointLocation(point)
        isPreviewMinimized = false
        previewTapSeq++
    }

    fun animateToGpsPoint(point: GeoPoint) {
        onGpsLocationFound(point)
        pickerCameraSeq++
        pickerCameraRequest = CameraMoveRequest(
            target = point,
            zoom = 14.5,
            requestId = pickerCameraSeq
        )
    }

    // If no GPS fix was cached yet but permission is already granted, silently center on GPS
    LaunchedEffect(field) {
        if (userLocation == null) {
            val fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                fetchRouteLastLocation(context) { point ->
                    if (point != null) {
                        animateToGpsPoint(point)
                    }
                }
            }
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchRouteLastLocation(context) { point ->
                val target = point ?: userLocation
                if (target != null) {
                    animateToGpsPoint(target)
                } else {
                    onShowFeedback("Aktuelle GPS-Position konnte gerade nicht ermittelt werden.")
                }
            }
        } else {
            onShowFeedback("Standortzugriff abgelehnt – verschiebe die Karte manuell.")
        }
    }

    fun jumpToCurrentGps() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchRouteLastLocation(context) { point ->
                val target = point ?: userLocation
                if (target != null) {
                    animateToGpsPoint(target)
                } else {
                    onShowFeedback("Aktuelle GPS-Position konnte gerade nicht ermittelt werden.")
                }
            }
        } else {
            gpsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MunichScooterMapView(
            isMinimalScooterMode = isMinimalScooterMode,
            isProminentLines = isProminentLines,
            transitMode = pickerTransitMode,
            bucketVisibilityOverride = pickerBucketVis,
            userLocation = userLocation,
            selectedTarget = pendingLocation,
            nearestStations = emptyList(),
            dynamicOverlay = dynamicOverlay,
            cameraMoveRequest = pickerCameraRequest,
            onCameraMoveHandled = { pickerCameraRequest = null },
            onMapTap = handlePreviewTap,
            onMapLongPress = handlePreviewTap,
            onMapCameraIdle = { _, _ -> },
            initialCenter = initialCenter,
            initialZoom = if (userLocation != null) 13.8 else 12.6,
            darkMapStyleOverride = darkMapStyleOverride,
            enableBundledLineTooltip = false,
            attributionAlignment = if (pendingLocation != null) {
                Alignment.TopStart
            } else {
                Alignment.BottomStart
            },
            attributionPadding = if (pendingLocation != null) {
                PaddingValues(start = 14.dp, top = 86.dp)
            } else {
                PaddingValues(start = 12.dp, bottom = 24.dp)
            },
            attributionUseStatusBarsPadding = pendingLocation != null,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AddLocationAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Netz: ${activeTransitChoice.shortLabel} · " + stringResource(
                            if (pendingLocation == null) {
                                R.string.route_picker_tap_hint
                            } else {
                                R.string.route_picker_adjust_hint
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("route_map_picker_cancel")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Abbrechen"
                    )
                }
            }
        }

        // Bottom stack: GPS jump FAB cleanly above the preview confirmation bar
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
                .widthIn(max = 580.dp)
        ) {
            FloatingActionButton(
                onClick = { jumpToCurrentGps() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .testTag("route_map_picker_gps_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.map_my_location_cd)
                )
            }

            AnimatedVisibility(
                visible = pendingLocation != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                pendingLocation?.let { pending ->
                    RouteMapPickerPreviewBar(
                        field = field,
                        pendingLocation = pending,
                        isResolving = isResolvingPreview,
                        isMinimized = isPreviewMinimized,
                        onToggleMinimized = { isPreviewMinimized = !isPreviewMinimized },
                        onConfirm = { onPointConfirmed(pending) },
                        onDiscard = {
                            pendingLocation = null
                            isResolvingPreview = false
                            isPreviewMinimized = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteMapPickerPreviewBar(
    field: RouteSearchField,
    pendingLocation: SearchLocation,
    isResolving: Boolean,
    isMinimized: Boolean,
    onToggleMinimized: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    val coordsText = String.format(
        Locale.US,
        "%.4f, %.4f",
        pendingLocation.location.latitude,
        pendingLocation.location.longitude
    )
    val confirmLabel = stringResource(
        if (field == RouteSearchField.START) {
            R.string.route_picker_confirm_start
        } else {
            R.string.route_picker_confirm_destination
        }
    )

    Surface(
        shape = RoundedCornerShape(if (isMinimized) 18.dp else 22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
            .testTag("route_map_picker_preview_bar")
    ) {
        if (isMinimized) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleMinimized)
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    .testTag("route_map_picker_preview_minimized_bar")
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
                        text = pendingLocation.name,
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
                IconButton(
                    onClick = onToggleMinimized,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("route_map_picker_maximize_preview")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.map_maximize_card_cd),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("route_map_picker_clear_preview")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.route_picker_discard_pin_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 14.dp)
            ) {
                // Clickable top drag-handle + header row to minimize
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleMinimized)
                        .testTag("route_map_picker_preview_header_toggle")
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
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = pendingLocation.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isResolving) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isResolving) {
                                    "${stringResource(R.string.route_picker_resolving_address)} · $coordsText"
                                } else {
                                    pendingLocation.subtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onToggleMinimized,
                            modifier = Modifier.testTag("route_map_picker_minimize_preview")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.map_minimize_card_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDiscard,
                            modifier = Modifier.testTag("route_map_picker_clear_preview")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.route_picker_discard_pin_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("route_map_picker_confirm_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchRouteLastLocation(context: Context, onResult: (GeoPoint?) -> Unit) {
    try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onResult(GeoPoint(loc.latitude, loc.longitude))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    } catch (_: Exception) {
        onResult(null)
    }
}
