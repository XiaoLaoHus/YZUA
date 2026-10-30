package cn.yzu.timetable.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yzu.timetable.data.model.PeriodTime
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import cn.yzu.timetable.ui.theme.pressScale
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

/** 课表纵向尺寸：节次高度随字体放大而增高，午休与晚饭留出分隔带。 */
private class GridMetrics(val period: Dp, val gap: Dp) {
    fun top(p: Int): Dp = period * (p - 1) + (if (p > 5) gap else 0.dp) + (if (p > 10) gap else 0.dp)
    fun bottom(p: Int): Dp = top(p) + period
    val total: Dp get() = bottom(YzuTimetable.PERIOD_COUNT)
}

/** 当天课程互相重叠时并排显示，不丢弃任何一门。 */
private data class Placed(val lesson: ScheduleLesson, val lane: Int, val lanes: Int)

private fun layoutDay(lessons: List<ScheduleLesson>): List<Placed> {
    val sorted = lessons.sortedWith(compareBy<ScheduleLesson> { it.startPeriod }.thenByDescending { it.endPeriod })
    val result = mutableListOf<Placed>()
    var cluster = mutableListOf<Pair<ScheduleLesson, Int>>()
    var clusterEnd = -1
    val laneEnds = mutableListOf<Int>()
    fun flush() {
        val lanes = (cluster.maxOfOrNull { it.second } ?: -1) + 1
        cluster.forEach { (l, lane) -> result += Placed(l, lane, lanes) }
        cluster = mutableListOf(); laneEnds.clear()
    }
    sorted.forEach { lesson ->
        if (lesson.startPeriod > clusterEnd && cluster.isNotEmpty()) flush()
        val lane = laneEnds.indexOfFirst { it < lesson.startPeriod }.let { if (it == -1) { laneEnds += lesson.endPeriod; laneEnds.lastIndex } else { laneEnds[it] = lesson.endPeriod; it } }
        cluster += lesson to lane
        clusterEnd = maxOf(clusterEnd, lesson.endPeriod)
    }
    if (cluster.isNotEmpty()) flush()
    return result
}

@Composable fun TimetableDayHeader(weekStart: LocalDate?, today: LocalDate, days: List<Int>, timeColumn: Dp = 40.dp) {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(timeColumn), contentAlignment = Alignment.Center) {
            Text(weekStart?.let { "${it.monthValue}月" } ?: "节次", style = MaterialTheme.typography.labelMedium, color = glass.textMuted, fontWeight = FontWeight.SemiBold)
        }
        days.forEach { day ->
            val date = weekStart?.plusDays((day - 1).toLong())
            val isToday = date == today
            Column(Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("周${dayNames[day - 1]}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                Text(date?.let { if (it.dayOfMonth == 1) "${it.monthValue}月" else "${it.dayOfMonth}" } ?: "", fontSize = 10.sp, style = TabularNumbers,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else glass.textFaint)
            }
        }
    }
}

/**
 * 一周课表主体：整周宽度适配屏幕不左右滚动，纵向可滑动。
 * 节次时间取扬大春夏/秋冬时间表；周次与单双周只使用 occursInWeek。
 */
