package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.components.*
import kotlinx.coroutines.launch
import cn.yzu.timetable.ui.theme.LocalGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ThemeModeSheet(onDismiss: () -> Unit) {
    val prefs = LocalDisplayPreferences.current
    val glass = LocalGlass.current
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("显示模式", style = MaterialTheme.typography.headlineSmall)
            Text("切换后立即生效，并保存在本机。", style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            GlassSegmented(ThemeMode.entries.map { it.label }, ThemeMode.entries.indexOf(prefs.themeMode), { prefs.updateThemeMode(ThemeMode.entries[it]) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UpdateSheet(onDismiss: () -> Unit) {
    val prefs = LocalDisplayPreferences.current
    val glass = LocalGlass.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val checking = UpdateCenter.checking.value
    val result = UpdateCenter.result.value
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("检查更新", style = MaterialTheme.typography.headlineSmall)
            GlassCard(strong = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SwitchRow(Icons.Default.SystemUpdate, "启动时自动检查", "每次冷启动尝试访问 GitHub Releases", prefs.updateCheckEnabled, prefs::updateUpdateCheck)
            }
            PrimaryCapsuleButton(if (checking) "正在检查…" else "立即检查", { scope.launch { UpdateCenter.run() } }, Modifier.fillMaxWidth(),
                enabled = !checking, loading = checking, icon = Icons.Default.SystemUpdate)
            when (val r = result) {
                is UpdateChecker.Result.UpdateAvailable -> StatusBanner("发现新版本 v${r.version}（本机 v${UpdateChecker.currentVersion()}）", Icons.Default.SystemUpdate, BannerTone.Info,
                    action = "去更新", onAction = { runCatching { uri.openUri(r.url) } })
                is UpdateChecker.Result.UpToDate -> StatusBanner("已是最新版本 v${UpdateChecker.currentVersion()}", Icons.Default.CheckCircle, BannerTone.Success)
                is UpdateChecker.Result.Failed -> StatusBanner(UpdateChecker.failureText(context, r), Icons.Default.ErrorOutline, BannerTone.Warning)
                null -> Text("尚未检查。GitHub 访问可能较慢，超时会自动放弃，不影响使用。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
            }
            Text("更新包发布在 GitHub Releases，均为正式签名 APK，可直接覆盖安装。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
        }
    }
}

@Composable internal fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textMuted)
        }
        Switch(checked, onChange)
    }
}
