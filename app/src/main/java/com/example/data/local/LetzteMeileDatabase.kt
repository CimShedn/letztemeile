package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.GeoPoint
import com.example.model.ScooterProfile
import com.example.model.SearchLocation
import com.example.model.StationType
import com.example.model.TransitStation
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    fun toSearchLocation(): SearchLocation = SearchLocation(
        id = id,
        name = name,
        subtitle = subtitle,
        location = GeoPoint(latitude, longitude)
    )
}

@Entity(tableName = "cached_bus_stops")
data class CachedBusStopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val linesCsv: String,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    fun toTransitStation(): TransitStation = TransitStation(
        id = id,
        name = name,
        location = GeoPoint(latitude, longitude),
        type = StationType.BUS,
        lines = linesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    )
}

@Entity(tableName = "custom_scooters")
data class CustomScooterEntity(
    @PrimaryKey val id: String,
    val modelName: String,
    val manufacturerRangeKm: Int,
    val realWorldFactorPercent: Int,
    val maxSpeedKmh: Int,
    val batteryCapacityWh: Int? = null,
    val nominalPowerW: Int? = null,
    val peakPowerW: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toScooterProfile(currentBatteryPercent: Int): ScooterProfile = ScooterProfile(
        activeScooterId = id,
        modelName = modelName,
        manufacturerRangeKm = manufacturerRangeKm,
        realWorldFactorPercent = realWorldFactorPercent,
        maxSpeedKmh = maxSpeedKmh,
        currentBatteryPercent = currentBatteryPercent,
        batteryCapacityWh = batteryCapacityWh,
        nominalPowerW = nominalPowerW,
        peakPowerW = peakPowerW
    )

    fun specsSummary(): String = buildList {
        add("$manufacturerRangeKm km")
        add("$realWorldFactorPercent%")
        add("$maxSpeedKmh km/h")
        batteryCapacityWh?.let { add("$it Wh") }
        when {
            nominalPowerW != null && peakPowerW != null -> add("${nominalPowerW}/${peakPowerW} W")
            nominalPowerW != null -> add("$nominalPowerW W")
        }
    }.joinToString(" · ")
}

/**
 * Local Room cache for dynamically loaded OpenStreetMap / Overpass transit tiles (worldwide / DACH).
 * Stores pre-built GeoJSON payloads per quantized viewport tile key so returning to a region is instant.
 */
@Entity(tableName = "cached_overpass_tiles")
data class CachedOverpassTileEntity(
    @PrimaryKey val tileKey: String,
    val payloadJson: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

/**
 * Persisted metadata for user-downloaded Offline Regions (MapLibre vector tiles + pinned Room ÖPNV data).
 */
@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Double,
    val maxZoom: Double,
    val mapLibreRegionId: Long,
    val tileSizeBytes: Long,
    val transitCacheKey: String,
    val stopsCacheKey: String,
    val transitStationsCount: Int,
    val transitSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    val totalSizeBytes: Long
        get() = tileSizeBytes + transitSizeBytes

    fun contains(point: GeoPoint): Boolean =
        point.latitude in south..north && point.longitude in west..east
}

@Dao
interface FavoritePlaceDao {
    @Query("SELECT * FROM favorite_places ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<FavoritePlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: FavoritePlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<FavoritePlaceEntity>)

    @Query("DELETE FROM favorite_places WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CachedBusStopDao {
    @Query("SELECT * FROM cached_bus_stops")
    fun observeAll(): Flow<List<CachedBusStopEntity>>

    @Query("SELECT * FROM cached_bus_stops")
    suspend fun getAllSnapshot(): List<CachedBusStopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stops: List<CachedBusStopEntity>)

    @Query("DELETE FROM cached_bus_stops WHERE fetchedAt < :cutoffMillis")
    suspend fun pruneExpired(cutoffMillis: Long)
}

@Dao
interface CustomScooterDao {
    @Query("SELECT * FROM custom_scooters ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomScooterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scooter: CustomScooterEntity)

    @Query("DELETE FROM custom_scooters WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CachedOverpassTileDao {
    @Query("SELECT * FROM cached_overpass_tiles WHERE tileKey = :tileKey LIMIT 1")
    suspend fun getTile(tileKey: String): CachedOverpassTileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tile: CachedOverpassTileEntity)

    @Query("DELETE FROM cached_overpass_tiles WHERE tileKey = :tileKey")
    suspend fun deleteByKey(tileKey: String)

    // Never prune pinned offline region payloads (prefixed with 'offline_')
    @Query("DELETE FROM cached_overpass_tiles WHERE fetchedAt < :cutoffMillis AND tileKey NOT LIKE 'offline_%'")
    suspend fun pruneExpired(cutoffMillis: Long)

    // Clears all temporary Overpass tiles (preserves valid pinned offline packs)
    @Query("DELETE FROM cached_overpass_tiles WHERE tileKey NOT LIKE 'offline_%'")
    suspend fun clearTemporaryTiles()

    // Removes poisoned/empty cached payloads so corrupted empty tiles never block fresh reloads
    @Query("DELETE FROM cached_overpass_tiles WHERE length(payloadJson) < 40")
    suspend fun purgeEmptyTiles()
}

@Dao
interface OfflineRegionDao {
    @Query("SELECT * FROM offline_regions ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions ORDER BY downloadedAt DESC")
    suspend fun getAllSnapshot(): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE :lat BETWEEN south AND north AND :lon BETWEEN west AND east ORDER BY downloadedAt DESC")
    suspend fun getRegionsContaining(lat: Double, lon: Double): List<OfflineRegionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: OfflineRegionEntity)

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [
        FavoritePlaceEntity::class,
        CachedBusStopEntity::class,
        CustomScooterEntity::class,
        CachedOverpassTileEntity::class,
        OfflineRegionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LetzteMeileDatabase : RoomDatabase() {
    abstract fun favoritePlaceDao(): FavoritePlaceDao
    abstract fun cachedBusStopDao(): CachedBusStopDao
    abstract fun customScooterDao(): CustomScooterDao
    abstract fun cachedOverpassTileDao(): CachedOverpassTileDao
    abstract fun offlineRegionDao(): OfflineRegionDao

    companion object {
        @Volatile
        private var INSTANCE: LetzteMeileDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_scooters` (
                        `id` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `manufacturerRangeKm` INTEGER NOT NULL,
                        `realWorldFactorPercent` INTEGER NOT NULL,
                        `maxSpeedKmh` INTEGER NOT NULL,
                        `batteryCapacityWh` INTEGER,
                        `nominalPowerW` INTEGER,
                        `peakPowerW` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_overpass_tiles` (
                        `tileKey` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tileKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_regions` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `south` REAL NOT NULL,
                        `west` REAL NOT NULL,
                        `north` REAL NOT NULL,
                        `east` REAL NOT NULL,
                        `minZoom` REAL NOT NULL,
                        `maxZoom` REAL NOT NULL,
                        `mapLibreRegionId` INTEGER NOT NULL,
                        `tileSizeBytes` INTEGER NOT NULL,
                        `transitCacheKey` TEXT NOT NULL,
                        `stopsCacheKey` TEXT NOT NULL,
                        `transitStationsCount` INTEGER NOT NULL,
                        `transitSizeBytes` INTEGER NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `favorite_places` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): LetzteMeileDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LetzteMeileDatabase::class.java,
                    "letztemeile.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
