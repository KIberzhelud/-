package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.model.UiThemeColor
import com.example.model.WallpaperPaletteData

data class VistaThemeTokens(
    val isGlassMode: Boolean,
    val baseColor: UiThemeColor,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val divider: Color,
    val accent: Color,
    val focusBorder: Color,
    val glassTop: Color,
    val glassUpperMid: Color,
    val glassLowerMid: Color,
    val glassBottom: Color,
    val chromeBorderOuter: Color,
    val chromeInnerGlow: Color,
    val insetBg: Color,
    val insetShadow: Color,
    val insetBottomRim: Color,
    val trackTop: Color,
    val trackMid: Color,
    val trackBottom: Color,
    val thumbLight: Color,
    val thumbMid: Color,
    val thumbDark: Color,
    val buttonTop: Color,
    val buttonUpperMid: Color,
    val buttonLowerMid: Color,
    val buttonBottom: Color,
    val primaryButtonTop: Color,
    val primaryButtonUpperMid: Color,
    val primaryButtonLowerMid: Color,
    val primaryButtonBottom: Color,
    val activeButtonTop: Color,
    val activeButtonUpperMid: Color,
    val activeButtonLowerMid: Color,
    val activeButtonBottom: Color,
    val tileBuckets: List<Triple<Color, Color, Color>>
)

val BlueVistaTokens = VistaThemeTokens(
    isGlassMode = false,
    baseColor = UiThemeColor.BLUE,
    background = NavyBackground,
    surface = NavySurface,
    surfaceElevated = NavySurfaceElevated,
    divider = NavyDivider,
    accent = ElectricCyan,
    focusBorder = FocusBorderCyan,
    glassTop = VistaGlassTop,
    glassUpperMid = VistaGlassUpperMid,
    glassLowerMid = VistaGlassLowerMid,
    glassBottom = VistaGlassBottom,
    chromeBorderOuter = VistaChromeBorderOuter,
    chromeInnerGlow = VistaChromeInnerGlow,
    insetBg = VistaInsetBg,
    insetShadow = VistaInsetShadow,
    insetBottomRim = VistaInsetBottomRim,
    trackTop = VistaTrackCyanTop,
    trackMid = VistaTrackCyanMid,
    trackBottom = VistaTrackCyanBottom,
    thumbLight = VistaThumbLight,
    thumbMid = VistaThumbMid,
    thumbDark = VistaThumbDark,
    buttonTop = Color(0xFF3E5878),
    buttonUpperMid = Color(0xFF233852),
    buttonLowerMid = Color(0xFF142236),
    buttonBottom = Color(0xFF0D1827),
    primaryButtonTop = Color(0xFF75C6FF),
    primaryButtonUpperMid = TileBrightAzureTop,
    primaryButtonLowerMid = TileBrightAzure,
    primaryButtonBottom = TileBrightAzureBottom,
    activeButtonTop = Color(0xFF4788C7),
    activeButtonUpperMid = TileDeepCobaltTop,
    activeButtonLowerMid = TileDeepCobalt,
    activeButtonBottom = TileDeepCobaltBottom,
    tileBuckets = listOf(
        Triple(TileVibrantBlueTop, TileVibrantBlue, TileVibrantBlueBottom),
        Triple(TileBrightAzureTop, TileBrightAzure, TileBrightAzureBottom),
        Triple(TileDeepCobaltTop, TileDeepCobalt, TileDeepCobaltBottom),
        Triple(TileMidnightSlateTop, TileMidnightSlate, TileMidnightSlateBottom)
    )
)

val GrayVistaTokens = VistaThemeTokens(
    isGlassMode = false,
    baseColor = UiThemeColor.GRAY,
    background = Color(0xFF121417),
    surface = Color(0xFF1D2025),
    surfaceElevated = Color(0xFF262A31),
    divider = Color(0xFF444B57),
    accent = Color(0xFFD2DCE8),
    focusBorder = Color(0xFFFFFFFF),
    glassTop = Color(0xFF59606B),
    glassUpperMid = Color(0xFF3A4049),
    glassLowerMid = Color(0xFF22262C),
    glassBottom = Color(0xFF13161B),
    chromeBorderOuter = Color(0xFF08090B),
    chromeInnerGlow = Color(0xFFD4DEE9),
    insetBg = Color(0xFF0E1013),
    insetShadow = Color(0xFF040506),
    insetBottomRim = Color(0xFF6A7382),
    trackTop = Color(0xFFF0F4F8),
    trackMid = Color(0xFF9AA5B4),
    trackBottom = Color(0xFF4D5663),
    thumbLight = Color(0xFFFAFAFC),
    thumbMid = Color(0xFFCDD4DE),
    thumbDark = Color(0xFF788291),
    buttonTop = Color(0xFF5C636E),
    buttonUpperMid = Color(0xFF393E47),
    buttonLowerMid = Color(0xFF23272D),
    buttonBottom = Color(0xFF171A1F),
    primaryButtonTop = Color(0xFFCFD8E3),
    primaryButtonUpperMid = Color(0xFF7B8594),
    primaryButtonLowerMid = Color(0xFF4D5563),
    primaryButtonBottom = Color(0xFF313742),
    activeButtonTop = Color(0xFF838E9E),
    activeButtonUpperMid = Color(0xFF535B68),
    activeButtonLowerMid = Color(0xFF363C46),
    activeButtonBottom = Color(0xFF22262E),
    tileBuckets = listOf(
        Triple(Color(0xFF575E69), Color(0xFF383D46), Color(0xFF23272E)),
        Triple(Color(0xFF666E7B), Color(0xFF444A55), Color(0xFF2A2F37)),
        Triple(Color(0xFF4D535E), Color(0xFF30353D), Color(0xFF1E2127)),
        Triple(Color(0xFF444A54), Color(0xFF292D34), Color(0xFF191C21))
    )
)

