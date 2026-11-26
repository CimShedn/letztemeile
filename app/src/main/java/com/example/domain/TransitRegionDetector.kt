package com.example.domain

import com.example.data.remote.OverpassTransitRepository
import com.example.model.GeoPoint
import com.example.model.PreferredTransitApp
import com.example.model.TransitRegionRecommendation

/**
 * Detects the active public transit region (all 19 DACH regional packages + DB Navigator fallback)
 * exclusively from real GPS coordinates.
 *
 * Priority order is delegated directly to [OverpassTransitRepository.REGIONAL_PACKAGES]:
 * 1. The 9 metro areas (MVV, VBB, HVV, RMV, VVS, VRS, VRR, VGN, SACHSEN)
 * 2. The 8 German area regions (NDS_BREMEN, NORD, WESTFALEN, RLP_SL, SUEDWEST, MITTE, SCHWABEN, BAYERN_NORD)
 * 3. Country-wide packages (AT with two sub-boxes, CH)
 */
object TransitRegionDetector {

    fun detectRegion(point: GeoPoint): TransitRegionRecommendation {
        val pkg = OverpassTransitRepository.findPackageForPoint(point)
        if (pkg != null) {
            return TransitRegionRecommendation(
                regionKey = pkg.regionId,
                regionDisplayName = pkg.displayName,
                recommendedApp = pkg.defaultTransitApp
            )
        }

        return TransitRegionRecommendation(
            regionKey = "DB",
            regionDisplayName = "Deutschland / International",
            recommendedApp = PreferredTransitApp.DB_NAVIGATOR
        )
    }

    /**
     * Returns true if [app] is already an appropriate local transit app for [regionKey]
     * (e.g. MVGO and MVV-App are both valid inside MVV; BVG Jelbi and VBB are both valid inside VBB).
     */
    fun appMatchesRegion(app: PreferredTransitApp, regionKey: String): Boolean {
        val normalizedId = OverpassTransitRepository.regionIdForDetectedRegionKey(regionKey)
        return OverpassTransitRepository.regionIdForTransitApp(app) == normalizedId
    }

    fun recommendationForApp(app: PreferredTransitApp): TransitRegionRecommendation {
        val regionId = OverpassTransitRepository.regionIdForTransitApp(app)
        val pkg = OverpassTransitRepository.getPackageForRegionId(regionId)
        return if (pkg != null) {
            TransitRegionRecommendation(
                regionKey = pkg.regionId,
                regionDisplayName = pkg.displayName,
                recommendedApp = app
            )
        } else {
            TransitRegionRecommendation(
                regionKey = "DB",
                regionDisplayName = "Deutschland / International",
                recommendedApp = app
            )
        }
    }
}
