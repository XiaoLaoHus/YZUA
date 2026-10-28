package cn.yzu.timetable.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.yzu.timetable.reminder.ClassMute
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass

private val leadOptions = listOf(5, 10, 15, 30)

private fun openSafely(context: Context, intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TodaySettingsSheet(onDismiss: () -> Unit) {
    val prefs = LocalDisplayPreferences.current
    val glass = LocalGlass.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationsOn = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val policyAccess = remember(tick) { ClassMute.hasPolicyAccess(context) }
    val exactAlarms = remember(tick) { Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun requestNotifications() {
        prefs.markNotificationAsked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openSafely(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("今日设置", style = MaterialTheme.typography.headlineSmall)
            GlassCard(strong = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SwitchRow(Icons.Default.NotificationsActive, "上课提醒", "上课前推送通知，显示课程、时间和地点", prefs.reminderEnabled) {
                    prefs.updateReminder(it); if (it && !notificationsOn) requestNotifications()
                }
            }
            if (prefs.reminderEnabled) {
                Text("提前提醒", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
                GlassSegmented(leadOptions.map { "$it 分钟" }, leadOptions.indexOf(prefs.reminderLead).coerceAtLeast(0), { prefs.updateLead(leadOptions[it]) }, Modifier.fillMaxWidth())
                if (!notificationsOn) StatusBanner("通知权限未开启，提醒无法显示", Icons.Default.NotificationsOff, BannerTone.Warning, action = "去开启", onAction = ::requestNotifications)
                if (!exactAlarms) StatusBanner("系统限制了精确闹钟，提醒可能晚几分钟", Icons.Default.Schedule, BannerTone.Info, action = "去设置",
                    onAction = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) openSafely(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) })
            }
            GlassCard(strong = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SwitchRow(Icons.Default.VolumeOff, "上课自动静音", "上课时静音，下课自动恢复铃声", prefs.autoMute, prefs::updateAutoMute)
            }
            if (prefs.autoMute) {
                if (policyAccess) StatusBanner("已获得勿扰权限：上课时切换为静音", Icons.Default.CheckCircle, BannerTone.Success)
                else StatusBanner("未获得勿扰权限，上课时将改为振动；授权后可完全静音", Icons.Default.Vibration, BannerTone.Info, action = "授权",
                    onAction = { openSafely(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) })
                Text("仅在铃声为响铃模式时生效；若你在课上手动调整了铃声，下课时不会被覆盖。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
            }
            Text("提醒和静音按本机保存的课表与扬大作息计算，不联网；更新课表或校准学期后自动重新安排。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
        }
    }
}

@Composable private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textMuted)
        }
        Switch(checked, onChange)
    }
}
