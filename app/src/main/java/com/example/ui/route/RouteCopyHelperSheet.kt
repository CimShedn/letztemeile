package com.example.ui.route

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ExternalAppLauncher
import com.example.model.GeographicScooterRoute
import com.example.model.PreferredTransitApp
import com.example.model.SearchLocation
import kotlinx.coroutines.delay
import java.util.Locale

private val COORDS_REGEX = Regex("""^-?\d+\.\d+,\s*-?\d+\.\d+$""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCopyHelperSheet(
    route: GeographicScooterRoute,
    preferredTransitApp: PreferredTransitApp,
    onDismiss: () -> Unit,
    onLaunchFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    // Observe configuration so entering/exiting Splitscreen updates isInMultiWindowMode immediately
    val configuration = LocalConfiguration.current
    val isInMultiWindow = remember(context, configuration) {
        context.findActivity()?.isInMultiWindowMode == true
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copiedKey by remember { mutableStateOf<String?>(null) }
    var copyNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(copiedKey, copyNonce) {
        if (copiedKey != null) {
            delay(2000L)
            copiedKey = null
        }
    }

    fun performCopy(rowKey: String, label: String, text: String) {
        ExternalAppLauncher.copyTextToClipboard(context, label, text)
        copiedKey = rowKey
        copyNonce++
        Toast.makeText(context, "$label kopiert", Toast.LENGTH_SHORT).show()
    }

    val startAddress = remember(route.startLocation) {
        formatCopyableAddress(route.startLocation)
    }
    val destAddress = remember(route.destinationLocation) {
        formatCopyableAddress(route.destinationLocation)
    }
    val originStationName = route.originStation.station.name.trim()
    val destinationStationName = route.destinationStation.station.name.trim()
    val hasStations = originStationName.isNotBlank() || destinationStationName.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("route_copy_helper_sheet")
        ) {
            // Compact Header (Splitscreen-friendly height)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Splitscreen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.route_copy_helper_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.route_copy_helper_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("route_copy_helper_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.route_copy_helper_close_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Start-Adresse
            CopyHelperRow(
                icon = Icons.Filled.Place,
                label = stringResource(R.string.route_copy_helper_start_address),
                value = startAddress,
                isCopied = copiedKey == "start_addr",
                onCopy = {
                    performCopy(
                        rowKey = "start_addr",
                        label = context.getString(R.string.route_copy_helper_start_address),
                        text = startAddress
                    )
                },
                testTag = "copy_helper_row_start_address"
            )

            // 2. Ziel-Adresse
            CopyHelperRow(
                icon = Icons.Filled.Flag,
                label = stringResource(R.string.route_copy_helper_dest_address),
                value = destAddress,
                isCopied = copiedKey == "dest_addr",
                onCopy = {
                    performCopy(
                        rowKey = "dest_addr",
                        label = context.getString(R.string.route_copy_helper_dest_address),
                        text = destAddress
                    )
                },
                testTag = "copy_helper_row_dest_address"
            )

            // 3. Optional Start & Destination Stations (ideal for transit apps that search by stop)
            if (hasStations) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = stringResource(R.string.route_copy_helper_stations_section),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (originStationName.isNotBlank()) {
                    CopyHelperRow(
                        icon = Icons.Filled.Train,
                        label = stringResource(R.string.route_copy_helper_start_station),
                        value = originStationName,
                        isCopied = copiedKey == "start_station",
                        onCopy = {
                            performCopy(
                                rowKey = "start_station",
                                label = context.getString(R.string.route_copy_helper_start_station),
                                text = originStationName
                            )
                        },
                        testTag = "copy_helper_row_start_station"
                    )
                }

                if (destinationStationName.isNotBlank()) {
                    CopyHelperRow(
                        icon = Icons.Filled.Train,
                        label = stringResource(R.string.route_copy_helper_dest_station),
                        value = destinationStationName,
                        isCopied = copiedKey == "dest_station",
                        onCopy = {
                            performCopy(
                                rowKey = "dest_station",
                                label = context.getString(R.string.route_copy_helper_dest_station),
                                text = destinationStationName
                            )
                        },
                        testTag = "copy_helper_row_dest_station"
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Primary action: Open regional ÖPNV-App adjacent (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT)
            Button(
                onClick = {
                    val result = ExternalAppLauncher.openTransitAppAdjacent(
                        context = context,
                        preferredTransitApp = preferredTransitApp,
                        originStationName = originStationName,
                        destinationStationName = destinationStationName
                    )
                    if (!result.launched) {
                        onLaunchFeedback(result.message)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("route_copy_helper_open_adjacent_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${preferredTransitApp.displayName} daneben öffnen",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Smart Splitscreen tip shown only when NOT yet in Android Multi-Window mode
            if (!isInMultiWindow) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                        .testTag("route_copy_helper_splitscreen_tip")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.route_copy_helper_splitscreen_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CopyHelperRow(
    icon: ImageVector,
    label: String,
    value: String,
    isCopied: Boolean,
    onCopy: () -> Unit,
    testTag: String
) {
    val borderColor = if (isCopied) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCopied) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(if (isCopied) 1.5.dp else 0.75.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onCopy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = if (isCopied) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("${testTag}_button")
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = if (isCopied) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(
                        if (isCopied) {
                            R.string.route_copy_helper_copied_action
                        } else {
                            R.string.route_copy_helper_copy_action
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatCopyableAddress(location: SearchLocation): String {
    val cleanName = location.name.trim()
    val isGenericPin = cleanName.isBlank() ||
        cleanName.equals("Kartenpunkt", ignoreCase = true) ||
        cleanName.equals("Mein Standort (GPS)", ignoreCase = true) ||
        cleanName.startsWith("Punkt nahe ", ignoreCase = true)

    if (isGenericPin) {
        return String.format(
            Locale.US,
            "%.5f, %.5f",
            location.location.latitude,
            location.location.longitude
        )
    }

    // Extract clean city/locality tokens from subtitle (dropping raw coordinates & generic prefixes)
    val subtitleParts = location.subtitle
        .split("·", ",")
        .map { it.trim() }
        .filter { part ->
            part.isNotBlank() &&
                !COORDS_REGEX.matches(part) &&
                !part.startsWith("Kartenpunkt", ignoreCase = true) &&
                !cleanName.contains(part, ignoreCase = true)
        }
        .distinctBy { it.lowercase(Locale.GERMAN) }
        .take(2)

    return if (subtitleParts.isNotEmpty()) {
        "$cleanName, ${subtitleParts.joinToString(", ")}"
    } else {
        cleanName
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
