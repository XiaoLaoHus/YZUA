package cn.yzu.timetable.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.yzu.timetable.MainActivity
import cn.yzu.timetable.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** 上课提醒通知：只包含课程名、时间与地点。 */
internal object ClassNotifier {
    private const val CHANNEL = "class_reminder"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "每节课开始前提醒课程与地点" })
    }

    fun notify(context: Context, event: ClassReminders.Event, now: LocalDateTime) {
        if (!now.isBefore(event.end)) return
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        ensureChannel(context)
        val minutes = Duration.between(now, event.start).toMinutes().coerceAtLeast(0)
        val lesson = event.lesson
        val title = if (minutes > 0) "$minutes 分钟后上课 · ${lesson.courseName}" else "正在上课 · ${lesson.courseName}"
        val place = lesson.classroom.ifBlank { "未提供地点" }
        val body = "${ClassReminders.formatTime(event.start.toLocalTime())}–${ClassReminders.formatTime(event.end.toLocalTime())} · $place"
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val zone = ZoneId.systemDefault()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0B5E3F.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n第 ${lesson.startPeriod}–${lesson.endPeriod} 节" + if (lesson.teacher.isNotBlank()) " · ${lesson.teacher}" else ""))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(event.start.atZone(zone).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setTimeoutAfter(Duration.between(now, event.end).toMillis().coerceAtLeast(60_000))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { compat.notify(lesson.id.hashCode() xor event.start.hashCode(), notification) } catch (_: SecurityException) { }
    }
}
