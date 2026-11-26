package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomScooterEntity
import com.example.data.local.FavoritePlaceEntity
import com.example.data.local.LetzteMeileDatabase
import com.example.data.local.MapDisplayPreferences
import com.example.data.local.ScooterPreferencesRepository
import com.example.data.remote.DynamicTransitOverlay
import com.example.data.remote.NetworkModule
import com.example.data.remote.OverpassTransitRepository
import com.example.data.remote.RegionalPackageStatus
import com.example.data.remote.ScooterBikeRoutingRepository
import com.example.data.staticdata.MunichSBahnNetwork
import com.example.domain.BatteryRangeCalculator
import com.example.domain.GeoDistanceUtils
import com.example.domain.GeographicScooterRouter
import com.example.domain.TransitRegionDetector
import com.example.model.AppSettings
import com.example.model.BatteryRangeFallbackSuggestion
import com.example.model.BatteryRoundTripAssessment
import com.example.model.BatteryTrafficLight
import com.example.model.DefaultScooterPresets
import com.example.model.GeoPoint
import com.example.model.GeographicScooterRoute
import com.example.model.MapBounds
import com.example.model.MapTransitMode
import com.example.model.NearbyStationResult
import com.example.model.PreferredMapsApp
import com.example.model.PreferredTransitApp
import com.example.model.RouteTransitChoice
import com.example.model.ScooterPreset
import com.example.model.ScooterProfile
import com.example.model.SearchLocation
import com.example.model.StationType
import com.example.model.TransitRegionRecommendation
import com.example.model.TransitStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

enum class MapPointRole {
    START,
    DESTINATION
}

data class MapScreenUiState(
    val displayPrefs: MapDisplayPreferences = MapDisplayPreferences(),
    val userLocation: GeoPoint? = null,
    val selectedTarget: SearchLocation? = null,
    val selectedTargetRole: MapPointRole = MapPointRole.START,
    val quickRouteStart: SearchLocation? = null,
    val quickRouteStartStation: NearbyStationResult? = null,
    val quickRouteDestination: SearchLocation? = null,
    val quickRouteDestinationStation: NearbyStationResult? = null,
    val nearestStationsToTarget: List<NearbyStationResult> = emptyList(),
    val selectedStationIndex: Int = 0,
    val targetBatteryQuickCheck: BatteryRoundTripAssessment? = null,
    val batteryFallbackSuggestion: BatteryRangeFallbackSuggestion? = null,
    val dynamicOverlay: DynamicTransitOverlay = DynamicTransitOverlay.EMPTY,
    val isLoadingViewportTransit: Boolean = false,
    val transitWarningHint: String? = null,
    val lastCameraCenter: GeoPoint = GeoDistanceUtils.MUNICH_CENTER,
    val lastCameraZoom: Double = 11.6,
    val lastVisibleBounds: MapBounds = MapBounds(south = 48.07, west = 11.45, north = 48.22, east = 11.70),
    val searchQuery: String = "",
    val searchSuggestions: List<SearchLocation> = emptyList(),
    val isSearching: Boolean = false,
    val cameraMoveRequest: CameraMoveRequest? = null
)

data class CameraMoveRequest(
    val target: GeoPoint,
    val zoom: Double = 13.5,
    val requestId: Long = System.currentTimeMillis()
)

sealed interface RoutingStatus {
    data object Idle : RoutingStatus
    data object Loading : RoutingStatus
    data class Success(val route: GeographicScooterRoute) : RoutingStatus
    data class Error(val message: String) : RoutingStatus
}

data class RouteScreenUiState(
    val startLocation: SearchLocation? = null,
    val destinationLocation: SearchLocation? = null,
    val preferredOriginStation: TransitStation? = null,
    val preferredDestinationStation: TransitStation? = null,
    val startQuery: String = "",
    val destinationQuery: String = "",
    val startMode: RouteTransitChoice = RouteTransitChoice.SBAHN,
    val destinationMode: RouteTransitChoice = RouteTransitChoice.SBAHN,
    val activeField: RouteSearchField? = null,
    val fieldSuggestions: List<SearchLocation> = emptyList(),
    val routingStatus: RoutingStatus = RoutingStatus.Idle,
    val batteryFallbackSuggestion: BatteryRangeFallbackSuggestion? = null
)

enum class RouteSearchField {
    START,
    DESTINATION
}

class LetzteMeileViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = ScooterPreferencesRepository(application)
    private val database = LetzteMeileDatabase.getInstance(application)
    private val favoriteDao = database.favoritePlaceDao()
    private val customScooterDao = database.customScooterDao()
    private val offlineRegionDao = database.offlineRegionDao()
    private val overpassRepo = OverpassTransitRepository(
        context = application,
        tileDao = database.cachedOverpassTileDao(),
        offlineRegionDao = offlineRegionDao
    )
    private val bikeRoutingRepo = ScooterBikeRoutingRepository(application)
    private val router = GeographicScooterRouter(
        photonApi = NetworkModule.photonApi,
        overpassRepo = overpassRepo,
        bikeRoutingRepo = bikeRoutingRepo
    )

    val scooterProfile: StateFlow<ScooterProfile> = prefsRepo.scooterProfileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScooterProfile()
    )

    val appSettings: StateFlow<AppSettings> = prefsRepo.appSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val customScooters: StateFlow<List<CustomScooterEntity>> = customScooterDao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favorites: StateFlow<List<FavoritePlaceEntity>> = favoriteDao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val initialSetupCompleted: StateFlow<Boolean?> = prefsRepo.initialSetupCompletedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _currentRegion = MutableStateFlow(
        TransitRegionDetector.detectRegion(GeoDistanceUtils.MUNICH_CENTER)
    )
    val currentRegion: StateFlow<TransitRegionRecommendation> = _currentRegion.asStateFlow()

    private val _regionSwitchPrompt = MutableStateFlow<TransitRegionRecommendation?>(null)
    val regionSwitchPrompt: StateFlow<TransitRegionRecommendation?> = _regionSwitchPrompt.asStateFlow()

    private val _mapState = MutableStateFlow(MapScreenUiState())
    val mapState: StateFlow<MapScreenUiState> = _mapState.asStateFlow()

    private val _routeState = MutableStateFlow(RouteScreenUiState())
    val routeState: StateFlow<RouteScreenUiState> = _routeState.asStateFlow()

    private val _regionalPackageStatuses = MutableStateFlow<List<RegionalPackageStatus>>(emptyList())
    val regionalPackageStatuses: StateFlow<List<RegionalPackageStatus>> = _regionalPackageStatuses.asStateFlow()

    private val _isDownloadingAllRegions = MutableStateFlow(false)
    val isDownloadingAllRegions: StateFlow<Boolean> = _isDownloadingAllRegions.asStateFlow()

    private val regionDownloadJobs = mutableMapOf<String, Job>()
    private var downloadAllRegionsJob: Job? = null

    private var mapSearchJob: Job? = null
    private var routeSearchJob: Job? = null
    private var viewportLoadJob: Job? = null
    private var mapNearbyStopsJob: Job? = null
    private var mapScooterRoutingJob: Job? = null
    private var mapFallbackJob: Job? = null
    private var mapReverseGeocodeJob: Job? = null
    private var routeFallbackJob: Job? = null
    private var lastLoadedTileKey: String? = null
    private var inFlightLoadKey: String? = null
    private var lastLoadedCenter: GeoPoint? = null
    private var lastLoadedIncludesBuses: Boolean = false
    private var lastObservedTransitMode: MapTransitMode? = null
    private var lastPromptedRegionId: String? = null
    private var lastDetectedGpsRegionId: String? = null
    private var lastMapOsmStations: List<TransitStation> = emptyList()

    init {
        refreshRegionalPackageStatuses()

        viewModelScope.launch {
            prefsRepo.lastPromptedRegionIdFlow.collect { storedRegionId ->
                if (!storedRegionId.isNullOrBlank()) {
                    lastPromptedRegionId = storedRegionId
                }
            }
        }

        viewModelScope.launch {
            prefsRepo.mapDisplayPreferencesFlow.collect { displayPrefs ->
                val transitChanged = lastObservedTransitMode == null ||
                    displayPrefs.transitMode != lastObservedTransitMode
                lastObservedTransitMode = displayPrefs.transitMode
                _mapState.update { it.copy(displayPrefs = displayPrefs) }
                if (transitChanged) {
                    val state = _mapState.value
                    scheduleViewportTransitLoad(
                        center = state.lastCameraCenter,
                        zoom = state.lastCameraZoom,
                        transitMode = displayPrefs.transitMode,
                        forceReload = false
                    )
                    state.selectedTarget?.let { target ->
                        fetchNearbyOsmStationsForMapTarget(target.location, displayPrefs.transitMode)
                    }
                }
            }
        }

        // Keep battery assessments & nearest-station filters live-synced whenever profile or active map network changes
        viewModelScope.launch {
            combine(scooterProfile, prefsRepo.mapDisplayPreferencesFlow) { profile, prefs ->
                profile to prefs
            }.collect { (profile, prefs) ->
                recalculateMapNearestWithProfile(profile, prefs.transitMode)
                recalculateRouteBatteryWithProfile(profile)
            }
        }
    }

    // ---- Tab "Karte": Pure Single-Point Station Lookup & 4-State Network Toggle ----

    fun toggleDayNightMapLayout(currentlyDark: Boolean) {
        viewModelScope.launch {
            prefsRepo.setNightMapOverride(!currentlyDark)
        }
    }

    fun cycleTransitMode() {
        selectTransitMode(_mapState.value.displayPrefs.transitMode.next())
    }

    fun selectTransitMode(mode: MapTransitMode) {
        viewModelScope.launch {
            prefsRepo.setTransitMode(mode)
            val state = _mapState.value
            scheduleViewportTransitLoad(
                center = state.lastCameraCenter,
                zoom = state.lastCameraZoom,
                transitMode = mode,
                forceReload = false
            )
            state.selectedTarget?.let { target ->
                fetchNearbyOsmStationsForMapTarget(target.location, mode)
            }
        }
    }

    fun onMapCameraIdle(center: GeoPoint, zoom: Double) {
        _mapState.update { it.copy(lastCameraCenter = center, lastCameraZoom = zoom) }
        // IMPORTANT: Region switch detection is strictly decoupled from map panning (GPS-only)!
        scheduleViewportTransitLoad(
            center = center,
            zoom = zoom,
            transitMode = _mapState.value.displayPrefs.transitMode,
            forceReload = false
        )
    }

    fun onMapBoundsChanged(bounds: MapBounds) {
        _mapState.update { it.copy(lastVisibleBounds = bounds) }
    }

    // ---- Regional Transit Cache Refresh ----

    fun clearTransitCacheAndReload() {
        viewModelScope.launch {
            viewportLoadJob?.cancel()
            inFlightLoadKey = null
            lastLoadedTileKey = null
            lastLoadedCenter = null
            lastLoadedIncludesBuses = false
            lastMapOsmStations = emptyList()
            _mapState.update { it.copy(isLoadingViewportTransit = true, transitWarningHint = null) }

            val activePkg = OverpassTransitRepository.findPackageForPoint(_mapState.value.lastCameraCenter)
                ?: OverpassTransitRepository.getPackageForRegionId(
                    OverpassTransitRepository.regionIdForTransitApp(appSettings.value.preferredTransitApp)
                )
            val activePkgId = activePkg?.regionId ?: "MVV"
            val activePkgName = activePkg?.displayName ?: "ÖPNV-Daten"
            val refreshResult = overpassRepo.refreshMvvBundleNow(activePkgId)
            refreshResult.fold(
                onSuccess = { (routesCount, stationsCount) ->
                    val st = _mapState.value
                    val center = st.lastCameraCenter
                    val effectiveZoom = maxOf(st.lastCameraZoom, OverpassTransitRepository.MIN_RAIL_ZOOM)
                    val transitMode = st.displayPrefs.transitMode
                    val needRail = transitMode.includesSBahn || transitMode.includesRegioLong
                    val includeBuses = transitMode.includesBus
                    val busOnlyMode = transitMode == MapTransitMode.BUS_ONLY

                    val loadResult = overpassRepo.loadViewportTransit(
                        center = center,
                        zoom = effectiveZoom,
                        needRail = needRail,
                        includeBuses = includeBuses,
                        busOnlyMode = busOnlyMode
                    )
                    val overlay = loadResult.overlay
                    if (overlay != null) {
                        val isRegionalHit = OverpassTransitRepository.isRegionalBundleRegionId(loadResult.fromOfflineRegionId)
                        val emptyGeoJson = """{"type":"FeatureCollection","features":[]}"""
                        lastLoadedCenter = center
                        lastLoadedTileKey = if (isRegionalHit) {
                            val latCell = kotlin.math.floor(center.latitude / 0.02).toInt()
                            val lonCell = kotlin.math.floor(center.longitude / 0.03).toInt()
                            "reg_${activePkgId}_${latCell}_${lonCell}"
                        } else {
                            overpassRepo.tileKeyFor(center, needRail, includeBuses)
                        }
                        lastLoadedIncludesBuses = (includeBuses || isRegionalHit) &&
                            overlay.busLinesGeoJson != emptyGeoJson &&
                            loadResult.networkWarningHint == null
                        applyLoadedViewportOverlay(
                            overlay = overlay,
                            warningHint = loadResult.networkWarningHint
                        )
                    } else {
                        _mapState.update { it.copy(isLoadingViewportTransit = false) }
                    }

                    val msg = "$activePkgName neu geladen ($routesCount Linien, $stationsCount Haltestellen)."
                    _snackbarMessages.tryEmit(msg)
                    refreshRegionalPackageStatuses()
                },
                onFailure = { err ->
                    val errMsg = err.localizedMessage
                        ?: "ÖPNV-Daten konnten nicht neu geladen werden."
                    _mapState.update {
                        it.copy(
                            isLoadingViewportTransit = false,
                            transitWarningHint = errMsg
                        )
                    }
                    _snackbarMessages.tryEmit(errMsg)
                    refreshRegionalPackageStatuses()
                }
            )
        }
    }

    // ---- Manual Offline Transit Network Download Manager (all 19 DACH regions) ----

    private var remoteSizeProbeJob: Job? = null

    fun refreshRegionalPackageStatuses() {
        viewModelScope.launch(Dispatchers.IO) {
            val diskSnapshots = overpassRepo.getAllRegionalPackageSnapshots()
            _regionalPackageStatuses.update { current ->
                val currentById = current.associateBy { it.regionId }
                diskSnapshots.map { snap ->
                    val existing = currentById[snap.regionId]
                    val mergedRemoteSize = snap.remoteSizeBytes ?: existing?.remoteSizeBytes
                    if (existing != null && existing.isDownloading) {
                        existing.copy(
                            isDownloaded = snap.isDownloaded,
                            sizeBytes = snap.sizeBytes,
                            remoteSizeBytes = mergedRemoteSize
                        )
                    } else {
                        snap.copy(
                            remoteSizeBytes = mergedRemoteSize,
                            errorMessage = existing?.errorMessage
                        )
                    }
                }
            }
        }

        if (remoteSizeProbeJob?.isActive != true) {
            remoteSizeProbeJob = viewModelScope.launch(Dispatchers.IO) {
                val remoteSizes = overpassRepo.fetchRemotePackageSizes()
                if (remoteSizes.isNotEmpty()) {
                    _regionalPackageStatuses.update { list ->
                        list.map { item ->
                            val probed = remoteSizes[item.regionId]
                            if (probed != null && probed > 0L) {
                                item.copy(remoteSizeBytes = probed)
                            } else {
                                item
                            }
                        }
                    }
                }
            }
        }
    }

    fun downloadSingleRegionalPackage(regionId: String, forceRefresh: Boolean = true) {
        val normalized = regionId.uppercase(Locale.US)
        val pkg = OverpassTransitRepository.getPackageForRegionId(normalized) ?: return
        regionDownloadJobs[normalized]?.cancel()
        regionDownloadJobs[normalized] = viewModelScope.launch {
            runSingleRegionDownload(pkg.regionId, pkg.displayName, forceRefresh = forceRefresh, showSnackbar = true)
        }
    }

    fun deleteSingleRegionalPackage(regionId: String) {
        val normalized = regionId.uppercase(Locale.US)
        val pkg = OverpassTransitRepository.getPackageForRegionId(normalized) ?: return
        regionDownloadJobs[normalized]?.cancel()
        regionDownloadJobs.remove(normalized)
        viewModelScope.launch {
            overpassRepo.deleteLocalRegionalPackage(normalized)
            updateRegionStatus(normalized) {
                it.copy(
                    isDownloaded = false,
                    sizeBytes = 0L,
                    isDownloading = false,
                    downloadedBytes = 0L,
                    totalBytes = -1L,
                    errorMessage = null
                )
            }
            _snackbarMessages.tryEmit("${pkg.displayName} lokal gelöscht.")
        }
    }

    fun downloadAllRegionalPackages() {
        if (_isDownloadingAllRegions.value) return
        downloadAllRegionsJob?.cancel()
        downloadAllRegionsJob = viewModelScope.launch {
            _isDownloadingAllRegions.value = true
            try {
                val missing = overpassRepo.getAllRegionalPackageSnapshots()
                    .filter { !it.isDownloaded }
                if (missing.isEmpty()) {
                    val totalCount = OverpassTransitRepository.REGIONAL_PACKAGES.size
                    _snackbarMessages.tryEmit("Alle $totalCount DACH-Netze sind bereits lokal gespeichert.")
                    return@launch
                }
                var succeeded = 0
                var failed = 0
                for (item in missing) {
                    val ok = runSingleRegionDownload(
                        regionId = item.regionId,
                        displayName = item.displayName,
                        forceRefresh = false,
                        showSnackbar = false
                    )
                    if (ok) succeeded++ else failed++
                }
                val summary = if (failed == 0) {
                    "$succeeded Netze erfolgreich geladen."
                } else {
                    "$succeeded geladen, $failed fehlgeschlagen (Details siehe Liste)."
                }
                _snackbarMessages.tryEmit(summary)
            } finally {
                _isDownloadingAllRegions.value = false
                refreshRegionalPackageStatuses()
            }
        }
    }

    fun cancelDownloadAllRegionalPackages() {
        downloadAllRegionsJob?.cancel()
        downloadAllRegionsJob = null
        regionDownloadJobs.values.forEach { it.cancel() }
        regionDownloadJobs.clear()
        _isDownloadingAllRegions.value = false
        _regionalPackageStatuses.update { list ->
            list.map { it.copy(isDownloading = false, downloadedBytes = 0L, totalBytes = -1L) }
        }
        refreshRegionalPackageStatuses()
    }

    private suspend fun runSingleRegionDownload(
        regionId: String,
        displayName: String,
        forceRefresh: Boolean,
        showSnackbar: Boolean
    ): Boolean {
        updateRegionStatus(regionId) {
            it.copy(
                isDownloading = true,
                downloadedBytes = 0L,
                totalBytes = -1L,
                errorMessage = null
            )
        }
        return try {
            val result = overpassRepo.downloadPackageToDiskOnly(
                regionId = regionId,
                forceRefresh = forceRefresh,
                onProgress = { downloaded, total ->
                    updateRegionStatus(regionId) {
                        it.copy(
                            isDownloading = true,
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    }
                }
            )
            result.fold(
                onSuccess = { bytes ->
                    updateRegionStatus(regionId) {
                        it.copy(
                            isDownloaded = true,
                            sizeBytes = bytes,
                            isDownloading = false,
                            downloadedBytes = bytes,
                            totalBytes = bytes,
                            errorMessage = null
                        )
                    }
                    // If the map camera is currently inside this region, refresh the overlay cleanly
                    val currentMapPkg = OverpassTransitRepository.findPackageForPoint(_mapState.value.lastCameraCenter)
                    if (currentMapPkg?.regionId == regionId) {
                        lastLoadedTileKey = null
                        scheduleViewportTransitLoad(
                            center = _mapState.value.lastCameraCenter,
                            zoom = _mapState.value.lastCameraZoom,
                            transitMode = _mapState.value.displayPrefs.transitMode,
                            forceReload = true
                        )
                    }
                    if (showSnackbar) {
                        val mb = bytes / (1024.0 * 1024.0)
                        _snackbarMessages.tryEmit(
                            String.format(Locale.GERMAN, "%s geladen (%.1f MB).", displayName, mb)
                        )
                    }
                    true
                },
                onFailure = { err ->
                    val diag = err.message?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                    updateRegionStatus(regionId) {
                        it.copy(
                            isDownloading = false,
                            downloadedBytes = 0L,
                            totalBytes = -1L,
                            errorMessage = diag
                        )
                    }
                    if (showSnackbar) {
                        _snackbarMessages.tryEmit("Fehler ($displayName): $diag")
                    }
                    false
                }
            )
        } catch (ce: CancellationException) {
            updateRegionStatus(regionId) {
                it.copy(isDownloading = false, downloadedBytes = 0L, totalBytes = -1L)
            }
            throw ce
        }
    }

    private fun updateRegionStatus(
        regionId: String,
        transform: (RegionalPackageStatus) -> RegionalPackageStatus
    ) {
        _regionalPackageStatuses.update { list ->
            val base = if (list.isEmpty()) overpassRepo.getAllRegionalPackageSnapshots() else list
            base.map { item ->
                if (item.regionId.equals(regionId, ignoreCase = true)) transform(item) else item
            }
        }
    }

    fun dismissTransitWarningHint() {
        _mapState.update { it.copy(transitWarningHint = null) }
    }

    private fun scheduleViewportTransitLoad(
        center: GeoPoint,
        zoom: Double,
        transitMode: MapTransitMode,
        forceReload: Boolean
    ) {
        if (zoom < OverpassTransitRepository.MIN_RAIL_ZOOM) return

        val matchedRegionalPkg = OverpassTransitRepository.findPackageForPoint(center)
        val isInRegionalPkg = matchedRegionalPkg != null
        val needRail = transitMode.includesSBahn || transitMode.includesRegioLong
        val includeBuses = transitMode.includesBus
        val busOnlyMode = transitMode == MapTransitMode.BUS_ONLY
        val fetchBuses = includeBuses && (isInRegionalPkg || zoom >= OverpassTransitRepository.minBusZoomFor(busOnlyMode))
        if (!needRail && !fetchBuses) return

        val tileKey = overpassRepo.tileKeyFor(center, needRail, fetchBuses)

        // Fast bounding-box check against regional local bundles BEFORE debounce or progress bar
        val effectiveKey = if (matchedRegionalPkg != null) {
            val latCell = kotlin.math.floor(center.latitude / 0.02).toInt()
            val lonCell = kotlin.math.floor(center.longitude / 0.03).toInt()
            "reg_${matchedRegionalPkg.regionId}_${latCell}_${lonCell}"
        } else {
            tileKey
        }

        // 0. Avoid cancelling & duplicating an identical in-flight load (e.g. startup init + initial onMapCameraIdle)
        if (!forceReload && viewportLoadJob?.isActive == true && effectiveKey == inFlightLoadKey) {
            return
        }

        // 1. Buffered anti-jitter guard: ignore small map pans & instant mode switches inside already-loaded area!
        val prevCenter = lastLoadedCenter
        if (!forceReload && prevCenter != null) {
            val movedKm = GeoDistanceUtils.haversineKm(prevCenter, center)
            val busRequirementSatisfied = !fetchBuses || lastLoadedIncludesBuses
            val bufferRadiusKm = if (fetchBuses) 1.1 else 3.4
            if (busRequirementSatisfied && (effectiveKey == lastLoadedTileKey || movedKm < bufferRadiusKm)) {
                return
            }
        }

        // 2. Instant zero-I/O RAM cache hit: apply immediately with zero debounce & zero loading indicator!
        val emptyGeoJson = """{"type":"FeatureCollection","features":[]}"""
        if (!forceReload) {
            val ramHit = overpassRepo.getRamCachedOverlay(
                center = center,
                needsBuses = fetchBuses,
                requiredOfflineRegionId = matchedRegionalPkg?.localOverlayRegionId
            )
            if (ramHit?.overlay != null) {
                viewportLoadJob?.cancel()
                inFlightLoadKey = null
                val isMvvHit = OverpassTransitRepository.isRegionalBundleRegionId(ramHit.fromOfflineRegionId)
                lastLoadedTileKey = if (isMvvHit) {
                    effectiveKey
                } else if (ramHit.fromOfflineRegionId != null) {
                    "offline_${ramHit.fromOfflineRegionId}_$tileKey"
                } else {
                    tileKey
                }
                lastLoadedCenter = center
                lastLoadedIncludesBuses = (fetchBuses || isMvvHit || lastLoadedIncludesBuses) &&
                    ramHit.overlay.busLinesGeoJson != emptyGeoJson
                applyLoadedViewportOverlay(
                    overlay = ramHit.overlay,
                    warningHint = null
                )
                return
            }
        }

        viewportLoadJob?.cancel()
        inFlightLoadKey = effectiveKey
        viewportLoadJob = viewModelScope.launch {
            try {
                // 3. PRIORITY: If inside a regional package (already downloaded) or saved Offline Region, load IMMEDIATELY!
                val offlineMatch = overpassRepo.loadCoveringOfflineRegionOverlay(center, needsBuses = fetchBuses)
                if (offlineMatch != null) {
                    val isMvvHit = OverpassTransitRepository.isRegionalBundleRegionId(offlineMatch.first)
                    lastLoadedTileKey = if (isMvvHit) effectiveKey else "offline_${offlineMatch.first}_$tileKey"
                    lastLoadedCenter = center
                    lastLoadedIncludesBuses = (fetchBuses || isMvvHit) &&
                        offlineMatch.second.busLinesGeoJson != emptyGeoJson
                    applyLoadedViewportOverlay(
                        overlay = offlineMatch.second,
                        warningHint = null
                    )
                    return@launch
                }

                // 4. Check if a local bundle or Room cache tile is already available (no loading bar needed)
                val hasLocalTile = !forceReload && overpassRepo.hasInstantLocalCache(center, needRail, fetchBuses)
                if (!hasLocalTile) {
                    if (!forceReload) delay(550)
                    _mapState.update { it.copy(isLoadingViewportTransit = true) }
                }

                val result = overpassRepo.loadViewportTransit(
                    center = center,
                    zoom = zoom,
                    needRail = needRail,
                    includeBuses = includeBuses,
                    busOnlyMode = busOnlyMode
                )
                val overlay = result.overlay
                if (overlay != null) {
                    val isMvvHit = OverpassTransitRepository.isRegionalBundleRegionId(result.fromOfflineRegionId)
                    lastLoadedTileKey = when {
                        isMvvHit -> effectiveKey
                        result.fromOfflineRegionId != null -> "offline_${result.fromOfflineRegionId}_$tileKey"
                        else -> tileKey
                    }
                    lastLoadedCenter = center
                    lastLoadedIncludesBuses = (fetchBuses || isMvvHit) &&
                        overlay.busLinesGeoJson != emptyGeoJson &&
                        result.networkWarningHint == null
                    applyLoadedViewportOverlay(
                        overlay = overlay,
                        warningHint = result.networkWarningHint
                    )
                } else {
                    _mapState.update {
                        it.copy(
                            isLoadingViewportTransit = false,
                            transitWarningHint = result.networkWarningHint ?: it.transitWarningHint
                        )
                    }
                }
            } finally {
                if (inFlightLoadKey == effectiveKey) {
                    inFlightLoadKey = null
                }
            }
        }
    }

    private fun applyLoadedViewportOverlay(
        overlay: DynamicTransitOverlay,
        warningHint: String?
    ) {
        _mapState.update { state ->
            val updatedNearest = state.selectedTarget?.let { target ->
                computeNearestStationsForActiveNetwork(
                    target = target.location,
                    discoveredStations = overlay.discoveredStations + lastMapOsmStations,
                    transitMode = state.displayPrefs.transitMode,
                    profile = scooterProfile.value
                )
            } ?: state.nearestStationsToTarget
            val updatedBattery = updatedNearest.firstOrNull()?.let { nearest ->
                BatteryRangeCalculator.assessRoundTrip(
                    firstMileKm = nearest.estimatedScooterKm,
                    lastMileKm = 0.0,
                    profile = scooterProfile.value
                )
            } ?: state.targetBatteryQuickCheck
            state.copy(
                dynamicOverlay = overlay,
                nearestStationsToTarget = updatedNearest,
                targetBatteryQuickCheck = if (state.selectedTarget != null) updatedBattery else null,
                isLoadingViewportTransit = false,
                transitWarningHint = warningHint
            )
        }
    }

    // ---- Initial Onboarding, Settings & Strictly GPS-Based Auto-Region Detection ----

    fun completeInitialRegionSetup(selectedApp: PreferredTransitApp) {
        val recommendation = TransitRegionDetector.recommendationForApp(selectedApp)
        val regionId = OverpassTransitRepository.regionIdForTransitApp(selectedApp)
        _currentRegion.value = recommendation
        lastPromptedRegionId = recommendation.regionKey
        _regionSwitchPrompt.value = null

        viewModelScope.launch {
            prefsRepo.completeInitialSetup(
                preferredApp = selectedApp,
                initialRegionId = recommendation.regionKey
            )
            downloadRegionalPackageIfConfigured(
                regionId = regionId,
                showConfirmation = true,
                centerCameraIfOutside = true
            )
        }
    }

    fun setPreferredMapsApp(app: PreferredMapsApp) {
        viewModelScope.launch { prefsRepo.setPreferredMapsApp(app) }
    }

    fun setPreferredTransitApp(app: PreferredTransitApp) {
        val recommendation = TransitRegionDetector.recommendationForApp(app)
        val regionId = OverpassTransitRepository.regionIdForTransitApp(app)
        _currentRegion.value = recommendation
        _regionSwitchPrompt.value = null
        viewModelScope.launch {
            prefsRepo.setPreferredTransitApp(app)
            prefsRepo.setLastPromptedRegionId(recommendation.regionKey)
            lastPromptedRegionId = recommendation.regionKey
            downloadRegionalPackageIfConfigured(
                regionId = regionId,
                showConfirmation = true,
                centerCameraIfOutside = true
            )
        }
    }

    fun setAutoRegionDetection(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoRegionDetection(enabled)
            if (!enabled) {
                _regionSwitchPrompt.value = null
            }
        }
    }

    fun acceptRegionSwitchPrompt() {
        val prompt = _regionSwitchPrompt.value ?: return
        _regionSwitchPrompt.value = null
        lastPromptedRegionId = prompt.regionKey
        val regionId = OverpassTransitRepository.regionIdForDetectedRegionKey(prompt.regionKey)
        viewModelScope.launch {
            prefsRepo.setPreferredTransitApp(prompt.recommendedApp)
            prefsRepo.setLastPromptedRegionId(prompt.regionKey)
            downloadRegionalPackageIfConfigured(regionId = regionId, showConfirmation = true)
        }
    }

    fun dismissRegionSwitchPrompt() {
        val dismissedKey = _regionSwitchPrompt.value?.regionKey
        _regionSwitchPrompt.value = null
        if (dismissedKey != null) {
            lastPromptedRegionId = dismissedKey
            viewModelScope.launch {
                prefsRepo.setLastPromptedRegionId(dismissedKey)
            }
        }
    }

    /**
     * Evaluates the transit region EXCLUSIVELY from a real GPS fix.
     * Remembers [lastPromptedRegionId] so the switch banner is never shown again until GPS
     * physically enters a different region.
     */
    private fun evaluateGpsRegion(gpsPoint: GeoPoint) {
        val detected = TransitRegionDetector.detectRegion(gpsPoint)
        val previousGpsRegion = lastDetectedGpsRegionId
        lastDetectedGpsRegionId = detected.regionKey
        _currentRegion.value = detected

        // Do not show switch prompt before one-time initial onboarding is finished
        if (initialSetupCompleted.value != true) return

        val settings = appSettings.value
        if (!settings.autoRegionDetection) return

        // Already using an app that fits the current GPS region -> remember it & stay silent
        if (TransitRegionDetector.appMatchesRegion(settings.preferredTransitApp, detected.regionKey)) {
            if (lastPromptedRegionId != detected.regionKey) {
                lastPromptedRegionId = detected.regionKey
                viewModelScope.launch { prefsRepo.setLastPromptedRegionId(detected.regionKey) }
            }
            return
        }

        // Only show banner if GPS actually entered a region different from the last prompted/dismissed one
        if (detected.regionKey == lastPromptedRegionId) return
        if (previousGpsRegion == detected.regionKey) return

        lastPromptedRegionId = detected.regionKey
        _regionSwitchPrompt.value = detected
    }

    private suspend fun downloadRegionalPackageIfConfigured(
        regionId: String,
        showConfirmation: Boolean,
        centerCameraIfOutside: Boolean = false
    ) {
        val pkg = OverpassTransitRepository.getPackageForRegionId(regionId) ?: return
        val wasCachedLocally = overpassRepo.hasLocalPackageFor(pkg)
        _mapState.update { it.copy(isLoadingViewportTransit = true, transitWarningHint = null) }
        val result = overpassRepo.downloadRegionalPackageIfAvailable(regionId = regionId, forceRefresh = false)
            ?: run {
                _mapState.update { it.copy(isLoadingViewportTransit = false) }
                return
            }

        result.fold(
            onSuccess = { (routesCount, stationsCount) ->
                val st = _mapState.value
                val shouldRecenter = centerCameraIfOutside && !pkg.bounds.contains(st.lastCameraCenter)
                val targetCenter = if (shouldRecenter) pkg.bounds.center() else st.lastCameraCenter
                val targetZoom = if (shouldRecenter) 11.4 else maxOf(st.lastCameraZoom, OverpassTransitRepository.MIN_RAIL_ZOOM)

                if (shouldRecenter) {
                    _mapState.update {
                        it.copy(
                            lastCameraCenter = targetCenter,
                            lastCameraZoom = targetZoom,
                            cameraMoveRequest = CameraMoveRequest(targetCenter, targetZoom)
                        )
                    }
                }

                lastLoadedTileKey = null
                lastLoadedCenter = null
                lastLoadedIncludesBuses = false
                scheduleViewportTransitLoad(
                    center = targetCenter,
                    zoom = targetZoom,
                    transitMode = st.displayPrefs.transitMode,
                    forceReload = true
                )
                if (showConfirmation) {
                    val verb = if (wasCachedLocally) "aktiviert (Offline-Cache)" else "geladen"
                    _snackbarMessages.tryEmit(
                        "${pkg.displayName} $verb ($routesCount Linien, $stationsCount Haltestellen)."
                    )
                }
                updateRegionStatus(pkg.regionId) { it.copy(errorMessage = null) }
                refreshRegionalPackageStatuses()
            },
            onFailure = { err ->
                val diag = err.message?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                val msg = "Fehler (${pkg.displayName}): $diag"
                _mapState.update { it.copy(isLoadingViewportTransit = false) }
                updateRegionStatus(pkg.regionId) { it.copy(errorMessage = diag) }
                refreshRegionalPackageStatuses()
                if (showConfirmation) {
                    _snackbarMessages.tryEmit(msg)
                }
            }
        )
    }

    fun updateUserLocation(
        point: GeoPoint,
        moveCamera: Boolean = false,
        isRealGps: Boolean = true,
        selectAsMapTarget: Boolean = false
    ) {
        if (isRealGps) {
            evaluateGpsRegion(point)
        }
        _mapState.update { state ->
            state.copy(
                userLocation = if (isRealGps) point else state.userLocation,
                lastCameraCenter = if (moveCamera) point else state.lastCameraCenter,
                lastCameraZoom = if (moveCamera) 13.8 else state.lastCameraZoom,
                cameraMoveRequest = if (moveCamera && !selectAsMapTarget) {
                    CameraMoveRequest(point, 13.8)
                } else {
                    state.cameraMoveRequest
                }
            )
        }
        if (selectAsMapTarget) {
            mapReverseGeocodeJob?.cancel()
            val gpsLocation = SearchLocation(
                id = "gps_${String.format(Locale.US, "%.5f_%.5f", point.latitude, point.longitude)}",
                name = if (isRealGps) "Mein Standort" else "Standard-Standort",
                subtitle = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude),
                location = point
            )
            selectMapTarget(
                location = gpsLocation,
                animateCamera = moveCamera,
                defaultRole = MapPointRole.START
            )
            mapReverseGeocodeJob = viewModelScope.launch {
                val resolved = resolvePointLocation(point)
                val updatedLoc = resolved.copy(
                    id = gpsLocation.id,
                    name = if (isRealGps && resolved.name.startsWith("Kartenpunkt")) {
                        "Mein Standort"
                    } else {
                        resolved.name
                    },
                    subtitle = if (isRealGps) "Mein Standort · ${resolved.subtitle}" else resolved.subtitle
                )
                _mapState.update { state ->
                    val updatedSelected = if (state.selectedTarget?.id == gpsLocation.id) updatedLoc else state.selectedTarget
                    val updatedQuery = if (state.selectedTarget?.id == gpsLocation.id) updatedLoc.name else state.searchQuery
                    val updatedQuickStart = if (state.quickRouteStart?.id == gpsLocation.id) updatedLoc else state.quickRouteStart
                    val updatedQuickDest = if (state.quickRouteDestination?.id == gpsLocation.id) updatedLoc else state.quickRouteDestination
                    state.copy(
                        selectedTarget = updatedSelected,
                        searchQuery = updatedQuery,
                        quickRouteStart = updatedQuickStart,
                        quickRouteDestination = updatedQuickDest
                    )
                }
            }
        } else if (moveCamera) {
            // Immediately check offline bounding boxes on GPS jump so returning to a saved region uses local cache with zero reload!
            scheduleViewportTransitLoad(
                center = point,
                zoom = 13.8,
                transitMode = _mapState.value.displayPrefs.transitMode,
                forceReload = false
            )
        }
    }

    fun onMapSearchQueryChanged(query: String) {
        _mapState.update { it.copy(searchQuery = query) }
        mapSearchJob?.cancel()
        if (query.trim().length < 2) {
            _mapState.update { it.copy(searchSuggestions = emptyList(), isSearching = false) }
            return
        }
        mapSearchJob = viewModelScope.launch {
            _mapState.update { it.copy(isSearching = true) }
            delay(250)
            val hits = router.searchPlaces(query, biasCenter = _mapState.value.lastCameraCenter)
            _mapState.update { it.copy(searchSuggestions = hits, isSearching = false) }
        }
    }

    fun clearMapSearch() {
        _mapState.update {
            it.copy(
                searchQuery = "",
                searchSuggestions = emptyList(),
                isSearching = false
            )
        }
    }

    fun selectMapTarget(
        location: SearchLocation,
        animateCamera: Boolean = true,
        defaultRole: MapPointRole? = null,
        triggerStreetRoutes: Boolean = true
    ) {
        val profile = scooterProfile.value
        val state = _mapState.value
        val resolvedRole = when {
            state.quickRouteStart != null && state.quickRouteDestination == null -> MapPointRole.DESTINATION
            state.quickRouteDestination != null && state.quickRouteStart == null -> MapPointRole.START
            else -> defaultRole ?: MapPointRole.DESTINATION
        }
        val nearest = computeNearestStationsForActiveNetwork(
            target = location.location,
            discoveredStations = state.dynamicOverlay.discoveredStations + lastMapOsmStations,
            transitMode = state.displayPrefs.transitMode,
            profile = profile
        )
        // Single-point scooter battery check: Hin- & Rückweg between chosen point and nearest station
        val nearestLegKm = nearest.firstOrNull()?.estimatedScooterKm ?: 0.0
        val batteryCheck = if (nearest.isNotEmpty()) {
            BatteryRangeCalculator.assessRoundTrip(
                firstMileKm = nearestLegKm,
                lastMileKm = 0.0,
                profile = profile
            )
        } else {
            null
        }

        _mapState.update {
            it.copy(
                selectedTarget = location,
                selectedTargetRole = resolvedRole,
                nearestStationsToTarget = nearest,
                selectedStationIndex = 0,
                targetBatteryQuickCheck = batteryCheck,
                batteryFallbackSuggestion = null,
                searchQuery = location.name,
                searchSuggestions = emptyList(),
                isSearching = false,
                cameraMoveRequest = if (animateCamera) CameraMoveRequest(location.location, 13.8) else it.cameraMoveRequest
            )
        }

        scheduleViewportTransitLoad(
            center = location.location,
            zoom = 13.6,
            transitMode = state.displayPrefs.transitMode,
            forceReload = false
        )
        // Query real stations around the tapped point (strictly filtered by active transitMode!)
        fetchNearbyOsmStationsForMapTarget(location.location, state.displayPrefs.transitMode)
        if (triggerStreetRoutes) {
            enrichMapNearestWithStreetRoutes(location.location, nearest, profile)
        }
        evaluateMapBatteryFallback(
            target = location,
            nearest = nearest.firstOrNull(),
            batteryCheck = batteryCheck,
            transitMode = state.displayPrefs.transitMode,
            profile = profile
        )
    }

    private fun fetchNearbyOsmStationsForMapTarget(point: GeoPoint, transitMode: MapTransitMode) {
        mapNearbyStopsJob?.cancel()
        mapNearbyStopsJob = viewModelScope.launch {
            val fetched = overpassRepo.queryNearbyStationsForMapMode(point, transitMode)
            if (fetched.isEmpty()) {
                val st = _mapState.value
                st.selectedTarget?.let { target ->
                    val activeOption = st.nearestStationsToTarget
                        .take(3)
                        .getOrNull(st.selectedStationIndex)
                        ?: st.nearestStationsToTarget.firstOrNull()
                    evaluateMapBatteryFallback(
                        target = target,
                        nearest = activeOption,
                        batteryCheck = st.targetBatteryQuickCheck,
                        transitMode = st.displayPrefs.transitMode,
                        profile = scooterProfile.value
                    )
                }
                return@launch
            }
            lastMapOsmStations = (lastMapOsmStations + fetched)
                .distinctBy { "${it.type}_${it.name.lowercase(Locale.GERMAN)}" }
                .takeLast(240)
            recalculateMapNearestWithProfile(scooterProfile.value, _mapState.value.displayPrefs.transitMode, refreshRoutes = true)
        }
    }

    /**
     * Enriches top 3 nearest stations on Tab "Karte" with real OSM bike/scooter street polylines
     * and distances (falling back automatically to 1.25x straight-line estimate when offline).
     */
    private fun enrichMapNearestWithStreetRoutes(
        targetPoint: GeoPoint,
        candidates: List<NearbyStationResult>,
        profile: ScooterProfile
    ) {
        mapScooterRoutingJob?.cancel()
        if (candidates.isEmpty()) return

        mapScooterRoutingJob = viewModelScope.launch {
            val topCandidates = candidates.take(3)
            val routedTop = coroutineScope {
                topCandidates.map { item ->
                    async {
                        val leg = bikeRoutingRepo.routeScooterLeg(
                            from = targetPoint,
                            to = item.station.location,
                            profile = profile
                        )
                        item.copy(
                            estimatedScooterKm = leg.distanceKm,
                            estimatedScooterMinutes = leg.durationMinutes,
                            routePath = leg.path,
                            isOfflineEstimate = leg.isOfflineEstimate
                        )
                    }
                }.awaitAll()
            }.sortedBy { it.estimatedScooterKm }

            val remaining = candidates.drop(3)
            val combined = routedTop + remaining

            val currentState = _mapState.value
            val currentTarget = currentState.selectedTarget ?: return@launch
            if (GeoDistanceUtils.haversineKm(currentTarget.location, targetPoint) > 0.02) return@launch

            val prevSelectedId = currentState.nearestStationsToTarget
                .getOrNull(currentState.selectedStationIndex)?.station?.id
            val preservedIdx = if (currentState.selectedStationIndex > 0 && prevSelectedId != null) {
                combined.take(3).indexOfFirst { it.station.id == prevSelectedId }.coerceAtLeast(0)
            } else {
                0
            }
            val activeOption = combined.getOrNull(preservedIdx) ?: combined.firstOrNull()
            val nearestLegKm = activeOption?.estimatedScooterKm ?: 0.0
            val updatedBattery = BatteryRangeCalculator.assessRoundTrip(
                firstMileKm = nearestLegKm,
                lastMileKm = 0.0,
                profile = scooterProfile.value
            )

            _mapState.update { state ->
                val isCurrentQuickStart = state.quickRouteStart != null &&
                    GeoDistanceUtils.haversineKm(state.quickRouteStart.location, currentTarget.location) < 0.025
                val isCurrentQuickDest = state.quickRouteDestination != null &&
                    GeoDistanceUtils.haversineKm(state.quickRouteDestination.location, currentTarget.location) < 0.025
                state.copy(
                    nearestStationsToTarget = combined,
                    selectedStationIndex = preservedIdx,
                    quickRouteStartStation = if (isCurrentQuickStart && activeOption != null) {
                        activeOption
                    } else {
                        state.quickRouteStartStation
                    },
                    quickRouteDestinationStation = if (isCurrentQuickDest && activeOption != null) {
                        activeOption
                    } else {
                        state.quickRouteDestinationStation
                    },
                    targetBatteryQuickCheck = updatedBattery
                )
            }
            evaluateMapBatteryFallback(
                target = currentTarget,
                nearest = activeOption,
                batteryCheck = updatedBattery,
                transitMode = _mapState.value.displayPrefs.transitMode,
                profile = scooterProfile.value
            )
        }
    }

    fun onMapPointPicked(point: GeoPoint) {
        mapReverseGeocodeJob?.cancel()
        mapScooterRoutingJob?.cancel()
        val customTarget = buildLocationFromPoint(point)
        val state = _mapState.value
        val pickedDefaultRole = when {
            state.quickRouteStart != null && state.quickRouteDestination == null -> MapPointRole.DESTINATION
            state.quickRouteDestination != null && state.quickRouteStart == null -> MapPointRole.START
            else -> MapPointRole.START
        }
        // Show pin immediately at tapped point; defer street routing briefly until async Snap-to-Road finishes
        selectMapTarget(
            location = customTarget,
            animateCamera = false,
            defaultRole = pickedDefaultRole,
            triggerStreetRoutes = false
        )
        mapReverseGeocodeJob = viewModelScope.launch {
            // 1. Snap tapped point onto nearest bikeable/drivable road within 130m (silent fallback if offline/too far)
            val snapped = withTimeoutOrNull(2600L) {
                bikeRoutingRepo.snapToNearestRoad(
                    point = point,
                    maxRadiusMeters = ScooterBikeRoutingRepository.MAX_SNAP_RADIUS_METERS
                )
            }
            if (_mapState.value.selectedTarget?.id != customTarget.id) return@launch

            val effectivePoint = snapped?.point ?: point
            if (snapped != null) {
                val snappedPlaceholder = buildLocationFromPoint(
                    point = effectivePoint,
                    preservedId = customTarget.id,
                    preferredName = snapped.roadName
                )
                _mapState.update { st ->
                    val updatedSelected = if (st.selectedTarget?.id == customTarget.id) snappedPlaceholder else st.selectedTarget
                    val updatedQuery = if (st.selectedTarget?.id == customTarget.id) snappedPlaceholder.name else st.searchQuery
                    val updatedQuickStart = if (st.quickRouteStart?.id == customTarget.id) snappedPlaceholder else st.quickRouteStart
                    val updatedQuickDest = if (st.quickRouteDestination?.id == customTarget.id) snappedPlaceholder else st.quickRouteDestination
                    st.copy(
                        selectedTarget = updatedSelected,
                        searchQuery = updatedQuery,
                        quickRouteStart = updatedQuickStart,
                        quickRouteDestination = updatedQuickDest
                    )
                }
                // Recompute station distances & real street routes starting seamlessly from the snapped road coordinate
                recalculateMapNearestWithProfile(
                    profile = scooterProfile.value,
                    transitMode = _mapState.value.displayPrefs.transitMode,
                    refreshRoutes = true,
                    discardCachedPaths = true
                )
            } else {
                // Offline, timeout, or > 130m away: keep original tapped point cleanly and run street routing from it
                enrichMapNearestWithStreetRoutes(
                    targetPoint = point,
                    candidates = _mapState.value.nearestStationsToTarget,
                    profile = scooterProfile.value
                )
            }

            // 2. Reverse-geocode the final (snapped or original) coordinate so address & coordinates match everywhere
            val resolved = resolvePointLocation(
                point = effectivePoint,
                preservedId = customTarget.id,
                fallbackRoadName = snapped?.roadName
            )
            _mapState.update { st ->
                val updatedSelected = if (st.selectedTarget?.id == customTarget.id) resolved else st.selectedTarget
                val updatedQuery = if (st.selectedTarget?.id == customTarget.id) resolved.name else st.searchQuery
                val updatedQuickStart = if (st.quickRouteStart?.id == customTarget.id) resolved else st.quickRouteStart
                val updatedQuickDest = if (st.quickRouteDestination?.id == customTarget.id) resolved else st.quickRouteDestination
                st.copy(
                    selectedTarget = updatedSelected,
                    searchQuery = updatedQuery,
                    quickRouteStart = updatedQuickStart,
                    quickRouteDestination = updatedQuickDest
                )
            }
        }
    }

    fun selectNearestStationOption(index: Int) {
        val state = _mapState.value
        val options = state.nearestStationsToTarget.take(3)
        if (options.isEmpty()) return
        val clamped = index.coerceIn(0, options.lastIndex)
        val chosen = options[clamped]
        val updatedBattery = BatteryRangeCalculator.assessRoundTrip(
            firstMileKm = chosen.estimatedScooterKm,
            lastMileKm = 0.0,
            profile = scooterProfile.value
        )
        _mapState.update { current ->
            val isCurrentQuickStart = current.quickRouteStart != null &&
                current.selectedTarget != null &&
                GeoDistanceUtils.haversineKm(
                    current.quickRouteStart.location,
                    current.selectedTarget.location
                ) < 0.025
            val isCurrentQuickDest = current.quickRouteDestination != null &&
                current.selectedTarget != null &&
                GeoDistanceUtils.haversineKm(
                    current.quickRouteDestination.location,
                    current.selectedTarget.location
                ) < 0.025
            current.copy(
                selectedStationIndex = clamped,
                quickRouteStartStation = if (isCurrentQuickStart) chosen else current.quickRouteStartStation,
                quickRouteDestinationStation = if (isCurrentQuickDest) chosen else current.quickRouteDestinationStation,
                targetBatteryQuickCheck = updatedBattery
            )
        }
        state.selectedTarget?.let { target ->
            evaluateMapBatteryFallback(
                target = target,
                nearest = chosen,
                batteryCheck = updatedBattery,
                transitMode = state.displayPrefs.transitMode,
                profile = scooterProfile.value
            )
        }
    }

    fun clearSelectedTarget() {
        mapReverseGeocodeJob?.cancel()
        mapScooterRoutingJob?.cancel()
        mapFallbackJob?.cancel()
        _mapState.update {
            it.copy(
                selectedTarget = null,
                nearestStationsToTarget = emptyList(),
                selectedStationIndex = 0,
                targetBatteryQuickCheck = null,
                batteryFallbackSuggestion = null,
                searchQuery = ""
            )
        }
    }

    /**
     * Marks [location] as the quick-route Start (with its currently selected station 1/2/3).
     * Returns `true` if a quick-route Destination was already set and the full Kombi-Route was launched immediately.
     */
    fun setQuickRouteStart(location: SearchLocation): Boolean {
        val state = _mapState.value
        val selectedStation = state.nearestStationsToTarget
            .take(3)
            .getOrNull(state.selectedStationIndex)
            ?: state.nearestStationsToTarget.firstOrNull()

        val existingDest = state.quickRouteDestination
        val isSameAsExistingDest = existingDest != null && (
            existingDest.id == location.id ||
                GeoDistanceUtils.haversineKm(existingDest.location, location.location) < 0.025
            )

        if (existingDest != null && !isSameAsExistingDest) {
            launchCombinedQuickRoute(
                start = location,
                startStation = selectedStation?.station,
                destination = existingDest,
                destStation = state.quickRouteDestinationStation?.station
            )
            return true
        }

        _mapState.update {
            it.copy(
                selectedTargetRole = MapPointRole.START,
                quickRouteStart = location,
                quickRouteStartStation = selectedStation,
                quickRouteDestination = if (isSameAsExistingDest) null else it.quickRouteDestination,
                quickRouteDestinationStation = if (isSameAsExistingDest) null else it.quickRouteDestinationStation
            )
        }
        val stationSuffix = selectedStation?.station?.name?.let { " (über $it)" } ?: ""
        _snackbarMessages.tryEmit("Start festgelegt: ${location.name}$stationSuffix")
        return false
    }

    /**
     * Marks [location] as the quick-route Destination (with its currently selected station 1/2/3).
     * Returns `true` if a quick-route Start was already set and the full Kombi-Route was launched immediately.
     */
    fun setQuickRouteDestination(location: SearchLocation): Boolean {
        val state = _mapState.value
        val selectedStation = state.nearestStationsToTarget
            .take(3)
            .getOrNull(state.selectedStationIndex)
            ?: state.nearestStationsToTarget.firstOrNull()

        val existingStart = state.quickRouteStart
        val isSameAsExistingStart = existingStart != null && (
            existingStart.id == location.id ||
                GeoDistanceUtils.haversineKm(existingStart.location, location.location) < 0.025
            )

        if (existingStart != null && !isSameAsExistingStart) {
            launchCombinedQuickRoute(
                start = existingStart,
                startStation = state.quickRouteStartStation?.station,
                destination = location,
                destStation = selectedStation?.station
            )
            return true
        }

        _mapState.update {
            it.copy(
                selectedTargetRole = MapPointRole.DESTINATION,
                quickRouteDestination = location,
                quickRouteDestinationStation = selectedStation,
                quickRouteStart = if (isSameAsExistingStart) null else it.quickRouteStart,
                quickRouteStartStation = if (isSameAsExistingStart) null else it.quickRouteStartStation
            )
        }
        val stationSuffix = selectedStation?.station?.name?.let { " (über $it)" } ?: ""
        _snackbarMessages.tryEmit("Ziel festgelegt: ${location.name}$stationSuffix")
        return false
    }

    fun clearQuickRouteStart() {
        _mapState.update {
            it.copy(
                quickRouteStart = null,
                quickRouteStartStation = null
            )
        }
    }

    fun clearQuickRouteDestination() {
        _mapState.update {
            it.copy(
                quickRouteDestination = null,
                quickRouteDestinationStation = null
            )
        }
    }

    private fun launchCombinedQuickRoute(
        start: SearchLocation,
        startStation: TransitStation?,
        destination: SearchLocation,
        destStation: TransitStation?
    ) {
        val state = _mapState.value
        val resolvedStartMode = startStation?.let(::routeChoiceForStation)
            ?: state.displayPrefs.transitMode.toRouteChoiceOrNull()
            ?: _routeState.value.startMode
        val resolvedDestMode = destStation?.let(::routeChoiceForStation)
            ?: state.displayPrefs.transitMode.toRouteChoiceOrNull()
            ?: _routeState.value.destinationMode

        _mapState.update {
            it.copy(
                quickRouteStart = null,
                quickRouteStartStation = null,
                quickRouteDestination = null,
                quickRouteDestinationStation = null
            )
        }
        _routeState.update {
            it.copy(
                startLocation = start,
                startQuery = start.name,
                destinationLocation = destination,
                destinationQuery = destination.name,
                preferredOriginStation = startStation,
                preferredDestinationStation = destStation,
                startMode = resolvedStartMode,
                destinationMode = resolvedDestMode,
                activeField = null,
                fieldSuggestions = emptyList()
            )
        }
        executeGeographicRoute()
    }

    private fun routeChoiceForStation(station: TransitStation): RouteTransitChoice = when (station.type) {
        StationType.BUS -> RouteTransitChoice.BUS
        StationType.REGIO, StationType.LONG_DISTANCE -> RouteTransitChoice.REGIO_DB
        StationType.SBAHN, StationType.UBAHN, StationType.TRAM -> RouteTransitChoice.SBAHN
    }

    fun onCameraMoveHandled() {
        _mapState.update { it.copy(cameraMoveRequest = null) }
    }

    fun toggleFavorite(location: SearchLocation) {
        viewModelScope.launch {
            val existing = favorites.value.firstOrNull { fav ->
                fav.id == location.id ||
                    GeoDistanceUtils.haversineKm(
                        GeoPoint(fav.latitude, fav.longitude),
                        location.location
                    ) < 0.025
            }
            if (existing != null) {
                favoriteDao.deleteById(existing.id)
                _snackbarMessages.tryEmit("Aus Favoriten entfernt")
            } else {
                val topOrder = (favorites.value.minOfOrNull { it.sortOrder } ?: 1) - 1
                favoriteDao.upsert(
                    FavoritePlaceEntity(
                        id = location.id,
                        name = location.name,
                        subtitle = location.subtitle,
                        latitude = location.location.latitude,
                        longitude = location.location.longitude,
                        sortOrder = topOrder
                    )
                )
                _snackbarMessages.tryEmit("Als Favorit gespeichert")
            }
        }
    }

    fun reorderFavorites(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        viewModelScope.launch {
            val byId = favorites.value.associateBy { it.id }
            val reordered = orderedIds.mapIndexedNotNull { index, id ->
                byId[id]?.copy(sortOrder = index)
            }
            if (reordered.isNotEmpty()) {
                favoriteDao.upsertAll(reordered)
            }
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch {
            favoriteDao.deleteById(id)
            _snackbarMessages.tryEmit("Favorit gelöscht")
        }
    }

    // ---- Tab "Route": Dedicated Geographic Start-to-Destination Planning ----

    fun onRouteFieldQueryChanged(field: RouteSearchField, query: String) {
        _routeState.update {
            when (field) {
                RouteSearchField.START -> it.copy(startQuery = query, activeField = field)
                RouteSearchField.DESTINATION -> it.copy(destinationQuery = query, activeField = field)
            }
        }
        routeSearchJob?.cancel()
        if (query.trim().length < 2) {
            _routeState.update { it.copy(fieldSuggestions = emptyList()) }
            return
        }
        routeSearchJob = viewModelScope.launch {
            delay(250)
            val results = router.searchPlaces(query, biasCenter = _mapState.value.lastCameraCenter)
            _routeState.update { it.copy(fieldSuggestions = results) }
        }
    }

    fun onRouteSuggestionSelected(location: SearchLocation) {
        val field = _routeState.value.activeField ?: RouteSearchField.DESTINATION
        applyRouteLocation(field, location)
    }

    fun dismissRouteSuggestions() {
        _routeState.update { it.copy(activeField = null, fieldSuggestions = emptyList()) }
    }

    fun clearRouteField(field: RouteSearchField) {
        routeSearchJob?.cancel()
        _routeState.update {
            when (field) {
                RouteSearchField.START -> it.copy(
                    startLocation = null,
                    preferredOriginStation = null,
                    startQuery = "",
                    activeField = field,
                    fieldSuggestions = emptyList()
                )
                RouteSearchField.DESTINATION -> it.copy(
                    destinationLocation = null,
                    preferredDestinationStation = null,
                    destinationQuery = "",
                    activeField = field,
                    fieldSuggestions = emptyList()
                )
            }
        }
    }

    fun setRouteLocationFromMap(field: RouteSearchField, location: SearchLocation) {
        applyRouteLocation(field, location)
    }

    fun setRouteLocationFromMap(field: RouteSearchField, point: GeoPoint) {
        val picked = buildLocationFromPoint(point)
        applyRouteLocation(field, picked)
    }

    fun buildQuickPointLocation(point: GeoPoint): SearchLocation = buildLocationFromPoint(point)

    suspend fun resolveTappedMapPoint(point: GeoPoint): SearchLocation {
        val originalId = "pin_${point.latitude}_${point.longitude}"
        val snapped = withTimeoutOrNull(2600L) {
            bikeRoutingRepo.snapToNearestRoad(
                point = point,
                maxRadiusMeters = ScooterBikeRoutingRepository.MAX_SNAP_RADIUS_METERS
            )
        }
        val effectivePoint = snapped?.point ?: point
        return resolvePointLocation(
            point = effectivePoint,
            preservedId = originalId,
            fallbackRoadName = snapped?.roadName
        )
    }

    suspend fun resolvePointLocation(
        point: GeoPoint,
        preservedId: String? = null,
        fallbackRoadName: String? = null
    ): SearchLocation {
        val fallback = buildLocationFromPoint(
            point = point,
            preservedId = preservedId,
            preferredName = fallbackRoadName
        )
        val coordsLabel = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
        val response = try {
            withTimeoutOrNull(3500L) {
                NetworkModule.photonApi.reverseGeocode(
                    lat = point.latitude,
                    lon = point.longitude,
                    limit = 1,
                    lang = "de"
                )
            }
        } catch (_: Exception) {
            null
        }
        val props = response?.features?.firstOrNull()?.properties ?: return fallback
        val streetLine = listOfNotNull(
            props.street?.takeIf { it.isNotBlank() },
            props.housenumber?.takeIf { it.isNotBlank() }
        ).joinToString(" ").trim()
        val placeName = props.name?.takeIf { it.isNotBlank() }
        val cityOrLocality = props.city?.takeIf { it.isNotBlank() }
            ?: props.locality?.takeIf { it.isNotBlank() }
            ?: props.county?.takeIf { it.isNotBlank() }
        val district = props.district?.takeIf { it.isNotBlank() && it != cityOrLocality }

        val title = when {
            placeName != null && streetLine.isNotBlank() && !placeName.equals(streetLine, ignoreCase = true) ->
                placeName
            streetLine.isNotBlank() -> streetLine
            placeName != null -> placeName
            !fallbackRoadName.isNullOrBlank() -> fallbackRoadName
            district != null && cityOrLocality != null -> "$district, $cityOrLocality"
            cityOrLocality != null -> cityOrLocality
            else -> return fallback
        }

        val detailParts = buildList {
            if (placeName != null && streetLine.isNotBlank() && !placeName.equals(streetLine, ignoreCase = true)) {
                add(streetLine)
            }
            if (district != null && !title.contains(district, ignoreCase = true)) {
                add(district)
            }
            if (cityOrLocality != null && !title.contains(cityOrLocality, ignoreCase = true)) {
                add(cityOrLocality)
            }
            add(coordsLabel)
        }

        return SearchLocation(
            id = fallback.id,
            name = title,
            subtitle = detailParts.joinToString(" · "),
            location = point
        )
    }

    fun setRouteStartFromGps(point: GeoPoint) {
        updateUserLocation(point, moveCamera = false)
        val gpsLoc = SearchLocation(
            id = "gps_user_location",
            name = "Mein Standort (GPS)",
            subtitle = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude),
            location = point
        )
        applyRouteLocation(RouteSearchField.START, gpsLoc)
    }

    private fun applyRouteLocation(field: RouteSearchField, location: SearchLocation) {
        _routeState.update {
            when (field) {
                RouteSearchField.START -> it.copy(
                    startLocation = location,
                    preferredOriginStation = null,
                    startQuery = location.name,
                    activeField = null,
                    fieldSuggestions = emptyList()
                )
                RouteSearchField.DESTINATION -> it.copy(
                    destinationLocation = location,
                    preferredDestinationStation = null,
                    destinationQuery = location.name,
                    activeField = null,
                    fieldSuggestions = emptyList()
                )
            }
        }
        if (_routeState.value.startLocation != null && _routeState.value.destinationLocation != null) {
            executeGeographicRoute()
        }
    }

    fun setRouteStartMode(mode: RouteTransitChoice) {
        _routeState.update { it.copy(startMode = mode, preferredOriginStation = null) }
        if (_routeState.value.startLocation != null && _routeState.value.destinationLocation != null) {
            executeGeographicRoute()
        }
    }

    fun setRouteDestinationMode(mode: RouteTransitChoice) {
        _routeState.update { it.copy(destinationMode = mode, preferredDestinationStation = null) }
        if (_routeState.value.startLocation != null && _routeState.value.destinationLocation != null) {
            executeGeographicRoute()
        }
    }

    fun swapStartAndDestination() {
        val current = _routeState.value
        val start = current.startLocation
        val dest = current.destinationLocation
        if (start == null && dest == null) return
        _routeState.update {
            it.copy(
                startLocation = dest,
                destinationLocation = start,
                preferredOriginStation = current.preferredDestinationStation,
                preferredDestinationStation = current.preferredOriginStation,
                startQuery = dest?.name ?: "",
                destinationQuery = start?.name ?: "",
                startMode = current.destinationMode,
                destinationMode = current.startMode,
                activeField = null,
                fieldSuggestions = emptyList()
            )
        }
        if (_routeState.value.startLocation != null && _routeState.value.destinationLocation != null) {
            executeGeographicRoute()
        }
    }

    fun clearPreferredOriginStation() {
        _routeState.update { it.copy(preferredOriginStation = null) }
        if (_routeState.value.startLocation != null && _routeState.value.destinationLocation != null) {
            executeGeographicRoute()
        }
    }

    fun clearPreferredDestinationStation() {
        _routeState.update { it.copy(preferredDestinationStation = null) }
        if (_routeState.value.startLocation != null && _routeState.value.destinationLocation != null) {
            executeGeographicRoute()
        }
    }

    fun executeGeographicRoute() {
        val current = _routeState.value
        val start = current.startLocation ?: return
        val dest = current.destinationLocation ?: return
        routeFallbackJob?.cancel()
        viewModelScope.launch {
            _routeState.update {
                it.copy(
                    routingStatus = RoutingStatus.Loading,
                    batteryFallbackSuggestion = null
                )
            }
            val result = router.planGeographicRoute(
                start = start,
                destination = dest,
                startMode = current.startMode,
                destinationMode = current.destinationMode,
                knownDynamicStations = _mapState.value.dynamicOverlay.discoveredStations + lastMapOsmStations,
                profile = scooterProfile.value,
                preferredOriginStation = current.preferredOriginStation,
                preferredDestinationStation = current.preferredDestinationStation
            )
            result.fold(
                onSuccess = { route ->
                    _routeState.update {
                        it.copy(
                            routingStatus = RoutingStatus.Success(route),
                            batteryFallbackSuggestion = null
                        )
                    }
                    evaluateRouteBatteryFallback(route, scooterProfile.value)
                },
                onFailure = { err ->
                    _routeState.update {
                        it.copy(
                            routingStatus = RoutingStatus.Error(
                                err.localizedMessage
                                    ?: "Verbindung konnte geografisch nicht berechnet werden."
                            ),
                            batteryFallbackSuggestion = null
                        )
                    }
                }
            )
        }
    }

    // ---- Scooter Profile & Persistent Custom Scooters ("Meine Scooter") ----

    fun updateBatteryPercent(percent: Int) {
        viewModelScope.launch { prefsRepo.updateBatteryPercent(percent) }
    }

    fun saveCustomScooter(
        existingId: String?,
        modelName: String,
        manufacturerRangeKm: Int,
        realWorldFactorPercent: Int,
        maxSpeedKmh: Int,
        batteryCapacityWh: Int?,
        nominalPowerW: Int?,
        peakPowerW: Int?
    ) {
        viewModelScope.launch {
            val scooterId = existingId ?: "custom_${UUID.randomUUID()}"
            val cleanName = modelName.trim().ifBlank { "Mein E-Scooter" }
            val existingCreatedAt = customScooters.value.firstOrNull { it.id == scooterId }?.createdAt
                ?: System.currentTimeMillis()
            val entity = CustomScooterEntity(
                id = scooterId,
                modelName = cleanName,
                manufacturerRangeKm = manufacturerRangeKm.coerceIn(10, 250),
                realWorldFactorPercent = realWorldFactorPercent.coerceIn(30, 100),
                maxSpeedKmh = maxSpeedKmh.coerceIn(10, 45),
                batteryCapacityWh = batteryCapacityWh?.takeIf { it > 0 },
                nominalPowerW = nominalPowerW?.takeIf { it > 0 },
                peakPowerW = peakPowerW?.takeIf { it > 0 },
                createdAt = existingCreatedAt
            )
            customScooterDao.upsert(entity)
            prefsRepo.saveActiveProfile(
                entity.toScooterProfile(currentBatteryPercent = scooterProfile.value.currentBatteryPercent)
            )
        }
    }

    fun selectCustomScooter(scooter: CustomScooterEntity) {
        viewModelScope.launch {
            prefsRepo.saveActiveProfile(
                scooter.toScooterProfile(currentBatteryPercent = scooterProfile.value.currentBatteryPercent)
            )
        }
    }

    fun deleteCustomScooter(scooterId: String) {
        viewModelScope.launch {
            customScooterDao.deleteById(scooterId)
            if (scooterProfile.value.activeScooterId == scooterId) {
                val remaining = customScooters.value.firstOrNull { it.id != scooterId }
                if (remaining != null) {
                    prefsRepo.saveActiveProfile(
                        remaining.toScooterProfile(scooterProfile.value.currentBatteryPercent)
                    )
                } else {
                    applyScooterPreset(DefaultScooterPresets.first())
                }
            }
        }
    }

    fun applyScooterPreset(preset: ScooterPreset) {
        viewModelScope.launch {
            prefsRepo.saveActiveProfile(
                ScooterProfile(
                    activeScooterId = preset.id,
                    modelName = preset.modelName,
                    manufacturerRangeKm = preset.manufacturerRangeKm,
                    realWorldFactorPercent = preset.realWorldFactorPercent,
                    maxSpeedKmh = preset.maxSpeedKmh,
                    currentBatteryPercent = scooterProfile.value.currentBatteryPercent,
                    batteryCapacityWh = preset.batteryCapacityWh,
                    nominalPowerW = preset.nominalPowerW,
                    peakPowerW = preset.peakPowerW
                )
            )
        }
    }

    // ---- Internal Helpers ----

    private fun buildLocationFromPoint(
        point: GeoPoint,
        preservedId: String? = null,
        preferredName: String? = null
    ): SearchLocation {
        val label = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
        val nearestDistrict = MunichSBahnNetwork.districts.minByOrNull {
            GeoDistanceUtils.haversineKm(point, it.location)
        }?.takeIf { GeoDistanceUtils.haversineKm(point, it.location) <= 8.0 }

        val cleanPreferred = preferredName?.trim()?.takeIf { it.isNotBlank() }
        val subtitle = if (nearestDistrict != null) {
            "Kartenpunkt bei ${nearestDistrict.name} · $label"
        } else {
            "Kartenpunkt · $label"
        }
        return SearchLocation(
            id = preservedId ?: "pin_${point.latitude}_${point.longitude}",
            name = cleanPreferred ?: nearestDistrict?.let { "Punkt nahe ${it.name}" } ?: "Kartenpunkt",
            subtitle = subtitle,
            location = point
        )
    }

    /**
     * Strictly searches ONLY stations belonging to the currently active map network filter (`transitMode`).
     * Uses real stations from `mvv_transit.json` / Overpass exclusively when available; static
     * [MunichSBahnNetwork] coordinates are only used as a last-resort fallback if no JSON data is loaded yet.
     */
    private fun computeNearestStationsForActiveNetwork(
        target: GeoPoint,
        discoveredStations: List<TransitStation>,
        transitMode: MapTransitMode,
        profile: ScooterProfile
    ): List<NearbyStationResult> {
        val realStations = when (transitMode) {
            MapTransitMode.SBAHN_ONLY -> discoveredStations.filter { RouteTransitChoice.SBAHN.matchesStation(it) }
            MapTransitMode.BUS_ONLY -> discoveredStations.filter { RouteTransitChoice.BUS.matchesStation(it) }
            MapTransitMode.REGIO_LONG_ONLY -> discoveredStations.filter { RouteTransitChoice.REGIO_DB.matchesStation(it) }
            MapTransitMode.ALL -> discoveredStations
        }

        val pool = if (realStations.isNotEmpty()) {
            realStations
        } else {
            // Last-resort emergency fallback ONLY if no mvv_transit.json / OSM data is loaded yet
            when (transitMode) {
                MapTransitMode.SBAHN_ONLY -> MunichSBahnNetwork.stations
                MapTransitMode.BUS_ONLY -> MunichSBahnNetwork.busTerminalStopsAsStations
                MapTransitMode.REGIO_LONG_ONLY -> MunichSBahnNetwork.regioLongStations
                MapTransitMode.ALL -> MunichSBahnNetwork.stations +
                    MunichSBahnNetwork.busTerminalStopsAsStations +
                    MunichSBahnNetwork.regioLongStations
            }
        }

        val deduped = deduplicateByNameAndProximity(pool)
        return GeoDistanceUtils.findNearestStations(
            point = target,
            stations = deduped,
            profile = profile,
            limit = 5
        )
    }

    private fun deduplicateByNameAndProximity(stations: List<TransitStation>): List<TransitStation> {
        val result = ArrayList<TransitStation>(stations.size)
        for (candidate in stations) {
            val normName = normalizeStationName(candidate.name)
            val existingIdx = result.indexOfFirst { existing ->
                existing.type == candidate.type &&
                    normalizeStationName(existing.name) == normName &&
                    GeoDistanceUtils.haversineKm(existing.location, candidate.location) <= 0.55
            }
            if (existingIdx >= 0) {
                val existing = result[existingIdx]
                val mergedLines = (existing.lines + candidate.lines).distinct().take(6)
                result[existingIdx] = existing.copy(lines = mergedLines)
            } else {
                result.add(candidate)
            }
        }
        return result
    }

    private fun normalizeStationName(name: String): String {
        return name.lowercase(Locale.GERMAN)
            .replace("s-bahnhof", "")
            .replace("s-bahn", "")
            .replace("bahnhof", "")
            .replace("bf.", "")
            .replace("(", " ")
            .replace(")", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun recalculateMapNearestWithProfile(
        profile: ScooterProfile,
        transitMode: MapTransitMode,
        refreshRoutes: Boolean = false,
        discardCachedPaths: Boolean = false
    ) {
        val state = _mapState.value
        val target = state.selectedTarget ?: return
        val existingByStationId = if (discardCachedPaths) {
            emptyMap()
        } else {
            state.nearestStationsToTarget.associateBy { it.station.id }
        }
        val recomputed = computeNearestStationsForActiveNetwork(
            target = target.location,
            discoveredStations = state.dynamicOverlay.discoveredStations + lastMapOsmStations,
            transitMode = transitMode,
            profile = profile
        )
        // Preserve already-computed real street polylines & distances when only battery/speed changed
        val merged = recomputed.map { item ->
            val prev = existingByStationId[item.station.id]
            if (prev != null && prev.routePath.size >= 2 && !prev.isOfflineEstimate) {
                item.copy(
                    estimatedScooterKm = prev.estimatedScooterKm,
                    estimatedScooterMinutes = GeoDistanceUtils.estimateScooterMinutes(prev.estimatedScooterKm, profile),
                    routePath = prev.routePath,
                    isOfflineEstimate = false
                )
            } else {
                item
            }
        }
        val prevSelectedId = state.nearestStationsToTarget
            .getOrNull(state.selectedStationIndex)?.station?.id
        val topThreeMerged = merged.take(3)
        val preservedIdx = if (topThreeMerged.isEmpty()) {
            0
        } else if (prevSelectedId != null) {
            val foundIdx = topThreeMerged.indexOfFirst { it.station.id == prevSelectedId }
            if (foundIdx >= 0) foundIdx else state.selectedStationIndex.coerceIn(0, topThreeMerged.lastIndex)
        } else {
            state.selectedStationIndex.coerceIn(0, topThreeMerged.lastIndex)
        }
        val activeOption = topThreeMerged.getOrNull(preservedIdx) ?: merged.firstOrNull()
        val activeLegKm = activeOption?.estimatedScooterKm ?: 0.0
        val batteryCheck = if (activeOption != null) {
            BatteryRangeCalculator.assessRoundTrip(
                firstMileKm = activeLegKm,
                lastMileKm = 0.0,
                profile = profile
            )
        } else {
            null
        }
        _mapState.update { current ->
            val isCurrentQuickStart = current.quickRouteStart != null &&
                GeoDistanceUtils.haversineKm(current.quickRouteStart.location, target.location) < 0.025
            val isCurrentQuickDest = current.quickRouteDestination != null &&
                GeoDistanceUtils.haversineKm(current.quickRouteDestination.location, target.location) < 0.025
            current.copy(
                nearestStationsToTarget = merged,
                selectedStationIndex = preservedIdx,
                quickRouteStartStation = if (isCurrentQuickStart && activeOption != null) {
                    activeOption
                } else {
                    current.quickRouteStartStation
                },
                quickRouteDestinationStation = if (isCurrentQuickDest && activeOption != null) {
                    activeOption
                } else {
                    current.quickRouteDestinationStation
                },
                targetBatteryQuickCheck = batteryCheck
            )
        }
        evaluateMapBatteryFallback(
            target = target,
            nearest = activeOption,
            batteryCheck = batteryCheck,
            transitMode = transitMode,
            profile = profile
        )
        val needsStreetRoutes = refreshRoutes || merged.take(3).any { it.routePath.size < 2 }
        if (needsStreetRoutes) {
            enrichMapNearestWithStreetRoutes(target.location, merged, profile)
        }
    }

    /**
     * Battery-range fallback hint for Tab "Karte":
     * NEVER mixes other transit modes into `nearestStationsToTarget`. Only offers a conscious
     * switch button when the nearest station of the user's chosen mode exceeds remaining range
     * (or round-trip is critical at low battery <= 25%).
     */
    private fun evaluateMapBatteryFallback(
        target: SearchLocation,
        nearest: NearbyStationResult?,
        batteryCheck: BatteryRoundTripAssessment?,
        transitMode: MapTransitMode,
        profile: ScooterProfile
    ) {
        mapFallbackJob?.cancel()
        val activeChoice = transitMode.toRouteChoiceOrNull()
        if (activeChoice == null) {
            _mapState.update { it.copy(batteryFallbackSuggestion = null) }
            return
        }

        val availableRangeKm = profile.currentAvailableRangeKm
        val outOfOneWayRange = nearest == null || nearest.estimatedScooterKm > availableRangeKm
        val lowBatteryCritical = profile.currentBatteryPercent <= 25 &&
            batteryCheck?.status == BatteryTrafficLight.CRITICAL
        if (!outOfOneWayRange && !lowBatteryCritical) {
            _mapState.update { it.copy(batteryFallbackSuggestion = null) }
            return
        }

        mapFallbackJob = viewModelScope.launch {
            val localAlt = overpassRepo.findClosestAlternativeStationLocal(target.location, activeChoice)
                ?: findInMemoryAlternativeStation(target.location, activeChoice)

            val currentTarget = _mapState.value.selectedTarget ?: return@launch
            if (currentTarget.id != target.id || _mapState.value.displayPrefs.transitMode != transitMode) {
                return@launch
            }

            val suggestion = if (localAlt != null) {
                val (altChoice, altStation) = localAlt
                val altScooterKm = GeoDistanceUtils.estimatedScooterKm(target.location, altStation.location)
                val isCloser = nearest == null || altScooterKm < nearest.estimatedScooterKm - 0.25
                if (isCloser) {
                    val distText = String.format(Locale.GERMAN, "%.1f km", altScooterKm)
                    val reason = if (nearest == null) {
                        "Keine ${activeChoice.shortLabel}-Haltestelle in Reichweite"
                    } else {
                        "${activeChoice.shortLabel} liegt außerhalb deiner Akkureichweite"
                    }
                    BatteryRangeFallbackSuggestion(
                        message = "$reason – stattdessen ${altChoice.shortLabel} (ca. $distText) in der Nähe anzeigen?",
                        actionLabel = "Zu ${altChoice.shortLabel} wechseln",
                        suggestedMapMode = altChoice.toMapTransitMode()
                    )
                } else {
                    null
                }
            } else {
                val fallbackMode = if (activeChoice == RouteTransitChoice.BUS) {
                    MapTransitMode.ALL
                } else {
                    MapTransitMode.BUS_ONLY
                }
                val fallbackLabel = if (fallbackMode == MapTransitMode.ALL) "Alle Netze" else "Bus"
                BatteryRangeFallbackSuggestion(
                    message = "${activeChoice.shortLabel} liegt außerhalb deiner Akkureichweite – stattdessen Bus/Regio in der Nähe anzeigen?",
                    actionLabel = "Zu $fallbackLabel wechseln",
                    suggestedMapMode = fallbackMode
                )
            }

            _mapState.update { it.copy(batteryFallbackSuggestion = suggestion) }
        }
    }

    /**
     * Battery-range fallback hint for Tab "Route":
     * NEVER auto-replaces S-Bahn with Bus/Regio. Only displays an optional one-tap switch when
     * the scooter legs exceed remaining battery range (or round-trip is critical at low battery <= 25%).
     */
    private fun evaluateRouteBatteryFallback(
        route: GeographicScooterRoute,
        profile: ScooterProfile
    ) {
        routeFallbackJob?.cancel()
        val availableRangeKm = profile.currentAvailableRangeKm
        val outOfOneWayRange = route.totalScooterOneWayKm > availableRangeKm ||
            !route.batteryAssessment.oneWayFeasible
        val lowBatteryCritical = profile.currentBatteryPercent <= 25 &&
            route.batteryAssessment.status == BatteryTrafficLight.CRITICAL
        if (!outOfOneWayRange && !lowBatteryCritical) {
            _routeState.update { it.copy(batteryFallbackSuggestion = null) }
            return
        }

        val checkStart = route.originStation.estimatedScooterKm >= route.destinationStation.estimatedScooterKm
        val legPoint = if (checkStart) route.startLocation.location else route.destinationLocation.location
        val legMode = if (checkStart) route.startMode else route.destinationMode
        val legScooterKm = if (checkStart) {
            route.originStation.estimatedScooterKm
        } else {
            route.destinationStation.estimatedScooterKm
        }
        val legPlaceLabel = if (checkStart) "am Start" else "am Ziel"

        routeFallbackJob = viewModelScope.launch {
            val localAlt = overpassRepo.findClosestAlternativeStationLocal(legPoint, legMode)
                ?: findInMemoryAlternativeStation(legPoint, legMode)

            val activeSuccess = _routeState.value.routingStatus as? RoutingStatus.Success ?: return@launch
            if (activeSuccess.route.id != route.id) return@launch

            val suggestion = if (localAlt != null) {
                val (altChoice, altStation) = localAlt
                val altScooterKm = GeoDistanceUtils.estimatedScooterKm(legPoint, altStation.location)
                if (altScooterKm < legScooterKm - 0.25) {
                    val distText = String.format(Locale.GERMAN, "%.1f km", altScooterKm)
                    val prefix = if (checkStart) "Start" else "Ziel"
                    BatteryRangeFallbackSuggestion(
                        message = "${legMode.shortLabel} $legPlaceLabel liegt außerhalb deiner Akkureichweite – stattdessen ${altChoice.shortLabel} (ca. $distText) nutzen?",
                        actionLabel = "$prefix auf ${altChoice.shortLabel} umstellen",
                        suggestedRouteMode = altChoice,
                        targetIsRouteStart = checkStart
                    )
                } else {
                    null
                }
            } else {
                val altChoice = if (legMode == RouteTransitChoice.BUS) {
                    RouteTransitChoice.SBAHN
                } else {
                    RouteTransitChoice.BUS
                }
                val prefix = if (checkStart) "Start" else "Ziel"
                BatteryRangeFallbackSuggestion(
                    message = "${legMode.shortLabel} $legPlaceLabel liegt außerhalb deiner Akkureichweite – stattdessen ${altChoice.shortLabel} in der Nähe testen?",
                    actionLabel = "$prefix auf ${altChoice.shortLabel} umstellen",
                    suggestedRouteMode = altChoice,
                    targetIsRouteStart = checkStart
                )
            }

            _routeState.update { it.copy(batteryFallbackSuggestion = suggestion) }
        }
    }

    private fun findInMemoryAlternativeStation(
        center: GeoPoint,
        excludedMode: RouteTransitChoice
    ): Pair<RouteTransitChoice, TransitStation>? {
        val known = _mapState.value.dynamicOverlay.discoveredStations + lastMapOsmStations
        var bestChoice: RouteTransitChoice? = null
        var bestStation: TransitStation? = null
        var bestDist = Double.MAX_VALUE
        for (choice in RouteTransitChoice.entries) {
            if (choice == excludedMode) continue
            val candidates = known.filter { choice.matchesStation(it) }
            val nearest = candidates.minByOrNull { GeoDistanceUtils.haversineKm(it.location, center) } ?: continue
            val dist = GeoDistanceUtils.haversineKm(nearest.location, center)
            if (dist < bestDist) {
                bestDist = dist
                bestChoice = choice
                bestStation = nearest
            }
        }
        return if (bestChoice != null && bestStation != null) bestChoice to bestStation else null
    }

    private fun recalculateRouteBatteryWithProfile(profile: ScooterProfile) {
        val status = _routeState.value.routingStatus
        if (status !is RoutingStatus.Success) return
        val route = status.route
        val updatedOriginMin = GeoDistanceUtils.estimateScooterMinutes(
            route.originStation.estimatedScooterKm,
            profile
        )
        val updatedDestMin = GeoDistanceUtils.estimateScooterMinutes(
            route.destinationStation.estimatedScooterKm,
            profile
        )
        val newAssessment = BatteryRangeCalculator.assessRoundTrip(
            firstMileKm = route.originStation.estimatedScooterKm,
            lastMileKm = route.destinationStation.estimatedScooterKm,
            profile = profile
        )
        val updatedRoute = route.copy(
            originStation = route.originStation.copy(estimatedScooterMinutes = updatedOriginMin),
            destinationStation = route.destinationStation.copy(estimatedScooterMinutes = updatedDestMin),
            totalScooterOneWayMinutes = updatedOriginMin + updatedDestMin,
            batteryAssessment = newAssessment
        )
        _routeState.update {
            it.copy(routingStatus = RoutingStatus.Success(updatedRoute))
        }
        evaluateRouteBatteryFallback(updatedRoute, profile)
    }
}
