package cn.yzu.timetable.ui.components

import android.content.Context
import cn.yzu.timetable.reminder.ClassReminders
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import cn.yzu.timetable.data.model.PeriodTime
import cn.yzu.timetable.data.model.ScheduleLesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 扬州大学上课时间表（2025 学生手册）：春夏季自 5 月 1 日起执行，秋冬季自 10 月 1 日起执行。 */
enum class ScheduleSeason(val label: String) { SpringSummer("春夏季"), AutumnWinter("秋冬季") }

enum class SeasonMode(val label: String) { Auto("自动"), SpringSummer("春夏季"), AutumnWinter("秋冬季") }

data class PeriodSlot(val number: Int, val start: LocalTime, val end: LocalTime) {
    val startText: String get() = start.format(HHMM)
    val endText: String get() = end.format(HHMM)
}

/** 大节分组，用于课表与时间表的视觉分段。 */
data class PeriodBlock(val name: String, val part: String, val periods: IntRange)

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private fun t(text: String) = LocalTime.parse(text)

object YzuTimetable {
    const val PERIOD_COUNT = 12

    private val morning = listOf("08:00" to "08:45", "08:55" to "09:40", "09:55" to "10:40", "10:50" to "11:35", "11:45" to "12:30")
    private val springSummer = morning + listOf(
        "14:30" to "15:15", "15:25" to "16:10", "16:20" to "17:05", "17:15" to "18:00", "18:10" to "18:55",
        "19:30" to "20:15", "20:25" to "21:10",
    )
    private val autumnWinter = morning + listOf(
        "14:00" to "14:45", "14:55" to "15:40", "15:50" to "16:35", "16:45" to "17:30", "17:40" to "18:25",
        "19:00" to "19:45", "19:55" to "20:40",
    )
    private val tables = mapOf(
        ScheduleSeason.SpringSummer to springSummer.mapIndexed { i, (s, e) -> PeriodSlot(i + 1, t(s), t(e)) },
        ScheduleSeason.AutumnWinter to autumnWinter.mapIndexed { i, (s, e) -> PeriodSlot(i + 1, t(s), t(e)) },
    )

    val blocks = listOf(
        PeriodBlock("第一大节", "上午", 1..2), PeriodBlock("第二大节", "上午", 3..5),
        PeriodBlock("第三大节", "下午", 6..8), PeriodBlock("第四大节", "下午", 9..10),
        PeriodBlock("第五大节", "晚间", 11..12),
    )

    /** 5 月 1 日至 9 月 30 日为春夏季，其余日期为秋冬季。 */
    fun seasonOn(date: LocalDate): ScheduleSeason =
        if (date.monthValue in 5..9) ScheduleSeason.SpringSummer else ScheduleSeason.AutumnWinter

    fun season(date: LocalDate, mode: SeasonMode): ScheduleSeason = when (mode) {
        SeasonMode.Auto -> seasonOn(date)
        SeasonMode.SpringSummer -> ScheduleSeason.SpringSummer
        SeasonMode.AutumnWinter -> ScheduleSeason.AutumnWinter
    }

    fun slots(season: ScheduleSeason): List<PeriodSlot> = tables.getValue(season)

    /** 下一次自动切换的日期，用于在设置中提示。 */
    fun nextSwitch(date: LocalDate): Pair<LocalDate, ScheduleSeason> {
        val may = LocalDate.of(date.year, 5, 1); val oct = LocalDate.of(date.year, 10, 1)
        return when {
            date.isBefore(may) -> may to ScheduleSeason.SpringSummer
            date.isBefore(oct) -> oct to ScheduleSeason.AutumnWinter
            else -> may.plusYears(1) to ScheduleSeason.SpringSummer
        }
    }

