package com.wayfare.ride.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayfare.ride.R

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val WayfareMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

enum class AppThemeMode(val key: String, val label: String) {
    DARK("dark", "Dark"),
    SYSTEM("system", "System"),
    LIGHT("light", "Light");

    fun next(): AppThemeMode = when (this) {
        LIGHT -> DARK
        DARK -> SYSTEM
        SYSTEM -> LIGHT
    }

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

@Immutable
data class WayfareExtendedColors(
    val isDark: Boolean,
    val routeHighlight: Color,
    val mapRoad: Color,
    val mapRoadMajor: Color,
    val mapWater: Color,
    val mapPark: Color,
    val mapBlock: Color,
    val destinationMarker: Color,
    val brandHighlight: Color,
    val emeraldAccent: Color,
    val emeraldContainer: Color,
    val crimsonAccent: Color,
    val crimsonContainer: Color,
    val positive: Color,
    val warning: Color,
    val subtleBorder: Color,
    val walletCardStart: Color,
    val walletCardEnd: Color,
    val walletCardBorder: Color,
    val walletOnCard: Color,
    val walletAccent: Color,
    val heatLow: Color,
    val heatMid: Color,
    val heatHigh: Color
)

private val LightExtendedColors = WayfareExtendedColors(
    isDark = false,
    routeHighlight = Color(0xFF4C9BFF),
    mapRoad = Color(0xFFFFFFFF),
    mapRoadMajor = Color(0xFFD2DEF2),
    mapWater = Color(0xFFBED8FC),
    mapPark = Color(0xFFD9EFE6),
    mapBlock = Color(0xFFEAF0FA),
    destinationMarker = Color(0xFF0D946B),
    brandHighlight = Color(0xFFDCE7FF),
    emeraldAccent = Color(0xFF0D946B),
    emeraldContainer = Color(0xFFD8F5EA),
    crimsonAccent = Color(0xFFD92B3A),
    crimsonContainer = Color(0xFFFFE5E8),
    positive = Color(0xFF0D946B),
    warning = Color(0xFFD97706),
    subtleBorder = Color(0xFFD6E0F2),
    walletCardStart = Color(0xFF124EE8),
    walletCardEnd = Color(0xFF071B4B),
    walletCardBorder = Color(0xFF4C9BFF),
    walletOnCard = Color(0xFFFFFFFF),
    walletAccent = Color(0xFF34D399),
    heatLow = Color(0xFFF59E0B),
    heatMid = Color(0xFFEA580C),
    heatHigh = Color(0xFFDC2626)
)

private val DarkExtendedColors = WayfareExtendedColors(
    isDark = true,
    routeHighlight = Color(0xFF7CB8FF),
    mapRoad = Color(0xFF1B2840),
    mapRoadMajor = Color(0xFF2B3E61),
    mapWater = Color(0xFF122745),
    mapPark = Color(0xFF122B29),
    mapBlock = Color(0xFF0B1324),
    destinationMarker = Color(0xFF34D399),
    brandHighlight = Color(0xFF162F61),
    emeraldAccent = Color(0xFF34D399),
    emeraldContainer = Color(0xFF0E3A2F),
    crimsonAccent = Color(0xFFFF6B78),
    crimsonContainer = Color(0xFF5C1720),
    positive = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    subtleBorder = Color(0xFF243554),
    walletCardStart = Color(0xFF153478),
    walletCardEnd = Color(0xFF071024),
    walletCardBorder = Color(0xFF4C80EB),
    walletOnCard = Color(0xFFF3F7FF),
    walletAccent = Color(0xFF34D399),
    heatLow = Color(0xFFFBBF24),
    heatMid = Color(0xFFFF6D3B),
    heatHigh = Color(0xFFFF2E54)
)

val LocalWayfareColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF124EE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2463),
    secondary = Color(0xFF0D946B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EEF9),
    onSecondaryContainer = Color(0xFF0D255E),
    tertiary = Color(0xFFD92B3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5E8),
    onTertiaryContainer = Color(0xFF610D16),
    background = Color(0xFFF5F8FD),
    onBackground = Color(0xFF0C1830),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C1830),
    surfaceVariant = Color(0xFFE8EFFA),
    onSurfaceVariant = Color(0xFF51617D),
    outline = Color(0xFFD0DCF0),
    outlineVariant = Color(0xFFE1E9F6),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6B9EFF),
    onPrimary = Color(0xFF041434),
    primaryContainer = Color(0xFF162F61),
    onPrimaryContainer = Color(0xFFEAF1FF),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF04291E),
    secondaryContainer = Color(0xFF192949),
    onSecondaryContainer = Color(0xFFEAF1FF),
    tertiary = Color(0xFFFF6B78),
    onTertiary = Color(0xFF3E040B),
    tertiaryContainer = Color(0xFF5C1720),
    onTertiaryContainer = Color(0xFFFFE5E8),
    background = Color(0xFF070D1A),
    onBackground = Color(0xFFF1F5FF),
    surface = Color(0xFF10192D),
    onSurface = Color(0xFFF1F5FF),
    surfaceVariant = Color(0xFF192640),
    onSurfaceVariant = Color(0xFFA6B8D6),
    outline = Color(0xFF283959),
    outlineVariant = Color(0xFF1E2D4A),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val WayfareTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val WayfareShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WayfareTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWayfareColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WayfareTypography,
            shapes = WayfareShapes,
            content = content
        )
    }
}
