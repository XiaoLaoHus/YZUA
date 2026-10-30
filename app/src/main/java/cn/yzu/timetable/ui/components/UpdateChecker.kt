package cn.yzu.timetable.ui.components

import android.content.Context
import cn.yzu.timetable.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 检查最新版本。只读公开接口，超时即失败，不影响正常使用。
 */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/XiaoLaoHus/YZUA/releases/latest"
    const val TIMEOUT_MS = 6000

    sealed interface Result {
        data class UpdateAvailable(val version: String, val url: String) : Result
        data object UpToDate : Result
        data class Failed(val reason: String) : Result
    }

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /** 比较 x.y.z 版本号，返回 true 表示 remote 更新。 */
    internal fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', '+').take(3).map { it.toIntOrNull() ?: 0 } + List(3) { 0 }
        val r = parts(remote); val c = parts(current)
        for (i in 0 until 3) if (r[i] != c[i]) return r[i] > c[i]
        return false
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "YZUA-UpdateCheck")
            }
            try {
                if (connection.responseCode != 200) return@withContext Result.Failed("服务返回 ${connection.responseCode}")
                val json = JsonParser.parseString(connection.inputStream.bufferedReader().use { it.readText() }).asJsonObject
                val tag = json.get("tag_name")?.asString?.removePrefix("v") ?: return@withContext Result.Failed("响应缺少版本号")
                val url = json.get("html_url")?.asString ?: "https://github.com/XiaoLaoHus/YZUA/releases/latest"
                if (isNewer(tag, currentVersion())) Result.UpdateAvailable(tag, url) else Result.UpToDate
            } finally { connection.disconnect() }
        } catch (error: Exception) {
            Result.Failed(error.message ?: "网络不可用")
        }
    }

    /** 弱提示用的简短文案。 */
    fun failureText(context: Context, result: Result.Failed): String = "检查更新失败：${result.reason}（不影响使用）"
}
