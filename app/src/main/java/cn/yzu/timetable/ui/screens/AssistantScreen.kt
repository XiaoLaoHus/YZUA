package cn.yzu.timetable.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.components.LocalBottomBarSpace
import cn.yzu.timetable.ui.theme.LocalGlass

/** 智能助手预留页：仅展示，不接入任何 AI 后端。 */
@Composable fun AssistantScreen(onBack: (() -> Unit)? = null) {
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "angle")
    val breathe by transition.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = LocalBottomBarSpace.current)) {
        if (onBack != null) SubPageHeader("智能助手", null, onBack)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(150.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    rotate(angle) {
                        drawCircle(Brush.sweepGradient(listOf(Color(0xFF6E8BFF), Color(0xFFB37CFF), Color(0xFFFF8FB1), Color(0xFF7FD6FF), Color(0xFF6E8BFF))), radius = size.minDimension / 2.4f)
                    }
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension / 2.4f)
                }
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("智能助手", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("智能问答功能即将上线\n先用课表页管理你的每一节课", style = MaterialTheme.typography.bodyLarge, color = LocalGlass.current.textMuted, textAlign = TextAlign.Center)
        }
    }
}
