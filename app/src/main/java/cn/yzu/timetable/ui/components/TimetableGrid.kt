package cn.yzu.timetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yzu.timetable.data.model.ScheduleLesson

private val palette = listOf(Color(0xFFE0F2FE), Color(0xFFEDE9FE), Color(0xFFDCFCE7), Color(0xFFFFEDD5), Color(0xFFFCE7F3), Color(0xFFE0E7FF), Color(0xFFFEF3C7))
private val accents = listOf(Color(0xFF0284C7), Color(0xFF7C3AED), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFFDB2777), Color(0xFF4F46E5), Color(0xFFD97706))

@Composable fun TimetableGrid(week: Int, lessons: List<ScheduleLesson>, onLessonClick: (ScheduleLesson) -> Unit) {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(38.dp)) { Spacer(Modifier.height(42.dp)); for (period in 1..12) { Box(Modifier.height(56.dp).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(period.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        days.forEachIndexed { dayIndex, day -> Column(Modifier.width(92.dp)) {
            Box(Modifier.height(42.dp).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("周$day", style = MaterialTheme.typography.labelMedium) }
            for (period in 1..12) {
                val lesson = lessons.firstOrNull { it.dayOfWeek == dayIndex + 1 && it.startPeriod == period && it.occursInWeek(week) }
                val occupied = lessons.any { it.dayOfWeek == dayIndex + 1 && period > it.startPeriod && period <= it.endPeriod && it.occursInWeek(week) }
                if (occupied && lesson == null) continue
                Box(Modifier.height(56.dp).fillMaxWidth().padding(2.dp)) {
                    if (lesson != null) Card(Modifier.fillMaxSize().clickable { onLessonClick(lesson) }, colors = CardDefaults.cardColors(containerColor = palette[lesson.colorIndex % palette.size]), shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(7.dp)) { Text(lesson.courseName, style = MaterialTheme.typography.labelMedium, color = accents[lesson.colorIndex % accents.size], maxLines = 2); Spacer(Modifier.height(2.dp)); Text(lesson.classroom, style = MaterialTheme.typography.labelSmall, maxLines = 1) } }
                }
            }
        } }
    }
}
