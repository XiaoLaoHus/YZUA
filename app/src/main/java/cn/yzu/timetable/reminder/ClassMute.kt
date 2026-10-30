package cn.yzu.timetable.reminder

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/**
 * 上课自动静音：已授予勿扰权限时切到静音，否则切到振动（无需特殊权限）；系统拒绝则放弃。
 * 只在铃声为“响铃”时介入，下课时仅当铃声仍是本应用设置的模式才恢复，不覆盖用户的手动调整。
 */
internal object ClassMute {
    private const val KEY_PREVIOUS = "mute_previous_mode"
    internal const val KEY_APPLIED = "mute_applied_mode"

    fun hasPolicyAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun mute(context: Context) {
        val prefs = context.getSharedPreferences(ClassReminders.PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_APPLIED)) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val previous = audio.ringerMode
        if (previous != AudioManager.RINGER_MODE_NORMAL) return
        val target = if (hasPolicyAccess(context)) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE
        try {
            audio.ringerMode = target
            if (audio.ringerMode == target) prefs.edit().putInt(KEY_PREVIOUS, previous).putInt(KEY_APPLIED, target).apply()
        } catch (_: SecurityException) { }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(ClassReminders.PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_APPLIED)) return
        val applied = prefs.getInt(KEY_APPLIED, -1)
        val previous = prefs.getInt(KEY_PREVIOUS, AudioManager.RINGER_MODE_NORMAL)
        prefs.edit().remove(KEY_APPLIED).remove(KEY_PREVIOUS).apply()
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode != applied) return
        try { audio.ringerMode = previous } catch (_: SecurityException) { }
    }
}
