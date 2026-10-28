package cn.yzu.timetable.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yzu.timetable.data.SemesterDates
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.AmbientBackground
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
private fun LocalDate.md() = "${monthValue}月${dayOfMonth}日"

/** 推荐值：第一学期开学周通常包含 9 月 1 日；第二学期不猜测，只沿用已有日期。 */
private data class CalibrationGuess(val firstTerm: Boolean, val september: LocalDate, val first: LocalDate?, val last: LocalDate?)

private fun guess(today: LocalDate, semester: SemesterDates?): CalibrationGuess {
    val firstTerm = today.monthValue >= 7 || today.monthValue == 1
    val september = LocalDate.of(if (today.monthValue == 1) today.year - 1 else today.year, 9, 1).monday()
    val existing = runCatching { semester?.let { it.start() to it.end() } }.getOrNull()
    val first = when {
        existing != null && semester?.confirmed == true -> existing.first
        firstTerm -> september
        else -> existing?.first
    }
    val last = when {
        existing != null && semester?.confirmed == true && existing.first == first -> existing.second.monday()
        first != null -> first.plusWeeks(19)
        else -> null
    }
    return CalibrationGuess(firstTerm, september, first, last)
}

/** 全屏日历标定页，作为 AppRoot 的覆盖层显示。 */
@Composable fun SemesterCalibrationScreen(today: LocalDate, semester: SemesterDates?, error: String?, onSave: (String, String) -> Boolean, onDismiss: () -> Unit) {
    val initial = remember(today, semester) { guess(today, semester) }
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf(initial.first) }
    var last by remember { mutableStateOf(initial.last) }
    val glass = LocalGlass.current
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    run {
        AmbientBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("第 ${step + 1} 步 / 共 2 步", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(if (step == 0) "标定开学第一周" else "标定最后一周", style = MaterialTheme.typography.headlineMedium)
                    }
                    GlassIconButton(Icons.Default.Close, "关闭", onDismiss, size = 40.dp)
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) { i -> Box(Modifier.height(4.dp).width(if (i == step) 28.dp else 12.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))) }
                }
                val hint = when {
                    step == 1 -> "已按 20 周给出建议。请对照学校校历，点选本学期最后一周（通常是考试周）。"
                    initial.firstTerm -> "第一学期开学周通常包含 9 月 1 日，已为你预选这一周；如学校校历不同，直接点选其他周。"
                    else -> "第二学期开学时间每年不同，请对照学校校历，点选开学第一周（以周一为准）。"
                }
                StatusBanner(hint, Icons.Default.Lightbulb, modifier = Modifier.padding(horizontal = 16.dp))
                AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 320f)) { it * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 320f)) { -it * dir / 3 } + fadeOut())
                }, label = "calibrationStep") { s ->
                    if (s == 0) {
                        val anchor = first ?: if (initial.firstTerm) initial.september else LocalDate.of(if (today.monthValue >= 7) today.year + 1 else today.year, 2, 1)
                        val months = (-1..3).map { YearMonth.from(anchor).plusMonths(it.toLong()) }
                        MonthList(months, initialIndex = 1) { month ->
                            CalendarMonth(month, today, selectStart = first, selectEnd = null, rangeStart = null, candidate = initial.september.takeIf { initial.firstTerm },
                                onPick = { first = it; last = it.plusWeeks(19) })
                        }
                    } else {
                        val start = first ?: today.monday()
                        val months = (0..7).map { YearMonth.from(start).plusMonths(it.toLong()) }
                        val target = last?.let { l -> months.indexOfFirst { it == YearMonth.from(l) }.coerceAtLeast(0) } ?: 0
                        MonthList(months, initialIndex = (target - 1).coerceAtLeast(0)) { month ->
                            CalendarMonth(month, today, selectStart = last, selectEnd = null, rangeStart = start, candidate = null,
                                onPick = { picked -> last = if (picked.isBefore(start)) start else picked })
                        }
                    }
                }
                CalibrationFooter(step, first, last, error, onBack = { step = 0 }, onNext = { step = 1 }, onSave = {
                    val f = first; val l = last
                    if (f != null && l != null && onSave(f.toString(), l.plusDays(6).toString())) onDismiss()
                })
            }
        }
    }
}

