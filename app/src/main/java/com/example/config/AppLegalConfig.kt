package com.example.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Zentrale Konfiguration für Projekt-URLs, Kontaktangaben und Open-Source-Quellenangaben.
 *
 * Hinweis: An [GITHUB_REPO_URL] kann später bei Bedarf einfach der konkrete Repository-Name
 * angehängt werden (z. B. `https://github.com/CimShedn/letzte-meile`).
 */
object AppLegalConfig {

    // ---- Projekt & Kontakt ----
    const val GITHUB_REPO_URL = "https://github.com/CimShedn"
    const val CONTACT_EMAIL = "thegolgatarian@gmail.com"

    // ---- Externe Datenquellen & Pflicht-Links ----
    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
    const val ODBL_LICENSE_URL = "https://opendatacommons.org/licenses/odbl/"
    const val OPENFREEMAP_URL = "https://openfreemap.org/"
    const val OPENMAPTILES_URL = "https://openmaptiles.org/"
    const val PHOTON_URL = "https://photon.komoot.io/"
    const val OSRM_URL = "https://project-osrm.org/"

    data class OpenSourceLibrary(
        val name: String,
        val detail: String,
        val license: String,
        val url: String
    )

    val OPEN_SOURCE_LIBRARIES: List<OpenSourceLibrary> = listOf(
        OpenSourceLibrary(
            name = "MapLibre Native Android SDK",
            detail = "org.maplibre.gl:android-sdk-opengl · Vektorkarten-Renderer",
            license = "BSD-2-Clause",
            url = "https://github.com/maplibre/maplibre-native"
        ),
        OpenSourceLibrary(
            name = "Jetpack Compose & Material 3",
            detail = "androidx.compose.* · Benutzeroberfläche & Icons",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/compose"
        ),
        OpenSourceLibrary(
            name = "AndroidX (Core, Navigation, Room, DataStore, Lifecycle)",
            detail = "androidx.* · Navigation, lokale Datenbank & Einstellungen",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx"
        ),
        OpenSourceLibrary(
            name = "Kotlin & Kotlinx Coroutines",
            detail = "org.jetbrains.kotlinx · Asynchrone Verarbeitung",
            license = "Apache 2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines"
        ),
        OpenSourceLibrary(
            name = "OkHttp",
            detail = "com.squareup.okhttp3 · HTTP-Client für Karten- & ÖPNV-Daten",
            license = "Apache 2.0",
            url = "https://square.github.io/okhttp/"
        ),
        OpenSourceLibrary(
            name = "Retrofit",
            detail = "com.squareup.retrofit2 · REST-Client (Photon-Adresssuche)",
            license = "Apache 2.0",
            url = "https://square.github.io/retrofit/"
        ),
        OpenSourceLibrary(
            name = "Moshi",
            detail = "com.squareup.moshi · JSON-Serialisierung",
            license = "Apache 2.0",
            url = "https://github.com/square/moshi"
        ),
        OpenSourceLibrary(
            name = "Google Play Services Location",
            detail = "com.google.android.gms:play-services-location · GPS-Standortdienst",
            license = "Android SDK License",
            url = "https://developers.google.com/android/guides/overview"
        )
    )

    fun openExternalUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Kein Browser zum Öffnen des Links gefunden.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openEmailAddress(context: Context, email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Keine E-Mail-App zum Öffnen gefunden.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
