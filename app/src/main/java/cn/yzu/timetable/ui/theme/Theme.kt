package cn.yzu.timetable.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 液态玻璃的视觉参数：浅色与深色各一套，避免每个组件自己猜透明度。 */
@Immutable
data class GlassColors(
    val dark: Boolean,
    val canvas: Color,
    val blobs: List<Color>,
    val fill: Color,
    val fillStrong: Color,
    val fillFallback: Color,
    val rimLight: Color,
    val rimShade: Color,
    val shadow: Color,
    val textMuted: Color,
    val textFaint: Color,
    val success: Color,
    val warning: Color,
)

private val LightGlass = GlassColors(
    dark = false,
    canvas = Color(0xFFF1F3F8),
    blobs = listOf(Color(0xFFB9D4FF), Color(0xFFD9CCFF), Color(0xFFC4EBDD), Color(0xFFFFE1CC)),
    fill = Color.White.copy(alpha = 0.58f),
    fillStrong = Color.White.copy(alpha = 0.80f),
    fillFallback = Color(0xF2F8F9FC),
    rimLight = Color.White.copy(alpha = 0.95f),
    rimShade = Color.White.copy(alpha = 0.25f),
    shadow = Color(0xFF1B2440).copy(alpha = 0.12f),
    textMuted = Color(0xFF5B6272),
    textFaint = Color(0xFF8E94A3),
    success = Color(0xFF248A55),
    warning = Color(0xFFB26A00),
)

private val DarkGlass = GlassColors(
    dark = true,
    canvas = Color(0xFF0B0D12),
    blobs = listOf(Color(0xFF1E3A6B), Color(0xFF3A2B66), Color(0xFF173F3A), Color(0xFF4A2E24)),
    fill = Color(0xFF2A2D36).copy(alpha = 0.50f),
    fillStrong = Color(0xFF2E323C).copy(alpha = 0.74f),
    fillFallback = Color(0xF21C1E25),
    rimLight = Color.White.copy(alpha = 0.30f),
    rimShade = Color.White.copy(alpha = 0.05f),
    shadow = Color.Black.copy(alpha = 0.50f),
    textMuted = Color(0xFFA9AFBD),
    textFaint = Color(0xFF7A808E),
    success = Color(0xFF5ED394),
    warning = Color(0xFFFFB44D),
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

private val Light = lightColorScheme(
    primary = Color(0xFF0A6CFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF0A2A66),
    secondary = Color(0xFF5E5CE6), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E4FF), onSecondaryContainer = Color(0xFF231F66),
    tertiary = Color(0xFF248A55), tertiaryContainer = Color(0xFFDDF3E6),
    background = Color(0xFFF1F3F8), onBackground = Color(0xFF15171C),
    surface = Color(0xFFF8F9FC), onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFE7EAF1), onSurfaceVariant = Color(0xFF5B6272),
    surfaceContainerHigh = Color(0xFFF4F6FA), surfaceContainerHighest = Color(0xFFEDEFF5),
    surfaceContainerLow = Color(0xFFF6F7FA), surfaceContainer = Color(0xFFF2F4F8),
    outline = Color(0xFFC4C9D4), outlineVariant = Color(0xFFDDE1E8),
    error = Color(0xFFD92D20), onError = Color.White, errorContainer = Color(0xFFFDE7E5), onErrorContainer = Color(0xFF6B140D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EA2FF), onPrimary = Color(0xFF00214F),
    primaryContainer = Color(0xFF173A6E), onPrimaryContainer = Color(0xFFD7E5FF),
    secondary = Color(0xFFA9A6FF), onSecondary = Color(0xFF1E1A5C),
    secondaryContainer = Color(0xFF2F2C66), onSecondaryContainer = Color(0xFFE4E2FF),
    tertiary = Color(0xFF5ED394), tertiaryContainer = Color(0xFF14432B),
    background = Color(0xFF0B0D12), onBackground = Color(0xFFECEEF3),
    surface = Color(0xFF15171D), onSurface = Color(0xFFECEEF3),
    surfaceVariant = Color(0xFF262A33), onSurfaceVariant = Color(0xFFA9AFBD),
    surfaceContainerHigh = Color(0xFF1E2128), surfaceContainerHighest = Color(0xFF262A33),
    surfaceContainerLow = Color(0xFF15171D), surfaceContainer = Color(0xFF1A1C22),
    outline = Color(0xFF4A505C), outlineVariant = Color(0xFF30343D),
    error = Color(0xFFFF6B5E), onError = Color(0xFF4A0904), errorContainer = Color(0xFF5A1A14), onErrorContainer = Color(0xFFFFDAD5),
)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** 数字等宽，时间和周次切换时不会左右跳动。 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable fun YangzhouTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // 仅影响 MainActivity 窗口外观：内容延伸到系统栏下方，供浮空玻璃底栏使用。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.Transparent.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.Transparent.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalGlass provides if (dark) DarkGlass else LightGlass) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography) {
            // 页面直接绘制在玻璃背景上而非 Surface，需手动提供默认文字颜色，否则深色模式文字会变黑。
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}
