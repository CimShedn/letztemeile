package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettings
import com.example.model.MapTransitMode
import com.example.model.PreferredMapsApp
import com.example.model.PreferredTransitApp
import com.example.model.ScooterProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.letzteMeileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "letztemeile_preferences"
)

data class MapDisplayPreferences(
    val isMinimalScooterMode: Boolean = true,
    val isProminentLines: Boolean = false,
    val isNightMapOverride: Boolean? = null,
    val transitMode: MapTransitMode = MapTransitMode.SBAHN_ONLY
)

class ScooterPreferencesRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_SCOOTER_ID = stringPreferencesKey("scooter_active_id")
        val MODEL_NAME = stringPreferencesKey("scooter_model_name")
        val MANUFACTURER_RANGE_KM = intPreferencesKey("scooter_manufacturer_range_km")
        val REAL_WORLD_FACTOR_PCT = intPreferencesKey("scooter_real_world_factor_pct")
        val MAX_SPEED_KMH = intPreferencesKey("scooter_max_speed_kmh")
        val CURRENT_BATTERY_PCT = intPreferencesKey("scooter_current_battery_pct")
        val BATTERY_CAPACITY_WH = intPreferencesKey("scooter_battery_capacity_wh")
        val NOMINAL_POWER_W = intPreferencesKey("scooter_nominal_power_w")
        val PEAK_POWER_W = intPreferencesKey("scooter_peak_power_w")
        val MINIMAL_SCOOTER_MODE = booleanPreferencesKey("map_minimal_scooter_mode")
        val PROMINENT_LINES = booleanPreferencesKey("map_prominent_lines")
        val MAP_THEME_OVERRIDE = stringPreferencesKey("map_theme_override")
        val TRANSIT_MODE = stringPreferencesKey("map_transit_mode_v2")
        val PREFERRED_MAPS_APP = stringPreferencesKey("settings_preferred_maps_app")
        val PREFERRED_TRANSIT_APP = stringPreferencesKey("settings_preferred_transit_app")
        val AUTO_REGION_DETECTION = booleanPreferencesKey("settings_auto_region_detection")
        val INITIAL_SETUP_COMPLETED = booleanPreferencesKey("settings_initial_setup_completed")
        val LAST_PROMPTED_REGION_ID = stringPreferencesKey("settings_last_prompted_region_id")
    }

    val scooterProfileFlow: Flow<ScooterProfile> = context.letzteMeileDataStore.data.map { prefs ->
        ScooterProfile(
            activeScooterId = prefs[Keys.ACTIVE_SCOOTER_ID] ?: "preset_soflow_so_one_pro_v1",
            modelName = prefs[Keys.MODEL_NAME] ?: "SoFlow SO ONE Pro V1",
            manufacturerRangeKm = (prefs[Keys.MANUFACTURER_RANGE_KM] ?: 65).coerceIn(10, 250),
            realWorldFactorPercent = (prefs[Keys.REAL_WORLD_FACTOR_PCT] ?: 70).coerceIn(30, 100),
            maxSpeedKmh = (prefs[Keys.MAX_SPEED_KMH] ?: 20).coerceIn(10, 45),
            currentBatteryPercent = (prefs[Keys.CURRENT_BATTERY_PCT] ?: 75).coerceIn(0, 100),
            batteryCapacityWh = prefs[Keys.BATTERY_CAPACITY_WH]?.takeIf { it > 0 }
                ?: if (prefs[Keys.MODEL_NAME] == null) 480 else null,
            nominalPowerW = prefs[Keys.NOMINAL_POWER_W]?.takeIf { it > 0 }
                ?: if (prefs[Keys.MODEL_NAME] == null) 500 else null,
            peakPowerW = prefs[Keys.PEAK_POWER_W]?.takeIf { it > 0 }
                ?: if (prefs[Keys.MODEL_NAME] == null) 1200 else null
        )
    }

    val mapDisplayPreferencesFlow: Flow<MapDisplayPreferences> =
        context.letzteMeileDataStore.data.map { prefs ->
            val mode = prefs[Keys.TRANSIT_MODE]?.let { raw ->
                MapTransitMode.entries.firstOrNull { it.name == raw }
            } ?: MapTransitMode.SBAHN_ONLY
            val nightOverride = when (prefs[Keys.MAP_THEME_OVERRIDE]) {
                "NIGHT" -> true
                "DAY" -> false
                else -> null
            }
            MapDisplayPreferences(
                isMinimalScooterMode = true,
                isProminentLines = false,
                isNightMapOverride = nightOverride,
                transitMode = mode
            )
        }

    val appSettingsFlow: Flow<AppSettings> = context.letzteMeileDataStore.data.map { prefs ->
        AppSettings(
            preferredMapsApp = PreferredMapsApp.fromId(prefs[Keys.PREFERRED_MAPS_APP]),
            preferredTransitApp = prefs[Keys.PREFERRED_TRANSIT_APP]?.let {
                PreferredTransitApp.fromId(it)
            } ?: PreferredTransitApp.MVV_APP,
            autoRegionDetection = prefs[Keys.AUTO_REGION_DETECTION] ?: true
        )
    }

    val initialSetupCompletedFlow: Flow<Boolean> = context.letzteMeileDataStore.data.map { prefs ->
        prefs[Keys.INITIAL_SETUP_COMPLETED] ?: false
    }

    val lastPromptedRegionIdFlow: Flow<String?> = context.letzteMeileDataStore.data.map { prefs ->
        prefs[Keys.LAST_PROMPTED_REGION_ID]
    }

    suspend fun saveActiveProfile(profile: ScooterProfile) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SCOOTER_ID] = profile.activeScooterId
            prefs[Keys.MODEL_NAME] = profile.modelName.ifBlank { "Mein E-Scooter" }
            prefs[Keys.MANUFACTURER_RANGE_KM] = profile.manufacturerRangeKm.coerceIn(10, 250)
            prefs[Keys.REAL_WORLD_FACTOR_PCT] = profile.realWorldFactorPercent.coerceIn(30, 100)
            prefs[Keys.MAX_SPEED_KMH] = profile.maxSpeedKmh.coerceIn(10, 45)
            prefs[Keys.CURRENT_BATTERY_PCT] = profile.currentBatteryPercent.coerceIn(0, 100)
            if (profile.batteryCapacityWh != null && profile.batteryCapacityWh > 0) {
                prefs[Keys.BATTERY_CAPACITY_WH] = profile.batteryCapacityWh
            } else {
                prefs.remove(Keys.BATTERY_CAPACITY_WH)
            }
            if (profile.nominalPowerW != null && profile.nominalPowerW > 0) {
                prefs[Keys.NOMINAL_POWER_W] = profile.nominalPowerW
            } else {
                prefs.remove(Keys.NOMINAL_POWER_W)
            }
            if (profile.peakPowerW != null && profile.peakPowerW > 0) {
                prefs[Keys.PEAK_POWER_W] = profile.peakPowerW
            } else {
                prefs.remove(Keys.PEAK_POWER_W)
            }
        }
    }

    suspend fun updateBatteryPercent(percent: Int) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.CURRENT_BATTERY_PCT] = percent.coerceIn(0, 100)
        }
    }

    suspend fun setNightMapOverride(isNight: Boolean) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.MAP_THEME_OVERRIDE] = if (isNight) "NIGHT" else "DAY"
        }
    }

    suspend fun setTransitMode(mode: MapTransitMode) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.TRANSIT_MODE] = mode.name
        }
    }

    suspend fun setPreferredMapsApp(app: PreferredMapsApp) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_MAPS_APP] = app.id
        }
    }

    suspend fun setPreferredTransitApp(app: PreferredTransitApp) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_TRANSIT_APP] = app.id
        }
    }

    suspend fun setAutoRegionDetection(enabled: Boolean) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.AUTO_REGION_DETECTION] = enabled
        }
    }

    suspend fun completeInitialSetup(preferredApp: PreferredTransitApp, initialRegionId: String?) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_TRANSIT_APP] = preferredApp.id
            prefs[Keys.INITIAL_SETUP_COMPLETED] = true
            if (!initialRegionId.isNullOrBlank()) {
                prefs[Keys.LAST_PROMPTED_REGION_ID] = initialRegionId
            }
        }
    }

    suspend fun setLastPromptedRegionId(regionId: String) {
        context.letzteMeileDataStore.edit { prefs ->
            prefs[Keys.LAST_PROMPTED_REGION_ID] = regionId
        }
    }
}
