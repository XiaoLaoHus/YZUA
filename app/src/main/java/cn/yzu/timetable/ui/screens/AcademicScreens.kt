package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.data.model.ExamSchedule

@Composable private fun AcademicHeader(title: String, subtitle: String, state: AppUiState, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "刷新查询") }
    }
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    Spacer(Modifier.height(14.dp))
}

@Composable fun ScoresScreen(state: AppUiState, onRefresh: () -> Unit, onScopeChange: (Boolean) -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        AcademicHeader("成绩查询", "扬大 URP 成绩查询", state, onRefresh)
        AcademicRefreshNotice(state, state.scoresUpdated, onLogin)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!state.allScoreTerms, { onScopeChange(false) }, { Text("本学期") }, enabled = !state.loading)
            FilterChip(state.allScoreTerms, { onScopeChange(true) }, { Text("全部学期") }, enabled = !state.loading)
        }
        state.scoreError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 10.dp)) }
        if (state.scoresLoaded && state.scores.isEmpty() && state.scoreError == null && !state.loading) {
            Text("查询范围内暂无成绩记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.scores) { score ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(score.courseName) },
                        supportingContent = { Text(listOf(score.courseCode, score.property, "学分 ${score.credit.ifBlank { "—" }}").filter(String::isNotBlank).joinToString(" · ")) },
                        trailingContent = { Text(score.score.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                    )
                    if (score.termLabel.isNotBlank()) Text(score.termLabel, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                    val stats = listOf("最高" to score.maxScore, "最低" to score.minScore, "平均" to score.averageScore)
                        .filter { it.second.isNotBlank() }.joinToString("  ·  ") { "${it.first} ${it.second}" }
                    if (stats.isNotBlank()) Text(stats, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    if (score.unpassedReason.isNotBlank()) Text(score.unpassedReason, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable fun ExamsScreen(state: AppUiState, onRefresh: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        AcademicHeader("考试安排", "查询当前日期前后约三个月", state, onRefresh)
        AcademicRefreshNotice(state, state.examsUpdated, onLogin)
        state.examError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 10.dp)) }
        if (state.examsLoaded && state.exams.isEmpty() && state.examError == null && !state.loading) {
            Text("查询时间内没有和你相关的考试信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.exams.sortedBy(ExamSchedule::startTime)) { exam ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(exam.courseName) },
                        supportingContent = { Text(listOf(
                            "开始：${exam.startTime.replace('T', ' ')}",
                            exam.endTime.takeIf(String::isNotBlank)?.let { "结束：${it.replace('T', ' ')}" }.orEmpty(),
                            exam.location.takeIf(String::isNotBlank)?.let { "地点：$it" }.orEmpty(),
                            exam.note,
                        ).filter(String::isNotBlank).joinToString("\n")) },
                    )
                }
            }
        }
    }
}

@Composable private fun AcademicRefreshNotice(state: AppUiState, updated: String?, onLogin: () -> Unit) {
    Text(updated?.let { "本地信息 · 更新于 $it" } ?: "尚未保存此范围的信息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.session == null) {
        Text("需要最新信息时，请登录一次进行刷新。", style = MaterialTheme.typography.bodySmall)
        TextButton(onLogin, enabled = !state.loading) { Text("前往我的登录刷新") }
    }
    Spacer(Modifier.height(8.dp))
}
