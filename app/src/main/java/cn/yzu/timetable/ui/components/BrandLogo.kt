package cn.yzu.timetable.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.R

/** YZUA 品牌图标：与桌面自适应图标同源（扬大绿渐变底 + 书页课表前景）。 */
@Composable fun YzuaLogo(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(modifier.size(size).shadow(size * 0.18f, shape, ambientColor = Color(0xFF063A2A), spotColor = Color(0xFF063A2A)).clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFF0B5E3F), Color(0xFF063A2A))))) {
        Image(painterResource(R.drawable.ic_launcher_foreground), "YZUA", Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f })
    }
}
