package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.BatteryQuickAdjustSheet
import com.example.ui.components.InitialRegionSetupDialog
import com.example.ui.drawer.AboutLetzteMeileSheet
import com.example.ui.drawer.SettingsModalSheet
import com.example.ui.map.MapScreen
import com.example.ui.route.RouteScreen
import com.example.ui.scooter.ScooterScreen
import com.example.ui.theme.LetzteMeileTheme
import com.example.viewmodel.LetzteMeileViewModel
import com.example.viewmodel.MapPointRole
import kotlinx.coroutines.launch

private const val ROUTE_MAP = "map"
private const val ROUTE_PLANNER = "route"
private const val ROUTE_SCOOTER = "scooter"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val AppTabs = listOf(
    NavTab(
        route = ROUTE_MAP,
        labelRes = R.string.nav_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_tab_map"
    ),
    NavTab(
        route = ROUTE_PLANNER,
        labelRes = R.string.nav_route,
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route,
        testTag = "nav_tab_route"
    ),
    NavTab(
        route = ROUTE_SCOOTER,
        labelRes = R.string.nav_scooter,
        selectedIcon = Icons.Filled.ElectricScooter,
        unselectedIcon = Icons.Outlined.ElectricScooter,
        testTag = "nav_tab_scooter"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LetzteMeileTheme {
                LetzteMeileApp()
            }
        }
    }
}

