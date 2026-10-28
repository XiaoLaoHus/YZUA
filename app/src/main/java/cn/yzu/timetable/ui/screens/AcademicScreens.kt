package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.data.model.ExamSchedule
import cn.yzu.timetable.data.model.ScoreRecord
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 二级页面顶部：返回按钮 + 标题 + 刷新。 */
@Composable fun SubPageHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, onRefresh: (() -> Unit)? = null, refreshEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回更多", onBack, size = 40.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textMuted)
        }
        if (onRefresh != null) GlassIconButton(Icons.Default.Refresh, "刷新查询", onRefresh, enabled = refreshEnabled, size = 40.dp)
    }
}

private fun LazyListScope.refreshNotice(state: AppUiState, updated: String?, onLogin: () -> Unit) {
    item("notice") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(updated?.let { "本地信息 · 更新于 $it" } ?: "尚未保存此范围的信息", style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textFaint)
            if (state.session == null) StatusBanner("需要最新信息时，请登录一次进行刷新。", Icons.Default.Login, action = "去登录", onAction = { if (!state.loading) onLogin() })
            LoadingStrip(state.loading)
        }
    }
}

private fun numeric(score: String) = score.trim().toDoubleOrNull()
private val failWords = listOf("不及格", "不合格", "未通过")

@Composable private fun scoreColor(record: ScoreRecord): Color {
    val glass = LocalGlass.current
    val n = numeric(record.score)
    return when {
        record.score.isBlank() -> glass.textFaint
        record.unpassedReason.isNotBlank() || (n != null && n < 60) || failWords.any { record.score.contains(it) } -> MaterialTheme.colorScheme.error
        (n != null && n >= 90) || record.score.contains("优") -> glass.success
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable fun ScoresScreen(state: AppUiState, onRefresh: () -> Unit, onScopeChange: (Boolean) -> Unit, onLogin: () -> Unit, onBack: (() -> Unit)? = null) {
    val glass = LocalGlass.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = LocalBottomBarSpace.current + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("header") { SubPageHeader("成绩", "扬大 URP 成绩查询", onBack, onRefresh, !state.loading) }
        item("scope") {
            GlassSegmented(listOf("本学期", "全部学期"), if (state.allScoreTerms) 1 else 0, { onScopeChange(it == 1) }, Modifier.fillMaxWidth(), enabled = !state.loading)
        }
        refreshNotice(state, state.scoresUpdated, onLogin)
        state.scoreError?.let { item("error") { StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error) } }
        if (state.scores.isNotEmpty()) item("summary") { ScoreSummary(state.scores) }
        if (state.scoresLoaded && state.scores.isEmpty() && state.scoreError == null && !state.loading) item("empty") {
            EmptyState(Icons.Default.Assessment, "暂无成绩记录", "查询范围内暂无成绩记录，成绩公布后登录刷新即可看到。")
        }
        if (!state.scoresLoaded && state.scores.isEmpty() && state.scoreError == null && !state.loading) item("never") {
            EmptyState(Icons.Default.CloudDownload, "尚未查询这个范围", "在“我的”中登录后点刷新，查询结果会保存在本机。", action = "前往登录", onAction = onLogin)
        }
        val groups = if (state.allScoreTerms) state.scores.groupBy { it.termLabel } else mapOf("" to state.scores)
        groups.forEach { (term, records) ->
            if (term.isNotBlank()) item("term-$term") { SectionLabel(term, trailing = "${records.size} 门") }
            items(records, key = null) { ScoreCard(it) }
        }
    }
}

@Composable private fun ScoreSummary(scores: List<ScoreRecord>) {
    val glass = LocalGlass.current
    val credits = scores.mapNotNull { it.credit.trim().toDoubleOrNull() }.sum()
    val failed = scores.count { r -> r.unpassedReason.isNotBlank() || numeric(r.score)?.let { it < 60 } == true || failWords.any { r.score.contains(it) } }
    val pending = scores.count { it.score.isBlank() }
    GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Stat("课程", "${scores.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Stat("学分合计", if (credits % 1.0 == 0.0) "${credits.toInt()}" else "%.1f".format(credits), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            Stat("未通过", "$failed", if (failed > 0) MaterialTheme.colorScheme.error else glass.success, Modifier.weight(1f))
            if (pending > 0) Stat("未公布", "$pending", glass.textMuted, Modifier.weight(1f))
        }
    }
}

@Composable private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers), color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalGlass.current.textFaint)
    }
}

