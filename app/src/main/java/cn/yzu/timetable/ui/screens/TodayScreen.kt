package cn.yzu.timetable.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class LessonPhase { Ongoing, Upcoming, Finished }

private data class TodayItem(val lesson: ScheduleLesson, val start: PeriodSlot?, val end: PeriodSlot?, val phase: LessonPhase)

private fun classify(lessons: List<ScheduleLesson>, season: ScheduleSeason, fallback: List<cn.yzu.timetable.data.model.PeriodTime>, now: LocalTime): List<TodayItem> =
    lessons.map { lesson ->
        val range = YzuTimetable.lessonRange(lesson, season, fallback)
        val phase = when {
            range == null -> LessonPhase.Upcoming
            now.isBefore(range.first.start) -> LessonPhase.Upcoming
            now.isBefore(range.second.end) -> LessonPhase.Ongoing
            else -> LessonPhase.Finished
        }
        TodayItem(lesson, range?.first, range?.second, phase)
    }.sortedWith(compareBy<TodayItem> { it.phase.ordinal }.thenBy { it.lesson.startPeriod }.thenBy { it.lesson.endPeriod })

@Composable fun TodayScreen(state: AppUiState, onProfile: () -> Unit, onDismissGuide: () -> Unit, onLesson: (ScheduleLesson) -> Unit, previewTime: LocalTime? = null) {
    val prefs = LocalDisplayPreferences.current
    val glass = LocalGlass.current
    val clock by rememberNow()
    val now = previewTime ?: clock
    var settingsOpen by remember { mutableStateOf(false) }
    if (settingsOpen) TodaySettingsSheet { settingsOpen = false }
    val season = YzuTimetable.season(state.today, prefs.seasonMode)
    val week = state.semester?.weekOn(state.today)
    val items = remember(state.todayLessons, season, state.periods, now) { classify(state.todayLessons, season, state.periods, now) }
    val finished = items.count { it.phase == LessonPhase.Finished }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = LocalBottomBarSpace.current + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("header") {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 4.dp)) {
                Text(state.today.format(DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE)) + (week?.let { " · 第 $it 周" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.today.format(DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    GlassIconButton(if (prefs.reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.Tune, "今日设置", { settingsOpen = true }, size = 40.dp)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("${season.label}作息", color = glass.textMuted, icon = Icons.Default.Schedule)
                    if (items.isNotEmpty()) InfoChip("${items.size} 门课 · 已上 $finished", icon = Icons.Default.AutoStories)
                }
            }
        }
        if (!state.onboarded) item("guide") {
            GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), contentPadding = PaddingValues(20.dp)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                Text("欢迎使用 YZUA", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("先到“我的”完成学校统一认证。导入一次后，课表、成绩和考试安排保存在本机，下次打开即可查看。", style = MaterialTheme.typography.bodyMedium, color = glass.textMuted)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryCapsuleButton("去我的完成登录", onProfile)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onDismissGuide) { Text("稍后设置") }
                }
            }
        }
        state.semesterReminder?.let { message -> item("reminder") {
            StatusBanner(message, Icons.Default.EventNote, BannerTone.Warning, action = "前往", onAction = onProfile)
        } }
        if (state.loading) item("loading") { LoadingStrip(true) }
        when {
            state.scheduleUpdated == null -> item("empty") {
                EmptyState(Icons.Default.CalendarMonth, "还没有导入课表", "连接校园网或手机 VPN 后，在“我的”中登录导入。", action = "前往我的", onAction = onProfile)
            }
            state.semester == null -> item("nosemester") {
                EmptyState(Icons.Default.EditCalendar, "请先校准学期日期", "课表已保存。在“我的”中标定开学第一周后，就能按日期显示今日课程。", action = "去校准", onAction = onProfile)
            }
            week == null -> item("outside") {
                EmptyState(Icons.Default.BeachAccess, "今天不在学期内", "今天不在保存的学期日期范围内，暂不套用旧课表。")
            }
            items.isEmpty() -> item("free") {
                EmptyState(Icons.Default.WbSunny, "今天没有课程", "按已保存的本学期课表筛选，好好安排这一天。")
            }
        }
        val ongoing = items.filter { it.phase == LessonPhase.Ongoing }
        val upcoming = items.filter { it.phase == LessonPhase.Upcoming }
        val done = items.filter { it.phase == LessonPhase.Finished }
        if (ongoing.isNotEmpty()) item("l-ongoing") { SectionLabel("正在上课") }
        items(ongoing, key = { "o" + it.lesson.id + it.lesson.startPeriod }) { OngoingCard(it, now, onLesson, Modifier.animateItem()) }
        if (upcoming.isNotEmpty()) item("l-up") { SectionLabel("待上", trailing = "${upcoming.size} 门") }
        items(upcoming, key = { "u" + it.lesson.id + it.lesson.startPeriod }) { item ->
            LessonRow(item, now, onLesson, Modifier.animateItem(), highlight = item === upcoming.first())
        }
        if (done.isNotEmpty()) item("l-done") { SectionLabel("已上", trailing = "${done.size} 门") }
        items(done, key = { "d" + it.lesson.id + it.lesson.startPeriod }) { LessonRow(it, now, onLesson, Modifier.animateItem()) }
        state.error?.let { item("error") { StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error) } }
        state.scheduleUpdated?.let { item("footer") {
            Text("课表更新于 $it · 数据保存在本机", style = MaterialTheme.typography.labelSmall, color = glass.textFaint, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        } }
    }
}