@Composable private fun MonthList(months: List<YearMonth>, initialIndex: Int, item: @Composable (YearMonth) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, months.lastIndex))
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(months, key = { it.toString() }) { item(it) }
    }
}

private val weekHeads = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 月历：每一行就是一个教学周，点任意一天即选中所在周（以周一为准）。
 * rangeStart 不为空时（第二步）在左侧标出教学周序号，并淡色填充第一周到所选最后一周的区间。
 */
@Composable private fun CalendarMonth(month: YearMonth, today: LocalDate, selectStart: LocalDate?, selectEnd: LocalDate?, rangeStart: LocalDate?, candidate: LocalDate?, onPick: (LocalDate) -> Unit) {
    val glass = LocalGlass.current
    val primary = MaterialTheme.colorScheme.primary
    val firstRow = month.atDay(1).monday()
    val rows = generateSequence(firstRow) { it.plusWeeks(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)) {
        Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            weekHeads.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = glass.textFaint, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        rows.forEach { monday ->
            val selected = monday == selectStart
            val inRange = rangeStart != null && selectStart != null && !monday.isBefore(rangeStart) && !monday.isAfter(selectStart)
            val isCandidate = candidate != null && monday == candidate
            val weekNo = rangeStart?.let { ChronoUnit.WEEKS.between(it, monday).toInt() + 1 }?.takeIf { it >= 1 }
            val bg = when { selected -> primary; inRange -> primary.copy(alpha = 0.10f); else -> Color.Transparent }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                .then(if (isCandidate && !selected) Modifier.border(1.5.dp, primary.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier)
                .clickable { onPick(monday) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    val label = when { weekNo != null -> "${weekNo}周"; isCandidate -> "推荐"; else -> "" }
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, style = TabularNumbers,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else primary.copy(alpha = 0.8f))
                }
                (0..6).forEach { offset ->
                    val day = monday.plusDays(offset.toLong())
                    val inMonth = YearMonth.from(day) == month
                    val color = when {
                        selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = if (inMonth) 1f else 0.55f)
                        !inMonth -> glass.textFaint.copy(alpha = 0.5f)
                        day == today -> primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("${day.dayOfMonth}", color = color, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                            fontWeight = if (day == today || (day.monthValue == 9 && day.dayOfMonth == 1)) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable private fun CalibrationFooter(step: Int, first: LocalDate?, last: LocalDate?, error: String?, onBack: () -> Unit, onNext: () -> Unit, onSave: () -> Unit) {
    val glass = LocalGlass.current
    val weeks = if (first != null && last != null) ChronoUnit.WEEKS.between(first, last).toInt() + 1 else null
    val valid = weeks != null && weeks in 1..30
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), strong = true, contentPadding = PaddingValues(18.dp)) {
        if (step == 0) {
            Text("开学第一周", style = MaterialTheme.typography.labelMedium, color = glass.textFaint)
            Text(first?.let { "${it.md()}（周一） – ${it.plusDays(6).md()}" } ?: "请在日历中点选一周", style = MaterialTheme.typography.titleMedium)
        } else {
            Text("最后一周 · 共 ${weeks ?: "—"} 周", style = MaterialTheme.typography.labelMedium, color = glass.textFaint)
            Text(last?.let { "${it.md()} – ${it.plusDays(6).md()}（学期结束日）" } ?: "请在日历中点选一周", style = MaterialTheme.typography.titleMedium)
            if (weeks != null && weeks > 30) Text("学期最长 30 周，请重新选择", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step == 1) GlassCapsuleButton("上一步", onBack, Modifier.weight(1f))
            if (step == 0) PrimaryCapsuleButton("下一步：选择最后一周", onNext, Modifier.weight(1f), enabled = first != null)
            else PrimaryCapsuleButton("保存", onSave, Modifier.weight(1f), enabled = valid)
        }
    }
}
