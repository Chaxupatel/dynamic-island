package com.dynamicisland.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dynamicisland.android.model.IslandThemeId

object IslandColors {
    val CapsuleBlack = Color(0xFF000000)
    val CapsuleBorderTop = Color(0x38FFFFFF)
    val CapsuleBorderBottom = Color(0x0FFFFFFF)
    val CapsuleBorder = Color(0x26FFFFFF)

    val ElectricCyan = Color(0xFF64E6EC)
    val CobaltBlue = Color(0xFF4C8DFF)
    val EmeraldGreen = Color(0xFF32D760)
    val WarmAmber = Color(0xFFFFB84D)
    val CoralRed = Color(0xFFFF5E63)
    val VioletAccent = Color(0xFF9B7BFF)

    val BackgroundDark = Color(0xFF06070B)
    val SurfaceDark = Color(0xFF0E1119)
    val CardDark = Color(0xFF131722)
    val CardElevatedDark = Color(0xFF1A2030)
    val CardBorderTop = Color(0x24FFFFFF)
    val CardBorderBottom = Color(0x0AFFFFFF)
    val MutedTextDark = Color(0xFF8E96AC)

    val CapsuleRimBrush = Brush.verticalGradient(
        colors = listOf(CapsuleBorderTop, CapsuleBorderBottom)
    )

    val CardGlassBorderBrush = Brush.verticalGradient(
        colors = listOf(CardBorderTop, CardBorderBottom)
    )
}

/**
 * Complete visual token set controlling both the dashboard app UI and the
 * system Dynamic Island overlay so every theme renders as one cohesive experience.
 */
@Immutable
data class IslandPalette(
    val id: IslandThemeId,
    val isLightTheme: Boolean,
    val appBackground: Color,
    val heroGradientTop: Color,
    val heroGradientBottom: Color,
    val stageBackgroundTop: Color,
    val stageBackgroundBottom: Color,
    val cardSurface: Color,
    val cardElevated: Color,
    val cardBorderTop: Color,
    val cardBorderBottom: Color,
    val hairlineDivider: Color,
    val appTextPrimary: Color,
    val appTextSecondary: Color,
    val appTextMuted: Color,
    val switchUncheckedThumb: Color,
    val switchUncheckedTrack: Color,
    val sliderInactiveTrack: Color,
    val cardCornerRadius: Dp,
    val tileCornerRadius: Dp,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val positiveAccent: Color,
    val warningAccent: Color,
    val dangerAccent: Color,
    val violetAccent: Color,
    val islandSurfaceTop: Color,
    val islandSurfaceBottom: Color,
    val islandBorderTop: Color,
    val islandBorderBottom: Color,
    val islandBorderWidth: Dp,
    val islandTextPrimary: Color,
    val islandTextSecondary: Color,
    val islandTextMuted: Color,
    val islandSurfaceChip: Color,
    val islandPrimaryButtonBg: Color,
    val islandPrimaryButtonFg: Color,
    val islandShadowSpot: Color,
    val islandAuraAlpha: Float,
    val islandCornerScale: Float,
    val showTelemetryPip: Boolean
) {
    val cardBorderBrush: Brush
        get() = Brush.verticalGradient(listOf(cardBorderTop, cardBorderBottom))

    val heroGradientBrush: Brush
        get() = Brush.verticalGradient(listOf(heroGradientTop, heroGradientBottom))

    val stageGradientBrush: Brush
        get() = Brush.verticalGradient(listOf(stageBackgroundTop, stageBackgroundBottom))

    val islandSurfaceBrush: Brush
        get() = Brush.verticalGradient(listOf(islandSurfaceTop, islandSurfaceBottom))

    val islandRimBrush: Brush
        get() = Brush.verticalGradient(listOf(islandBorderTop, islandBorderBottom))
}

