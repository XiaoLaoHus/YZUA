package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers

@Composable fun ProfileScreen(state: AppUiState, vm: AppViewModel, onLogin: () -> Unit, onCalibrate: () -> Unit) {
    val glass = LocalGlass.current
    var clearOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = LocalBottomBarSpace.current + 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("我的")
        IdentityCard(state)
        LoadingStrip(state.loading)
        if (state.session == null) {
            PrimaryCapsuleButton("统一认证登录 / 刷新信息", onLogin, Modifier.fillMaxWidth(), enabled = !state.loading, icon = Icons.Default.Login)
        } else {
            PrimaryCapsuleButton("刷新课表、成绩与考试", vm::refresh, Modifier.fillMaxWidth(), enabled = !state.loading, icon = Icons.Default.Refresh, loading = state.loading)
            GlassCapsuleButton("退出登录（保留本地数据）", vm::logout, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Logout, color = MaterialTheme.colorScheme.onSurface)
        }
        Text("启动不自动登录。成绩和考试需要更新时，登录一次再刷新即可。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint, modifier = Modifier.padding(horizontal = 6.dp))
        state.error?.let { StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error) }

        SectionLabel("学期")
        SemesterCard(state, onCalibrate)
        state.semesterReminder?.let { StatusBanner(it, Icons.Default.EventNote, BannerTone.Warning, action = "校准", onAction = onCalibrate) }

        SectionLabel("本地数据")
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            DataRow(Icons.Default.CalendarMonth, Color(0xFF0A84FF), "课表", state.scheduleUpdated)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DataRow(Icons.Default.Assessment, Color(0xFF34A853), "当前范围成绩", state.scoresUpdated)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DataRow(Icons.Default.EventAvailable, Color(0xFFFF9500), "考试安排", state.examsUpdated)
        }
        GlassCapsuleButton("清除本地数据 / 更换账号", { clearOpen = true }, Modifier.fillMaxWidth(), enabled = !state.loading,
            icon = Icons.Default.DeleteOutline, color = MaterialTheme.colorScheme.error)
        Text("查询结果仅保存在应用私有目录，不备份到云端；账号密码与 API Cookie 不随查询结果保存。更换账号前请先清除本地数据。",
            style = MaterialTheme.typography.bodySmall, color = glass.textFaint, modifier = Modifier.padding(horizontal = 6.dp))
        Text("YZUA · Yangzhou University Agent", style = MaterialTheme.typography.labelMedium, color = glass.textFaint,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
    }
    if (clearOpen) AlertDialog(onDismissRequest = { clearOpen = false }, shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("清除本机查询结果？") },
        text = { Text("会清除课表、成绩、考试安排和学期设置，并退出当前登录。之后可重新登录导入。") },
        confirmButton = { TextButton({ vm.clearLocalData(); clearOpen = false }) { Text("清除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton({ clearOpen = false }) { Text("取消") } })
}

@Composable private fun IdentityCard(state: AppUiState) {
    val glass = LocalGlass.current
    val name = state.session?.displayName ?: "YZUA"
    val (status, color) = when {
        state.session != null -> "本次登录有效 · 可在线刷新" to glass.success
        state.scheduleUpdated != null -> "离线查看本地数据" to MaterialTheme.colorScheme.primary
        else -> "登录一次，导入课表与查询结果" to glass.textMuted
    }
    GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4F8DFF), Color(0xFF8A7CFF), Color(0xFF5ED3C4)))),
                contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
                }
            }
        }
    }
}

@Composable private fun SemesterCard(state: AppUiState, onCalibrate: () -> Unit) {
    val glass = LocalGlass.current
    val semester = state.semester
    val dates = runCatching { semester?.let { it.start() to it.end() } }.getOrNull()
    GlassCard(Modifier.fillMaxWidth(), onClick = onCalibrate, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EditCalendar, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("学期日期与周次校准", style = MaterialTheme.typography.titleMedium)
                Text(if (dates != null) "首周 ${semester!!.firstMonday} · 结束 ${semester.lastDay}" else "用日历点选开学第一周和最后一周",
                    style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            }
            if (semester != null && !semester.confirmed) InfoChip("待确认", color = glass.warning)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = glass.textFaint)
        }
        if (semester != null && dates != null) {
            val total = runCatching { semester.totalWeeks() }.getOrDefault(0).coerceAtLeast(1)
            val week = semester.weekOn(state.today)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(when {
                    week != null -> "第 $week 周"
                    state.today.isBefore(dates.first) -> "尚未开学"
                    else -> "学期已结束"
                }, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), modifier = Modifier.weight(1f))
                Text("共 $total 周", style = MaterialTheme.typography.labelMedium, color = glass.textFaint)
            }
            Spacer(Modifier.height(8.dp))
            GlassProgress(when { week != null -> week.toFloat() / total; state.today.isBefore(dates.first) -> 0f; else -> 1f }, Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun DataRow(icon: ImageVector, color: Color, title: String, updated: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(updated?.let { "更新于 $it" } ?: "尚未导入", style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textMuted)
    }
}
