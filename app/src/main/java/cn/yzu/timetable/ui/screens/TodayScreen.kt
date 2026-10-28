package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.AppUiState
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun TodayScreen(state: AppUiState, onProfile: () -> Unit, onDismissGuide: () -> Unit, onLesson: (ScheduleLesson) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
        item {
            Text("今日", style = MaterialTheme.typography.headlineLarge)
            Text(state.today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.semester?.weekOn(state.today)?.let { "第 $it 周 · ${state.todayLessons.size} 门课程" } ?: "使用本机日期 · 离线查看", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        }
        if (!state.onboarded) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.School, null)
                    Text("欢迎使用扬大课表", style = MaterialTheme.typography.titleLarge)
                    Text("先到“我的”完成学校统一认证。导入一次后，课表、成绩和考试安排保存在本机，下次打开即可查看。")
                    Button(onProfile) { Text("去我的完成登录") }
                    TextButton(onDismissGuide) { Text("稍后设置") }
                }
            }
        }
        state.semesterReminder?.let { message -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) { Text(message); TextButton(onProfile) { Text("前往我的设置 / 刷新") } }
            }
        } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.scheduleUpdated == null) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                Text("还没有导入课表", style = MaterialTheme.typography.titleMedium)
                Text("连接校园网或手机 VPN 后，在“我的”中登录导入。", modifier = Modifier.padding(top = 8.dp))
                TextButton(onProfile) { Text("前往我的") }
            } }
        } else if (state.semester == null) item { Text("课表已保存，请先在“我的”中校准首周日期。") }
        else if (state.semester.weekOn(state.today) == null) item { Text("今天不在保存的学期日期范围内，暂不套用旧课表。") }
        else if (state.todayLessons.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
                Text("今天没有课程", style = MaterialTheme.typography.titleLarge)
                Text("按已保存的本学期课表筛选", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        items(state.todayLessons) { lesson ->
            Card(Modifier.fillMaxWidth().clickable { onLesson(lesson) }) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("第 ${lesson.startPeriod}–${lesson.endPeriod} 节", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(lesson.courseName, style = MaterialTheme.typography.titleLarge)
                    Text(lesson.classroom, style = MaterialTheme.typography.bodyLarge)
                    if (lesson.teacher.isNotBlank()) Text(lesson.teacher, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.scheduleUpdated?.let { item { Text("课表更新于 $it · 数据保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}
