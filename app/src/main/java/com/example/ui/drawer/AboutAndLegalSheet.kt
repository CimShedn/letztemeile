package com.example.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.config.AppLegalConfig
import com.example.data.remote.OverpassTransitRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutLetzteMeileSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalRegionCount = OverpassTransitRepository.REGIONAL_PACKAGES.size

    BackHandler(onBack = onDismiss)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            modifier = Modifier.testTag("about_back_button")
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
                                text = stringResource(R.string.legal_sheet_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Letzte Meile · E-Scooter & ÖPNV (DACH)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("about_modal_sheet")
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Letzte Meile kombiniert die erste und letzte Meile mit dem E-Scooter mit regionalen ÖPNV-Netzen im DACH-Raum – minimalistisch, lokal zwischengespeichert und ohne Nutzerkonto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. Haftungsausschluss (Disclaimer)
            LegalSectionCard(
                icon = Icons.Filled.WarningAmber,
                title = stringResource(R.string.legal_section_disclaimer),
                testTag = "legal_section_disclaimer"
            ) {
                LegalBulletPoint(
                    heading = "Unverbindliche Orientierungshilfe",
                    body = "Alle angezeigten Routen, Entfernungen, Fahrzeiten und Karteninhalte dienen ausschließlich der unverbindlichen Orientierung."
                )
                LegalBulletPoint(
                    heading = "E-Scooter-Reichweite (Schätzwert)",
                    body = "Die Reichweiten- und Hin-/Rückweg-Berechnung ist ein reiner Schätzwert. Die tatsächliche Reichweite hängt stark von Fahrergewicht, Steigungen, Außentemperatur, Wind, Untergrund, Reifendruck und Akkuzustand ab."
                )
                LegalBulletPoint(
                    heading = "ÖPNV-Daten aus OpenStreetMap",
                    body = "Linienverläufe und Haltestellen basieren auf OpenStreetMap-Daten und können unvollständig, ungenau oder veraltet sein. Es gelten stets die Straßenverkehrsordnung (StVO) sowie die offiziellen Fahrpläne, Tarif- und Mitnahmeregeln der jeweiligen Verkehrsbetriebe."
                )
                LegalBulletPoint(
                    heading = "Unabhängigkeitshinweis & Marken",
                    body = "„Letzte Meile“ ist ein unabhängiges Projekt und steht in keiner Verbindung zu den genannten Verkehrsverbünden und Unternehmen (z. B. DB, MVV, MVG, BVG, VBB, HVV, RMV, VVS, VRS, KVB, VRR, VGN, DVB, LVB, VOR, ÖBB, SBB). Alle genannten Bezeichnungen sind Marken der jeweiligen Rechteinhaber."
                )
            }

            // 2. Datenschutzerklärung (DSGVO)
            LegalSectionCard(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.legal_section_privacy),
                testTag = "legal_section_privacy"
            ) {
                LegalBulletPoint(
                    heading = "Kein Tracking, keine Werbung, kein Konto",
                    body = "Die App benötigt kein Nutzerkonto, enthält keine Werbung, nutzt keine Analyse- oder Tracking-Dienste und gibt keine personenbezogenen Daten an Dritte zu Werbezwecken weiter. Scooter-Profil, Favoriten, Einstellungen und Offline-Pakete werden ausschließlich lokal auf deinem Gerät gespeichert."
                )
                LegalBulletPoint(
                    heading = "GPS & Standortdaten",
                    body = "Dein Standort wird (nur nach deiner Freigabe) über den Android-Standortdienst ermittelt und primär lokal für die Anzeige deiner Position auf der Karte sowie die automatische DACH-Regionserkennung verwendet. Koordinaten werden nur dann aktiv an externe Dienste übertragen, wenn du eine Scooter-Route ab bzw. zu einem Punkt berechnest (Start-/Zielkoordinaten an OSRM) oder bei der Adresssuche (aktueller Kartenmittelpunkt als Such-Bias an Photon)."
                )
                LegalBulletPoint(
                    heading = "Server-Anfragen & IP-Adresse",
                    body = "Bei Internetverbindungen wird technisch bedingt die IP-Adresse deines Geräts an die jeweils genutzten Server übermittelt:\n" +
                        "• Kartenkacheln, Styles & Schriften: OpenFreeMap (tiles.openfreemap.org)\n" +
                        "• Regionale ÖPNV-JSON-Pakete: GitHub bzw. der konfigurierte Datenserver\n" +
                        "• Adresssuche (Geocoding): Photon by Komoot (photon.komoot.io)\n" +
                        "• E-Scooter-/Rad-Routing: FOSSGIS e.V. (routing.openstreetmap.de) / OSRM (router.project-osrm.org)\n" +
                        "• ÖPNV-Fallback außerhalb der $totalRegionCount Regionen: Overpass API (OpenStreetMap)\n" +
                        "Wenn du externe Karten- oder ÖPNV-Apps öffnest, gelten die Datenschutzbestimmungen des jeweiligen Anbieters."
                )
            }

            // 3. Datenquellen & Lizenzen
            LegalSectionCard(
                icon = Icons.Filled.Public,
                title = stringResource(R.string.legal_section_sources),
                testTag = "legal_section_sources"
            ) {
                Text(
                    text = "Karten-, Routing- und ÖPNV-Daten basieren auf OpenStreetMap-Daten unter der Open Database License (ODbL). Die aufbereiteten regionalen ÖPNV-JSON-Dateien werden im zugehörigen GitHub-Repository zugänglich gemacht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegalLinkRow(
                        icon = Icons.Filled.Map,
                        title = "© OpenStreetMap-Mitwirkende (ODbL)",
                        subtitle = AppLegalConfig.OSM_COPYRIGHT_URL,
                        testTag = "legal_link_osm",
                        onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.OSM_COPYRIGHT_URL) }
                    )
                    LegalLinkRow(
                        icon = Icons.Filled.Public,
                        title = "OpenFreeMap (Kartenkacheln & Styles)",
                        subtitle = AppLegalConfig.OPENFREEMAP_URL,
                        testTag = "legal_link_openfreemap",
                        onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.OPENFREEMAP_URL) }
                    )
                    LegalLinkRow(
                        icon = Icons.Filled.Code,
                        title = "GitHub-Repository & ÖPNV-JSON-Daten",
                        subtitle = AppLegalConfig.GITHUB_REPO_URL,
                        testTag = "legal_link_github",
                        onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.GITHUB_REPO_URL) }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    text = "Verwendete Open-Source-Bibliotheken & SDKs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLegalConfig.OPEN_SOURCE_LIBRARIES.forEach { lib ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { AppLegalConfig.openExternalUrl(context, lib.url) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = lib.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lib.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = lib.license,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Kontakt & Projekt
            LegalSectionCard(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.legal_section_contact_project),
                testTag = "legal_section_contact_project"
            ) {
                Text(
                    text = "Letzte Meile ist ein privates, nicht-kommerzielles Open-Source-Projekt. Bei Fragen, Fehlermeldungen oder rechtlichen Hinweisen erreichst du mich jederzeit per E-Mail oder über GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LegalLinkRow(
                    icon = Icons.Filled.MailOutline,
                    title = "E-Mail-Kontakt",
                    subtitle = AppLegalConfig.CONTACT_EMAIL,
                    testTag = "legal_contact_email",
                    onClick = { AppLegalConfig.openEmailAddress(context, AppLegalConfig.CONTACT_EMAIL) }
                )

                LegalLinkRow(
                    icon = Icons.Filled.Code,
                    title = "Projekt & Quellcode (GitHub)",
                    subtitle = AppLegalConfig.GITHUB_REPO_URL,
                    testTag = "legal_contact_github",
                    onClick = { AppLegalConfig.openExternalUrl(context, AppLegalConfig.GITHUB_REPO_URL) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LegalSectionCard(
    icon: ImageVector,
    title: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun LegalBulletPoint(
    heading: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegalLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
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
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.legal_open_link_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
