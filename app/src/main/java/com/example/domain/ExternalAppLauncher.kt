package com.example.domain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.model.GeoPoint
import com.example.model.PreferredMapsApp
import com.example.model.PreferredTransitApp
import java.util.Locale

data class ExternalLaunchResult(
    val launched: Boolean,
    val message: String
)

/**
 * Simple, zero-API-dependency launcher for the user's preferred Maps and Transit apps.
 * - Maps apps receive direct origin/destination coordinates via standard URI/Intent where supported.
 * - Transit apps open directly (or via web fallback) AND copy the discovered station names to the
 *   clipboard so the user can paste them immediately into any regional transit app.
 */
object ExternalAppLauncher {

    fun openMapsApp(
        context: Context,
        preferredMapsApp: PreferredMapsApp,
        origin: GeoPoint,
        destination: GeoPoint,
        destinationLabel: String,
        originLabel: String? = null
    ): ExternalLaunchResult {
        val encodedLabel = Uri.encode(destinationLabel)
        val lat1 = String.format(Locale.US, "%.6f", origin.latitude)
        val lon1 = String.format(Locale.US, "%.6f", origin.longitude)
        val lat2 = String.format(Locale.US, "%.6f", destination.latitude)
        val lon2 = String.format(Locale.US, "%.6f", destination.longitude)
        val legSummary = if (!originLabel.isNullOrBlank()) {
            "$originLabel → $destinationLabel"
        } else {
            destinationLabel
        }

        // 1. Try preferred Maps app with direct route/geo URI
        val primaryIntent = when (preferredMapsApp) {
            PreferredMapsApp.GOOGLE_MAPS -> {
                val url = "https://www.google.com/maps/dir/?api=1&origin=$lat1,$lon1&destination=$lat2,$lon2&travelmode=bicycling"
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(preferredMapsApp.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            PreferredMapsApp.OSMAND, PreferredMapsApp.HERE_WEGO -> {
                val geoUri = Uri.parse("geo:$lat2,$lon2?q=$lat2,$lon2($encodedLabel)")
                Intent(Intent.ACTION_VIEW, geoUri).apply {
                    preferredMapsApp.packageName?.let { setPackage(it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            PreferredMapsApp.SYSTEM_DEFAULT -> {
                val geoUri = Uri.parse("geo:$lat2,$lon2?q=$lat2,$lon2($encodedLabel)")
                Intent(Intent.ACTION_VIEW, geoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        if (tryStartActivity(context, primaryIntent)) {
            return ExternalLaunchResult(true, "${preferredMapsApp.displayName}: $legSummary")
        }

        // 2. Fallback: generic geo URI without package restriction
        val genericGeo = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat2,$lon2?q=$lat2,$lon2($encodedLabel)")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStartActivity(context, genericGeo)) {
            return ExternalLaunchResult(true, "Karten-App: $legSummary")
        }

        // 3. Final fallback: Google Maps web directions in browser
        val webFallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$lat1,$lon1&destination=$lat2,$lon2&travelmode=bicycling")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStartActivity(context, webFallback)) {
            return ExternalLaunchResult(true, "Karte im Browser: $legSummary")
        }

        return ExternalLaunchResult(false, "Keine Karten-App auf dem Gerät gefunden.")
    }

    fun openTransitApp(
        context: Context,
        preferredTransitApp: PreferredTransitApp,
        originStationName: String,
        destinationStationName: String,
        transferStationName: String? = null
    ): ExternalLaunchResult {
        val routeClipText = if (transferStationName != null &&
            !transferStationName.equals(originStationName, ignoreCase = true) &&
            !transferStationName.equals(destinationStationName, ignoreCase = true)
        ) {
            "$originStationName → $transferStationName → $destinationStationName"
        } else {
            "$originStationName → $destinationStationName"
        }

        copyToClipboard(context, "LetzteMeile Haltestellen", routeClipText)

        // 1. Try launching the installed preferred transit app directly
        preferredTransitApp.packageName?.let { pkg ->
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null && tryStartActivity(context, launchIntent)) {
                return ExternalLaunchResult(
                    launched = true,
                    message = "${preferredTransitApp.displayName} geöffnet · Zwischenablage: $routeClipText"
                )
            }
        }

        // 2. Web fallback with prefilled start/destination for DB Navigator or regional web planners
        val webUrl = buildWebFallbackUrl(preferredTransitApp, originStationName, destinationStationName)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(context, browserIntent)) {
            return ExternalLaunchResult(
                launched = true,
                message = "${preferredTransitApp.displayName} nicht installiert – im Browser geöffnet · Kopiert: $routeClipText"
            )
        }

        return ExternalLaunchResult(
            launched = false,
            message = "Haltestellen kopiert: $routeClipText"
        )
    }

    /**
     * Launches the currently selected regional transit app (or a web/app chooser fallback)
     * with [Intent.FLAG_ACTIVITY_NEW_TASK] and [Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT]
     * so it opens side-by-side when in Android Splitscreen / Multi-Window mode.
     */
    fun openTransitAppAdjacent(
        context: Context,
        preferredTransitApp: PreferredTransitApp,
        originStationName: String,
        destinationStationName: String
    ): ExternalLaunchResult {
        val adjacentFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT

        // 1. Try launching the installed preferred regional transit app adjacent
        preferredTransitApp.packageName?.let { pkg ->
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(adjacentFlags)
            }
            if (launchIntent != null && tryStartActivity(context, launchIntent)) {
                return ExternalLaunchResult(
                    launched = true,
                    message = "${preferredTransitApp.displayName} gestartet"
                )
            }
        }

        // 2. Fallback: open regional web timetable via app chooser with adjacent flags
        val webUrl = buildWebFallbackUrl(preferredTransitApp, originStationName, destinationStationName)
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(adjacentFlags)
        }
        val chooserIntent = Intent.createChooser(viewIntent, preferredTransitApp.displayName).apply {
            addFlags(adjacentFlags)
        }
        if (tryStartActivity(context, chooserIntent) || tryStartActivity(context, viewIntent)) {
            return ExternalLaunchResult(
                launched = true,
                message = "${preferredTransitApp.displayName} nicht installiert – im Browser geöffnet"
            )
        }

        return ExternalLaunchResult(
            launched = false,
            message = "${preferredTransitApp.displayName} konnte nicht geöffnet werden."
        )
    }

    fun copyTextToClipboard(context: Context, label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildWebFallbackUrl(
        app: PreferredTransitApp,
        origin: String,
        destination: String
    ): String {
        val encOrigin = Uri.encode(origin)
        val encDest = Uri.encode(destination)
        return when (app) {
            PreferredTransitApp.DB_NAVIGATOR ->
                "https://www.bahn.de/buchung/fahrplan/suche?S=$encOrigin&Z=$encDest"
            PreferredTransitApp.MVV_APP ->
                "https://efa.mvv-muenchen.de/"
            PreferredTransitApp.MVGO ->
                "https://www.mvg.de/verbindungen.html"
            PreferredTransitApp.BVG_JELBI ->
                "https://www.bvg.de/de/verbindungen/verbindungssuche"
            PreferredTransitApp.HVV_SWITCH ->
                "https://www.hvv.de/de/fahrplaene/abruf-fahrplaninfos/fahrplanauskunft"
            PreferredTransitApp.VBB_APP ->
                "https://fahrinfo.vbb.de/"
            PreferredTransitApp.RMV_GO ->
                "https://www.rmv.de/auskunft/bin/jp/query.exe/dn"
            PreferredTransitApp.VVS_MOBIL ->
                "https://www.vvs.de/fahrplan"
            PreferredTransitApp.VRS_KVB ->
                "https://www.vrs.de/fahren/fahrplanauskunft"
            PreferredTransitApp.VRR_APP ->
                "https://www.vrr.de/de/fahrplan-mobilitaet/fahrplanauskunft/"
            PreferredTransitApp.VGN_APP ->
                "https://www.vgn.de/verbindungen/"
            PreferredTransitApp.SACHSEN_DVB ->
                "https://www.dvb.de/de-de/fahrplan/verbindungsauskunft"
            PreferredTransitApp.NDS_BREMEN,
            PreferredTransitApp.NORD,
            PreferredTransitApp.WESTFALEN,
            PreferredTransitApp.RLP_SL,
            PreferredTransitApp.MITTE,
            PreferredTransitApp.SCHWABEN,
            PreferredTransitApp.BAYERN_NORD ->
                "https://www.bahn.de/buchung/fahrplan/suche?S=$encOrigin&Z=$encDest"
            PreferredTransitApp.SUEDWEST ->
                "https://www.bwegt.de/fahrplanauskunft"
            PreferredTransitApp.OEBB_SCOTTY ->
                "https://fahrplan.oebb.at/"
            PreferredTransitApp.SBB_MOBILE ->
                "https://www.sbb.ch/de?stops=%5B%7B%22label%22:%22$encOrigin%22%7D,%7B%22label%22:%22$encDest%22%7D%5D"
        }
    }

    private fun copyToClipboard(context: Context, label: String, text: String) {
        copyTextToClipboard(context, label, text)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