private fun minutesText(minutes: Long): String = when {
    minutes < 1 -> "不到 1 分钟"
    minutes < 60 -> "$minutes 分钟"
    else -> "${minutes / 60} 小时" + if (minutes % 60 != 0L) " ${minutes % 60} 分" else ""
}

@Composable private fun OngoingCard(item: TodayItem, now: LocalTime, onLesson: (ScheduleLesson) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val primary = MaterialTheme.colorScheme.primary
    val start = item.start ?: return
    val end = item.end ?: return
    val totalSec = ChronoUnit.SECONDS.between(start.start, end.end).coerceAtLeast(1)
    val progressTarget = ChronoUnit.SECONDS.between(start.start, now).toFloat() / totalSec
    val progress by animateFloatAsState(progressTarget, tween(900, easing = FastOutSlowInEasing), label = "lessonProgress")
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    GlassCard(modifier.fillMaxWidth(), onClick = { onLesson(item.lesson) }, tint = primary.copy(alpha = if (glass.dark) 0.16f else 0.09f), strong = true, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(pulse.value).clip(CircleShape).background(primary))
            Spacer(Modifier.width(8.dp))
            Text("进行中 · 第 ${item.lesson.startPeriod}–${item.lesson.endPeriod} 节", style = MaterialTheme.typography.labelLarge, color = primary, modifier = Modifier.weight(1f))
            Text("还剩 " + minutesText(ChronoUnit.MINUTES.between(now, end.end) + 1), style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(item.lesson.courseName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(item.lesson.classroom.ifBlank { "未提供地点" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (item.lesson.teacher.isNotBlank()) Text(item.lesson.teacher, style = MaterialTheme.typography.bodySmall, color = glass.textFaint, maxLines = 1)
        }
        Spacer(Modifier.height(16.dp))
        GlassProgress(progress, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row {
            Text(start.startText, style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = glass.textMuted, modifier = Modifier.weight(1f))
            Text("${(progressTarget.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = glass.textFaint)
            Text(end.endText, style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = glass.textMuted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable private fun LessonRow(item: TodayItem, now: LocalTime, onLesson: (ScheduleLesson) -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val glass = LocalGlass.current
    val colors = courseColors(item.lesson.colorIndex, glass.dark)
    val finished = item.phase == LessonPhase.Finished
    GlassCard(modifier.fillMaxWidth().alpha(if (finished) 0.6f else 1f), onClick = { onLesson(item.lesson) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(58.dp)) {
                Text(item.start?.startText ?: "第${item.lesson.startPeriod}节", style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), fontWeight = FontWeight.Bold)
                Text(item.end?.endText ?: "", style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = glass.textFaint)
            }
            Box(Modifier.padding(horizontal = 10.dp).width(4.dp).height(46.dp).clip(CircleShape).background(colors.accent))
            Column(Modifier.weight(1f)) {
                Text(item.lesson.courseName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = glass.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(item.lesson.classroom.ifBlank { "未提供地点" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(listOf("第 ${item.lesson.startPeriod}–${item.lesson.endPeriod} 节", item.lesson.teacher).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = glass.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                finished -> Icon(Icons.Default.CheckCircle, "已上", tint = glass.success.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                highlight && item.start != null -> {
                    val wait = ChronoUnit.MINUTES.between(now, item.start.start) + 1
                    if (wait <= 180) InfoChip(minutesText(wait) + "后")
                }
            }
        }
    }
}
