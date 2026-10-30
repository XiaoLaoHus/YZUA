package cn.yzu.timetable.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.yzu.timetable.data.AcademicCache
import cn.yzu.timetable.data.AcademicCalendar
import cn.yzu.timetable.data.AcademicSnapshot
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.components.SeasonMode
import cn.yzu.timetable.ui.components.YzuTimetable
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 上课提醒与上课静音的本地定时器。
 * 只读取本机 academic-v1.json 缓存（不写入、不联网），按扬大作息计算节次时间；
 * AlarmManager 只排下一个事件，触发后处理并排下一个，形成链式调度。
 */
object ClassReminders {
    const val PREFS = "yzua_display_prefs"
    const val KEY_REMINDER = "reminder_enabled"
    const val KEY_LEAD = "reminder_lead_minutes"
    const val KEY_MUTE = "auto_mute_enabled"
    const val KEY_SEASON = "season_mode"
    internal const val KEY_LAST_FIRED = "reminder_last_fired"
    internal const val ACTION_FIRE = "cn.yzu.timetable.reminder.FIRE"
    internal const val ACTION_RESCHEDULE = "cn.yzu.timetable.reminder.RESCHEDULE"
    const val DEFAULT_LEAD = 15

    enum class Kind { Remind, Mute, Unmute }
    data class Event(val at: LocalDateTime, val kind: Kind, val lesson: ScheduleLesson, val start: LocalDateTime, val end: LocalDateTime)

    data class Settings(val remind: Boolean, val lead: Int, val mute: Boolean, val season: SeasonMode)

    fun settings(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(p.getBoolean(KEY_REMINDER, true), p.getInt(KEY_LEAD, DEFAULT_LEAD), p.getBoolean(KEY_MUTE, false),
            runCatching { SeasonMode.valueOf(p.getString(KEY_SEASON, null) ?: "Auto") }.getOrDefault(SeasonMode.Auto))
    }

    /** 计算 from 之后若干天内的提醒/静音/解除事件，按时间排序。 */
    fun events(snapshot: AcademicSnapshot, from: LocalDateTime, days: Int, s: Settings): List<Event> {
        val semester = snapshot.semester ?: return emptyList()
        return (0..days).flatMap { offset ->
            val date = from.toLocalDate().plusDays(offset.toLong())
            val season = YzuTimetable.season(date, s.season)
            val lessons = runCatching { AcademicCalendar.todayLessons(date, semester, snapshot.lessons) }.getOrDefault(emptyList())
            lessons.flatMap { lesson ->
                val range = YzuTimetable.lessonRange(lesson, season, snapshot.periods) ?: return@flatMap emptyList()
                val start = date.atTime(range.first.start); val end = date.atTime(range.second.end)
                buildList {
                    if (s.remind) add(Event(start.minusMinutes(s.lead.toLong()), Kind.Remind, lesson, start, end))
                    if (s.mute) { add(Event(start, Kind.Mute, lesson, start, end)); add(Event(end, Kind.Unmute, lesson, start, end)) }
                }
            }
        }.sortedWith(compareBy<Event> { it.at }.thenBy { it.kind.ordinal })
    }

    internal fun readSnapshot(context: Context): AcademicSnapshot? =
        runCatching { AcademicCache(File(context.noBackupFilesDir, "academic-v1.json")).read().snapshot }.getOrNull()

    private fun pending(context: Context, action: String = ACTION_FIRE, code: Int = 0) = PendingIntent.getBroadcast(context, code,
        Intent(context, ClassReminderReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 供设置页展示：接下来最多三条待触发事件。 */
    fun nextEvents(context: Context, limit: Int = 3): List<Event> {
        val app = context.applicationContext
        val snapshot = readSnapshot(app) ?: return emptyList()
        val now = LocalDateTime.now()
        return events(snapshot, now, 8, settings(app)).filter { it.at.isAfter(now) }.take(limit)
    }

    /** 重新排下一次事件；关闭全部功能时取消闹钟，并恢复由本应用开启的静音。 */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(app)
        alarm.cancel(intent)
        val s = settings(app)
        if (!s.mute) ClassMute.restore(app)
        if (!s.remind && !s.mute) return
        val snapshot = readSnapshot(app) ?: return
        val now = LocalDateTime.now()
        val next = events(snapshot, now, 8, s).firstOrNull { it.at.isAfter(now) } ?: return
        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
        android.util.Log.i(TAG, "scheduled ${next.kind} at ${next.at} for ${next.lesson.courseName}")
        // 自愈闹钟：每天清晨不精确触发一次，即使精确链断裂也能恢复。
        val morning = now.toLocalDate().plusDays(1).atTime(5, 30)
        alarm.setWindow(AlarmManager.RTC_WAKEUP, morning.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 30 * 60_000L, pending(app, ACTION_RESCHEDULE, 1))
        syncCurrentClassState(app, s, snapshot, now)
    }

    /** 打开 App 或重排时，若正处于上课区间则补齐静音/恢复状态。 */
    private fun syncCurrentClassState(app: Context, s: Settings, snapshot: AcademicSnapshot, now: LocalDateTime) {
        if (!s.mute) return
        val ongoing = events(snapshot, now.toLocalDate().atStartOfDay(), 0, s)
            .firstOrNull { it.kind == Kind.Mute && !now.isBefore(it.start) && now.isBefore(it.end) }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when {
            ongoing != null && !prefs.contains(ClassMute.KEY_APPLIED) -> ClassMute.mute(app)
            ongoing == null && prefs.contains(ClassMute.KEY_APPLIED) -> ClassMute.restore(app)
        }
    }

    /** 闹钟触发：处理上次处理之后到现在的所有到期事件（最多回看 10 分钟），再排下一次。 */
    internal fun fire(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = settings(app)
        val snapshot = readSnapshot(app)
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val lastMillis = prefs.getLong(KEY_LAST_FIRED, 0L)
        val floor = now.minusMinutes(10)
        val last = if (lastMillis == 0L) floor else maxOf(floor, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastMillis), zone))
        if (snapshot != null) {
            val due = events(snapshot, floor.toLocalDate().atStartOfDay(), 1, s).filter { it.at.isAfter(last) && !it.at.isAfter(now.plusSeconds(30)) }
            android.util.Log.i(TAG, "fire: ${due.size} due event(s) at $now")
            due.forEach { e ->
                when (e.kind) {
                    Kind.Remind -> ClassNotifier.notify(app, e, now)
                    Kind.Mute -> ClassMute.mute(app)
                    Kind.Unmute -> ClassMute.restore(app)
                }
            }
            due.maxOfOrNull { it.at }?.let { prefs.edit().putLong(KEY_LAST_FIRED, it.atZone(zone).toInstant().toEpochMilli()).apply() }
        }
        reschedule(app)
    }

    private const val TAG = "YZUA-Reminder"

    fun formatTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
}
