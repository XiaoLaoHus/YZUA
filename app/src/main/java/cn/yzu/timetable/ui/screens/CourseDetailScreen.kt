package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yzu.timetable.data.model.PeriodTime
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.AmbientBackground
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.LocalDate

private val dayLabel = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 把周次集合压缩成“1-8, 10-16 周”，严格单/双周时额外标注。 */
internal fun weeksText(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return "未提供"
    if (sorted.size > 2 && sorted.zipWithNext().all { (a, b) -> b - a == 2 })
        return "第 ${sorted.first()}-${sorted.last()} 周（${if (sorted.first() % 2 == 1) "单" else "双"}周）"
    val parts = mutableListOf<String>()
    var start = sorted.first(); var prev = start
    sorted.drop(1).forEach { w -> if (w != prev + 1) { parts += if (start == prev) "$start" else "$start-$prev"; start = w }; prev = w }
    parts += if (start == prev) "$start" else "$start-$prev"
    return "第 ${parts.joinToString(", ")} 周"
}

@Composable fun CourseDetailScreen(lesson: ScheduleLesson, onBack: () -> Unit, periods: List<PeriodTime> = emptyList(), currentWeek: Int? = null, today: LocalDate = LocalDate.now()) {
    val glass = LocalGlass.current
    val colors = courseColors(lesson.colorIndex, glass.dark)
    val season = YzuTimetable.season(today, LocalDisplayPreferences.current.seasonMode)
    val range = YzuTimetable.lessonRange(lesson, season, periods)
    AmbientBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text("课程详情", style = MaterialTheme.typography.titleLarge)
            }
            GlassCard(Modifier.fillMaxWidth(), tint = colors.container.copy(alpha = 0.75f), strong = true, contentPadding = PaddingValues(22.dp)) {
                Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(colors.accent))
                Spacer(Modifier.height(14.dp))
                Text(lesson.courseName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                if (lesson.courseCode.isNotBlank()) Text(lesson.courseCode, style = MaterialTheme.typography.labelLarge, color = colors.content)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(range?.let { "${it.first.startText} – ${it.second.endText}" } ?: "第 ${lesson.startPeriod}-${lesson.endPeriod} 节",
                        style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers))
                }
                Text("${dayLabel.getOrElse(lesson.dayOfWeek - 1) { "周${lesson.dayOfWeek}" }} · 第 ${lesson.startPeriod}-${lesson.endPeriod} 节 · ${season.label}作息",
                    style = MaterialTheme.typography.bodyMedium, color = glass.textMuted)
            }
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                DetailLine(Icons.Default.Place, "上课地点", lesson.classroom.ifBlank { "未提供" }, emphasize = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailLine(Icons.Default.Person, "任课教师", lesson.teacher.ifBlank { "未提供" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailLine(Icons.Default.DateRange, "上课周次", weeksText(lesson.weekNumbers))
            }
            val maxWeek = maxOf(lesson.weekNumbers.maxOrNull() ?: 0, 16, currentWeek ?: 0).coerceAtMost(30)
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Text("周次分布", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
                Spacer(Modifier.height(10.dp))
                (1..maxWeek).chunked(8).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { w ->
                            val on = lesson.occursInWeek(w)
                            Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (on) colors.accent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center) {
                                Text("$w", fontSize = 12.sp, fontWeight = if (w == currentWeek) FontWeight.Black else FontWeight.Medium, style = TabularNumbers,
                                    color = if (on) androidx.compose.ui.graphics.Color.White else glass.textFaint)
                            }
                        }
                        repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (currentWeek != null) Text("加粗数字为当前周（第 $currentWeek 周）", style = MaterialTheme.typography.labelSmall, color = glass.textFaint, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable private fun DetailLine(icon: ImageVector, label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textFaint)
            Text(value, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        }
    }
}