private val ObsidianGlassPalette = IslandPalette(
    id = IslandThemeId.OBSIDIAN_GLASS,
    isLightTheme = false,
    appBackground = IslandColors.BackgroundDark,
    heroGradientTop = Color(0xFF151B2B),
    heroGradientBottom = Color(0xFF0B0E16),
    stageBackgroundTop = Color(0xFF07090F),
    stageBackgroundBottom = Color(0xFF0E1320),
    cardSurface = IslandColors.CardDark,
    cardElevated = IslandColors.CardElevatedDark,
    cardBorderTop = IslandColors.CardBorderTop,
    cardBorderBottom = IslandColors.CardBorderBottom,
    hairlineDivider = Color.White.copy(alpha = 0.07f),
    appTextPrimary = Color.White,
    appTextSecondary = Color(0xFFE3E7F2),
    appTextMuted = IslandColors.MutedTextDark,
    switchUncheckedThumb = Color(0xFFB3BBD0),
    switchUncheckedTrack = Color(0xFF23293A),
    sliderInactiveTrack = Color.White.copy(alpha = 0.12f),
    cardCornerRadius = 24.dp,
    tileCornerRadius = 14.dp,
    primaryAccent = IslandColors.CobaltBlue,
    secondaryAccent = IslandColors.ElectricCyan,
    positiveAccent = IslandColors.EmeraldGreen,
    warningAccent = IslandColors.WarmAmber,
    dangerAccent = IslandColors.CoralRed,
    violetAccent = IslandColors.VioletAccent,
    islandSurfaceTop = Color(0xFF000000),
    islandSurfaceBottom = Color(0xFF000000),
    islandBorderTop = Color(0x38FFFFFF),
    islandBorderBottom = Color(0x0FFFFFFF),
    islandBorderWidth = 0.8.dp,
    islandTextPrimary = Color.White,
    islandTextSecondary = Color(0xFFDCE2F0),
    islandTextMuted = IslandColors.MutedTextDark,
    islandSurfaceChip = Color.White.copy(alpha = 0.08f),
    islandPrimaryButtonBg = Color.White,
    islandPrimaryButtonFg = Color.Black,
    islandShadowSpot = Color.Black,
    islandAuraAlpha = 0.14f,
    islandCornerScale = 1.0f,
    showTelemetryPip = false
)

private val AlabasterGoldPalette = IslandPalette(
    id = IslandThemeId.ALABASTER_GOLD,
    isLightTheme = true,
    appBackground = Color(0xFFF3EFE6),
    heroGradientTop = Color(0xFFFFFDF9),
    heroGradientBottom = Color(0xFFEFE9DC),
    stageBackgroundTop = Color(0xFFE6DFD1),
    stageBackgroundBottom = Color(0xFFF4EFE6),
    cardSurface = Color(0xFFFCFBF7),
    cardElevated = Color(0xFFEFEAE0),
    cardBorderTop = Color(0xFFFFFFFF),
    cardBorderBottom = Color(0x289B876A),
    hairlineDivider = Color(0x1F5E4E38),
    appTextPrimary = Color(0xFF171614),
    appTextSecondary = Color(0xFF33302A),
    appTextMuted = Color(0xFF70695D),
    switchUncheckedThumb = Color(0xFFFFFFFF),
    switchUncheckedTrack = Color(0xFFD6CEC0),
    sliderInactiveTrack = Color(0x265E4E38),
    cardCornerRadius = 22.dp,
    tileCornerRadius = 15.dp,
    primaryAccent = Color(0xFFC86327),
    secondaryAccent = Color(0xFF177B75),
    positiveAccent = Color(0xFF1D884C),
    warningAccent = Color(0xFFB97310),
    dangerAccent = Color(0xFFC6363D),
    violetAccent = Color(0xFF6B53C5),
    islandSurfaceTop = Color(0xFFFCF9F2),
    islandSurfaceBottom = Color(0xFFEAE2D3),
    islandBorderTop = Color(0xFFFFFFFF),
    islandBorderBottom = Color(0x4D9C7D56),
    islandBorderWidth = 1.1.dp,
    islandTextPrimary = Color(0xFF161513),
    islandTextSecondary = Color(0xFF3D3932),
    islandTextMuted = Color(0xFF6E675C),
    islandSurfaceChip = Color(0x12161513),
    islandPrimaryButtonBg = Color(0xFF1A1815),
    islandPrimaryButtonFg = Color(0xFFFCF9F2),
    islandShadowSpot = Color(0x524A3820),
    islandAuraAlpha = 0.11f,
    islandCornerScale = 1.0f,
    showTelemetryPip = false
)

