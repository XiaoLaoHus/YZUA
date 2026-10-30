package cn.yzu.timetable.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val MD = DateTimeFormatter.ofPattern("M/d")

@Composable fun TimetableScreen(state: AppUiState, vm: AppViewModel, onLessonClick: (ScheduleLesson) -> Unit, onProfile: () -> Unit = {}, onCalibrate: () -> Unit = onProfile) {
    val prefs = LocalDisplayPreferences.current
    val now by rememberNow()
    var settingsOpen by remember { mutableStateOf(false) }
    val total = state.term.totalWeeks.coerceAtLeast(1)
    // 翻页器是显示的唯一来源：按钮与滑动都只驱动翻页器，停稳后再用 shiftWeek 同步到 ViewModel，避免双向同步互相打断。
    val pager = rememberPagerState(initialPage = state.week.coerceIn(1, total) - 1) { total }
    val scope = rememberCoroutineScope()
    val week = (pager.targetPage + 1).coerceIn(1, total)
    val pageTween = tween<Float>(320, easing = FastOutSlowInEasing)
    fun goTo(target: Int, after: () -> Unit = {}) {
        scope.launch { pager.animateScrollToPage((target - 1).coerceIn(0, total - 1), animationSpec = pageTween); after() }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val current = vm.state.value.week
            if (page + 1 != current) vm.shiftWeek(page + 1 - current)
        }
    }
    LaunchedEffect(state.week) {
        if (!pager.isScrollInProgress && pager.settledPage != state.week - 1) pager.scrollToPage((state.week - 1).coerceIn(0, total - 1))
    }
    val thisWeek = state.semester?.weekOn(state.today)
    val weekStart: (Int) -> LocalDate? = { w -> runCatching { state.semester?.start()?.plusWeeks((w - 1).toLong()) }.getOrNull() }
    val start = weekStart(week)
    val refDate = start?.let { s -> if (!state.today.isBefore(s) && state.today.isBefore(s.plusDays(7))) state.today else s } ?: state.today
    val season = YzuTimetable.season(refDate, prefs.seasonMode)
    val lessons = state.lessons + prefs.customLessons
    val weekendLessons = lessons.count { it.dayOfWeek >= 6 && it.occursInWeek(week) }
    val days = if (prefs.hideWeekend) (1..5).toList() else (1..7).toList()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(week, transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it * dir / 2 } + fadeIn()) togetherWith
                        (slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { -it * dir / 2 } + fadeOut()) using SizeTransform(clip = false)
                }, label = "weekTitle") { w ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("第 $w 周", style = MaterialTheme.typography.headlineLarge.merge(TabularNumbers))
                        if (w == thisWeek) { Spacer(Modifier.width(8.dp)); InfoChip("本周", Modifier.padding(bottom = 6.dp)) }
                    }
                }
                Text(listOfNotNull(start?.let { "${it.format(MD)} – ${it.plusDays(6).format(MD)}" }, "${season.label}作息").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = LocalGlass.current.textMuted)
            }
            GlassIconButton(Icons.Default.ChevronLeft, "上一周", { goTo(week - 1) }, enabled = week > 1, size = 38.dp)
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Default.ChevronRight, "下一周", { goTo(week + 1) }, enabled = week < total, size = 38.dp)
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Default.Tune, "课表设置", { settingsOpen = true }, size = 38.dp)
        }
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(2.dp))
            LoadingStrip(state.loading)
            AnimatedVisibility(thisWeek != null && week != thisWeek) {
                GlassCapsuleButton("回到本周 · 第 $thisWeek 周", { thisWeek?.let { goTo(it) { vm.resetWeek() } } }, icon = Icons.Default.Today, modifier = Modifier.height(40.dp))
            }
            state.semesterReminder?.let { StatusBanner(it, Icons.Default.EventNote, BannerTone.Warning, action = "校准", onAction = onCalibrate) }
            state.error?.let { StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error) }
            if (prefs.hideWeekend && weekendLessons > 0) StatusBanner("本周末有 $weekendLessons 节课已隐藏", Icons.Default.VisibilityOff,
                action = "显示", onAction = { prefs.updateHideWeekend(false) })
        }
        if (state.scheduleUpdated == null && state.lessons.isEmpty()) {
            Box(Modifier.padding(16.dp)) {
                EmptyState(Icons.Default.CalendarMonth, "还没有导入课表", "连接校园网或手机 VPN 后，在“我的”中登录统一认证即可导入本学期课表。", action = "前往我的", onAction = onProfile)
            }
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, pageSpacing = 12.dp, contentPadding = PaddingValues(horizontal = 8.dp), key = { it }) { page ->
            val w = page + 1
            val pageStart = weekStart(w)
            val pageRef = pageStart?.let { s -> if (!state.today.isBefore(s) && state.today.isBefore(s.plusDays(7))) state.today else s } ?: state.today
            GlassCard(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                TimetableDayHeader(pageStart, state.today, days)
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    TimetableGrid(w, lessons, state.periods, pageStart, state.today, now, days, YzuTimetable.season(pageRef, prefs.seasonMode), onLessonClick)
                    state.scheduleUpdated?.let {
                        Text("本地课表 · 更新于 $it", style = MaterialTheme.typography.labelSmall, color = LocalGlass.current.textFaint,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(LocalBottomBarSpace.current + 12.dp))
                }
            }
        }
    }
    if (settingsOpen) TimetableSettingsSheet(state, season, onRefresh = vm::refresh, onDismiss = { settingsOpen = false })
}
