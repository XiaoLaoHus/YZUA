package cn.yzu.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收提醒闹钟，以及开机、时间/时区变化、应用更新后的重新排程。 */
class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                if (intent.action == ClassReminders.ACTION_FIRE) ClassReminders.fire(context) else ClassReminders.reschedule(context)
            } finally { pending.finish() }
        }.start()
    }
}