private val MonolithCarbonPalette = IslandPalette(
    id = IslandThemeId.MONOLITH_CARBON,
    isLightTheme = false,
    appBackground = Color(0xFF050506),
    heroGradientTop = Color(0xFF131317),
    heroGradientBottom = Color(0xFF08080A),
    stageBackgroundTop = Color(0xFF030304),
    stageBackgroundBottom = Color(0xFF0F0F13),
    cardSurface = Color(0xFF0E0E11),
    cardElevated = Color(0xFF18181D),
    cardBorderTop = Color(0x38FFFFFF),
    cardBorderBottom = Color(0x24FF2E4D),
    hairlineDivider = Color(0x1FFFFFFF),
    appTextPrimary = Color(0xFFF6F7F9),
    appTextSecondary = Color(0xFFD4D7DF),
    appTextMuted = Color(0xFF838896),
    switchUncheckedThumb = Color(0xFFA0A5B5),
    switchUncheckedTrack = Color(0xFF202128),
    sliderInactiveTrack = Color(0x24FFFFFF),
    cardCornerRadius = 12.dp,
    tileCornerRadius = 8.dp,
    primaryAccent = Color(0xFFFF2E4D),
    secondaryAccent = Color(0xFFE5EAF2),
    positiveAccent = Color(0xFF28D970),
    warningAccent = Color(0xFFFFAA2B),
    dangerAccent = Color(0xFFFF2E4D),
    violetAccent = Color(0xFF9EA4B8),
    islandSurfaceTop = Color(0xFF0D0D10),
    islandSurfaceBottom = Color(0xFF040405),
    islandBorderTop = Color(0x52FFFFFF),
    islandBorderBottom = Color(0x58FF2E4D),
    islandBorderWidth = 1.0.dp,
    islandTextPrimary = Color(0xFFF7F8FA),
    islandTextSecondary = Color(0xFFD0D4DD),
    islandTextMuted = Color(0xFF878C99),
    islandSurfaceChip = Color(0x16FFFFFF),
    islandPrimaryButtonBg = Color(0xFFFF2E4D),
    islandPrimaryButtonFg = Color.White,
    islandShadowSpot = Color.Black,
    islandAuraAlpha = 0.16f,
    islandCornerScale = 0.54f,
    showTelemetryPip = true
)

fun paletteFor(themeId: IslandThemeId): IslandPalette = when (themeId) {
    IslandThemeId.OBSIDIAN_GLASS -> ObsidianGlassPalette
    IslandThemeId.ALABASTER_GOLD -> AlabasterGoldPalette
    IslandThemeId.MONOLITH_CARBON -> MonolithCarbonPalette
}

val LocalIslandPalette = staticCompositionLocalOf { ObsidianGlassPalette }

object IslandTheme {
    val palette: IslandPalette
        @Composable get() = LocalIslandPalette.current
}

@Composable
fun DynamicIslandTheme(
    themeId: IslandThemeId = IslandThemeId.OBSIDIAN_GLASS,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeId)
    val colorScheme = if (palette.isLightTheme) {
        lightColorScheme(
            primary = palette.primaryAccent,
            secondary = palette.secondaryAccent,
            tertiary = palette.positiveAccent,
            background = palette.appBackground,
            surface = palette.cardSurface,
            surfaceVariant = palette.cardElevated,
            onPrimary = Color.White,
            onBackground = palette.appTextPrimary,
            onSurface = palette.appTextPrimary,
            onSurfaceVariant = palette.appTextMuted
        )
    } else {
        darkColorScheme(
            primary = palette.primaryAccent,
            secondary = palette.secondaryAccent,
            tertiary = palette.positiveAccent,
            background = palette.appBackground,
            surface = palette.cardSurface,
            surfaceVariant = palette.cardElevated,
            onPrimary = Color.White,
            onBackground = palette.appTextPrimary,
            onSurface = palette.appTextPrimary,
            onSurfaceVariant = palette.appTextMuted
        )
    }

    CompositionLocalProvider(LocalIslandPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