fun buildVistaThemeTokens(
    themeColor: UiThemeColor,
    wallpaperPalette: WallpaperPaletteData?,
    hasWallpaper: Boolean
): VistaThemeTokens {
    val base = when (themeColor) {
        UiThemeColor.BLUE -> BlueVistaTokens
        UiThemeColor.GRAY -> GrayVistaTokens
    }
    if (wallpaperPalette == null) {
        return base
    }

    val dark = Color(wallpaperPalette.dominantDarkArgb.toInt())
    val accent = Color(wallpaperPalette.accentArgb.toInt())
    val highlight = Color(wallpaperPalette.highlightArgb.toInt())

    val bg = lerp(dark, Color.Black, 0.52f)
    val surf = lerp(dark, Color.Black, 0.25f)
    val surfElev = lerp(dark, accent, 0.16f)
    val div = lerp(dark, accent, 0.36f)

    return VistaThemeTokens(
        isGlassMode = hasWallpaper,
        baseColor = themeColor,
        background = bg,
        surface = surf,
        surfaceElevated = surfElev,
        divider = div,
        accent = highlight,
        focusBorder = Color.White,
        glassTop = lerp(dark, accent, 0.42f),
        glassUpperMid = lerp(dark, accent, 0.24f),
        glassLowerMid = lerp(dark, Color.Black, 0.28f),
        glassBottom = lerp(dark, Color.Black, 0.56f),
        chromeBorderOuter = Color(0xFF05070A),
        chromeInnerGlow = lerp(highlight, Color.White, 0.35f),
        insetBg = lerp(dark, Color.Black, 0.68f),
        insetShadow = Color(0xFF020305),
        insetBottomRim = lerp(dark, highlight, 0.45f),
        trackTop = lerp(highlight, Color.White, 0.42f),
        trackMid = accent,
        trackBottom = lerp(accent, Color.Black, 0.46f),
        thumbLight = Color(0xFFF6FAFF),
        thumbMid = lerp(highlight, Color(0xFFC5D2E0), 0.5f),
        thumbDark = lerp(dark, accent, 0.42f),
        buttonTop = lerp(dark, highlight, 0.38f),
        buttonUpperMid = lerp(dark, accent, 0.22f),
        buttonLowerMid = lerp(dark, Color.Black, 0.32f),
        buttonBottom = lerp(dark, Color.Black, 0.54f),
        primaryButtonTop = lerp(accent, Color.White, 0.45f),
        primaryButtonUpperMid = lerp(accent, highlight, 0.35f),
        primaryButtonLowerMid = accent,
        primaryButtonBottom = lerp(accent, Color.Black, 0.45f),
        activeButtonTop = lerp(accent, Color.White, 0.25f),
        activeButtonUpperMid = lerp(dark, accent, 0.52f),
        activeButtonLowerMid = lerp(dark, accent, 0.28f),
        activeButtonBottom = lerp(dark, Color.Black, 0.45f),
        tileBuckets = listOf(
            Triple(lerp(dark, accent, 0.42f), lerp(dark, accent, 0.20f), lerp(dark, Color.Black, 0.35f)),
            Triple(lerp(dark, highlight, 0.36f), lerp(dark, accent, 0.24f), lerp(dark, Color.Black, 0.32f)),
            Triple(lerp(dark, accent, 0.28f), lerp(dark, Color.Black, 0.16f), lerp(dark, Color.Black, 0.44f)),
            Triple(lerp(dark, Color.White, 0.16f), lerp(dark, Color.Black, 0.22f), lerp(dark, Color.Black, 0.48f))
        )
    )
}

val LocalVistaTheme = staticCompositionLocalOf { BlueVistaTokens }
