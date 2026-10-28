package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.data.model.ScheduleLesson

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CourseDetailScreen(lesson: ScheduleLesson, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("课程详情") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding -> Column(Modifier.padding(padding).padding(20.dp)) {
        Text(lesson.courseName, style = MaterialTheme.typography.headlineSmall); Text(lesson.courseCode, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp)); DetailRow("教师", lesson.teacher); DetailRow("教室", lesson.classroom); DetailRow("上课时间", "周${lesson.dayOfWeek} · 第 ${lesson.startPeriod}-${lesson.endPeriod} 节"); DetailRow("上课周次", "第 ${lesson.weeks.first}-${lesson.weeks.last} 周")
    } }
}
@Composable private fun DetailRow(label: String, value: String) { ListItem(headlineContent = { Text(value) }, supportingContent = { Text(label) }) }
