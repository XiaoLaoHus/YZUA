package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.components.TimetableGrid

@Composable fun TimetableScreen(state: AppUiState, vm: AppViewModel, onLessonClick: (cn.yzu.timetable.data.model.ScheduleLesson) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("我的课表", style = MaterialTheme.typography.headlineMedium); Text(listOfNotNull(state.academicYear, state.term.label).joinToString(" "), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(state.session?.source?.label ?: "扬大 URP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }; IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "刷新") } }
        Spacer(Modifier.height(14.dp)); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { IconButton({ vm.shiftWeek(-1) }) { Icon(Icons.Default.ChevronLeft, "上一周") }; Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text("第 ${state.week} 周", style = MaterialTheme.typography.titleMedium); Text("点击左右切换周次", style = MaterialTheme.typography.labelSmall) }; IconButton({ vm.shiftWeek(1) }) { Icon(Icons.Default.ChevronRight, "下一周") } } }
        TextButton(vm::resetWeek) { Text("回到本周") }
        state.semesterReminder?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp)) }
        state.scheduleUpdated?.let { Text("本地课表 · 更新于 $it", style = MaterialTheme.typography.labelSmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 6.dp)) }
        TimetableGrid(state.week, state.lessons, state.periods, onLessonClick)
    }
}
