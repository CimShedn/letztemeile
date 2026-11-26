package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.config.AppLegalConfig

/**
 * Permanently visible, non-intrusive ODbL & tile-provider map attribution chip
 * ("© OSM · OpenFreeMap") with a 48dp accessible touch target.
 * Tapping opens an attribution dialog with clickable browser links to OpenStreetMap Copyright,
 * OpenFreeMap, OpenMapTiles, and the GitHub transit-data repository.
 */
@Composable
fun MapAttributionPill(
    modifier: Modifier = Modifier,
    onOpenLegalInfo: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val cdText = stringResource(R.string.map_attribution_cd)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = cdText }
            .clickable(role = Role.Button) { showDialog = true }
            .testTag("map_attribution_badge")
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
            shadowElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(R.string.map_attribution_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    if (showDialog) {
        MapAttributionDialog(
            onOpenLegalInfo = onOpenLegalInfo,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun MapAttributionDialog(
    onOpenLegalInfo: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Karten- & Datenquellen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Diese Karte verwendet Vektorkacheln von OpenFreeMap (OpenMapTiles-Schema) sowie Kartendaten und ÖPNV-Linien von OpenStreetMap-Mitwirkenden (ODbL).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AttributionLinkItem(
                    icon = Icons.Filled.Map,
                    title = "© OpenStreetMap-Mitwirkende",
                    subtitle = "Open Database License (ODbL) · openstreetmap.org/copyright",
                    testTag = "map_attribution_link_osm",
                    onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.OSM_COPYRIGHT_URL) }
                )

                AttributionLinkItem(
                    icon = Icons.Filled.Public,
                    title = "© OpenFreeMap",
                    subtitle = "Kartenkacheln, Styles & Schriften · openfreemap.org",
                    testTag = "map_attribution_link_openfreemap",
                    onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.OPENFREEMAP_URL) }
                )

                AttributionLinkItem(
                    icon = Icons.Filled.Public,
                    title = "© OpenMapTiles",
                    subtitle = "Vektorkachel-Schema · openmaptiles.org",
                    testTag = "map_attribution_link_openmaptiles",
                    onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.OPENMAPTILES_URL) }
                )

                AttributionLinkItem(
                    icon = Icons.Filled.Code,
                    title = "ÖPNV-JSON-Daten (GitHub)",
                    subtitle = AppLegalConfig.GITHUB_REPO_URL,
                    testTag = "map_attribution_link_github",
                    onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.GITHUB_REPO_URL) }
                )

                if (onOpenLegalInfo != null) {
                    AttributionLinkItem(
                        icon = Icons.Filled.Gavel,
                        title = stringResource(R.string.legal_sheet_title),
                        subtitle = "Haftungsausschluss, Datenschutz & Kontakt",
                        testTag = "map_attribution_open_legal",
                        onClick = {
                            onDismiss()
                            onOpenLegalInfo()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("map_attribution_close_button")
            ) {
                Text("Schließen")
            }
        }
    )
}

@Composable
private fun AttributionLinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.legal_open_link_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
