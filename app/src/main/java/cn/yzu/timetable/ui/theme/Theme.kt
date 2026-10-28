package cn.yzu.timetable.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF3390EC)
private val Purple = Color(0xFF6C63B5)
private val Light = lightColorScheme(primary = Blue, secondary = Purple, background = Color(0xFFF6F7FA), surface = Color.White)
private val Dark = darkColorScheme(primary = Color(0xFF8BC8FF), secondary = Color(0xFFC9C2FF))
@Composable fun YangzhouTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) Dark else Light, typography = Typography(), content = content) }