    /**
     * 节次时间：1–12 节按扬大时间表；超出范围时才回退到 URP 返回的 jcsjbs 时间。
     * 返回 null 表示没有可靠时间，界面只显示节次，不虚构时间。
     */
    fun slot(number: Int, season: ScheduleSeason, fallback: List<PeriodTime> = emptyList()): PeriodSlot? =
        slots(season).getOrNull(number - 1) ?: fallback.firstOrNull { it.number == number }?.let { p ->
            runCatching { PeriodSlot(number, LocalTime.parse(p.startTime.padTime()), LocalTime.parse(p.endTime.padTime())) }.getOrNull()
        }

    fun lessonRange(lesson: ScheduleLesson, season: ScheduleSeason, fallback: List<PeriodTime> = emptyList()): Pair<PeriodSlot, PeriodSlot>? {
        val start = slot(lesson.startPeriod, season, fallback) ?: return null
        val end = slot(lesson.endPeriod, season, fallback) ?: start
        return start to end
    }

    private fun String.padTime(): String = trim().let { if (it.length == 4 && it[1] == ':') "0$it" else it }
}

/** 仅界面显示偏好（隐藏周末、作息季节），独立于教务缓存，不含任何账号或查询数据。 */
@Stable
class DisplayPreferences internal constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(ClassReminders.PREFS, Context.MODE_PRIVATE)
    var hideWeekend by mutableStateOf(prefs.getBoolean(KEY_WEEKEND, false))
        private set
    var seasonMode by mutableStateOf(runCatching { SeasonMode.valueOf(prefs.getString(KEY_SEASON, null) ?: "Auto") }.getOrDefault(SeasonMode.Auto))
        private set

    var reminderEnabled by mutableStateOf(prefs.getBoolean(ClassReminders.KEY_REMINDER, true))
        private set
    var reminderLead by mutableIntStateOf(prefs.getInt(ClassReminders.KEY_LEAD, ClassReminders.DEFAULT_LEAD))
        private set
    var autoMute by mutableStateOf(prefs.getBoolean(ClassReminders.KEY_MUTE, false))
        private set
    var notificationAsked by mutableStateOf(prefs.getBoolean(KEY_NOTIFY_ASKED, false))
        private set
    fun updateReminder(value: Boolean) { reminderEnabled = value; prefs.edit().putBoolean(ClassReminders.KEY_REMINDER, value).apply() }
    fun updateLead(minutes: Int) { reminderLead = minutes; prefs.edit().putInt(ClassReminders.KEY_LEAD, minutes).apply() }
    fun updateAutoMute(value: Boolean) { autoMute = value; prefs.edit().putBoolean(ClassReminders.KEY_MUTE, value).apply() }
    fun markNotificationAsked() { notificationAsked = true; prefs.edit().putBoolean(KEY_NOTIFY_ASKED, true).apply() }

    /** 首次使用须知是否已同意；仅记录勾选状态。 */
    var disclaimerAccepted by mutableStateOf(prefs.getBoolean(KEY_DISCLAIMER, false))
        private set
    fun acceptDisclaimer() { disclaimerAccepted = true; prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply() }

    fun updateHideWeekend(value: Boolean) { hideWeekend = value; prefs.edit().putBoolean(KEY_WEEKEND, value).apply() }
    fun updateSeasonMode(value: SeasonMode) { seasonMode = value; prefs.edit().putString(KEY_SEASON, value.name).apply() }

    private companion object { const val KEY_WEEKEND = "hide_weekend"; const val KEY_SEASON = ClassReminders.KEY_SEASON; const val KEY_NOTIFY_ASKED = "notification_permission_asked"; const val KEY_DISCLAIMER = "disclaimer_v1_accepted" }
}

val LocalDisplayPreferences = staticCompositionLocalOf<DisplayPreferences> { error("DisplayPreferences not provided") }

@Composable fun rememberDisplayPreferences(): DisplayPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { DisplayPreferences(context) }
}

/** 每 20 秒刷新的本机时钟，用于今日课程状态和课表“当前时间”线。 */
@Composable fun rememberNow(): State<LocalTime> = produceState(LocalTime.now()) {
    while (true) {
        value = LocalTime.now()
        kotlinx.coroutines.delay(20_000)
    }
}