@Composable
fun LetzteMeileApp(
    viewModel: LetzteMeileViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_MAP

    val scooterProfile by viewModel.scooterProfile.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currentRegion by viewModel.currentRegion.collectAsStateWithLifecycle()
    val regionSwitchPrompt by viewModel.regionSwitchPrompt.collectAsStateWithLifecycle()
    val customScooters by viewModel.customScooters.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    val routeState by viewModel.routeState.collectAsStateWithLifecycle()
    val initialSetupCompleted by viewModel.initialSetupCompleted.collectAsStateWithLifecycle()
    val regionalPackageStatuses by viewModel.regionalPackageStatuses.collectAsStateWithLifecycle()
    val isDownloadingAllRegions by viewModel.isDownloadingAllRegions.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showBatteryQuickSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var returnToSettingsFromAbout by remember { mutableStateOf(false) }

    LaunchedEffect(showSettingsSheet) {
        if (showSettingsSheet) {
            viewModel.refreshRegionalPackageStatuses()
        }
    }

    // Subtle Snackbar when entering a new transit region with Auto-Region Detection enabled
    LaunchedEffect(regionSwitchPrompt?.regionKey) {
        val prompt = regionSwitchPrompt ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${prompt.regionDisplayName} erkannt – ${prompt.recommendedApp.displayName} nutzen?",
            actionLabel = "Wechseln",
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.acceptRegionSwitchPrompt()
            SnackbarResult.Dismissed -> viewModel.dismissRegionSwitchPrompt()
        }
    }

    // One-shot feedback Snackbars (e.g. after "ÖPNV-Daten neu laden" completes or fails)
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Prevent horizontal map panning from accidentally dragging the drawer open
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("navigation_drawer_sheet")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ElectricScooter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentRegion.regionDisplayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${appSettings.preferredTransitApp.displayName} · ${appSettings.preferredMapsApp.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                val drawerItemColors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(R.string.drawer_reload_transit),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null
                        )
                    },
                    selected = false,
                    shape = RoundedCornerShape(14.dp),
                    colors = drawerItemColors,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.clearTransitCacheAndReload()
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_item_reload_transit")
                )

                Spacer(modifier = Modifier.height(4.dp))

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(R.string.drawer_settings),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null
                        )
                    },
                    selected = false,
                    shape = RoundedCornerShape(14.dp),
                    colors = drawerItemColors,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettingsSheet = true
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_item_settings")
                )

                Spacer(modifier = Modifier.height(4.dp))

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(R.string.drawer_about),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null
                        )
                    },
                    selected = false,
                    shape = RoundedCornerShape(14.dp),
                    colors = drawerItemColors,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showAboutSheet = true
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_item_about")
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            val navHostContent: @Composable (Modifier) -> Unit = { contentModifier ->
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_MAP,
                    modifier = contentModifier
                ) {
                    composable(ROUTE_MAP) {
                        MapScreen(
                            uiState = mapState,
                            scooterProfile = scooterProfile,
                            preferredMapsApp = appSettings.preferredMapsApp,
                            favorites = favorites,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onSearchQueryChanged = viewModel::onMapSearchQueryChanged,
                            onClearSearch = viewModel::clearMapSearch,
                            onSelectTarget = {
                                viewModel.selectMapTarget(
                                    location = it,
                                    animateCamera = true,
                                    defaultRole = MapPointRole.DESTINATION
                                )
                            },
                            onClearTarget = viewModel::clearSelectedTarget,
                            onMapPointPicked = viewModel::onMapPointPicked,
                            onMapCameraIdle = viewModel::onMapCameraIdle,
                            onMapBoundsChanged = viewModel::onMapBoundsChanged,
                            onCameraMoveHandled = viewModel::onCameraMoveHandled,
                            onToggleDayNightMap = viewModel::toggleDayNightMapLayout,
                            onCycleTransitMode = viewModel::cycleTransitMode,
                            onSelectTransitMode = viewModel::selectTransitMode,
                            onUserLocationFound = { point, isRealGps ->
                                viewModel.updateUserLocation(
                                    point = point,
                                    moveCamera = true,
                                    isRealGps = isRealGps,
                                    selectAsMapTarget = true
                                )
                            },
                            onSilentGpsLocationFound = { point ->
                                viewModel.updateUserLocation(
                                    point = point,
                                    moveCamera = false,
                                    isRealGps = true,
                                    selectAsMapTarget = false
                                )
                            },
                            onOpenBatterySheet = { showBatteryQuickSheet = true },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onSelectStationOption = viewModel::selectNearestStationOption,
                            onSetQuickRouteStart = { start ->
                                if (viewModel.setQuickRouteStart(start)) {
                                    navigateToTab(ROUTE_PLANNER)
                                }
                            },
                            onSetQuickRouteDestination = { destination ->
                                if (viewModel.setQuickRouteDestination(destination)) {
                                    navigateToTab(ROUTE_PLANNER)
                                }
                            },
                            onClearQuickRouteStart = viewModel::clearQuickRouteStart,
                            onClearQuickRouteDestination = viewModel::clearQuickRouteDestination,
                            onShowFeedback = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = msg,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onDismissTransitWarning = viewModel::dismissTransitWarningHint,
                            onOpenLegalInfo = { showAboutSheet = true }
                        )
                    }

                    composable(ROUTE_PLANNER) {
                        RouteScreen(
                            uiState = routeState,
                            scooterProfile = scooterProfile,
                            appSettings = appSettings,
                            mapDisplayPrefs = mapState.displayPrefs,
                            dynamicOverlay = mapState.dynamicOverlay,
                            userLocation = mapState.userLocation,
                            lastMapCameraCenter = mapState.lastCameraCenter,
                            favorites = favorites,
                            onFieldQueryChanged = viewModel::onRouteFieldQueryChanged,
                            onSuggestionSelected = viewModel::onRouteSuggestionSelected,
                            onDismissSuggestions = viewModel::dismissRouteSuggestions,
                            onClearField = viewModel::clearRouteField,
                            onBuildQuickPointLocation = viewModel::buildQuickPointLocation,
                            onResolvePreviewLocation = viewModel::resolveTappedMapPoint,
                            onSetLocationFromMap = { field, location ->
                                viewModel.setRouteLocationFromMap(field, location)
                            },
                            onSetStartMode = viewModel::setRouteStartMode,
                            onSetDestinationMode = viewModel::setRouteDestinationMode,
                            onClearPreferredOriginStation = viewModel::clearPreferredOriginStation,
                            onClearPreferredDestinationStation = viewModel::clearPreferredDestinationStation,
                            onSwapLocations = viewModel::swapStartAndDestination,
                            onGpsStartFound = viewModel::setRouteStartFromGps,
                            onUpdateUserLocation = { point ->
                                viewModel.updateUserLocation(point, moveCamera = false, isRealGps = true)
                            },
                            onExecuteSearch = viewModel::executeGeographicRoute,
                            onOpenBatterySheet = { showBatteryQuickSheet = true },
                            onReorderFavorites = viewModel::reorderFavorites,
                            onDeleteFavorite = viewModel::removeFavorite,
                            onShowFeedback = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = msg,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }

                    composable(ROUTE_SCOOTER) {
                        ScooterScreen(
                            profile = scooterProfile,
                            customScooters = customScooters,
                            favorites = favorites,
                            onBatteryChanged = viewModel::updateBatteryPercent,
                            onSaveCustomScooter = viewModel::saveCustomScooter,
                            onSelectCustomScooter = viewModel::selectCustomScooter,
                            onDeleteCustomScooter = viewModel::deleteCustomScooter,
                            onApplyPreset = viewModel::applyScooterPreset,
                            onSelectFavoriteOnMap = { loc ->
                                viewModel.selectMapTarget(loc, animateCamera = true)
                                navigateToTab(ROUTE_MAP)
                            },
                            onDeleteFavorite = viewModel::removeFavorite
                        )
                    }
                }
            }

            if (useNavRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val railColors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                colors = railColors,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                    Scaffold(
                        snackbarHost = {
                            if (!showSettingsSheet) {
                                SnackbarHost(hostState = snackbarHostState)
                            }
                        },
                        contentWindowInsets = if (currentRoute == ROUTE_MAP) {
                            WindowInsets(0, 0, 0, 0)
                        } else {
                            WindowInsets.safeDrawing
                        }
                    ) { innerPadding ->
                        navHostContent(Modifier.fillMaxSize().padding(innerPadding))
                    }
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = {
                        if (!showSettingsSheet) {
                            SnackbarHost(hostState = snackbarHostState)
                        }
                    },
                    contentWindowInsets = if (currentRoute == ROUTE_MAP) {
                        WindowInsets(0, 0, 0, 0)
                    } else {
                        WindowInsets.safeDrawing
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            val navBarColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AppTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    colors = navBarColors,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    navHostContent(Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }
    }

    if (showBatteryQuickSheet) {
        BatteryQuickAdjustSheet(
            profile = scooterProfile,
            onBatteryChanged = viewModel::updateBatteryPercent,
            onDismiss = { showBatteryQuickSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsModalSheet(
            settings = appSettings,
            detectedRegion = currentRegion,
            regionalPackageStatuses = regionalPackageStatuses,
            isDownloadingAllRegions = isDownloadingAllRegions,
            onSelectMapsApp = viewModel::setPreferredMapsApp,
            onSelectTransitApp = viewModel::setPreferredTransitApp,
            onToggleAutoRegion = viewModel::setAutoRegionDetection,
            onDownloadSingleRegion = viewModel::downloadSingleRegionalPackage,
            onDeleteSingleRegion = viewModel::deleteSingleRegionalPackage,
            onDownloadAllRegions = viewModel::downloadAllRegionalPackages,
            onCancelDownloadAllRegions = viewModel::cancelDownloadAllRegionalPackages,
            onClearTransitCache = {
                showSettingsSheet = false
                viewModel.clearTransitCacheAndReload()
            },
            onOpenAboutAndLegal = {
                returnToSettingsFromAbout = true
                showSettingsSheet = false
                showAboutSheet = true
            },
            onDismiss = { showSettingsSheet = false },
            snackbarHostState = snackbarHostState
        )
    }

    if (showAboutSheet) {
        AboutLetzteMeileSheet(
            onDismiss = {
                showAboutSheet = false
                if (returnToSettingsFromAbout) {
                    returnToSettingsFromAbout = false
                    showSettingsSheet = true
                }
            }
        )
    }

    if (initialSetupCompleted == false) {
        InitialRegionSetupDialog(
            initialSelection = appSettings.preferredTransitApp,
            onConfirmSelection = viewModel::completeInitialRegionSetup
        )
    }
    }
}