@Composable fun TimetableGrid(
    week: Int,
    lessons: List<ScheduleLesson>,
    periods: List<PeriodTime>,
    weekStart: LocalDate?,
    today: LocalDate,
    now: LocalTime,
    days: List<Int>,
    season: ScheduleSeason,
    onLessonClick: (ScheduleLesson) -> Unit,
    modifier: Modifier = Modifier,
    timeColumn: Dp = 40.dp,
) {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    val metrics = remember(fontScale) { GridMetrics(66.dp * fontScale, 22.dp) }
    val slots = YzuTimetable.slots(season)
    val todayColumn = weekStart?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 }?.takeIf { it in 1..7 && it in days }
    val lineColor = scheme.outlineVariant.copy(alpha = if (glass.dark) 0.5f else 0.8f)

    BoxWithConstraints(modifier.fillMaxWidth().height(metrics.total)) {
        val dayWidth = (maxWidth - timeColumn) / days.size
        // 底层：节次分隔线、午休/晚饭分隔带、今日列高亮
        Canvas(Modifier.matchParentSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            for (p in 1..YzuTimetable.PERIOD_COUNT) {
                val y = metrics.bottom(p).toPx()
                val blockEnd = YzuTimetable.blocks.any { it.periods.last == p }
                drawLine(lineColor, Offset(timeColumn.toPx(), y), Offset(size.width, y), strokeWidth = if (blockEnd) 1.2f else 0.8f,
                    pathEffect = if (blockEnd) null else dash)
            }
            listOf(5, 10).forEach { p ->
                drawRect(scheme.primary.copy(alpha = 0.04f), Offset(0f, metrics.bottom(p).toPx()), androidx.compose.ui.geometry.Size(size.width, metrics.gap.toPx()))
            }
            todayColumn?.let { day ->
                val x = timeColumn.toPx() + dayWidth.toPx() * days.indexOf(day)
                drawRoundRect(scheme.primary.copy(alpha = if (glass.dark) 0.08f else 0.05f), Offset(x, 0f),
                    androidx.compose.ui.geometry.Size(dayWidth.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
            }
        }
        // 时间列
        slots.forEach { slot ->
            Column(Modifier.offset(y = metrics.top(slot.number)).width(timeColumn).height(metrics.period),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("${slot.number}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, style = TabularNumbers)
                Text(slot.startText, fontSize = 9.5.sp, color = glass.textMuted, style = TabularNumbers, lineHeight = 11.sp)
                Text(slot.endText, fontSize = 9.5.sp, color = glass.textFaint, style = TabularNumbers, lineHeight = 11.sp)
            }
        }
        listOf(5 to "午休", 10 to "晚饭").forEach { (p, label) ->
            Box(Modifier.offset(y = metrics.bottom(p)).fillMaxWidth().height(metrics.gap), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 10.sp, color = glass.textFaint, letterSpacing = 6.sp)
            }
        }
        // 课程卡片
        days.forEachIndexed { column, day ->
            val placed = remember(lessons, week, day) { layoutDay(lessons.filter { it.dayOfWeek == day && it.occursInWeek(week) }) }
            placed.forEach { item ->
                val laneWidth = dayWidth / item.lanes
                val start = item.lesson.startPeriod.coerceIn(1, YzuTimetable.PERIOD_COUNT)
                val end = item.lesson.endPeriod.coerceIn(start, YzuTimetable.PERIOD_COUNT)
                LessonBlock(
                    item.lesson, metrics.bottom(end) - metrics.top(start), column,
                    Modifier.offset(x = timeColumn + dayWidth * column + laneWidth * item.lane, y = metrics.top(start)).width(laneWidth),
                    onLessonClick,
                )
            }
        }
        // 当前时间线
        if (todayColumn != null) nowOffset(now, slots, metrics)?.let { y ->
            val x = timeColumn + dayWidth * days.indexOf(todayColumn)
            Box(Modifier.offset(x = x - 3.dp, y = y - 3.dp).size(6.dp).clip(CircleShape).background(scheme.error))
            Box(Modifier.offset(x = x, y = y - 0.75.dp).width(dayWidth).height(1.5.dp).background(scheme.error.copy(alpha = 0.85f)))
        }
    }
}

private fun nowOffset(now: LocalTime, slots: List<PeriodSlot>, m: GridMetrics): Dp? {
    if (now.isBefore(slots.first().start) || !now.isBefore(slots.last().end)) return null
    slots.forEachIndexed { i, slot ->
        if (!now.isBefore(slot.start) && now.isBefore(slot.end)) {
            val f = ChronoUnit.SECONDS.between(slot.start, now).toFloat() / ChronoUnit.SECONDS.between(slot.start, slot.end)
            return m.top(slot.number) + m.period * f
        }
        val next = slots.getOrNull(i + 1) ?: return null
        if (!now.isBefore(slot.end) && now.isBefore(next.start)) {
            val f = ChronoUnit.SECONDS.between(slot.end, now).toFloat() / ChronoUnit.SECONDS.between(slot.end, next.start)
            return m.bottom(slot.number) + (m.top(next.number) - m.bottom(slot.number)) * f
        }
    }
    return null
}

@Composable private fun LessonBlock(lesson: ScheduleLesson, height: Dp, column: Int, modifier: Modifier, onClick: (ScheduleLesson) -> Unit) {
    val glass = LocalGlass.current
    val colors = courseColors(lesson.colorIndex, glass.dark)
    val interaction = remember { MutableInteractionSource() }
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val appear = remember(lesson.id) { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(lesson.id) { delay(column * 25L); appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(height).padding(1.5.dp)
            .graphicsLayer { alpha = appear.value; val s = 0.92f + 0.08f * appear.value; scaleX = s; scaleY = s }
            .pressScale(interaction, 0.94f)
            .clip(shape)
            .background(colors.container)
            .clickable(interaction, null, role = Role.Button) { onClick(lesson) },
    ) {
        Box(Modifier.padding(start = 3.dp, top = 6.dp).width(3.dp).height((height - 15.dp).coerceAtLeast(8.dp)).clip(CircleShape).background(colors.accent))
        Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 3.dp, top = 5.dp, bottom = 4.dp)) {
            Text(lesson.courseName, fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.content,
                maxLines = if (height > 120.dp) 4 else 3, overflow = TextOverflow.Ellipsis)
            if (lesson.classroom.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text("@" + lesson.classroom, fontSize = 9.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium,
                    color = colors.content.copy(alpha = 0.86f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (lesson.teacher.isNotBlank() && height > 110.dp) {
                Spacer(Modifier.weight(1f))
                Text(lesson.teacher, fontSize = 9.sp, lineHeight = 11.sp, color = colors.content.copy(alpha = 0.55f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
            }
        }
    }
}
