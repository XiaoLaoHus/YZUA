package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.R
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass

private const val DEVELOPER_EMAIL = "xiaolaohus@qq.com"
private const val REPOSITORY_URL = "https://github.com/XiaoLaoHus/YZUA"

@Composable fun AboutScreen(onBack: () -> Unit) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = LocalBottomBarSpace.current + 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubPageHeader("关于", null, onBack)
        GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                YzuaLogo(80.dp)
                Spacer(Modifier.height(14.dp))
                Text("YZUA", style = MaterialTheme.typography.headlineMedium)
                Text("Yangzhou University Agent", style = MaterialTheme.typography.bodyMedium, color = glass.textMuted)
                if (version != null) { Spacer(Modifier.height(8.dp)); InfoChip("版本 $version") }
            }
        }
        SectionLabel("开发者")
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.developer_avatar), "小老虎s 的头像", Modifier.size(60.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("小老虎s", style = MaterialTheme.typography.titleLarge)
                    Text("独立开发者", style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            LinkRow(Icons.Default.Email, Color(0xFF0A84FF), "联系邮箱", DEVELOPER_EMAIL) { runCatching { uri.openUri("mailto:$DEVELOPER_EMAIL") } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LinkRow(Icons.Default.Code, Color(0xFF24292F), "GitHub 开源地址", "github.com/XiaoLaoHus/YZUA") { runCatching { uri.openUri(REPOSITORY_URL) } }
        }
        SectionLabel("使用须知")
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            disclaimerPoints.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = glass.textMuted, modifier = Modifier.padding(vertical = 3.dp)) }
        }
    }
}

@Composable private fun LinkRow(icon: ImageVector, color: Color, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = if (LocalGlass.current.dark) 0.35f else 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (LocalGlass.current.dark) Color.White else color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textFaint)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = LocalGlass.current.textFaint, modifier = Modifier.size(18.dp))
    }
}
