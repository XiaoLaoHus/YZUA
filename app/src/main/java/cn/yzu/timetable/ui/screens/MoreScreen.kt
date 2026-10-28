package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.temporal.ChronoUnit

@Composable fun MoreScreen(state: AppUiState, onScores: () -> Unit, onExams: () -> Unit, onAssistant: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = LocalBottomBarSpace.current + 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("更多", "成绩、考试、空教室与智能助手")
        Spacer(Modifier.height(2.dp))
        val scoreValue = if (state.scores.isNotEmpty()) "${state.scores.size} 门" else "—"
        val scoreCaption = when {
            state.scores.isNotEmpty() -> (if (state.allScoreTerms) "全部学期" else "本学期") + "成绩"
            state.scoresLoaded -> "暂无成绩记录"
            else -> "尚未导入"
        }
        val nextExam = state.exams.mapNotNull { e -> parseExamTime(e.startTime)?.let { e to it } }
            .filter { !it.second.toLocalDate().isBefore(state.today) }.minByOrNull { it.second }
        val examValue = nextExam?.let { ChronoUnit.DAYS.between(state.today, it.second.toLocalDate()).let { d -> if (d == 0L) "今天" else if (d == 1L) "明天" else "$d 天后" } } ?: if (state.exams.isNotEmpty()) "${state.exams.size} 场" else "—"
        val examCaption = when {
            nextExam != null -> "下一场：" + nextExam.first.courseName
            state.exams.isNotEmpty() -> "考试均已结束"
            state.examsLoaded -> "近期没有考试"
            else -> "尚未导入"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HubTile("成绩", Icons.Default.Assessment, Color(0xFF34A853), scoreValue, scoreCaption, state.scoresUpdated, onScores, Modifier.weight(1f))
            HubTile("考试", Icons.Default.EventAvailable, Color(0xFFFF9500), examValue, examCaption, state.examsUpdated, onExams, Modifier.weight(1f))
        }
        FeatureRow("查找空教室", "按教学楼与节次查询空闲教室 · 即将上线", Icons.Default.MeetingRoom,
            listOf(Color(0xFF34C3A0), Color(0xFF3D9BFF)), onClick = null)
        FeatureRow("智能助手", "Yangzhou University Agent · 即将上线", Icons.Default.AutoAwesome,
            listOf(Color(0xFF6E8BFF), Color(0xFFB37CFF), Color(0xFFFF8FB1)), onClick = onAssistant)
        Text("成绩与考试结果保存在本机，可离线查看；需要最新信息时在“我的”中登录刷新。", style = MaterialTheme.typography.bodySmall, color = glass.textFaint,
            modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable private fun HubTile(title: String, icon: ImageVector, color: Color, value: String, caption: String, updated: String?, onClick: () -> Unit, modifier: Modifier) {
    val glass = LocalGlass.current
    GlassCard(modifier.heightIn(min = 168.dp), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = glass.textFaint)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers), fontWeight = FontWeight.Bold)
        }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = glass.textMuted, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Text(updated?.let { "更新于 $it" } ?: "未保存", style = MaterialTheme.typography.labelSmall, color = glass.textFaint)
    }
}

/** 功能入口行；onClick 为空表示功能尚未上线，只展示“即将上线”标记，不可点击。 */
@Composable private fun FeatureRow(title: String, subtitle: String, icon: ImageVector, gradient: List<Color>, onClick: (() -> Unit)?) {
    val glass = LocalGlass.current
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(gradient)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = glass.textFaint)
            else InfoChip("即将上线", color = glass.textMuted)
        }
    }
}
