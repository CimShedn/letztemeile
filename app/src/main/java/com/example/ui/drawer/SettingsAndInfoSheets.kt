package com.example.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.OverpassTransitRepository
import com.example.data.remote.RegionalPackageStatus
import com.example.model.AppSettings
import com.example.model.PreferredMapsApp
import com.example.model.PreferredTransitApp
import com.example.model.TransitRegionRecommendation
import java.util.Locale

private data class SettingsRegionOption(
    val app: PreferredTransitApp,
    val regionLabel: String,
    val appDisplayName: String
)

/**
 * Deduplicated DACH region & transit app options (all unique offline regions + DB Navigator).
 * Eliminates duplicate entries such as the legacy second "Berlin & Brandenburg (VBB)" row.
 */
private val SETTINGS_REGION_OPTIONS: List<SettingsRegionOption> = buildList {
    OverpassTransitRepository.REGIONAL_PACKAGES.values.forEach { pkg ->
        add(
            SettingsRegionOption(
                app = pkg.defaultTransitApp,
                regionLabel = pkg.displayName,
                appDisplayName = pkg.appLabel
            )
        )
    }
    add(
        SettingsRegionOption(
            app = PreferredTransitApp.DB_NAVIGATOR,
            regionLabel = PreferredTransitApp.DB_NAVIGATOR.regionLabel,
            appDisplayName = PreferredTransitApp.DB_NAVIGATOR.displayName
        )
    )
}

