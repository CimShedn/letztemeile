package com.example.ui.scooter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CustomScooterEntity
import com.example.data.local.FavoritePlaceEntity
import com.example.model.DefaultScooterPresets
import com.example.model.ScooterPreset
import com.example.model.ScooterProfile
import com.example.model.SearchLocation
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ScooterScreen(
    profile: ScooterProfile,
    customScooters: List<CustomScooterEntity>,
    favorites: List<FavoritePlaceEntity>,
    onBatteryChanged: (Int) -> Unit,
    onSaveCustomScooter: (
        existingId: String?,
        modelName: String,
        manufacturerRangeKm: Int,
        realWorldFactorPercent: Int,
        maxSpeedKmh: Int,
        batteryCapacityWh: Int?,
        nominalPowerW: Int?,
        peakPowerW: Int?
    ) -> Unit,
    onSelectCustomScooter: (CustomScooterEntity) -> Unit,
    onDeleteCustomScooter: (String) -> Unit,
    onApplyPreset: (ScooterPreset) -> Unit,
    onSelectFavoriteOnMap: (SearchLocation) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Editing state: null = create new custom scooter; non-null = edit that saved custom scooter
    var editingCustomId by remember { mutableStateOf<String?>(null) }
    var modelName by remember { mutableStateOf("") }
    var manufacturerRangeText by remember { mutableStateOf("65") }
    var realFactorSlider by remember { mutableFloatStateOf(70f) }
    var maxSpeedText by remember { mutableStateOf("20") }
    var batteryWhText by remember { mutableStateOf("") }
    var nominalPowerText by remember { mutableStateOf("") }
    var peakPowerText by remember { mutableStateOf("") }

    var savedFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedFeedback) {
        if (savedFeedback != null) {
            kotlinx.coroutines.delay(2000)
            savedFeedback = null
        }
    }

    fun startNewCustomScooter() {
        editingCustomId = null
        modelName = ""
        manufacturerRangeText = "65"
        realFactorSlider = 70f
        maxSpeedText = "20"
        batteryWhText = ""
        nominalPowerText = ""
        peakPowerText = ""
    }

    fun startEditingCustomScooter(entity: CustomScooterEntity) {
        editingCustomId = entity.id
        modelName = entity.modelName
        manufacturerRangeText = entity.manufacturerRangeKm.toString()
        realFactorSlider = entity.realWorldFactorPercent.toFloat()
        maxSpeedText = entity.maxSpeedKmh.toString()
        batteryWhText = entity.batteryCapacityWh?.toString().orEmpty()
        nominalPowerText = entity.nominalPowerW?.toString().orEmpty()
        peakPowerText = entity.peakPowerW?.toString().orEmpty()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // 1. Hero Header Card with bespoke editorial scooter/S-Bahn illustration & active specs
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(184.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_scooter_hero_1795267789481),
                            contentDescription = "E-Scooter und S-Bahn",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x2E121316),
                                            Color(0xE8121316)
                                        )
                                    )
                                )
                        )
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        ) {
                            Surface(
                                color = Color(0xD9059669),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ElectricScooter,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AKTIVER E-SCOOTER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.modelName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.GERMAN,
                                            "Real %.0f km · %s",
                                            profile.effectiveFullRangeKm,
                                            profile.specsSummary()
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE5E7EB),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${profile.currentBatteryPercent} %",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color(0xFF34D399)
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.GERMAN,
                                            "~%.1f km frei",
                                            profile.currentAvailableRangeKm
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFD1D5DB)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Live Battery Level Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aktueller Akkustand",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "${profile.currentBatteryPercent} %",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = profile.currentBatteryPercent.toFloat(),
                            onValueChange = { onBatteryChanged(it.roundToInt()) },
                            valueRange = 5f..100f,
                            steps = 18,
                            modifier = Modifier.testTag("scooter_battery_slider")
                        )
                    }
                }
            }

            // 3. PERSISTENT "Meine Scooter" Section (Always above Standard Presets!)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Meine Scooter (${customScooters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(
                            onClick = { startNewCustomScooter() },
                            modifier = Modifier.testTag("new_custom_scooter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Neu")
                        }
                    }

                    if (customScooters.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Noch kein eigener Scooter gespeichert. Lege unten dein Modell an – es bleibt dauerhaft hier gespeichert, auch wenn du zu Standard-Modellen wechselst.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customScooters.forEach { custom ->
                                val isActive = profile.activeScooterId == custom.id
                                Surface(
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = if (isActive) 1.5.dp else 0.75.dp,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectCustomScooter(custom) }
                                        .testTag("custom_scooter_${custom.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = custom.modelName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isActive) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Aktiv",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = custom.specsSummary(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        IconButton(
                                            onClick = { startEditingCustomScooter(custom) },
                                            modifier = Modifier.testTag("edit_custom_scooter_${custom.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Scooter bearbeiten",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (editingCustomId == custom.id) {
                                                    startNewCustomScooter()
                                                }
                                                onDeleteCustomScooter(custom.id)
                                            },
                                            modifier = Modifier.testTag("delete_custom_scooter_${custom.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Scooter löschen",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Standard Presets (SoFlow SO ONE Pro V1 right at the front!)
            item {
                Column {
                    Text(
                        text = "Standard-Modelle",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(DefaultScooterPresets, key = { it.id }) { preset ->
                            val isSelected = profile.activeScooterId == preset.id
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 0.75.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .widthIn(min = 195.dp, max = 250.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onApplyPreset(preset) }
                                    .testTag("preset_${preset.id}")
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = preset.modelName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Aktiv",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${preset.manufacturerRangeKm} km · ${preset.realWorldFactorPercent}% · ${preset.maxSpeedKmh} km/h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (preset.batteryCapacityWh != null || preset.nominalPowerW != null) {
                                        val powerText = buildList {
                                            preset.batteryCapacityWh?.let { add("$it Wh") }
                                            if (preset.nominalPowerW != null && preset.peakPowerW != null) {
                                                add("${preset.nominalPowerW} W / ${preset.peakPowerW} W Peak")
                                            }
                                        }.joinToString(" · ")
                                        Text(
                                            text = powerText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Custom Scooter Form (Create New OR Edit Saved Custom Scooter)
            item {
                val isEditing = editingCustomId != null
                val inputColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = if (isEditing) 1.5.dp else 0.75.dp,
                        color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEditing) "Eigenen Scooter bearbeiten" else "Eigenen Scooter anlegen",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (isEditing) {
                                TextButton(onClick = { startNewCustomScooter() }) {
                                    Text("Abbrechen")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text(stringResource(R.string.scooter_model_label)) },
                            placeholder = { Text("z. B. Mein SoFlow Pendler") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = inputColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scooter_model_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = manufacturerRangeText,
                                onValueChange = { manufacturerRangeText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Reichweite (km)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scooter_range_input")
                            )
                            OutlinedTextField(
                                value = maxSpeedText,
                                onValueChange = { maxSpeedText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("km/h") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scooter_speed_input")
                            )
                        }

                        // Optional technical details (Wh, Nominal W, Peak W)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = batteryWhText,
                                onValueChange = { batteryWhText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Akku (Wh)") },
                                placeholder = { Text("480") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = nominalPowerText,
                                onValueChange = { nominalPowerText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Nenn (W)") },
                                placeholder = { Text("500") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = peakPowerText,
                                onValueChange = { peakPowerText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Peak (W)") },
                                placeholder = { Text("1200") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.scooter_real_factor_label),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${realFactorSlider.roundToInt()} %",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = realFactorSlider,
                                onValueChange = { realFactorSlider = it },
                                valueRange = 40f..100f,
                                steps = 11,
                                modifier = Modifier.testTag("scooter_real_factor_slider")
                            )
                            Text(
                                text = "Berücksichtigt Kälte, Steigungen & Stop-and-Go im Stadtverkehr (Empfehlung: 65–75 %).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isEditing) {
                                OutlinedButton(
                                    onClick = { startNewCustomScooter() },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Text("Verwerfen")
                                }
                            }
                            Button(
                                onClick = {
                                    val cleanName = modelName.trim().ifBlank { "Mein E-Scooter" }
                                    onSaveCustomScooter(
                                        editingCustomId,
                                        cleanName,
                                        manufacturerRangeText.toIntOrNull()?.coerceIn(10, 250) ?: 65,
                                        realFactorSlider.roundToInt().coerceIn(30, 100),
                                        maxSpeedText.toIntOrNull()?.coerceIn(10, 45) ?: 20,
                                        batteryWhText.toIntOrNull(),
                                        nominalPowerText.toIntOrNull(),
                                        peakPowerText.toIntOrNull()
                                    )
                                    savedFeedback = if (isEditing) {
                                        "Änderungen gespeichert"
                                    } else {
                                        "In 'Meine Scooter' gespeichert"
                                    }
                                    startNewCustomScooter()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("save_scooter_profile_button")
                            ) {
                                if (savedFeedback != null) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(savedFeedback ?: "")
                                } else {
                                    Text(
                                        if (isEditing) {
                                            "Änderungen speichern"
                                        } else {
                                            "In 'Meine Scooter' speichern"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Saved Favorites (Room)
            item {
                Text(
                    text = stringResource(R.string.scooter_favorites_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (favorites.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.scooter_favorites_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(favorites, key = { it.id }) { fav ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectFavoriteOnMap(fav.toSearchLocation()) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fav.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = fav.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteFavorite(fav.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = stringResource(R.string.map_remove_favorite_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
