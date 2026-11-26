package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Friendly E-Mobility Brand Identity (Warm Mint Green)
val BrandMintDark = Color(0xFF34D399)
val OnBrandMintDark = Color(0xFF052E1E)
val BrandMintContainerDark = Color(0xFF12362B)
val OnBrandMintContainerDark = Color(0xFFA7F3D0)

val BrandMintLight = Color(0xFF059669)
val OnBrandMintLight = Color(0xFFFFFFFF)
val BrandMintContainerLight = Color(0xFFD1FAE5)
val OnBrandMintContainerLight = Color(0xFF064E3B)

// Soft Secondary Action Surfaces (e.g. Kopier-Hilfe, Maps buttons)
val SecondarySurfaceDark = Color(0xFF262C36)
val OnSecondarySurfaceDark = Color(0xFFF3F4F6)
val SecondarySurfaceLight = Color(0xFFEAEFF5)
val OnSecondarySurfaceLight = Color(0xFF111827)

// Legacy Brand Aliases (kept for compatibility with map/transit accents)
val MvvBlue = Color(0xFF0065BD)
val MvvBlueDeep = Color(0xFF025297)
val MvvBlueLight = Color(0xFF60A5FA)
val ScooterMint = BrandMintLight
val ScooterMintBright = BrandMintDark

// Official DACH / Munich Transit Line Palette
val SBahnS1 = Color(0xFF16BAE7)
val SBahnS2 = Color(0xFF76B82A)
val SBahnS3 = Color(0xFF951B81)
val SBahnS4 = Color(0xFFE30613)
val SBahnS5 = Color(0xFF005C9E)
val SBahnS6 = Color(0xFF00975F)
val SBahnS7 = Color(0xFF963833)
val SBahnS8 = Color(0xFFF7A600)
val SBahnS8Text = Color(0xFF111315)
val SBahnS20 = Color(0xFFED6B83)
val MvvBusPetrol = Color(0xFF00586A)
val MvvUBahnBlue = Color(0xFF0065BD)
val MvvTramRed = Color(0xFFD91A1A)

// Battery Traffic Light & Range Semantic Colors (Emerald / Warm Amber / Soft Coral)
val BatterySafeLight = Color(0xFF059669)
val BatterySafeContainerLight = Color(0xFFD1FAE5)
val BatterySafeDark = Color(0xFF34D399)
val BatterySafeContainerDark = Color(0xFF112D23)

val BatteryTightLight = Color(0xFFB45309)
val BatteryTightContainerLight = Color(0xFFFEF3C7)
val BatteryTightDark = Color(0xFFFBBF24)
val BatteryTightContainerDark = Color(0xFF33260C)

val BatteryCriticalLight = Color(0xFFDC2626)
val BatteryCriticalContainerLight = Color(0xFFFEE2E2)
val BatteryCriticalDark = Color(0xFFF87171)
val BatteryCriticalContainerDark = Color(0xFF3A181C)

// Light Theme Surfaces (Clean, Warm Daylight Readability)
val LightBackground = Color(0xFFF6F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF1F5)
val LightSurfaceContainerLow = Color(0xFFF3F5F8)
val LightSurfaceContainer = Color(0xFFEEF1F5)
val LightSurfaceContainerHigh = Color(0xFFE6EBF2)
val LightSurfaceContainerHighest = Color(0xFFDDE3EC)
val LightOnBackground = Color(0xFF111827)
val LightOnSurface = Color(0xFF111827)
val LightOnSurfaceVariant = Color(0xFF4B5563)
val LightOutline = Color(0xFFCBD5E1)
val LightOutlineVariant = Color(0xFFE2E8F0)

// Dark Theme Surfaces (Warm Anthracite Cockpit — WCAG AA Verified)
val DarkBackground = Color(0xFF121316)
val DarkSurface = Color(0xFF1C1F24)
val DarkSurfaceVariant = Color(0xFF242830)
val DarkSurfaceContainerLow = Color(0xFF1F232A)
val DarkSurfaceContainer = Color(0xFF242830)
val DarkSurfaceContainerHigh = Color(0xFF262C36)
val DarkSurfaceContainerHighest = Color(0xFF2E3440)
val DarkOnBackground = Color(0xFFF3F4F6)
val DarkOnSurface = Color(0xFFF3F4F6)
val DarkOnSurfaceVariant = Color(0xFFAEB6C2)
val DarkOutline = Color(0xFF3B4352)
val DarkOutlineVariant = Color(0x802E3440)

fun lineColorForName(lineName: String): Color {
    val clean = lineName.trim().uppercase()
    return when {
        clean.startsWith("ICE") -> Color(0xFFD5001C)
        clean.startsWith("IC") || clean.startsWith("EC") || clean.startsWith("RJ") -> Color(0xFFB91C1C)
        clean.startsWith("RE") || clean.startsWith("IRE") || clean.startsWith("REX") -> Color(0xFFC8102E)
        clean.startsWith("RB") || clean.startsWith("MEX") || clean.startsWith("BRB") -> Color(0xFF1D4ED8)
        clean.startsWith("ALX") || clean.startsWith("BOB") -> Color(0xFF0F766E)
        clean.startsWith("S1") -> SBahnS1
        clean.startsWith("S20") -> SBahnS20
        clean.startsWith("S2") -> SBahnS2
        clean.startsWith("S3") -> SBahnS3
        clean.startsWith("S4") -> SBahnS4
        clean.startsWith("S5") -> SBahnS5
        clean.startsWith("S6") -> SBahnS6
        clean.startsWith("S7") -> SBahnS7
        clean.startsWith("S8") -> SBahnS8
        clean.startsWith("S") -> Color(0xFF00975F)
        clean.startsWith("U") -> MvvUBahnBlue
        clean.startsWith("TRAM") || clean.startsWith("STR") -> MvvTramRed
        clean.startsWith("BUS") || clean.startsWith("X") || clean.all { it.isDigit() } -> MvvBusPetrol
        else -> MvvBlue
    }
}

fun lineTextColorForName(lineName: String): Color {
    val clean = lineName.trim().uppercase()
    return if (clean.startsWith("S8")) SBahnS8Text else Color.White
}