private fun normalizeSettingsTransitApp(app: PreferredTransitApp): PreferredTransitApp = when (app) {
    PreferredTransitApp.MVGO -> PreferredTransitApp.MVV_APP
    PreferredTransitApp.VBB_APP -> PreferredTransitApp.BVG_JELBI
    else -> app
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    settings: AppSettings,
    detectedRegion: TransitRegionRecommendation,
    regionalPackageStatuses: List<RegionalPackageStatus>,
    isDownloadingAllRegions: Boolean,
    onSelectMapsApp: (PreferredMapsApp) -> Unit,
    onSelectTransitApp: (PreferredTransitApp) -> Unit,
    onToggleAutoRegion: (Boolean) -> Unit,
    onDownloadSingleRegion: (String, Boolean) -> Unit,
    onDeleteSingleRegion: (String) -> Unit,
    onDownloadAllRegions: () -> Unit,
    onCancelDownloadAllRegions: () -> Unit,
    onClearTransitCache: () -> Unit,
    onOpenAboutAndLegal: () -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState? = null
) {
    BackHandler(onBack = onDismiss)

    // Accordion expand states (all 3 large sections start collapsed for a clean overview)
    var isMapsExpanded by rememberSaveable { mutableStateOf(false) }
    var isRegionExpanded by rememberSaveable { mutableStateOf(false) }
    var isOfflineNetsExpanded by rememberSaveable { mutableStateOf(false) }

    val normalizedSelectedTransitApp = normalizeSettingsTransitApp(settings.preferredTransitApp)
    val selectedRegionOption = SETTINGS_REGION_OPTIONS.firstOrNull {
        it.app == normalizedSelectedTransitApp
    } ?: SETTINGS_REGION_OPTIONS.first()

    val loadedCount = regionalPackageStatuses.count { it.isDownloaded }
    val totalRegionCount = regionalPackageStatuses.size.coerceAtLeast(OverpassTransitRepository.REGIONAL_PACKAGES.size)
    val totalLoadedBytes = regionalPackageStatuses.filter { it.isDownloaded }.sumOf { it.sizeBytes }
    val offlineSummary = if (loadedCount > 0) {
        "$loadedCount von $totalRegionCount Netzen geladen · ${formatSizeMb(totalLoadedBytes)}"
    } else {
        "0 von $totalRegionCount Netzen geladen"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back_cd),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Karten- & ÖPNV-Apps, Region und Offline-Netze",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_modal_sheet")
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
                // 1. Auto-Region Detection (ALWAYS visible — NOT collapsed)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Auto-Regionserkennung",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Erkennt deine Region per GPS (alle $totalRegionCount DACH-Regionen) und schlägt automatisch das passende ÖPNV-Netz vor",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = settings.autoRegionDetection,
                                onCheckedChange = onToggleAutoRegion,
                                modifier = Modifier.testTag("settings_auto_region_switch")
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Erkannte Region: ${detectedRegion.regionDisplayName} → ${detectedRegion.recommendedApp.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // 2. Expandable Card: Bevorzugte Karten-App (Scooter-Wege)
                ExpandableSettingsCard(
                    icon = Icons.Filled.Map,
                    title = "Bevorzugte Karten-App (Scooter-Wege)",
                    summary = settings.preferredMapsApp.displayName,
                    expanded = isMapsExpanded,
                    onToggle = { isMapsExpanded = !isMapsExpanded },
                    headerTestTag = "settings_accordion_maps"
                ) {
                    Column {
                        PreferredMapsApp.entries.forEachIndexed { idx, app ->
                            val selected = settings.preferredMapsApp == app
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectMapsApp(app) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("settings_maps_${app.id}")
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectMapsApp(app) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = app.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (idx < PreferredMapsApp.entries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // 3. Expandable Card: Region & bevorzugte ÖPNV-App (deduplicated DACH regions + DB Navigator)
                ExpandableSettingsCard(
                    icon = Icons.Filled.Train,
                    title = "Region & bevorzugte ÖPNV-App",
                    summary = "${selectedRegionOption.regionLabel} · ${selectedRegionOption.appDisplayName}",
                    expanded = isRegionExpanded,
                    onToggle = { isRegionExpanded = !isRegionExpanded },
                    headerTestTag = "settings_accordion_region"
                ) {
                    Column {
                        SETTINGS_REGION_OPTIONS.forEachIndexed { idx, option ->
                            val selected = normalizedSelectedTransitApp == option.app
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTransitApp(option.app) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("settings_transit_${option.app.id}")
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectTransitApp(option.app) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = option.regionLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = option.appDisplayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (idx < SETTINGS_REGION_OPTIONS.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // 4. Expandable Card: Offline-ÖPNV-Netze verwalten (Download Manager for all DACH regions)
                ExpandableSettingsCard(
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.settings_offline_nets_title),
                    summary = offlineSummary,
                    expanded = isOfflineNetsExpanded,
                    onToggle = { isOfflineNetsExpanded = !isOfflineNetsExpanded },
                    headerTestTag = "settings_accordion_offline_nets"
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Subtle Wi-Fi recommendation info banner
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                                .testTag("settings_offline_nets_wifi_hint")
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_offline_nets_wifi_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // Optional top action button: Download all DACH networks (or cancel active batch)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (isDownloadingAllRegions) {
                                OutlinedButton(
                                    onClick = onCancelDownloadAllRegions,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("settings_offline_nets_cancel_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_offline_nets_cancel_all))
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = onDownloadAllRegions,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("settings_offline_nets_download_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_offline_nets_download_all),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        regionalPackageStatuses.forEachIndexed { idx, pkg ->
                            OfflineRegionDownloadRow(
                                status = pkg,
                                onDownload = { onDownloadSingleRegion(pkg.regionId, false) },
                                onRefresh = { onDownloadSingleRegion(pkg.regionId, true) },
                                onDelete = { onDeleteSingleRegion(pkg.regionId) }
                            )
                            if (idx < regionalPackageStatuses.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 5. ÖPNV-Cache zurücksetzen (ALWAYS visible — NOT collapsed)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "ÖPNV-Cache zurücksetzen",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Leert den lokalen Linien-Cache und lädt S-Bahn-, Bus- und Regio-Linien im aktuellen Kartenausschnitt neu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onClearTransitCache,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_clear_transit_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ÖPNV-Cache leeren & neu laden",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 6. Über die App & Rechtliches (ALWAYS visible — NOT collapsed)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.legal_sheet_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Haftungsausschluss, Datenschutzerklärung (DSGVO), OpenStreetMap-/Open-Source-Lizenzen sowie Kontakt & Projekt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onOpenAboutAndLegal,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_about_legal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.legal_sheet_title),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExpandableSettingsCard(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerTestTag: String,
    content: @Composable () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accordion_chevron"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag(headerTestTag)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_collapse_cd else R.string.settings_expand_cd
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun OfflineRegionDownloadRow(
    status: RegionalPackageStatus,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("offline_net_row_${status.regionId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val statusText = when {
                    status.isDownloading -> {
                        if (status.totalBytes > 0L) {
                            "Lädt… ${formatSizeMb(status.downloadedBytes)} / ${formatSizeMb(status.totalBytes)}"
                        } else if (status.downloadedBytes > 0L) {
                            "Lädt… ${formatSizeMb(status.downloadedBytes)}"
                        } else {
                            "Verbindung wird hergestellt…"
                        }
                    }
                    status.isDownloaded -> "Geladen · ${formatSizeMb(status.sizeBytes)}"
                    status.remoteSizeBytes != null && status.remoteSizeBytes > 0L ->
                        "${stringResource(R.string.settings_offline_net_not_loaded)} · ca. ${formatSizeMb(status.remoteSizeBytes)}"
                    else ->
                        "${stringResource(R.string.settings_offline_net_not_loaded)} · ca. ${formatApproxMb(status.approxSizeMb)}"
                }

                val statusColor = when {
                    status.isDownloading -> MaterialTheme.colorScheme.primary
                    status.isDownloaded -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (status.isDownloaded || status.isDownloading) FontWeight.Medium else FontWeight.Normal,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                status.isDownloading -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(22.dp)
                            .testTag("offline_net_spinner_${status.regionId}")
                    )
                }
                status.isDownloaded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("offline_net_refresh_${status.regionId}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.settings_offline_net_refresh_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("offline_net_delete_${status.regionId}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.settings_offline_net_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                else -> {
                    FilledTonalButton(
                        onClick = onDownload,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("offline_net_download_${status.regionId}")
                    ) {
                        Text(
                            text = stringResource(R.string.settings_offline_net_load),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (status.isDownloading) {
            if (status.totalBytes > 0L) {
                val progress = (status.downloadedBytes.toFloat() / status.totalBytes.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                )
            } else {
                LinearProgressIndicator(
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }

        if (!status.errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_net_error_${status.regionId}")
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = "Fehler: ${status.errorMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun formatSizeMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 100.0) {
        String.format(Locale.GERMAN, "%.0f MB", mb)
    } else if (mb >= 1.0) {
        String.format(Locale.GERMAN, "%.1f MB", mb)
    } else {
        val kb = (bytes / 1024L).coerceAtLeast(1L)
        "$kb KB"
    }
}

private fun formatApproxMb(mb: Double): String {
    return if (mb >= 10.0) {
        String.format(Locale.GERMAN, "%.0f MB", mb)
    } else {
        String.format(Locale.GERMAN, "%.1f MB", mb)
    }
}