@Composable private fun ScoreCard(score: ScoreRecord) {
    val glass = LocalGlass.current
    val color = scoreColor(score)
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(score.courseName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (score.property.isNotBlank()) InfoChip(score.property, color = glass.textMuted)
                    InfoChip("学分 ${score.credit.ifBlank { "—" }}", color = glass.textMuted)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(score.score.ifBlank { "未公布" }, style = (if (score.score.length <= 4 && score.score.isNotBlank()) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall).merge(TabularNumbers),
                    color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        val stats = listOf("最高" to score.maxScore, "最低" to score.minScore, "平均" to score.averageScore).filter { it.second.isNotBlank() }
        if (stats.isNotEmpty() || score.courseCode.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Row {
                Text(score.courseCode, style = MaterialTheme.typography.labelSmall, color = glass.textFaint, modifier = Modifier.weight(1f))
                Text(stats.joinToString("   ") { "${it.first} ${it.second}" }, style = MaterialTheme.typography.labelSmall.merge(TabularNumbers), color = glass.textMuted)
            }
        }
        if (score.unpassedReason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            InfoChip(score.unpassedReason, color = MaterialTheme.colorScheme.error, icon = Icons.Default.Info)
        }
    }
}

/** 解析学校返回的考试时间；无法识别时返回 null，界面原样显示文本。 */
internal fun parseExamTime(text: String): LocalDateTime? {
    val m = Regex("""(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})日?(?:[T\s]+(\d{1,2}):(\d{2}))?""").find(text) ?: return null
    return runCatching {
        val (y, mo, d) = m.destructured
        val date = LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
        val h = m.groupValues[4].toIntOrNull(); val mi = m.groupValues[5].toIntOrNull()
        if (h != null && mi != null) date.atTime(h, mi) else date.atStartOfDay()
    }.getOrNull()
}
private fun hasClock(text: String) = Regex("""\d{1,2}:\d{2}""").containsMatchIn(text)
private fun clock(text: String) = Regex("""\d{1,2}:\d{2}""").find(text)?.value
private val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable fun ExamsScreen(state: AppUiState, onRefresh: () -> Unit, onLogin: () -> Unit, onBack: (() -> Unit)? = null) {
    val today = state.today
    val nowTime = rememberNow().value
    val now = today.atTime(nowTime)
    val sorted = remember(state.exams) { state.exams.sortedBy(ExamSchedule::startTime).map { it to parseExamTime(it.startTime) } }
    fun ended(exam: ExamSchedule, start: LocalDateTime?): Boolean {
        val end = parseExamTime(exam.endTime)?.takeIf { hasClock(exam.endTime) } ?: start?.let { if (hasClock(exam.startTime)) it.plusHours(2) else it.toLocalDate().plusDays(1).atStartOfDay() }
        return end != null && now.isAfter(end)
    }
    val next = sorted.firstOrNull { (e, s) -> s != null && !ended(e, s) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = LocalBottomBarSpace.current + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("header") { SubPageHeader("考试", "查询当前日期前后约三个月", onBack, onRefresh, !state.loading) }
        refreshNotice(state, state.examsUpdated, onLogin)
        state.examError?.let { item("error") { StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error) } }
        if (state.examsLoaded && state.exams.isEmpty() && state.examError == null && !state.loading) item("empty") {
            EmptyState(Icons.Default.EventAvailable, "近期没有考试", "查询时间内没有和你相关的考试信息。")
        }
        if (!state.examsLoaded && state.exams.isEmpty() && state.examError == null && !state.loading) item("never") {
            EmptyState(Icons.Default.CloudDownload, "尚未查询考试安排", "在“我的”中登录后刷新，考试安排会保存在本机。", action = "前往登录", onAction = onLogin)
        }
        next?.let { (exam, start) -> item("next") { NextExamCard(exam, start!!, today) } }
        if (sorted.isNotEmpty()) item("all") { SectionLabel("全部考试", trailing = "${sorted.size} 场") }
        items(sorted) { (exam, start) -> ExamRow(exam, start, ended(exam, start)) }
    }
}

@Composable private fun NextExamCard(exam: ExamSchedule, start: LocalDateTime, today: LocalDate) {
    val glass = LocalGlass.current
    val days = ChronoUnit.DAYS.between(today, start.toLocalDate())
    val accent = if (days <= 3) glass.warning else MaterialTheme.colorScheme.primary
    GlassCard(Modifier.fillMaxWidth(), strong = true, tint = accent.copy(alpha = if (glass.dark) 0.16f else 0.09f), contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下一场考试", style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.weight(1f))
            InfoChip(when (days) { 0L -> "今天"; 1L -> "明天"; else -> "还有 $days 天" }, color = accent, icon = Icons.Default.HourglassTop)
        }
        Spacer(Modifier.height(10.dp))
        Text(exam.courseName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        ExamLine(Icons.Default.Schedule, examTimeText(exam, start))
        if (exam.location.isNotBlank()) ExamLine(Icons.Default.Place, exam.location)
    }
}

@Composable private fun ExamLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalGlass.current.textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers), fontWeight = FontWeight.Medium)
    }
}

private fun examTimeText(exam: ExamSchedule, start: LocalDateTime?): String {
    if (start == null) return exam.startTime.replace('T', ' ')
    val date = "${start.monthValue}月${start.dayOfMonth}日 ${weekNames[start.dayOfWeek.value - 1]}"
    val from = clock(exam.startTime); val to = clock(exam.endTime)
    return listOfNotNull(date, from?.let { f -> to?.let { "$f – $it" } ?: f }).joinToString("  ")
}

@Composable private fun ExamRow(exam: ExamSchedule, start: LocalDateTime?, ended: Boolean) {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth().alpha(if (ended) 0.55f else 1f), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(54.dp).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (start != null) {
                Text("${start.monthValue}月", style = MaterialTheme.typography.labelSmall, color = glass.textFaint)
                Text("${start.dayOfMonth}", style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers), fontWeight = FontWeight.Bold)
                Text(weekNames[start.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall, color = glass.textMuted)
            } else Icon(Icons.Default.Event, null, tint = glass.textFaint)
        }
        Spacer(Modifier.width(8.dp))
        GlassCard(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exam.courseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (ended) InfoChip("已结束", color = glass.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            val from = clock(exam.startTime); val to = clock(exam.endTime)
            Text(if (from != null) listOfNotNull(from, to).joinToString(" – ") else exam.startTime.replace('T', ' '),
                style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (exam.location.isNotBlank()) Text(exam.location, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (exam.note.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(exam.note, style = MaterialTheme.typography.bodySmall, color = glass.textMuted) }
        }
    }
}
