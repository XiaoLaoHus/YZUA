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
import cn.yzu.timetable.reminder.ClassReminders
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
                val upcoming = remember(tick, prefs.reminderLead, prefs.reminderEnabled, prefs.seasonMode) { cn.yzu.timetable.reminder.ClassReminders.nextEvents(context) }
                GlassCard(strong = true, contentPadding = PaddingValues(16.dp)) {
                    Text("提醒队列", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
                    Spacer(Modifier.height(6.dp))
                    if (upcoming.isEmpty()) Text("近期没有可提醒的课程：本学期课表未导入、今日无课，或本周课程已全部结束。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
                    else upcoming.forEach { e ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${e.at.monthValue}/${e.at.dayOfMonth} ${ClassReminders.formatTime(e.at.toLocalTime())} · ${e.lesson.courseName}",
                                style = MaterialTheme.typography.bodySmall.merge(cn.yzu.timetable.ui.theme.TabularNumbers))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassCapsuleButton("发送测试通知", {
                        if (!notificationsOn) requestNotifications()
                        else {
                            val lesson = upcoming.firstOrNull()?.lesson ?: cn.yzu.timetable.data.model.ScheduleLesson("test", "", "测试课程", "", "测试地点", 1, 1, 1, 1..1, 0, setOf(1))
                            val at = java.time.LocalDateTime.now()
                            cn.yzu.timetable.reminder.ClassNotifier.notify(context,
                                cn.yzu.timetable.reminder.ClassReminders.Event(at, cn.yzu.timetable.reminder.ClassReminders.Kind.Remind, lesson,
                                    at.plusMinutes(prefs.reminderLead.toLong()), at.plusMinutes(prefs.reminderLead.toLong() + 45)), at)
                            android.widget.Toast.makeText(context, "测试通知已发送", android.widget.Toast.LENGTH_SHORT).show()
                            tick++
                        }
                    }, Modifier.fillMaxWidth(), icon = Icons.Default.NotificationsActive)
                }
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
