package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BatteryRangeFallbackSuggestion
import com.example.model.BatteryRoundTripAssessment
import com.example.model.BatteryTrafficLight
import com.example.model.ScooterProfile
import com.example.ui.theme.BatteryCriticalContainerDark
import com.example.ui.theme.BatteryCriticalContainerLight
import com.example.ui.theme.BatteryCriticalDark
import com.example.ui.theme.BatteryCriticalLight
import com.example.ui.theme.BatterySafeContainerDark
import com.example.ui.theme.BatterySafeContainerLight
import com.example.ui.theme.BatterySafeDark
import com.example.ui.theme.BatterySafeLight
import com.example.ui.theme.BatteryTightContainerDark
import com.example.ui.theme.BatteryTightContainerLight
import com.example.ui.theme.BatteryTightDark
import com.example.ui.theme.BatteryTightLight
import com.example.ui.theme.lineColorForName
import com.example.ui.theme.lineTextColorForName
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TransitLineBadge(
    lineName: String,
    modifier: Modifier = Modifier
) {
    val bgColor = lineColorForName(lineName)
    val fgColor = lineTextColorForName(lineName)
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = lineName.replace(" ", ""),
            color = fgColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun BatteryQuickChip(
    profile: ScooterProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val accent = when {
        profile.currentBatteryPercent >= 40 -> if (isDark) BatterySafeDark else BatterySafeLight
        profile.currentBatteryPercent >= 20 -> if (isDark) BatteryTightDark else BatteryTightLight
        else -> if (isDark) BatteryCriticalDark else BatteryCriticalLight
    }
    val batteryIcon = when {
        profile.currentBatteryPercent >= 80 -> Icons.Filled.BatteryFull
        profile.currentBatteryPercent >= 50 -> Icons.Filled.Battery5Bar
        profile.currentBatteryPercent >= 20 -> Icons.Filled.Battery3Bar
        else -> Icons.Filled.Battery0Bar
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        modifier = modifier.testTag("battery_quick_chip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = batteryIcon,
                contentDescription = stringResource(R.string.scooter_battery_quick_title),
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${profile.currentBatteryPercent}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.GERMAN, " · %.0f km", profile.currentAvailableRangeKm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BatteryTrafficLightBanner(
    assessment: BatteryRoundTripAssessment,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val (fg, bg) = when (assessment.status) {
        BatteryTrafficLight.SAFE -> if (isDark) {
            BatterySafeDark to BatterySafeContainerDark
        } else {
            BatterySafeLight to BatterySafeContainerLight
        }
        BatteryTrafficLight.TIGHT -> if (isDark) {
            BatteryTightDark to BatteryTightContainerDark
        } else {
            BatteryTightLight to BatteryTightContainerLight
        }
        BatteryTrafficLight.CRITICAL -> if (isDark) {
            BatteryCriticalDark to BatteryCriticalContainerDark
        } else {
            BatteryCriticalLight to BatteryCriticalContainerLight
        }
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.75.dp, fg.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assessment.summaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = assessment.detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.9f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ElectricScooter,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Optional, explicit battery-range fallback prompt. Shown ONLY when the nearest station of the
 * user's strictly selected transit mode exceeds remaining scooter range — gives the user a
 * conscious one-tap switch without ever auto-mixing other transit modes into the list.
 */
@Composable
fun BatteryRangeFallbackBanner(
    suggestion: BatteryRangeFallbackSuggestion,
    onSwitchClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                )
                Text(
                    text = suggestion.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            FilledTonalButton(
                onClick = onSwitchClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("${testTag}_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = suggestion.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Unobtrusive, tappable advisory badge for bus/tram/subway legs (e-scooter battery safety rules).
 * Bus routes are NEVER filtered out — tapping opens a concise info dialog with practical carry-bag tips.
 */
@Composable
fun BusCarryWarningPill(
    expanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val fg = if (isDark) BatteryTightDark else BatteryTightLight
    val bg = if (isDark) BatteryTightContainerDark else BatteryTightContainerLight
    var showInfoDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Surface(
        onClick = { showInfoDialog = true },
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.75.dp, fg.copy(alpha = 0.42f)),
        modifier = modifier.testTag("bus_carry_warning_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (expanded) 8.dp else 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = stringResource(R.string.route_bus_warning_title),
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (expanded) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.route_bus_warning_title)} (Tippen für Infos)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                    Text(
                        text = stringResource(R.string.route_bus_warning_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = fg.copy(alpha = 0.9f)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Mitnahme prüfen ⓘ",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showInfoDialog) {
        BusCarryInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
fun BusCarryInfoDialog(
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text(
                text = "E-Scooter-Mitnahme im ÖPNV",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Viele Verkehrsbetriebe (z. B. MVG/MVV, BVG, HVV) schließen die Mitnahme von E-Scootern in Bussen, U-Bahnen und Trams wegen Sicherheitsvorgaben zu Lithium-Ionen-Akkus aktuell aus. In S-Bahnen und Regionalzügen (DB) bleibt die Mitnahme regulär erlaubt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Praxis-Tipp: Viele Fahrer akzeptieren zusammengeklappte Scooter bei ausreichend Platz trotzdem – und verstaut in einer kompakten Transport- oder Tragetasche gilt der Scooter häufig als reguläres Handgepäckstück.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("bus_carry_dialog_ok")
            ) {
                Text("Verstanden")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryQuickAdjustSheet(
    profile: ScooterProfile,
    onBatteryChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.scooter_battery_quick_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = profile.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${profile.currentBatteryPercent} %",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = profile.currentBatteryPercent.toFloat(),
                onValueChange = { onBatteryChanged(it.roundToInt()) },
                valueRange = 5f..100f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("quick_battery_slider")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(25, 50, 75, 100).forEach { pct ->
                    Surface(
                        color = if (profile.currentBatteryPercent == pct) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onBatteryChanged(pct) }
                            .testTag("battery_preset_$pct")
                    ) {
                        Text(
                            text = "$pct %",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (profile.currentBatteryPercent == pct) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Reale Restreichweite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.GERMAN, "%.1f km", profile.currentAvailableRangeKm),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Sicher pro Weg (Hin+Rück)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.GERMAN, "bis %.1f km", (profile.currentAvailableRangeKm * 0.8) / 2.0),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
