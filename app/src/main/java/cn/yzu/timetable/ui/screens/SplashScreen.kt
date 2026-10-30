package cn.yzu.timetable.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.R
import cn.yzu.timetable.ui.components.YzuaLogo
import cn.yzu.timetable.ui.theme.AmbientBackground
import cn.yzu.timetable.ui.theme.LocalGlass

/** 进程级闸门：只在冷启动显示一次开屏。 */
internal object ColdStartGate { @Volatile var splashShown = false }

/** 开屏页：张謇先生训辞“坚苦自立”，背景与 App 的玻璃底色统一。 */
@Composable fun SplashScreen(preview: Boolean = false) {
    val glass = LocalGlass.current
    var entered by remember { mutableStateOf(preview) }
    LaunchedEffect(Unit) { entered = true }
    val rise by animateFloatAsState(if (entered) 1f else 0.86f, tween(900, easing = FastOutSlowInEasing), label = "splashScale")
    val fade by animateFloatAsState(if (entered) 1f else 0f, tween(700), label = "splashFade")
    AmbientBackground {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.splash_motto), "坚苦自立",
                Modifier.height(300.dp).graphicsLayer { alpha = fade; scaleX = rise; scaleY = rise })
            Spacer(Modifier.height(18.dp))
            Text("坚苦自立", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = fade })
            Spacer(Modifier.height(6.dp))
            Text("—— 张謇 训辞", style = MaterialTheme.typography.bodySmall, color = glass.textMuted,
                modifier = Modifier.graphicsLayer { alpha = fade })
            Spacer(Modifier.height(48.dp))
            YzuaLogo(44.dp, Modifier.graphicsLayer { alpha = fade * 0.9f })
        }
    }
}
