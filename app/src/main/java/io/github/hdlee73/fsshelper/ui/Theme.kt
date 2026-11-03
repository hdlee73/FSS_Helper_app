package io.github.hdlee73.fsshelper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hdlee73.fsshelper.data.ThemeMode

/**
 * 시안 3 네이비 공문: 짙은 남색 머리띠, 옅은 회색 바탕, 모서리 4~6dp의 흰 카드(그림자 없음).
 * 도구마다 고유 색(아이콘)을 두고 나머지는 무채색으로 차분하게 둔다.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF1B2B4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E8F3),
    onPrimaryContainer = Color(0xFF1B2B4B),
    secondary = Color(0xFF2F4B82),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8ECF4),
    onSecondaryContainer = Color(0xFF15213A),
    tertiary = Color(0xFFD08A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF6B4300),
    background = Color(0xFFEEF0F4),
    onBackground = Color(0xFF1A2233),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2233),
    surfaceVariant = Color(0xFFF1F3F7),
    onSurfaceVariant = Color(0xFF5F6879),
    surfaceContainer = Color(0xFFF6F7FA),
    surfaceContainerHigh = Color(0xFFE6E9F0),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFDDE1EA),
    outline = Color(0xFF9AA2B2),
    outlineVariant = Color(0xFFDCE0E8),
    error = Color(0xFFC0392B),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF6B1B14),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAFC4F0),
    onPrimary = Color(0xFF10203F),
    primaryContainer = Color(0xFF2A3D63),
    onPrimaryContainer = Color(0xFFDDE6FA),
    secondary = Color(0xFF9DB4E6),
    onSecondary = Color(0xFF10203F),
    secondaryContainer = Color(0xFF263449),
    onSecondaryContainer = Color(0xFFDDE6FA),
    tertiary = Color(0xFFFFC560),
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF3D2E12),
    onTertiaryContainer = Color(0xFFFFDDA0),
    background = Color(0xFF12161E),
    onBackground = Color(0xFFE4E8F0),
    surface = Color(0xFF1A1F29),
    onSurface = Color(0xFFE4E8F0),
    surfaceVariant = Color(0xFF222835),
    onSurfaceVariant = Color(0xFFA3ABBC),
    surfaceContainer = Color(0xFF181D26),
    surfaceContainerHigh = Color(0xFF262D3B),
    surfaceContainerLow = Color(0xFF151A22),
    surfaceContainerLowest = Color(0xFF10141B),
    surfaceContainerHighest = Color(0xFF2E3646),
    outline = Color(0xFF5E6778),
    outlineVariant = Color(0xFF2C3341),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0E09),
    errorContainer = Color(0xFF3A1C1A),
    onErrorContainer = Color(0xFFFFDAD5),
)

/** 앱 막대·홈 머리띠·메뉴 바탕의 남색 */
private val LocalNavy = staticCompositionLocalOf { Color(0xFF1B2B4B) }
val NavyBand: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalNavy.current

/** 정상·주의·오류를 색과 글자 모두로 보여 줄 때 쓰는 색. */
@Immutable
data class StatusColors(
    val ok: Color, val okBg: Color,
    val warn: Color, val warnBg: Color,
    val bad: Color, val badBg: Color,
)

private val LightStatus = StatusColors(
    ok = Color(0xFF21794F), okBg = Color(0xFFE2F2E9),
    warn = Color(0xFFB35E12), warnBg = Color(0xFFFBEBDD),
    bad = Color(0xFFB93A30), badBg = Color(0xFFF8E2DF),
)
private val DarkStatus = StatusColors(
    ok = Color(0xFF7FD6A8), okBg = Color(0xFF173227),
    warn = Color(0xFFF2B35B), warnBg = Color(0xFF3A2A14),
    bad = Color(0xFFFF8A80), badBg = Color(0xFF3A1C1A),
)

val LocalStatus = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatus provides (if (dark) DarkStatus else LightStatus), LocalNavy provides (if (dark) Color(0xFF16213A) else Color(0xFF1B2B4B))) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
